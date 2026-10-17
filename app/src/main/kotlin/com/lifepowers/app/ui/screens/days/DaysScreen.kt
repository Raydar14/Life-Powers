package com.lifepowers.app.ui.screens.days

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SectionLabel
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.TierDot
import com.lifepowers.app.ui.theme.DeepPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.core.Metrics
import com.lifepowers.core.Tier

@Composable
fun DaysScreen(onBack: () -> Unit, viewModel: DaysViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(CloudPresets.days)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader(kicker = Fmt.month(state.today), title = "Your days", titleSize = 28.sp) {
                TextLink(text = "Back", onClick = onBack)
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), gap = 10.dp) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (letter in listOf("M", "T", "W", "T", "F", "S", "S")) {
                        Text(
                            text = letter, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                        )
                    }
                }
                val weeks = state.days.chunked(7)
                for (week in weeks) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (date in week) {
                            val future = date.isAfter(state.today)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .clickable(enabled = !future) { viewModel.select(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                TierDot(
                                    tier = state.tiers[date] ?: Tier.UNKNOWN,
                                    size = 30.dp,
                                    ringed = date == state.today || date == state.selected,
                                    hollow = future,
                                    ringGap = if (date == state.selected && date != state.today) 1.dp else 3.dp,
                                )
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (state.selected == state.today) "Today" else Fmt.dateKicker(state.selected),
                    color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Black,
                )
                val tier = state.tiers[state.selected] ?: Tier.UNKNOWN
                Text(text = tier.label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                SectionLabel("What the day measured")
                if (state.selectedPoints.isEmpty()) {
                    MutedLine(text = if (state.loaded) "Nothing synced for this day yet." else "Loading.")
                }
                val groups = state.selectedPoints.groupBy { it.metric }
                for ((metric, points) in groups) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = Fmt.metricName(metric), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        val unit = Metrics.spec(metric).unit
                        for (p in points) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = Fmt.aggWord(p.agg), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = (if (p.value % 1.0 == 0.0 || p.value > 50) Fmt.num(p.value) else Fmt.num(p.value, 1)) + if (unit.isNotEmpty()) " $unit" else "",
                                        color = Ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                                    )
                                    val z = p.z30
                                    Text(
                                        text = Fmt.zWord(z),
                                        color = if (z != null && (z > 1.5 || z < -1.5)) DeepPink else Muted,
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
                SectionLabel("Moments")
                if (state.selectedEvents.isEmpty()) MutedLine(text = "No moments logged this day.")
                for (e in state.selectedEvents) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = Fmt.kindName(e.kind), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                            val q = e.quantity
                            if (q != null) Text(text = Fmt.num(q), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(text = Fmt.timeAt(e.startUtcMs, e.zoneOffsetMin), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
