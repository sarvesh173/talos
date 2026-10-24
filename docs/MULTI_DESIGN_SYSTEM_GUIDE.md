# Unified Multi-Design System Master Guide: M3, Carbon, Fluent 2, & Atlassian

> **Target Platform:** Android Jetpack Compose & Enterprise Applications (TALOS)  
> **Synthesized Version:** 2026.1  
> **Source Systems:** 
> 1. Google Material Design 3 (M3 Expressive) (`m3.material.io`)
> 2. IBM Carbon Design System (`carbondesignsystem.com`)
> 3. Microsoft Fluent 2 (`fluent2.microsoft.design`)
> 4. Atlassian Design System (`atlassian.design`)

---

## Executive Summary & Architectural Vision

This master guide establishes a unified, multi-system design framework tailored for high-performance enterprise applications and mobile architectures (such as TALOS). By synthesizing the rigorous engineering precision of **IBM Carbon**, the calm depth and specular lighting of **Microsoft Fluent 2**, the tonal elevation and dynamic color tokens of **Google Material Design 3 (M3 Expressive)**, and the progressive disclosure and onboarding clarity of **Atlassian**, this guide provides concrete implementation rules for Jetpack Compose.

---

## 1. Google Material Design 3 (M3 Expressive)

### Core Philosophies
- **Dynamic & Tonal Elevation**: Surfaces are defined by tonal luminance steps rather than harsh drop shadows, creating a natural sense of hierarchy in both light and dark modes.
- **5-Tier Surface Hierarchy (`surfaceContainer` tokens)**:
  - `surfaceContainerLowest`: Deepest background plane (Pure OLED `#000000` or `#F8FAFC`).
  - `surfaceContainerLow`: Secondary background canvas for grouped content blocks.
  - `surfaceContainer`: Standard resting card container (`#0C1017` / `#FFFFFF`).
  - `surfaceContainerHigh`: Elevated interactive modals, tooltips, and floating action popovers (`#131923` / `#F1F5F9`).
  - `surfaceContainerHighest`: Highlighted chips, action pills, and active selection states (`#1A2230` / `#E2E8F0`).
- **Expressive Corner Radii**:
  - Small elements & buttons: `8.dp` – `12.dp`
  - Cards & Standard Containers: `20.dp` – `28.dp` (`ShapeLargeIncreased`)
  - Hero Dialogs & Bottom Sheets: `32.dp` (`ShapeExtraLargeIncreased`)
  - Full Pills, Badges, & Avatars: `CircleShape` (`999.dp`)
- **Motion & Easing Tokens**:
  - *Emphasized Decelerate*: `CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.00f)` — initial swift kinetic energy gliding into a graceful landing.
  - *Emphasized Accelerate*: `CubicBezierEasing(0.30f, 0.00f, 1.00f, 1.00f)` — swift departure for dismissing surfaces.

---

## 2. IBM Carbon Design System

### Core Philosophies
- **Engineering Precision & Machined Aesthetics**: Clean mathematical alignment, 1px/1.5px subtle metallic borders, and uncompromised structural grid discipline.
- **Dark Mode Discipline**: High-contrast contrast ratios (minimum 4.5:1 for body text, 3:1 for graphical boundaries) adhering strictly to WCAG 2.2 AAA guidelines.
- **Socket Anatomy for Emblems & Badging**:
  - Keep logo/emblem icons inside dedicated high-contrast obsidian sockets (`#0F172A`) with subtle accent borders (`1.dp`) to guarantee 100% visibility regardless of background lightness.
- **Information Density**: Lead with structured tabular and metric data, eliminate fluffy decorative noise, and keep status indicators crisp and legible.

---

## 3. Microsoft Fluent 2

### Core Philosophies
- **Cohesion & Calm Depth**: Subtle specular gradients (`metallicBorderBrush`) imitating brushed anodized metal, subtle reflections, or frosted acrylic glass.
- **Restful OLED Dark Mode**: Pure deep obsidian blacks paired with soft slate and indigo accents, preventing retina fatigue during long continuous operations.
- **In-Place Transitions**: Prefer depth-based scale and fade transitions over disruptive horizontal sliding cards when changing core application contexts.

---

## 4. Atlassian Design System

### Core Philosophies
- **Progressive Disclosure in Onboarding Wizards**:
  - Never overwhelm the user with all configuration steps simultaneously.
  - Clear step indicators with animated progress fill.
  - Each step has one primary job: `Discover` -> `Verify` -> `Token` -> `Complete`.
- **Affirmative Feedback**:
  - When copying tokens or completing tasks, show instant animated confirmation (`AnimatedVisibility` with expand/collapse) without layout jumping.
  - Action buttons must state their exact outcome (e.g. "Confirm Mesh & Continue" rather than generic "Next").

---

## 5. Jetpack Compose Implementation Guide (TALOS)

### A. Theme & Token Architecture
```kotlin
@Composable
fun TalosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TalosDarkColorScheme else TalosLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TalosTypography,
        shapes = TalosShapes,
        content = content
    )
}
```

### B. Surface Container Hierarchy Implementation
```kotlin
val LocalSurfaceContainerLowest = compositionLocalOf { Color.Unspecified }
val LocalSurfaceContainerLow = compositionLocalOf { Color.Unspecified }
val LocalSurfaceContainer = compositionLocalOf { Color.Unspecified }
val LocalSurfaceContainerHigh = compositionLocalOf { Color.Unspecified }
val LocalSurfaceContainerHighest = compositionLocalOf { Color.Unspecified }
```

### C. Motion & Easing Specifications
```kotlin
object TalosMotion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.00f)
    val EmphasizedAccelerate = CubicBezierEasing(0.30f, 0.00f, 1.00f, 1.00f)
    const val StandardDurationMillis = 350
}
```

---
*Generated and synthesized autonomously for TALOS framework verification.*
