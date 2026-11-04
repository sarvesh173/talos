# HERMES_APK_DESIGN_BRIEF — Dynamic UI patterns for TALOS

Source: decompiled apktool output at `/tmp/hermes_extract/apk_out` (official Hermes Android app, `ai.nousresearch.hermes.mobile` per `AndroidManifest.xml` package attribute).
Rule: this is a design brief, not a port. No shader / JS / smali is reproduced; every claim cites a file path. Durations, sizes and shapes are paraphrased with parameters.
Files read: 69 distinct files (see end). No builds were run.

## Entry points: every route into the app

All entries land on the same chat surface (`MainActivity`) except where noted; TALOS mapping is a proposal.

| # | Route (from manifest) | TALOS feature it becomes |
|---|---|---|
| 1 | `MainActivity` singleTask, `adjustResize`, configChanges for keyboard/orientation/size (`AndroidManifest.xml`) — the sole chat host. Deep-link state via intent extras `hermes.entryPending` / `hermes.entryId` (`smali/ai/.../MainActivity.smali` ~lines 708/778/1230/1271) + `ACTION_VIEW` data strings | Single-activity chat root; pending-entry extras become a “resume draft” contract for cold starts |
| 2 | 7 launcher activity-aliases all targeting `MainActivity`, one enabled (`LauncherOriginal`), six disabled variants: `LauncherOrbM01/M02`, `LauncherOrbS01/S02/S03`, `LauncherDefault` with mipmaps `ic_launcher_orbm01`, `...orbm02`, `...orbs01-03`, `...default`, `ic_launcher` (`AndroidManifest.xml`) | TALOS “character icon” picker: one enabled alias + six alternates; switching = disable old alias, enable new, no new activity |
| 3 | Deep link scheme `hermesagent://...` (any host, `VIEW/DEFAULT/BROWSABLE` on `MainActivity`) (`AndroidManifest.xml`) | Custom TALOS deep links `talos://new-chat`, `talos://live-voice`, `talos://section?name=chat` |
| 4 | Deep link `hermes://connections/done` (`VIEW/DEFAULT/BROWSABLE`, host=`connections`, path=`/done`) (`AndroidManifest.xml`) — OAuth/device-connection return | TALOS account/device link callback `talos://connections/done` |
| 5 | App shortcuts `res/xml/shortcuts.xml`: `new_chat` → `hermesagent://new-chat` → `MainActivity`; `live_voice` → `hermesagent://live-voice` → `entry.LiveVoiceShortcut` alias → `MainActivity`; `sessions` → `hermesagent://section?name=chat` → `MainActivity`; plus `<share-target>` → `share.ShareActivity` for `*/*` | TALOS long-press shortcuts New Chat / Live Voice / Sessions + system share target |
| 6 | QS tiles (non-toggleable): `entry.LiveVoiceTileService` (`ic_shortcut_live_voice`, `@string/tile_live_voice`), `entry.NewChatTileService` (`ic_shortcut_new_chat`, `@string/tile_new_chat`), both `BIND_QUICK_SETTINGS_TILE`, `TOGGLEABLE_TILE=false` (`AndroidManifest.xml`; `smali_classes2/.../entry/LiveVoiceTileService.smali`, `NewChatTileService.smali` — 55-line stubs extending a shared tile base `Lx27`) | TALOS QS tiles Live Voice / New Chat (tap = launch, not toggle) |
| 7 | Home widget: receiver `entry.HmActionsWidget` (`APPWIDGET_UPDATE`, `@xml/widget_hm_actions`: min 180×80dp, resizeMode horizontal, home_screen, 3×1 cell, `updatePeriodMillis=0`) rendering via one static RemoteViews refresh on update/options-change (`smali/.../entry/HmActionsWidget.smali`) | TALOS 3×1 action widget; push-driven refresh, no periodic polling |
| 8 | Bot directory picker `entry.BotDirectoryActivity` (`excludeFromRecents`, empty taskAffinity): `GET_CONTENT` for `application/vnd.ai.nousresearch.hermes.mobile.bots+json`, `PICK` for `...bot+json` (`AndroidManifest.xml`; `smali_classes2/.../entry/BotDirectoryActivity.smali` with extras `...extra.INSTALLATION_ID`, `...extra.INCLUDE_HIDDEN`) | TALOS bot-installation picker intent (`bots+json`/`bot+json` MIME contract) |
| 9 | Share sheet `share.ShareActivity` (`SEND`/`SEND_MULTIPLE`, `*/*`, `excludeFromRecents`, `adjustResize`; saved-state key `hermes.share.state` with fields directory/phase/note/installation/profile/recipientName/search/removedText/removedFiles) (`AndroidManifest.xml`; `smali_classes2/.../share/ShareActivity.smali`) | TALOS share-intent handler with manual-text/file-removal phases |
| 10 | Firebase push: `HermesMobileMessagingService` (`MESSAGING_EVENT`, reads `gcm.n.*` fields title/body/icon/sound/tag/color/click_action/channel/link/image/ticker/priority/visibility/count/sticky/local_only etc., error string “Couldn't schedule a device wake”) (`AndroidManifest.xml`; `smali_classes2/.../HermesMobileMessagingService.smali`) + `NotificationActionReceiver` action `ai.nousresearch.hermes.mobile.PUSH_ACTION` (`smali_classes2/.../NotificationActionReceiver.smali`) + WorkManager `NotificationActionWorker` (receipt regex `(reply\|answer\|approval)-result-[0-9a-f]{64}`, input key `receipt_id`, channel `hermes.notification.action.v1` “Sending your response”) + `NotificationReceiptFallbackWorker` (same `receipt_id` key) + `PushSyncWorker` (CoroutineWorker delegating to an injected lambda, success→success-result else failure) | TALOS push pipeline: FCM → foreground-visible channel → action buttons → WorkManager receipt worker with fallback worker |
| 11 | Device wake: `DeviceWakeWorker` (CoroutineWorker; validates `[0-9a-f]{64}`, input keys `relay_install_id`/`receipt_id` family, channel `hermes.device.wake.v1` “Device Access — Shown briefly while Hermes Agent answers an agent's request in the background”, foreground text “Answering an agent's request”) (`smali_classes2/.../device/DeviceWakeWorker.smali`) | TALOS short-lived foreground “working in background” notification while agent executes |
| 12 | Alarms: `device.DeviceAlarmReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`; `goAsync` → coroutine) (`AndroidManifest.xml`; `smali/.../device/DeviceAlarmReceiver.smali`); backing manager uses `AlarmManager.setAlarmClock` with `AlarmClockInfo` + channel `hermes.device.alarms` / `device-alarms.v1`, exact-alarm check `canScheduleExactAlarms()` (grep `smali/vp.smali`, `smali/gm5.smali` `setExact`) | TALOS alarm restore-on-boot/time-change + exact-alarm permission tracking |
| 13 | Health data: `HealthDataSdkService` binding (`ACTION_BIND_SDK_SERVICE`), `device.DevicePrivacyActivity` (`ACTION_SHOW_PERMISSIONS_RATIONALE`) + alias `ViewPermissionUsageActivity` (`VIEW_PERMISSION_USAGE`/`HEALTH_PERMISSIONS`) (`AndroidManifest.xml`; `smali_classes2/.../device/DevicePrivacyActivity.smali`) | TALOS Health Connect permission-rationale + usage-view activities |
| 14 | ML Kit doc scanner `GmsDocumentScanningDelegateActivity` (portrait, black no-titlebar), Files `FileProvider` (`...files`, `@xml/preview_paths`), Firebase/ML Kit/Startup providers (`AndroidManifest.xml`) | TALOS scan-to-chat + file-preview sharing |
| 15 | PDF text `PdfTextRotation` (native lib tag `hm_pdf_text`) (`smali_classes2/.../PdfTextRotation.smali`; `lib/*/libhm_pdf_text.so`) | TALOS PDF orientation-corrected text extraction |

