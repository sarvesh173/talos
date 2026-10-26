# Android Multi-Window / Split-Screen OEM Compatibility Matrix

Comprehensive reference matrix for split-screen and multi-window behavior across major Android OEMs for **TALOS** (Kotlin + Jetpack Compose).

---

## 1. Samsung One UI (One UI 6 / 7 / 8)

| Field | Specification & Behavior |
| :--- | :--- |
| **OEM & OS** | Samsung Galaxy (One UI 6.x / 7.x / 8.x, Android 14/15/16) |
| **How split-screen is invoked** | 1. **Recents Screen**: Tap app icon at the top of the app thumbnail -> "Open in split screen view".<br>2. **Edge Panel (Apps Edge)**: Drag an app icon from the edge tray and drop to top/bottom/left/right halves.<br>3. **Two-Finger Gesture**: Two fingers swipe up from bottom edge (Labs feature).<br>4. **Taskbar**: Drag app icon from persistent taskbar (Fold/Tablet).<br>5. **Pop-up View**: Can transition between Pop-up (freeform) and Split-screen via top window pill handle. |
| **Window sizing (ratios, min width/height)** | - **Ratios**: Strict snapping to **50:50**, **70:30** (~2/3:1/3), and **30:70** (~1/3:2/3) in vertical split; folds/tablets support 3-way split (50:25:25 or equal thirds).<br>- **Minimum Size**: Enforces default minimum dimensions (~220dp width in multi-split, down to ~150-200dp height in 30:70). On phones, cannot be resized narrower than ~1/3.<br>- Custom layouts can be forced via Samsung Good Lock (MultiStar). |
| **App-side pitfalls** | - **Letterboxing & Compat Mode**: If `resizeableActivity="false"` or orientation is fixed (e.g., `screenOrientation="portrait"`), One UI wraps the app in a letterboxed sandbox with restart warning prompts unless Good Lock "Multi Window for all apps" overrides it.<br>- **Orientation Lock**: Fixed orientation activities get letterboxed or blocked from native split.<br>- **Insets**: Edge-to-edge insets abruptly change when docking/undocking; IME (software keyboard) pushes the small pane heavily, causing layout collapse if minimum height isn't guarded.<br>- **Background Process / Multi-Resume**: Samsung fully supports Multi-Resume (Android 10+), both apps remain `RESUMED` while visible. However, Samsung aggressively pauses video/render timers or throttles background activities if RAM pressure triggers. |
| **Mitigations for TALOS** | 1. Declare `android:resizeableActivity="true"` in AndroidManifest.<br>2. Support dynamic configuration changes: `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|density"`.<br>3. In Jetpack Compose, avoid fixed `Modifier.height(...)` or hardcoded button columns; use `Modifier.verticalScroll(rememberScrollState())` inside panes.<br>4. Consume `WindowInsets.safeDrawing` or `safeContent` adaptively without assuming status bar is pinned to the top of the pane.<br>5. Do not tie audio/AI streaming purely to `onPause()`—rely on `onStop()` / process visibility. |
| **Source URL** | https://developer.samsung.com/galaxy-z/multi-window.html |
| **Status** | **Documented** (Official Samsung Developer Guidelines & One UI multi-window specs) |

---

## 2. Vivo OriginOS & Funtouch OS

