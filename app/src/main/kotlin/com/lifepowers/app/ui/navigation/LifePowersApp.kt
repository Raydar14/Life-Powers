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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lifepowers.app.domain.ProfileRepository
import com.lifepowers.app.ui.components.BottomBar
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Tab
import com.lifepowers.app.ui.screens.checkin.CheckinScreen
import com.lifepowers.app.ui.screens.days.DaysScreen
import com.lifepowers.app.ui.screens.feel.FeelScreen
import com.lifepowers.app.ui.screens.journal.JournalScreen
import com.lifepowers.app.ui.screens.placeholder.PlaceholderScreen
import com.lifepowers.app.ui.screens.profile.ProfileScreen
import com.lifepowers.app.ui.screens.settings.SettingsScreen
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
}

/** Decides the first screen from the saved profile. Null until the profile has loaded once. */
@HiltViewModel
class AppViewModel @Inject constructor(profileRepository: ProfileRepository) : ViewModel() {
    val startRoute: StateFlow<String?> = profileRepository.profile
        .map { if (!it.onboardingDone && !it.playFirst) Routes.WELCOME else Routes.TODAY }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

/** Which tab the bar highlights for a route. Check-in belongs to Journal, like the mockup. */
private fun tabFor(route: String?): Tab? = when (route) {
    Routes.TODAY -> Tab.TODAY
    Routes.JOURNAL, Routes.CHECKIN -> Tab.JOURNAL
    Routes.GOALS -> Tab.GOALS
    Routes.SKY -> Tab.SKY
    Routes.PATTERNS -> Tab.PATTERNS
    else -> null
}

/** Clears the stack down to the start destination and lands on Today. */
fun NavHostController.goHome() {
    navigate(Routes.TODAY) {
        popUpTo(graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
fun LifePowersApp(viewModel: AppViewModel = hiltViewModel()) {
    val resolved by viewModel.startRoute.collectAsStateWithLifecycle()
    var start by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(resolved) { if (start == null && resolved != null) start = resolved }

    Box(modifier = Modifier.fillMaxSize().background(Ground)) {
        val startDestination = start
        if (startDestination == null) {
            Clouds(CloudPresets.today)
        } else {
            val navController = rememberNavController()
            val go: (String) -> Unit = { route -> navController.navigate(route) { launchSingleTop = true } }
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
                composable(Routes.JOURNAL) { JournalScreen(onNavigate = go) }
                composable(Routes.DAYS) { DaysScreen(onBack = back) }
                composable(Routes.FEEL) { FeelScreen(onDone = { navController.goHome() }, onSayMore = { go(Routes.CHECKIN) }) }
                composable(Routes.PROFILE) { ProfileScreen(onSaved = { navController.goHome() }, onBack = back) }
                composable(Routes.SETTINGS) { SettingsScreen(onNavigate = go, onBack = back) }
                composable(Routes.GOALS) { PlaceholderScreen(title = "Goals", kicker = "Habits and the timer") }
                composable(Routes.SKY) { PlaceholderScreen(title = "Sky and earth", kicker = "Moon, weather, ground") }
                composable(Routes.PATTERNS) { PlaceholderScreen(title = "Patterns", kicker = "What your data can support") }
            }

            val backStackEntry by navController.currentBackStackEntryAsState()
            val route = backStackEntry?.destination?.route
            if (route != null && route != Routes.WELCOME) {
                BottomBar(
                    active = tabFor(route),
                    onSelect = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(Routes.TODAY) { inclusive = tab.route == Routes.TODAY }
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                )
            }
        }
    }
}
