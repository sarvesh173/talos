# Client/server gap — what the app calls that the host does not serve

**Date:** 2026-11-12
**Method:** every `.url(...)` in `android/.../network/TalosClient.kt` was extracted and compared
against every `@app.<verb>("<path>")` in `server/*.py`. Re-running the extraction reproduces the
table; nothing below is inferred from a screen.

The app and the host are on different versions of the same features. The app has controls for
delegates, child agents, a browser and image generation; the host serves one delegate listing, a
cancel and a pause.

This matters for the same reason the hardcoded agent roster mattered: a control that silently does
nothing is worse than no control, because it invites a setting the user believes is in effect.

## What is missing

| App call | Verb | Server has | Effect |
|---|---|---|---|
| `/api/delegates/{id}/quota` | PATCH | — | quota change discarded |
| `/api/delegates/{id}/pause` and `/resume` | POST | `/api/agents/delegates/{id}/pause` | wrong prefix; pause never reaches the host |
| `/api/delegates/{id}/model` | POST | — | model switch discarded |
| `/api/delegates/{id}/children` | POST | — | child spawn discarded |
| `/api/children/{id}` | DELETE | — | terminate discarded |
| `/api/children/{id}/pause`, `/resume` | POST | — | child pause discarded |
| `/api/browser/status` | GET | — | status always reads as "no browser" |
| `/api/browser/fetch` | POST | — | fetch never runs |
| `/api/image/gallery` | GET | — | gallery always errors |
| `/api/image/gallery/{id}` | DELETE | — | delete discarded |
| `/api/image/generate` | POST | — | generation never runs |
| `/api/agents/{id}/command-limit` | PUT | — | discarded |
| `/api/agents/{id}/pause`, `/resume` | POST | — | discarded |
| `/api/agents/pause-all`, `/resume-all` | POST | — | discarded |

What the host does serve, for contrast: `/api/agents/delegates` (list),
`/api/agents/delegates/{id}/cancel`, `/api/agents/delegates/{id}/pause`, `/api/delegation/hierarchy`.

## Which of these fail loudly

Most of them did not. Six delegate methods wrapped the call in `runCatching`, acted only
`if (resp.isSuccessful)`, and otherwise wrote one `Log.w` line. Nothing reached the UI, so a control
that could not work looked exactly like one that worked.

`checkBrowserStatus` was worse than silent: it mapped a failed request to `onResult(null)`, which is
the same value it passes when the host answers and there is no browser. "Could not ask" and "nothing
to report" were indistinguishable.

The image gallery does the right thing and is the pattern to copy: it sets a `_galleryError` the
screen renders, and its comment already says why — *an unreadable gallery is not an empty gallery*.

## Fixed since this was written

- **The six delegate methods.** They share `runCommand` now, which puts the reason in a
  `commandError` flow. `CliToolManager` shows it in the subtitle it already used for the hierarchy
  error and the canvas raises a toast, which is how that screen says everything else. The controls
  still cannot reach the host; they just stop pretending they did.
- **The command-limit toast.** It said "Limit updated locally" when the host had rejected the write.
- **`checkBrowserStatus`.** It takes an `onError` callback, the screen keeps the reason in
  `browserError` and shows it in the error colour, and the refresh toast only claims a refresh when
  one happened. An empty body is now reported too: the old `?: return@use` left the callback uncalled,
  so the screen sat on its placeholder forever.

The route table above is unchanged — none of these added an endpoint.

## Deliberately not changed

- **No server routes were added.** Building a delegate-quota API, a child-agent API, a browser proxy
  and an image generator is four features, and which of them should exist is a product decision, not
  a bug fix. Guessing would produce endpoints the UI half-uses.
- **No app controls were deleted.** The same argument in reverse: the screens may be finished client
  work waiting on a host that never caught up. Deleting them is also a product decision.
- **The one clean fix is left for the same reason.** `/api/delegates` → `/api/agents/delegates` is a
  two-line prefix correction, but it makes pause/resume appear to work while quota, model and
  children still do not, which is a more confusing state than uniform failure. Fixing it belongs with
  a decision about the whole delegate surface.

## Also found, unrelated to routes

- `LookAndFeelScreen` reads `animateCharacters`, `appIcon`, `loadingIndicatorStyle` and `reduceMotion`
  and renders their current state, but never writes them: the only `client.set*` calls in that screen
  are `setAccentColor`, `setThemeMode`, `setThemePreset` and `setDarkBackground`. So four appearance
  settings are displayed as if they were controls and cannot be changed. The setters exist and are
  never called, which is the same shape as the route gap — a client that offers something the other
  side does not accept.
- `AttachmentSheet.kt` and `AttachmentBottomSheet.kt` are two live attachment pickers with different
  tiles, one used by `AppleAgentChatScreen` and one by `MainActivity`. Two pickers for one job.
- Eleven `TalosClient` methods have no caller anywhere in the app; four of them are one-line aliases
  for a method that is used (`setShowThinking` → `setThinkingEnabled` and three more).
- `ai-design-check` reports 65 warnings, all pre-existing: 37 off the 4 dp spacing grid, 8 tracked
  uppercase labels, 10 emoji used as icons, 2 text sizes under 10 sp, 1 `${model.contextLength!! / 1024}k`
  (safe — it is inside a null check — but the `!!` is unnecessary).

## What would close it

Either:

1. **Server catches up.** Add the delegate, child, browser and image routes. Largest, most useful,
   needs each feature specified.
2. **App catches down.** Hide the controls whose endpoints do not exist, and delete the dead client
   methods. Smallest honest state: the app stops offering things the host cannot do.

Both are decisions about what TALOS is for. Neither should be made by a patch that assumes.