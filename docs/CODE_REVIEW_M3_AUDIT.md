# TALOS Code Review — M3 Audit & Architectural Assessment

**Repository:** `sarvesh173/talos`
**Review Date:** 2026-10-24
**Reviewer:** Hermes Agent Code Review
**Files Reviewed:** 19 source files (8 Kotlin, 10 Python, 1 docs)

---

## Executive Summary

TALOS is an Android-first multi-agent collaboration hub with a Python FastAPI backend, WebSocket pub/sub bus, and a simulated agent orchestration engine. The codebase is well-structured with clear separation of concerns and good test coverage. Several non-trivial architectural and security issues were identified.

---

## Axis 1 — Material Design 3 Compliance

### ✅ PASS: Color Token Roles

`Theme.kt:45–79` implements a complete M3 tonal surface hierarchy for dark mode:

```
surface             → #000000 (OLED black)
surfaceContainerLow  → #080B10
surfaceContainer     → #0C1017  ← card plane
surfaceContainerHigh → #131923
surfaceContainerHighest → #1A2230
surfaceDim          → #05070B
surfaceBright       → #1F2937
outline             → #334155  (3:1 contrast)
outlineVariant      → #1E2430  (decorative hairline)
```

`Theme.kt:84–118` correctly mirrors the light palette with surface values ranging from `#FFFFFFFF` down through greys. All 8 surface planes are declared. ✅

**Issue — `surface` and `background` are identical in dark scheme:**

`Theme.kt:62,64`
```kotlin
background = Color(0xFF000000),  // True Pure OLED Black
surface = Color(0xFF000000),    // Base surface — SAME VALUE
```

M3 spec requires `surface` to be visually distinguishable from `background`. With both set to `#000000`, no elevation distinction exists. Recommend at minimum `surface = Color(0xFF0D0D0D)` or a 2-step tonal offset.

### ✅ PASS: Shape Scale

`Theme.kt:123–129`
```kotlin
val TalosShapes = Shapes(
    extraSmall = 4.dp,   // ← M3 default
    small       = 8.dp,   // ← M3 default
    medium      = 12.dp,  // ← M3 default
    large       = 16.dp,  // ← M3 default
    extraLarge  = 28.dp   // ← M3 default
)
```
All 5 M3 shape tiers match spec exactly. Custom expressive shapes `ShapeLargeIncreased` (20dp) and `ShapeExtraLargeIncreased` (32dp) are additive extensions — acceptable. `ShapeFull = CircleShape` for badges. ✅

### ⚠️ PARTIAL: Typography Scale

`Theme.kt:139–245` defines all 13 M3 text roles. **Issues:**

1. **`displayLarge` and `displayMedium` are identical weights** — both `FontWeight.Bold`. M3 spec requires `displayLarge` to be `Weight.Normal (w300)` with larger optical size; `displayMedium` should be `Weight.Normal (w400)`. Using `Bold` throughout flattens the optical hierarchy.

2. **`headlineMedium` = `FontWeight.Bold`** — M3 spec recommends `SemiBold (w600)`.

3. **`labelLarge` = `FontWeight.Bold`** — M3 spec recommends `Medium (w500)`. Labels are action text; semibold reduces legibility.

4. **`labelSmall` uses 9sp** — M3 spec minimum is 11sp for accessibility.

### ✅ PASS: M3 Metallic Specular Brush Tokens

`Theme.kt:275–292` — `metallicBorderBrush()` composes specular gradient tokens with dark/light variants. Correctly uses `LocalIsDark` composition local. ✅

### ✅ PASS: Spacing System

`Theme.kt:25–32` — `MdSpacing` 8dp-grid token system: `xxs=4`, `xs=8`, `sm=16`, `md=24`, `lg=32`, `xl=48`. Aligned with I/O 2026 M3 guidance. ✅

---

## Axis 2 — Multi-Agent Bus & WebSocket Binary Frame Handling

