# Needs from other owners: experiments

## App start (LifePowersApp.onCreate)

Schedule the hourly experiment check next to the other work:

```kotlin
import com.lifepowers.app.experiments.ExperimentWork

ExperimentWork.schedule(this)
```

It is a unique periodic request (`experiment_reminder_periodic`, every hour, KEEP). The repository also
calls it whenever an experiment starts, so nothing breaks before this line lands; it only means
reminders resume after a reboot or update without opening the app first.

The worker is a `@HiltWorker` (`ExperimentReminderWorker`), so it relies on the existing
`HiltWorkerFactory` configuration. It posts on `Notices.CHANNEL_REMINDERS` with id
`30_000 + (experimentId % 10_000)` and route `"experiments"`. Nothing else is needed from
`notify`, but if `Notices.Ids` gets an entry for it, it should be `fun experiment(id: Long) = 30_000 + (id % 10_000).toInt()`.

## Navigation (ui/navigation/LifePowersApp.kt)

Route strings live in `com.lifepowers.app.ui.screens.experiments.ExperimentRoutes`.

| Route | Screen | Arguments |
|---|---|---|
| `ExperimentRoutes.LIST` = `"experiments"` | `ExperimentsScreen(onNavigate = go, onBack = back)` | none |
| `ExperimentRoutes.NEW` = `"experiment/new?behavior={behavior}&outcome={outcome}"` | `ExperimentNewScreen(prefillBehavior, prefillOutcome, onDone = { id -> ... }, onBack = back)` | `behavior`: `NavType.StringType`, nullable, default null; `outcome`: `NavType.StringType`, nullable, default null |
| `ExperimentRoutes.DETAIL` = `"experiment/{id}"` | `ExperimentScreen(id, onNavigate = go, onBack = back)` | `id`: `NavType.LongType` |

Suggested wiring:

```kotlin
composable(ExperimentRoutes.LIST) { ExperimentsScreen(onNavigate = go, onBack = back) }
composable(
    route = ExperimentRoutes.NEW,
    arguments = listOf(
        navArgument("behavior") { type = NavType.StringType; nullable = true; defaultValue = null },
        navArgument("outcome") { type = NavType.StringType; nullable = true; defaultValue = null },
    ),
) { entry ->
    ExperimentNewScreen(
        prefillBehavior = entry.arguments?.getString("behavior"),
        prefillOutcome = entry.arguments?.getString("outcome"),
        // Replace the form with the new experiment so Back goes to the list, not the form.
        onDone = { id -> navController.navigate(ExperimentRoutes.detail(id)) { popUpTo(ExperimentRoutes.NEW) { inclusive = true } } },
        onBack = back,
    )
}
composable(
    route = ExperimentRoutes.DETAIL,
    arguments = listOf(navArgument("id") { type = NavType.LongType }),
) { entry -> ExperimentScreen(id = entry.arguments?.getLong("id") ?: 0L, onNavigate = go, onBack = back) }
```

Notes:

- Register `NEW` before `DETAIL`. `"experiment/new"` should win over `"experiment/{id}"` because it is
  an exact segment match and `new` does not parse as a Long, but declaring it first removes any doubt.
- `ExperimentRoutes.new()` leaves out null parameters, so it produces `"experiment/new"`,
  `"experiment/new?behavior=..."`, or both parameters, encoded with `Uri.encode`.
- Add `"experiments"` to `Routes.byName` so the notice route opens the list.
- The three routes belong under the Patterns tab for the bottom bar highlight.
- Today navigates to `"breathe"` from the new Breathe pill; that route belongs to the breathing owner.

## Assistant

`PatternsScreen` now places `com.lifepowers.app.ui.screens.assistant.AssistantNarrativeCard(findings, goals)`
at the top of the findings list, passing the displayed insight sentences and an empty goals list. It
compiles once that file exists with the signature
`AssistantNarrativeCard(findings: List<String>, goals: List<String>, modifier: Modifier = Modifier)`.