| Field | Specification & Behavior |
| :--- | :--- |
| **OEM & OS** | Vivo & iQOO (OriginOS 3 / 4 / 5 and Funtouch OS 13 / 14 / 15, Android 13/14/15) |
| **How split-screen is invoked** | 1. **Three-finger swipe up**: Swipe up from middle of screen with three fingers.<br>2. **Recents (Multitasking) Menu**: Tap app dropdown/dots on card -> "Split-screen" (分屏).<br>3. **Smart Sidebar**: Drag an app out from the edge sidebar onto the current screen.<br>4. **Top Window Handle**: Tap the top `...` multitask bar on supported windows to switch between Fullscreen, Small Window, and Split.<br>5. **Message floating icon**: Tapping an incoming messaging head in landscape/fullscreen prompts a split-screen view. |
| **Window sizing (ratios, min width/height)** | - **Ratios**: On slab phones, supports continuous / stepless ratio dragging within a clamped range (typically **50:50**, bounded between roughly **70:30** and **30:70**). When dragged beyond the limit, it snaps to full screen or collapses into a floating mini-icon/window.<br>- **Folds / Tablets (OriginOS Fold / Pad)**: Supports "Parallel Mode" (原子工作台 / 多分屏) up to 4 concurrent window panes, with stepless corner drag (`┛`).<br>- **Minimum size**: Phone split pane minimum height is roughly 200-240dp. |
| **App-side pitfalls** | - **Status Bar Inset Trap**: On the lower/secondary split pane or small window mode (`WINDOWING_MODE_SPLIT_SCREEN_SECONDARY` or freeform), **there is NO system status bar at the top**. If the app blindly pads `WindowInsets.statusBars`, it creates dead empty whitespace at the top of the pane.<br>- **Top Handle Collision**: In floating mini-window mode, OriginOS renders a top system functional control bar that will obscure unpadded custom app header buttons.<br>- **Display Metrics vs Activity Metrics**: Vivo HIG explicitly notes that querying `getDefaultDisplay().getMetrics()` or application context yields screen-level bounds instead of the docked window stack; developers must use Activity Context or Compose LocalConfiguration.<br>- **Aggressive Background Freezing**: Vivo uses strict system battery optimization engines (Jovi / Multi-Turbo). If an app pane loses user interaction focus for an extended period in split screen or transitions partially, timers and background threads are aggressively throttled unless flagged as active foreground tasks. |
| **Mitigations for TALOS** | 1. Use Compose `WindowInsets.safeDrawing` or query `WindowInsetsCompat.getInsets(Type.systemBars())` which automatically reports 0 status bar top inset when docked in the secondary split pane.<br>2. Never hardcode status bar heights or calculate layout based on `DisplayMetrics.widthPixels / heightPixels`; always derive sizes from Composable `BoxWithConstraints` or `LocalConfiguration.current`.<br>3. Add top padding to top app bars when in floating window mode so system drag pill does not overlap navigation icons.<br>4. Maintain active coroutine scopes bound to Compose lifecycle and ensure ongoing audio/socket tasks use foreground services with notifications. |
| **Source URL** | https://dev.vivo.com.cn/documentCenter/doc/573 and https://developers.vivo.com/hig/837d281c8904491ea8ba00ab023dd9ce |
| **Status** | **Documented** (Official Vivo Open Platform Developer HIG & Split Screen Adaptation Guide) |

---

## 3. Xiaomi HyperOS & MIUI

| Field | Specification & Behavior |
| :--- | :--- |
| **OEM & OS** | Xiaomi, Redmi, POCO (MIUI 14, HyperOS 1.0 / 2.0, Android 13/14/15) |
| **How split-screen is invoked** | 1. **Recents Screen**: Long-press on app thumbnail card -> tap Split-Screen icon (two horizontal bars). On HyperOS, also available via top app title context menu in Recents.<br>2. **Floating Windows Sidebar (Side toolbox)**: Swipe sidebar handle out and drag/tap to split or open mini-window.<br>3. **Three-finger swipe down / gesture**: On select HyperOS builds, quick gestures convert active app to floating window / split.<br>4. **Fold / Pad Top Multitask Menu**: Three dots (`...`) at top of screen allows tapping split left/right. |
| **Window sizing (ratios, min width/height)** | - **Ratios**: Strict 3-step or stepped slider on standard phones: **50:50**, **~67:33** (2/3:1/3), and **~33:67** (1/3:2/3). Snaps automatically when let go near bounds.<br>- **Narrowness Limit**: On standard phones, pane width cannot be narrower than full screen width in vertical split; in horizontal split or foldables, pane width cannot be reduced narrower than 1/3 (~300-330dp minimum width / 220dp minimum height).<br>- HyperOS on Fold/Pad supports multi-gear width breakpoints (Compact <600dp, Medium 600-840dp, Expanded ≥840dp). |
| **App-side pitfalls** | - **Strict `resizeableActivity` Check**: Xiaomi aggressively disables the split-screen option in Recents if `android:resizeableActivity="false"` OR if fixed orientation (`portrait`/`landscape`) is set, unless "Force activities to be resizable" is explicitly enabled in MIUI Developer Options.<br>- **Activity Lifecycle & Freezing**: In older MIUI / HyperOS implementations, Xiaomi's battery engine (`joyose` / `mi_thermald`) treats the non-focused split-pane with extreme aggression. When the user interacts with Pane A, Pane B may receive `onPause()` (prior to Android 10 Multi-Resume) or remain `RESUMED` but have UI renders and background coroutines throttled or frozen if memory is constrained.<br>- **Configuration Change Flicker / Restart**: If `configChanges` lacks `smallestScreenSize|screenSize|screenLayout`, HyperOS immediately restarts the Activity on divider drag, resetting state and text inputs.<br>- **Keyboard (IME) Overlap**: In vertical split, opening the software keyboard inside the bottom pane shrinks available height down to ~120-150dp; fixed-height non-scrollable layouts immediately overflow or crash. |
| **Mitigations for TALOS** | 1. Explicitly declare `android:resizeableActivity="true"` in both `<application>` and `<activity>`.<br>2. Support app continuity with `<meta-data android:name="miui.supportAppContinuity" android:value="true" />` and `<meta-data android:name="android.supports_size_changes" android:value="true" />`.<br>3. Handle config changes in manifest: `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|density"`.<br>4. Do NOT halt real-time background agent processing or WebSocket/SSE connections inside `onPause()`; rely on `onStop()` or run inside an explicit Foreground Service.<br>5. Never use non-scrollable vertical columns for form inputs or chat views. |
| **Source URL** | https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1592 and https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1484 |
| **Status** | **Documented** (Official Xiaomi HyperOS Developer Documentation on Split-Screen & Large Screens) |