## The character shader: full parameter table

Source: `assets/character-fur.agsl` (236 lines, read in full). In plain words the shader draws a fur coat on top of an elliptical 2-D body image (`body`): it works in two regions — inside the silhouette it layers 11–12 shells from tip to root sampling two counter-rotated 3-D value-noise hair grids with Kajiya-Kay-style sheen, and outside the silhouette it adds a 3-layer fuzzy fringe plus sparse long flyaways, all shaded from the body colour at the root. Hash/noise helpers are 3-D value noise; two fixed grid rotations stop the lattice from aligning with the screen. Lighting is fixed (upper-left, slightly toward viewer). Density thins with distance (detail/edge-softness clamps) so far/small renders fall back to a smooth average coat. Missing native calls that feed these uniforms were not in the GLSL file (see Unverified).

| Uniform | Type | Range / observed default-ish | What it controls | TALOS agent-state mapping (proposed, with numbers) |
|---|---|---|---|---|
| `body` | shader (image) | the ellipsoid body, premultiplied RGBA | Root skin colour, silhouette, centre/radii basis | Body colour per TALOS theme (e.g. idle `#8D63E6` as in first onboarding preset, `unknown/characters/catalog.json`) |
| `center` | float2 | body centre in pixels | Ellipse origin | Recentre on resize; keep static per state |
| `radii` | float2 | body half-extents in body-units (pixels-per-unit space) | Ellipse size/shape; drives hair-cell count (`radius/(1.3·pixelsPerUnit)`, min ~20 cells) and fringe slot count (`2π·radius/0.5`, min 90) | Scale: idle 1.00, thinking 1.00, working 0.99 (squash), error 1.03, sleeping 0.97 |
| `hair` | float | ≥0; effective length `L = hair·amount`; early-out if `L<0.01` | Fur length in body-units | idle 0.55, thinking 0.55, working 0.45, error 0.70, sleeping 0.90 |
| `time` | float | ms-like clock; sway noise sampled at `time·0.0005` | Gentle sway bend (`bend = comb + tangent·sway·0.5`) | Idle sway normal; thinking/working sway ×0.5; sleeping sway ×0.2 (slow) |
| `seed` | float | any float (onboarding uses strings `"11"`…`"88"`, `catalog.json`) | Stable per-character hash salt; clump noise offset `u·7+seed`; fringe per-slot hashing | Fixed per TALOS agent id (e.g. hash of agent name); never animate |
| `amount` | float | ~0.65–0.89 in onboarding presets (`catalog.json` `speed`-adjacent; shader multiplies `hair·amount`) | Coat fullness/length multiplier | idle 0.71, thinking-low 0.77, thinking-high 0.83, working 0.89, error 0.89, sleeping 0.65 |
| `pixelsPerUnit` | float | density driver | Hair density on screen (fewer px/unit = bigger cells = more detail) | idle 1.0, working 1.0, error 1.0, sleeping 0.7 (coarser, cheaper) |
| `rotX`/`rotY`/`rotZ` | float3 (each a basis vector) | Inverse head rotation (3 basis vectors) | Body-space direction `u` under each pixel; `rotZ` yaw scrolls fringe slots | Idle slow yaw drift ±0.05 rad/s; thinking yaw sway ±0.10; working mostly locked; sleeping locked |
| `comb` | float2 | bend vector (screen-ish space); lifts/flattens fringe, leans strands, extends fringe reach `L·(1+\|comb\|)` | Wind/comb direction; fringe lean `bend·L`; `outsideDist` includes comb-side projection | idle `(0,0)`; thinking `(0.10,0)` alternate ± every 2.4 s (matches `ponderLeft/Right` hold); working `(0,0)`; error `(0.35,0)` spiky; sleeping `(0,-0.06)` droop |

