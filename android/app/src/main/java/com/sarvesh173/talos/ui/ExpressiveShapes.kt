package com.sarvesh173.talos.ui

import android.graphics.Path as AndroidPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * M3 Expressive shape engine built on androidx.graphics:graphics-shapes (stable 1.1.0).
 * Verified API: RoundedPolygon.toPath() and Morph.toPath(progress) return android.graphics.Path
 * (Shapes_androidKt extensions), converted to Compose Path via asComposePath().
 */
object M3EShapes {

    /** 12-lobed scallop (cookie) - the signature M3 Expressive badge morph target. */
    fun scallop12(): RoundedPolygon = RoundedPolygon(
        numVertices = 12,
        radius = 1f,
        rounding = CornerRounding(radius = 0.18f, smoothing = 0.55f)
    )

    /** Softer 8-sided cookie for mid-size containers. */
    fun scallop8(): RoundedPolygon = RoundedPolygon(
        numVertices = 8,
        radius = 1f,
        rounding = CornerRounding(radius = 0.25f, smoothing = 0.6f)
    )

    /** Circle anchor for morph start. */
    fun circle(): RoundedPolygon = RoundedPolygon(numVertices = 24, radius = 1f)

    /** Rounded-square container anchor. */
    fun squircle(): RoundedPolygon = RoundedPolygon(
        numVertices = 4,
        radius = 1f,
        rounding = CornerRounding(radius = 0.42f, smoothing = 0.55f)
    )
}

/**
 * A Compose [Shape] that renders a [Morph] at the given progress.
 * Morph paths are normalized to a 0..1 box, so scale to draw size and center.
 */
class M3EMorphShape(
    private val morph: Morph,
    private val progress: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val androidPath: AndroidPath = morph.toPath(progress)
        val path: Path = androidPath.asComposePath()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        val bounds = path.getBounds()
        val dx = (size.width - bounds.size.width) / 2f - bounds.left
        val dy = (size.height - bounds.size.height) / 2f - bounds.top
        if (dx != 0f || dy != 0f) path.translate(Offset(dx, dy))
        return Outline.Generic(path)
    }
}

/** A static [Shape] wrapper for a [RoundedPolygon] (normalized 0..1, scaled to size). */
class M3EPolygonShape(
    private val polygon: RoundedPolygon
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val androidPath: AndroidPath = polygon.toPath()
        val path: Path = androidPath.asComposePath()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        val bounds = path.getBounds()
        val dx = (size.width - bounds.size.width) / 2f - bounds.left
        val dy = (size.height - bounds.size.height) / 2f - bounds.top
        if (dx != 0f || dy != 0f) path.translate(Offset(dx, dy))
        return Outline.Generic(path)
    }
}
