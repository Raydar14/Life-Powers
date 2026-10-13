# Data sources and integration paths

One section per device. For each: what it measures, how the data can leave the vendor, which path
the app uses, and what is known to be missing. "Health Connect" means the Android Health Connect
platform; the user connects the vendor app to it once and the Life Powers app reads from it.

Verified against vendor documentation in October 2026. Vendor policies change, so recheck before
implementing each adapter.

---

## Oura Ring

**Measures.** Sleep stages and timing, HRV (nightly average and 5 minute series), resting heart rate,
respiratory rate, skin temperature deviation, SpO2, daytime heart rate, activity and steps, workouts,
readiness and sleep scores with contributors, daytime stress, resilience, cardiovascular age, VO2 max
estimate, user tags.

**Paths out.**

| Path | What you get | Notes |
|---|---|---|
| Health Connect | Sleep sessions and stages, heart rate, HRV, steps, activity | Enabled by the user in the Oura app under data sharing. Google Fit sync is discontinued. |
| Oura Cloud API v2 | Everything above plus readiness and sleep contributors, stress, resilience, tags, ring configuration | Base `https://api.ouraring.com/v2/usercollection/...`. Personal access token works for a single user without an OAuth app. Webhooks exist but need a public endpoint. V1 is retired. |
| CSV export | Daily summaries | From the Oura web account. Useful for Phase 0 only. |

**App uses.** Health Connect for the shared types, API v2 for scores, contributors, stress, and tags.
Source priority for sleep metrics: API v2 over Health Connect, since the API carries the scores.

**Missing.** Nothing significant. This is the richest and best documented source.

---

## Inito hormone monitor (assumed device)

**Measures.** Urine LH, estrogen metabolite (E3G), progesterone metabolite (PdG), FSH, per test day.
Also the app's own fertile window and ovulation confirmation.

**Paths out.**

| Path | What you get | Notes |
|---|---|---|
| PDF report | Chart plus a table of hormone values for a completed cycle | Share icon on the Chart screen. Requires at least one complete cycle. |
| Manual entry | Whatever you type | Reliable fallback. |
| API | None | Inito has no public API as of this writing. Support contact is help@inito.com if that changes. |

**App uses.** A PDF importer that reads the hormone value table, with a review screen so a parsing
error can be corrected before it is saved. Manual entry for the current cycle before the PDF exists.

**Missing.** Real time sync. Values arrive at the end of a cycle unless entered by hand. This is
acceptable because the hormone stream is used to label cycle phase, and phase can be back filled.

**Note on the Android app.** The Google Play listing says the Android Inito app supports only the
wireless InSight reader, not the clip on monitor. If you use the clip on model with an iPhone, the
PDF export still works and can be shared to the Android phone.

---

## Galaxy Watch via Samsung Health

**Measures.** Steps, distance, floors, heart rate (continuous and resting), HRV during sleep on
recent models, sleep stages and sleep score, SpO2, skin temperature during sleep, exercise sessions
with HR zones, body composition (BIA on Watch 4 and later), stress, ECG and blood pressure on
supported models in supported regions.

**Paths out.**

| Path | What you get | Notes |
|---|---|---|
| Health Connect | Exercise, sleep, heart rate, steps, SpO2, body composition, and most other types Samsung Health holds | Samsung Health has written to Health Connect since version 6.22.5. Watch data reaches Health Connect only after the watch syncs to the phone, on the watch's own schedule. |
| Samsung Health Data SDK | Direct read of Samsung Health's store, including skin temperature and some types not in Health Connect | Android only, needs Samsung Health 6.30.2 or later, and the app's package name and signing certificate must be registered with Samsung. Has a developer test mode. |
| Download personal data | Full CSV export | Samsung Health settings. Phase 0 only. |

**App uses.** Health Connect. The Samsung Health Data SDK is a Phase 3 option if skin temperature or
another type turns out to be missing from Health Connect.

**Missing.** Samsung's sleep score and stress score are proprietary and may not cross to Health
Connect. The Oura equivalents cover the same ground.

---

## Continuous glucose monitor

Two very different cases depending on brand.

### Dexcom G7 or Stelo

