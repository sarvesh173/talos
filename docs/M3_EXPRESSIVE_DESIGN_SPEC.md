# Material 3 Expressive (M3E) Design Specification

> **Specification Authority:** Google Material 3 Expressive System (m3.material.io, AndroidX Compose Material3, Material Web v0.192, DSM 2026-10)  
> **Status:** Ground-Up Authoring Standard for TALOS  

---

## 1. Color System & HCT Tonal Architecture

Material 3 Expressive utilizes the **HCT (Hue, Chroma, Tone)** perceptual color space. Unlike RGB or HSL, HCT accurately reflects human visual perception: tone corresponds directly to perceived luminance ($L^*$), guaranteeing accessible contrast regardless of chosen hue.

### 1.1 Palette Generation from Seed
Every scheme is derived from a primary seed color. For TALOS:
- **Baseline Seed:** `#6750A4` (Deep Royal Iris)
- **Brand Seed:** `#6366F1` (Electric Indigo)

The seed dynamically populates 5 key tonal palettes sampled at 16 standardized tone stops ($T \in [0, 100]$):
1. **Primary Palette:** Key accent colors.
2. **Secondary Palette:** Less prominent supportive UI controls.
3. **Tertiary Palette:** Playful, warm contrast elements (e.g. badges, reactions).
4. **Neutral Palette:** Surfaces, backgrounds, and container planes.
5. **Neutral Variant Palette:** Outline boundaries, dividers, and state layers.

### 1.2 Contrast Modes
- **Standard Contrast (0.0):** Default comfortable visual hierarchy.
- **Medium Contrast (0.5):** Increased tonal delta between text and containers for bright outdoor environments.
- **High Contrast (1.0):** Strict 7:1+ AAA contrast ratio for maximum vision accessibility.

### 1.3 Complete Token Mappings

| Role | Tone (Light) | Tone (Dark) | Purpose & Usage Guidelines |
|:---|:---:|:---:|:---|
| `primary` | 40 | 80 | High-emphasis fills, prominent FABs, active tab indicators |
| `on-primary` | 100 | 20 | Text and vector glyphs rendered directly on `primary` |
| `primary-container` | 90 | 30 | Medium-emphasis standout container fills (selected rooms) |
| `on-primary-container` | 10 | 90 | Text and vector glyphs on `primary-container` |
| `secondary` | 40 | 80 | Secondary actions, inactive toggles, filter pills |
| `on-secondary` | 100 | 20 | Text and glyphs on `secondary` |
| `secondary-container` | 90 | 30 | Recessive containers (tonal buttons, chips) |
| `on-secondary-container` | 10 | 90 | Text and glyphs on `secondary-container` |
| `tertiary` | 40 | 80 | Warm contrasting accents (agent thought badges, notifications) |
| `on-tertiary` | 100 | 20 | Text on `tertiary` |
| `tertiary-container` | 90 | 30 | Tertiary container highlight fills |
| `on-tertiary-container` | 10 | 90 | Text on `tertiary-container` |
| `surface` | 98 | 6 | Base background plane for application viewport |
| `on-surface` | 10 | 90 | High-contrast body typography, titles, and headers |
| `on-surface-variant` | 30 | 80 | Subtitles, helper text, and secondary timestamps |
| `surface-container-lowest` | 100 | 4 | Deepest recessed plane (terminal logs, code sockets) |
| `surface-container-low` | 96 | 10 | Recessed sidebar rail, background drawer panels |
| `surface-container` | 94 | 12 | Default cards, message bubbles, elevated controls |
| `surface-container-high` | 92 | 17 | Floating top app bars, modals, bottom sheets |
| `surface-container-highest` | 90 | 22 | Highlighted cards, hover elevations, popover menus |
| `outline` | 50 | 60 | High-emphasis control borders requiring 3:1 contrast |
| `outline-variant` | 80 | 30 | Subtle decorative hairlines, dividers, container edges |

---

## 2. Shape Scale & Expressive Morphing Library

Material 3 Expressive expands the shape system from static corner radii into a dynamic 10-level scale and a 35-shape morphing engine.

### 2.1 The 10 Corner Radius Tokens
1. `md.sys.shape.corner.none`: **0dp / 0px** (Full-bleed media, viewport edges)
2. `md.sys.shape.corner.extra-small`: **4dp / 4px** (Tiny badges, tooltips, tags)
3. `md.sys.shape.corner.small`: **8dp / 8px** (Snackbars, notification pills)
4. `md.sys.shape.corner.medium`: **12dp / 12px** (Standard cards, dropdown menus)
5. `md.sys.shape.corner.large`: **16dp / 16px** (Floating modal dialogs, drawers)
6. `md.sys.shape.corner.large-increased`: **20dp / 20px** (Expressive action cards)
7. `md.sys.shape.corner.extra-large`: **28dp / 28px** (Large hero containers, sheets)
8. `md.sys.shape.corner.extra-large-increased`: **32dp / 32px** (Expanded search bars, command docks)
9. `md.sys.shape.corner.extra-extra-large`: **48dp / 48px** (Display tiles)
10. `md.sys.shape.corner.full`: **9999px / Circle** (Floating Action Buttons, pill chips, action avatars)

