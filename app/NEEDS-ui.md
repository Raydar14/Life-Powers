# Needs from the UI layer

Things the UI (com.lifepowers.app.ui, MainActivity) needs from outside its own packages, and how it
works around each until it exists. Written by the UI engineer; nothing here blocks a build.

## Fonts
- **Nunito through the Google Fonts provider needs a certificates resource.** `androidx.compose.ui.text.googlefonts`
  requires `GoogleFont.Provider("com.google.android.gms.fonts", "com.google.android.gms", R.array.com_google_android_gms_fonts_certs)`.
  The UI cannot add resources, so `ui/theme/Type.kt` uses `FontFamily.SansSerif` at weights 500 to 900.
  When `res/values/font_certs.xml` exists, replace `LifePowersFontFamily` in `Type.kt` with a
  `FontFamily(Font(GoogleFont("Nunito"), provider, FontWeight.Medium), ... FontWeight.Black)`.

## Voice (com.lifepowers.app.voice)
- `CheckinViewModel` injects `CheckinSessionController` exactly as specified: `state`, `currentPrompt`,
  `nextPrompt()`, `start()`, `stop()`, `reset()`, `suspend confirmSymptoms(record, kinds)`,
  `suspend logSpokenBehaviors(record, kinds)`, `companion suggestions(record): List<String>`.
  `State.Idle` and `State.Processing` are referenced with `is`, so they can be `data object` or `object`.
- The UI requests RECORD_AUDIO before calling `start()`. If the controller also checks, that is fine.

## Connectors (com.lifepowers.app.connectors)
- `HealthConnectConnector.PERMISSIONS: Set<String>` is launched through
  `PermissionController.createRequestPermissionResultContract()` in `SettingsScreen`.
- `UsageStatsConnector.settingsIntent(): Intent` is started from the Phone usage row.
- If either name differs when the files land, only `ui/screens/settings/SettingsScreen.kt` needs the change.

## Data
- The Feel screen stores the tint as two samples with metric keys `feel.tint_hue` (0 to 360) and
  `feel.tint_light` (0 to 1), source `feel`. `Metrics.catalog` has no entry for them; `Metrics.spec()`
  falls back to a CALENDAR day LAST spec, which is acceptable. Adding them to the catalog (unit "deg" and
  "0..1", Agg.LAST) would make them first class. Constants live in `ui/screens/feel/FeelViewModel.kt`.
- Manual journal events use `source = "manual"` and `unit = "intensity"` for cravings and `"severity"` for
  symptoms, with `quantity` 1 to 5. Behaviors tapped in the journal carry `quantity = 1.0`.
- The Today screen reads `ForecastRepository.forDay(today)` and expects the DAY snapshot's `reasons` to be
  component names (as `Forecast.day()` produces) or metric keys; both are humanized.
- `ProfileRepository.phaseFor(today)` is called once per Today view model; it does not re-run when the
  profile changes until the screen is recreated.

## Later phases (placeholders in place)
- Goals, Sky and Patterns are placeholder screens ("Coming in the next phase") at routes `goals`, `sky`,
  `patterns`. Photo and tarot buttons in Journal show a one line "next phase" note.
- The Days screen shows tier dots, daily metric points with z words, and events; the one night on one
  clock chart from the mockup needs the sample level series and comes with Timeline work.
- The "sample data labeled as sample" for door two is a data layer concern; the UI only sets `playFirst`.
