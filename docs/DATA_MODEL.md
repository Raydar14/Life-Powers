# Canonical data model

SQLite via Room with SQLCipher. All timestamps are stored as UTC epoch milliseconds plus the local
zone offset in minutes at the time of recording. All values are stored in a single canonical unit per
metric and converted at the UI layer.

## Tables

```sql
-- Exactly what the vendor sent. Never updated, never deleted except by full reset.
CREATE TABLE raw_sample (
    id              INTEGER PRIMARY KEY,
    source          TEXT NOT NULL,          -- 'oura_api', 'health_connect', 'dexcom_api', 'inito_pdf', 'usage_stats', 'manual', ...
    source_type     TEXT NOT NULL,          -- vendor's own record type name
    source_id       TEXT,                   -- vendor's record id when it has one
    payload         TEXT NOT NULL,          -- JSON
    received_at     INTEGER NOT NULL,
    UNIQUE (source, source_type, source_id)
);

-- One row per measurement after normalization and dedupe.
CREATE TABLE metric_sample (
    id              INTEGER PRIMARY KEY,
    metric          TEXT NOT NULL,          -- see metric catalog below
    value           REAL NOT NULL,
    start_utc       INTEGER NOT NULL,
    end_utc         INTEGER,                -- null for instantaneous readings
    zone_offset_min INTEGER NOT NULL,
    source          TEXT NOT NULL,
    raw_id          INTEGER REFERENCES raw_sample(id),
    is_canonical    INTEGER NOT NULL DEFAULT 1,   -- 0 when a higher priority source supplied the same measurement
    UNIQUE (metric, start_utc, source)
);
CREATE INDEX idx_metric_time ON metric_sample (metric, start_utc) WHERE is_canonical = 1;

-- Behaviors and context. The causes side of the analysis.
CREATE TABLE behavior_event (
    id              INTEGER PRIMARY KEY,
    kind            TEXT NOT NULL,          -- see event catalog below
    start_utc       INTEGER NOT NULL,
    end_utc         INTEGER,
    zone_offset_min INTEGER NOT NULL,
    quantity        REAL,                   -- drinks, mg caffeine, minutes, etc.
    unit            TEXT,
    note            TEXT,
    source          TEXT NOT NULL,          -- 'manual', 'oura_tag', 'dexcom_event', 'usage_stats_derived'
    raw_id          INTEGER REFERENCES raw_sample(id)
);
CREATE INDEX idx_event_time ON behavior_event (kind, start_utc);

-- One row per metric per local day. Rebuilt nightly for the trailing 7 days and on demand.
CREATE TABLE daily_metric (
    local_date      TEXT NOT NULL,          -- 'YYYY-MM-DD'
    metric          TEXT NOT NULL,
    agg             TEXT NOT NULL,          -- 'mean', 'min', 'max', 'sum', 'last', 'count', 'tir_70_140', 'cv'
    value           REAL NOT NULL,
    n               INTEGER NOT NULL,       -- samples contributing
    z30             REAL,                   -- z score against trailing 30 day window, filled by baseline job
    PRIMARY KEY (local_date, metric, agg)
);

-- One row per local day. Filled by the phase labeler.
CREATE TABLE cycle_day (
    local_date      TEXT PRIMARY KEY,
    cycle_id        INTEGER NOT NULL,
    cycle_day       INTEGER NOT NULL,       -- 1 = first day of bleeding
    phase           TEXT NOT NULL,          -- natural cycle: 'menstrual', 'follicular', 'ovulatory', 'luteal', 'unknown'. pack rhythm: 'active_1', 'active_2', 'active_3', 'hormone_free'. dosing rhythm: 'dose_d0_2', 'dose_d3_6', 'dose_d7_plus'. gestational: 'week_N'. no cycle: table empty.
    confidence      REAL NOT NULL,          -- 0 to 1
    basis           TEXT NOT NULL,          -- 'hormone', 'temperature', 'symptom', 'calendar', 'pack_calendar', 'dose_log'
    hormonal_model  TEXT NOT NULL           -- the model in force on that date
);

-- Output of the correlation engine. Rebuilt nightly.
CREATE TABLE insight (
    id              INTEGER PRIMARY KEY,
    input_key       TEXT NOT NULL,          -- metric or event kind
    outcome_metric  TEXT NOT NULL,
    outcome_agg     TEXT NOT NULL,
    lag_days        INTEGER NOT NULL,
    stratum         TEXT NOT NULL,          -- 'all', 'phase:luteal', 'weekday', ...
    n_pairs         INTEGER NOT NULL,
    rho             REAL,                   -- Spearman
    effect_z        REAL,                   -- median outcome z with input present minus without
    ci_low          REAL,
    ci_high         REAL,
    q_value         REAL,                   -- FDR adjusted
    computed_at     INTEGER NOT NULL,
    status          TEXT NOT NULL DEFAULT 'new',   -- 'new', 'seen', 'pinned', 'dismissed'
    UNIQUE (input_key, outcome_metric, outcome_agg, lag_days, stratum)
);

-- Self experiments.
CREATE TABLE experiment (
    id              INTEGER PRIMARY KEY,
    name            TEXT NOT NULL,
    behavior_kind   TEXT NOT NULL,
    outcome_metric  TEXT NOT NULL,
    outcome_agg     TEXT NOT NULL,
    block_days      INTEGER NOT NULL DEFAULT 3,
    target_days     INTEGER NOT NULL DEFAULT 28,
    started_on      TEXT NOT NULL,
    ended_on        TEXT,
    result_json     TEXT
);
CREATE TABLE experiment_day (
    experiment_id   INTEGER REFERENCES experiment(id),
    local_date      TEXT NOT NULL,
    condition       TEXT NOT NULL,          -- 'on', 'off'
    adhered         INTEGER,                -- null until the user confirms
    PRIMARY KEY (experiment_id, local_date)
);

-- The person's hormonal and demographic context. One current row.
CREATE TABLE user_profile (
    id                  INTEGER PRIMARY KEY CHECK (id = 1),
    date_of_birth       TEXT,                   -- 'YYYY-MM-DD'
    sex_at_birth        TEXT,                   -- 'female', 'male', 'intersex', 'prefer_not_to_say'
    gender_identity     TEXT,                   -- free text or chosen label, optional
    menstrual_status    TEXT,                   -- 'regular', 'irregular', 'perimenopause', 'postmenopause', 'amenorrhea', 'pregnant', 'postpartum', 'not_applicable'
    cycle_length_days   INTEGER,
    cycle_variability   INTEGER,                -- typical spread in days
    last_period_start   TEXT,
    contraception       TEXT,                   -- 'none', 'combined_pill', 'progestin_pill', 'hormonal_iud', 'copper_iud', 'implant', 'injection', 'ring', 'patch', 'other'
    hrt                 TEXT,                   -- null or description
    gah_therapy         TEXT,                   -- gender affirming hormones: null or 'estradiol', 'testosterone', 'blockers', combination
    due_or_delivery     TEXT,                   -- for pregnant or postpartum
    hormonal_model      TEXT NOT NULL,          -- derived by the selector: 'natural_cycle', 'natural_cycle_low_trust', 'pack_rhythm', 'suppressed_uncertain', 'no_cycle', 'dosing_rhythm', 'gestational'
    updated_at          INTEGER NOT NULL
);

-- Dated history of every profile change, so analysis splits at the right day.
CREATE TABLE profile_change (
    id              INTEGER PRIMARY KEY,
    field           TEXT NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    effective_date  TEXT NOT NULL,              -- the date the change took effect in the body, not when it was typed
    note            TEXT
);

-- Medications and hormone doses are behavior_event rows of kind 'medication' or 'hormone_dose'.

-- One row per voice check-in.
CREATE TABLE voice_checkin (
    id              INTEGER PRIMARY KEY,
    start_utc       INTEGER NOT NULL,
    zone_offset_min INTEGER NOT NULL,
    duration_s      REAL NOT NULL,
    prompt_id       TEXT NOT NULL,
    transcript      TEXT,
    audio_path      TEXT,                       -- null once deleted (the default)
    acoustic_json   TEXT NOT NULL,              -- the 88 feature vector plus signal quality fields
    words_json      TEXT,                       -- language marker rates and any cloud ratings
    quality_ok      INTEGER NOT NULL DEFAULT 1, -- 0 excludes from baselines
    quality_note    TEXT,                       -- 'noise', 'too_short', 'illness_logged'
    crisis_flag     INTEGER NOT NULL DEFAULT 0  -- set when crisis language detected; recording not scored
);
CREATE INDEX idx_checkin_time ON voice_checkin (start_utc);

-- Sync bookkeeping.
CREATE TABLE sync_cursor (
    source          TEXT NOT NULL,
    stream          TEXT NOT NULL,
    cursor          TEXT,
    last_success_at INTEGER,
    last_error      TEXT,
    PRIMARY KEY (source, stream)
);
```