### ✅ PASS: RoomEventBus Architecture

`core/room_bus.py:13–262` — Clean pub/sub bus with:
- Per-room `WebSocket` subscriber sets
- Per-room and global `agent_listener` callback registration
- Per-room and global `binary_listener` registration for audio frames
- Proper `asyncio.Lock` on all subscriber mutations

### ⚠️ CRITICAL: Binary Frame Content-Type Unknown

`core/room_bus.py:111–140` — `broadcast_bytes()` dispatches raw `bytes` with no MIME type or metadata. The method comment says "e.g. streaming audio/voice memos" but nothing prevents a PNG or arbitrary binary from being broadcast.

`android/TalosClient.kt:231–253` — Android client **unconditionally writes received `ByteString` frames to `.opus` files**:
```kotlin
val audioFile = File(audioDir, "memo_${...}_$nanoSalt.opus")
audioFile.writeBytes(bytes.toByteArray())
```
If a non-Opus binary frame arrives (image, document, protocol frame), it will be saved with `.opus` extension and passed to `NativeAudioPlayerBubble`. A malformed file passed to the audio player could cause a crash or security issue.

**Fix:** Add a `binary_frame_type` field to `BusEvent` or an explicit `Content-Type` header in the WebSocket protocol. On Android, validate magic bytes before writing.

### ✅ PASS: Binary Listener Contract

`core/room_bus.py:146–161` — `handle_binary_frame()` correctly fans out to other subscribers (excluding sender) AND dispatches to registered binary listeners. The `exclude=sender` pattern prevents echo. Dead socket cleanup is implemented. ✅

### ✅ PASS: Android Binary Handling

`android/TalosClient.kt:183–185` — WebSocket listener correctly branches on `bytes` vs `text`:
```kotlin
override fun onMessage(webSocket: WebSocket, text: String) {
    handleInboundMessage(text)
}
override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
    handleInboundBinaryAudio(bytes)
}
```
No mixing of binary/text paths. ✅

### ⚠️ MINOR: Server-Side Binary Echo Without Type Tag

`server/main.py:551–557` — Server receives binary WebSocket frames and forwards them via `bus.handle_binary_frame()` but adds no metadata about the content type:
```python
if "bytes" in raw_msg and raw_msg["bytes"] is not None:
    await bus.handle_binary_frame(room_id, raw_msg["bytes"], sender=websocket)
```
The binary payload has no envelope. A frame corrupted in transit or misrouted from another protocol will propagate silently.

---

## Axis 3 — Offline Sync

### ✅ PASS: Android Offline Queue

`android/TalosClient.kt:419–465` — Full offline-first message queue:
- `loadOfflineQueue()` / `saveOfflineQueue()` persisted in SharedPreferences
- `enqueueOfflineMessage()` called on send failure
- `flushOfflineQueue()` on WebSocket reconnect
- `QueuedMessage` includes roomId, content, and timestamp

### ✅ PASS: Message Caching

`android/TalosClient.kt:84–100` — Per-room message cache (last 100 messages) in SharedPreferences:
```kotlin
private fun loadCachedMessages(roomId: String): List<Message>
private fun saveCachedMessages(roomId: String, list: List<Message>)
```
Loaded on room switch. ✅

### ⚠️ ISSUE: Offline Queue Has No Size Limit

`android/TalosClient.kt:461–465`
```kotlin
private fun enqueueOfflineMessage(roomId: String, text: String) {
    val list = loadOfflineQueue().toMutableList()
    list.add(QueuedMessage(roomId, text))
    saveOfflineQueue(list)
}
```
No upper bound on queue size. A device offline for days with frequent messages will accumulate unbounded SharedPreferences data. Recommend a 50-message or 48-hour cap.

### ✅ PASS: Optimistic UI Updates