Proposed TALOS state table (combines shader params + `unknown/characters/motions-v2.json` motion presets, which ship 12 named motions: `idle, listen, think, work, speak, curious, celebrate, love, oops, sleep, bounce, dance, spin` with per-step expression/hold/transition/easing):

| TALOS state | Shader (hair, amount, comb, time-rate) | Motion preset | Signature numbers (observed values used as anchor) |
|---|---|---|---|
| idle | hair 0.55, amount 0.71, comb 0, sway 1.0× | `idle` (breathe, micro-glances) | blink 190 ms every 3400–6200 ms (initial 2600); steps hold 1800–3800 ms, transition 700–900 ms, easing smooth |
| thinking (low effort) | hair 0.55, amount 0.77, comb ±0.06 | `think` (motionScale 0.8, effect dots) | blink 260 ms / 2800–5000 ms; ponder holds 2400–2800 ms, transition 600–650 ms |
| thinking (high effort) | hair 0.60, amount 0.83, comb ±0.10 | `think` + longer holds (proposed ×1.5) | transition 650 ms; blink interval upper bound 5000 ms |
| thinking (ultra) | hair 0.65, amount 0.89, comb ±0.14 | `think` variant (proposed) + scan expressions | eye micro-saccades on; blink 260 ms |
| working | hair 0.45, amount 0.89, comb 0, sway 0.5× | `work` (motionScale 0.6, double blink) | blink 150 ms double every 1800–3200 ms; read-step holds 380–480 ms, transition 170–190 ms, easing snappy; ends `perk` 700 ms/spring 240 ms |
| error | hair 0.70, amount 0.89, comb 0.35 | `oops` (body none, effect drop, hop false) | surprised 320 ms/130 ms snappy → wobble 1300 ms spin → reelLeft/Right 220 ms each → sorry 1600 ms/520 ms → neutral |
| sleeping | hair 0.90, amount 0.65, comb droop, sway 0.2× | `sleep` (blink disabled, motionScale 1.6, effect zzz) | sleep 4600 ms/1500 ms → sleepDrift 5000 ms/1500 ms |
| celebrate (success) | hair 0.60, amount 0.89 | `celebrate` (hop true, sparkles) | widePop stars 800/220 spring → topSpin 900 ms → beam |
| listen/voice | hair 0.55, amount 0.77 | `listen` (microSaccades, lift 1.5) | attentive 2600/420 → tiltCurious 2200/520 |

