package com.lifepowers.app.ui.screens.breathe

import android.net.Uri

/** Routes for breathing with biofeedback. Every navigation string in this package comes from here. */
object BreatheRoutes {
    const val HOME = "breathe"
    const val EDIT = "breathe/edit/{patternId}?preset={preset}"
    const val SESSION = "breathe/session"
    const val SUMMARY = "breathe/summary/{sessionId}"
    const val HISTORY = "breathe/history"

    /** The editor for a saved pattern, a new one (id 0), or a new one prefilled from a preset by name. */
    fun edit(id: Long, preset: String? = null): String =
        if (preset == null) "breathe/edit/$id" else "breathe/edit/$id?preset=${Uri.encode(preset)}"

    fun summary(id: Long): String = "breathe/summary/$id"
}
