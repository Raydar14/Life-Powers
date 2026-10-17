# Assistant features: what other owners need to wire

Everything below is already written in `assistant/`, `di/AssistantModule.kt`, and `ui/screens/assistant/`.
The journal, tarot, and check-in buttons are wired in their own screens. Two composables need a home
in screens owned by others.

## AssistantSettingsSection (settings owner)

`com.lifepowers.app.ui.screens.assistant.AssistantSettingsSection(modifier: Modifier = Modifier)`

- Place it in `SettingsScreen` where the current `AssistantCard(...)` sits (the "Assistant" group,
  around the `AssistantCard(on = state.assistantOn, ...)` call), and remove `AssistantCard` and
  `AssistantRow` with their "next phase" labels. The section has its own `@HiltViewModel`
  (`AssistantSettingsViewModel`), so nothing is passed in.
- It holds the master switch (still `SettingsRepository.assistantMaster`, so the existing
  `SettingsViewModel.setAssistant` keeps working if you want to leave it), the key field with Save and
  Remove, and the four per feature switches (shown only while the master switch is on).
- It renders as a column of soft cards; give it `Modifier.fillMaxWidth()` inside the settings column.

## AssistantNarrativeCard (patterns or Today owner)

`com.lifepowers.app.ui.screens.assistant.AssistantNarrativeCard(findings: List<String>, goals: List<String>, modifier: Modifier = Modifier)`

- Suggested home: the Patterns screen, below the findings list (or a weekly spot on Today).
- `findings`: the finding sentences already shown to the person, for example
  `insights.map { Patterns.sentence(it, PatternLabels::label) }`. Plain sentences only, no numbers
  beyond what the sentence already shows.
- `goals`: one summary line per goal, for example `"Walk 20 minutes: 4 of 7 days this week"`.
- It renders nothing unless the weekly note feature is available (master on, feature on, key saved)
  and there is at least one finding or goal, so it is safe to place unconditionally.

## Hilt

- `di/AssistantModule.kt` binds `AssistantRepository` to `AssistantRepositoryImpl`. `AssistantKeyStore`,
  `ClaudeGateway`, and `PhotoNotes` are `@Singleton` classes with `@Inject` constructors; no other module
  changes are needed. `AssistantDao` comes from `PersonalDatabaseModule` as before.
- New constructor dependencies on existing view models: `TarotPullViewModel` now also takes
  `AssistantRepository` and `ForecastRepository`; `JournalEntryViewModel` takes `AssistantRepository` and
  `PhotoNotes`; `CheckinViewModel` takes `AssistantRepository`. All are already bound.

## Manifest and build

- No manifest change: `INTERNET` is already declared. Calls go to api.anthropic.com over HTTPS.
- `com.anthropic:anthropic-java:2.34.0` brings Jackson 2.18 and OkHttp 4.12.0 (same as the app's).
  If the release build reports duplicate resources such as
  `META-INF/versions/9/module-info.class`, add that path to `packaging.resources.excludes` in
  `app/build.gradle.kts`. Minify is off today; if it is turned on, keep Jackson and
  `com.anthropic.**` model classes (the SDK deserializes by reflection).
- Backups and "delete everything" (data vault owner): the key lives in EncryptedSharedPreferences file
  `assistant_secrets`. Leave it out of backups; delete it (or call `AssistantRepository.setKey(null)`) in
  `deleteEverything()`. Per feature switches are DataStore booleans `assistant_<feature>` in the
  settings store; the call log is the `assistant_call` table and photo notes the `photo_note` table.
- "What has ever left the phone" in Privacy and data can read `AssistantRepository.log`
  (newest first, up to 200 calls, each with the plain words of what was sent and whether it worked).
