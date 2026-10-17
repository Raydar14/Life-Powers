package com.lifepowers.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lifepowers.app.domain.ProfileRepository
import com.lifepowers.app.ui.components.BottomBar
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Tab
import com.lifepowers.app.ui.screens.checkin.CheckinScreen
import com.lifepowers.app.ui.screens.days.DaysScreen
import com.lifepowers.app.ui.screens.feel.FeelScreen
import com.lifepowers.app.ui.screens.goals.GoalEditScreen
import com.lifepowers.app.ui.screens.goals.GoalRoutes
import com.lifepowers.app.ui.screens.goals.GoalsScreen
import com.lifepowers.app.ui.screens.goals.HabitScreen
import com.lifepowers.app.ui.screens.goals.TimerScreen
import com.lifepowers.app.ui.screens.journal.JournalEntryScreen
import com.lifepowers.app.ui.screens.journal.JournalScreen
import com.lifepowers.app.ui.screens.journal.TarotPullScreen
import com.lifepowers.app.ui.screens.journal.TarotScreen
import com.lifepowers.app.ui.screens.patterns.PatternsScreen
import com.lifepowers.app.ui.screens.profile.ProfileScreen
import com.lifepowers.app.ui.screens.settings.SettingsScreen
import com.lifepowers.app.ui.screens.sky.SkyScreen
import com.lifepowers.app.ui.screens.today.TodayScreen
import com.lifepowers.app.ui.screens.welcome.WelcomeScreen
import com.lifepowers.app.ui.theme.Ground
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

object Routes {
    const val WELCOME = "welcome"
    const val TODAY = "today"
    const val CHECKIN = "checkin"
    const val JOURNAL = "journal"
    const val DAYS = "days"
    const val FEEL = "feel"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val GOALS = "goals"
    const val SKY = "sky"
    const val PATTERNS = "patterns"

    /** Journal sub screens. The journal package builds the concrete strings. */
    const val JOURNAL_ENTRY = "journal/entry/{entryId}"
    const val TAROT = "tarot"
    const val TAROT_PULL = "tarot/pull/{pullId}?entryId={entryId}"

    /** The timer with no goal attached, for links and notices. */
    const val TIMER_PLAIN = "timer"

    /** Top level routes a notice may name directly. */
    internal val byName: Set<String> = setOf(TODAY, CHECKIN, JOURNAL, DAYS, FEEL, PROFILE, SETTINGS, GOALS, SKY, PATTERNS, TAROT)
}

/** Decides the first screen from the saved profile. Null until the profile has loaded once. */
@HiltViewModel
class AppViewModel @Inject constructor(profileRepository: ProfileRepository) : ViewModel() {
    val startRoute: StateFlow<String?> = profileRepository.profile
        .map { if (!it.onboardingDone && !it.playFirst) Routes.WELCOME else Routes.TODAY }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

/** Which tab the bar highlights for a route pattern. Check-in, entries and tarot belong to Journal. */
private fun tabFor(route: String?): Tab? = when (route) {
    Routes.TODAY -> Tab.TODAY
    Routes.JOURNAL, Routes.CHECKIN, Routes.JOURNAL_ENTRY, Routes.TAROT, Routes.TAROT_PULL -> Tab.JOURNAL
    Routes.GOALS, GoalRoutes.GOAL, GoalRoutes.GOAL_EDIT, GoalRoutes.TIMER -> Tab.GOALS
    Routes.SKY -> Tab.SKY
    Routes.PATTERNS -> Tab.PATTERNS
    else -> null
}

/** Routes drawn without the bottom bar: first run, and the calm timer. */
private fun hidesBar(route: String?): Boolean = route == Routes.WELCOME || route == GoalRoutes.TIMER

private val tabRoutes: Set<String> = Tab.values().map { it.route }.toSet()

/** Clears the stack down to the start destination and lands on Today. */
fun NavHostController.goHome() {
    navigate(Routes.TODAY) {
        popUpTo(graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
    }
}

/** Opens a tab the way the bottom bar does, so the stack never grows tab on tab. */
fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(Routes.TODAY) { inclusive = route == Routes.TODAY }
        launchSingleTop = true
    }
}

/**
 * Turns a route named by a notice ("goals", "sky", "timer", "patterns") into a concrete route in this
 * graph, or null when nothing here matches.
 */
internal fun resolveNoticeRoute(raw: String): String? {
    val r = raw.trim().removePrefix("/")
    return when {
        r.isEmpty() -> null
        r == "timer" -> Routes.TIMER_PLAIN
        r in Routes.byName -> r
        Regex("""goal/\d+""").matches(r) -> r
        Regex("""timer\?goalId=\d+""").matches(r) -> r
        Regex("""journal/entry/\d+""").matches(r) -> r
        else -> null
    }
}

