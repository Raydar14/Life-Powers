package com.lifepowers.app.assistant

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.lifepowers.app.data.Dates
import com.lifepowers.app.data.personal.AssistantCallEntity
import com.lifepowers.app.data.personal.AssistantDao
import com.lifepowers.app.data.personal.PhotoNoteEntity
import com.lifepowers.app.domain.AssistantCallLog
import com.lifepowers.app.domain.AssistantFeatures
import com.lifepowers.app.domain.AssistantPreview
import com.lifepowers.app.domain.AssistantRepository
import com.lifepowers.app.domain.AssistantResult
import com.lifepowers.app.domain.MetricRepository
import com.lifepowers.app.domain.Photo
import com.lifepowers.app.domain.SettingsRepository
import com.lifepowers.app.domain.TarotPull
import com.lifepowers.core.Metrics
import com.lifepowers.core.Sample
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assistant features behind the master switch. Everything is off until the person turns it on, a
 * key is stored, and they tap Send on the preview. Every call that leaves the phone is logged with
 * the plain words of what was sent.
 */
@Singleton
class AssistantRepositoryImpl @Inject constructor(
    private val store: DataStore<Preferences>,
    private val settings: SettingsRepository,
    private val dao: AssistantDao,
    private val metrics: MetricRepository,
    private val keyStore: AssistantKeyStore,
    private val gateway: ClaudeGateway,
) : AssistantRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val keyPresent = MutableStateFlow(false)

    init {
        scope.launch { keyPresent.value = keyStore.read() != null }
    }

    override val masterOn: Flow<Boolean> = settings.assistantMaster.distinctUntilChanged()

    override val enabled: Flow<Map<String, Boolean>> = store.data
        .map { prefs -> AssistantFeatures.ALL.associateWith { prefs[featureKey(it)] ?: false } }
        .distinctUntilChanged()

    override suspend fun setFeature(feature: String, on: Boolean) {
        if (feature == MASTER) {
            settings.setAssistantMaster(on)
            return
        }
        store.edit { it[featureKey(feature)] = on }
    }

    override val hasKey: Flow<Boolean> = keyPresent.asStateFlow()

    override suspend fun setKey(key: String?) {
        val ok = withContext(Dispatchers.IO) { keyStore.write(key) }
        if (ok) {
            gateway.reset()
            keyPresent.value = !key.isNullOrBlank()
        }
    }

    override fun available(feature: String): Flow<Boolean> = combine(
        masterOn,
        store.data.map { it[featureKey(feature)] ?: false },
        keyPresent,
    ) { master, on, key -> master && on && key }.distinctUntilChanged()

    override val log: Flow<List<AssistantCallLog>> = dao.calls().map { rows ->
        rows.map { AssistantCallLog(it.id, it.feature, it.atUtc, it.sentSummary, it.response, it.ok) }
    }

    // Card

    override fun previewCard(pull: TarotPull, includeNote: Boolean, dayTier: String?): AssistantPreview =
        AssistantPreviews.card(pull, includeNote, dayTier)

    override suspend fun interpretCard(pull: TarotPull, includeNote: Boolean, dayTier: String?): AssistantResult {
        val preview = previewCard(pull, includeNote, dayTier)
        val key = ready(AssistantFeatures.CARD) ?: return notReady()
        val outcome = gateway.send(key, AssistantPrompts.CARD_SYSTEM, AssistantPrompts.cardMessage(pull, includeNote, dayTier))
        val result = when (outcome) {
            is CallOutcome.Text -> AssistantResult.Ok(AssistantReplies.clean(outcome.text))
            CallOutcome.Refused -> AssistantResult.Failed(ClaudeGateway.REFUSED)
            is CallOutcome.Failed -> AssistantResult.Failed(outcome.message)
        }
        record(preview, result)
        return result
    }

    // Photo

    override fun previewPhoto(photo: Photo, sizeBytes: Int, caption: String?): AssistantPreview =
        AssistantPreviews.photo(sizeBytes, caption)

    override suspend fun askAboutPhoto(photo: Photo, jpeg: ByteArray, caption: String?): AssistantResult {
        val key = ready(AssistantFeatures.PHOTO) ?: return notReady()
        // Prepared again here so whatever the caller passed, only an upright, stripped, bounded JPEG leaves.
        val prepared = withContext(Dispatchers.Default) { runCatching { PhotoPrep.prepare(jpeg) }.getOrNull() }
            ?: return AssistantResult.Failed("That photo could not be read.")
        val preview = previewPhoto(photo, prepared.size, caption)
        val outcome = gateway.send(
            apiKey = key,
            system = AssistantPrompts.PHOTO_SYSTEM,
            userText = AssistantPrompts.photoMessage(caption),
            jpeg = prepared,
            schema = AssistantReplies.QUESTIONS_SCHEMA,
        )
        val result = when (outcome) {
            is CallOutcome.Text -> AssistantReplies.parseQuestions(outcome.text)
                ?.let { AssistantResult.Questions(it) }
                ?: AssistantResult.Failed(ClaudeGateway.UNREADABLE)
            CallOutcome.Refused -> AssistantResult.Failed(ClaudeGateway.REFUSED)
            is CallOutcome.Failed -> AssistantResult.Failed(outcome.message)
        }
        record(preview, result)
        if (result is AssistantResult.Questions && photo.id != 0L) {
            guard {
                dao.insertPhotoNote(
                    PhotoNoteEntity(
                        photoId = photo.id,
                        atUtc = System.currentTimeMillis(),
                        questions = PhotoNotes.joinLines(result.questions),
                        answers = "",
                    ),
                )
            }
        }
        return result
    }

    // Check-in ratings

    override fun previewCheckin(transcript: String): AssistantPreview = AssistantPreviews.checkin(transcript)

    override suspend fun rateCheckin(checkinStartUtcMs: Long, zoneOffsetMin: Int, transcript: String): AssistantResult {
        if (transcript.isBlank()) return AssistantResult.Failed("This check-in has no words to read.")
        val preview = previewCheckin(transcript)
        val key = ready(AssistantFeatures.CHECKIN) ?: return notReady()
        val outcome = gateway.send(
            apiKey = key,
            system = AssistantPrompts.CHECKIN_SYSTEM,
            userText = AssistantPrompts.checkinMessage(transcript),
            schema = AssistantReplies.RATINGS_SCHEMA,
        )
        val result = when (outcome) {
            is CallOutcome.Text -> AssistantReplies.parseRatings(outcome.text)
                ?.let { AssistantResult.Ratings(it.energy, it.optimism, it.tension, it.warmth, it.reflection) }
                ?: AssistantResult.Failed(ClaudeGateway.UNREADABLE)
            CallOutcome.Refused -> AssistantResult.Failed(ClaudeGateway.REFUSED)
            is CallOutcome.Failed -> AssistantResult.Failed(outcome.message)
        }
        record(preview, result)
        if (result is AssistantResult.Ratings) {
            guard {
                val samples = listOf(
                    Metrics.ASSIST_ENERGY to result.energy,
                    Metrics.ASSIST_OPTIMISM to result.optimism,
                    Metrics.ASSIST_TENSION to result.tension,
                    Metrics.ASSIST_WARMTH to result.warmth,
                ).map { (metric, value) ->
                    Sample(metric = metric, value = value, startUtcMs = checkinStartUtcMs, zoneOffsetMin = zoneOffsetMin, source = SOURCE)
                }
                metrics.upsertSamples(samples)
                val date = Dates.localDate(checkinStartUtcMs, zoneOffsetMin)
                metrics.rebuildDaily(date, date)
            }
        }
        return result
    }

    // Weekly narrative

    override fun previewNarrative(findings: List<String>, goals: List<String>): AssistantPreview =
        AssistantPreviews.narrative(findings, goals)

    override suspend fun weeklyNarrative(findings: List<String>, goals: List<String>): AssistantResult {
        if (findings.none { it.isNotBlank() } && goals.none { it.isNotBlank() }) {
            return AssistantResult.Failed("There are no findings or goals to write from yet.")
        }
        val preview = previewNarrative(findings, goals)
        val key = ready(AssistantFeatures.NARRATIVE) ?: return notReady()
        val outcome = gateway.send(key, AssistantPrompts.NARRATIVE_SYSTEM, AssistantPrompts.narrativeMessage(findings, goals))
        val result = when (outcome) {
            is CallOutcome.Text -> AssistantResult.Ok(AssistantReplies.clean(outcome.text))
            CallOutcome.Refused -> AssistantResult.Failed(ClaudeGateway.REFUSED)
            is CallOutcome.Failed -> AssistantResult.Failed(outcome.message)
        }
        record(preview, result)
        return result
    }

    // Helpers

    /** The key when this feature may send right now, or null. Nothing is sent and nothing is logged otherwise. */
    private suspend fun ready(feature: String): String? {
        if (!available(feature).first()) return null
        return withContext(Dispatchers.IO) { keyStore.read() }
    }

    private fun notReady(): AssistantResult = AssistantResult.Failed("Turn this assistant feature on in Settings, with your key saved, first.")

    private suspend fun record(preview: AssistantPreview, result: AssistantResult) {
        val (response, ok) = when (result) {
            is AssistantResult.Ok -> result.text to true
            is AssistantResult.Questions -> result.questions.joinToString("\n") to true
            is AssistantResult.Ratings -> (
                "Energy ${AssistantReplies.ratingNumber(result.energy)}, optimism ${AssistantReplies.ratingNumber(result.optimism)}, " +
                    "tension ${AssistantReplies.ratingNumber(result.tension)}, warmth ${AssistantReplies.ratingNumber(result.warmth)}. ${result.reflection}"
                ).trim() to true
            is AssistantResult.Failed -> result.message to false
        }
        guard {
            dao.insertCall(
                AssistantCallEntity(
                    feature = preview.feature,
                    atUtc = System.currentTimeMillis(),
                    sentSummary = AssistantPreviews.sentSummary(preview),
                    response = response,
                    ok = ok,
                ),
            )
        }
    }

    private suspend fun guard(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The answer still reaches the person; a failed local write is not worth losing it over.
        }
    }

    companion object {
        const val MASTER = "master"
        const val SOURCE = "assistant"

        fun featureKey(feature: String) = booleanPreferencesKey("assistant_$feature")
    }
}
