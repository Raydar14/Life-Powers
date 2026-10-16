package com.lifepowers.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.White

enum class Tab(val route: String, val label: String) {
    TODAY("today", "Today"),
    JOURNAL("journal", "Journal"),
    GOALS("goals", "Goals"),
    SKY("sky", "Sky"),
    PATTERNS("patterns", "Patterns"),
}

/** The floating white pill with five word tabs. The active tab is a light pink pill with ink text. */
@Composable
fun BottomBar(active: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .softShadow(shape, Ink, 0.2f, 14.dp)
            .clip(shape)
            .background(White)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (tab in Tab.values()) {
            val isActive = tab == active
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (isActive) LightPink else White)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    color = if (isActive) Ink else Muted,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
