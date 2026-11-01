package com.sarvesh173.talos.ui

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * TALOS High-Precision Vector Iconography Suite (v3.0)
 *
 * Grounded in Material 3 Expressive & Metallic Titanium aesthetics:
 * - Direct 24x24dp viewport coordinate space
 * - Precision optical geometry, smooth geometric arcs, multi-layered silhouettes
 * - Zero amateur squircle containment boxes: icons stand bold, clear, and tactile
 */
object TalosIcons {

    private var _talosmic: ImageVector? = null

    val TalosMic: ImageVector
        get() {
            if (_talosmic != null) return _talosmic!!
            _talosmic = materialIcon(name = "TalosMic") {
                // Studio Condenser Capsule & Diaphragm Grille
                materialPath(pathFillType = PathFillType.NonZero) {
                    // Capsule Head (Smooth Pill Geometry)
                    moveTo(12.0f, 2.0f)
                    curveTo(14.21f, 2.0f, 16.0f, 3.79f, 16.0f, 6.0f)
                    lineTo(16.0f, 11.5f)
                    curveTo(16.0f, 13.71f, 14.21f, 15.5f, 12.0f, 15.5f)
                    curveTo(9.79f, 15.5f, 8.0f, 13.71f, 8.0f, 11.5f)
                    lineTo(8.0f, 6.0f)
                    curveTo(8.0f, 3.79f, 9.79f, 2.0f, 12.0f, 2.0f)
                    close()

                    // Precision Acoustic Louver Inset (Negative Space Cut)
                    moveTo(9.5f, 6.0f)
                    lineTo(14.5f, 6.0f)
                    lineTo(14.5f, 7.2f)
                    lineTo(9.5f, 7.2f)
                    close()

                    moveTo(9.5f, 9.0f)
                    lineTo(14.5f, 9.0f)
                    lineTo(14.5f, 10.2f)
                    lineTo(9.5f, 10.2f)
                    close()

                    // Studio Shockmount Suspension Cradle & Pivot Stem
                    moveTo(5.5f, 10.5f)
                    curveTo(6.05f, 10.5f, 6.5f, 10.95f, 6.5f, 11.5f)
                    curveTo(6.5f, 14.54f, 8.96f, 17.0f, 12.0f, 17.0f)
                    curveTo(15.04f, 17.0f, 17.5f, 14.54f, 17.5f, 11.5f)
                    curveTo(17.5f, 10.95f, 17.95f, 10.5f, 18.5f, 10.5f)
                    curveTo(19.05f, 10.5f, 19.5f, 10.95f, 19.5f, 11.5f)
                    curveTo(19.5f, 15.28f, 16.65f, 18.41f, 13.0f, 18.93f)
                    lineTo(13.0f, 20.2f)
                    lineTo(16.0f, 20.2f)
                    curveTo(16.55f, 20.2f, 17.0f, 20.65f, 17.0f, 21.2f)
                    curveTo(17.0f, 21.75f, 16.55f, 22.2f, 16.0f, 22.2f)
                    lineTo(8.0f, 22.2f)
                    curveTo(7.45f, 22.2f, 7.0f, 21.75f, 7.0f, 21.2f)
                    curveTo(7.0f, 20.65f, 7.45f, 20.2f, 8.0f, 20.2f)
                    lineTo(11.0f, 20.2f)
                    lineTo(11.0f, 18.93f)
                    curveTo(7.35f, 18.41f, 4.5f, 15.28f, 4.5f, 11.5f)
                    curveTo(4.5f, 10.95f, 4.95f, 10.5f, 5.5f, 10.5f)
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
                // Precision Rangefinder Camera with Optical Lens Ring & Sensor Dot
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    // Outer Magnesium Alloy Chassis with Contoured Prism Top
                    moveTo(8.5f, 4.0f)
                    curveTo(8.85f, 4.0f, 9.18f, 4.18f, 9.38f, 4.48f)
                    lineTo(10.5f, 6.0f)
                    lineTo(19.5f, 6.0f)
                    curveTo(21.16f, 6.0f, 22.5f, 7.34f, 22.5f, 9.0f)
                    lineTo(22.5f, 18.0f)
                    curveTo(22.5f, 19.66f, 21.16f, 21.0f, 19.5f, 21.0f)
                    lineTo(4.5f, 21.0f)
                    curveTo(2.84f, 21.0f, 1.5f, 19.66f, 1.5f, 18.0f)
                    lineTo(1.5f, 9.0f)
                    curveTo(1.5f, 7.34f, 2.84f, 6.0f, 4.5f, 6.0f)
                    lineTo(6.8f, 6.0f)
                    lineTo(7.62f, 4.48f)
                    curveTo(7.82f, 4.18f, 8.15f, 4.0f, 8.5f, 4.0f)
                    close()

                    // Concentric Multi-Coated Lens Barrel (Outer Negative Inset)
                    moveTo(12.0f, 9.0f)
                    curveTo(9.51f, 9.0f, 7.5f, 11.01f, 7.5f, 13.5f)
                    curveTo(7.5f, 15.99f, 9.51f, 18.0f, 12.0f, 18.0f)
                    curveTo(14.49f, 18.0f, 16.5f, 15.99f, 16.5f, 13.5f)
                    curveTo(16.5f, 11.01f, 14.49f, 9.0f, 12.0f, 9.0f)
                    close()

                    // Internal Optical Aperture Element (Positive Gleam)
                    moveTo(12.0f, 11.2f)
                    curveTo(13.27f, 11.2f, 14.3f, 12.23f, 14.3f, 13.5f)
                    curveTo(14.3f, 14.77f, 13.27f, 15.8f, 12.0f, 15.8f)
                    curveTo(10.73f, 15.8f, 9.7f, 14.77f, 9.7f, 13.5f)
                    curveTo(9.7f, 12.23f, 10.73f, 11.2f, 12.0f, 11.2f)
                    close()

                    // Knurled Mode Dial / Flash Status Pupil
                    moveTo(19.0f, 8.5f)
                    curveTo(19.55f, 8.5f, 20.0f, 8.95f, 20.0f, 9.5f)
                    curveTo(20.0f, 10.05f, 19.55f, 10.5f, 19.0f, 10.5f)
                    curveTo(18.45f, 10.5f, 18.0f, 10.05f, 18.0f, 9.5f)
                    curveTo(18.0f, 8.95f, 18.45f, 8.5f, 19.0f, 8.5f)
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
                // Tri-Orbital Swarm Topology with Core Nexus and Mechanical Bus Links
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    // Central Nexus Sovereign Core
                    moveTo(12.0f, 8.5f)
                    curveTo(13.93f, 8.5f, 15.5f, 10.07f, 15.5f, 12.0f)
                    curveTo(15.5f, 13.93f, 13.93f, 15.5f, 12.0f, 15.5f)
                    curveTo(10.07f, 15.5f, 8.5f, 13.93f, 8.5f, 12.0f)
                    curveTo(8.5f, 10.07f, 10.07f, 8.5f, 12.0f, 8.5f)
                    close()

                    moveTo(12.0f, 10.3f)
                    curveTo(11.06f, 10.3f, 10.3f, 11.06f, 10.3f, 12.0f)
                    curveTo(10.3f, 12.94f, 11.06f, 13.7f, 12.0f, 13.7f)
                    curveTo(12.94f, 13.7f, 13.7f, 12.94f, 13.7f, 12.0f)
                    curveTo(13.7f, 11.06f, 12.94f, 10.3f, 12.0f, 10.3f)
                    close()

                    // Node 1 (Alpha Apex)
                    moveTo(12.0f, 1.8f)
                    curveTo(13.49f, 1.8f, 14.7f, 3.01f, 14.7f, 4.5f)
                    curveTo(14.7f, 5.99f, 13.49f, 7.2f, 12.0f, 7.2f)
                    curveTo(10.51f, 7.2f, 9.3f, 5.99f, 9.3f, 4.5f)
                    curveTo(9.3f, 3.01f, 10.51f, 1.8f, 12.0f, 1.8f)
                    close()

                    // Node 2 (Beta South-West)
                    moveTo(4.5f, 14.8f)
                    curveTo(5.99f, 14.8f, 7.2f, 16.01f, 7.2f, 17.5f)
                    curveTo(7.2f, 18.99f, 5.99f, 20.2f, 4.5f, 20.2f)
                    curveTo(3.01f, 20.2f, 1.8f, 18.99f, 1.8f, 17.5f)
                    curveTo(1.8f, 16.01f, 3.01f, 14.8f, 4.5f, 14.8f)
                    close()

                    // Node 3 (Gamma South-East)
                    moveTo(19.5f, 14.8f)
                    curveTo(20.99f, 14.8f, 22.2f, 16.01f, 22.2f, 17.5f)
                    curveTo(22.2f, 18.99f, 20.99f, 20.2f, 19.5f, 20.2f)
                    curveTo(18.01f, 20.2f, 16.8f, 18.99f, 16.8f, 17.5f)
                    curveTo(16.8f, 16.01f, 18.01f, 14.8f, 19.5f, 14.8f)
                    close()
                }

                // Bus Traces (Interconnect Rails)
                materialPath(pathFillType = PathFillType.NonZero) {
                    // Apex Bus
                    moveTo(11.25f, 7.1f)
                    lineTo(12.75f, 7.1f)
                    lineTo(12.75f, 8.6f)
                    lineTo(11.25f, 8.6f)
                    close()

                    // South-West Bus
                    moveTo(6.8f, 16.1f)
                    lineTo(9.2f, 14.3f)
                    lineTo(10.1f, 15.5f)
                    lineTo(7.7f, 17.3f)
                    close()

                    // South-East Bus
                    moveTo(14.8f, 14.3f)
                    lineTo(17.2f, 16.1f)
                    lineTo(16.3f, 17.3f)
                    lineTo(13.9f, 15.5f)
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
                // Architect Drafting Slate with Active Stylus Pen
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    // Drafting Tablet Surface
                    moveTo(3.5f, 3.0f)
                    lineTo(16.5f, 3.0f)
                    curveTo(17.88f, 3.0f, 19.0f, 4.12f, 19.0f, 5.5f)
                    lineTo(19.0f, 14.5f)
                    curveTo(19.0f, 15.88f, 17.88f, 17.0f, 16.5f, 17.0f)
                    lineTo(3.5f, 17.0f)
                    curveTo(2.12f, 17.0f, 1.0f, 15.88f, 1.0f, 14.5f)
                    lineTo(1.0f, 5.5f)
                    curveTo(1.0f, 4.12f, 2.12f, 3.0f, 3.5f, 3.0f)
                    close()

                    // Active Glass Canvas Working Window
                    moveTo(3.2f, 5.2f)
                    lineTo(3.2f, 14.8f)
                    lineTo(16.8f, 14.8f)
                    lineTo(16.8f, 5.2f)
                    close()

                    // Tablet Stand Brackets
                    moveTo(5.0f, 19.0f)
                    lineTo(9.0f, 19.0f)
                    lineTo(9.0f, 20.8f)
                    lineTo(5.0f, 20.8f)
                    close()

                    moveTo(13.0f, 19.0f)
                    lineTo(17.0f, 19.0f)
                    lineTo(17.0f, 20.8f)
                    lineTo(13.0f, 20.8f)
                    close()
                }

                // Angled Precision Stylus Nib
                materialPath(pathFillType = PathFillType.NonZero) {
                    moveTo(21.2f, 6.2f)
                    curveTo(21.98f, 6.98f, 21.98f, 8.24f, 21.2f, 9.02f)
                    lineTo(15.8f, 14.42f)
                    lineTo(12.5f, 15.2f)
                    lineTo(13.28f, 11.9f)
                    lineTo(18.68f, 6.5f)
                    curveTo(19.46f, 5.72f, 20.72f, 5.72f, 21.5f, 6.5f)
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
                // Titanium Cyber Aegis Shield & Hardened Biometric Tumbler
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    // Outer Faceted Vault Shell
                    moveTo(12.0f, 1.5f)
                    lineTo(21.5f, 5.5f)
                    lineTo(21.5f, 16.5f)
                    lineTo(12.0f, 22.5f)
                    lineTo(2.5f, 16.5f)
                    lineTo(2.5f, 5.5f)
                    close()

                    // Recessed Armor Perimeter Plate
                    moveTo(12.0f, 3.8f)
                    lineTo(4.5f, 7.0f)
                    lineTo(4.5f, 15.3f)
                    lineTo(12.0f, 20.1f)
                    lineTo(19.5f, 15.3f)
                    lineTo(19.5f, 7.0f)
                    close()

                    // Central Rotary Combination Lock Tumbler
                    moveTo(12.0f, 8.0f)
                    curveTo(10.07f, 8.0f, 8.5f, 9.57f, 8.5f, 11.5f)
                    curveTo(8.5f, 12.98f, 9.42f, 14.24f, 10.7f, 14.74f)
                    lineTo(10.2f, 17.0f)
                    lineTo(13.8f, 17.0f)
                    lineTo(13.3f, 14.74f)
                    curveTo(14.58f, 14.24f, 15.5f, 12.98f, 15.5f, 11.5f)
                    curveTo(15.5f, 9.57f, 13.93f, 8.0f, 12.0f, 8.0f)
                    close()

                    // Keyway Core
                    moveTo(12.0f, 10.2f)
                    curveTo(12.72f, 10.2f, 13.3f, 10.78f, 13.3f, 11.5f)
                    curveTo(13.3f, 12.22f, 12.72f, 12.8f, 12.0f, 12.8f)
                    curveTo(11.28f, 12.8f, 10.7f, 12.22f, 10.7f, 11.5f)
                    curveTo(10.7f, 10.78f, 11.28f, 10.2f, 12.0f, 10.2f)
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
                // Quantum Obscura Web Surface with Isometric Coordinate Geodesics
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    // Spherical Boundary Rim
                    moveTo(12.0f, 2.0f)
                    curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                    curveTo(2.0f, 17.52f, 6.48f, 22.0f, 12.0f, 22.0f)
                    curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                    curveTo(22.0f, 6.48f, 17.52f, 2.0f, 12.0f, 2.0f)
                    close()

                    // Outer Lens Cavity
                    moveTo(12.0f, 3.8f)
                    curveTo(16.53f, 3.8f, 20.2f, 7.47f, 20.2f, 12.0f)
                    curveTo(20.2f, 16.53f, 16.53f, 20.2f, 12.0f, 20.2f)
                    curveTo(7.47f, 20.2f, 3.8f, 16.53f, 3.8f, 12.0f)
                    curveTo(3.8f, 7.47f, 7.47f, 3.8f, 12.0f, 3.8f)
                    close()

                    // Central Coordinate Meridian
                    moveTo(12.0f, 5.0f)
                    curveTo(10.2f, 5.0f, 8.8f, 8.13f, 8.8f, 12.0f)
                    curveTo(8.8f, 15.87f, 10.2f, 19.0f, 12.0f, 19.0f)
                    curveTo(13.8f, 19.0f, 15.2f, 15.87f, 15.2f, 12.0f)
                    curveTo(15.2f, 8.13f, 13.8f, 5.0f, 12.0f, 5.0f)
                    close()

                    moveTo(12.0f, 6.8f)
                    curveTo(12.8f, 6.8f, 13.6f, 9.1f, 13.6f, 12.0f)
                    curveTo(13.6f, 14.9f, 12.8f, 17.2f, 12.0f, 17.2f)
                    curveTo(11.2f, 17.2f, 10.4f, 14.9f, 10.4f, 12.0f)
                    curveTo(10.4f, 9.1f, 11.2f, 6.8f, 12.0f, 6.8f)
                    close()
                }

                // Equatorial Navigation Ribbon
                materialPath(pathFillType = PathFillType.NonZero) {
                    moveTo(3.9f, 11.2f)
                    lineTo(20.1f, 11.2f)
                    lineTo(20.1f, 12.8f)
                    lineTo(3.9f, 12.8f)
                    close()
                }
            }
            return _talosbrowser!!
        }