## Metric catalog

Canonical unit in brackets. Day assignment rule tells the rollup which local day a sample belongs to.

| Metric | Unit | Day rule | Daily aggs |
|---|---|---|---|
| sleep.total_min | min | night ending on date | last |
| sleep.deep_min, sleep.rem_min, sleep.light_min, sleep.awake_min | min | night ending | last |
| sleep.onset_latency_min | min | night ending | last |
| sleep.bedtime_utc, sleep.wake_utc | epoch ms | night ending | last |
| sleep.efficiency | ratio | night ending | last |
| hrv.night_avg | ms (rMSSD) | night ending | last |
| hr.resting | bpm | night ending | last |
| hr.sample | bpm | calendar | mean, min, max |
| resp.rate_night | breaths per min | night ending | last |
| temp.deviation | degrees C | night ending | last |
| spo2.night_avg | percent | night ending | last |
| score.readiness, score.sleep, score.activity | 0 to 100 | calendar | last |
| stress.day_high_min, stress.recovery_min | min | calendar | last |
| activity.steps | count | calendar | sum |
| activity.active_kcal | kcal | calendar | sum |
| workout.session | min | calendar | sum, count |
| workout.last_end_utc | epoch ms | calendar | last |
| glucose.sample | mg/dL | calendar | mean, min, max, cv, tir_70_140 |
| glucose.fasting | mg/dL | calendar | last (mean of 04:00 to 06:00 local) |
| glucose.overnight_mean | mg/dL | night ending | last (00:00 to 06:00 local) |
| glucose.peak_post_meal | mg/dL | calendar | max (needs meal event within 3 h) |
| weight | kg | calendar | mean |
| body.fat_pct, body.muscle_kg, body.water_pct, body.bone_kg | pct or kg | calendar | mean |
| bp.systolic, bp.diastolic, bp.pulse | mmHg, bpm | calendar | mean, count, plus a 'morning' variant restricted to 05:00 to 10:00 |
| hormone.lh, hormone.e3g, hormone.pdg, hormone.fsh | vendor units (mIU/mL, ng/mL, ug/mL, mIU/mL) | calendar | last |
| screen.total_min | min | calendar | sum |
| screen.after_21_min | min | calendar | sum |
| screen.last_use_utc | epoch ms | night ending | last |
| screen.first_use_utc | epoch ms | calendar | last |
| screen.by_category.* | min | calendar | sum |
| voice.energy_index | z | calendar | last |
| voice.flatness_index | z | calendar | last |
| voice.tension_index | z | calendar | last |
| voice.arousal, voice.valence | 0 to 1 | calendar | last |
| voice.f0_mean, voice.f0_range, voice.loudness_mean, voice.speech_rate, voice.pause_mean_s | Hz, Hz, dB, syllables per s, s | calendar | last |
| words.energy_self_report | 0 to 10 | calendar | last |
| words.valence, words.optimism, words.anxiety_markers, words.self_focus | 0 to 1 | calendar | last |
| words_voice_gap | z difference | calendar | last |
| hormone.fsh_trend | mIU/mL | calendar | 28 day mean |

