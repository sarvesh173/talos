"""Provider catalog for the TALOS core.

The catalog describes the model providers that the chamber can talk to. Every
entry is grounded in the *live* local Hermes installation rather than in
hard-coded guesses:

* base URLs and credential environment variable names come from the Hermes
  provider registry (``hermes_cli.auth.PROVIDER_REGISTRY``),
* the Hermes runtime entry is resolved from the active
  ``~/.hermes/config.yaml`` (``model.base_url`` / ``model.default``),
* context windows are resolved from the local Hermes context-length cache
  (``~/.hermes/context_length_cache.yaml``) and finally from a static
  fallback table.

Nothing here performs network I/O. Live probing lives in
:mod:`server.provider_service` so the catalog stays importable and testable
without a network.
"""

from __future__ import annotations

import hashlib
import os
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple

HERMES_HOME = Path(os.environ.get("HERMES_HOME", Path.home() / ".hermes"))
HERMES_CONFIG = HERMES_HOME / "config.yaml"
HERMES_ENV_FILE = HERMES_HOME / ".env"
HERMES_AUTH_FILE = HERMES_HOME / "auth.json"
HERMES_CONTEXT_CACHE = HERMES_HOME / "context_length_cache.yaml"


# ---------------------------------------------------------------------------
# Credential helpers
# ---------------------------------------------------------------------------


def read_dotenv(path: Path = HERMES_ENV_FILE) -> Dict[str, str]:
    """Parse a ``KEY=value`` dotenv file without executing anything."""
    values: Dict[str, str] = {}
    try:
        if not path.exists():
            return values
        for raw in path.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            values[key.strip()] = value
    except OSError:
        return values
    return values


def _yaml_load(path: Path) -> Dict:
    """Best-effort YAML load. Returns ``{}`` when PyYAML is unavailable."""
    try:
        import yaml  # type: ignore
    except Exception:
        return {}
    try:
        if not path.exists():
            return {}
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
    except Exception:
        return {}
    return data if isinstance(data, dict) else {}


def read_hermes_model_config() -> Dict[str, str]:
    """Return the active Hermes runtime settings (``model`` block)."""
    config = _yaml_load(HERMES_CONFIG)
    model = config.get("model")
    if not isinstance(model, dict):
        return {}
    out: Dict[str, str] = {}
    for key in ("base_url", "default", "provider", "api_mode"):
        value = model.get(key)
        if isinstance(value, (str, int, float)):
            out[key] = str(value)
    return out


def read_context_cache() -> Dict[str, int]:
    """Return the local Hermes ``model -> context length`` cache."""
    data = _yaml_load(HERMES_CONTEXT_CACHE)
    lengths = data.get("context_lengths")
    if not isinstance(lengths, dict):
        return {}
    out: Dict[str, int] = {}
    for key, value in lengths.items():
        try:
            out[str(key)] = int(value)
        except (TypeError, ValueError):
            continue
    return out


def lookup_context_length(model: str, base_url: str = "") -> Optional[Tuple[int, str]]:
    """Resolve the context length of ``model``.

    Returns ``(length, source)`` where source is ``hermes-cache`` when the
    local Hermes cache answered, or ``None`` when the model is unknown.
    """
    if not model:
        return None
    cache = read_context_cache()
    if not cache:
        return None
    exact = f"{model}@{base_url}" if base_url else None
    if exact and exact in cache:
        return cache[exact], "hermes-cache"
    if model in cache:
        return cache[model], "hermes-cache"
    # Fall back to a suffix match on the model id, ignoring the base url.
    suffix = f"/{model}"
    matches = [v for k, v in cache.items() if k.endswith(suffix)]
    if matches:
        return max(matches), "hermes-cache"
    return None


def read_nous_anon_token() -> Optional[str]:
    """Return the anonymous Nous Portal token stored by Hermes, if any."""
    try:
        import json

        if not HERMES_AUTH_FILE.exists():
            return None
        data = json.loads(HERMES_AUTH_FILE.read_text(encoding="utf-8"))
        providers = data.get("providers")
        if not isinstance(providers, dict):
            return None
        nous = providers.get("nous")
        if not isinstance(nous, dict):
            return None
        token = nous.get("anon_token")
        return token if isinstance(token, str) and token else None
    except Exception:
        return None


# ---------------------------------------------------------------------------
# Catalog definitions
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class ProviderSpec:
    """Static description of one model provider."""

    id: str
    name: str
    vendor: str
    base_url: str
    api_style: str  # openai | anthropic | gemini
    default_model: str
    docs_url: str = ""
    #: Environment variables that may hold the credential, most specific first.
    credential_env_vars: Tuple[str, ...] = ()
    #: Static fallback context window for ``default_model``.
    context_length: int = 128000
    #: Path appended to ``base_url`` to list models.
    models_path: str = "/models"
    #: Seconds before a live probe is considered stale.
    cache_ttl: float = 60.0
    #: Probe timeout in seconds.
    timeout: float = 8.0
    notes: str = ""
    #: Extra credential sources that are not plain env vars.
    extra_credential_sources: Tuple[str, ...] = field(default=())

    def models_url(self) -> str:
        return self.base_url.rstrip("/") + self.models_path