---

## 4. OnePlus / OPPO / Realme (OxygenOS, ColorOS, realme UI)

| Field | Specification & Behavior |
| :--- | :--- |
| **OEM & OS** | OnePlus, OPPO, Realme (Unified ColorOS base: OxygenOS 13/14/15, ColorOS 13/14/15, realme UI 4/5/6, Android 13/14/15) |
| **How split-screen is invoked** | 1. **Three-finger swipe up**: Swipe upwards from the middle of the screen with 3 fingers.<br>2. **Recents Task Screen**: Tap 3 dots (or 2 dots) in upper-right corner of the task card -> Select "Split screen" (分屏).<br>3. **Smart Sidebar**: Touch and hold an app from the edge Smart Sidebar and drag it into the top/bottom or left/right half.<br>4. **Open Canvas (OxygenOS 15 / Foldables)**: Apps can slide off-canvas into virtual overflow panes while retaining near full size.<br>5. **OPPO WMUnit API**: Custom in-app split-screen SDK (`MultiWindowTrigger`) on ColorOS tablets and foldables. |
| **Window sizing (ratios, min width/height)** | - **Traditional Split Ratios**: Snaps to **50:50**, **~70:30** (~2/3:1/3), and **~30:70** (~1/3:2/3). Dragging past the outer threshold snaps the app to full screen or closes the secondary app.<br>- **Open Canvas (Foldables & OxygenOS 15)**: Allows running 2 to 3 apps side-by-side or stacked where one app remains ~90% visible while the other peeks/overflows on the border, switching view on tap.<br>- **Minimum size**: Enforces a strict minimum height threshold of ~220dp; will not allow vertical splits below ~30% of display height on phones. |
| **App-side pitfalls** | - **Quick Return / Floating Window Interop**: ColorOS allows converting Split-Screen sessions directly into Floating Windows (Flexible Windows / Mini-window). During this transition, window size changes dramatically within a single frame, triggering rapid configuration change callbacks.<br>- **Aggressive Memory Management (RAM Expansion / Quick Freeze)**: OPPO/OnePlus utilizes aggressive process management (ColorOS "Trinity Engine" / CPU & RAM Vitamization). An app in the unfocused split pane that does not hold an active Audio Focus or active Foreground Service notification can be suspended or frozen after short idle periods, killing background WebSockets/polling.<br>- **Orientation and Fixed Bounds**: Activities declaring `android:screenOrientation="portrait"` are either forced into letterbox compat mode with black sidebars or prevented from opening in split view unless overridden in system labs.<br>- **Window Inset Discrepancies**: Top pane gets status bar inset; bottom pane gets 0dp top status bar inset but gets the navigation bar and IME keyboard insets. |
| **Mitigations for TALOS** | 1. Set `android:resizeableActivity="true"` and never restrict `screenOrientation` on resizable activities.<br>2. Support immediate, smooth resizing without Activity recreation using `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|density"`.<br>3. Keep agent background tasks and network listeners alive in an explicit Android Foreground Service (`Service.startForeground()`) with `FOREGROUND_SERVICE_DATA_SYNC` / `SPECIAL_USE` to survive Trinity Engine background freezing.<br>4. Guard Compose layouts against rapid height shifts: wrap all toolbars and detail content in scroll containers (`verticalScroll`) and compute sizes using `BoxWithConstraints` rather than global display metrics. |
| **Source URL** | https://github.com/OPPO-OpenPlatform/wmunit_demo and https://www.androidauthority.com/oxygen-os-15-oneplus-open-canvas-wider-availability-3493192/ |
| **Status** | **Documented** (Official OPPO Open Platform WMUnit architecture & OxygenOS 14/15 Open Canvas specifications) |

