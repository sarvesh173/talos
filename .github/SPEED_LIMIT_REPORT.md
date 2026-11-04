# Speed report: model exceeded the animation ceiling

The orb animation saturates at **20,000 output tokens/second per user**. That is a
deliberate, future-proofed ceiling — not a claim about what hardware can do.

If a model is genuinely streaming faster than that, the animation is no longer
telling the truth: it renders "as fast as we can show" while the model does much
more. The right fix is to raise the ceiling, and that needs evidence.

## What to include

Please fill in the fields below. The two that actually matter are the **exact
model id** and the **provider** — without those nobody can reproduce the number.

```
model:            e.g. vendor/model-name-here   (exact string from the API,
                                                   not a display name)
provider:         e.g. openrouter / cerebras / groq / local-proxy
measured_tok/s:   e.g. 6200
how_measured:     GUI footer / API usage block / terminal command
date:             YYYY-MM-DD
```

## Where to find the numbers

**In the app** — the token speed is shown under each reply in the message footer.
Tap it; the sheet lists the model id, provider, ttft, and tokens/second for that
turn.

**From the API** — the `usage` block of any completion response is authoritative:

```bash
curl -s https://openrouter.ai/api/v1/chat/completions \
  -H "Authorization: Bearer $OPENROUTER_API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"model":"PROVIDER/MODEL","messages":[{"role":"user","content":"count to 200"}],"stream":false}' \
  | jq '.usage'
```

`completion_tokens` divided by the elapsed generation time is the number to
report. Use a long output (200+ tokens) so the first-token latency does not
dominate.

**From TALOS itself**

```bash
python3 - <<'PY'
import asyncio, sys
sys.path.insert(0, '.')
from core.llm_client import ModelClient
async def main():
    c = ModelClient(model="PROVIDER/MODEL", base_url="https://your-endpoint/v1")
    r = await c.complete([{"role": "user", "content": "count to 200 in words"}], max_tokens=1500)
    print(f"{c.model}: {r.tokens_per_second:.1f} tok/s, ttft {r.ttft_ms} ms")
asyncio.run(main())
PY
```

## What happens next

The ceiling lives in one place —
`android/app/src/main/java/com/sarvesh173/talos/ui/orb/AdaptiveOrbSpeed.kt`,
function `orbSpeedTarget`. Raising it is a one-line change plus a re-calibration
of the curve slope so the newly reachable range stays legible.

Reports are checked against published per-user benchmarks before the constant
moves. **Aggregate server throughput does not count** — a provider advertising
10,000 tok/s across 256 concurrent users is serving ~40 tok/s each, and raising
the ceiling for that would make every normal reply animate at maximum speed.

## Reference points (verified 2026-11-04)

| Per-user output | Model | Provider |
|---|---|---|
| ~17,000 | Llama 3.1 8B (hardwired silicon) | Taalas HC1 / ChatJimmy |
| 2623 | Llama 4 Scout | Cerebras |
| 1581–1712 | Celeris-1 | Celeris |
| 1178 | Mercury 2.5 | Inception |
| 725 | Llama 4 Scout | SambaNova |
| 621 | Llama 4 Scout | Groq |
| 593 | GLM-5.3-Flash | Inco |
| 485 | Nemotron 3.5 Super VL | NVIDIA |
| 402 | Gemini 3.5 Flash Lite | Google |
| 248 | DeepSeek V4.1 Flash | DeepSeek |
| 140 | GLM-5.3-Flash | Lambda (C1, interactive) |

The ceiling sits at 20,000 — above the fastest per-user number measured as of
this writing (Taalas HC1, ~17,000 tok/s from an 8B model hardwired into custom
silicon). That leaves headroom for the next generation without the animation
losing meaning today.

Note that Taalas is a special case: it is not downloadable weights or a
trillion-parameter model, it is an 8B model with an aggressive 3-6 bit
quantization baked into proprietary silicon, and its public demo caps context
at 6,144 tokens. It still counts as a real per-user measurement, which is why
the ceiling moved above it.