    private var _taloscode: ImageVector? = null

    /** Precision chevron pair with motional cursor rail — source-diff / code module. */
    val TalosCode: ImageVector
        get() {
            if (_taloscode != null) return _taloscode!!
            _taloscode = materialIcon(name = "TalosCode") {
                materialPath(pathFillType = PathFillType.NonZero) {
                    moveTo(8.6f, 7.2f)
                    lineTo(3.6f, 12.0f)
                    lineTo(8.6f, 16.8f)
                    lineTo(9.9f, 15.5f)
                    lineTo(6.2f, 12.0f)
                    lineTo(9.9f, 8.5f)
                    close()
                    moveTo(15.4f, 7.2f)
                    lineTo(20.4f, 12.0f)
                    lineTo(15.4f, 16.8f)
                    lineTo(14.1f, 15.5f)
                    lineTo(17.8f, 12.0f)
                    lineTo(14.1f, 8.5f)
                    close()
                    // Central motional cursor rail
                    moveTo(13.7f, 5.9f)
                    lineTo(10.2f, 18.1f)
                    lineTo(11.8f, 18.4f)
                    lineTo(15.3f, 6.2f)
                    close()
                }
            }
            return _taloscode!!
        }

    private var _talosterminal: ImageVector? = null

    /** Machined console window with prompt caret and output rail — CLI governance. */
    val TalosTerminal: ImageVector
        get() {
            if (_talosterminal != null) return _talosterminal!!
            _talosterminal = materialIcon(name = "TalosTerminal") {
                materialPath(pathFillType = PathFillType.NonZero) {
                    moveTo(4.4f, 4.6f)
                    curveTo(3.2f, 4.6f, 2.4f, 5.4f, 2.4f, 6.6f)
                    lineTo(2.4f, 17.4f)
                    curveTo(2.4f, 18.6f, 3.2f, 19.4f, 4.4f, 19.4f)
                    lineTo(19.6f, 19.4f)
                    curveTo(20.8f, 19.4f, 21.6f, 18.6f, 21.6f, 17.4f)
                    lineTo(21.6f, 6.6f)
                    curveTo(21.6f, 5.4f, 20.8f, 4.6f, 19.6f, 4.6f)
                    close()
                    // Console face inset
                    moveTo(4.6f, 6.4f)
                    lineTo(19.4f, 6.4f)
                    lineTo(19.4f, 17.6f)
                    lineTo(4.6f, 17.6f)
                    close()
                }
                materialPath(pathFillType = PathFillType.NonZero) {
                    // Prompt caret
                    moveTo(6.9f, 9.3f)
                    lineTo(9.4f, 11.7f)
                    lineTo(6.9f, 14.1f)
                    lineTo(7.9f, 15.2f)
                    lineTo(11.5f, 11.7f)
                    lineTo(7.9f, 8.2f)
                    close()
                    // Output rail
                    moveTo(12.9f, 9.0f)
                    lineTo(17.4f, 9.0f)
                    lineTo(17.4f, 10.4f)
                    lineTo(12.9f, 10.4f)
                    close()
                    moveTo(12.9f, 13.1f)
                    lineTo(16.2f, 13.1f)
                    lineTo(16.2f, 14.5f)
                    lineTo(12.9f, 14.5f)
                    close()
                }
            }
            return _talosterminal!!
        }

