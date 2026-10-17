package com.lifepowers.app.ui.screens.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.domain.LocationMode
import com.lifepowers.app.domain.WorldEvent
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CardTitle
import com.lifepowers.app.ui.components.Chip
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Dot
import com.lifepowers.app.ui.components.MoonDisc
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.TierCard
import com.lifepowers.app.ui.navigation.Routes
import com.lifepowers.app.ui.theme.BodyText
import com.lifepowers.app.ui.theme.Ground
import com.lifepowers.app.ui.theme.HairLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.SoftBlue
import com.lifepowers.app.ui.theme.White
import com.lifepowers.app.ui.theme.hexColor
import com.lifepowers.app.ui.theme.sublineColor
import com.lifepowers.app.ui.theme.textColor
import com.lifepowers.core.AuroraChance
import com.lifepowers.core.Tier
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SkyScreen(onNavigate: (String) -> Unit, viewModel: SkyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(CloudPresets.sky)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val noPlace = state.location.mode == LocationMode.NONE
            ScreenHeader(title = "Sky and earth", subline = SkyText.locationLine(state.location), titleSize = 28.sp) {
                TextLink(text = if (noPlace) "Choose a place" else "Location", onClick = { onNavigate(Routes.SETTINGS) })
            }
            SkyPanel(state, onNavigate)
            WeatherTiles(state)
            OutsideCard(state)
            GroundCard(state)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MutedLine(
                    text = "Moon and planets are computed on your phone. Weather from Open-Meteo, quakes from USGS, volcano reports from the Smithsonian, the K index from NOAA.",
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                TextLink(text = if (refreshing) "Refreshing" else "Refresh", onClick = { viewModel.refresh() })
            }
        }
    }
}

