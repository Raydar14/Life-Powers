# Breathing and the heart link: requests for other owners

Written by the breath backend pass (`breath/`, `di/BreathModule.kt`). No new dependency is needed.
`PersonalDatabase.kt`, the manifest, and the domain contracts were not changed.

## domain/Contracts.kt: EventKinds

Please add:

```kotlin
const val BREATH_SESSION = "breath_session"
```

`BreathRepositoryImpl` uses a private constant with the same value for now. Once it lands, it can
switch to `EventKinds.BREATH_SESSION`. Any timeline or CSV code that labels event kinds should
show it as "Breathing".

## notify/Notices.kt: notice id

The session's ongoing notice uses id and request code **1001** (`BreathSessionService.NOTICE_ID`).
3000 was suggested, but it falls inside the reminder range (2000 plus goal id), and 30 000 and up
belong to experiments. Please add `const val BREATH = 1001` to `Notices.Ids` so nobody else takes it.
The notice uses `Notices.CHANNEL_TIMER`, which is named "Habit timer". A better name would be
"Timer and breathing".

## AndroidManifest.xml: optional WAKE_LOCK

```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

With it, the service holds a partial wake lock for the session (capped at the planned length plus
5 minutes, or 3 hours), so voice and buzz cues stay on time with the screen off. Without it everything
still works and keeps correct time, but cues may come late while the phone sleeps. Tones are less
affected, since audio playing keeps the phone awake. The service checks for the permission, so
it is safe to add later.

## Navigation

The ongoing notice opens route **`breathe/session`** (`BreathSessionService.ROUTE`) via
`Notices.EXTRA_ROUTE`. The nav graph needs to send that route to the running session screen.

## For the UI

- Runtime permissions before scanning or connecting: `BreathMath.requiredPermissions(Build.VERSION.SDK_INT)`
  (BLUETOOTH_SCAN and BLUETOOTH_CONNECT on Android 12 and later, ACCESS_FINE_LOCATION before that).
  `HeartLink.hasPermissions()` reports whether they are granted. When they are missing, or Bluetooth is off,
  scanning and connecting set `state` to NEEDS_PERMISSION or BLUETOOTH_OFF and do nothing else. To ask
  for Bluetooth to be turned on, use `BluetoothAdapter.ACTION_REQUEST_ENABLE`.
- POST_NOTIFICATIONS is not needed for the session to run. Without it the foreground notice is hidden.
- Start the session from the foreground, for example from a button tap. Android 12 and later refuse to start the
  foreground service from the background. If that happens the session still runs, but only while the app is open.
- `live`:
  - The first frame is published inside `start()`, with no lead in: `live.point` is always
    `Breathing.pointAt(live.pattern, live.elapsedS)`, and `elapsedS` leaves out paused time.
  - On finish, `live` keeps one last frame with `running = false` while the save runs. Then
    `lastSavedId` is set (null when the session was discarded or the save failed), and only after that
    does `live` become null.
  - Tones, voice, and buzz stop as soon as `finish()` or `cancel()` is called.
- `start()` does nothing for a pattern `Breathing.isAllowed` rejects. A second `start()` while a session runs
  finishes and saves the first. Sessions under 30 seconds of active time are not saved.
- Saved session minutes run from start to finish by the wall clock, so pauses are counted.
- Starting a session reconnects the remembered sensor when nothing is connected. `HeartLink.remembered`
  is stored in the settings DataStore under keys `breath_heart_address` and `breath_heart_name`.
  Backup and delete-everything code should treat those keys as settings. They are not secrets.
- `deleteSession` removes the row, its timeline moment, and its breath.minutes sample, then rebuilds that day.
  A goal check it made stays, the same as timer checks.

## App start

Nothing is required. The heart link connects only when asked, and the controller makes no calls when it is created.
