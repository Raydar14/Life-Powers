package com.lifepowers.app.ui.screens.goals

import java.net.URLEncoder

/** Routes for goals, the habit tracker, and the timer. Every navigation string comes from here. */
object GoalRoutes {
    const val GOAL_EDIT = "goal/edit/{goalId}?name={name}"
    const val GOAL = "goal/{goalId}"
    const val TIMER = "timer?goalId={goalId}"

    fun edit(id: Long, name: String? = null): String =
        if (name == null) "goal/edit/$id" else "goal/edit/$id?name=${encode(name)}"

    fun goal(id: Long): String = "goal/$id"

    fun timer(goalId: Long?): String = if (goalId == null) "timer" else "timer?goalId=$goalId"

    /** URLEncoder writes spaces as plus signs, which navigation does not turn back into spaces. */
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
