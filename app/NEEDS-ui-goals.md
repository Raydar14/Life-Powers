# Needs from the goals and patterns UI

Written by the goals and patterns UI engineer. Everything lives in
`ui/screens/goals/` and `ui/screens/patterns/`; nothing outside those packages was edited.

## Composables to wire into the NavHost

| Route | Composable |
|---|---|
| `Routes.GOALS` ("goals") | `GoalsScreen(onNavigate: (String) -> Unit)` |
| `Routes.PATTERNS` ("patterns") | `PatternsScreen(onNavigate: (String) -> Unit)` |
| `GoalRoutes.GOAL_EDIT` ("goal/edit/{goalId}?name={name}") | `GoalEditScreen(goalId: Long, prefillName: String?, onDone: () -> Unit, onBack: () -> Unit)` |
| `GoalRoutes.GOAL` ("goal/{goalId}") | `HabitScreen(goalId: Long, onNavigate: (String) -> Unit, onBack: () -> Unit)` |
| `GoalRoutes.TIMER` ("timer?goalId={goalId}") | `TimerScreen(goalId: Long?, onDone: () -> Unit)` |

Each composable also takes a trailing `viewModel` parameter with a `hiltViewModel()` default, like
`TodayScreen`, so call them without it.

Arguments: `goalId` is `NavType.LongType` with default `0L`; `name` is a nullable `NavType.StringType`
with default `null`. Long arguments cannot be nullable in Navigation, so the timer reads `0L` as "no goal".

```kotlin
// imports: androidx.navigation.NavType, androidx.navigation.navArgument,
// com.lifepowers.app.ui.screens.goals.*, com.lifepowers.app.ui.screens.patterns.PatternsScreen
composable(Routes.GOALS) { GoalsScreen(onNavigate = go) }
composable(Routes.PATTERNS) { PatternsScreen(onNavigate = go) }
composable(
    route = GoalRoutes.GOAL_EDIT,
    arguments = listOf(
        navArgument("goalId") { type = NavType.LongType; defaultValue = 0L },
        navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null },
    ),
) { entry ->
    GoalEditScreen(
        goalId = entry.arguments?.getLong("goalId") ?: 0L,
        prefillName = entry.arguments?.getString("name"),
        onDone = back,
        onBack = back,
    )
}
composable(
    route = GoalRoutes.GOAL,
    arguments = listOf(navArgument("goalId") { type = NavType.LongType }),
) { entry ->
    HabitScreen(goalId = entry.arguments?.getLong("goalId") ?: 0L, onNavigate = go, onBack = back)
}
composable(
    route = GoalRoutes.TIMER,
    arguments = listOf(navArgument("goalId") { type = NavType.LongType; defaultValue = 0L }),
) { entry ->
    TimerScreen(goalId = entry.arguments?.getLong("goalId")?.takeIf { it > 0L }, onDone = back)
}
```

## Navigation strings

- Every string comes from `GoalRoutes`: `edit(id, name)`, `goal(id)`, `timer(goalId)`.
  `timer(null)` is "timer"; `edit(id)` with no name is "goal/edit/<id>".
- `edit(id, name)` encodes the name with `URLEncoder` and swaps `+` for `%20`, because Navigation
  decodes query values with `Uri.decode`, which leaves `+` as a plus sign. `prefillName` arrives decoded.
- New goals use id `0`. The edit screen shapes the form from the suggested name: "Walk 3 times a week"
  becomes a weekly count of 3, "Meditate 10 minutes a day" a daily 10 minutes, "Less phone time" a phone
  time measurement, anything with "journal" a journal checked goal.

## Bottom bar

- `tabFor()` should map `GoalRoutes.GOAL`, `GoalRoutes.GOAL_EDIT` and `GoalRoutes.TIMER` to
  `Tab.GOALS` (the back stack entry's `destination.route` is the pattern string), so the Goals tab
  stays lit on its detail screens.
- Every screen leaves 120 dp at the bottom for the floating bar; the Goals tab leaves 190 dp because
  its floating "New goal" button sits 92 dp above the bottom, just over the bar.
- Optional: hide the bar on `GoalRoutes.TIMER` so the running timer is fully calm. The screen works either way.

## From the goals and patterns implementations

- `GoalRepository.save` should re-arm reminders (`ReminderScheduler.rescheduleAll()`) itself. The UI
  injects only `GoalRepository`, `TimerController` and `PatternRepository`, never the scheduler.
- `GoalRepository.views(today)` and `view(id, today)`: archived goals are filtered out again in the UI,
  and `view` emitting null shows "This goal is archived or gone."
- `GoalEditViewModel` loads an existing goal with `goalRepository.goals.first()`, so editing works for
  active goals only.
- Timer checks should carry `source = "timer"` and minutes in `amount`; automatic ones `source = "auto"`.
  The habit screen words each of today's checks from these.
- The habit screen hides the check button once a done or not goal is met for its period, so repeat
  checks only happen for counts, minutes and measurements.
- `TimerController.running` must be a hot `StateFlow`; the view model reads `.value` once to refuse a
  second start while a session runs. "Let it go" calls `cancel()` and leaves the screen, like "Stop and log".
- Pattern keys are labelled by `PatternLabels.label(key)`: metric keys, `habit:<name>` for habit series,
  bare event kinds ("alcohol", "stress_event") or `event:<kind>`, and `symptom.<kind>`.

## API worth a look in CI

- `TimePickDialog` in `goals/GoalBits.kt` uses `TimePicker`, `rememberTimePickerState` and
  `TimePickerDefaults.colors(...)` under `@OptIn(ExperimentalMaterial3Api::class)`. The named color
  parameters used are `clockDialColor`, `selectorColor`, `containerColor`,
  `periodSelectorSelectedContainerColor`, `timeSelectorSelectedContainerColor` and
  `timeSelectorUnselectedContainerColor`.
- `HabitViewModel` uses `flatMapLatest` under `@OptIn(ExperimentalCoroutinesApi::class)`.
- Dialogs use `androidx.compose.ui.window.Dialog` with a `SoftCard` inside rather than `AlertDialog`,
  and the time dialog sets `DialogProperties(usePlatformDefaultWidth = false)` so the clock dial fits.
