package com.lifepowers.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.lifepowers.core.Tier

/** The five palette colors and the derived tones from docs/DESIGN.md section 7. */
val Ground = Color(0xFFFFFFF4)
val Ink = Color(0xFF3B2340)
val Muted = Color(0xFF7A6680)
val DeepPink = Color(0xFFB8206F)
val HotPink = Color(0xFFE6389A)
val LightPink = Color(0xFFFFB3DD)
val Lavender = Color(0xFFCBB2ED)
val SoftBlue = Color(0xFFB1C6E1)
val White = Color(0xFFFFFFFF)

/** Text tones used on tinted panels. */
val PanelInkOnHot = Color(0xFF220C28)
val BodyText = Color(0xFF4F3454)
val Plum = Color(0xFF7A3A6A)
val DeepLavender = Color(0xFF5A3E78)
val HairLine = Color(0xFFEFE6F4)
val DeepLavenderLine = Color(0xFF8E6FC4)

fun Tier.color(): Color = when (this) {
    Tier.BEST -> HotPink
    Tier.GOOD -> LightPink
    Tier.NOT_GREAT -> Lavender
    Tier.LIKELY_HARD -> SoftBlue
    Tier.UNKNOWN -> Ground
}

/** The tier's own hex from core, parsed. Same values as [color]. */
fun Tier.hexColor(): Color = parseHex(hex)

/** "#RRGGBB" or "#AARRGGBB" to a Color. Falls back to the ground on anything else. */
fun parseHex(hex: String): Color {
    val digits = hex.removePrefix("#")
    val argb = when (digits.length) {
        6 -> digits.toLongOrNull(16)?.let { it or 0xFF000000L }
        8 -> digits.toLongOrNull(16)
        else -> null
    } ?: return Ground
    return Color(argb.toInt())
}

/** Main text on a panel wearing this tier. */
fun Tier.textColor(): Color = when (this) {
    Tier.BEST -> PanelInkOnHot
    Tier.UNKNOWN -> Muted
    else -> Ink
}

/** Secondary text on a panel wearing this tier. */
fun Tier.sublineColor(): Color = when (this) {
    Tier.BEST -> PanelInkOnHot
    Tier.UNKNOWN -> Muted
    else -> BodyText
}

/** Shadow tone under a panel wearing this tier. */
fun Tier.shadowColor(): Color = when (this) {
    Tier.BEST, Tier.GOOD -> HotPink
    Tier.NOT_GREAT -> DeepLavenderLine
    Tier.LIKELY_HARD -> Color(0xFF6D8CB4)
    Tier.UNKNOWN -> Ink
}
