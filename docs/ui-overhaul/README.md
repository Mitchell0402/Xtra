# UI overhaul working files

Plan: "Lagoon UI 全面重构计划" (Claude Doc, finalized 2026-11-12). One stage per PR, U0 to U8.

| File | What it is |
| --- | --- |
| `u0-report.md` | U0 validation report: toolchain, CI, baselines, the three technical spikes and their numbers |
| `u1-report.md` | U1 report: design tokens, icons, the 14 components, showcase, dark-only theme, languages, size |
| `u9-plan.md` | U9 plan (2026-11-17): audit of every sub-screen still in the old Material style after v0.2, and the remediation plan; revision 2 after two reviews, D1–D3 decided, approved 2026-11-17 |
| `u9-report.md` | U9 report: legacy UI removed, Material Components library dropped, guard and surface registry in CI, walkthroughs, phone checks |
| `u9/surfaces.md` | Sub-screen registry (checked against the code by `tools/ci/legacy_ui_guard.py`) with before/after verdicts; `u9/*.webp` are the walkthrough mosaics |
| `motion-audit.md` | Motion audit and plan (2026-11-18, revision 2, approved 2026-11-18 with all five defaults): every transition and animation inventoried from code and emulator recordings, issues ranked, staged plan M0–M6 with effort and performance risk; recordings in `E:/Projects/Lagoon-audit/motion/`. Reviews: `motion-audit-review-1.md`, `motion-audit-review-2.md` |
| `motion-m1-report.md` | Motion stages M0 (tokens, motion guard, transition benchmark; PR #41) and M1 (fixes and values: first-load fade once, one-shot tab marker, player springs with release speed, mini close) |
| `feature-inventory.md` | App-wide feature list generated from the code (`tools/ui-inventory/inventory.py`); each stage claims and ticks its rows |

## How the guardrails work

- **CI** (`.github/workflows/android.yml`): every PR runs the JVM tests (unit, interaction, contract, Roborazzi screenshot
  comparison) in one Gradle task, Android Lint against `app/lint-baseline.xml`, and a `qa` build whose APK size goes to the
  run summary and whose APK and R8 mapping are kept as an artifact for 90 days.
- **Protected code** (`.github/workflows/protected-paths.yml`, `tools/ci/protected_paths.py`): a PR touching the ad-blocking
  pipeline, the database package, signing, manifest permissions or CI fails until its description has a
  `## Protected code declaration` section naming each touched area.
- **Screenshots**: baselines live in `app/src/testDebug/screenshots` and are only recorded on CI. After an intended visual
  change, run the CI workflow by hand on the PR branch with "Record new screenshot baselines" ticked (Actions tab, or
  `gh workflow run android.yml --ref <branch> -f record_screenshots=true`); it commits the new images to the branch.
- **Test naming**: `*ScreenshotTest`, `*InteractionTest`, `*ContractTest`, everything else is a unit test. The CI summary
  groups results by these suffixes.
- **Builds**: `qa` = release code with R8, debug key, id `io.github.mitchell0402.lagoon.qa` (manual tests);
  `benchmark` = qa plus profileable plus the lab screens, id `...lagoon.bench` (`:benchmark` module).
- **Lab** (`app/src/lab`, debug and benchmark builds only): the U0 spikes (chat list, player surface switch) and the
  U1 component showcase. The U0 Compose shell spike was replaced by the real shell in U2 (`ui/main/shell`).

## Design system (U1)

- Tokens: `app/src/main/java/.../ui/design/theme` (`Lagoon.colors`, `Lagoon.type`, `Lagoon.motion`, `LagoonShapes`,
  `LagoonSpacing`, `WidthClass`). Screens take every colour, size, radius, duration and spring from here.
- Components: `ui/design/components`. Wrap every Compose screen in `LagoonTheme { }`.
- Icons: `ui/design/icons/LucideIcons` is generated; add an SVG to `tools/icons/lucide` and run
  `python tools/icons/lucide_to_compose.py`.
- Showcase: debug builds have a second launcher icon, "Lagoon 组件" (`app/src/lab/.../showcase`). Its specimen sheets are
  also what `ComponentScreenshotTest` captures.

### Which motion for which change (motion plan section 7)

Every value comes from `Lagoon.motion`; `tools/ci/motion_guard.py` (CI, lint job) fails on a hard-coded `tween`,
`spring` or `keyframes`, an `animateTo` / `AnimatedVisibility` / `AnimatedContent` / `animateItem` /
`animateContentSize` without a spec, and any `rememberInfiniteTransition` outside `ui/design/theme`. Known exceptions
are counted in `tools/ci/motion_allowlist.txt`.

| Change | Use |
| --- | --- |
| Sibling switch (bottom tabs, segments, groups, settings wide right pane) | `sibling`; where it can be swiped, content follows the finger and settles on `steady` |
| One level deeper (page; sub-page inside a sheet) | `forward` / `sheetForward` |
| Back | `back` / `sheetBack`, with predictive back everywhere |
| Card to detail (the same image on both pages) | shared element, `sharedBounds` (`LONG`, standard curve) |
| Open the player (no player yet) | grows from the tapped card on `playerSpring`, starting at the fragment's first frame, poster underneath; rises from the bottom (`playerRiseStart`) when there is no card |
| Replace content inside the player (raid, full video, new card while mini) | cross-fade in place (`contentSwap`), keep the mode; while mini, swap and expand together |
| Cold start restoring playback | no animation |
| Player expand / collapse, mini card | `playerSpring` (no overshoot), follows the finger, release keeps the speed |
| Video not on screen yet (open, back to foreground with video on) | poster from the image cache, fades out on the first frame (`SHORT`), 3 s at most |
| Sheets, toasts | `steady` slide, scrim fade |
| A block appearing or disappearing in a page | `expand` / `collapse` |
| Items the user adds or removes | `listItemFadeIn` / `listItemPlacement` / `listItemFadeOut`; paged lists appending or reordering get none |
| Geometry (widths, video and chat rectangles) | `layout` (`steady`); never resize a SurfaceView every frame, scale it and set the size at the end |
| Values and colours | `fade`, rolling numbers |
| Press | `pressable` (97 %, `bouncy` release) |
| First load | the first 12 items fade in one after another, once per list |
| Across activities (settings, login) | the `forward` / `back` look on Android 14+; restarting the main screen cross-fades |
| Continuous (live dot, spinners, skeleton) | `continuousTransition(label, Ambient or Progress)` with `motion.loop(...)`; `Ambient` stops over a glass backdrop |

