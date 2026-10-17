# Development

## Layout

| Path | What |
|---|---|
| `core/` | Pure Kotlin (JVM) module. Metric catalog, rollups, baselines, hormonal models and phase labeler, forecast tiers, symptom parser, feel words, acoustic features, lexicon scorer. No Android dependencies, fully unit tested. |
| `app/` | Android app. Compose UI, Room with SQLCipher, Hilt, WorkManager, Health Connect, Oura, usage stats, voice check-in. Depends on `:core`. |
| `docs/` | Design, data sources, data model, mockups. |
| `.github/workflows/android.yml` | CI: core tests, app unit tests, debug APK. |

## Build

Normal path, needs internet access to Google's Maven and Maven Central:

```
./gradlew :core:test
./gradlew :app:assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`. CI runs the same steps on every push and
uploads the APK as a workflow artifact, so a build can be installed from the Actions tab without a
local Android SDK.

## Core tests without Maven access

Some sandboxes cannot download jars. The core module can still be compiled and tested with a
standalone Kotlin compiler from GitHub releases:

```
KOTLINC=/path/to/kotlinc scripts/core-tests-offline.sh
```

## Conventions

- Metric keys, event kinds, units, and day rules come from `docs/DATA_MODEL.md` and are mirrored in
  `core/.../Metrics.kt` and `app/.../domain/Contracts.kt`. Add there first.
- Colors and tiers are fixed in `core/.../Forecast.kt` (`Tier`). The UI never invents a sixth color.
- Nothing leaves the phone except calls to connected vendor APIs. Assistant features are off by
  default and gated behind one switch.
- No icons, no emoji in the UI. Labels are words.
