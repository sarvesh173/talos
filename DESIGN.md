# TALOS Design System — DESIGN.md

> **Status:** Active implementation contract. Every UI change must conform.
> **Grounding:** M3 Expressive (May 2025) corpus — 40 pages / 257k chars in `/tmp/m3_research/` — plus community research from r/androiddev, r/MaterialDesign, r/UXDesign, and HN/X discussions.
> **Rule zero:** the interface contains no emojis. Icons carry meaning, not glyphs.

---

## 1. Motion: Physics First

M3 removed easing+duration as the primary system. Motion is springs, defined by **stiffness**, **damping**, and **initial velocity**.

| Purpose | Compose spec | Overshoot |
|---|---|---|
| Spatial default (enter, resize, move) | `spring(DampingRatioNoBouncy, StiffnessMediumLow)` | no |
| Spatial expressive (press, pop, morph) | `spring(DampingRatioMediumBouncy, StiffnessMedium)` | **yes** |
| Spatial fast (chips, ripples) | `spring(DampingRatioLowBouncy, StiffnessMediumHigh)` | subtle |
| Effects (color, alpha, tint) | `spring(DampingRatioNoBouncy, StiffnessMediumLow)` | **never** |

Hard rule: **anything that changes position/scale may bounce; anything that changes color/opacity must not.**

Named recipes in code:
- `PressableSurface` — pressed scale `0.96`, expressive spatial spring, haptic on actuation.
- `AnimatedTalosIcon` — `IDLE` (ambient breath 0.97↔1.03), `RECORDING` (emission rings + 1.06 body), `ACTIVATE` (one-shot 1.22 spring pop).

## 2. Shape: No Square Boxes

| Role | Radius |
|---|---|
| Thin tracks / progress | 6dp |
| Chips, badges, small controls | 12dp |
| Small cards, tiles | 14dp |
| Default surfaces, buttons | 16dp |
| Sheets, dialogs, FAB | 24dp+ |

Buttons are **fully rounded** at default and ≥40dp tall; radius tightens slightly while pressed (shape morph) and springs back on release.

## 3. Color: 26 Roles, Seeded Iris

Six groups: `primary`, `secondary`, `tertiary`, `error`, `surface`, `outline`.

- `X` = fill for emphasis, `XContainer` = fill for foreground elements (**never** for text/icons), `OnX` = text/icons on `X`, `XVariant` = lower emphasis.
- No raw hex in screens — always `MaterialTheme.colorScheme.*`.

**Surface ramp (dark)** — nested containers must be distinguishable; the flat-ramp "washout" is the top community complaint:

| Token | Value | Step |
|---|---|---|
| surfaceContainerLowest | `#0F0D13` | — |
| surfaceContainerLow | `#1D1B20` | 1.13:1 |
| surfaceContainer (default) | `#25232B` | 1.10:1 |
| surfaceContainerHigh | `#2B2930` | 1.08:1 |
| surfaceContainerHighest | `#36343B` | 1.17:1 |

`outlineVariant` raised to `#55515C` so borders read (2.0:1 on container, was 1.74:1).

**Contrast floor:** text ≥4.5:1, UI components ≥3:1, verified numerically before merge.

## 4. Dark Mode Behavior

- Elevation is **surface tint / tonal shift**, not shadows.
- Dark theme buttons keep the same roles; only fill tone changes.
- Theme switching cross-fades via **effects springs**; never an instant snap.

## 5. Icons

- Custom `TalosIcons` family only (Mic, Camera, Hub, Board, Vault, Browser) — hand-built vector paths, unique silhouettes, no stock glyphs.
- 20dp inside buttons, 24dp standalone, **2dp stroke weight**.
- Interactive controls use `AnimatedTalosIcon`; static icons are not permitted there.

## 6. Touch

- Every target ≥48×48dp.
- Every actuation emits haptics (`TalosHaptics`, `VibratorManager` waveform).
- Gestures follow physical metaphors: hold to record, release to send, swipe up to lock, swipe away to cancel.

## 7. Redesign Order

1. LandingScreen (hero, hardware tiles, live activity)
2. Chat surfaces and bubbles
3. Module hub and agent canvas
4. Onboarding wizard
5. Browser and gallery

Merge gate per screen: contrast audit, spring audit, shape audit, 48dp audit, emoji scan (must return zero), `:app:compileReleaseKotlin`.

---

*Audit tooling: contrast ratios computed numerically; emoji scan across all Kotlin sources must return zero.*
