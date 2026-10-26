package com.sarvesh173.talos.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window size buckets used for adaptive layout.
 *
 * Split-screen is the reason this exists: in a 1/3-width split the width can
 * be as narrow as ~320dp while the height stays tall, and in a 50/50
 * landscape split the height can drop below 400dp while the width is wide.
 * A layout that only thinks in "phone vs tablet" breaks in both cases, so
 * every screen reacts to width AND height independently.
 *
 * Deliberately dependency-free (no window-size-class artifact) so the
 * pinned Material 3 Expressive toolchain stays untouched.
 */
enum class WindowWidthClass { Compact, Medium, Expanded }

enum class WindowHeightClass { Short, Medium, Tall }

@Immutable
data class WindowSize(
    val widthClass: WindowWidthClass,
    val heightClass: WindowHeightClass,
    val width: Dp,
    val height: Dp,
) {
    /** True in a narrow pane (1/3-width split, small phones). */
    val isNarrow: Boolean get() = widthClass == WindowWidthClass.Compact

    /** True when vertical space is tight (landscape split, keyboard open). */
    val isShort: Boolean get() = heightClass == WindowHeightClass.Short
}

@Composable
fun windowWidthClass(width: Dp): WindowWidthClass = when {
    width < 600.dp -> WindowWidthClass.Compact
    width < 840.dp -> WindowWidthClass.Medium
    else -> WindowWidthClass.Expanded
}

@Composable
fun windowHeightClass(height: Dp): WindowHeightClass = when {
    height < 480.dp -> WindowHeightClass.Short
    height < 840.dp -> WindowHeightClass.Medium
    else -> WindowHeightClass.Tall
}

/**
 * Measure the current window and classify it.
 *
 * BoxWithConstraints reports the size of the *window*, not the screen, so in
 * multi-window mode it reflects the actual pane the app occupies.
 */
@Composable
fun rememberWindowSize(): WindowSize {
    var result by remember { mutableStateOf(WindowSize(WindowWidthClass.Compact, WindowHeightClass.Medium, 0.dp, 0.dp)) }
    BoxWithConstraints {
        result = WindowSize(
            widthClass = windowWidthClass(maxWidth),
            heightClass = windowHeightClass(maxHeight),
            width = maxWidth,
            height = maxHeight,
        )
    }
    return result
}