    private var _talospalette: ImageVector? = null

    /** Aperture ring with three pigment wells — Material You theme selection. */
    val TalosPalette: ImageVector
        get() {
            if (_talospalette != null) return _talospalette!!
            _talospalette = materialIcon(name = "TalosPalette") {
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    moveTo(12.0f, 2.6f)
                    curveTo(17.19f, 2.6f, 21.4f, 6.81f, 21.4f, 12.0f)
                    curveTo(21.4f, 17.19f, 17.19f, 21.4f, 12.0f, 21.4f)
                    curveTo(6.81f, 21.4f, 2.6f, 17.19f, 2.6f, 12.0f)
                    curveTo(2.6f, 6.81f, 6.81f, 2.6f, 12.0f, 2.6f)
                    close()
                    moveTo(12.0f, 4.6f)
                    curveTo(7.92f, 4.6f, 4.6f, 7.92f, 4.6f, 12.0f)
                    curveTo(4.6f, 16.08f, 7.92f, 19.4f, 12.0f, 19.4f)
                    curveTo(16.08f, 19.4f, 19.4f, 16.08f, 19.4f, 12.0f)
                    curveTo(19.4f, 7.92f, 16.08f, 4.6f, 12.0f, 4.6f)
                    close()
                    // Pigment well alpha
                    moveTo(8.2f, 8.2f)
                    curveTo(9.0f, 8.2f, 9.6f, 8.8f, 9.6f, 9.6f)
                    curveTo(9.6f, 10.4f, 9.0f, 11.0f, 8.2f, 11.0f)
                    curveTo(7.4f, 11.0f, 6.8f, 10.4f, 6.8f, 9.6f)
                    curveTo(6.8f, 8.8f, 7.4f, 8.2f, 8.2f, 8.2f)
                    close()
                    moveTo(12.0f, 13.0f)
                    curveTo(12.8f, 13.0f, 13.4f, 13.6f, 13.4f, 14.4f)
                    curveTo(13.4f, 15.2f, 12.8f, 15.8f, 12.0f, 15.8f)
                    curveTo(11.2f, 15.8f, 10.6f, 15.2f, 10.6f, 14.4f)
                    curveTo(10.6f, 13.6f, 11.2f, 13.0f, 12.0f, 13.0f)
                    close()
                    moveTo(16.0f, 7.9f)
                    curveTo(16.8f, 7.9f, 17.4f, 8.5f, 17.4f, 9.3f)
                    curveTo(17.4f, 10.1f, 16.8f, 10.7f, 16.0f, 10.7f)
                    curveTo(15.2f, 10.7f, 14.6f, 10.1f, 14.6f, 9.3f)
                    curveTo(14.6f, 8.5f, 15.2f, 7.9f, 16.0f, 7.9f)
                    close()
                }
            }
            return _talospalette!!
        }

    private var _talossparkle: ImageVector? = null

    /** Generative four-point emission star with satellite spark — AI image studio. */
    val TalosSparkle: ImageVector
        get() {
            if (_talossparkle != null) return _talossparkle!!
            _talossparkle = materialIcon(name = "TalosSparkle") {
                materialPath(pathFillType = PathFillType.NonZero) {
                    moveTo(11.0f, 2.6f)
                    curveTo(11.9f, 7.6f, 14.4f, 10.1f, 19.4f, 11.0f)
                    curveTo(14.4f, 11.9f, 11.9f, 14.4f, 11.0f, 19.4f)
                    curveTo(10.1f, 14.4f, 7.6f, 11.9f, 2.6f, 11.0f)
                    curveTo(7.6f, 10.1f, 10.1f, 7.6f, 11.0f, 2.6f)
                    close()
                    // Satellite spark
                    moveTo(18.4f, 14.0f)
                    curveTo(18.8f, 16.1f, 19.7f, 17.0f, 21.8f, 17.4f)
                    curveTo(19.7f, 17.8f, 18.8f, 18.7f, 18.4f, 20.8f)
                    curveTo(18.0f, 18.7f, 17.1f, 17.8f, 15.0f, 17.4f)
                    curveTo(17.1f, 17.0f, 18.0f, 16.1f, 18.4f, 14.0f)
                    close()
                }
            }
            return _talossparkle!!
        }

    private var _talospin: ImageVector? = null

    /** Faceted map pin with caliper core — location sharing. */
    val TalosPin: ImageVector
        get() {
            if (_talospin != null) return _talospin!!
            _talospin = materialIcon(name = "TalosPin") {
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    moveTo(12.0f, 2.2f)
                    curveTo(7.6f, 2.2f, 4.2f, 5.6f, 4.2f, 9.9f)
                    curveTo(4.2f, 15.1f, 12.0f, 21.8f, 12.0f, 21.8f)
                    curveTo(12.0f, 21.8f, 19.8f, 15.1f, 19.8f, 9.9f)
                    curveTo(19.8f, 5.6f, 16.4f, 2.2f, 12.0f, 2.2f)
                    close()
                    moveTo(12.0f, 6.4f)
                    curveTo(14.1f, 6.4f, 15.7f, 8.0f, 15.7f, 10.1f)
                    curveTo(15.7f, 12.2f, 14.1f, 13.8f, 12.0f, 13.8f)
                    curveTo(9.9f, 13.8f, 8.3f, 12.2f, 8.3f, 10.1f)
                    curveTo(8.3f, 8.0f, 9.9f, 6.4f, 12.0f, 6.4f)
                    close()
                }
            }
            return _talospin!!
        }

    private var _taloschat: ImageVector? = null

    /** Apple HIG Chat Bubble — custom speech glyph. */
    val TalosChat: ImageVector
        get() {
            if (_taloschat != null) return _taloschat!!
            _taloschat = materialIcon(name = "TalosChat") {
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    moveTo(12.0f, 3.5f)
                    curveTo(6.8f, 3.5f, 3.2f, 7.0f, 3.2f, 11.5f)
                    curveTo(3.2f, 14.2f, 4.8f, 16.6f, 7.2f, 17.8f)
                    lineTo(6.4f, 20.8f)
                    curveTo(6.2f, 21.4f, 6.8f, 21.8f, 7.3f, 21.5f)
                    lineTo(11.2f, 19.3f)
                    curveTo(11.5f, 19.3f, 11.7f, 19.3f, 12.0f, 19.3f)
                    curveTo(17.2f, 19.3f, 20.8f, 15.8f, 20.8f, 11.5f)
                    curveTo(20.8f, 7.0f, 17.2f, 3.5f, 12.0f, 3.5f)
                    close()
                    // Inner knockout for outline feeling
                    moveTo(12.0f, 5.3f)
                    curveTo(16.2f, 5.3f, 19.0f, 8.1f, 19.0f, 11.5f)
                    curveTo(19.0f, 14.9f, 16.2f, 17.5f, 12.0f, 17.5f)
                    curveTo(11.7f, 17.5f, 11.4f, 17.5f, 11.1f, 17.4f)
                    lineTo(8.2f, 19.1f)
                    lineTo(8.8f, 16.8f)
                    curveTo(6.8f, 15.8f, 5.0f, 13.8f, 5.0f, 11.5f)
                    curveTo(5.0f, 8.1f, 7.8f, 5.3f, 12.0f, 5.3f)
                    close()
                }
            }
            return _taloschat!!
        }

    private var _talosbell: ImageVector? = null

    /** Apple HIG Notification Bell — custom chime bell. */
    val TalosBell: ImageVector
        get() {
            if (_talosbell != null) return _talosbell!!
            _talosbell = materialIcon(name = "TalosBell") {
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    moveTo(12.0f, 2.5f)
                    curveTo(12.6f, 2.5f, 13.0f, 2.9f, 13.0f, 3.5f)
                    lineTo(13.0f, 4.3f)
                    curveTo(16.5f, 5.1f, 18.0f, 8.5f, 18.0f, 13.0f)
                    lineTo(19.2f, 16.5f)
                    lineTo(4.8f, 16.5f)
                    lineTo(6.0f, 13.0f)
                    curveTo(6.0f, 8.5f, 7.5f, 5.1f, 11.0f, 4.3f)
                    lineTo(11.0f, 3.5f)
                    curveTo(11.0f, 2.9f, 11.4f, 2.5f, 12.0f, 2.5f)
                    close()
                    moveTo(12.0f, 6.1f)
                    curveTo(8.8f, 6.1f, 7.8f, 9.2f, 7.8f, 13.0f)
                    lineTo(7.1f, 14.8f)
                    lineTo(16.9f, 14.8f)
                    lineTo(16.2f, 13.0f)
                    curveTo(16.2f, 9.2f, 15.2f, 6.1f, 12.0f, 6.1f)
                    close()
                    // Clapper
                    moveTo(10.0f, 18.0f)
                    curveTo(10.0f, 19.3f, 10.9f, 20.5f, 12.0f, 20.5f)
                    curveTo(13.1f, 20.5f, 14.0f, 19.3f, 14.0f, 18.0f)
                    close()
                }
            }
            return _talosbell!!
        }

    private var _talosstorage: ImageVector? = null

    /** Apple HIG Storage / Cache Plates. */
    val TalosStorage: ImageVector
        get() {
            if (_talosstorage != null) return _talosstorage!!
            _talosstorage = materialIcon(name = "TalosStorage") {
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    moveTo(4.0f, 6.5f)
                    curveTo(4.0f, 4.5f, 7.5f, 3.5f, 12.0f, 3.5f)
                    curveTo(16.5f, 3.5f, 20.0f, 4.5f, 20.0f, 6.5f)
                    curveTo(20.0f, 8.5f, 16.5f, 9.5f, 12.0f, 9.5f)
                    curveTo(7.5f, 9.5f, 4.0f, 8.5f, 4.0f, 6.5f)
                    close()
                    moveTo(12.0f, 5.2f)
                    curveTo(8.0f, 5.2f, 5.8f, 6.0f, 5.8f, 6.5f)
                    curveTo(5.8f, 7.0f, 8.0f, 7.8f, 12.0f, 7.8f)
                    curveTo(16.0f, 7.8f, 18.2f, 7.0f, 18.2f, 6.5f)
                    curveTo(18.2f, 6.0f, 16.0f, 5.2f, 12.0f, 5.2f)
                    close()
                    // Lower layer
                    moveTo(4.0f, 11.5f)
                    lineTo(4.0f, 14.5f)
                    curveTo(4.0f, 16.5f, 7.5f, 17.5f, 12.0f, 17.5f)
                    curveTo(16.5f, 17.5f, 20.0f, 16.5f, 20.0f, 14.5f)
                    lineTo(20.0f, 11.5f)
                    curveTo(18.5f, 13.0f, 15.5f, 13.8f, 12.0f, 13.8f)
                    curveTo(8.5f, 13.8f, 5.5f, 13.0f, 4.0f, 11.5f)
                    close()
                }
            }
            return _talosstorage!!
        }

    private var _taloschevronright: ImageVector? = null

    /** Apple HIG 1.5dp Outline Navigation Chevron. */
    val TalosChevronRight: ImageVector
        get() {
            if (_taloschevronright != null) return _taloschevronright!!
            _taloschevronright = materialIcon(name = "TalosChevronRight") {
                materialPath(pathFillType = PathFillType.NonZero) {
                    moveTo(9.5f, 6.0f)
                    lineTo(15.5f, 12.0f)
                    lineTo(9.5f, 18.0f)
                    lineTo(8.2f, 16.7f)
                    lineTo(12.9f, 12.0f)
                    lineTo(8.2f, 7.3f)
                    close()
                }
            }
            return _taloschevronright!!
        }

    private var _talosbackarrow: ImageVector? = null

    /** Apple HIG 1.5dp Outline Back Arrow. */
    val TalosBackArrow: ImageVector
        get() {
            if (_talosbackarrow != null) return _talosbackarrow!!
            _talosbackarrow = materialIcon(name = "TalosBackArrow") {
                materialPath(pathFillType = PathFillType.NonZero) {
                    moveTo(14.5f, 6.0f)
                    lineTo(8.5f, 12.0f)
                    lineTo(14.5f, 18.0f)
                    lineTo(15.8f, 16.7f)
                    lineTo(11.1f, 12.0f)
                    lineTo(15.8f, 7.3f)
                    close()
                }
            }
            return _talosbackarrow!!
        }

    private var _talosmail: ImageVector? = null

    /** Apple HIG Mail Envelope. */
    val TalosMail: ImageVector
        get() {
            if (_talosmail != null) return _talosmail!!
            _talosmail = materialIcon(name = "TalosMail") {
                materialPath(pathFillType = PathFillType.EvenOdd) {
                    moveTo(4.0f, 5.0f)
                    lineTo(20.0f, 5.0f)
                    curveTo(21.1f, 5.0f, 21.8f, 5.7f, 21.8f, 6.8f)
                    lineTo(21.8f, 17.2f)
                    curveTo(21.8f, 18.3f, 21.1f, 19.0f, 20.0f, 19.0f)
                    lineTo(4.0f, 19.0f)
                    curveTo(2.9f, 19.0f, 2.2f, 18.3f, 2.2f, 17.2f)
                    lineTo(2.2f, 6.8f)
                    curveTo(2.2f, 5.7f, 2.9f, 5.0f, 4.0f, 5.0f)
                    close()
                    moveTo(4.2f, 6.8f)
                    lineTo(12.0f, 12.5f)
                    lineTo(19.8f, 6.8f)
                    close()
                }
            }
            return _talosmail!!
        }

    private var _talosplus: ImageVector? = null

    /** Apple HIG Plus action glyph (+). */
    val TalosPlus: ImageVector
        get() {
            if (_talosplus != null) return _talosplus!!
            _talosplus = materialIcon(name = "TalosPlus") {
                materialPath(pathFillType = PathFillType.NonZero) {
                    moveTo(12.0f, 5.0f)
                    curveTo(12.55f, 5.0f, 13.0f, 5.45f, 13.0f, 6.0f)
                    lineTo(13.0f, 11.0f)
                    lineTo(18.0f, 11.0f)
                    curveTo(18.55f, 11.0f, 19.0f, 11.45f, 19.0f, 12.0f)
                    curveTo(19.0f, 12.55f, 18.55f, 13.0f, 18.0f, 13.0f)
                    lineTo(13.0f, 13.0f)
                    lineTo(13.0f, 18.0f)
                    curveTo(13.0f, 18.55f, 12.55f, 19.0f, 12.0f, 19.0f)
                    curveTo(11.45f, 19.0f, 11.0f, 18.55f, 11.0f, 18.0f)
                    lineTo(11.0f, 13.0f)
                    lineTo(6.0f, 13.0f)
                    curveTo(5.45f, 13.0f, 5.0f, 12.55f, 5.0f, 12.0f)
                    curveTo(5.0f, 11.45f, 5.45f, 11.0f, 6.0f, 11.0f)
                    lineTo(11.0f, 11.0f)
                    lineTo(11.0f, 6.0f)
                    curveTo(11.0f, 5.45f, 11.45f, 5.0f, 12.0f, 5.0f)
                    close()
                }
            }
            return _talosplus!!
        }

    private var _talossendarrow: ImageVector? = null

    /** Apple iMessage Upward Circular Send Arrow (↑). */
    val TalosSendArrow: ImageVector
        get() {
            if (_talossendarrow != null) return _talossendarrow!!
            _talossendarrow = materialIcon(name = "TalosSendArrow") {
                materialPath(pathFillType = PathFillType.NonZero) {
                    moveTo(12.0f, 4.0f)
                    curveTo(12.35f, 4.0f, 12.65f, 4.15f, 12.85f, 4.4f)
                    lineTo(18.35f, 10.4f)
                    curveTo(18.75f, 10.8f, 18.75f, 11.45f, 18.35f, 11.85f)
                    curveTo(17.95f, 12.25f, 17.3f, 12.25f, 16.9f, 11.85f)
                    lineTo(12.75f, 7.3f)
                    lineTo(12.75f, 19.0f)
                    curveTo(12.75f, 19.55f, 12.3f, 20.0f, 11.75f, 20.0f)
                    curveTo(11.2f, 20.0f, 10.75f, 19.55f, 10.75f, 19.0f)
                    lineTo(10.75f, 7.3f)
                    lineTo(6.6f, 11.85f)
                    curveTo(6.2f, 12.25f, 5.55f, 12.25f, 5.15f, 11.85f)
                    curveTo(4.75f, 11.45f, 4.75f, 10.8f, 5.15f, 10.4f)
                    lineTo(10.65f, 4.4f)
                    curveTo(10.85f, 4.15f, 11.15f, 4.0f, 11.5f, 4.0f)
                    close()
                }
            }
            return _talossendarrow!!
        }

    private var _talosdoc: ImageVector? = null

    /** Machined sheet with folded corner and content rails — document context. */
    val TalosDoc: ImageVector
        get() {
            if (_talosdoc != null) return _talosdoc!!
            _talosdoc = materialIcon(name = "TalosDoc") {
                materialPath(pathFillType = PathFillType.NonZero) {
                    moveTo(5.6f, 2.4f)
                    lineTo(14.2f, 2.4f)
                    lineTo(18.4f, 6.6f)
                    lineTo(18.4f, 21.6f)
                    lineTo(5.6f, 21.6f)
                    close()
                    // Content rails knocked out
                    moveTo(8.0f, 9.2f)
                    lineTo(16.0f, 9.2f)
                    lineTo(16.0f, 10.6f)
                    lineTo(8.0f, 10.6f)
                    close()
                    moveTo(8.0f, 12.8f)
                    lineTo(16.0f, 12.8f)
                    lineTo(16.0f, 14.2f)
                    lineTo(8.0f, 14.2f)
                    close()
                    moveTo(8.0f, 16.4f)
                    lineTo(13.2f, 16.4f)
                    lineTo(13.2f, 17.8f)
                    lineTo(8.0f, 17.8f)
                    close()
                }
            }
            return _talosdoc!!
        }
}

