# Needs from the journal and tarot UI

Written by the journal UI engineer for Phase 2. Everything lives in
`app/src/main/kotlin/com/lifepowers/app/ui/screens/journal/`. Nothing here blocks a build, but the
navigation items are needed for the screens to be reachable.

## Navigation (ui/navigation)

Composables and the routes they expect:

| Route | Composable |
|---|---|
| `journal` | `JournalScreen(onNavigate = go)` (unchanged signature) |
| `journal/entry/{id}` | `JournalEntryScreen(entryId = id, onBack = back, onNavigate = go)` |
| `tarot` | `TarotScreen(onNavigate = go, onBack = back)` |
| `tarot/pull/{id}?entryId={entryId}` | `TarotPullScreen(pullId = id, entryId = entryId, onBack = back)` |

- `id` and `entryId` are `NavType.LongType`. `entryId` is an optional query argument with
  `defaultValue = 0L`, so both `tarot/pull/5` and `tarot/pull/0?entryId=12` resolve to the same destination.
- 0 means new: `journal/entry/0` starts a new entry for today; `tarot/pull/0` starts a new pull for today.
- **JournalEntryScreen has an optional third parameter `onNavigate: ((String) -> Unit)? = null`.** The required
  call `JournalEntryScreen(entryId, onBack)` compiles, but please pass `onNavigate = go` as well: without it the
  entry cannot open its attached cards or show "Add a card to this entry" (they are hidden when it is null).
- Every screen also takes `viewModel = hiltViewModel()` as a trailing default parameter, like the others.
- The entry screen handles system back itself (BackHandler) so it can keep unsaved words; it calls `onBack`
  once the save finishes. `onBack` should be a plain `popBackStack()`.
- Suggested `tabFor`: `journal/entry/{id}`, `tarot` and `tarot/pull/...` belong to the Journal tab.
- The Journal screen still uses `Routes.CHECKIN` for its "Check in" button.

## Data (JournalRepository)

- `JournalRepository` must be bound in Hilt; four view models inject it (`JournalViewModel`,
  `JournalEntryViewModel`, `TarotPullViewModel` and `TarotViewModel`).
- There is no single pull lookup in the contract, so `TarotPullViewModel` finds an existing pull through the
  entry (when `entryId` is passed) or by reading `pulls(1900-01-01, today + 5 years)` once. A
  `suspend fun pull(id: Long): TarotPull?` would be cheaper; if one is added, only `findPull` changes.
- `entry(id)` should emit again when photos or pulls are added to or removed from that entry, so the open entry
  screen picks up a card saved from the pull screen. The screen only takes photos and pulls from later
  emissions; it never overwrites what the person is typing.
- `saveEntry` receives the entry's current `photos` and `pulls` lists; it should ignore them (they are saved
  through `addPhoto` and `savePull`).
- Deleting an entry: the dialog says "The words, the voice note, and its photos are deleted from the phone."
  It says nothing about pulls; keeping pulls with `entryId = null` (so they stay in the tarot log) seems kindest.
- The repository's timeline moments for entries and pulls (kind `journal` or `tarot_pull` with a source other
  than `manual`) are hidden from the Journal "Today so far" card, since entries show there already. Old manual
  journal notes (source `manual`) still show.
- Photo bytes are passed as read: from the picker with `contentResolver.getType(uri)` (falling back to
  `image/jpeg`), and from the camera as JPEG at quality 90. Thumbnails are decoded with `BitmapFactory`
  (`inSampleSize`) and turned upright from EXIF orientation with the platform `android.media.ExifInterface`.

## Manifest

- "Take photo" uses `ActivityResultContracts.TakePicturePreview()`, which needs no permission as long as the
  manifest does **not** declare `android.permission.CAMERA`. If someone adds CAMERA, the camera intent will
  throw until the permission is requested at runtime. The preview bitmap is small by design; a full size
  capture would need a FileProvider and `TakePicture()`.
- RECORD_AUDIO is already declared; the entry screen requests it before a voice note.

## Assistant (next phase)

- No assistant buttons appear in this phase. `JournalEntryScreen.kt` keeps a private, unused
  `AssistantEntryActions(visible)` stub for "ask about a photo" and "interpret a card". When wired it must read
  `SettingsRepository.assistantMaster` (and the per feature switch) and render nothing while it is off.
- `TarotPull.interpretation` and `interpretationFromAssistant` are carried through the pull editor untouched.
