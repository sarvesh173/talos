# Android app structure

Generated from the tree, not written from memory. Regenerate rather than trust a
number in here after a large change.

## Reading this

A change is hardest to make safely in a file that is long and holds several
things at once, because the part you meant to touch shares a scope with parts you
did not. The list below is ordered that way on purpose.

## Where a screen starts

Every screen the app can show is routed from `MainActivity.kt`'s screen router,
a `when` over a string. The string is the screen's name. Adding a screen means
adding a branch there and a row in Settings that points at it, so a screen that
exists but has no branch is invisible rather than broken.


Biggest files first, because that is where a change is hardest to make safely.

## Files over 600 lines

| Lines | File |
|---|---|
| 2,286 | `network/TalosClient.kt` |
| 2,270 | `MainActivity.kt` |
| 1,663 | `ui/OnboardingWizard.kt` |
| 1,454 | `ui/Components.kt` |
| 1,297 | `ui/features/LookAndFeelScreen.kt` |
| 1,267 | `ui/bot/AgentLogoCloud.kt` |
| 1,174 | `ui/features/SettingsScreen.kt` |
| 1,145 | `ui/AppleAgentChatScreen.kt` |
| 1,130 | `ui/TalosIcons.kt` |
| 1,025 | `ui/AgentCanvasScreen.kt` |
| 847 | `ui/LandingScreen.kt` |
| 812 | `ui/Theme.kt` |
| 719 | `ui/chat/DirectiveHost.kt` |
| 712 | `ui/bot/AgentLogoCustomizeScreen.kt` |
| 707 | `ui/avatar/GroupMemberAvatar.kt` |
| 696 | `ui/WhiteboardScreen.kt` |
| 672 | `ui/avatar/IdentityGallery.kt` |
| 671 | `ui/kanban/KanbanScreen.kt` |
| 659 | `ui/cron/JobListScreen.kt` |
| 649 | `ui/agent/AgentQuestionOverlay.kt` |
| 637 | `ui/agent/TalosDirectiveRenderers.kt` |

## Directories

| Lines | Files | Directory |
|---|---|---|
| 18,290 | 39 | `ui` |
| 6,347 | 12 | `ui/features` |
| 4,397 | 11 | `ui/bot` |
| 2,948 | 8 | `ui/agent` |
| 2,941 | 9 | `ui/avatar` |
| 2,686 | 5 | `network` |
| 2,270 | 1 | `.` |
| 2,066 | 6 | `ui/orb` |
| 1,313 | 3 | `ui/chat` |
| 1,085 | 4 | `ui/settings` |
| 935 | 3 | `ui/kanban` |
| 903 | 5 | `ui/entry` |
| 675 | 3 | `ui/agents` |
| 659 | 1 | `ui/cron` |
| 577 | 1 | `ui/review` |
| 479 | 2 | `notify` |
| 424 | 1 | `ui/processes` |
| 373 | 1 | `agent/capability` |
| 351 | 5 | `data` |
| 229 | 1 | `ui/richtext` |
| 226 | 2 | `core/feature` |
| 163 | 1 | `core/motion` |