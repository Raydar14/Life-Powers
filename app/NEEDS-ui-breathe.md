# Needs from other owners: breathing screens

Written by the breathing UI pass (`ui/screens/breathe/`). Only files in that package were added.
No new dependency is needed. The manifest already declares the Bluetooth permissions the screens ask for
(`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, and `ACCESS_FINE_LOCATION` for Android 9 to 11).

The view models inject `BreathRepository`, `HeartLink`, `BreathSessionController` (from
`domain/Phase3Contracts.kt`) and `GoalRepository`, so these need Hilt bindings from the breath package owner.

## Navigation (ui/navigation/LifePowersApp.kt)

Route strings live in `com.lifepowers.app.ui.screens.breathe.BreatheRoutes`. The screens build every
navigation string with `BreatheRoutes.edit(id, preset)` and `BreatheRoutes.summary(id)`; `edit` encodes the
preset name with `Uri.encode`, so navigation hands back the plain name.

| Route | Screen | Arguments |
|---|---|---|
| `BreatheRoutes.HOME` = `"breathe"` | `BreatheScreen(onNavigate = go, onBack = back)` | none |
| `BreatheRoutes.EDIT` = `"breathe/edit/{patternId}?preset={preset}"` | `BreathEditScreen(patternId, presetName, onNavigate = go, onBack = back)` | `patternId`: `NavType.LongType`, default `0L`; `preset`: `NavType.StringType`, nullable, default `null` |
| `BreatheRoutes.SESSION` = `"breathe/session"` | `BreathSessionScreen(onDone = { id -> ... })` | none. Hide the bottom bar here. |
| `BreatheRoutes.SUMMARY` = `"breathe/summary/{sessionId}"` | `BreathSummaryScreen(sessionId, onNavigate = go, onBack = back)` | `sessionId`: `NavType.LongType`, default `0L` |
| `BreatheRoutes.HISTORY` = `"breathe/history"` | `BreathHistoryScreen(onNavigate = go, onBack = back)` | none |

Suggested wiring:

```kotlin
import com.lifepowers.app.ui.screens.breathe.BreathEditScreen
import com.lifepowers.app.ui.screens.breathe.BreathHistoryScreen
import com.lifepowers.app.ui.screens.breathe.BreathSessionScreen
import com.lifepowers.app.ui.screens.breathe.BreathSummaryScreen
import com.lifepowers.app.ui.screens.breathe.BreatheRoutes
import com.lifepowers.app.ui.screens.breathe.BreatheScreen

composable(BreatheRoutes.HOME) { BreatheScreen(onNavigate = go, onBack = back) }
composable(
    route = BreatheRoutes.EDIT,
    arguments = listOf(
        navArgument("patternId") { type = NavType.LongType; defaultValue = 0L },
        navArgument("preset") { type = NavType.StringType; nullable = true; defaultValue = null },
    ),
) { entry ->
    BreathEditScreen(
        patternId = entry.arguments?.getLong("patternId") ?: 0L,
        presetName = entry.arguments?.getString("preset"),
        onNavigate = go,
        onBack = back,
    )
}
composable(BreatheRoutes.SESSION) {
    BreathSessionScreen(
        onDone = { id ->
            if (id != null) {
                navController.navigate(BreatheRoutes.summary(id)) {
                    popUpTo(BreatheRoutes.HOME) { inclusive = false }
                }
            } else {
                // Back to the Breathe screen, dropping the editor if the session started from it.
                if (!navController.popBackStack(BreatheRoutes.HOME, inclusive = false)) navController.popBackStack()
            }
        },
    )
}
composable(
    route = BreatheRoutes.SUMMARY,
    arguments = listOf(navArgument("sessionId") { type = NavType.LongType; defaultValue = 0L }),
) { entry ->
    BreathSummaryScreen(sessionId = entry.arguments?.getLong("sessionId") ?: 0L, onNavigate = go, onBack = back)
}
composable(BreatheRoutes.HISTORY) { BreathHistoryScreen(onNavigate = go, onBack = back) }
```

Notes on behavior the wiring should know:

- `BreathSessionScreen` is a calm full screen with its own clouds and navigation bar padding. Please add
  `BreatheRoutes.SESSION` to the routes that hide the floating bottom bar. While the visual guide is on it
  keeps the screen awake (`View.keepScreenOn`) and clears that when it leaves.
- `onDone(id)` fires once: with the saved session id after "Finish and save", with `null` after "Stop without
  saving", when a session under 30 seconds is finished (the controller discards it), or when the screen opens
  and no session appears within about four seconds. It also fires when the session ends on its own (planned
  length, notification action), with the new saved id when one appears.
- Pressing system back on the session screen just leaves it; the session keeps running in the controller's
  service and the Breathe screen shows a card to return to it.
- Start actions on Breathe, the editor, and the summary call `BreathSessionController.start(...)` and then
  navigate to `BreatheRoutes.SESSION`. Starting is disabled while a session already runs.
- The summary's "Breathe again" also navigates to `BreatheRoutes.SESSION`; with the suggested `popUpTo(HOME)`
  above, the stack stays short.
- An entry point is needed somewhere (Today or Settings) that calls `go(BreatheRoutes.HOME)`.

## For the breath package owner (implementations)

- The session screen draws the breath smoothly between controller updates by anchoring on
  `BreathLive.elapsedS` and computing `Breathing.pointAt(pattern, elapsed)` on each frame (never more than two
  seconds past the last update). That assumes `BreathLive.point == Breathing.pointAt(pattern, elapsedS)`, with
  `elapsedS` excluding paused time. If the controller adds a lead in, please say so and the screen can follow
  `point` instead.
- `HeartLink.stopScan()` is only called while `state` is `SCANNING`. `startScan()` is called only after
  `hasPermissions()` is true or the permission request was granted.
- The editor's "Count toward a habit" choice is passed as `goalId` to `start(pattern, goalId)` only; it is
  not stored on `SavedBreathPattern` because the contract has no field for it. Starting a saved pattern from
  the Breathe screen passes no goal. If a `goalId` field is added to `SavedBreathPattern`, the editor can keep it.
- "Start now" on an existing saved pattern saves the edits first (`savePattern`), then starts under that id.
  A new or preset pattern starts with `id = 0` and is not saved.
