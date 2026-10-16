package com.lifepowers.app.ui.screens.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.ProfileRepository
import com.lifepowers.app.domain.UserProfileState
import com.lifepowers.app.ui.components.Chip
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Kicker
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TierDot
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.theme.BodyText
import com.lifepowers.app.ui.theme.DeepPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.White
import com.lifepowers.core.Tier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(private val profileRepository: ProfileRepository) : ViewModel() {
    val profile: StateFlow<UserProfileState?> = profileRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Door two: mark play first, then move on. */
    fun playFirst(onDone: () -> Unit) {
        viewModelScope.launch {
            val current = profileRepository.profile.first()
            profileRepository.save(current.copy(playFirst = true))
            onDone()
        }
    }
}

@Composable
fun WelcomeScreen(onSetUp: () -> Unit, onPlayFirst: () -> Unit, viewModel: WelcomeViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val name = profile?.displayName?.takeIf { it.isNotBlank() }

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(CloudPresets.welcome)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 28.dp),
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TierDot(Tier.BEST, 18.dp)
                TierDot(Tier.GOOD, 18.dp)
                TierDot(Tier.NOT_GREAT, 18.dp)
                TierDot(Tier.LIKELY_HARD, 18.dp)
                TierDot(Tier.UNKNOWN, 18.dp)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (name != null) "Hello, $name." else "Hello.",
                color = Ink, fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "This is your whole picture in one place: body, mind, moon, and the things you do. You choose how to begin.",
                color = BodyText, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(48.dp))

            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                color = Ink, radius = 30.dp, shadowColor = Ink, shadowAlpha = 0.28f, elevation = 16.dp,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp), gap = 8.dp,
                onClick = onSetUp,
            ) {
                Kicker("Door one", color = LightPink)
                Text(text = "Set me up properly", color = White, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
                Text(
                    text = "Ten to fifteen minutes in short pieces: your devices, your profile, your place, your words, your goals. Leave and come back any time.",
                    color = Lavender, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                )
                WrapRow(modifier = Modifier.padding(top = 4.dp), horizontalGap = 6.dp, verticalGap = 6.dp) {
                    for (label in listOf("Devices", "You", "Place", "Words", "Goals")) {
                        Chip(text = label, background = White.copy(alpha = 0.14f), textColor = White, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                radius = 30.dp, shadowAlpha = 0.1f, elevation = 14.dp,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp), gap = 8.dp,
                onClick = { viewModel.playFirst(onPlayFirst) },
            ) {
                Kicker("Door two", color = DeepPink)
                Text(text = "Let me play first", color = Ink, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
                Text(
                    text = "Open the app with sample data and touch everything. A small card on Today will offer one setup piece at a time, whenever you feel like it.",
                    color = BodyText, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
            MutedLine(
                text = "Everything stays on this phone. Assistant features are off until you turn them on.",
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
        }
    }
}