---

## 5. Google Pixel (Stock Android 14 / 15 / 16)

| Field | Specification & Behavior |
| :--- | :--- |
| **OEM & OS** | Google Pixel (AOSP / Stock Android 14, 15, and 16 preview) |
| **How split-screen is invoked** | 1. **Overview / Recents Screen**: Swipe up and hold to open Overview -> Tap app icon above the task card -> Tap "Split screen" (or "Split top/left") -> Select second app from recents or app drawer.<br>2. **Taskbar (Pixel Fold & Pixel Tablet)**: Long press / drag an app from the persistent dock/taskbar to the left/right/top/bottom screen edge.<br>3. **App Pairs**: Saved split-screen icon pairs on home screen launched together (introduced in Android 14/15). |
| **Window sizing (ratios, min width/height)** | - **Ratios**: Snaps strictly to **50:50**, **~67:33** (2/3:1/3), and **~33:67** (1/3:2/3) via center divider bar on phone portraits.<br>- **Minimum size**: Default Android WindowManager enforces a minimum width/height clamp (default ~220dp, defined by `defaultMinimalSize`). An app cannot be resized narrower than 1/3 of the display.<br>- **Large screen / Foldables**: On Pixel Fold inner display, supports 50:50 side-by-side or app pairing with fluid divider. |
| **Window & Compat mode triggers** | - **Letterboxing & SizeCompatMode**: On Android 12+, if `resizeableActivity="false"` on small screens (<600dp sw), split-screen is blocked entirely. On large screens (≥600dp sw), AOSP forces the app into split-screen but letterboxes it in **SizeCompatMode** (with rounded corners, blurred wallpaper, and restart buttons).<br>- **Android 16 Overrides**: Android 16 (API level 36) forcefully ignores `screenOrientation`, `maxAspectRatio`, and `resizeableActivity="false"` on displays with `sw >= 600dp`, stretching apps or letterboxing based on user aspect preferences.<br>- **Orientation lock**: If `screenOrientation="portrait"` is set, rotating the device while split either breaks split-screen or enters heavy letterbox padding. |
| **Insets & Process Lifecycle** | - **Insets**: Fully edge-to-edge compliant. Top pane receives `WindowInsets.Type.statusBars()`; bottom pane receives 0 status bar inset, but consumes `WindowInsets.Type.navigationBars()` and `WindowInsets.Type.ime()`.<br>- **Multi-Resume**: Standard Android 10+ Multi-Resume. Both activities stay in `RESUMED` state simultaneously. Unfocused activity does NOT receive `onPause()` simply because the user tapped the other pane.<br>- **Process Kills**: Pure AOSP low-memory killer (LMKD) applies standard OOM adj scores. Visible split panes are flagged as `VISIBLE_APP` / foreground priority and are never killed before cached/background apps. |
| **Mitigations for TALOS** | 1. Declare `android:resizeableActivity="true"` in `<application>`.<br>2. Omit `android:screenOrientation` and `android:maxAspectRatio` / `android:minAspectRatio` from manifest.<br>3. Handle config changes smoothly: `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|density"`.<br>4. Always query insets through Jetpack Compose `WindowInsets.safeDrawing` or `safeContent` to gracefully handle 0dp status bar in secondary pane.<br>5. Never rely on `onPause()` to pause real-time operations—use `onStop()` when the Activity is no longer visible. |
| **Source URL** | https://developer.android.com/develop/ui/views/layout/support-multi-window-mode and https://developer.android.com/guide/practices/device-compatibility-mode |
| **Status** | **Documented** (Official Android Developers Reference & AOSP Large Screen Compatibility Guide) |

