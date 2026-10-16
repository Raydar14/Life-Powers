# Voice pipeline: gaps and requests

Written by the voice engineer for the data and UI engineers. Everything below is worked around in
`com.lifepowers.app.voice`; none of it blocks a build.

## SettingsRepository

- `allowSharedMic: Flow<Boolean>` (default true) plus `setAllowSharedMic`. The design decision is to run
  `SpeechRecognizer` for the transcript and start `AudioRecord` 300 ms later for acoustic features. Some
  devices refuse or silence the second client. Until the setting exists the flag lives on
  `CheckinSessionController.allowSharedMic` (a plain `@Volatile var`, default true); the UI can expose it
  from a developer or settings screen and the data engineer can move it into DataStore later.
- `keepAudio` is read as `settings.keepAudio.first()` once per check-in. The WAV is written unencrypted to
  `filesDir/audio/checkin_<startUtcMs>.wav`. DESIGN.md 5.6 says kept audio should be encrypted; wrapping
  the file with `androidx.security.crypto.EncryptedFile` (already a dependency) is a small follow up once
  the data layer has a shared master key helper. No deletion policy exists yet either.

## EventKinds

- `screens_in_bed` is in the docs/DATA_MODEL.md event catalog but not in `EventKinds`. BehaviorMentions
  uses `BehaviorMentions.SCREENS_IN_BED = "screens_in_bed"` for "on my phone late" mentions. Please add
  `const val SCREENS_IN_BED = "screens_in_bed"` to `EventKinds` and the voice package will switch to it.

## CheckinRepository

- `recent(limit)` is a `Flow`; the controller takes `.first()` of `recent(60)` as the baseline. If the
  Room query ever emits an empty list before the real one, the baseline check falls back to "no indices"
  for that check-in, which is safe but loses a point. A `suspend fun recentOnce(limit): List<CheckinRecord>`
  would remove the ambiguity.
- Baseline stratification by hormonal phase (DESIGN.md 5.2) is not done yet; it needs
  `ProfileRepository.phaseFor` joined to each record's local date. Left for a later pass.

## Metrics

- The controller writes `words.*` and `voice.*` samples through `MetricRepository.upsertSamples` with
  source `voice_checkin`. It does not call `rebuildDaily`; the data layer should fold that into
  `upsertSamples` or run it on its own schedule, as it does for connector samples.
- The words versus voice gap rule (`WordsVoiceGap.fires`) needs z scores of `words.valence` and the two
  voice indices across the last three check-ins. That belongs to whoever computes Today cards; the voice
  package only stores the per day values.

## Device behavior to verify on hardware

- Whether `AudioRecord` gets real audio while the recognizer holds the mic. If it gets silence, the
  processor marks the record `no_audio` (peak under 0.001) and features stay null, so nothing breaks, but
  acoustic indices will never appear on that device. In that case the fallback is to set
  `allowSharedMic = false` and accept transcript only, or to switch to a recognizer that accepts PCM.
- Google's recognizer finalizes segments at pauses; `SpeechToText` restarts listening after each final
  segment and concatenates. Expect a short dropped word at each restart boundary.
