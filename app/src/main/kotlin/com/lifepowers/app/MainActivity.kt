package com.lifepowers.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lifepowers.app.ui.navigation.LifePowersApp as LifePowersUi
import com.lifepowers.app.ui.theme.LifePowersTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LifePowersTheme {
                LifePowersUi()
            }
        }
    }
}
