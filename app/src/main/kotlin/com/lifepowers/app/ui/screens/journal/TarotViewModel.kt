package com.lifepowers.app.ui.screens.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.ForecastRepository
import com.lifepowers.app.domain.JournalRepository
import com.lifepowers.app.domain.ProfileRepository
import com.lifepowers.app.domain.TarotPull
import com.lifepowers.core.Tarot
import com.lifepowers.core.Tier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** One card in the mirror: its counts, and the day tier of each day it turned up, oldest first. */
data class MirrorCard(val count: Tarot.CardCount, val days: List<Pair<LocalDate, Tier>>)

data class TarotUiState(
    val today: LocalDate = LocalDate.now(),
    /** Pulls from the last 365 days, newest first. */
    val pulls: List<TarotPull> = emptyList(),
    /** Cards that turned up more than once, most often first. */
    val mirror: List<MirrorCard> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class TarotViewModel @Inject constructor(
    journalRepository: JournalRepository,
    private val profileRepository: ProfileRepository,
    forecastRepository: ForecastRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val from: LocalDate = today.minusDays(364)

    /** Phase words per date, looked up once. Only touched from the combine below, one emission at a time. */
    private val phaseCache = HashMap<LocalDate, String?>()

    val state: StateFlow<TarotUiState> = combine(
        journalRepository.pulls(from, today).catch { emit(emptyList()) },
        forecastRepository.dayTiers(from, today).catch { emit(emptyMap()) },
    ) { pulls, tiers ->
        val sorted = pulls.sortedWith(compareByDescending<TarotPull> { it.localDate }.thenByDescending { it.createdUtcMs })
        val phases = HashMap<LocalDate, String?>()
        for (date in sorted.map { it.localDate }.distinct()) phases[date] = phaseFor(date)
        val counts = Tarot.counts(sorted.map { Tarot.Pull(it.card, it.reversed, phases[it.localDate], tiers[it.localDate]) })
        val mirror = counts
            .filter { it.count >= 2 }
            .take(MIRROR_CARDS)
            .map { c ->
                val days = sorted
                    .filter { it.card == c.card }
                    .map { it.localDate }
                    .distinct()
                    .sorted()
                    .map { d -> d to (tiers[d] ?: Tier.UNKNOWN) }
                MirrorCard(c, days)
            }
        TarotUiState(today = today, pulls = sorted, mirror = mirror, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TarotUiState())

    private suspend fun phaseFor(date: LocalDate): String? {
        if (phaseCache.containsKey(date)) return phaseCache[date]
        val words = runCatching { profileRepository.phaseFor(date) }.getOrNull()?.let { phaseWords(it.phase) }
        phaseCache[date] = words
        return words
    }

    private companion object {
        const val MIRROR_CARDS = 6
    }
}