Expression vectors: `motions-v2.json` `expressions` holds 62 named 15-component vectors (angry … yawnWide); first four components read as head tilt/turn/roll + eye scale, last component is eye-shape flag. `eyeScales` maps 64 eye shapes (ball 1.16 … visor 1.5). `catalog.json` holds 20 body forms (bat … wisp, each with per-motion extents), 20 eye styles (bold … zombie), 7 toppers (bearEars, catEars, crown, devilHorns, halo, sprout, swoosh) and 8 onboarding presets (colour/form/eyes/seed/speed/topper).

## Animation inventory

### Observed (seen in files)

**Web bot-screen (`assets/bot-screen/index.html`)**
- Canvas resize: `width/height .18s ease-out` (`#screen canvas` transition). Communicates: smooth remote-resolution change.
- Trackpad collapse: `flex-basis/margin-top/opacity/border-color .3s ease` in portrait; landscape variant swaps `margin-top→margin-left .3s ease` and basis 34% (`@media (orientation: landscape) and (max-height: 520px)`). Communicates: view-only / keyboard-open = give space back to desktop.
- Zoom: `transform 130ms ease-out` when animated; pointer re-draw window `pointerAnimationUntil = now+170 ms` via rAF. Communicates: pinch settles without pointer lag.
- Pointer: 20×20 px ring + 4 px dot, translate(-50%,-50%), clamped 10 px from viewport edges.
- Gestures (no animation, but thresholds): screen tap = click if moved ≤7 px; trackpad tap = click if moved ≤14 px and <350 ms (two-finger = right-click mask 4); hold 450 ms without moving → drag (mask 1); click press→release gap 80 ms (some desktops drop same-frame pairs); sensitivity `0.85 × framebufferWidth/trackpadWidth`; zoom clamp 1–4× about focal point; pan clamp to scaled-canvas overflow; focus inset 36 px.
- H.264 probe guard: `VideoDecoder.flush()` raced against a 2000 ms stall timer that closes the decoder (works around noVNC issue on some Android 16 WebViews).

