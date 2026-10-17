package com.lifepowers.app.ui.screens.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.theme.Ink

/** Goals, Sky and Patterns arrive in the next phase. This keeps navigation complete in the same style. */
@Composable
fun PlaceholderScreen(title: String, kicker: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(CloudPresets.placeholder)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader(kicker = kicker, title = title, titleSize = 28.sp)
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Coming in the next phase", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                BodyLine(text = "This part of the picture is still being built. Everything you log now will be here when it opens.")
            }
        }
    }
}
