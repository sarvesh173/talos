# TALOS API Reference

**Base URL (LAN):** `http://192.168.31.209:8000`
**Base URL (Tailscale):** `http://100.75.107.79:8000`
**Auth:** every call needs `Authorization: Bearer talos_sk_...`

Get a token:

```bash
curl -X POST http://192.168.31.209:8000/api/auth/pair
```

Returns `{ "token": "talos_sk_...", "label": "...", "status": "paired" }`.

---

## OpenCode (chat with your coding agent)

### Health — is the agent reachable, and which models?

```bash
curl http://192.168.31.209:8000/api/opencode/health \
  -H "Authorization: Bearer $TALOS_TOKEN"
```

```json
{
  "ok": true,
  "status": 200,
  "error": null,
  "models": ["nemotron-3.5-lightning-free", "..."]
}
```

`ok: false` carries the real reason (401 credential, unreachable, ...), never a
generic failure.

---

### List sessions

```bash
curl http://192.168.31.209:8000/api/opencode/sessions \
  -H "Authorization: Bearer $TALOS_TOKEN"
```

```json
{
  "ok": true,
  "count": 2,
  "sessions": [
    { "id": "ses_abc123", "title": "Fix login bug", "agent": "build", "model": "nemotron-3.5-lightning-free", "project_id": "..." }
  ]
}
```

---

### Create a session

```bash
curl -X POST http://192.168.31.209:8000/api/opencode/sessions \
  -H "Authorization: Bearer $TALOS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}'
```

```json
{ "ok": true, "session": { "id": "ses_abc123", "title": "New Session", "model": null } }
```

---

### Read a conversation

```bash
curl "http://192.168.31.209:8000/api/opencode/sessions/ses_abc123/messages?limit=50" \
  -H "Authorization: Bearer $TALOS_TOKEN"
```

```json
{
  "ok": true,
  "count": 4,
  "messages": [
    {
      "id": "msg_1",
      "role": "user",
      "text": "Fix the login bug",
      "thought": null,
      "tool_records": [],
      "metrics": null,
      "error": null,
      "created_ms": 1794591732280
    },
    {
      "id": "msg_2",
      "role": "assistant",
      "text": "The login handler ignores...",
      "thought": "Let me look at the auth module first...",
      "tool_records": [
        {
          "id": "call-1",
          "name": "read",
          "summary": "src/auth.py",
          "arguments": "{\"path\":\"src/auth.py\"}",
          "status": "succeeded",
          "started_at": 1794591735000,
          "ended_at": 1794591735400,
          "output_preview": "def login(user, password):",
          "error": null
        }
      ],
      "metrics": {
        "latency_ms": 4200,
        "prompt_tokens": 1038,
        "completion_tokens": 162,
        "reasoning_tokens": 18,
        "cost_usd": 0,
        "model": "nemotron-3.5-lightning-free",
        "cache_read_tokens": 115280
      },
      "error": null,
      "created_ms": 1794591736000
    }
  ]
}
```

**Field notes**

- `role`: `user` | `assistant`. (`idle`, `compaction`, `system` rows carry no prose and are dropped.)
- `thought`: the model's reasoning. `null` when the provider streams none.
- `tool_records[]`: one entry per tool call. `status` is `running` | `succeeded` | `failed`.
- `metrics`: telemetry the engine already reports — latency, tokens, cost, model, cache reads.
- `created_ms`: epoch **milliseconds**.
- Messages come back **oldest first**.

---

### Send a message

```bash
curl -X POST http://192.168.31.209:8000/api/opencode/sessions/ses_abc123/prompt \
  -H "Authorization: Bearer $TALOS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"text":"What is wrong with the login handler?"}'
```

```json
{ "ok": true, "sent": "What is wrong with the login handler?", "message": { "data": { "id": "msg_3", "type": "user" } } }
```

Accepted immediately; the answer arrives later. **Poll `GET .../messages`** until the
last message is an `assistant` turn with non-empty `text`.

---

## Python client

```python
import time
import requests

BASE = "http://192.168.31.209:8000"
TOKEN = "talos_sk_..."          # from POST /api/auth/pair
H = {"Authorization": f"Bearer {TOKEN}"}


def create_session() -> str:
    r = requests.post(f"{BASE}/api/opencode/sessions", headers=H, json={})
    r.raise_for_status()
    return r.json()["session"]["id"]


def send(session_id: str, text: str, timeout: float = 120.0) -> str:
    requests.post(
        f"{BASE}/api/opencode/sessions/{session_id}/prompt",
        headers=H, json={"text": text}, timeout=30,
    ).raise_for_status()

    deadline = time.time() + timeout
    seen = 0
    while time.time() < deadline:
        time.sleep(2)
        msgs = requests.get(
            f"{BASE}/api/opencode/sessions/{session_id}/messages",
            headers=H, params={"limit": 50}, timeout=20,
        ).json()["messages"]

        # Only look at turns that did not exist when we sent.
        for m in msgs[seen:]:
            if m["role"] == "assistant" and m["text"].strip():
                return m["text"]
        seen = len(msgs)

    raise TimeoutError("no answer in time")


if __name__ == "__main__":
    sid = create_session()
    print(send(sid, "Reply with exactly: OK-123"))
```

---

## curl one-liner

```bash
TALOS_TOKEN=$(curl -s -X POST http://192.168.31.209:8000/api/auth/pair | jq -r .token)
SID=$(curl -s -X POST http://192.168.31.209:8000/api/opencode/sessions \
  -H "Authorization: Bearer $TALOS_TOKEN" -H "Content-Type: application/json" -d '{}' | jq -r .session.id)

curl -s -X POST "http://192.168.31.209:8000/api/opencode/sessions/$SID/prompt" \
  -H "Authorization: Bearer $TALOS_TOKEN" -H "Content-Type: application/json" \
  -d '{"text":"Say only: PONG"}'

sleep 20

curl -s "http://192.168.31.209:8000/api/opencode/sessions/$SID/messages?limit=10" \
  -H "Authorization: Bearer $TALOS_TOKEN" | jq '.messages[] | select(.role=="assistant") | .text'
```

---

## Errors

| HTTP | Meaning |
|---|---|
| 401 | Missing or revoked token. Call `/api/auth/pair` again. |
| 422 | `text` was empty or not a string. |
| 502 | Could not create the session — OpenCode unreachable. |

`{"ok": false, "error": "..."}` is returned with 200 on some paths; always check `ok`.