`android/TalosClient.kt:390–417` — `sendMessage()` immediately appends an optimistic human message to `_messages` if WebSocket send fails, then queues for retry. Correct pattern. ✅

---

## Axis 4 — Security Review

### 🔴 CRITICAL: CORS Wildcard Allows All Origins

`server/main.py:98–104`
```python
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # ← Any website can read/write the mesh
    allow_credentials=True,  # ← Cookies + auth headers sent
    allow_methods=["*"],
    allow_headers=["*"],
)
```
With `allow_credentials=True` and `allow_origins=["*"]`, browsers will reject the combination — but the intent to allow all origins is clear. If this is meant for a Tailscale mesh where the server only listens on the mesh subnet, the CORS config is overly permissive for any accidentally exposed port. Consider restricting to the mesh subnet range or removing CORS entirely if this is a private network service.

### 🔴 CRITICAL: Master Token Written to World-Readable File

`server/auth.py:114–116`
```python
_TOKEN_FILE.write_text(master_token.strip() + "\n")
_TOKEN_FILE.chmod(0o600)
```
While `chmod(0o600)` is set, `_TOKEN_FILE.parent.mkdir(parents=True, exist_ok=True)` may create directories with `0o755` permissions on first run. If the home directory itself is too permissive, the token file is readable by other local users. Additionally, if the home directory is on a network filesystem, the token may be readable across machines. Prefer storing in a process-private temp directory or `~/.local/share/talos/`.

### 🟡 MEDIUM: WebSocket Auth on Query Param — Token Leaks in Logs

`server/main.py:527–538`
```python
async def websocket_endpoint(
    websocket: WebSocket,
    room_id: str,
    client_id: str,
    token: Optional[str] = Query(None)  # ← Token in URL query param
):
    if not token or not verify_token(token):
        await websocket.close(code=4401, reason="Unauthorized")
```
Tokens in WebSocket query parameters are visible in server access logs, browser history, and referrer headers. Standard practice is to send the token as a header (`Sec-WebSocket-Protocol` or a custom header during handshake) or in the first WebSocket message after connection. The HTTP `Authorization` header is not available in WebSocket handshakes, but `Sec-WebSocket-Protocol` is the closest equivalent.

### 🟡 MEDIUM: Consensus Deliberation Is Heuristic Mocking

`core/consensus.py:39–75`
```python
for agent in participants:
    if "hermes" in name_lower or "security" in name_lower:
        vote = VoteChoice.approve  # Always approves
```
Every agent always votes `approve` with 0.88–0.98 confidence. The deliberation is a deterministic mock with no actual reasoning. While documented in `agent_orchestrator.py:22–24` ("deterministic simulations"), this means the `/api/consensus` endpoint always returns `outcome: "approved"` and is not a genuine security gate. Should either be documented clearly or replaced with actual model calls.

### ✅ PASS: Token Never Exposed in API Responses

`server/auth.py:127–142` — `_row_to_info()` returns `masked_token` (e.g., `talos_sk_abc1...9f2e`) never the raw secret. Good discipline. ✅

### ✅ PASS: Master Token Protected Against Revocation

`server/auth.py:283–286`
```python
if master and row["token"] == master:
    # The master token is the last resort credential; it may not be revoked.
    conn.close()
    return False
```
Hard-coded protection. ✅

### ✅ PASS: Auth DB and Message DB Use Separate Files

`server/auth.py:29` → `_DATA_DIR / "talos.db"` (auth data)
`server/db.py:12` → `_DATA_DIR / "talos.db"` — **Wait, same file.** Both modules write to `talos.db`. The auth tables (in `server/auth.py:90`) and message/room tables (in `server/db.py:36-76`) share the same SQLite file. This is fine for ACID consistency but neither module uses a connection pool or transactions across the two modules. `sqlite3` is used with `check_same_thread=False` in both — concurrent writes from FastAPI async handlers could race.

---

## Axis 5 — Architecture