**Native `res/anim` (16 files) + `res/animator` (1) + `res/interpolator` (7) + `res/values/integers.xml`**
- Activity transitions (`abc_*`, AppCompat): grow/shrink-from-bottom scale 0.9↔1.0 pivot 50%/100% over `@integer/abc_config_activityDefaultDur` = 220 ms, fade over `...ShortDur` = 150 ms, interpolator decelerate. Popup enter/exit = fade 150 ms only.
- Material checkbox path morphs 300–333 ms with two custom path interpolators (sharp `l 1,0 l 0,1`; cubic `0.33,0 0,1`), fillAlpha flips 33 ms; outer-box scale 0.2↔0.18 over 200+300 ms; radio dot/ring scale + strokeWidth 2↔18 over 166/183 + 16 + 300–316 ms with `fast_out_slow_in` (cubic 0.4,0 0.2,1). Communicates: stock Material control state changes only — no bespoke app motion in XML.
- Button elevation animator (`mtrl_btn_state_list_anim`): `translationZ` over `@integer/mtrl_btn_anim_duration_ms` = 100 ms, delay 100 ms on rest state; pressed 100 ms, hovered/focused instant-ish. M3 motion tokens present: short1 50 ms, short3 150 ms, medium3 350 ms (`values/integers.xml`).
- Character motion system (`unknown/characters/motions-v2.json`): 12 motion presets; easings named `smooth/snappy/spring/wobble`; blink params per motion (190–260 ms, 150 ms double for `work`); sleep disables blink. This is the app’s real animation language; XML is stock.

**Shader motion (`assets/character-fur.agsl`)**
- Internal-shell fade uses `average = 1-(1-0.42·(1-t)^1.6)^2` (root→tip falloff); fringe root haze `0.9·(1-d/0.3)^1.6`; tip fade `smoothstep(1,0.8,d)`; Sheen `pow(sqrt(1-along²),48)·0.14–0.16·t`; shade range 0.95↔1.15 root→tip, jitter 0.96↔1.04. (Paraphrase of in-shader constants; the shader itself isn’t ported.)

### Inferred (not directly observed — needs runtime check)
- Splash theme `Theme.HermesMobile.Starting` on `MainActivity` likely animates the character in (no XML found) — inferred from theme name only (`AndroidManifest.xml`).
- Compose-side transitions (the app is Compose — `META-INF/androidx.compose.*` versions in `unknown/META-INF/`) are not in `res/anim`; durations above are XML floors, Compose motion may differ.
- Expression-vector component order (tilt/turn/roll/eye-scale/eye-shape) is inferred from value ranges + `eyeScales`/`eyeUnits` tables; the interpreter is native/Kotlin and was obfuscated (`Lx27`, `Lqgg` etc.).

## Bot screen: the noVNC+trackpad pattern

Sources: `assets/bot-screen/index.html` (466 lines) + `assets/bot-screen/native-socket.js` (58 lines) + `assets/bot-screen/novnc.js` (noVNC 1.7, 188 KB).

**Layout:** column: `#viewport` (flex 1, radius 16, dark `#171717`, `touch-action: none`) containing `#screen` (canvas) + `#pointer` ring; below `#trackpad` (flex-basis 220 px, glass gradient + 24 px blur/saturate, radius 16, handle bar + title + hint). Landscape ≤520 px height: row layout, trackpad 34% width beside desktop. Hidden 1×1 `#keyboardInput` (16 px font to suppress Android zoom) for soft-keyboard text injection. Trackpad starts `view-only` (basis 0, opacity 0).

**States:** `viewOnly` (take-over off; pointer hidden, RFB `viewOnly=true`, keyboard blurred) ↔ control (trackpad visible, keyboard focusable); `keyboard-open` collapses trackpad exactly like view-only. Native drives state via `window.hmScreen` API: `connect(url, isViewOnly)`, `setViewOnly(v)`, `showKeyboard()`, `setKeyboardVisible(v)`, `fitScreen()` (animated zoom reset), `disconnect(clean)`, `focus()`. `connect` uses `scaleViewport=true, resizeSession=false, shared=true, focusOnClick=true, background=transparent, qualityLevel=7`; pointer centred on connect.

**Bridge to native:** Web→native: `window.hmNotify(obj)` → `HmDesktop.postMessage(JSON.stringify(obj))`. Types: `ready` (on load), `socket-ready {id}`, `send {id, data:btoa(chunk)}`, `socket-closed {id}`, `connected {id}`, `disconnected {id, code, clean}`, `error {message?}` (also on `window error`/`unhandledrejection`). Native→web: `window.hmNativeReceive(id, base64) → bool`; rejects if unknown/closed socket or payload >87384 chars.