## Event catalog

| kind | quantity | Typical source |
|---|---|---|
| alcohol | standard drinks | manual, oura_tag |
| caffeine | mg or servings | manual, oura_tag |
| meal | none, with note | manual, dexcom_event |
| late_meal | derived: meal with start after 20:00 local | derived |
| supplement | none, with note | manual |
| medication | none, with note | manual |
| illness | none | manual, oura_tag |
| travel | time zone change in hours | derived from zone offset jumps |
| stress_event | 1 to 5 | manual |
| period_start | none | manual, derived from hormone pattern |
| bp_context | none, note: rested, post_coffee, post_exercise, stressed | manual prompt |
| screens_in_bed | minutes | derived from usage stats after sleep.bedtime |
| symptom.* | severity 1 to 5. Kinds: cramps, bloating, breast_tenderness, headache, migraine, hot_flash, night_sweat, mood_swing, irritability, anxiety, low_libido, acne, fatigue, brain_fog, insomnia, craving | manual, voice check-in confirmation |
| medication | dose, note: name | manual, profile |
| hormone_dose | dose, note: type and route | manual, profile |
| contraception_change | none, note: from and to | profile_change |
| pack_day | day number 1 to 28 | derived from profile for pack rhythm model |

## Rollup rules worth stating

- A "night ending on date D" is the main sleep session whose end time falls between 00:00 and 14:00
  local on D. Naps are stored but excluded from night metrics.
- Glucose time in range uses 70 to 140 mg/dL as the default band, which suits a person without
  diabetes using a CGM for metabolic insight. The band is a setting.
- Weight is always presented as a 7 day trailing mean. The daily mean exists in the table but the UI
  never surfaces a single day's weight as a finding.
- Blood pressure analysis uses the morning variant by default, since that is the reading clinical
  guidance treats as most comparable day to day.
- Screen time after 21:00 is a default cut. The baseline job also computes the user's own median
  bedtime and derives a personal "screen minutes in the 2 hours before median bedtime" metric.

- Voice and words metrics are computed only from check-ins with quality_ok = 1 and only once 14
  such check-ins exist. The composite indices are means of the z scores of their component features,
  with sign chosen so that a higher value means more of the named quality.
- words_voice_gap is words.valence z minus the negative of voice.flatness_index z, so a large positive
  value means "words say better than the voice does."
- When the hormonal model is pack_rhythm, phase strata are the four pack weeks. When it is
  dosing_rhythm, strata are the days since dose buckets. When it is no_cycle, the phase stratum is
  omitted and only weekday versus weekend stratification runs.
- Baselines are recomputed from scratch for the window after any profile_change with a hormonal
  effect (contraception, HRT, gender affirming hormones, pregnancy status), and insights do not pool
  data across such a change.

## Baseline and z score

For each (metric, agg) and each date D, the baseline job computes the median and interquartile range
over the 30 days ending the day before D, requiring at least 10 values. z30 is (value minus median)
divided by (IQR divided by 1.349), a robust standard deviation estimate. Metrics with fewer than 10
values in the window get a null z30 and are excluded from insights for that day.
