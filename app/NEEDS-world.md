# Earth and sky: requests and notes

Written by the earth and sky pass (`com.lifepowers.app.world`, `di/WorldModule.kt`, and the sky tier in
`data/ForecastRepositoryImpl.kt`). No new dependency and no manifest change is needed.

## LifePowersApp (owner of `LifePowersApp.kt`)

Schedule the three hourly refresh and the evening sky notice at start. Existing work is kept, so this is
safe on every launch:

```kotlin
import com.lifepowers.app.world.WorldWork

override fun onCreate() {
    super.onCreate()
    syncManager.schedulePeriodic()
    WorldWork.schedule(this)
}
```

`WorldRefreshWorker` and `SkyEveningWorker` are `@HiltWorker`s and use the `HiltWorkerFactory` already
configured there.

## UI

- **Location permission before `refreshDeviceLocation()`.** It returns false without a grant. Request
  with `ActivityResultContracts.RequestMultiplePermissions()`:
  `arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)` for PRECISE, `arrayOf(ACCESS_COARSE_LOCATION)`
  for APPROXIMATE. Then `setLocation(LocationSetting(mode = mode))`, `refreshDeviceLocation()`, `refresh()`,
  which is what `SettingsViewModel` already does.
- **Refresh the device location when the app opens.** The manifest has no
  `ACCESS_BACKGROUND_LOCATION`, so the periodic worker's attempt (only when the stored fix is older than
  20 hours) may get no fresh fix and falls back to the last known one. For PRECISE and APPROXIMATE, call
  `worldRepository.refreshDeviceLocation()` from a foreground screen once a day (Today or Sky `init` is
  fine; it is cheap and returns false quietly without permission).
- **Notifications permission.** Sky, aurora, and ground notices go through `Notices.post`, which does
  nothing without `POST_NOTIFICATIONS` on API 33+. Ask once when any sky notice switch is on.
- **`setLocation` does not refresh by itself.** Call `refresh()` afterwards (the settings screen does), or
  `WorldWork.refreshNow(context)` to hand it to WorkManager so it survives leaving the screen.
- **What `SkyToday` holds.** `retrogrades` is empty while `transitsOn` is false (the scan only runs when
  someone will read it). `weather` is filled for today and tomorrow only, from the last refresh, partial
  values included. `planets`, `pairings`, `aurora`, and `kpNeeded` need a location; moon, eclipses (lunar
  only, `visibleHere = null`), and showers work without one. `tierReasons` names the specific things
  (for example "Orionids peak tonight") when the tier is BEST.
- **Ground events.** `groundEvents(since)` also contains `kind = "aurora"` moments, one per night when
  aurora was possible or likely. Volcano reports without a publish date are stamped with the time they
  were first seen.

## Data layer (owner of `data/`)

- `ForecastRepositoryImpl` now also injects `SkyTierSource` and `com.lifepowers.app.world.WorldPrefs`,
  and writes a `Component.SKY` row on every `recompute`. The row has a score (BEST 1.0, GOOD 0.4,
  NOT_GREAT -0.4, LIKELY_HARD -1.0) and `inputsUsed = 1` only with transits on; otherwise score null and
  0 inputs, so `Forecast.day` ignores it. Nothing else in the file changed.
- Samples written: `weather.*` and `air.*` from source `open_meteo`, `moon.illumination` from
  `astronomy_engine`, `space.kp_max` from `noaa_swpc`, all stamped at local noon. Each refresh calls
  `rebuildDaily(earliest touched date, today)` and `recompute(today)`.

## Core (owner of `core/`), optional

- Air quality is not backfilled: `OpenMeteo` has no historical air URL. If wanted, add
  `fun airArchiveUrl(p: GeoPoint, from: LocalDate, to: LocalDate)` using
  `https://air-quality-api.open-meteo.com/v1/air-quality?...&start_date=$from&end_date=$to&hourly=pm2_5,us_aqi&timezone=auto`
  and the world backfill will call it beside the weather archive.

## Behavior notes

- DataStore keys all start with `world_`. Markers: `world_backfill_through` (archive fetched through
  this date; after the first run it only fills a gap longer than the forecast's seven past days),
  `world_moon_through`, `world_aurora_night`.
- The archive backfill waits until the person has their own daily data (anything outside `weather.`,
  `air.`, `moon.`, `space.`, `forecast.`), then fetches from that date, at most a year back, to six days ago.
- `space.kp_max` starts at install: the NOAA product holds about a week, and only completed local days
  with at least six observed readings are written.
- Ground notices: only events from the last 24 hours, at most three per refresh, so a first run never
  floods. Every pending event is marked notified either way.
- The evening worker re-enqueues itself with `APPEND_OR_REPLACE` rather than `REPLACE`, because REPLACE
  from inside the running unique work would cancel the worker that called it. `rescheduleEvening`
  (called from `saveSkySettings`) uses REPLACE.

## Not verified by compilation

No Android SDK here. `WorldMath`, `EveningNotice`, and their tests compile and pass with the standalone
Kotlin 2.1.0 compiler against `:core`. The rest of the package compiled against real OkHttp 4.12, Dagger
2.53.1, and kotlinx.serialization jars, with hand-written stubs for the Android, WorkManager, DataStore,
and Room APIs it touches. `ForecastRepositoryImpl` was not compiled.