**Protocol (`native-socket.js`):** a `NativeSocket extends EventTarget` mimicking WebSocket (readyState 0→1, `binaryType='arraybuffer'`, `onopen/onmessage/onclose/onerror`). Only native opens network; each socket id is the URL. `send()` chunks outbound bytes into 65536-byte base64 pieces via `hmNotify {type:'send'}`. Inbound base64 is decoded to ArrayBuffer `MessageEvent`. RFB client is noVNC; local zoom/pan are CSS `translate+scale` on the canvas (not RFB resize).

**Keyboard:** hidden input + IME composition handling (`compositionstart/end`, `isComposing` guard); special keys mapped to keysyms (Backspace `FF08`, Tab `FF09`, Enter `FF0D`, Escape `FF1B`); other chars sent as `codepoint` or `0x01000000|codepoint` when >`FF`.

## Transcript schema: the Room schema

Source: generated Room impl `smali/qgg.smali` (4897 lines, CREATE/DROP/INDEX strings), DAO interface `smali/ai/.../data/TranscriptDatabase.smali` (single abstract accessor `r()` returning DAO `Lpgg`), wiring in `smali/k50.smali` (database file name `hermes-transcripts.db`), queries in `smali/dh.smali` (`SELECT * FROM transcript_owners WHERE installation = ?`) and `smali/hb4.smali` (INSERT statements). This is a data contract — adopt freely. WorkManager’s own tables (`WorkSpec`, `Dependency`, `WorkTag`, `WorkProgress` etc. in the same file) are **not** app data.

| Table | Columns (name, type, nullability) | PK / FK / Index |
|---|---|---|
| `transcripts` (entity `TranscriptRecord`) | `key TEXT NOT NULL`, `installation TEXT NOT NULL`, `profile TEXT NOT NULL`, `session TEXT NOT NULL`, `title TEXT NOT NULL`, `accessed INTEGER NOT NULL`, `chunkCount INTEGER NOT NULL` | PK `(key)`; index `index_transcripts_installation_accessed (installation, accessed)` — LRU listing per device |
| `transcript_chunks` (entity `TranscriptChunk`) | `key TEXT NOT NULL`, `position INTEGER NOT NULL`, `text TEXT NOT NULL` | PK `(key, position)`; FK `key → transcripts(key) ON DELETE CASCADE` — deleting a transcript deletes chunks |
| `transcript_owners` (entity `TranscriptOwner`) | `installation TEXT NOT NULL`, `revision TEXT NOT NULL`, `epoch TEXT NOT NULL` | PK `(installation)` — sync ownership/revision/epoch per device |
| `cached_bot_resolutions` | `installation TEXT NOT NULL`, `profile TEXT NOT NULL`, `session TEXT NOT NULL` | PK `(installation, profile)` — last-used bot per profile |
| `room_master_table` | `id INTEGER PK`, `identity_hash TEXT` | Room version hash (stock) |

Design notes: transcript body is stored as ordered text chunks keyed by `(key, position)` (enables append/streaming writes without rewriting); `chunkCount` denormalised for cheap existence checks; `accessed` backs LRU eviction; owners table separates device sync epoch from content. TALOS should adopt: same four tables, same PKs/FK, index on `(installation, accessed)`, single-file `talos-transcripts.db`.

## Native capabilities: what the permissions imply

Source: `AndroidManifest.xml` `uses-permission` block.

