package com.lifepowers.app.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.DashedCard
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.app.ui.components.HotButton
import com.lifepowers.app.ui.components.Kicker
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.SectionLabel
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TierDot
import com.lifepowers.app.ui.components.softShadow
import com.lifepowers.app.ui.navigation.Routes
import com.lifepowers.app.ui.theme.BodyText
import com.lifepowers.app.ui.theme.DeepLavender
import com.lifepowers.app.ui.theme.DeepPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.PanelInkOnHot
import com.lifepowers.app.ui.theme.Plum
import com.lifepowers.app.ui.theme.White
import com.lifepowers.app.ui.theme.color
import com.lifepowers.app.ui.theme.shadowColor
import com.lifepowers.app.ui.theme.sublineColor
import com.lifepowers.app.ui.theme.textColor
import com.lifepowers.core.Component
import com.lifepowers.core.Tier

@Composable
fun TodayScreen(onNavigate: (String) -> Unit, viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(CloudPresets.today)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Header(state, onNavigate)
            DayCard(state, onNavigate)
            QuickRow(onNavigate)
            SectionLabel(text = "Each panel wears its own forecast", modifier = Modifier.padding(start = 6.dp, top = 2.dp))
            ComponentGrid(state, onNavigate)
            MindCard(state, onNavigate)
        }
    }
}

@Composable
private fun Header(state: TodayUiState, onNavigate: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = Fmt.dateKicker(state.today), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "${Fmt.greeting()}, ${state.displayName ?: "there"}",
                color = Ink, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(
                text = "Settings", onClick = { onNavigate(Routes.SETTINGS) },
                textColor = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp), shadowAlpha = 0.1f,
            )
            val initial = state.displayName?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "Me"
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .softShadow(RoundedCornerShape(999.dp), Ink, 0.1f, 8.dp)
                    .clip(CircleShape)
                    .background(White)
                    .clickable { onNavigate(Routes.PROFILE) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = initial, color = DeepPink, fontSize = if (initial.length > 1) 11.sp else 14.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun DayCard(state: TodayUiState, onNavigate: (String) -> Unit) {
    val tier = state.dayTier
    if (tier == Tier.UNKNOWN) {
        DashedCard(
            modifier = Modifier.fillMaxWidth(), radius = 28.dp,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), gap = 6.dp,
        ) { DayCardBody(state, onNavigate) }
    } else {
        SoftCard(
            modifier = Modifier.fillMaxWidth(), color = tier.color(),
            shadowColor = tier.shadowColor(), shadowAlpha = 0.16f, elevation = 14.dp,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), gap = 6.dp,
        ) { DayCardBody(state, onNavigate) }
    }
}

@Composable
private fun DayCardBody(state: TodayUiState, onNavigate: (String) -> Unit) {
    val tier = state.dayTier
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Kicker("Today's forecast", color = if (tier == Tier.BEST) PanelInkOnHot else Plum)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable { onNavigate(Routes.DAYS) }.padding(4.dp),
        ) {
            for ((date, dayTier) in state.week) {
                TierDot(tier = dayTier, size = 10.dp, ringed = date == state.today)
            }
        }
    }
    Text(text = state.dayTitle, color = tier.textColor().let { if (tier == Tier.UNKNOWN) Muted else it }, fontSize = 28.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black)
    BodyLine(text = state.daySentence, color = tier.sublineColor())
}

@Composable
private fun QuickRow(onNavigate: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(
            text = "How I feel", onClick = { onNavigate(Routes.FEEL) }, modifier = Modifier.weight(1f),
            shadowAlpha = 0.07f, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
        )
        PillButton(
            text = "Craving", onClick = { onNavigate(Routes.JOURNAL) }, modifier = Modifier.weight(1f),
            shadowAlpha = 0.07f, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
        )
        HotButton(text = "Talk", onClick = { onNavigate(Routes.CHECKIN) }, modifier = Modifier.weight(1f), fontSize = 13.sp)
    }
}

private data class TileSpec(
    val title: String,
    val tier: Tier,
    val value: String,
    val valueSuffix: String? = null,
    val subline: String,
    val valueSize: TextUnit = 30.sp,
    val onClick: (() -> Unit)? = null,
)