@Composable
fun LifePowersApp(
    pendingRoute: String? = null,
    onRouteHandled: () -> Unit = {},
    viewModel: AppViewModel = hiltViewModel(),
) {
    val resolved by viewModel.startRoute.collectAsStateWithLifecycle()
    var start by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(resolved) { if (start == null && resolved != null) start = resolved }

    Box(modifier = Modifier.fillMaxSize().background(Ground)) {
        val startDestination = start
        if (startDestination == null) {
            Clouds(CloudPresets.today)
        } else {
            val navController = rememberNavController()
            val go: (String) -> Unit = { route ->
                if (route in tabRoutes) navController.openTab(route) else navController.navigate(route) { launchSingleTop = true }
            }
            val back: () -> Unit = { navController.popBackStack() }

            NavHost(navController = navController, startDestination = startDestination) {
                composable(Routes.WELCOME) {
                    WelcomeScreen(
                        onSetUp = { go(Routes.PROFILE) },
                        onPlayFirst = { navController.goHome() },
                    )
                }
                composable(Routes.TODAY) { TodayScreen(onNavigate = go) }
                composable(Routes.CHECKIN) { CheckinScreen(onBack = back) }
                composable(Routes.DAYS) { DaysScreen(onBack = back) }
                composable(Routes.FEEL) { FeelScreen(onDone = { navController.goHome() }, onSayMore = { go(Routes.CHECKIN) }) }
                composable(Routes.PROFILE) { ProfileScreen(onSaved = { navController.goHome() }, onBack = back) }
                composable(Routes.SETTINGS) { SettingsScreen(onNavigate = go, onBack = back) }

                // Journal and tarot
                composable(Routes.JOURNAL) { JournalScreen(onNavigate = go) }
                composable(
                    route = Routes.JOURNAL_ENTRY,
                    arguments = listOf(navArgument("entryId") { type = NavType.LongType; defaultValue = 0L }),
                ) { entry ->
                    JournalEntryScreen(entryId = entry.arguments?.getLong("entryId") ?: 0L, onBack = back, onNavigate = go)
                }
                composable(Routes.TAROT) { TarotScreen(onNavigate = go, onBack = back) }
                composable(
                    route = Routes.TAROT_PULL,
                    arguments = listOf(
                        navArgument("pullId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("entryId") { type = NavType.LongType; defaultValue = 0L },
                    ),
                ) { entry ->
                    TarotPullScreen(
                        pullId = entry.arguments?.getLong("pullId") ?: 0L,
                        entryId = entry.arguments?.getLong("entryId") ?: 0L,
                        onBack = back,
                    )
                }

                // Goals, habits, timer
                composable(Routes.GOALS) { GoalsScreen(onNavigate = go) }
                composable(
                    route = GoalRoutes.GOAL_EDIT,
                    arguments = listOf(
                        navArgument("goalId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    GoalEditScreen(
                        goalId = entry.arguments?.getLong("goalId") ?: 0L,
                        prefillName = entry.arguments?.getString("name")?.takeIf { it.isNotBlank() },
                        onDone = back,
                        onBack = back,
                    )
                }
                composable(
                    route = GoalRoutes.GOAL,
                    arguments = listOf(navArgument("goalId") { type = NavType.LongType; defaultValue = 0L }),
                ) { entry ->
                    HabitScreen(goalId = entry.arguments?.getLong("goalId") ?: 0L, onNavigate = go, onBack = back)
                }
                composable(
                    route = GoalRoutes.TIMER,
                    arguments = listOf(navArgument("goalId") { type = NavType.LongType; defaultValue = -1L }),
                ) { entry ->
                    val raw = entry.arguments?.getLong("goalId") ?: -1L
                    TimerScreen(goalId = raw.takeIf { it > 0L }, onDone = back)
                }

                composable(Routes.SKY) { SkyScreen(onNavigate = go) }
                composable(Routes.PATTERNS) { PatternsScreen(onNavigate = go) }
            }

            // A notice tap names a route; open it once the graph exists.
            LaunchedEffect(pendingRoute) {
                val raw = pendingRoute ?: return@LaunchedEffect
                val target = resolveNoticeRoute(raw)
                if (target != null) {
                    runCatching {
                        if (target in tabRoutes) navController.openTab(target) else navController.navigate(target) { launchSingleTop = true }
                    }
                }
                onRouteHandled()
            }

            val backStackEntry by navController.currentBackStackEntryAsState()
            val route = backStackEntry?.destination?.route
            if (route != null && !hidesBar(route)) {
                BottomBar(
                    active = tabFor(route),
                    onSelect = { tab -> navController.openTab(tab.route) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                )
            }
        }
    }
}