---

## Baseline Rules that Apply to Every Vendor

These foundational requirements guarantee that TALOS renders without crashes, letterboxing, or unexpected restarts across Samsung, Vivo, Xiaomi, OnePlus/OPPO, and Google Pixel:

1. **Explicitly declare `android:resizeableActivity="true"`**:
   Define `android:resizeableActivity="true"` at the `<application>` level in `AndroidManifest.xml`. If omitted or set to false, OEMs will either ban the app from split-screen invocation (Xiaomi/Pixel) or force it into legacy letterbox compatibility mode (Samsung/Vivo).
2. **Never restrict `android:maxAspectRatio` or `android:minAspectRatio`**:
   Setting aspect ratio caps automatically flags the activity as non-resizable on modern OEM window managers, triggering black bars, letterboxing, and resize restrictions.
3. **Handle configuration changes without activity restarts**:
   Declare `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|density"` on all Activities. When a user drags the split-screen divider or docks/undocks the app, Android will update configuration via callback rather than destroying and recreating the Compose Activity.
4. **Adopt adaptive insets (`WindowInsets.safeDrawing`) instead of hardcoding status bar padding**:
   In split-screen, the bottom/secondary pane or floating window has **0dp top status bar inset**. Never hardcode system bar heights or assume the status bar is at the top of the app window. Use Compose's `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` or `safeContent`.
5. **Avoid fixed-height layouts; make all containers scrollable**:
   Split-screen vertically halves the screen height (down to ~200-300dp), and opening an on-screen keyboard shrinks it to <150dp. Never use fixed heights for content containers or root columns; always wrap panels in `Modifier.verticalScroll(rememberScrollState())` or lazy lists (`LazyColumn`).
6. **Decouple business logic / agent execution from `onPause()`**:
   In multi-window mode, unfocused panes may receive pause events on older Android implementations or aggressive skins. Rely on `onStop()` (when completely invisible) for suspending UI rendering, and run active agent tasks, WebSockets, and audio streaming within a designated Foreground Service.
7. **Query window bounds via Activity Context or Compose `LocalConfiguration` / `BoxWithConstraints`**:
   Never use `ApplicationContext` or `DisplayMetrics.widthPixels / heightPixels` to calculate layout sizes, as OEMs return full physical display dimensions instead of the docked multi-window bounds.

---

## Vendor-Specific Tweaks We Will Implement

Ordered list of concrete platform adaptations for TALOS:

1. **Xiaomi HyperOS / MIUI App Continuity Metadata**:
   Add `<meta-data android:name="miui.supportAppContinuity" android:value="true" />` and `<meta-data android:name="android.supports_size_changes" android:value="true" />` in `AndroidManifest.xml` to prevent MIUI's aggressive relaunch on divider adjust and foldable un/fold.
2. **OPPO / OnePlus / Realme Foreground Service Flagging**:
   To prevent ColorOS's Trinity Engine from freezing agent background tasks when the user interacts with the opposite split pane, run real-time background workflows in a Foreground Service with `foregroundServiceType="specialUse"` or `"dataSync"`.
3. **Vivo OriginOS Secondary Split Status Bar Guard**:
   Ensure top app bars dynamically inspect `WindowInsets.statusBars.asPaddingValues().calculateTopPadding()`. When placed in Vivo's lower split pane (where top inset is 0), collapse top bar padding to 0dp so headers are not artificially pushed downward.
4. **Samsung One UI / Good Lock MultiStar Resilience**:
   Ensure state persistence via Compose `rememberSaveable` and ViewModel state stores so that when users pop TALOS between split-screen, Pop-up view, and full screen via Samsung Edge Panels, no user prompt or agent execution state is lost.
5. **Floating Window / Mini-Window Top Pill Padding**:
   Add adaptive top padding buffer (~24-32dp) when window bounds detect a floating/freeform windowing mode (Vivo mini-window, Xiaomi floating window, ColorOS flexible window) so OEM control pills do not obscure header action buttons.