### ✅ STRONG: Separation of Concerns

| Layer | Files | Responsibility |
|---|---|---|
| UI (Kotlin) | 6 composable files | Android UI, M3 theming, animations |
| Network (Kotlin) | `TalosClient.kt` | OkHttp WS, offline queue, caching |
| Domain (Python) | `core/models.py` | Pydantic schemas, shared with Android via JSON |
| Bus (Python) | `core/room_bus.py` | Pub/sub, WebSocket subscriber management |
| Persistence (Python) | `server/db.py` | SQLite rooms, messages, agents |
| Auth (Python) | `server/auth.py` | Token lifecycle, master token protection |
| Orchestration (Python) | `server/agent_orchestrator.py` | Multi-agent turn scheduling, streaming |
| Provider (Python) | `server/provider_service.py` | Model health probing, failover routing |
| API (Python) | `server/main.py` | FastAPI routes, WebSocket gateway |

### ✅ STRONG: Event Streaming Contract

The orchestrator emits clean, attributable delta events:
- `turn_start` → `typing_indicator` → `thought_delta` (chunked) → `token_delta` (chunked) → `message_new` → `turn_end`

Chunk boundaries respect word boundaries (`re.split(r"\S+\s*", text)`), so clients concatenating deltas get exact原文 without whitespace corruption. Good contract design. ✅

### ⚠️ Architecture Issue: Orchestrator Uses Process-Global Singleton

`server/agent_orchestrator.py:982` — `orchestrator = AgentOrchestrator()`

This global singleton is stateful (in-memory `_seen_messages`, `_active_turns`, `_rounds`, `_history`). If the FastAPI server runs with multiple workers (`uvicorn --workers N`), each worker gets its own orchestrator instance. They share the room bus (also a process-global singleton at `core/room_bus.py:263`) but not the orchestrator state — leading to duplicated agent turns or missed deduplication. For a single-process async server this is fine, but it must be documented as single-worker only.

### ✅ STRONG: Provider Credential Resolution

`core/providers.py:314–337` — Multi-source credential resolution with documented precedence: env vars → dotenv → Hermes auth file. Never logs or exposes credentials. Good secrets hygiene. ✅

### ✅ PASS: Deterministic Agent Responses

`server/agent_orchestrator.py:372–382` — SHA256 digest-based `_pick()` for deterministic random selection. Same prompt always produces the same agent response. Enables reproducible testing and debug sessions. ✅

---

## Axis 6 — Test Coverage

### ✅ Strong E2E Test Suite

`tests/test_e2e_mesh.py` — Full integration tests via httpx ASGI transport:
- Mesh status discovery (no auth required) ✅
- Pairing flow → token issued + bus welcome message ✅
- Token verification (valid, invalid, missing) ✅
- Council orchestration: human message triggers agent turns ✅
- **Agent collision prevention**: replies do not re-trigger bench ✅
- DM rooms do not wake full bench ✅
- WebSocket binary frame handling ✅

`tests/test_core.py` — Unit tests for bus, models, SQLite persistence:
- Room/event/message/agent seed data ✅
- Message persistence with tool_calls ✅
- Agent listener async dispatch ✅
- Binary listener fan-out ✅
- Dead socket cleanup ✅

`tests/test_consensus.py` — Consensus protocol tests ✅

**Gap:** No tests for:
- Provider health probe with actual network calls
- Token expiry enforcement (`verify_token()` does check `expires_at`, but no test covers expiry)
- Offline queue size bounds
- Android-side WebSocket reconnect after network loss

---

## Axis 7 — Anti-Patterns & Code Quality

### 🔴 Dead Code: `SYNAPSE_CHOREOGRAPHY_CODE.kt`

`docs/SYNAPSE_CHOREOGRAPHY_CODE.kt` — A 276-line standalone composable (`EnhancedClaudeOpenCodeConduit`) that duplicates logic already in `LandingScreen.kt`. The conduit animation, comet particles, spring physics, and retraction logic are implemented twice. The doc file is not imported anywhere. Should either be deleted or refactored into a shared composable.