| Path | What you get | Notes |
|---|---|---|
| Health Connect | Glucose readings | Stelo writes with about a three hour delay. G6 and later apps support Health Connect on recent versions. |
| Dexcom API | Estimated glucose values, device info, user logged events (carbs, insulin, exercise, health) | OAuth 2.0. The API carries delayed data for consumer apps. Public developer program. |

**App uses.** Health Connect for readings, Dexcom API for logged events, which become behavior
events in the app.

### Abbott FreeStyle Libre 3 or 3 Plus

| Path | What you get | Notes |
|---|---|---|
| LibreView CSV | Full glucose history with timestamps, scan and sensor markers, notes | Export from the LibreView website. Manual step. |
| Health Connect | Not confirmed | No Abbott documentation found describing a Health Connect integration for the US Libre apps. |
| LibreLinkUp | Follower data | Intended for caregivers, not a public API. Not recommended as a dependency. |

**App uses.** A LibreView CSV importer, with a reminder to export weekly. If Abbott ships Health
Connect support, that adapter replaces the importer.

**Missing in both cases.** Real time readings. For retrospective pattern analysis the delay does not
matter. The app is explicitly not for dosing decisions.

---

## Smart scale

| Brand | Path | Notes |
|---|---|---|
| Withings | Withings API (OAuth 2.0, webhooks per user) and Health Connect | Full body composition by measure type code. Development apps are restricted to whitelisted accounts, which is fine for one user. Redirect URI must be a public URL, so a tiny static redirect page is needed. |
| Renpho, Eufy, Wyze, others | Health Connect | Weight and body fat reliably. Other composition fields vary by vendor. |
| Garmin Index | Health Connect via Garmin Connect | Garmin's own API is for business partners. |

**App uses.** Health Connect for weight, body fat, body water, bone mass, lean mass where present.
Withings API only if the scale is Withings and more detail is wanted.

**Missing.** Nothing that matters for trend analysis. The app averages over 7 days anyway.

---

## Smart blood pressure monitor

| Brand | Path | Notes |
|---|---|---|
| Withings BPM Connect or Core | Withings API and Health Connect | Same API as the scale. |
| Omron | Omron Connect app to Health Connect | Confirm the specific model supports Health Connect in Omron Connect settings. |
| Qardio, iHealth, others | Health Connect | Most vendors added Health Connect after Google Fit sync was retired. |
| Any | Manual entry | Two numbers and a pulse. Always available. |

**App uses.** Health Connect BloodPressureRecord. Manual entry as fallback.

**Missing.** Context. The app asks a one tap question after each new reading arrives: seated and
rested, after coffee, after exercise, stressed. That context is what makes BP analysis meaningful.

---

## Screen time

| Path | What you get | Notes |
|---|---|---|
| Android UsageStatsManager | Per app foreground time, interaction events, per hour buckets | Requires the PACKAGE_USAGE_STATS special permission, granted by the user in system settings, not a runtime dialog. Standard in parental control and digital wellbeing apps. |
| Digital Wellbeing | None | Google exposes no export or API. |
| RescueTime or similar | Their API | Only if you already use one. |

**App uses.** UsageStatsManager, queried daily for the previous day in hourly buckets. Derived
metrics: total screen minutes, screen minutes after 9pm, last unlock before sleep onset (joined to
Oura sleep start), first unlock after waking, longest continuous session.

**Missing.** Content. The app knows which app was open, not what you were doing in it. App category
(social, video, reading, messaging) is a good enough proxy and is grouped in the UI.

---

## Source priority

When two sources supply the same metric, the canonical copy comes from the first available source in
this list. The other copy stays in raw storage.

| Metric | Priority |
|---|---|
| Sleep stages, sleep timing | Oura API, Oura via Health Connect, Samsung Health via Health Connect |
| HRV, resting HR, temperature deviation | Oura API, Oura via Health Connect, Samsung Health |
| Daytime heart rate | Galaxy Watch via Health Connect, Oura |
| Steps, workouts | Galaxy Watch via Health Connect, Oura |
| Glucose | Dexcom API, Health Connect, LibreView import |
| Weight, body composition | Withings API, Health Connect |
| Blood pressure | Withings API, Health Connect, manual |
| Hormones | Inito PDF import, manual |
| Screen time | UsageStatsManager |