@Composable
private fun SkyPanel(state: SkyUiState, onNavigate: (String) -> Unit) {
    val sky = state.sky
    val tier = sky?.tier ?: Tier.UNKNOWN
    TierCard(
        tier = tier,
        modifier = Modifier.fillMaxWidth(),
        radius = 28.dp,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        gap = 10.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (sky?.transitsOn == true) "Sky and transits" else "The sky tonight",
                color = tier.textColor(), fontSize = 16.sp, fontWeight = FontWeight.Black,
            )
            Text(text = tier.label, color = tier.textColor(), fontSize = 12.sp, fontWeight = FontWeight.Black)
        }

        if (sky == null) {
            Text(text = "Reading the sky", color = tier.textColor(), fontSize = 18.sp, fontWeight = FontWeight.Black)
            BodyLine(text = "The moon and planets are worked out on your phone. This fills in a moment.", color = tier.sublineColor())
            return@TierCard
        }

        val moon = sky.moon
        val retro = if (sky.transitsOn) sky.retrogrades else emptyList()
        val lead = retro.firstOrNull { it.stationing } ?: retro.firstOrNull()
        val headline = lead?.let { SkyText.retrogradeLine(it) } ?: SkyText.capitalized(moon.name.label)
        val subline = if (lead != null) {
            "Moon ${moon.name.label}, ${SkyText.percent(moon.illumination)} percent. ${SkyText.nextFull(moon, state.today)}."
        } else {
            "${SkyText.percent(moon.illumination)} percent lit. ${SkyText.nextFull(moon, state.today)}."
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            MoonDisc(
                illumination = moon.illumination,
                waxing = moon.phaseAngle < 180.0,
                shadowColor = if (tier == Tier.UNKNOWN) Lavender else tier.hexColor(),
                litColor = if (tier == Tier.UNKNOWN) White else Ground,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = headline, color = tier.textColor(), fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black)
                Text(text = subline, color = tier.sublineColor(), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        val lines = mutableListOf<String>()
        val rise = moon.riseUtcMs
        val set = moon.setUtcMs
        if (rise != null && set != null) lines.add("Moonrise ${SkyText.time(rise)}, moonset ${SkyText.time(set)}.")
        if (sky.transitsOn) {
            lines.add("The moon is in ${moon.sign}.")
            for (r in retro) if (r !== lead) lines.add(SkyText.retrogradeLine(r) + ".")
        }
        for (line in lines) {
            Text(text = line, color = tier.textColor(), fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (sky.transitsOn) "Transits are on." else "Signs and retrogrades are off.",
                color = tier.sublineColor(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextLink(text = "Settings", onClick = { onNavigate(Routes.SETTINGS) })
        }
    }
}

@Composable
private fun WeatherTiles(state: SkyUiState) {
    val weather = state.sky?.weather
    val noPlace = state.location.mode == LocationMode.NONE
    val missing = if (noPlace) "needs a place" else "no reading yet"
    val change = weather?.pressureChange24h
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeatherTile(
                title = "Temperature", tier = null,
                value = SkyText.degrees(weather?.tempMax),
                detail = weather?.tempMin?.let { "low of ${SkyText.degrees(it)}" } ?: missing,
                modifier = Modifier.weight(1f),
            )
            WeatherTile(
                title = "Pressure", tier = state.environmentTier,
                value = change?.let { SkyText.signed(it) } ?: "--",
                detail = if (change != null) "hPa in 24 hours" else missing,
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeatherTile(
                title = "Air", tier = null,
                value = SkyText.airWord(weather?.aqiMax),
                detail = if (weather?.aqiMax == null && weather?.pm25Mean == null) missing else SkyText.airDetail(weather),
                modifier = Modifier.weight(1f),
            )
            WeatherTile(
                title = "Daylight", tier = null,
                value = SkyText.daylight(weather?.daylightMin),
                detail = if (weather?.daylightMin != null) "of daylight today" else missing,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A weather tile. White when it forecasts nothing ([tier] null); otherwise it wears the tier. */
@Composable
private fun WeatherTile(title: String, tier: Tier?, value: String, detail: String, modifier: Modifier) {
    val main = tier?.textColor() ?: Ink
    val sub = tier?.sublineColor() ?: Muted
    val content: @Composable ColumnScope.() -> Unit = {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = title, color = if (tier == null) Muted else main, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            if (tier != null) Text(text = tier.label, color = main, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        Text(text = value, color = main, fontSize = 24.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(text = detail, color = sub, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
    }
    val padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    if (tier == null) {
        SoftCard(modifier = modifier.height(98.dp), radius = 22.dp, shadowAlpha = 0.07f, contentPadding = padding, gap = 2.dp, content = content)
    } else {
        TierCard(tier = tier, modifier = modifier.height(98.dp), radius = 22.dp, contentPadding = padding, gap = 2.dp, elevation = 10.dp, content = content)
    }
}

private data class SkyItem(val color: Color, val title: String, val body: String)

private fun outsideItems(state: SkyUiState): List<SkyItem> {
    val sky = state.sky ?: return emptyList()
    val out = mutableListOf<SkyItem>()
    sky.showers
        .filter { it.visibleHere != false && !it.peakDate.isBefore(state.today) }
        .take(2)
        .forEach { out.add(SkyItem(HotPink, SkyText.showerTitle(it, state.today), SkyText.showerBody(it))) }
    sky.pairings.take(3).forEach { out.add(SkyItem(LightPink, SkyText.pairingTitle(it), SkyText.pairingBody(it))) }
    sky.planets.take(4).forEach { out.add(SkyItem(LightPink, SkyText.planetTitle(it), SkyText.planetBody(it))) }
    sky.eclipses
        .filter { it.visibleHere == true }
        .take(2)
        .forEach { out.add(SkyItem(Lavender, SkyText.eclipseTitle(it), SkyText.eclipseBody(it))) }
    sky.aurora?.let { chance ->
        val color = when (chance) {
            AuroraChance.LIKELY -> HotPink
            AuroraChance.POSSIBLE -> LightPink
            AuroraChance.UNLIKELY -> SoftBlue
        }
        out.add(SkyItem(color, SkyText.auroraTitle(chance), SkyText.auroraBody(sky.kpNow, sky.kpTonightMax, sky.kpNeeded, state.settings.notifyAurora)))
    }
    return out
}

@Composable
private fun OutsideCard(state: SkyUiState) {
    val items = outsideItems(state)
    SoftCard(modifier = Modifier.fillMaxWidth(), shadowAlpha = 0.08f, gap = 12.dp) {
        CardTitle("Worth stepping outside for")
        if (items.isEmpty()) {
            MutedLine(
                text = if (state.sky == null) "Working out tonight's sky." else "Nothing stands out in the next while. A clear night is still worth a look.",
                fontSize = 13.sp,
            )
        }
        for (item in items) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Dot(color = item.color, size = 12.dp, modifier = Modifier.padding(top = 4.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = item.title, color = Ink, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black)
                    Text(text = item.body, color = BodyText, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        if (state.location.mode == LocationMode.NONE) {
            MutedLine(text = "Showers and the moon work anywhere. Set a place to see planets, local eclipses and the aurora.")
        }
    }
}

@Composable
private fun GroundCard(state: SkyUiState) {
    val radius = state.settings.groundRadiusKm.roundToInt()
    SoftCard(modifier = Modifier.fillMaxWidth(), shadowAlpha = 0.08f, gap = 10.dp) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CardTitle("The ground, Ring of Fire", modifier = Modifier.weight(1f))
            Chip(text = "within $radius km", fontSize = 11.sp)
        }
        if (state.ground.isEmpty()) {
            MutedLine(text = "Quiet this week, as far as the feeds know.", fontSize = 13.sp)
        }
        for (event in state.ground.take(8)) GroundRow(event, state)
        MutedLine(
            text = if (state.location.mode == LocationMode.NONE) {
                "Magnitude 4.5 and up on the Ring of Fire. Set a place to see distances and anything near you."
            } else {
                "Magnitude 4.5 and up on the Ring of Fire, and 2.5 and up within $radius km of you."
            },
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun GroundRow(event: WorldEvent, state: SkyUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = event.title, color = Ink, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black)
            val detail = SkyText.groundDetail(event, state.today)
            if (detail.isNotEmpty()) Text(text = detail, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        MagnitudeCircle(event.magnitude)
    }
}

/** A calm circle sized to the reading: pale for small or unknown, soft blue, then lavender for large. */
@Composable
private fun MagnitudeCircle(magnitude: Double?) {
    val fill = when {
        magnitude == null || magnitude < 4.5 -> Ground
        magnitude < 6.0 -> SoftBlue
        else -> Lavender
    }
    var m = Modifier.size(40.dp).clip(CircleShape).background(fill)
    if (fill == Ground) m = m.border(2.dp, HairLine, CircleShape)
    Box(modifier = m, contentAlignment = Alignment.Center) {
        if (magnitude != null) {
            Text(text = String.format(Locale.getDefault(), "%.1f", magnitude), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}
