package com.lifepowers.app.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.TierDot
import com.lifepowers.app.ui.components.softShadow
import com.lifepowers.app.domain.LocationMode
import com.lifepowers.app.ui.navigation.Routes
import com.lifepowers.app.experiments.ExperimentMath
import com.lifepowers.app.ui.screens.experiments.ExperimentRoutes
import com.lifepowers.app.ui.screens.sky.SkyText
import com.lifepowers.app.ui.theme.BodyText
import com.lifepowers.app.ui.theme.DeepLavender
import com.lifepowers.app.ui.theme.DeepPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.PanelInkOnHot
import com.lifepowers.app.ui.theme.Plum
import com.lifepowers.core.Sky
import com.lifepowers.app.ui.theme.White
import com.lifepowers.app.ui.theme.hexColor
import com.lifepowers.app.ui.theme.shadowColor
import com.lifepowers.app.ui.theme.sublineColor
import com.lifepowers.app.ui.theme.textColor
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.SoftBlue
import com.lifepowers.core.Component
import com.lifepowers.core.Condition
import com.lifepowers.core.Tier

/** The breathing screen's route. Named here because the navigation file belongs to another package. */
private const val BREATHE_ROUTE = "breathe"

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
            state.experiment?.let { ExperimentCard(it, onNavigate) }
            ComponentGrid(state, onNavigate)
            MindCard(state, onNavigate)
            MutedLine(
                text = "Each panel wears its own forecast, so two panels can disagree.",
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

@Composable
private fun Header(state: TodayUiState, onNavigate: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "${Fmt.greeting()}, ${state.displayName ?: "there"}",
                color = Ink, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black,
            )
            Text(text = Fmt.dateLine(state.today), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
            modifier = Modifier.fillMaxWidth(), color = tier.hexColor(),
            shadowColor = tier.shadowColor(), shadowAlpha = 0.16f, elevation = 14.dp,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), gap = 6.dp,
        ) { DayCardBody(state, onNavigate) }
    }
}

@Composable
private fun DayCardBody(state: TodayUiState, onNavigate: (String) -> Unit) {
    val tier = state.dayTier
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = state.dayTitle,
            color = tier.textColor(),
            fontSize = 28.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(999.dp)).clickable { onNavigate(Routes.DAYS) }.padding(4.dp),
        ) {
            for ((date, dayTier) in state.week) {
                TierDot(tier = dayTier, size = 10.dp, ringed = date == state.today)
            }
        }
    }
    BodyLine(text = state.daySentence, color = tier.sublineColor())
}

@Composable
private fun QuickRow(onNavigate: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PillButton(
            text = "How I feel", onClick = { onNavigate(Routes.FEEL) }, modifier = Modifier.weight(1.15f),
            fontSize = 12.sp, shadowAlpha = 0.07f, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
        )
        PillButton(
            text = "Craving", onClick = { onNavigate(Routes.JOURNAL) }, modifier = Modifier.weight(1f),
            fontSize = 12.sp, shadowAlpha = 0.07f, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
        )
        PillButton(
            text = "Breathe", onClick = { onNavigate(BREATHE_ROUTE) }, modifier = Modifier.weight(1f),
            fontSize = 12.sp, shadowAlpha = 0.07f, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
        )
        HotButton(text = "Talk", onClick = { onNavigate(Routes.CHECKIN) }, modifier = Modifier.weight(0.9f), fontSize = 13.sp)
    }
}

/** The running experiment's plan for today: hot pink on an on day, soft blue on an off day. */
@Composable
private fun ExperimentCard(experiment: TodayExperiment, onNavigate: (String) -> Unit) {
    val on = experiment.condition == Condition.ON
    val text = if (on) {
        "Experiment: on day. ${ExperimentMath.upperFirst(experiment.behavior.trim().trimEnd('.'))}."
    } else {
        "Experiment: off day. Live as usual."
    }
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        color = if (on) HotPink else SoftBlue,
        radius = 24.dp,
        shadowColor = if (on) Tier.BEST.shadowColor() else Tier.LIKELY_HARD.shadowColor(),
        shadowAlpha = 0.2f,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
        gap = 0.dp,
        onClick = { onNavigate(ExperimentRoutes.LIST) },
    ) {
        Text(
            text = text,
            color = if (on) PanelInkOnHot else Ink,
            fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.ExtraBold,
        )
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
    /** A panel that forecasts nothing stays white and shows this link where the tier word would be. */
    val plainLink: Pair<String, () -> Unit>? = null,
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
    val weather = environmentTile(state, onNavigate)
    val sky = skyTile(state, onNavigate)
    val cravings = TileSpec(
        title = "Cravings",
        tier = state.tier(Component.CRAVINGS),
        value = "${state.cravingCount} logged",
        subline = if (state.cravingCount < 14) "a pattern needs about 14" else "enough for a pattern",
        valueSize = 22.sp,
        onClick = { onNavigate(Routes.JOURNAL) },
    )
    val goals = goalsTile(state, onNavigate)
    val rows = listOf(recovery to glucose, bp to hormones, weather to sky, cravings to goals)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((left, right) in rows) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Tile(left, Modifier.weight(1f))
                Tile(right, Modifier.weight(1f))
            }
        }
    }
}

