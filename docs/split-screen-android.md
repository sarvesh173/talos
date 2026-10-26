# Android Multi-Window & Split-Screen Engineering Guide (Android 12L – 16)

This document specifies the concrete requirements, architectural considerations, and implementation patterns for running the TALOS Android application reliably across split-screen, multi-window, and freeform modes on OEM devices (Samsung One UI, Xiaomi HyperOS, Vivo OriginOS/FuntouchOS, OnePlus OxygenOS, and Google Pixel).

---

## 1. Manifest Requirements for Multi-Window & Split-Screen

### Target SDK & Platform Baseline
- **Documented:** Apps targeting API level 24 (Android 7.0 Nougat) or higher default to supporting multi-window mode (`android:resizeableActivity="true"` by default).
- **Documented:** Apps targeting API level 31 (Android 12) or higher run in multi-window mode by default on large screens (`sw >= 600dp`) regardless of manifest configurations.
- **Documented (Android 16 / API 36+):** On displays with smallest width `sw >= 600dp`, Android ignores `android:resizeableActivity`, `android:screenOrientation`, `android:minAspectRatio`, and `android:maxAspectRatio`.
- *Reference:* [Android Developers - Support multi-window mode](https://developer.android.com/develop/ui/views/layout/support-multi-window-mode)
- *Reference:* [Android Developers - App orientation, aspect ratio, and resizability](https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability)

### Manifest Attributes Analysis

| Attribute | Behavior in Split-Screen | Required for Target SDK 24+? | Best Practice in TALOS |
| :--- | :--- | :--- | :--- |
| `android:resizeableActivity` | When `true`, enables split-screen, multi-window, and desktop windowing. When `false`, restricts split-screen on small screens (`sw < 600dp`) and triggers letterboxing/size-compat mode on large screens (`sw >= 600dp`). | **No** (defaults to `true` on SDK 24+), but **explicitly declaring `true`** prevents OEM manifest parser regressions (e.g., Xiaomi MIUI/HyperOS, Vivo OriginOS). | Declare `android:resizeableActivity="true"` on `<application>` or `<activity>`. |
| `android:configChanges` | Determines whether the Activity survives window resize without destruction and recreation. | **No**, but without `screenSize\|smallestScreenSize\|screenLayout\|orientation`, dragging the split-screen divider destroys and recreates the Activity repeatedly. | Declare `orientation\|screenSize\|smallestScreenSize\|screenLayout\|uiMode\|keyboardHidden\|density`. |
| `android:maxAspectRatio` | Caps the display aspect ratio. If the window aspect ratio exceeds this, the platform letterboxes the app. | **Must NOT be set** for resizable apps. Ignored if `resizeableActivity="true"`. | **Omit completely**. |
| `android:minAspectRatio` | Enforces a minimum aspect ratio. Causes letterboxing if window is wider/squarer than specified. | **Must NOT be set** for resizable apps. Ignored if `resizeableActivity="true"`. | **Omit completely**. |
| `android:screenOrientation` | Locks orientation (`portrait`, `landscape`). In split-screen on small screens, can prevent entering split-screen or force letterboxing. | Avoid fixed orientations for multi-window. | Omit or use `unspecified` / `fullSensor`. |

#### Why `smallestScreenSize` and `screenLayout` are Crucial
In `android/app/src/main/AndroidManifest.xml`, TALOS currently declares:
```xml
android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
```
**Deficiency identified:** `smallestScreenSize` is missing.
- When an app transitions into split-screen mode or when the divider is dragged across the 600dp threshold, the system updates `Configuration.smallestScreenWidthDp`.
- Without `smallestScreenSize` in `android:configChanges`, Android triggers a complete Activity teardown and recreation whenever `smallestScreenWidthDp` changes.
- In addition, omitting `density` causes Activity recreation if dragged to a secondary display or when display density changes dynamically.

#### Recommended AndroidManifest.xml Configuration:
```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:resizeableActivity="true"
    android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|density|keyboardHidden|uiMode"
    android:windowSoftInputMode="adjustResize">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```
*Citations:*
- [Android Developers - \<activity\> element](https://developer.android.com/guide/topics/manifest/activity-element)
- [Android Developers - Configuration and continuity](https://developer.android.com/guide/topics/large-screens/configuration-and-continuity)

---

## 2. Android 12L+ Letterboxing & Size Compatibility Mode

### Mechanisms
Starting in Android 12 (API 31) and enhanced in Android 12L (API 32) and 13 (API 33), Android introduces advanced letterboxing and Size Compatibility Mode:
1. **Trigger Condition:** An app has fixed orientation (`screenOrientation="portrait"`), fixed aspect ratio (`maxAspectRatio` or `minAspectRatio`), or `resizeableActivity="false"`.
2. **Behavior:** When the window provided by the system does not match the app's declared constraints, the OS centers the app on the screen or positions it to one side, adding letterbox matting (solid bars or blurred wallpaper) and showing an app restart button.
3. **Manufacturer & User Overrides:**
   - OEMs (Samsung, Xiaomi, Vivo, OnePlus) implement per-app overrides (e.g., `OVERRIDE_MIN_ASPECT_RATIO_TO_ALIGN_WITH_SPLIT_SCREEN`).
   - Android 14+ allows users to manually override aspect ratios in device Settings.
4. **Android 16+ Platform Change:** Android 16 (API 36) strictly ignores aspect ratio and orientation restrictions on screens where `sw >= 600dp`, forcing apps to fill the window.

### How to Guarantee Letterboxing is Avoided
1. Ensure `android:resizeableActivity="true"`.
2. Remove all `android:maxAspectRatio` and `android:minAspectRatio` attributes.
3. Avoid hardcoding `android:screenOrientation="portrait"`.
4. Ensure Compose layouts adapt to dynamic bounds rather than assuming screen dimensions.

*Reference:* [Android Developers - Device compatibility mode](https://developer.android.com/guide/practices/device-compatibility-mode)

---

## 3. Adaptive Layout in Jetpack Compose

### Window Size Classes & Breakpoints
The Android Adaptive design specification defines standard breakpoints based on the **window area allocated to the app**, NOT the physical device display:

| Window Size Class | Breakpoint Width | Typical Split-Screen Context |
| :--- | :--- | :--- |
| **Compact Width** | `width < 600dp` | Phone in portrait; **1/2 and 1/3 split-screen partitions on phones**; half-screen splits on portrait foldables. |
| **Medium Width** | `600dp <= width < 840dp` | Large unfolded foldables in portrait; 1/2 split-screen on landscape tablets. |
| **Expanded Width** | `840dp <= width < 1200dp` | Full-screen landscape tablets; full-screen desktop windowing. |
| **Compact Height** | `height < 480dp` | **50/50 landscape split-screen on phones**; phones in landscape. |
| **Medium Height** | `480dp <= height < 900dp` | Standard phone full screen; tablet split-screen. |
| **Expanded Height** | `height >= 900dp` | **1/3 split-screen portrait partition on tall phones (e.g., 20:9 or 21:9)**; tablets in portrait. |

*Reference:* [Android Developers - Use window size classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes)

### Dependencies
Add the official Compose Material 3 Adaptive library in `android/app/build.gradle.kts`:
```kotlin
dependencies {
    // Official AndroidX Compose Material 3 Adaptive suite
    implementation("androidx.compose.material3.adaptive:adaptive:1.1.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.1.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.1.0")
}
```

### Implementing Adaptive Partitions

In split-screen, two extreme edge cases occur:
1. **1/3 Portrait Split (Ultra-narrow width, tall height):**
   - Available width is typically 280dp – 340dp (Compact), while height may be 600dp+ (Medium or Expanded).
   - Multi-column layouts, horizontal action bars, and side-by-side data cards overflow or wrap destructively.
2. **50/50 Landscape Split (Short height, medium/compact width):**
   - Available height is under 400dp (Compact Height).
   - Fixed-height banners, hero headers, or tall top bars consume 70%+ of the visible viewport, leaving zero space for scrollable content or text input with keyboard up.

#### Production Implementation Example:
```kotlin
package com.sarvesh173.talos.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass

@Composable
fun TalosAdaptiveScreenLayout(
    modifier: Modifier = Modifier,
    headerContent: @Composable () -> Unit,
    primaryContent: @Composable () -> Unit,
    actionControls: @Composable () -> Unit
) {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val widthClass = adaptiveInfo.windowSizeClass.windowWidthSizeClass
    val heightClass = adaptiveInfo.windowSizeClass.windowHeightSizeClass

    // Detect split-screen edge cases
    val isUltraNarrow = widthClass == WindowWidthSizeClass.COMPACT
    val isCompactHeight = heightClass == WindowHeightSizeClass.COMPACT

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight

        if (isCompactHeight) {
            // Scenario B: Landscape 50/50 split -> Short height.
            // Row-based layout or minimized top-chrome so content remains usable.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    headerContent()
                    primaryContent()
                }
                Column(
                    modifier = Modifier
                        .width(180.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    actionControls()
                }
            }
        } else {
            // Scenario A: Portrait split (1/3 or 1/2) -> Compact width, medium/tall height.
            // Stack vertically; eliminate fixed-width side bars.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                headerContent()
                Spacer(modifier = Modifier.height(8.dp))
                primaryContent()
                Spacer(modifier = Modifier.height(12.dp))
                actionControls()
            }
        }
    }
}
```

---

## 4. Viewport Height, Scroll Containers & System Window Insets

### 1. Replacing Fixed-Height Containers
- **Anti-pattern:** `Modifier.height(320.dp)` or `Modifier.fillMaxHeight(0.6f)` inside nested elements.
- When an app is placed in a 1/3 split or when the virtual keyboard (IME) appears, the available viewport height can drop to ~220dp. A 320dp fixed container immediately causes clipping, overlapping, or unscrollable off-screen UI elements.
- **Rule:** Use `Modifier.heightIn(min = ..., max = ...)` or weight-based flex distribution (`Modifier.weight(1f, fill = false)`).

### 2. Nested Scroll Crash: `LazyColumn` Inside Scrollable Parent
- **Fatal Defect:** Placing a `LazyColumn` or `LazyRow` inside a `Modifier.verticalScroll(...)` or `Modifier.horizontalScroll(...)` parent container without a bounded constraint crashes with:
  `java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints, which is disallowed.`
- **Remedy:**
  1. Flatten into a single `LazyColumn` using `item { ... }` headers/footers instead of an external `verticalScroll`.
  2. If a nested lazy list must be bounded within an adaptive screen, wrap it in a parent with explicit height constraints via `BoxWithConstraints` or provide fixed boundaries with `Modifier.fillParentMaxHeight()` inside lazy scopes.

### 3. WindowInsets in Split-Screen Mode
In full-screen mode, an app typically insets for the status bar at top and the navigation bar at bottom. In split-screen mode:
- **Top app in split-screen:** Has status bar insets at the top, but the bottom border is the multi-window partition divider (no navigation bar inset).
- **Bottom app in split-screen:** Has the split-screen divider at the top (top inset is 0dp or caption bar), and the navigation bar at the bottom.
- **Secondary / Side-by-side app:** Bordered by the window partition.

#### Inset Comparison
- `WindowInsets.statusBars` / `WindowInsets.navigationBars`: Insets for specific system bars. In split-screen, one or both may be zero depending on which partition the app occupies.
- `WindowInsets.ime`: The software keyboard. In split-screen, the keyboard can obscure almost 90% of the partition if not padded.
- `WindowInsets.safeDrawing`: Encompasses `statusBars`, `navigationBars`, `displayCutout`, and `captionBar`.
- `WindowInsets.safeGestures`: Encompasses areas where system navigation gestures take priority.
- `WindowInsets.safeContent`: Intersection of `safeDrawing` and `safeGestures`.

#### Inset Handling Pattern:
```kotlin
@Composable
fun TalosSplitScreenScaffold(
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        // Allow content to adapt to system bars and IME smoothly without hardcoding bar heights
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            content(innerPadding)
        }
    }
}
```
*Reference:* [Android Developers - About window insets](https://developer.android.com/develop/ui/compose/system/insets)

---

## 5. Touch Gestures & System Interaction Conflicts

### Conflict Mechanisms
1. **Edge-Drag / System Back Navigation:**
   - In modern Android gesture navigation, swiping inward from the left or right edge triggers System Back.
   - In split-screen mode, the system back area remains active along the outer edges of the physical screen. If your app has horizontal swipeable elements, carousels, or edge drawer triggers near the screen boundary, gestures will clash.
2. **Predictive Back Navigation (Android 13+ / API 33+):**
   - With Predictive Back enabled, an inward edge swipe triggers a scale-down animation of the Activity.
   - If an app tries to handle drag events on raw edges without proper consumption or exclusion, the gesture is captured by the Window Manager Shell.

### Solutions in Jetpack Compose

#### 1. System Gesture Exclusion
Use `Modifier.systemGestureExclusion()` to prevent the system from intercepting swipes on custom controls (e.g., audio scrubbers, sliders, or interactive canvas nodes).
```kotlin
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier

@Composable
fun InteractiveScrubber(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            // Informs the Window Manager to exclude this component's bounding box
            // from system back gesture triggers (subject to platform vertical limits)
            .systemGestureExclusion()
    ) {
        // Scrubber / waveform UI
    }
}
```
*Platform Constraint (Documented):* Android limits system gesture exclusion to a maximum vertical height (typically 200dp per edge) to prevent malicious or broken apps from disabling system back navigation.

#### 2. Using `safeGesturesPadding`
When UI controls must not overlap system gesture trigger regions:
```kotlin
Modifier.safeGesturesPadding()
```

#### 3. Edge-Aware Pointer Input Handling
When implementing custom swipe or drag handling with `pointerInput`, ensure pointer events are consumed so parent gestures do not fight the gesture detector:
```kotlin
Modifier.pointerInput(Unit) {
    detectHorizontalDragGestures { change, dragAmount ->
        // Explicitly consume the pointer change so parents/system know the gesture is claimed
        change.consume()
        handleDrag(dragAmount)
    }
}
```

#### 4. Predictive Back Handler
Use AndroidX Activity Compose `PredictiveBackHandler` to properly intercept back events without intercepting raw touch events at the view edge:
```kotlin
import androidx.activity.compose.PredictiveBackHandler
import kotlinx.coroutines.flow.Flow
import androidx.activity.BackEventCompat
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun TalosPredictiveBackCoordinator(
    hasUnsavedChanges: Boolean,
    onConfirmDismiss: () -> Unit
) {
    PredictiveBackHandler(enabled = hasUnsavedChanges) { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { backEvent ->
                // Animate canvas scale or alpha based on backEvent.progress (0.0 to 1.0)
            }
            // Completed gesture:
            onConfirmDismiss()
        } catch (e: CancellationException) {
            // User aborted the gesture; revert visual transformations
        }
    }
}
```
*Citations:*
- [Android Developers - Ensure compatibility with gesture navigation](https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav)
- [Android Developers - Add support for the predictive back gesture](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)

---

## 6. Lifecycle, Configuration Changes & State Preservation

### What Happens During a Window Resize?
When a user drags the multi-window partition divider:
1. **With default config:** If `smallestScreenSize|screenSize|screenLayout|orientation` is NOT declared in `android:configChanges`, Android **destroys and recreates the Activity** on every breakpoint transition.
2. **With declared `configChanges`:** The Activity is **not destroyed**. Instead:
   - `onConfigurationChanged(newConfig: Configuration)` is invoked on the Activity.
   - Compose automatically invalidates `LocalConfiguration.current`, `WindowInsets`, and `WindowAdaptiveInfo`.
   - All composables observing these values recompose with the new dimensions.

### State Loss Risks & Mitigation

| State Holder | Behavior on Resize without `configChanges` | Behavior on Resize with `configChanges` | Safe Across Process Death? | Recommended Use |
| :--- | :--- | :--- | :--- | :--- |
| `remember { mutableStateOf(...) }` | **Lost** (re-initialized to default) | Preserved | No | Transient animation state only. |
| `rememberSaveable { mutableStateOf(...) }` | **Preserved** via `Bundle` | Preserved | **Yes** | UI form state, selected tab, scroll IDs. |
| `ViewModel` (via `viewModel()`) | **Preserved** (cleared only on finish) | Preserved | Survives recreation, lost on process death unless backed by `SavedStateHandle` | Network pipelines, repository connections, agent sync loops. |
| Coroutines in `LaunchedEffect` | Cancelled & restarted if keys change | Continuous (survives unless key recomposes) | No | Scoped jobs. |

#### Preventing Coroutine Cancellation & Socket Disconnects
TALOS pairs with a Rust core over Tailscale. Network sockets and WebSocket event streams must **never** be tied directly to a Composable lifecycle or an Activity that restarts on window resize:
- **Rule:** Manage Tailscale and Rust RPC connections inside an Application-scoped component or a Jetpack `ViewModel` using `viewModelScope`.
- Avoid launching connection loops inside `LaunchedEffect(LocalConfiguration.current)` or any key that fluctuates when the split-screen divider moves.

```kotlin
// In ViewModel: survives window resize and configuration changes cleanly
class TalosMeshViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val rustClient: RustOverlayClient
) : ViewModel() {
    val connectionState = rustClient.observeState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MeshState.Idle)

    fun sendAgentPayload(bytes: ByteArray) {
        viewModelScope.launch {
            rustClient.transmit(bytes)
        }
    }
}
```
*Reference:* [Android Developers - Test different screen and window sizes (State restoration)](https://developer.android.com/training/testing/different-screens)

---

## 7. Verification Without a Physical Phone

Testing split-screen layouts on emulators and automated test environments requires exercising dynamic window resizing, configuration changes, and multi-window bounds via ADB.

### 1. Enabling Freeform and Multi-Window via ADB
On Google APIs emulator images (or rooted developer builds), enable platform multi-window and freeform flags:
```bash
# Enable freeform window management support globally
adb shell settings put global enable_freeform_support 1

# Force all activities to be resizable regardless of manifest attributes
adb shell settings put global force_resizable_activities 1

# Enable multi-window for non-resizable activities (Android 12+)
adb shell settings put global enable_non_resizable_multi_window 1
```

### 2. Simulating Split-Screen via Android Window Manager (`wm`)
The `wm` utility allows you to simulate high-density, tall-aspect, or narrow-screen split partitions directly on an emulator:

```bash
# Check current display dimensions and density
adb shell wm size
adb shell wm density

# Simulate an ultra-narrow 1/3 split window on a tall phone (e.g., 360x1600 px)
adb shell wm size 360x1600
adb shell wm density 320

# Simulate a compact-height 50/50 landscape split (e.g., 1080x480 px)
adb shell wm size 1080x480
adb shell wm density 420

# Reset back to default physical dimensions
adb shell wm size reset
adb shell wm density reset
```

### 3. Launching Directly into Windowing Modes via Activity Manager (`am`)
You can launch an activity directly into split-screen or freeform windowing modes:
```bash
# Windowing Mode Constants:
# 1 = WINDOWING_MODE_FULLSCREEN
# 5 = WINDOWING_MODE_FREEFORM
# 3 = WINDOWING_MODE_SPLIT_SCREEN_PRIMARY (Android 11 and lower)
# 4 = WINDOWING_MODE_SPLIT_SCREEN_SECONDARY (Android 11 and lower)

# Launch TALOS directly into freeform mode:
adb shell am start -n com.sarvesh173.talos/.MainActivity --windowingMode 5

# Inspect active task ID
adb shell am stack list
# or on Android 12+:
adb shell cmd window dump tasks
```

### 4. Resizing Freeform / Multi-Window Tasks
Once a task is running in freeform or multi-window mode, resize its window bounds dynamically:
```bash
# Usage: am task resize <TASK_ID> <LEFT> <TOP> <RIGHT> <BOTTOM>
# Example: Resize Task 125 to 1/3 width partition (bounds 0 0 360 1200)
adb shell am task resize 125 0 0 360 1200

# Resize Task 125 to 50/50 landscape partition (bounds 0 0 1080 500)
adb shell am task resize 125 0 0 1080 500
```

### 5. Automated Compose UI Testing (`DeviceConfigurationOverride`)
To verify split-screen layouts in automated local instrumented tests without touching hardware buttons:
```kotlin
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class SplitScreenLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testOneThirdPortraitSplit() {
        composeTestRule.setContent {
            // Emulate 1/3 split partition: 320dp width x 800dp height
            DeviceConfigurationOverride(
                override = DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 800.dp))
            ) {
                TalosAdaptiveScreenLayout(
                    headerContent = { /* header */ },
                    primaryContent = { /* primary */ },
                    actionControls = { /* controls */ }
                )
            }
        }
        // Assert UI components exist without clipping
    }

    @Test
    fun testFiftyFiftyLandscapeSplit() {
        composeTestRule.setContent {
            // Emulate 50/50 landscape split: 600dp width x 380dp height
            DeviceConfigurationOverride(
                override = DeviceConfigurationOverride.ForcedSize(DpSize(600.dp, 380.dp))
            ) {
                TalosAdaptiveScreenLayout(
                    headerContent = { /* header */ },
                    primaryContent = { /* primary */ },
                    actionControls = { /* controls */ }
                )
            }
        }
        // Assert UI switches to horizontal adaptation
    }
}
```
*Citations:*
- [Android Developers - Test on multiple screen sizes with the resizable emulator](https://developer.android.com/studio/run/resizable-emulator)
- [Android Developers - Libraries and tools to test different screen sizes](https://developer.android.com/training/testing/different-screens/tools)
