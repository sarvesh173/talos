# TALOS Agent Emblems

Pure-vector emblems for the CLI agents in the TALOS mesh. No emoji, no raster
art, no font glyphs - everything is hand-authored path data.

## Files

| Slug | Agent | Accent | SVG | Android drawable |
|------|-------|--------|-----|------------------|
| `hermes` | Hermes Agent (Nous Research) | `#22C55E` | `hermes.svg` | `res/drawable/ic_hermes.xml` |
| `claude` | Claude Code (Anthropic) | `#D97757` | `claude.svg` | `res/drawable/ic_claude.xml` |
| `openclaw` | OpenClaw | `#F97316` | `openclaw.svg` | `res/drawable/ic_openclaw.xml` |
| `omnirush` | OmniRush | `#A855F7` | `omnirush.svg` | `res/drawable/ic_omnirush.xml` |
| `alya` | Alya | `#F43F5E` | `alya.svg` | `res/drawable/ic_alya.xml` |

`manifest.json` maps slug -> label / accent / file locations.

## Design

All emblems share a `24 x 24` viewBox and are built from the same primitives:

* **Hermes** - solid `H` monogram flanked by two scalloped wings (three feather
  tips per side, mirrored about the vertical centre line).
* **Claude** - Anthropic-style sunburst: four long cardinal rays plus four
  short diagonal rays, each a tapered spike.
* **OpenClaw** - three hooked talons, each a tapered ribbon curving down to a
  sharp point.
* **OmniRush** - a single speed bolt polygon.
* **Alya** - a rose: an Archimedean spiral core inside a soft petal outline.

## Regenerating

`generate_icons.py` is the single source of truth. It writes the SVGs and the
Android vector drawables from the same shape list, so the two never drift.

```bash
python3 assets/agent-icons/generate_icons.py
```

The Android drawables are emitted in white (`#FFFFFFFF`) so they can be tinted
at the call site; the SVGs carry each agent's accent color directly.

## Usage in Compose

```kotlin
AgentRoster()                        // row of all five emblem badges
AgentEmblemIcon(emblemByKey("hermes")) // single tinted emblem
```

Defined in `android/app/src/main/java/com/sarvesh173/talos/ui/AgentEmblems.kt`.