- **Calendar + contacts** (`READ/WRITE_CALENDAR`, `READ_CONTACTS`) → agent reads/inserts calendar events, resolves contact names.
- **Location** (`ACCESS_COARSE/FINE`) → location-aware replies/scheduling.
- **Health Connect** (36 permissions: steps/sleep/heart/exercise/distance, calories, HRV, VO2max, SpO2, BP, glucose, skin temp, weight/fat/lean mass, height, nutrition, hydration, mindfulness, power, speed + `READ_HEALTH_DATA_HISTORY`, `READ_HEALTH_DATA_IN_BACKGROUND`, `WRITE_WEIGHT/BODY_FAT/HYDRATION/NUTRITION/MINDFULNESS`) + `HealthDataSdkService` + privacy/rationale activities → health sync both directions, background reads gated by rationale.
- **Alarms/scheduling** (`SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`) + `DeviceAlarmReceiver` + `AlarmClockInfo`/`setExact` machinery (`smali/vp.smali`, `smali/gm5.smali`) → user reminders/alarms set from chat.
- **Biometrics** (`USE_BIOMETRIC`, `USE_FINGERPRINT`) → unlock/approve actions.
- **Audio/voice** (`RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`) + QS `LiveVoiceTileService` + `hermesagent://live-voice` → live voice mode.
- **Notifications** (`POST_NOTIFICATIONS`, `POST_PROMOTED_NOTIFICATIONS`) + FCM + `NotificationActionWorker/Fallback` + `DeviceWakeWorker` → reply/approve from notification, background execution foreground notice.
- **Network** (`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_LOCAL_NETWORK`) + `native-socket.js` byte channel → bot desktop streaming (VNC/WebRTC via `libjingle_peerconnection_so.so`), local-device access.
- **Foreground service** + WorkManager (`SystemJobService`, `SystemForegroundService`) → scheduled sync/wake work.
- **Document pipeline**: ML Kit scanner + PDFium (`libpdfium*.so`, `libhm_pdf_text.so`) + `PdfTextRotation` → scan/PDF attachments OCR’d into chat.

TALOS should adopt: calendar/contacts/location tools, Health Connect background sync with rationale, exact-alarm reminders with boot-restore, biometric approval gate, live-voice tile + shortcut, notification reply/approve + foreground “working” notice, scanner→PDF text path. All behind explicit runtime grants (the Health rationale + `VIEW_PERMISSION_USAGE` pattern is the model).

## Porting queue: ordered items with acceptance criteria

1. **Chat root + deep links** — Single-activity host, `VIEW` for `hermesagent://` and `hermes://connections/done`, extras `entryPending/entryId`, cold-start resume. Accept: all 5 shortcut URLs open correct section; `connections/done` returns to chat post-link; process-death resume shows pending draft.
2. **Launcher variants** — 7 aliases, one enabled; in-app icon picker toggles enable/disable. Accept: exactly one enabled at all times; chooser survives reboot; shortcuts re-register per alias.
3. **QS tiles + shortcuts + widget** — Live Voice/New Chat tiles (non-toggleable), 3 shortcuts + share target, 3×1 widget with push refresh. Accept: tile tap → live-voice/new-chat; widget updates only on data change (`updatePeriodMillis=0`).
4. **Fur character engine** — Fresh implementation of the 12-uniform fur shader (inner shells + fringe + flyaways), driven by presets in §The character shader. Accept: 60 fps on a mid device at 1080p with default radii; `amount=0` renders body only; `comb` steers fringe without swimming.
5. **Motion system** — 12 presets, 62 expressions, blink scheduler, 8 onboarding presets, 20 forms / 20 eye styles / 7 toppers from `catalog.json`/`motions-v2.json` (re-authored, not copied). Accept: `idle/think/work/sleep/oops/celebrate` visually distinct in a blind A/B; blink rates match table.
6. **Bot screen** — WebView noVNC viewport + glass trackpad, landscape rule, keyboard collapse, NativeSocket bridge (`hmNotify`/`HmDesktop`/`hmNativeReceive`). Accept: tap/hold/pinch/zoom thresholds as listed; 65 536-byte chunking + 87 384-byte inbound cap honoured; 2000 ms H.264 flush guard present.
7. **Transcript store** — Four tables + LRU index per §Transcript schema. Accept: cascade delete removes chunks; LRU order follows `accessed`; corrupt chunk insert aborts (`INSERT OR ABORT`).
8. **Push + action receipts** — FCM service, notification actions, `NotificationActionWorker` + fallback, `PushSyncWorker`. Accept: reply/answer/approval receipts validated by 64-hex regex; duplicate receipts are idempotent; offline actions retry via fallback worker.
9. **Device wake + alarms** — Foreground `DeviceWakeWorker` notice + exact-alarm restore receiver. Accept: alarm survives reboot/timezone change; exact-alarm revoke pauses scheduling with user notice.
10. **Device tools** — Calendar/contacts/location, Health Connect (with rationale + background read), biometrics. Accept: each tool gated; Health background read shows rationale; write health only with its write scopes.
11. **Share + bot directory + scanner/PDF** — Share target with phased send, bot picker MIMEs, ML Kit scan → PDF text. Accept: share handles `*/*`; picker returns `bot+json`; scanned PDFs land as chat text with orientation fixed.
12. **Emoji + highlight + fonts** — 8 emoji JSON packs (Activities 85, Animals 152, Emotions 529, Flags 269, Food 133, Items 261, Symbols 223, Places 218 = 1 870 entries, `assets/emoji/*.json`), highlight.js 52 registered languages (`assets/highlight/highlight.min.js`: applescript…yaml), KaTeX fonts (21 TTFs, `assets/fonts/`). Accept: emoji picker renders all packs; every registered language highlights without changing source text; math renders in bundled KaTeX face.

