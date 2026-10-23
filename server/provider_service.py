"""Live provider health service for the TALOS core.

This module turns the static catalog in :mod:`core.providers` into a live
health picture: it resolves credentials, probes each provider's model
listing endpoint, measures the round-trip latency, and classifies the
result into a :class:`~core.models.ProviderStatus`.

Probing is best-effort and never raises: a provider that is offline, slow
or misconfigured is reported as such instead of failing the request. Results
are cached for a short TTL so that a dashboard polling the endpoint does not
hammer the upstream providers.
"""

from __future__ import annotations

import asyncio
import logging
import os
import time
from typing import Any, Dict, List, Optional, Tuple

from core.models import (
    FailoverRouteResponse,
    ProviderHealth,
    ProviderInfo,
    ProviderModel,
    ProviderRanking,
    ProviderStatus,
    ProviderSummary,
)
from core.providers import (
    ProviderSpec,
    get_spec,
    list_specs,
    lookup_context_length,
    resolve_credential,
)

logger = logging.getLogger("talos.providers")

#: Environment variable that forces the probe to be skipped entirely
#: (useful for offline tests and for air-gapped deployments).
PROVIDER_PROBE_DISABLED_ENV = "TALOS_DISABLE_PROVIDER_PROBING"

#: Default time-to-live for a cached probe result.
DEFAULT_CACHE_TTL = 60.0


def _auth_headers(spec: ProviderSpec, credential: Optional[str]) -> Dict[str, str]:
    """Build the provider specific auth headers."""
    if not credential:
        return {}
    if spec.api_style == "anthropic":
        return {
            "x-api-key": credential,
            "anthropic-version": "2023-06-01",
        }
    return {"Authorization": f"Bearer {credential}"}


def _extract_models(payload: Any) -> List[ProviderModel]:
    """Parse a provider model listing into :class:`ProviderModel` entries."""
    models: List[ProviderModel] = []
    if not isinstance(payload, dict):
        return models
    raw = payload.get("data")
    if not isinstance(raw, list):
        raw = payload.get("models")
    if not isinstance(raw, list):
        return models
    for entry in raw:
        if not isinstance(entry, dict):
            continue
        model_id = entry.get("id") or entry.get("name")
        if not model_id:
            continue
        if isinstance(model_id, str) and model_id.startswith("models/"):
            model_id = model_id.split("/", 1)[1]
        context_length = None
        for key in ("context_length", "context_length_tokens", "inputTokenLimit", "max_input_tokens"):
            value = entry.get(key)
            if isinstance(value, int) and value > 0:
                context_length = value
                break
        models.append(
            ProviderModel(
                id=str(model_id),
                context_length=context_length,
                context_source="live-catalog" if context_length else "unknown",
            )
        )
    return models


