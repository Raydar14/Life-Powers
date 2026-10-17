# Goals, timer, reminders, journal, patterns: requests for other owners

Written by the Phase 2 personal data pass (`goals/`, `patterns/`, `data/personal/`, `di/PersonalModule.kt`).
No new dependency is needed; everything uses libraries already in `gradle/libs.versions.toml`.
`PersonalDatabase.kt` was not changed (no new queries were needed).

## AndroidManifest.xml (inside `<application>`)

`RECEIVE_BOOT_COMPLETED` and `POST_NOTIFICATIONS` are already declared. No exact alarm permission
is needed; reminders use inexact `setAndAllowWhileIdle`.

```xml
<receiver
    android:name=".goals.ReminderReceiver"
    android:exported="false" />

<receiver
    android:name=".goals.BootReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
        <action android:name="android.intent.action.TIME_SET" />
    </intent-filter>
</receiver>
```

The last two actions are optional but recommended: reminder times are local, so a zone or clock
change re-arms them. `BootReceiver` handles all four.

## LifePowersApp.onCreate

```kotlin
import com.lifepowers.app.domain.ReminderScheduler
import com.lifepowers.app.notify.Notices
import com.lifepowers.app.patterns.PersonalWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

    @Inject lateinit var reminderScheduler: ReminderScheduler
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        syncManager.schedulePeriodic()
        Notices.ensureChannels(this)
        PersonalWork.schedule(this)
        // Alarms are lost on force stop; re-arm on every start. Cheap and idempotent.
        appScope.launch { runCatching { reminderScheduler.rescheduleAll() } }
    }
```

`PersonalWork.schedule` enqueues two unique periodic jobs with KEEP: `patterns_periodic`
(`PatternWorker`, every 24 h, battery not low) and `auto_check_periodic` (`AutoCheckWorker`, every
6 h, auto checks for yesterday and today). Both are `@HiltWorker`s and rely on the existing
`HiltWorkerFactory` configuration.

## UI

- **Routes from notices.** Goal reminders and closing notices open `MainActivity` with
  `Notices.EXTRA_ROUTE = "goals"`. The running timer notice and the "Time is up" notice use
  `"timer"`. MainActivity / navigation should read that extra and open the Goals screen or the
  timer screen.
- **Notification permission.** On Android 13+ the UI must request `POST_NOTIFICATIONS` (for
  example when the first reminder time is set or the first timer starts). Without it every notice is
  silently skipped (`Notices.canPost`), and the timer and reminders still work.
- **No reschedule calls needed from the UI.** `GoalRepository.save`, `archive`, and `delete` re-arm
  the alarms themselves.
- **Timer.** `TimerController.running` is a StateFlow loaded from the database at construction;
  it can be null for a moment after process start. Elapsed time is `now - startUtcMs`.
  `stop()` returns null when the session was under half a minute (it is discarded, like cancel).
- **Pattern keys.** Insight `input` and `outcome` keys are metric keys (`sleep.deep_min`), event
  kinds (`alcohol`, `screens_in_bed`, `craving`, `habit_session`, `symptom.headache`), or
  `habit:<goal name>` for a habit's check days. The label function passed to `Patterns.sentence`
  needs to handle all three, for example `habit:Meditate` as "meditating" or "Meditate days".
- **First findings.** `PatternRepository` loads `filesDir/patterns.json` at construction; until the
  first `PatternWorker` run, `computedUtcMs` is null. A refresh button can call `recompute()`
  directly (runs on a background dispatcher, takes well under a second for 180 days).
- **Closing line in the view.** `GoalView.closingLine` is filled regardless of the goal's
  `closingNotice` flag so the screen can show it; only the 18:00 notice honors the flag.

## Data layer (Phase 1 owner)

- Done: `MetricRepository.deleteSamples(source, startUtcMs)` exists and the journal repository
  clears an entry's `words.*` samples when it is re-saved without scoring or deleted.
- Journal and voice words on the same day both write `words.*` with `Agg.LAST`; the later sample
  of the day wins.

## Behavior notes

- Reminder alarms: one per goal at its next due time (honoring `reminderDays`), request code
  `7_100_000 + goalId % 1_000_000`, plus one daily 18:00 closing alarm, armed only while some
  non daily goal has `closingNotice` on. Armed goal ids are kept in SharedPreferences
  `goal_reminders`. A reminder is skipped when the current period is already met.
- The "Time is up" notice is posted on the reminders channel (default importance) so it is heard;
  the running notice stays on the quiet timer channel.
- A journal entry or tarot pull whose local date differs from its creation day (written for an
  earlier day) is placed on the timeline at noon of its own date instead of the creation time.
  Same-day entries use `createdUtcMs` exactly.
- Auto checks are idempotent: each run deletes the goal's `auto` check for the date and inserts one
  only when the day qualifies. A phone down goal can show checked early in the day and lose the
  check later if screen time crosses the threshold; yesterday is settled on the next run.
- Deleting a journal entry also deletes its tarot pulls (rows and timeline moments), its photos
  (rows and encrypted files), and its journal moment.
- Photos: `filesDir/photos/<uuid>.bin`, `EncryptedFile` AES256_GCM_HKDF_4KB under the default
  `MasterKey` (the same keystore alias the database passphrase prefs use). Images with a long side
  over 2048 px are scaled down, rotated upright from EXIF, and re-encoded as JPEG quality 85.

## Not verified by a real build

No Android SDK here. The pure helpers (`PersonalCodec`, `GoalMath`, `PatternSeries`) and their
tests were compiled and run with a standalone kotlinc against `:core` (15 tests pass). Every other
file was type checked with kotlinc against hand written stubs of the Android, AndroidX, Room,
WorkManager, Hilt, and security-crypto APIs, so Kotlin-level errors are ruled out but the stubs
mirror my reading of those APIs. Points CI should confirm:

- `EncryptedFile.Builder(Context, File, MasterKey, FileEncryptionScheme)` and
  `MasterKey.Builder(context).setKeyScheme(AES256_GCM)` in security-crypto 1.1.0-alpha06.
- `NotificationCompat.Builder.setSilent`, `setUsesChronometer`, `setShowWhen`, `setWhen` (core 1.15).
- `android.graphics.Matrix.isIdentity` read as a Kotlin property, `android.media.ExifInterface(InputStream)`.
- `androidx.work.workDataOf`, `OneTimeWorkRequestBuilder`, `PeriodicWorkRequestBuilder` (work 2.10).
- `EntryPointAccessors.fromApplication(Context, Class)` for the receivers, which are plain
  `BroadcastReceiver`s (not `@AndroidEntryPoint`) so no Hilt bytecode transform is involved.
- Lint may flag `NotificationManagerCompat.notify` as MissingPermission in `TimerControllerImpl`;
  it is guarded by `Notices.canPost` and a SecurityException catch, same as `Notices.post`.