### 2.2 Expressive Morph Shapes (SVG Splines)
- **12-Lobed Scallop:** Flower-like decorative badge used for version tags and agent status anchors.
- **Continuous Squircle (Superellipse $n = 4$):** Organic curved rectangular containers preventing harsh corner pinching.
- **Asymmetrical Pill:** Dynamic message containers where sender side has reduced radius ($6\text{px}$) and receiver side has expansive radius ($24\text{px}$).

---

## 3. Motion System: Closed-Form Physical Springs

M3 Expressive fundamentally replaces fixed durations with **physical spring simulations** governed by the differential equation of damped harmonic motion:
$$m x''(t) + c x'(t) + k (x(t) - x_{\text{target}}) = 0$$

Assuming unit mass ($m = 1.0$), the natural angular frequency is $\omega_n = \sqrt{k}$, and the damping coefficient is $c = 2 \zeta \sqrt{k}$, where $\zeta$ is the dimensionless damping ratio.

### 3.1 Closed-Form Formulas
1. **Underdamped Case ($\zeta < 1.0$, Spatial Movement with Bounce):**
   $$\omega_d = \omega_n \sqrt{1 - \zeta^2}$$
   $$x(t) = x_{\text{target}} + e^{-\zeta \omega_n t} \left[ (x_0 - x_{\text{target}}) \cos(\omega_d t) + \frac{v_0 + \zeta \omega_n (x_0 - x_{\text{target}})}{\omega_d} \sin(\omega_d t) \right]$$

2. **Critically Damped Case ($\zeta = 1.0$, Effects & Alpha without Overshoot):**
   $$x(t) = x_{\text{target}} + e^{-\omega_n t} \left[ (x_0 - x_{\text{target}}) + (v_0 + \omega_n (x_0 - x_{\text{target}})) t \right]$$

### 3.2 Standard & Expressive Spring Tokens

| Composite Token Name | Scheme | Damping Ratio $\zeta$ | Stiffness $k$ | Derived $c$ | Target Motion Property |
|:---|:---:|:---:|:---:|:---:|:---|
| `spring.fast.spatial` | Expressive | **0.60** | **800** | 33.94 | Buttons, chips, switches (lively bounce) |
| `spring.default.spatial` | Expressive | **0.80** | **380** | 31.19 | Sheets, cards, navigation drawers |
| `spring.slow.spatial` | Expressive | **0.80** | **200** | 22.63 | Full-screen transitions, scene transforms |
| `spring.fast.effects` | Expressive | **1.00** | **3800** | 123.29 | Icon tints, selection highlights, state-layer fade |
| `spring.default.effects` | Expressive | **1.00** | **1600** | 80.00 | Card background color transitions |
| `spring.slow.effects` | Expressive | **1.00** | **800** | 56.57 | Backdrop blur and atmospheric wallpaper fade |

---

## 4. State Layer & Interaction Architecture

In M3 Expressive, visual interactive state is communicated via a dedicated **State Layer** placed directly between the container surface and the content layer. The state layer always inherits the `on-color` (e.g. `on-surface` or `on-primary`) of the container.

- **Hover State:** Opacity `0.08` (8%)
- **Focus State:** Opacity `0.10` - `0.12` (10% - 12%)
- **Pressed State:** Opacity `0.10` - `0.12` (10% - 12%) with instantaneous spring scale $(0.96 \times)$
- **Dragged State:** Opacity `0.16` (16%)
- **Disabled State:** Opacity `0.38` (38%) container reduction, `0.38` text reduction; no interactive state layer.

---

## 5. Spacing, Elevation & Layout Grid

- **8dp Base Spacing Grid:** All paddings, margins, and gaps are strict multiples of $4\text{dp}$ / $8\text{dp}$:
  - Micro: `4px`
  - Compact: `8px`
  - Medium: `16px`
  - Spaced: `24px`
  - Expanded: `32px`
- **Zero-Elevation RenderNode Guarantee:** During real-time scrolling and page transitions, dynamic GPU drop shadows (`elevation > 0`) are disabled to eliminate frame stutter. Visual depth is established via **Tonal Surface Elevation** (`surface-container-lowest` $\rightarrow$ `surface-container-highest`) and specular linear gradient outline borders.