val TalosMic: ImageVector get() = TalosIcons.TalosMic
val TalosCamera: ImageVector get() = TalosIcons.TalosCamera
val TalosHub: ImageVector get() = TalosIcons.TalosHub
val TalosBoard: ImageVector get() = TalosIcons.TalosBoard
val TalosVault: ImageVector get() = TalosIcons.TalosVault
val TalosBrowser: ImageVector get() = TalosIcons.TalosBrowser
val TalosCode: ImageVector get() = TalosIcons.TalosCode
val TalosTerminal: ImageVector get() = TalosIcons.TalosTerminal
val TalosPalette: ImageVector get() = TalosIcons.TalosPalette
val TalosSparkle: ImageVector get() = TalosIcons.TalosSparkle
val TalosPin: ImageVector get() = TalosIcons.TalosPin
val TalosDoc: ImageVector get() = TalosIcons.TalosDoc
val TalosChat: ImageVector get() = TalosIcons.TalosChat
val TalosBell: ImageVector get() = TalosIcons.TalosBell
val TalosStorage: ImageVector get() = TalosIcons.TalosStorage
val TalosChevronRight: ImageVector get() = TalosIcons.TalosChevronRight
val TalosBackArrow: ImageVector get() = TalosIcons.TalosBackArrow
val TalosMail: ImageVector get() = TalosIcons.TalosMail
val TalosPlus: ImageVector get() = TalosIcons.TalosPlus
val TalosSendArrow: ImageVector get() = TalosIcons.TalosSendArrow
