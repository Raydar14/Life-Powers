# Data layer: known gaps and notes for the build

Written by the data, connectors, sync, and DI pass. Nothing here needs a new dependency; every
library used is already in `gradle/libs.versions.toml`.

## Things the UI or voice engineers must do

- **Health Connect permissions.** `HealthConnectConnector.PERMISSIONS` is the set to request with
  `PermissionController.createRequestPermissionResultContract()`. The connector reads nothing until
  the user grants them; every record type is read in its own try/catch so partial grants work.
- **Health Connect in the background.** Reads from a WorkManager job are refused unless the app
  holds `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` (Android 14+) or the sync runs
  while the app is in the foreground. The manifest does not declare that permission. Until it does,
  call `SyncManager.syncNow()` on app open so the Health Connect read happens in the foreground.
- **Usage access.** `UsageStatsConnector.settingsIntent()` opens the system usage access screen;
  `UsageStatsConnector.hasUsageAccess(context)` checks it. No runtime dialog exists for this.
- **Oura token.** Stored through `SettingsRepository.setOuraToken`. The connector reports
  `NOT_CONNECTED` while it is blank.

## Deliberate simplifications

- `sync_cursor` has one row per connector (primary key `source`) rather than the `(source, stream)`
  key in `docs/DATA_MODEL.md`. The row's cursor packs one value per stream (`core/StreamCursors`),
  so a failed stream keeps its own position and is retried.
- `raw_sample` is not stored. Vendor payloads are mapped straight to `metric_sample`.
- The `is_canonical` flag is written as true for every sample. Source priority is applied at
  rollup time instead (`core/SourcePriority`): for each metric and day only the best ranked source
  present feeds the daily value, so Oura's own API and Oura's copy in Health Connect are not
  counted twice. Health Connect samples carry the writing app's package in their source.
- `ProfileRepository.phaseFor` uses `hormone_dose` events for the dosing rhythm's `lastDose` and
  the period anchor as `packStart` for the pack rhythm. No `profile_change` history is kept.
- The HORMONAL forecast component has one calendar input (luteal -0.3, follicular +0.3), so its
  own tier is always UNKNOWN (fewer than 3 inputs) while its score still feeds the DAY composite.
- Expedited `syncNow` work needs a foreground notification below API 31. The worker creates a
  low importance channel named `sync` and uses the system `stat_notify_sync` icon.
- Usage stats intervals that were already open at local midnight are missed for that day's
  first minutes, since `queryEvents` only returns events inside the window.
- `Room.fallbackToDestructiveMigration()` is set while the schema is at version 1. Replace with
  real migrations before any release build has shipped data.

## Not verified by compilation

This machine has no Android SDK and Maven Central returned 429 for the scratch JVM build, so none
of the app module was compiled here. The pure Kotlin pieces (`OuraMapper`, `ScreenIntervals`,
`CheckinJson`, `Dates`) and their tests are written against the exact catalog versions and should
be the first things CI reports on.
