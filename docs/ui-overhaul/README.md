# UI overhaul working files

Plan: "Lagoon UI 全面重构计划" (Claude Doc, finalized 2026-11-12). One stage per PR, U0 to U8.

| File | What it is |
| --- | --- |
| `u0-report.md` | U0 validation report: toolchain, CI, baselines, the three technical spikes and their numbers |
| `u1-report.md` | U1 report: design tokens, icons, the 14 components, showcase, dark-only theme, languages, size |
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