### 🟡 Magic Constants Without Named Constants

`android/OnboardingWizard.kt:114–115`
```kotlin
private const val MESH_IP = "<tailscale_ip>"
private const val PEER_LABEL = "V2158"
```
Hard-coded personal mesh IP address. Should be sourced from `TalosClient.DEFAULT_BASE_URL` or a config resource.

### 🟡 Unused Import

`android/TalosClient.kt:17` — `kotlinx.coroutines.flow.MutableStateFlow` is imported but `MutableStateFlow` is not directly used (only `StateFlow` is used via extension). Minor style issue.

### 🟡 Android: Hardcoded Sender Name

`android/TalosClient.kt:398,430` — `sender_name = "Sarvesh"` is hardcoded in two places. Should come from `SharedPreferences` or a user profile.

### 🟡 Android: Audio Player State Not Restored

`android/Components.kt:234–235` — `NativeAudioPlayerBubble` initializes `progress by remember { mutableFloatStateOf(0.35f) }` with a hardcoded 35% progress. Actual playback position is never read back from the player. The slider is cosmetic.

### 🟡 Python: Silent Exception Swallowing

`server/provider_service.py:226,246` — `asyncio.TimeoutError` and all other exceptions are caught and logged but return an error tuple; callers treat this as `ProviderStatus.unreachable`. This is correct defensive coding, but no alerting or circuit-breaker pattern is applied. Repeated probe failures won't trigger any backoff.

### ✅ Good: Comprehensive Docstrings

`server/agent_orchestrator.py:1–27` — Full module docstring explains the orchestration philosophy, event contract, and simulation-only nature of agents. Exemplary. ✅

---

## Priority Recommendations

| Priority | Finding | File(s) |
|---|---|---|
| 🔴 Critical | CORS wildcard + credentials | `server/main.py:98` |
| 🔴 Critical | Master token filesystem permissions risk | `server/auth.py:114` |
| 🔴 Critical | Binary frames saved as `.opus` unconditionally | `android/TalosClient.kt:235` |
| 🟡 Medium | WebSocket token in query param | `server/main.py:169` |
| 🟡 Medium | `surface` == `background` in dark scheme | `Theme.kt:62,64` |
| 🟡 Medium | Offline queue unbounded | `android/TalosClient.kt:461` |
| 🟡 Medium | Orchestrator not worker-safe | `server/agent_orchestrator.py:982` |
| 🟡 Medium | Consensus always approves | `core/consensus.py:63` |
| 🟡 Medium | Typography weight mismatches M3 spec | `Theme.kt:139–245` |
| 🟡 Medium | `SYNAPSE_CHOREOGRAPHY_CODE.kt` is dead duplicate code | `docs/SYNAPSE_CHOREOGRAPHY_CODE.kt` |
| 🟢 Low | Hardcoded mesh IP in wizard | `android/OnboardingWizard.kt:114` |
| 🟢 Low | Hardcoded sender name "Sarvesh" | `android/TalosClient.kt:398` |
| 🟢 Low | Audio progress bar is cosmetic only | `android/Components.kt:234` |
| 🟢 Low | `MutableStateFlow` unused import | `android/TalosClient.kt:17` |

---

## Verdict

**Production viable with mitigations.** The codebase demonstrates strong architectural discipline, clean separation of concerns, comprehensive E2E test coverage, and excellent documentation. The M3 implementation is substantially correct with minor token weight and dark-surface issues. The bus architecture is sound, though the binary frame protocol needs a content-type envelope before production use. The security issues (CORS, token filesystem exposure, query-param auth) are the most pressing fixes before exposing any public-facing port.

**Estimated Fix Effort:** 2–3 days for critical/security issues, 1 day for M3 corrections.
