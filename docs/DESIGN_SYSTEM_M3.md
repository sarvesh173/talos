# TALOS Material 3 Expressive & Metallic Design System

> Authoritative design specification for TALOS — Material 3 Expressive (May 2025) + Metallic microgeometry.

## Table of Contents

- [Core Principles](#core-principles)
- [Color System (29+ Semantic Roles)](#color-system-29-semantic-roles)
- [Surface Container Hierarchy](#surface-container-hierarchy)
- [Tonal Elevation Math](#tonal-elevation-math)
- [Shape Scale](#shape-scale)
- [Typography Scale (15 + 15 Emphasized)](#typography-scale-15--15-emphasized)
- [Metallic Microgeometry](#metallic-microgeometry)
- [Anti-Slop Guidelines](#anti-slop-guidelines)
- [Platform Implementation Notes](#platform-implementation-notes)

---

## Core Principles (Google M3 & Apple HIG Alignment)

### 1. Semantic Tonal Depth over Artificial Drop Shadows

- Depth is communicated via **5 surface container tiers** (`surfaceContainerLowest` through `surfaceContainerHighest`) rather than zero-offset blurry neon drop shadows.
- On OLED displays, `surfaceContainerLowest` and `surface` are locked to **True Pure Black (`#000000`)**, ensuring inactive pixels turn off completely.

### 2. Specular Metallic Microgeometry

High-performance developer instruments feature **multi-stop linear brushed metal gradient borders** (`metallicBorderBrush()`):

- **Dark Mode:** `#94A3B8` (specular highlight 45%) → `#475569` (ambient midtone 25%) → `#1E2430` (recessed shadow 60%)
- **Light Mode:** `#CBD5E1` (specular highlight 80%) → `#94A3B8` (midtone 40%) → `#E2E8F0` (recessed shadow 90%)

Subtle chamfered corner geometry and 1dp hair-line bevels provide tactile hardware cues.

### 3. Anti-Slop Visual Discipline

- **No Radioactive Neon Halos:** Pure neon cyan/magenta halos (`#00E5FF`, `#FF00FF`) are strictly refused.
- **Dark Metallic Squircles for High-Contrast Emblems:** Complex vendor marks with white paths (e.g. OpenClaw lobster) are set within a dedicated dark slate (`#0F172A`) squircle tile with a 1dp brand border, guaranteeing 100% visibility in both Light Mode and Dark Mode.
- **Quadrant-Clamped Dialogue Bubbles:** Agent speech bubbles are anchored strictly within quadrant margins (below top nodes, above bottom nodes), preventing UI clipping across status bars, navigation rails, and center cards.

### 4. 8dp Spatial System (`MdSpacing`)

| Token | Value | Usage |
|-------|-------|-------|
| `xxs` | 4dp | Micro badge padding, inline dot gaps |
| `xs` | 8dp | Gaps between icon and label, chip padding |
| `sm` | 16dp | Card internal padding, screen edge margins on compact mobile |
| `md` | 24dp | Spacing between sections, canvas padding on medium/expanded tablets |
| `lg` | 32dp | Large container margins |
| `xl` | 48dp | Hero section separation |

---

## Color System (29+ Semantic Roles)

### Accent Colors

| Role | Dark Theme (OLED) | Light Theme (Editorial) | Purpose |
|------|-------------------|------------------------|---------|
| `primary` | `#6366F1` | `#6366F1` | High-emphasis fills, primary CTA buttons |
| `onPrimary` | `#FFFFFF` | `#FFFFFF` | Text & icons on primary fills |
| `primaryContainer` | `#312E81` | `#E0E7FF` | Prominent component containers |
| `onPrimaryContainer` | `#E0E7FF` | `#1E1B4B` | Text on primary containers |
| `secondary` | `#38BDF8` | `#0284C7` | Less prominent accents, active highlights |
| `onSecondary` | `#0C4A6E` | `#FFFFFF` | Text on secondary fills |
| `secondaryContainer` | `#0369A1` | `#E0F2FE` | Tonal buttons & filter chips |
| `onSecondaryContainer` | `#E0F2FE` | `#0C4A6E` | Text on secondary containers |
| `tertiary` | `#FFBF00` | `#D97706` | Complementary brand accents (Hermes Gold) |
| `onTertiary` | `#451A03` | `#FFFFFF` | Text on tertiary fills |
| `tertiaryContainer` | `#78350F` | `#FFEDD1` | Complementary container fills |
| `onTertiaryContainer` | `#FFF8E8` | `#3D1E00` | Text on tertiary containers |

### Error Colors (Static)

| Role | Dark Theme (OLED) | Light Theme (Editorial) | Purpose |
|------|-------------------|------------------------|---------|
| `error` | `#F2B8B5` | `#B3261E` | Attention-grabbing color for urgent elements |
| `onError` | `#601410` | `#FFFFFF` | Text & icons on error |
| `errorContainer` | `#8C1D18` | `#F9DEDC` | Error container fill |
| `onErrorContainer` | `#F9DEDC` | `#410E0B` | Text on error containers |

### Surface Colors

| Role | Dark Theme (OLED) | Light Theme (Editorial) | Purpose |
|------|-------------------|------------------------|---------|
| `surface` | `#000000` | `#FFFFFF` | Base canvas background |
| `onSurface` | `#F1F5F9` | `#0F172A` | Primary readable typography |
| `onSurfaceVariant` | `#94A3B8` | `#64748B` | Secondary metadata & timestamps |
| `surfaceContainerLowest` | `#000000` | `#FFFFFF` | Lowest elevation plane (OLED subpixel shutoff) |
| `surfaceContainerLow` | `#080B10` | `#F1F5F9` | Recessed container background |
| `surfaceContainer` | `#0C1017` | `#E2E8F0` | Default card plane |
| `surfaceContainerHigh` | `#131923` | `#CBD5E1` | High-emphasis cards, speech bubbles, modals |
| `surfaceContainerHighest` | `#1A2230` | `#94A3B8` | Master hardware core hub |
| `surfaceDim` | `#000000` | `#DED8E1` | Dimmest surface in both themes |
| `surfaceBright` | `#3B383E` | `#FEF7FF` | Brightest surface in both themes |

### Inverse Colors

| Role | Dark Theme (OLED) | Light Theme (Editorial) | Purpose |
|------|-------------------|------------------------|---------|
| `inverseSurface` | `#E6E0E9` | `#322F35` | Background for contrasting elements |
| `inverseOnSurface` | `#322F35` | `#F5EFF7` | Text on inverse-surface |
| `inversePrimary` | `#6750A4` | `#D0BCFF` | Actionable text on inverse-surface |

### Outline Colors

| Role | Dark Theme (OLED) | Light Theme (Editorial) | Purpose |
|------|-------------------|------------------------|---------|
| `outline` | `#334155` | `#64748B` | Interactive boundaries with 3:1 WCAG contrast |
| `outlineVariant` | `#1E2430` | `#E2E8F0` | Subtle decorative hairlines |

### Fixed Accent Colors (Add-on)

| Role | Token |
|------|-------|
| Primary Fixed | `--md-sys-color-primary-fixed` |
| Primary Fixed Dim | `--md-sys-color-primary-fixed-dim` |
| On Primary Fixed | `--md-sys-color-on-primary-fixed` |
| On Primary Fixed Variant | `--md-sys-color-on-primary-fixed-variant` |
| Secondary Fixed | `--md-sys-color-secondary-fixed` |
| Secondary Fixed Dim | `--md-sys-color-secondary-fixed-dim` |
| On Secondary Fixed | `--md-sys-color-on-secondary-fixed` |
| On Secondary Fixed Variant | `--md-sys-color-on-secondary-fixed-variant` |
| Tertiary Fixed | `--md-sys-color-tertiary-fixed` |
| Tertiary Fixed Dim | `--md-sys-color-tertiary-fixed-dim` |
| On Tertiary Fixed | `--md-sys-color-on-tertiary-fixed` |
| On Tertiary Fixed Variant | `--md-sys-color-on-tertiary-fixed-variant` |

---

## Surface Container Hierarchy

The 5 container levels create tonal elevation (no shadows). Each level increases/decreases tone by ~4–5%.

| Level | Container Token | Dark Theme | Light Theme | Use |
|-------|-----------------|------------|-------------|-----|
| 0 | `surface` | `#000000` | `#FFFFFF` | Background |
| 1 | `surfaceContainerLowest` | `#000000` | `#FFFFFF` | Lowest-emphasis cards, expanded grids |
| 2 | `surfaceContainerLow` | `#080B10` | `#F1F5F9` | Low-emphasis containers |
| 3 | `surfaceContainer` | `#0C1017` | `#E2E8F0` | Default containers (navigation areas) |
| 4 | `surfaceContainerHigh` | `#131923` | `#CBD5E1` | High-emphasis containers |
| 5 | `surfaceContainerHighest` | `#1A2230` | `#94A3B8` | Highest-emphasis containers (top layers) |

**Surface dim/bright:** Unlike regular `surface`, `surface-dim` and `surface-bright` maintain their relative brightness across themes. Use `surface-bright` for areas that should always be the lightest, `surface-dim` for areas that should always be the dimmest.

---

## Tonal Elevation Math

### Tonal Value Mapping (Light Scheme)

| Role | Palette | Tone |
|------|---------|------|
| Primary | Primary | 40 |
| On Primary | Primary | 100 |
| Primary Container | Primary | 90 |
| On Primary Container | Primary | 10 |
| Surface | Neutral | 98 |
| On Surface | Neutral | 10 |
| Surface Container Lowest | Neutral | 100 |
| Surface Container Low | Neutral | 96 |
| Surface Container | Neutral | 94 |
| Surface Container High | Neutral | 92 |
| Surface Container Highest | Neutral | 90 |
| Outline | Neutral-Variant | 50 |
| Outline Variant | Neutral-Variant | 80 |

### Tonal Value Mapping (Dark Scheme)

| Role | Palette | Tone |
|------|---------|------|
| Primary | Primary | 80 |
| On Primary | Primary | 20 |
| Primary Container | Primary | 30 |
| On Primary Container | Primary | 90 |
| Surface | Neutral | 6 |
| On Surface | Neutral | 90 |
| Surface Container Lowest | Neutral | 4 |
| Surface Container Low | Neutral | 10 |
| Surface Container | Neutral | 12 |
| Surface Container High | Neutral | 17 |
| Surface Container Highest | Neutral | 22 |
| Outline | Neutral-Variant | 60 |
| Outline Variant | Neutral-Variant | 30 |

### Elevation Through Container Hierarchy

| Elevation Level | Container Token | Tonal Offset |
|-----------------|-----------------|--------------|
| 0 | `surface` | 0% |
| 1 | `surfaceContainerLowest` | +2% |
| 2 | `surfaceContainerLow` | +4% |
| 3 | `surfaceContainer` | +6% |
| 4 | `surfaceContainerHigh` | +8% |
| 5 | `surfaceContainerHighest` | +10% |

### Component Elevation Mapping

| Level | DP Height | Use |
|-------|-----------|-----|
| 0 | 0dp | Most resting components |
| 1 | 1dp | Elevated cards, modal sheets |
| 2 | 3dp | Menus, nav bar, scrolled app bar |
| 3 | 6dp | FAB, dialogs, search, date/time pickers |
| 4 | 8dp | Hover/focus increase only |
| 5 | 12dp | Hover/focus increase only |

---

## Shape Scale

| Token | Value (dp) | Value (px) | Use |
|-------|-----------|------------|-----|
| `none` | 0 | 0px | — |
| `extraSmall` | 4dp | 4px | Snackbar, micro chips |
| `small` | 8dp | 8px | Text fields, chips, menus |
| `medium` | 12dp | 12px | Cards |
| `large` | 16dp | 16px | FAB, extended FAB, nav drawer |
| `largeIncreased` | 20dp | 20px | M3 Expressive agent cards |
| `extraLarge` | 28dp | 28px | Dialogs, bottom sheets |
| `extraLargeIncreased` | 32dp | 32px | Expressive large containers |
| `extraExtraLarge` | 48dp | 48px | Expressive large headlines |
| `full` | — | 9999px | Buttons, badges, pills, sliders |

### Component Shape Mapping

| Component | Default Shape Token |
|-----------|---------------------|
| Filled/outlined/tonal buttons | `full` |
| Icon buttons | `full` |
| Chips | `small` |
| Cards | `medium` |
| Dialogs | `extraLarge` |
| Text fields (top corners) | `small` |
| Menus | `small` |
| Navigation drawer (end corners) | `large` |
| Bottom sheets (top corners) | `extraLarge` |
| Snackbar | `extraSmall` |
| Badges | `full` |
| Sliders (handle) | `full` |
| Switch (track) | `full` |
| Tabs (indicator) | `full` |
| Search bar | `full` |

### Shape Morphing (Expressive)

Components can morph between shapes on interaction:

- Buttons morph between shapes on press
- Selected states can shift shapes
- Loading indicators use shape morphing for progress

**Platform notes:** Shape morphing is **not** in `@material/web` (maintenance-only; Expressive not on Web). **Jetpack Compose** is the primary implementation target. **Flutter:** check Flutter Material 3 / expressive docs. **Web:** approximate with CSS transitions on `border-radius`.

---

## Typography Scale (15 + 15 Emphasized)

### Baseline Type Scale

| Style | Font | Weight | Size (sp) | Line Height | Tracking |
|-------|------|--------|-----------|-------------|----------|
| Display Large | Roboto | 400 | 57 | 64sp | -0.25px |
| Display Medium | Roboto | 400 | 45 | 52sp | 0 |
| Display Small | Roboto | 400 | 36 | 44sp | 0 |
| Headline Large | Roboto | 400 | 32 | 40sp | 0 |
| Headline Medium | Roboto | 400 | 28 | 36sp | 0 |
| Headline Small | Roboto | 400 | 24 | 32sp | 0 |
| Title Large | Roboto | 400 | 22 | 28sp | 0 |
| Title Medium | Roboto | 500 | 16 | 24sp | 0.15px |
| Title Small | Roboto | 500 | 14 | 20sp | 0.1px |
| Body Large | Roboto | 400 | 16 | 24sp | 0.5px |
| Body Medium | Roboto | 400 | 14 | 20sp | 0.25px |
| Body Small | Roboto | 400 | 12 | 16sp | 0.4px |
| Label Large | Roboto | 500 | 14 | 20sp | 0.1px |
| Label Medium | Roboto | 500 | 12 | 16sp | 0.5px |
| Label Small | Roboto | 500 | 11 | 16sp | 0.5px |

### Emphasized Type Styles

Each baseline style has an emphasized variant with higher weight and minor tracking adjustments. Use for:

- Selected/active states in components
- Primary action buttons
- Headlines needing emphasis
- Unread/important content

**Token mapping:** Baseline `md.sys.typescale.body-large` ↔ Emphasized `md.sys.typescale.emphasized.body-large`

### Component Type Mapping

| Component | Type Style |
|-----------|-----------|
| Button labels | Label Large (emphasized) |
| Card titles | Title Medium |
| Card body | Body Medium |
| Top app bar titles | Title Large |
| Navigation labels | Label Medium |
| Dialog headlines | Headline Small |
| Dialog body | Body Medium |
| Chip labels | Label Large |
| Text field inputs | Body Large |
| Text field labels (floating) | Body Small |
| List headlines | Body Large |
| List supporting text | Body Medium |
| Snackbar text | Body Medium |
| Tooltip text | Body Small |
| Tab labels | Title Small |
| Badge counts | Label Small |

### CSS Custom Properties

```css
/* Example: Title Medium (baseline) */
--md-sys-typescale-title-medium-font: 'Roboto', sans-serif;
--md-sys-typescale-title-medium-weight: 500;
--md-sys-typescale-title-medium-size: 1rem;
--md-sys-typescale-title-medium-line-height: 1.5rem;
--md-sys-typescale-title-medium-tracking: 0.009375rem;

/* Example: Label Large (button) */
--md-sys-typescale-label-large-font: 'Roboto', sans-serif;
--md-sys-typescale-label-large-weight: 500;
--md-sys-typescale-label-large-size: 0.875rem;
--md-sys-typescale-label-large-line-height: 1.25rem;
--md-sys-typescale-label-large-tracking: 0.00625rem;
```

### Typeface Customization

MD3 uses two typeface roles:

- **Brand:** Used for Display and Headline styles (expression-focused)
- **Plain:** Used for Title, Body, and Label styles (readability-focused)

Both default to Roboto. To customize:

```css
:root {
  /* Brand typeface for display/headline */
  --md-ref-typeface-brand: 'Your Display Font', sans-serif;
  /* Plain typeface for body/label/title */
  --md-ref-typeface-plain: 'Your Body Font', sans-serif;
}
```

---

## Metallic Microgeometry

### Specular Highlight Brushes

Metallic surfaces need crisp, high-fidelity specular highlights to convey polish and quality.

| Token | CSS Value | Usage |
|-------|-----------|-------|
| `--md-sys-metallic-highlight-spread` | 0.5 – 1.0 | Highlight spread radius (px) |
| `--md-sys-metallic-highlight-strength` | 0.6 – 0.8 | Highlight opacity |
| `--md-sys-metallic-highlight-angle` | 35° – 45° | Highlight angle (CSS `linear-gradient`) |

**Implementation (CSS):**

```css
.metallic-surface {
  background: var(--md-sys-metallic-base-color);
  border-radius: var(--md-sys-shape-corner-medium, 12px);
  position: relative;
  overflow: hidden;
}

.metallic-surface::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 100%;
  background: linear-gradient(
    var(--md-sys-metallic-highlight-angle, 40deg),
    transparent 0%,
    var(--md-sys-metallic-highlight-spread, 0.5) 50%,
    transparent 100%
  );
  opacity: var(--md-sys-metallic-highlight-strength, 0.7);
  pointer-events: none;
}
```

### Brushed Chamfer Wells

Brushed chamfers create subtle topographic cues at element edges, mimicking machined hardware.

| Token | CSS Value | Usage |
|-------|-----------|-------|
| `--md-sys-metallic-chamfer-width` | 0.5 – 1px | Chamfer line width |
| `--md-sys-metallic-chamfer-offset` | 2px – 4px | Chamfer offset from edge |
| `--md-sys-metallic-chamfer-color` | Surface variant tone | Chamfer color |

**Implementation:**

```css
.metallic-chamfer {
  border-radius: var(--md-sys-shape-corner-medium, 12px);
  position: relative;
  padding: var(--md-sys-metallic-chamfer-offset);
}

.metallic-chamfer::after {
  content: '';
  position: absolute;
  top: var(--md-sys-metallic-chamfer-offset);
  left: var(--md-sys-metallic-chamfer-offset);
  right: var(--md-sys-metallic-chamfer-offset);
  bottom: var(--md-sys-metallic-chamfer-offset);
  border: 1px solid var(--md-sys-metallic-chamfer-color);
  border-radius: calc(var(--md-sys-shape-corner-medium, 12px) - var(--md-sys-metallic-chamfer-offset));
}
```

### Hardware Finish Tokens

Base tokens for metallic variants on different accent colors.

| Token | Light Variant | Dark Variant | Usage |
|-------|---------------|--------------|-------|
| `--md-sys-metallic-base-light` | +8% to primary | +10% to inverse-primary | Base highlight color |
| `--md-sys-metallic-base-dark` | +10% to primary | +12% to inverse-primary | Base highlight color |
| `--md-sys-metallic-chamfer-light` | +5% to primary | +7% to inverse-primary | Chamfer color light |
| `--md-sys-metallic-chamfer-dark` | +6% to primary | +8% to inverse-primary | Chamfer color dark |

**Implementing metallic variants:**

```css
:root {
  /* Metallic primary surface */
  --md-sys-metallic-primary-light: color-mix(in srgb, var(--md-sys-color-primary) 92%, white 8%);
  --md-sys-metallic-primary-dark: color-mix(in srgb, var(--md-sys-color-primary) 92%, black 8%);

  /* Metallic chamfer on primary */
  --md-sys-metallic-primary-chamfer-light: color-mix(in srgb, var(--md-sys-color-primary) 95%, white 5%);
  --md-sys-metallic-primary-chamfer-dark: color-mix(in srgb, var(--md-sys-color-primary) 94%, black 6%);
}
```

### Vendor Identity Matrix

| Agent | Official Vendor | Accent Color | Mark Description | Vector Source |
|-------|-----------------|--------------|------------------|---------------|
| **OpenClaw** | OpenClaw OSS | `#EA580C` | Multi-color crimson lobster | `ic_openclaw.xml` |
| **Claude Code** | Anthropic | `#D97757` | Terracotta 8-point sunburst spark | `ic_claude.xml` |
| **Hermes Agent** | Nous Research | `#FFBF00` | Symmetrical winged caduceus monogram | `ic_hermes.xml` |
| **Codex** | OpenAI | `#7A9DFF` | Electric lavender-blue cloud with `>_` terminal prompt | `ic_codex.xml` |
| **OpenCode** | OpenCode AI | `#3B82F6` | 3D Isometric Terminal Monogram | `ic_opencode.xml` |

---

## Anti-Slop Guidelines

### Zero Neon Halos

Never create glowing neon borders, glows, or halo effects around active states. MD3 Expressive's glow is subtle, not electric.

**Do:**

- Use `primary` container with clean `on-primary-container` text.
- Use elevated surface containers with no extra glow.

**Do Not:**

- Add `box-shadow: 0 0 10px var(--md-sys-color-primary)` around buttons.
- Add `text-shadow: 0 0 2px var(--md-sys-color-primary)` around active labels.
- Use neon `outline` colors for selection states.

### High-Contrast Dark Wells

For complex emblems, input fields, or high-emphasis containers, use deeper container tones to increase contrast against background.

**Rule:** Dark theme surfaces must never be `surface-container` with only `on-surface-variant` text. Use `surface-container-high` or `surface-container-highest` for:

- Primary action buttons (filled) on dark
- Input fields with complex labels
- High-emphasis cards in navigation areas
- Dialogs with extensive content

**Example (Dark Theme):**

```css
/* Do: High-contrast dark well */
.metallic-button-dark {
  background: var(--md-sys-color-surface-container-high, #2B2930);
  color: var(--md-sys-color-on-surface-container-high, #E6E0E9);
  border: 1px solid var(--md-sys-color-outline-variant, #49454F);
}

/* Do Not: Low-contrast dark well (violates accessibility) */
.bad-button {
  background: var(--md-sys-color-surface-container, #211F26);
  color: var(--md-sys-color-on-surface-variant, #CAC4D0);
}
```

### Forbidden Combinations

Never pair colors outside their intended pairs:

| Container/Fill | Text/Icon Color |
|----------------|-----------------|
| `primary` | `on-primary` |
| `primary-container` | `on-primary-container` |
| `secondary` | `on-secondary` |
| `secondary-container` | `on-secondary-container` |
| `tertiary` | `on-tertiary` |
| `tertiary-container` | `on-tertiary-container` |
| `error` | `on-error` |
| `error-container` | `on-error-container` |
| `surface` or `surface-container-*` | `on-surface` or `on-surface-variant` |
| `inverse-surface` | `inverse-on-surface` or `inverse-primary` |

Pairing outside these rules breaks MD3 contrast guarantees.

---

## Platform Implementation Notes

### Jetpack Compose (Primary Target)

Use `androidx.compose.material3` with `MaterialTheme`.

```kotlin
MaterialTheme(
  colorScheme = colorScheme, // from dynamicLightColorScheme / lightColorScheme
  typography = Typography(),
  shapes = Shapes(),
) {
  // Content
}
```

**Expressive motion:** Use `SpringSpec` and `spring()` APIs in Compose.

**Expressive shapes:** Use `FloatingShape` and `ControlShape` for buttons, use shape morphing with `animateFloatAsState`.

### Flutter

```dart
MaterialApp(
  theme: ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
  ),
);
```

Check Flutter Material 3 expressive docs for shape morphing and motion APIs.

### Web (@material/web)

**Important:** `@material/web` is in **maintenance mode**. Expressive motion and shape morphing are **not** available.

Use:

- CSS custom properties for tokens
- Easing/duration tokens for motion
- CSS `border-radius` transitions for shape morphing approximation

**Anti-pattern on Web:**

- Never assume expressiveness parity with Compose/Flutter
- Use CSS tokens (`--md-sys-color-*`, `--md-sys-shape-corner-*`)
- Implement metallic microgeometry with CSS `background: linear-gradient` and `box-shadow`

### Metallic Microgeometry Implementation by Platform

| Platform | Specular Highlights | Brushed Chamfers |
|----------|---------------------|------------------|
| Jetpack Compose | `LocalMetallicHighlightBrush` + `Surface` | `Modifier.metallicChamfer()` |
| Flutter | Custom `Paint` with linear gradients on Material surfaces | Custom `ChamferDecoration` |
| Web | CSS `linear-gradient` overlays | CSS `border` with `box-shadow` tricks |

---

## Conclusion

This specification establishes TALOS's Material 3 Expressive & Metallic Design System. Follow these guidelines to maintain consistent, accessible, and delightful UI across all platforms.

**Priority order for implementation:**

1. Color system + tonal hierarchy (Foundation)
2. Typography scale (Communication)
3. Shape scale (Organization)
4. Motion (Feedback)
5. Metallic microgeometry (Tactile refinement)