/** The weather panel wears the ENVIRONMENT tier and reads pressure change and daylight. */
private fun environmentTile(state: TodayUiState, onNavigate: (String) -> Unit): TileSpec {
    val weather = state.sky?.weather
    val change = weather?.pressureChange24h
    val daylight = weather?.daylightMin?.let { SkyText.daylight(it) }
    val noPlace = state.sky?.location?.mode == LocationMode.NONE
    return TileSpec(
        title = "Weather",
        tier = state.tier(Component.ENVIRONMENT),
        value = change?.let { "${SkyText.signed(it)} hPa" } ?: "--",
        subline = when {
            change != null && daylight != null -> "pressure in 24 hours, $daylight of daylight"
            change != null -> "pressure in 24 hours"
            daylight != null -> "$daylight of daylight"
            noPlace -> "set a place to read this"
            else -> "pressure and daylight"
        },
        valueSize = 22.sp,
        onClick = { onNavigate(Routes.SKY) },
    )
}

/** The sky panel wears the SKY tier: the moon, and a retrograde when transits are on. */
private fun skyTile(state: TodayUiState, onNavigate: (String) -> Unit): TileSpec {
    val sky = state.sky
    val tier = state.tier(Component.SKY)
    if (sky == null) {
        return TileSpec(
            title = "Sky", tier = tier, value = "--", subline = "moon, weather and transits",
            valueSize = 22.sp, onClick = { onNavigate(Routes.SKY) },
        )
    }
    val moon = sky.moon
    val percent = SkyText.percent(moon.illumination)
    val retro = if (sky.transitsOn) sky.retrogrades else emptyList()
    val lead: Sky.RetrogradeStatus? = retro.firstOrNull { it.stationing } ?: retro.firstOrNull()
    return if (lead != null) {
        val line = SkyText.retrogradeLine(lead)
        val rest = line.removePrefix("${lead.period.planet} retrograde").removePrefix(lead.period.planet).trim().trimStart(',').trim()
        TileSpec(
            title = "Sky", tier = tier,
            value = "${lead.period.planet} retrograde",
            subline = if (rest.isEmpty()) "Moon $percent percent" else "$rest. Moon $percent percent",
            valueSize = 22.sp, onClick = { onNavigate(Routes.SKY) },
        )
    } else {
        TileSpec(
            title = "Sky", tier = tier,
            value = SkyText.capitalized(moon.name.label),
            subline = "$percent percent lit. ${SkyText.nextFull(moon, state.today)}",
            valueSize = 22.sp, onClick = { onNavigate(Routes.SKY) },
        )
    }
}

/** Goals forecast nothing, so the panel stays white: habits done today, with a link to the tab. */
private fun goalsTile(state: TodayUiState, onNavigate: (String) -> Unit): TileSpec {
    val done = state.habitsDone
    val total = state.habitsTotal
    val open = { onNavigate(Routes.GOALS) }
    return when {
        done == null || total == null -> TileSpec(
            title = "Habits", tier = Tier.UNKNOWN, value = "--", subline = "habits done today",
            valueSize = 22.sp, onClick = open, plainLink = "Goals" to open,
        )
        total == 0 -> TileSpec(
            title = "Habits", tier = Tier.UNKNOWN, value = "None yet", subline = "start one in Goals",
            valueSize = 22.sp, onClick = open, plainLink = "Goals" to open,
        )
        else -> TileSpec(
            title = "Habits", tier = Tier.UNKNOWN, value = "$done of $total",
            subline = if (total == 1) "habit done today" else "habits done today",
            valueSize = 22.sp, onClick = open, plainLink = "Goals" to open,
        )
    }
}

@Composable
private fun Tile(spec: TileSpec, modifier: Modifier) {
    val plain = spec.plainLink != null
    val tier = spec.tier
    val main = if (plain) Ink else tier.textColor()
    val sub = if (plain) BodyText else tier.sublineColor()
    val body: @Composable () -> Unit = {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = spec.title, color = if (plain) Muted else main, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            val link = spec.plainLink
            if (link != null) {
                TextLink(text = link.first, onClick = link.second)
            } else {
                Text(text = tier.label, color = main, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = spec.value, color = main, fontSize = spec.valueSize,
                lineHeight = (spec.valueSize.value * 1.1f).sp, fontWeight = FontWeight.Black,
            )
            if (spec.valueSuffix != null) {
                Text(
                    text = spec.valueSuffix, color = if (tier == Tier.BEST) PanelInkOnHot else Plum,
                    fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        Text(text = spec.subline, color = sub, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    }
    val sized = modifier.fillMaxHeight().heightIn(min = 108.dp)
    val padding = PaddingValues(horizontal = 15.dp, vertical = 13.dp)
    when {
        plain -> SoftCard(
            modifier = sized, color = White, radius = 24.dp, shadowAlpha = 0.08f,
            contentPadding = padding, gap = 3.dp, onClick = spec.onClick,
        ) { body() }
        tier == Tier.UNKNOWN -> DashedCard(modifier = sized, contentPadding = padding, onClick = spec.onClick) { body() }
        else -> SoftCard(
            modifier = sized, color = tier.hexColor(), radius = 24.dp,
            shadowColor = tier.shadowColor(), shadowAlpha = 0.2f,
            contentPadding = padding, gap = 3.dp,
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
            modifier = Modifier.fillMaxWidth(), color = tier.hexColor(), radius = 24.dp,
            shadowColor = tier.shadowColor(), shadowAlpha = 0.2f,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp), gap = 0.dp,
            onClick = { onNavigate(Routes.CHECKIN) },
        ) { content() }
    }
}
