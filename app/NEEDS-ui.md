# Needs from the UI layer

Things the UI (com.lifepowers.app.ui, MainActivity) needs from outside its own packages, and how it
works around each until it exists. Written by the UI engineer; nothing here blocks a build.

## Phase 2 bindings the UI injects
- `SkyViewModel` injects `WorldRepository` and `ForecastRepository`; `TodayViewModel` now also injects
  `WorldRepository` and `GoalRepository`; `SettingsViewModel` injects `WorldRepository`. Hilt needs a
  binding for both interfaces (world and goals packages) or the app module will not compile.
- `SkyViewModel` calls `worldRepository.refresh()` once when the Sky screen opens and ignores failures.
  Settings calls `refresh()` after a location change.
- Precise location requests both `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` together (Android 12
  and later ignore a fine only request). If only coarse is granted the UI saves `APPROXIMATE`. After
  saving the mode it calls `refreshDeviceLocation()`; the repository is expected to fill `label` and
  `point` for device modes. A city choice saves `LocationSetting(CITY, "Name, Region", point)`.
- The Sky screen filters `groundEvents()` to kinds other than "aurora" and reads aurora from `SkyToday`.

## Navigation from other packages
- `LifePowersApp` calls these by exact signature: `GoalsScreen(onNavigate)`, `GoalEditScreen(goalId,
  prefillName, onDone, onBack)`, `HabitScreen(goalId, onNavigate, onBack)`, `TimerScreen(goalId: Long?,
  onDone)`, `GoalRoutes.GOAL_EDIT / GOAL / TIMER`, `PatternsScreen(onNavigate)`, `JournalEntryScreen(entryId,
  onBack, onNavigate)`, `TarotScreen(onNavigate, onBack)`, `TarotPullScreen(pullId, entryId, onBack)`.
- The `name` argument of the goal edit route is read as nav-compose decodes it; callers should
  `Uri.encode` the name when building the route.
- Notice taps: `MainActivity` reads `Notices.EXTRA_ROUTE` in `onCreate` and `onNewIntent`. Accepted values are
  the top level route names (today, journal, goals, sky, patterns, settings, days, feel, checkin, profile,
  tarot), "timer", and the concrete forms `goal/<id>`, `timer?goalId=<id>`, `journal/entry/<id>`. Anything
  else is ignored.

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

## Still open
- The Days screen shows tier dots, daily metric points with z words, and events; the one night on one
  clock chart from the mockup needs the sample level series and comes with Timeline work.
- The "sample data labeled as sample" for door two is a data layer concern; the UI only sets `playFirst`.
- Settings rows for Forecast, Privacy and data, and Appearance are still shown as "next phase".
- Personal sky lines like "your own full moons: sleep 22 minutes shorter" on the Sky panel wait for
  pattern findings keyed to moon illumination; the panel shows only computed astronomy for now.
- Temperatures are shown as Open-Meteo returns them (Celsius by default) with a degree sign; a unit
  setting would belong under Appearance.