class ProviderService:
    """Probes providers and caches the resulting health snapshot."""

    def __init__(self, specs: Optional[List[ProviderSpec]] = None, cache_ttl: float = DEFAULT_CACHE_TTL):
        self._specs = list(specs) if specs is not None else list_specs()
        self._cache_ttl = cache_ttl
        self._cache: Dict[str, ProviderInfo] = {}
        self._lock = asyncio.Lock()

    # -- catalog ----------------------------------------------------------

    def specs(self) -> List[ProviderSpec]:
        return list(self._specs)

    def spec(self, provider_id: str) -> Optional[ProviderSpec]:
        return get_spec(provider_id)

    # -- probing ---------------------------------------------------------

    async def probe(self, spec: ProviderSpec) -> ProviderInfo:
        """Probe a single provider and return its health snapshot."""
        credential, credential_source = resolve_credential(spec)
        now = time.time()

        info = ProviderInfo(
            id=spec.id,
            name=spec.name,
            vendor=spec.vendor,
            base_url=spec.base_url,
            api_style=spec.api_style,
            default_model=spec.default_model,
            docs_url=spec.docs_url,
            credential_present=bool(credential),
            credential_source=credential_source,
            checked_at=now,
        )

        # Context window of the default model: live catalog wins, then the
        # local Hermes cache, then the static fallback.
        cached = lookup_context_length(spec.default_model, spec.base_url)
        info.context_length = cached[0] if cached else spec.context_length

        if os.environ.get(PROVIDER_PROBE_DISABLED_ENV):
            info.status = ProviderStatus.unknown
            info.status_detail = "probing disabled"
            return info

        if not credential:
            info.status = ProviderStatus.unconfigured
            info.status_detail = "no credential found for this provider"
            return info

        started = time.perf_counter()
        try:
            status_code, payload, error = await self._fetch_models(spec, credential)
        except asyncio.TimeoutError:
            info.latency_ms = round((time.perf_counter() - started) * 1000, 2)
            info.status = ProviderStatus.unreachable
            info.status_detail = "probe timed out"
            info.error = "timeout"
            return info
        except Exception as exc:  # pragma: no cover - defensive
            info.latency_ms = round((time.perf_counter() - started) * 1000, 2)
            info.status = ProviderStatus.unreachable
            info.status_detail = "probe failed"
            info.error = f"{type(exc).__name__}: {exc}"
            return info

        info.latency_ms = round((time.perf_counter() - started) * 1000, 2)

        if error:
            info.status = ProviderStatus.unreachable
            info.status_detail = "provider unreachable"
            info.error = error
            return info

        if status_code == 200:
            info.status = ProviderStatus.operational
            info.status_detail = "reachable"
            models = _extract_models(payload)
            info.models = models
            info.models_count = len(models)
            live_default = next((m for m in models if m.id == spec.default_model), None)
            if live_default and live_default.context_length:
                info.context_length = live_default.context_length
            if models:
                lengths = [m.context_length for m in models if m.context_length]
                if lengths:
                    info.max_context_length = max(lengths)
        elif status_code in (401, 403):
            info.status = ProviderStatus.unauthorized
            info.status_detail = "credential rejected by provider"
        elif status_code == 429:
            info.status = ProviderStatus.rate_limited
            info.status_detail = "provider rate limit reached"
        elif status_code >= 500:
            info.status = ProviderStatus.degraded
            info.status_detail = f"provider returned {status_code}"
        else:
            info.status = ProviderStatus.degraded
            info.status_detail = f"unexpected status {status_code}"

        return info

    async def _fetch_models(
        self, spec: ProviderSpec, credential: str
    ) -> Tuple[int, Any, Optional[str]]:
        """Fetch the model listing. Returns ``(status, payload, error)``."""
        url = spec.models_url()
        headers = _auth_headers(spec, credential)
        headers.setdefault("Accept", "application/json")

        # Use httpx when available (it is a hard dependency of the test
        # suite), and fall back to a stdlib request otherwise so the server
        # keeps working in a minimal deployment.
        try:
            import httpx
        except Exception:  # pragma: no cover - httpx is normally present
            httpx = None  # type: ignore

        if httpx is not None:
            timeout = httpx.Timeout(spec.timeout, connect=min(spec.timeout, 5.0))
            try:
                async with httpx.AsyncClient(timeout=timeout) as client:
                    response = await client.get(url, headers=headers)
                    try:
                        payload = response.json()
                    except Exception:
                        payload = None
                    return response.status_code, payload, None
            except httpx.TimeoutException:
                raise asyncio.TimeoutError()
            except Exception as exc:
                return 0, None, f"{type(exc).__name__}: {exc}"

        # Stdlib fallback.
        import urllib.error
        import urllib.request

        request = urllib.request.Request(url, headers=headers, method="GET")
        try:
            with urllib.request.urlopen(request, timeout=spec.timeout) as response:
                body = response.read()
                try:
                    import json

                    payload = json.loads(body.decode("utf-8"))
                except Exception:
                    payload = None
                return response.status, payload, None
        except urllib.error.HTTPError as exc:
            return exc.code, None, None
        except Exception as exc:
            return 0, None, f"{type(exc).__name__}: {exc}"

    # -- public surface --------------------------------------------------

    async def snapshot(self, force: bool = False) -> List[ProviderInfo]:
        """Return the health of every provider, probing when needed."""
        async with self._lock:
            now = time.time()
            results: List[ProviderInfo] = []
            to_probe: List[ProviderSpec] = []

            for spec in self._specs:
                cached = self._cache.get(spec.id)
                fresh = (
                    cached is not None
                    and not force
                    and (now - (cached.checked_at or 0)) < self._cache_ttl
                )
                if fresh:
                    results.append(cached)
                else:
                    to_probe.append(spec)

            if to_probe:
                probed = await asyncio.gather(
                    *(self.probe(spec) for spec in to_probe), return_exceptions=True
                )
                for spec, outcome in zip(to_probe, probed):
                    if isinstance(outcome, BaseException):
                        logger.warning("Provider probe failed for %s: %s", spec.id, outcome)
                        info = ProviderInfo(
                            id=spec.id,
                            name=spec.name,
                            vendor=spec.vendor,
                            base_url=spec.base_url,
                            api_style=spec.api_style,
                            default_model=spec.default_model,
                            status=ProviderStatus.unknown,
                            status_detail="probe error",
                            error=str(outcome),
                            checked_at=time.time(),
                        )
                    else:
                        info = outcome
                    self._cache[spec.id] = info
                    results.append(info)

            order = {spec.id: index for index, spec in enumerate(self._specs)}
            results.sort(key=lambda item: order.get(item.id, 999))
            now = time.time()
            for item in results:
                if item.checked_at:
                    item.age_ms = int(max(0.0, now - item.checked_at) * 1000)
            return results

    async def get(self, provider_id: str, force: bool = False) -> Optional[ProviderInfo]:
        """Return the health of one provider."""
        spec = self.spec(provider_id)
        if spec is None:
            return None
        cached = self._cache.get(spec.id)
        if (
            not force
            and cached is not None
            and (time.time() - (cached.checked_at or 0)) < self._cache_ttl
        ):
            return cached
        info = await self.probe(spec)
        self._cache[spec.id] = info
        return info

    async def health(self, force: bool = False) -> List[ProviderHealth]:
        """Return real-time latency, status (healthy/degraded), and context length for registered providers."""
        infos = await self.snapshot(force=force)
        out: List[ProviderHealth] = []
        for info in infos:
            is_healthy = (
                info.status == ProviderStatus.operational
                and not info.error
            )
            out.append(
                ProviderHealth(
                    id=info.id,
                    name=info.name,
                    status="healthy" if is_healthy else "degraded",
                    latency_ms=info.latency_ms,
                    context_length=info.context_length,
                    vendor=info.vendor,
                )
            )
        return out

    async def get_health(self, force: bool = False) -> List[ProviderHealth]:
        """Alias for health()."""
        return await self.health(force=force)

    async def single_health(self, provider_id: str, force: bool = False) -> Optional[ProviderHealth]:
        """Return real-time latency, status (healthy/degraded), and context length for a single provider."""
        info = await self.get(provider_id, force=force)
        if info is None:
            return None
        is_healthy = (
            info.status == ProviderStatus.operational
            and not info.error
        )
        return ProviderHealth(
            id=info.id,
            name=info.name,
            status="healthy" if is_healthy else "degraded",
            latency_ms=info.latency_ms,
            context_length=info.context_length,
            vendor=info.vendor,
        )

    @staticmethod
    def summarize(providers: List[ProviderInfo]) -> ProviderSummary:
        """Aggregate a provider list into a :class:`ProviderSummary`."""
        summary = ProviderSummary(total=len(providers))
        latencies: List[float] = []
        fastest: Optional[Tuple[str, float]] = None
        for info in providers:
            status = info.status.value if hasattr(info.status, "value") else str(info.status)
            if status in (
                ProviderStatus.operational.value,
                ProviderStatus.degraded.value,
                ProviderStatus.rate_limited.value,
                ProviderStatus.unauthorized.value,
                ProviderStatus.unconfigured.value,
                ProviderStatus.unreachable.value,
                ProviderStatus.unknown.value,
            ):
                setattr(summary, status, getattr(summary, status) + 1)
            if info.credential_present:
                summary.configured += 1
            if info.latency_ms is not None:
                latencies.append(info.latency_ms)
                if fastest is None or info.latency_ms < fastest[1]:
                    fastest = (info.id, info.latency_ms)
            if info.checked_at:
                if summary.checked_at is None or info.checked_at > summary.checked_at:
                    summary.checked_at = info.checked_at
        if latencies:
            summary.average_latency_ms = round(sum(latencies) / len(latencies), 2)
        if fastest:
            summary.fastest_provider = fastest[0]
        return summary

    async def benchmark_and_route(self) -> FailoverRouteResponse:
        """Concurrently probe all providers and calculate ranked failover route."""
        infos = await self.snapshot(force=True)

        def sort_key(info: ProviderInfo) -> Tuple[int, float]:
            is_op = 0 if info.status == ProviderStatus.operational else (1 if info.status == ProviderStatus.degraded else 2)
            lat = info.latency_ms if info.latency_ms is not None else 999999.0
            return (is_op, lat)

        ranked = sorted(infos, key=sort_key)
        rankings: List[ProviderRanking] = []
        failover_chain: List[str] = []

        for idx, info in enumerate(ranked):
            tier = "primary" if idx == 0 else f"fallback-{idx}"
            status_str = "healthy" if info.status == ProviderStatus.operational and not info.error else "degraded"
            if info.status in (ProviderStatus.unreachable, ProviderStatus.unauthorized, ProviderStatus.unconfigured):
                status_str = "offline"

            rankings.append(
                ProviderRanking(
                    id=info.id,
                    name=info.name,
                    latency_ms=info.latency_ms,
                    status=status_str,
                    tier=tier,
                )
            )
            if status_str in ("healthy", "degraded"):
                failover_chain.append(info.id)

        primary = failover_chain[0] if failover_chain else (ranked[0].id if ranked else "hermes")
        self._cached_failover = FailoverRouteResponse(
            primary_provider=primary,
            failover_chain=failover_chain,
            rankings=rankings,
            benchmark_timestamp=time.time(),
        )
        return self._cached_failover

    async def get_failover_routing(self) -> FailoverRouteResponse:
        """Return cached or active failover routing order."""
        if hasattr(self, "_cached_failover") and self._cached_failover is not None:
            return self._cached_failover
        return await self.benchmark_and_route()


#: Process-wide provider service used by the FastAPI app.
provider_service = ProviderService()