@Composable
private fun ComponentGrid(state: TodayUiState, onNavigate: (String) -> Unit) {
    val recovery = TileSpec(
        title = "Recovery",
        tier = if (state.readiness == null) Tier.UNKNOWN else state.tier(Component.RECOVERY),
        value = Fmt.num(state.readiness),
        subline = if (state.hrv != null) "HRV ${Fmt.num(state.hrv)} ms, ${Fmt.zWord(state.hrvZ)}" else "readiness and HRV from your ring",
    )
    val glucose = TileSpec(
        title = "Glucose",
        tier = if (state.glucoseOvernight == null) Tier.UNKNOWN else state.tier(Component.METABOLIC),
        value = Fmt.num(state.glucoseOvernight),
        subline = if (state.glucoseMax != null) "high of ${Fmt.num(state.glucoseMax)} yesterday" else "overnight mean, mg/dL",
    )
    val bp = TileSpec(
        title = "Blood pressure",
        tier = if (state.systolic == null || state.diastolic == null) Tier.UNKNOWN else state.tier(Component.CARDIO),
        value = Fmt.num(state.systolic),
        valueSuffix = "/${Fmt.num(state.diastolic)}",
        subline = if (state.systolic != null) "latest cuff reading" else "no reading yet",
    )
    val phase = state.phase
    val hormones = TileSpec(
        title = "Hormones",
        tier = if (phase == null) Tier.UNKNOWN else state.tier(Component.HORMONAL),
        value = if (phase == null) "--" else Fmt.phaseTitle(phase.phase, phase.cycleDay),
        subline = if (phase == null) "set your profile to read this" else "from ${phase.basis.replace('_', ' ')}, ${(phase.confidence * 100).toInt()} percent sure",
        valueSize = 22.sp,
    )
    val sky = TileSpec(
        title = "Sky",
        tier = state.tier(Component.SKY),
        value = "Coming next phase",
        subline = "moon, weather and transits",
        valueSize = 22.sp,
        onClick = { onNavigate(Routes.SKY) },
    )
    val cravings = TileSpec(
        title = "Cravings",
        tier = state.tier(Component.CRAVINGS),
        value = "${state.cravingCount} logged",
        subline = if (state.cravingCount < 14) "a pattern needs about 14" else "enough for a pattern",
        valueSize = 22.sp,
        onClick = { onNavigate(Routes.JOURNAL) },
    )
    val rows = listOf(recovery to glucose, bp to hormones, sky to cravings)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((left, right) in rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(left, Modifier.weight(1f))
                Tile(right, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Tile(spec: TileSpec, modifier: Modifier) {
    val tier = spec.tier
    val body: @Composable () -> Unit = {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = spec.title, color = tier.textColor(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text(text = tier.label, color = tier.textColor(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = spec.value, color = tier.textColor(), fontSize = spec.valueSize,
                lineHeight = (spec.valueSize.value * 1.1f).sp, fontWeight = FontWeight.Black,
            )
            if (spec.valueSuffix != null) {
                Text(
                    text = spec.valueSuffix, color = if (tier == Tier.BEST) PanelInkOnHot else Plum,
                    fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        Text(text = spec.subline, color = tier.sublineColor(), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    }
    if (tier == Tier.UNKNOWN) {
        DashedCard(modifier = modifier.height(108.dp), onClick = spec.onClick) { body() }
    } else {
        SoftCard(
            modifier = modifier.height(108.dp), color = tier.color(), radius = 24.dp,
            shadowColor = tier.shadowColor(), shadowAlpha = 0.2f,
            contentPadding = PaddingValues(horizontal = 15.dp, vertical = 13.dp), gap = 3.dp,
            onClick = spec.onClick,
        ) { body() }
    }
}

@Composable
private fun MindCard(state: TodayUiState, onNavigate: (String) -> Unit) {
    val tier = state.tier(Component.MIND)
    val message = state.gapMessage ?: state.mindSummary
    val content: @Composable () -> Unit = {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Mind and voice", color = tier.textColor(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Text(text = tier.label, color = if (tier == Tier.NOT_GREAT) DeepLavender else tier.textColor(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(text = message, color = if (tier == Tier.UNKNOWN) Muted else BodyText.let { if (tier == Tier.BEST) PanelInkOnHot else it }, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
            }
            Text(text = "Say more", color = tier.textColor().let { if (tier == Tier.UNKNOWN) Ink else it }, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
    if (tier == Tier.UNKNOWN) {
        DashedCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp), onClick = { onNavigate(Routes.CHECKIN) }) { content() }
    } else {
        SoftCard(
            modifier = Modifier.fillMaxWidth(), color = tier.color(), radius = 24.dp,
            shadowColor = tier.shadowColor(), shadowAlpha = 0.2f,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp), gap = 0.dp,
            onClick = { onNavigate(Routes.CHECKIN) },
        ) { content() }
    }
}