def _hermes_spec() -> ProviderSpec:
    """Build the Hermes entry from the live local Hermes runtime."""
    runtime = read_hermes_model_config()
    base_url = runtime.get("base_url") or "https://inference-api.nousresearch.com/v1"
    default_model = runtime.get("default") or "hermes-4-405b"
    context = lookup_context_length(default_model, base_url)
    context_length = context[0] if context else 131072
    return ProviderSpec(
        id="hermes",
        name="Hermes",
        vendor="Nous Research",
        base_url=base_url,
        api_style="openai",
        default_model=default_model,
        docs_url="https://hermes-agent.nousresearch.com",
        credential_env_vars=(
            "OMNIROUTE_API_KEY",
            "OMNIROUTER_API_KEY",
            "NOUS_API_KEY",
            "HERMES_API_KEY",
        ),
        context_length=context_length,
        timeout=6.0,
        notes="Local Hermes agent runtime gateway.",
        extra_credential_sources=("nous-anon-token",),
    )


#: The providers exposed by ``GET /api/providers``.
PROVIDER_CATALOG: Tuple[ProviderSpec, ...] = (
    ProviderSpec(
        id="anthropic",
        name="Anthropic",
        vendor="Anthropic",
        base_url="https://api.anthropic.com",
        api_style="anthropic",
        default_model="claude-sonnet-4.5",
        docs_url="https://docs.anthropic.com",
        credential_env_vars=("ANTHROPIC_API_KEY", "ANTHROPIC_TOKEN"),
        context_length=200000,
        models_path="/v1/models",
    ),
    ProviderSpec(
        id="openai",
        name="OpenAI",
        vendor="OpenAI",
        base_url="https://api.openai.com/v1",
        api_style="openai",
        default_model="gpt-5.5",
        docs_url="https://platform.openai.com/docs",
        credential_env_vars=("OPENAI_API_KEY",),
        context_length=400000,
    ),
    ProviderSpec(
        id="gemini",
        name="Google Gemini",
        vendor="Google",
        base_url="https://generativelanguage.googleapis.com/v1beta",
        api_style="gemini",
        default_model="gemini-3.6-flash-high",
        docs_url="https://ai.google.dev/docs",
        credential_env_vars=("GOOGLE_API_KEY", "GEMINI_API_KEY"),
        context_length=1048576,
    ),
    ProviderSpec(
        id="deepseek",
        name="DeepSeek",
        vendor="DeepSeek",
        base_url="https://api.deepseek.com/v1",
        api_style="openai",
        default_model="deepseek-v4-pro",
        docs_url="https://api-docs.deepseek.com",
        credential_env_vars=("DEEPSEEK_API_KEY",),
        context_length=128000,
    ),
    ProviderSpec(
        id="mistral",
        name="Mistral",
        vendor="Mistral AI",
        base_url="https://api.mistral.ai/v1",
        api_style="openai",
        default_model="codestral-2508",
        docs_url="https://docs.mistral.ai",
        credential_env_vars=("MISTRAL_API_KEY",),
        context_length=128000,
    ),
    ProviderSpec(
        id="qwen",
        name="Qwen",
        vendor="Alibaba Cloud",
        base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
        api_style="openai",
        default_model="qwen3-32b",
        docs_url="https://help.aliyun.com/zh/model-studio/developer-reference/api-reference",
        credential_env_vars=("DASHSCOPE_API_KEY", "QWEN_API_KEY", "ALIYUN_API_KEY"),
        context_length=131072,
        models_path="/v1/models",
    ),
    _hermes_spec(),
)

#: Fast lookup by provider id.
PROVIDERS_BY_ID: Dict[str, ProviderSpec] = {spec.id: spec for spec in PROVIDER_CATALOG}

#: The provider ids the chamber is expected to expose.
REQUIRED_PROVIDER_IDS: Tuple[str, ...] = (
    "anthropic",
    "openai",
    "gemini",
    "deepseek",
    "mistral",
    "qwen",
    "hermes",
)


def list_specs() -> List[ProviderSpec]:
    """Return the catalog as a list."""
    return list(PROVIDER_CATALOG)


def get_spec(provider_id: str) -> Optional[ProviderSpec]:
    """Return one catalog entry, or ``None`` when the id is unknown."""
    return PROVIDERS_BY_ID.get((provider_id or "").strip().lower())


# ---------------------------------------------------------------------------
# Credential resolution
# ---------------------------------------------------------------------------


def resolve_credential(spec: ProviderSpec) -> Tuple[Optional[str], Optional[str]]:
    """Find a usable credential for ``spec``.

    Returns ``(secret, source_label)``. The secret never leaves this module
    except into the HTTP request that probes the provider; the label (for
    example ``env:ANTHROPIC_API_KEY``) is safe to expose over the API.
    """
    for env_var in spec.credential_env_vars:
        value = os.environ.get(env_var)
        if value:
            return value, f"env:{env_var}"

    dotenv = read_dotenv()
    for env_var in spec.credential_env_vars:
        value = dotenv.get(env_var)
        if value:
            return value, f"hermes-env:{env_var}"

    if "nous-anon-token" in spec.extra_credential_sources:
        token = read_nous_anon_token()
        if token:
            return token, "hermes-auth:nous-anon-token"

    return None, None


def mask_token(token: str) -> str:
    """Mask a secret for display: ``talos_sk_ab12...9f3e``."""
    if not token:
        return ""
    if len(token) <= 12:
        return token[:4] + "..." + token[-2:]
    return f"{token[:10]}...{token[-4:]}"


def fingerprint(token: str) -> str:
    """Return a stable, non-reversible identifier for a secret."""
    if not token:
        return ""
    return hashlib.sha256(token.encode("utf-8")).hexdigest()[:16]


def sanitize_base_url(url: str) -> str:
    """Strip any embedded credentials from a base URL."""
    if not url:
        return ""
    return re.sub(r"//[^/@\s]+@", "//", url)
