package com.lifepowers.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.lifepowers.app.notify.Notices
import com.lifepowers.app.ui.navigation.LifePowersApp as LifePowersUi
import com.lifepowers.app.ui.theme.LifePowersTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** A route named by a notice tap, waiting for the navigation graph. Cleared once handled. */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // On a configuration change the route was already handled by the previous instance.
        if (savedInstanceState == null) pendingRoute.value = routeFrom(intent)
        setContent {
            LifePowersTheme {
                LifePowersUi(
                    pendingRoute = pendingRoute.value,
                    onRouteHandled = { pendingRoute.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFrom(intent)?.let { pendingRoute.value = it }
    }

    private fun routeFrom(intent: Intent?): String? =
        intent?.getStringExtra(Notices.EXTRA_ROUTE)?.trim()?.takeIf { it.isNotEmpty() }
}
