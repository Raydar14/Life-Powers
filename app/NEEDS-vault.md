# Needs and notes: data vault, privacy screen, settings, navigation

Written by the data ownership pass. Files added or changed:

- `vault/VaultCodec.kt` (new): pure helpers for the backup JSON, settings, CSV, and names. Unit tested in
  `app/src/test/kotlin/com/lifepowers/app/vault/VaultCodecTest.kt` (12 tests, passing on a plain JVM).
- `vault/DataVaultImpl.kt` (new): `DataVault` implementation, `@Singleton`.
- `di/VaultModule.kt` (new): binds `DataVault` to `DataVaultImpl`.
- `ui/screens/privacy/PrivacyScreen.kt` and `PrivacyViewModel.kt` (new): route `"privacy"`.
- `ui/screens/settings/SettingsScreen.kt` and `SettingsViewModel.kt`: the old assistant card is gone, and
  `AssistantSettingsSection()` takes its place. New rows for Breathing, Experiments, and Privacy and data.
- `ui/navigation/LifePowersApp.kt`: the breathing, experiment, and privacy routes.

No new dependency. `MainActivity` was not changed (it is a `ComponentActivity`, so
`rememberLauncherForActivityResult` works as it is).

## Bindings this code relies on from other owners

- `AssistantRepository` must be bound in Hilt. `PrivacyViewModel` injects it to read `log`.
- `com.lifepowers.app.ui.screens.assistant.AssistantSettingsSection(modifier: Modifier = Modifier)` must
  exist. Settings no longer calls `SettingsRepository.setAssistantMaster`; the section owns the switches.
- Breathing and experiment composables with the signatures in the task (see the route table below).

## Backup format

UTF-8 JSON, sealed with `BackupCrypto.encrypt` (password, PBKDF2, AES-GCM):

```
{"format":"life-powers-backup","version":1,"createdUtc":<ms>,
 "databases":{"main":{"<table>":[{"<column>":<value>,...},...]},"personal":{...}},
 "photos":[{"fileName":"<name>","bytesBase64":"<decrypted bytes>"}],
 "settings":{"<pref name>":{"type":"boolean|int|long|float|double|string|string_set","value":"<text>" or [..]}}}
```

- Every table in `sqlite_master` except `android_metadata`, `sqlite_*`, and `room_*` is dumped with
  `SELECT *`, so new tables are carried with no change here. Cells: integers and reals are JSON numbers, text
  is a JSON string, null is null, a blob is `{"b64": "..."}`. Non finite reals are written as null.
- Photos are included only when the person turns on "Include photos".
- Secrets never go in: any DataStore key named `oura_token` or containing `key`, `token`, `secret`,
  `passphrase`, or `password`. The assistant key (in EncryptedSharedPreferences) and the database passphrase
  are not read at all.

## Restore

- Decrypt, parse, check `format` and `version`, then per database in one Room transaction
  (`withTransaction`, so Room flows refresh): delete every row of every user table, then insert the backup's
  rows with `CONFLICT_REPLACE`. Tables this version lacks and columns a table lacks (`PRAGMA table_info`) are
  skipped. A row the schema refuses is skipped rather than failing the restore.
- Photos are stored again through `PhotoVault.store`. PhotoVault always picks a fresh UUID name, so the
  restored `photo` rows are updated to the new names (and mime). The task asked to keep file names; that
  needs a `PhotoVault.storeAs(fileName, bytes, mime)` from the PhotoVault owner. Until then names change,
  which nothing else depends on. Photo files no row points at are deleted afterwards.
- Non secret preferences are replaced with the backup's. Secrets already on this phone stay.
- `ReminderScheduler.rescheduleAll()` runs afterwards so restored goals get their alarms.
- `MetricRepository.rebuildDaily` is not called: `daily_metric` is in the backup itself.
- `filesDir/patterns.json` is left alone; the pattern worker refreshes it on its next run.
- Wrong password and not a backup come back as `DataResult.Failed` with BackupCrypto's own sentences.

## Delete everything

`clearAllTables()` on both databases, `rescheduleAll()` (which cancels every armed reminder now that no goals
exist), `WorkManager.cancelAllWork()`, `NotificationManagerCompat.cancelAll()`, delete `filesDir/photos`,
`filesDir/audio`, `filesDir/patterns.json`, the cache directory, clear the DataStore, then clear every
shared preferences file except:

- `life_powers_keys` (the database passphrase). The database files stay too, emptied, so the app keeps
  working without a restart.
- Library files: `__androidx_security_crypto_encrypted_file*` (EncryptedFile keysets, no personal data),
  `androidx.*`, `com.google.*`, `WebView*`.

Encrypted preference files (detected by their Tink keyset entries, such as `assistant_secrets`) are cleared
through `EncryptedSharedPreferences.edit().clear()` with the default AES256_GCM master key, so their keysets
stay valid for any repository still holding them open. If that open fails, the raw file is cleared instead.
Periodic work is scheduled again on the next app start by `LifePowersApp.onCreate`.

Owners who add a second DataStore file or keep secrets elsewhere: tell this owner, so delete everything
covers it.

## Export for spreadsheets

A zip (UTF-8 with BOM, CRLF, RFC 4180 quoting) with `daily_metrics.csv`, `events.csv` (local start and end
times from each row's own offset), `goals.csv`, `habit_checks.csv` (with the goal name joined in),
`breath_sessions.csv`, `experiments.csv`, and `experiment_days.csv`. A table this version lacks is skipped.

## Routes

| Route | Screen | Tab |
|---|---|---|
| `breathe` | `BreatheScreen(onNavigate, onBack)` | Today |
| `breathe/edit/{patternId}?preset={preset}` | `BreathEditScreen(patternId: Long = 0, presetName: String?, ...)` | Today |
| `breathe/session` | `BreathSessionScreen(onDone)`; no bottom bar. With an id it goes to `breathe/summary/<id>` and pops the session; with null it pops back to `breathe` (or replaces the session with it) | Today |
| `breathe/summary/{sessionId}` | `BreathSummaryScreen(sessionId: Long, ...)` | Today |
| `breathe/history` | `BreathHistoryScreen(...)` | Today |
| `experiments` | `ExperimentsScreen(...)` | Patterns |
| `experiment/new?behavior={behavior}&outcome={outcome}` | `ExperimentNewScreen(...)`; registered before the detail route. `onDone(id)` goes to `experiment/<id>` and pops the form | Patterns |
| `experiment/{id}` | `ExperimentScreen(id: Long, ...)`, `id` is `LongType` | Patterns |
| `privacy` | `PrivacyScreen(onNavigate, onBack)` | none |

Notice taps now also accept `breathe/session`, `experiments`, and `experiment/<id>`.

## Known limits

- The backup is built in memory (BackupCrypto works on whole byte arrays), so a backup with many photos needs
  roughly three times its size in free memory. Running out shows a plain message suggesting a backup without
  photos. A streaming format would need a new BackupCrypto version.
- Leaving the privacy screen while a job runs cancels it (viewModelScope). A failed save deletes
  the half written document. Restore rolls back per database through the transaction.