## What you could not verify

- No Kotlin/Compose source, no native code (only smali + `.so` names); all `Lx27`/`Lqgg`/`Lpgg`/`Lvp` etc. obfuscated identities beyond strings are **unverified**.
- Shader uniform *default values* and per-frame update rates are not in the `.agsl` file; ranges in the table are inferred from in-shader arithmetic. The native caller that sets `center/radii/rot*/comb` was not located.
- `MainActivity` deep-link *path* handling beyond the two intent-filters (e.g. which `hermesagent://` hosts map to which section) is string-level only; exact routing logic is inside obfuscated composables.
- QS tile activation side-effects (the 55-line tile stubs delegate to a shared base class) and `HmActionsWidget` RemoteViews content (layout file `res/layout/widget_hm_actions` not opened) are **unverified**.
- `PushSyncWorker`’s lambda target and exact WorkManager constraints/backoff are obfuscated (`Ljp5`/`Lud4`/`Loyc`); only the success/failure result mapping was read.
- `novnc.js` is third-party noVNC 1.7; only the app’s usage in `index.html` (RFB options, keysyms, scale behaviour) is asserted.
- Splash/Compose animations, character render threading, and WebRTC (`libjingle_peerconnection_so.so`) usage were not traced.

## Files read (69)

`AndroidManifest.xml`, `res/xml/shortcuts.xml`, `res/xml/widget_hm_actions.xml`, `res/values/integers.xml`, `assets/character-fur.agsl`, `assets/bot-screen/index.html`, `assets/bot-screen/native-socket.js`, `assets/bot-screen/novnc.js` (skimmed for version/options), `assets/highlight/highlight.min.js` (language list), `assets/highlight/tokens.js`, `assets/emoji/{activities,animalsAndNature,emotionsAndPeople,flags,foodAndDrinks,items,symbols,travellingAndPlaces}.json` (8), `res/anim/*.xml` (16), `res/animator/mtrl_btn_state_list_anim.xml`, `res/interpolator/*.xml` (7), `smali/ai/.../MainActivity.smali`, `HermesMobileApplication.smali`, `PushSyncWorker.smali`, `data/TranscriptDatabase.smali`, `data/TranscriptDatabase_Impl.smali`, `device/DeviceAlarmReceiver.smali`, `entry/HmActionsWidget.smali` (7), `smali_classes2/ai/.../entry/NewChatTileService.smali`, `entry/LiveVoiceTileService.smali`, `entry/BotDirectoryActivity.smali`, `device/DeviceWakeWorker.smali`, `device/DevicePrivacyActivity.smali`, `NotificationActionWorker.smali`, `NotificationActionReceiver.smali`, `NotificationReceiptFallbackWorker.smali`, `HermesMobileMessagingService.smali`, `PdfTextRotation.smali`, `share/ShareActivity.smali` (11), `smali/qgg.smali`, `smali/k50.smali`, `smali/hb4.smali`, `smali/dh.smali`, `smali/vp.smali`, `smali/gm5.smali` (grep-level, 6), `unknown/characters/catalog.json`, `unknown/characters/motions-v2.json`. 18 smali under `ai/nousresearch/hermes/mobile/` exist in total (7 in `smali/`, 11 in `smali_classes2/`); all were opened or grepped. Unread/bulk: 20 000+ stock smali, 400+ `res/drawable*` (only anim-relevant names listed), binary `.so`/`.ttf`/`.prof` (names only).
