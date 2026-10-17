package com.lifepowers.app.ui.screens.experiments

import android.net.Uri

/** Routes for experiments. Every navigation string for them comes from here. */
object ExperimentRoutes {
    const val LIST = "experiments"
    const val NEW = "experiment/new?behavior={behavior}&outcome={outcome}"
    const val DETAIL = "experiment/{id}"

    /** Null parameters are left out, so navigation fills in their null defaults. */
    fun new(behavior: String? = null, outcome: String? = null): String {
        val params = buildList {
            if (!behavior.isNullOrBlank()) add("behavior=${Uri.encode(behavior)}")
            if (!outcome.isNullOrBlank()) add("outcome=${Uri.encode(outcome)}")
        }
        return if (params.isEmpty()) "experiment/new" else "experiment/new?" + params.joinToString("&")
    }

    fun detail(id: Long): String = "experiment/$id"
}
