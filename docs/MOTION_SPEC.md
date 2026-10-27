# TALOS Motion Spec — Message-Grade Animation Lineage

Measured from live sources on 2026-10-28 with the local Obscura headless browser
(28 MB RSS, no Chromium). Raw values below are the ground truth the Android
`core/motion/MotionTokens.kt` file encodes.

---

## 1. Telegram Web K (`web.telegram.org/k/`)

CSS bundle: `https://web.telegram.org/k/index-C7zwiSRP.css`

```css
--transition-standard-easing: cubic-bezier(.4, 0, .2, 1);
--transition-standard-in-time:  .2s   /* second rule: .3s */
--transition-standard-out-time: .15s  /* second rule: .25s */
--transition-standard-in:  var(--transition-standard-in-time)  var(--transition-standard-easing);
--transition-standard-out: var(--transition-standard-out-time) var(--transition-standard-easing);
--transition-time:       .4s;
--transition-time-move:  .3s;
```

Additional easings present in the bundle (used by Telegram's own popups/menus):

| Easing | Where it appears |
|---|---|
| `cubic-bezier(.4, 0, .2, 1)` | standard in/out |
| `cubic-bezier(.4, 0, .6, 1)` | accelerate-out |
| `cubic-bezier(.12, 0, .39, 0)` | sharp decelerate |
| `cubic-bezier(.12, 1.1, .56, 1.1)` | overshoot pop |
| `cubic-bezier(.12, 1.1, .56, 1.2)` | overshoot pop (deeper) |
| `cubic-bezier(.22, 1, .36, 1)` | popup / menu reveal |
| `cubic-bezier(.34, 1.56, .64, 1)` | back-out (spring-like) |
| `cubic-bezier(.35, .35, .47, 1.34)` | playful overshoot |
| `cubic-bezier(.35, .35, .7, 1)` | settle |
| `cubic-bezier(.39, .575, .565, 1)` | ease-out-quad |
| `cubic-bezier(.47, 0, .745, .715)` | ease-in-quad |
| `cubic-bezier(.61, 1, .88, 1)` | ease-out-cubic |
| `cubic-bezier(.35,.35,.7,1)` | settle |

## 2. Telegram Android (source, `DrKLO/Telegram`)

`TMessagesProj/src/main/java/org/telegram/ui/Components/CubicBezierInterpolator.java`

```java
DEFAULT          = cubic-bezier(0.25, 0.1, 0.25, 1)
EASE_OUT         = cubic-bezier(0, 0, 0.58, 1)
EASE_OUT_QUINT   = cubic-bezier(0.23, 1, 0.32, 1)
EASE_IN          = cubic-bezier(0.42, 0, 1, 1)
EASE_BOTH        = cubic-bezier(0.42, 0, 0.58, 1)
EASE_OUT_BACK    = cubic-bezier(0.34, 1.56, 0.64, 1)
EmphasizedDecelerate = cubic-bezier(0.05, 0.7, 0.1, 1)
EmphasizedAccelerate = cubic-bezier(0.3, 0, 0.8, 0.15)
StandardDecelerate   = cubic-bezier(0, 0, 0, 1)
```

`TMessagesProj/src/main/java/org/telegram/ui/ActionBar/BottomSheet.java` — sheet
present/dismiss duration is **250 ms**.

## 3. WhatsApp Web (`web.whatsapp.com/`)

CSS bundle:
`https://static.whatsapp.net/rsrc.php/v5/yn/l/0,cross/WBNgetnMqIrGRLM5ENHN5kOB_OY8k1KqH.css`

Distinct animation durations observed (seconds):

```
.1 .15 .16 .167 .2 .22 .25 .28 .3 .332 .333 .35 .4 .417 .45 .5 .52 .551 .58 .6 .65 .7 .75
```

Distinct transition durations observed:

```
.05 .08 .1 .15 .18 .2 .25 .28 .3 .33 .4 .44 .45 .5 .7
```

Notable easings (57 distinct cubic-beziers in the bundle):

| Easing | Behaviour |
|---|---|
| `cubic-bezier(.04,0,0,1)` | near-linear long settle |
| `cubic-bezier(.08,.52,.52,1)` | soft accelerate |
| `cubic-bezier(.09,.87,.72,1)` | sharp decelerate |
| `cubic-bezier(.1,.82,.25,1)` | fast decelerate |
| `cubic-bezier(.1,.9,.2,1)` | snap-in decelerate |
| `cubic-bezier(.1,1,.36,1)` | strong overshoot ease |
| `cubic-bezier(.12,.8,.32,1)` | material-style decelerate |
| `cubic-bezier(.14,1,.34,1)` | overshoot decelerate |
| `cubic-bezier(.165,.84,.44,1)` | soft overshoot |
| `cubic-bezier(.17,.17,0,1)` | strong accelerate-in |
| `cubic-bezier(.17,.67,.34,1.13)` | overshoot back |
| `cubic-bezier(.17,.89,.32,1.27)` | deep overshoot |
| `cubic-bezier(.2,0,0,1)` | cubic accelerate |
| `cubic-bezier(.22,1,.36,1)` | popup reveal |
| `cubic-bezier(.23,1,.32,1)` | quint decelerate |
| `cubic-bezier(.24,.91,.01,.99)` | symmetric ease |

## 4. How this maps to Compose

`core/motion/MotionTokens.kt` encodes the shared lineage:

| Compose symbol | Value | Lineage |
|---|---|---|
| `MotionTokens.Standard` | `CubicBezierEasing(0.4f, 0f, 0.2f, 1f)` | TG web standard easing / WA `.2,0,0,1` family |
| `MotionTokens.EmphasizedDecelerate` | `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)` | TG Android emphasized decelerate |
| `MotionTokens.EmphasizedAccelerate` | `CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)` | TG Android emphasized accelerate |
| `MotionTokens.PopupReveal` | `CubicBezierEasing(0.22f, 1f, 0.36f, 1f)` | TG web + WA shared |
| `MotionTokens.BackOut` | `CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)` | TG Android `EASE_OUT_BACK` |
| `MotionTokens.FastSpatial` | spring `damping 0.6 / stiffness 800` | M3 Expressive fast spatial |
| `MotionTokens.DefaultSpatial` | spring `damping 0.8 / stiffness 380` | M3 Expressive default spatial |
| `MotionTokens.SlowSpatial` | spring `damping 0.8 / stiffness 200` | M3 Expressive slow spatial |
| `MotionTokens.FastEffects` | spring `damping 1.0 / stiffness 3800` | M3 Expressive effects (no overshoot) |
| `MotionTokens.DefaultEffects` | spring `damping 1.0 / stiffness 1600` | M3 Expressive effects |
| `MotionTokens.EnterDuration` | `260 ms` | TG web in `.2s`–`.3s` band |
| `MotionTokens.ExitDuration` | `200 ms` | TG web out `.15s`–`.25s` band |
| `MotionTokens.SheetDuration` | `250 ms` | TG Android BottomSheet |
| `MotionTokens.TypingCadence` | `333 ms` per dot | WA `.333s` cadence |
| `MotionTokens.StaggerStep` | `40 ms` | WA `.04s`-ish stagger band |

## 5. Non-negotiable motion rules

1. **Spatial springs** for position/size/shape/rotation. **Effects springs**
   (critically damped) for color/alpha/state layers — never a spatial spring on
   alpha, it overshoots and reads as a glitch.
2. **Infinite loops never use springs.** Keyframes on the emphasized curve only.
3. **Never mix families.** A screen transition does not use a WhatsApp curve
   while a button uses an M3 spring without a reason.
4. **Bounded work**: live telemetry repaints the ring once per frame, never
   re-lays out the list per sample.
5. **Reduced motion**: when `Settings.Global.ANIMATOR_DURATION_SCALE == 0`,
   decorative motion is skipped and entrances snap; semantic state changes
   (loading → success) still render.
6. **One identity morph per screen.** `graphics.shapes.Morph` is the
   distinctiveness budget — reserved for the primary CTA and the send button.
   Everything else gets press-scale only.
