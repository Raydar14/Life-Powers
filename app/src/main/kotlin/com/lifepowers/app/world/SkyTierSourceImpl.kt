package com.lifepowers.app.world

import com.lifepowers.app.domain.SkyTierSource
import com.lifepowers.core.Tier
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The sky panel's tier, read by the forecast. Shares [SkyComputer] with the Sky screen so both use
 * the same rule. Depends on nothing in the forecast, so there is no injection cycle.
 */
@Singleton
class SkyTierSourceImpl @Inject constructor(private val sky: SkyComputer) : SkyTierSource {
    override suspend fun tierFor(date: LocalDate): Pair<Tier, List<String>> {
        val today = sky.compute(date)
        return today.tier to today.tierReasons
    }
}
