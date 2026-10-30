package com.sarvesh173.talos.ui

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Custom TALOS Vector Iconography Suite
 *
 * Designed with a coherent 24dp viewport, 2dp stroke geometry,
 * and high-contrast squircle-badge silhouettes distinct from stock Material Icons.
 */
object TalosIcons {
    private var _talosmic: ImageVector? = null

    val TalosMic: ImageVector
        get() {
            if (_talosmic != null) return _talosmic!!
            _talosmic = materialIcon(name = "TalosMic") {
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    moveTo(7.0f, 2.0f)
                    lineTo(17.0f, 2.0f)
                    curveTo(20.5f, 2.0f, 22.0f, 3.5f, 22.0f, 7.0f)
                    lineTo(22.0f, 17.0f)
                    curveTo(22.0f, 20.5f, 20.5f, 22.0f, 17.0f, 22.0f)
                    lineTo(7.0f, 22.0f)
                    curveTo(3.5f, 22.0f, 2.0f, 20.5f, 2.0f, 17.0f)
                    lineTo(2.0f, 7.0f)
                    curveTo(2.0f, 3.5f, 3.5f, 2.0f, 7.0f, 2.0f)
                    close()
                    moveTo(7.0f, 4.0f)
                    curveTo(4.8f, 4.0f, 4.0f, 4.8f, 4.0f, 7.0f)
                    lineTo(4.0f, 17.0f)
                    curveTo(4.0f, 19.2f, 4.8f, 20.0f, 7.0f, 20.0f)
                    lineTo(17.0f, 20.0f)
                    curveTo(19.2f, 20.0f, 20.0f, 19.2f, 20.0f, 17.0f)
                    lineTo(20.0f, 7.0f)
                    curveTo(20.0f, 4.8f, 19.2f, 4.0f, 17.0f, 4.0f)
                    close()
                    moveTo(12.0f, 6.5f)
                    curveTo(13.1f, 6.5f, 14.0f, 7.4f, 14.0f, 8.5f)
                    lineTo(14.0f, 11.0f)
                    curveTo(14.0f, 12.1f, 13.1f, 13.0f, 12.0f, 13.0f)
                    curveTo(10.9f, 13.0f, 10.0f, 12.1f, 10.0f, 11.0f)
                    lineTo(10.0f, 8.5f)
                    curveTo(10.0f, 7.4f, 10.9f, 6.5f, 12.0f, 6.5f)
                    close()
                    moveTo(8.0f, 10.0f)
                    lineTo(9.5f, 10.0f)
                    lineTo(9.5f, 11.5f)
                    curveTo(9.5f, 12.9f, 10.6f, 14.0f, 12.0f, 14.0f)
                    curveTo(13.4f, 14.0f, 14.5f, 12.9f, 14.5f, 11.5f)
                    lineTo(14.5f, 10.0f)
                    lineTo(16.0f, 10.0f)
                    lineTo(16.0f, 11.5f)
                    curveTo(16.0f, 13.4f, 14.6f, 15.0f, 12.8f, 15.4f)
                    lineTo(12.8f, 16.5f)
                    lineTo(14.5f, 16.5f)
                    curveTo(14.8f, 16.5f, 15.0f, 16.7f, 15.0f, 17.0f)
                    curveTo(15.0f, 17.3f, 14.8f, 17.5f, 14.5f, 17.5f)
                    lineTo(9.5f, 17.5f)
                    curveTo(9.2f, 17.5f, 9.0f, 17.3f, 9.0f, 17.0f)
                    curveTo(9.0f, 16.7f, 9.2f, 16.5f, 9.5f, 16.5f)
                    lineTo(11.2f, 16.5f)
                    lineTo(11.2f, 15.4f)
                    curveTo(9.4f, 15.0f, 8.0f, 13.4f, 8.0f, 11.5f)
                    close()
                }
            }
            return _talosmic!!
        }

    private var _taloscamera: ImageVector? = null

    val TalosCamera: ImageVector
        get() {
            if (_taloscamera != null) return _taloscamera!!
            _taloscamera = materialIcon(name = "TalosCamera") {
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    moveTo(7.0f, 2.0f)
                    lineTo(17.0f, 2.0f)
                    curveTo(20.5f, 2.0f, 22.0f, 3.5f, 22.0f, 7.0f)
                    lineTo(22.0f, 17.0f)
                    curveTo(22.0f, 20.5f, 20.5f, 22.0f, 17.0f, 22.0f)
                    lineTo(7.0f, 22.0f)
                    curveTo(3.5f, 22.0f, 2.0f, 20.5f, 2.0f, 17.0f)
                    lineTo(2.0f, 7.0f)
                    curveTo(2.0f, 3.5f, 3.5f, 2.0f, 7.0f, 2.0f)
                    close()
                    moveTo(7.0f, 4.0f)
                    curveTo(4.8f, 4.0f, 4.0f, 4.8f, 4.0f, 7.0f)
                    lineTo(4.0f, 17.0f)
                    curveTo(4.0f, 19.2f, 4.8f, 20.0f, 7.0f, 20.0f)
                    lineTo(17.0f, 20.0f)
                    curveTo(19.2f, 20.0f, 20.0f, 19.2f, 20.0f, 17.0f)
                    lineTo(20.0f, 7.0f)
                    curveTo(20.0f, 4.8f, 19.2f, 4.0f, 17.0f, 4.0f)
                    close()
                    moveTo(8.0f, 12.5f)
                    arcTo(4.0f, 4.0f, 0.0f, true, false, 16.0f, 12.5f)
                    arcTo(4.0f, 4.0f, 0.0f, true, false, 8.0f, 12.5f)
                    close()
                    moveTo(9.8f, 12.5f)
                    arcTo(2.2f, 2.2f, 0.0f, true, false, 14.2f, 12.5f)
                    arcTo(2.2f, 2.2f, 0.0f, true, false, 9.8f, 12.5f)
                    close()
                    moveTo(11.0f, 12.5f)
                    arcTo(1.0f, 1.0f, 0.0f, true, false, 13.0f, 12.5f)
                    arcTo(1.0f, 1.0f, 0.0f, true, false, 11.0f, 12.5f)
                    close()
                    moveTo(15.3f, 7.5f)
                    arcTo(1.2f, 1.2f, 0.0f, true, false, 17.7f, 7.5f)
                    arcTo(1.2f, 1.2f, 0.0f, true, false, 15.3f, 7.5f)
                    close()
                    moveTo(6.5f, 7.0f)
                    curveTo(6.5f, 6.7f, 6.7f, 6.5f, 7.0f, 6.5f)
                    lineTo(9.5f, 6.5f)
                    curveTo(9.8f, 6.5f, 10.0f, 6.7f, 10.0f, 7.0f)
                    curveTo(10.0f, 7.3f, 9.8f, 7.5f, 9.5f, 7.5f)
                    lineTo(7.0f, 7.5f)
                    curveTo(6.7f, 7.5f, 6.5f, 7.3f, 6.5f, 7.0f)
                    close()
                }
            }
            return _taloscamera!!
        }

    private var _taloshub: ImageVector? = null

    val TalosHub: ImageVector
        get() {
            if (_taloshub != null) return _taloshub!!
            _taloshub = materialIcon(name = "TalosHub") {
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    moveTo(9.5f, 12.0f)
                    arcTo(2.5f, 2.5f, 0.0f, true, false, 14.5f, 12.0f)
                    arcTo(2.5f, 2.5f, 0.0f, true, false, 9.5f, 12.0f)
                    close()
                    moveTo(3.8f, 12.0f)
                    arcTo(8.2f, 8.2f, 0.0f, true, false, 20.2f, 12.0f)
                    arcTo(8.2f, 8.2f, 0.0f, true, false, 3.8f, 12.0f)
                    close()
                    moveTo(5.6f, 12.0f)
                    arcTo(6.4f, 6.4f, 0.0f, true, false, 18.4f, 12.0f)
                    arcTo(6.4f, 6.4f, 0.0f, true, false, 5.6f, 12.0f)
                    close()
                    moveTo(9.8f, 3.8f)
                    arcTo(2.2f, 2.2f, 0.0f, true, false, 14.2f, 3.8f)
                    arcTo(2.2f, 2.2f, 0.0f, true, false, 9.8f, 3.8f)
                    close()
                    moveTo(16.9f, 16.1f)
                    arcTo(2.2f, 2.2f, 0.0f, true, false, 21.3f, 16.1f)
                    arcTo(2.2f, 2.2f, 0.0f, true, false, 16.9f, 16.1f)
                    close()
                    moveTo(2.7f, 16.1f)
                    arcTo(2.2f, 2.2f, 0.0f, true, false, 7.1f, 16.1f)
                    arcTo(2.2f, 2.2f, 0.0f, true, false, 2.7f, 16.1f)
                    close()
                    moveTo(11.2f, 6.0f)
                    lineTo(12.8f, 6.0f)
                    lineTo(12.8f, 9.6f)
                    lineTo(11.2f, 9.6f)
                    close()
                    moveTo(13.8f, 13.2f)
                    lineTo(17.6f, 15.2f)
                    lineTo(16.8f, 16.6f)
                    lineTo(13.0f, 14.6f)
                    close()
                    moveTo(10.2f, 13.2f)
                    lineTo(11.0f, 14.6f)
                    lineTo(7.2f, 16.6f)
                    lineTo(6.4f, 15.2f)
                    close()
                }
            }
            return _taloshub!!
        }

    private var _talosboard: ImageVector? = null

    val TalosBoard: ImageVector
        get() {
            if (_talosboard != null) return _talosboard!!
            _talosboard = materialIcon(name = "TalosBoard") {
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    moveTo(5.0f, 2.0f)
                    lineTo(15.0f, 2.0f)
                    curveTo(16.7f, 2.0f, 18.0f, 3.3f, 18.0f, 5.0f)
                    lineTo(18.0f, 15.0f)
                    curveTo(18.0f, 16.7f, 16.7f, 18.0f, 15.0f, 18.0f)
                    lineTo(5.0f, 18.0f)
                    curveTo(3.3f, 18.0f, 2.0f, 16.7f, 2.0f, 15.0f)
                    lineTo(2.0f, 5.0f)
                    curveTo(2.0f, 3.3f, 3.3f, 2.0f, 5.0f, 2.0f)
                    close()
                    moveTo(5.5f, 4.0f)
                    curveTo(4.7f, 4.0f, 4.0f, 4.7f, 4.0f, 5.5f)
                    lineTo(4.0f, 14.5f)
                    curveTo(4.0f, 15.3f, 4.7f, 16.0f, 5.5f, 16.0f)
                    lineTo(14.5f, 16.0f)
                    curveTo(15.3f, 16.0f, 16.0f, 15.3f, 16.0f, 14.5f)
                    lineTo(16.0f, 5.5f)
                    curveTo(16.0f, 4.7f, 15.3f, 4.0f, 14.5f, 4.0f)
                    close()
                    moveTo(6.0f, 11.0f)
                    lineTo(8.0f, 11.0f)
                    lineTo(8.0f, 14.0f)
                    lineTo(6.0f, 14.0f)
                    close()
                    moveTo(9.5f, 8.5f)
                    lineTo(11.5f, 8.5f)
                    lineTo(11.5f, 14.0f)
                    lineTo(9.5f, 14.0f)
                    close()
                    moveTo(13.0f, 6.5f)
                    lineTo(14.5f, 6.5f)
                    lineTo(14.5f, 10.0f)
                    lineTo(13.0f, 10.0f)
                    close()
                    moveTo(20.3f, 7.3f)
                    curveTo(20.8f, 6.8f, 21.6f, 6.8f, 22.1f, 7.3f)
                    curveTo(22.6f, 7.8f, 22.6f, 8.6f, 22.1f, 9.1f)
                    lineTo(16.5f, 14.7f)
                    lineTo(14.7f, 12.9f)
                    close()
                    moveTo(14.7f, 12.9f)
                    lineTo(16.5f, 14.7f)
                    lineTo(13.5f, 17.7f)
                    lineTo(11.5f, 18.5f)
                    lineTo(12.3f, 16.5f)
                    close()
                }
            }
            return _talosboard!!
        }

    private var _talosvault: ImageVector? = null

    val TalosVault: ImageVector
        get() {
            if (_talosvault != null) return _talosvault!!
            _talosvault = materialIcon(name = "TalosVault") {
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    moveTo(12.0f, 2.0f)
                    lineTo(20.5f, 6.9f)
                    lineTo(20.5f, 17.1f)
                    lineTo(12.0f, 22.0f)
                    lineTo(3.5f, 17.1f)
                    lineTo(3.5f, 6.9f)
                    close()
                    moveTo(12.0f, 4.3f)
                    lineTo(5.4f, 8.1f)
                    lineTo(5.4f, 15.9f)
                    lineTo(12.0f, 19.7f)
                    lineTo(18.6f, 15.9f)
                    lineTo(18.6f, 8.1f)
                    close()
                    moveTo(9.8f, 10.2f)
                    curveTo(9.8f, 9.0f, 10.8f, 8.0f, 12.0f, 8.0f)
                    curveTo(13.2f, 8.0f, 14.2f, 9.0f, 14.2f, 10.2f)
                    curveTo(14.2f, 11.0f, 13.7f, 11.7f, 13.0f, 12.1f)
                    lineTo(13.5f, 15.5f)
                    curveTo(13.6f, 15.8f, 13.3f, 16.0f, 13.0f, 16.0f)
                    lineTo(11.0f, 16.0f)
                    curveTo(10.7f, 16.0f, 10.4f, 15.8f, 10.5f, 15.5f)
                    lineTo(11.0f, 12.1f)
                    curveTo(10.3f, 11.7f, 9.8f, 11.0f, 9.8f, 10.2f)
                    close()
                    moveTo(11.3f, 3.5f)
                    arcTo(0.7f, 0.7f, 0.0f, true, false, 12.7f, 3.5f)
                    arcTo(0.7f, 0.7f, 0.0f, true, false, 11.3f, 3.5f)
                    close()
                    moveTo(18.3f, 7.5f)
                    arcTo(0.7f, 0.7f, 0.0f, true, false, 19.7f, 7.5f)
                    arcTo(0.7f, 0.7f, 0.0f, true, false, 18.3f, 7.5f)
                    close()
                    moveTo(18.3f, 16.5f)
                    arcTo(0.7f, 0.7f, 0.0f, true, false, 19.7f, 16.5f)
                    arcTo(0.7f, 0.7f, 0.0f, true, false, 18.3f, 16.5f)
                    close()
                    moveTo(11.3f, 20.5f)
                    arcTo(0.7f, 0.7f, 0.0f, true, false, 12.7f, 20.5f)
                    arcTo(0.7f, 0.7f, 0.0f, true, false, 11.3f, 20.5f)
                    close()
                    moveTo(4.3f, 16.5f)
                    arcTo(0.7f, 0.7f, 0.0f, true, false, 5.7f, 16.5f)
                    arcTo(0.7f, 0.7f, 0.0f, true, false, 4.3f, 16.5f)
                    close()
                    moveTo(4.3f, 7.5f)
                    arcTo(0.7f, 0.7f, 0.0f, true, false, 5.7f, 7.5f)
                    arcTo(0.7f, 0.7f, 0.0f, true, false, 4.3f, 7.5f)
                    close()
                }
            }
            return _talosvault!!
        }

    private var _talosbrowser: ImageVector? = null

    val TalosBrowser: ImageVector
        get() {
            if (_talosbrowser != null) return _talosbrowser!!
            _talosbrowser = materialIcon(name = "TalosBrowser") {
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    moveTo(5.5f, 12.0f)
                    arcTo(6.5f, 6.5f, 0.0f, true, false, 18.5f, 12.0f)
                    arcTo(6.5f, 6.5f, 0.0f, true, false, 5.5f, 12.0f)
                    close()
                    moveTo(7.5f, 12.0f)
                    arcTo(4.5f, 4.5f, 0.0f, true, false, 16.5f, 12.0f)
                    arcTo(4.5f, 4.5f, 0.0f, true, false, 7.5f, 12.0f)
                    close()
                    moveTo(11.2f, 7.6f)
                    lineTo(12.8f, 7.6f)
                    lineTo(12.8f, 16.4f)
                    lineTo(11.2f, 16.4f)
                    close()
                    moveTo(7.6f, 11.2f)
                    lineTo(16.4f, 11.2f)
                    lineTo(16.4f, 12.8f)
                    lineTo(7.6f, 12.8f)
                    close()
                    moveTo(1.5f, 14.5f)
                    curveTo(1.5f, 12.5f, 6.0f, 9.0f, 12.0f, 8.0f)
                    curveTo(18.0f, 7.0f, 22.5f, 9.0f, 22.5f, 11.0f)
                    curveTo(22.5f, 13.0f, 18.0f, 16.5f, 12.0f, 17.5f)
                    curveTo(6.0f, 18.5f, 1.5f, 16.5f, 1.5f, 14.5f)
                    close()
                    moveTo(3.8f, 14.3f)
                    curveTo(3.8f, 15.5f, 7.5f, 16.8f, 12.0f, 16.0f)
                    curveTo(16.5f, 15.2f, 20.2f, 12.6f, 20.2f, 11.3f)
                    curveTo(20.2f, 10.1f, 16.5f, 8.8f, 12.0f, 9.6f)
                    curveTo(7.5f, 10.4f, 3.8f, 13.0f, 3.8f, 14.3f)
                    close()
                    moveTo(18.5f, 10.2f)
                    arcTo(1.3f, 1.3f, 0.0f, true, false, 21.1f, 10.2f)
                    arcTo(1.3f, 1.3f, 0.0f, true, false, 18.5f, 10.2f)
                    close()
                }
            }
            return _talosbrowser!!
        }
}

val TalosMic: ImageVector get() = TalosIcons.TalosMic
val TalosCamera: ImageVector get() = TalosIcons.TalosCamera
val TalosHub: ImageVector get() = TalosIcons.TalosHub
val TalosBoard: ImageVector get() = TalosIcons.TalosBoard
val TalosVault: ImageVector get() = TalosIcons.TalosVault
val TalosBrowser: ImageVector get() = TalosIcons.TalosBrowser
