# Swipey — Design Spec

**Date:** 2026-08-16
**Status:** Approved for planning

## 1. Purpose

Swipey lets a user clean up their phone's gallery by swiping: left marks a photo or video
for deletion, right keeps it. At the end of a session, everything marked is moved to
Android's system trash in a single operation.

The core problem it solves is that gallery apps make bulk cleanup tedious — you tap into a
photo, tap delete, confirm, tap back, repeat. Swipey makes the unit of work one gesture.

**Absolute requirement:** every item Swipey removes must be recoverable from a bin. The app
is structurally incapable of destroying media — it never calls `createDeleteRequest()` and
never calls `ContentResolver.delete()` on a media URI. Permanent deletion can only happen
through the OS's own expiry sweep or through the user's own gallery app.

## 2. Scope

**In scope (MVP)**

- Three ways to start a session: all media by sort order, by album, or shuffled
- Photos and videos, with minimal video playback (muted autoplay, tap to pause)
- Swipe left/right with undo, plus equivalent buttons
- Review screen before committing anything
- Batch move-to-trash through one system consent dialog
- In-app Bin listing what Swipey trashed, with restore
- Memory of kept items so they do not reappear in later sessions

**Out of scope (explicitly deferred)**

- Any permanent-delete or "empty bin" affordance
- Duplicate detection, blur/screenshot detection, any automatic scoring
- Favorites, tagging, moving between albums, editing
- Cloud, sync, accounts, sharing
- iOS

## 3. Platform decisions

| Decision | Value | Reason |
|---|---|---|
| Language / UI | Kotlin + Jetpack Compose | Native gesture feel; the whole app is one MediaStore data source |
| minSdk | 33 | Granular `READ_MEDIA_*` permissions with no legacy storage branch |
| compileSdk / targetSdk | 36 | Current platform installed locally (android-36) |
| JDK | 17 (Zulu or Oracle, both present) | Required by Android Gradle Plugin; JDK 23 is also installed and must not be used |
| Architecture | Single Gradle module, no DI framework | ~15 source files; Hilt and use-case layers would be scaffolding without payoff |
| Image loading | Coil 3 (`coil-compose` + `coil-video`) | Handles `content://` URIs and video frame extraction |
| Video playback | Media3 ExoPlayer, one shared instance | Only the top card ever plays |
| Local storage | Room | Two small tables; see §7 |
| Navigation | `navigation-compose` | Six screens |

Dependency versions are pinned in `gradle/libs.versions.toml` and resolved against what the
local SDK actually supports at implementation time.

## 4. Screens and flow

```
PermissionGate ──> Home ──┬── All media ──> SortChooser ──┐
                          ├── Albums ────> AlbumList ─────┼──> Deck ──> Review ──[system dialog]──> Result
                          ├── Shuffle ────────────────────┘                                          │
                          └── Bin <────────────────────────────────────────────────────────────────┘
```

**PermissionGate** — requests media access in a single dialog (§6). Three outcomes: full
access proceeds; partial access ("Select photos and videos") shows a blocking explanation
with a button to app settings; denial shows a rationale with a retry.

**Home** — three entries (All media, Albums, Shuffle) plus a Bin entry showing the count of
items Swipey has trashed.

**SortChooser** — Newest first, Oldest first, Largest first, Smallest first.

**AlbumList** — one row per bucket: name, item count, total size. Albums are sorted by total
size descending, because the biggest folders are the ones worth cleaning.

**Deck** — the swipe surface. Header shows `42 / 318`, a `12 marked · 1.2 GB` chip that taps
through to Review, and the chip is hidden when nothing is marked. Bottom bar has Undo,
Delete, Keep.

Terminal states:

| Condition | Behaviour |
|---|---|
| Queue exhausted, `markedCount > 0` | Auto-navigate to Review |
| Queue exhausted, `markedCount == 0` | "Nothing marked — all caught up", route back to Home |
| Source has zero eligible items after the `reviewed_media` filter | Never enter the Deck; show the empty state on AlbumList or Home |
| Back press with marks pending | Confirm dialog: discard marks, or go to Review |

**Review** — 3-column grid of everything marked, each tile showing its size with an X to
unmark. Header: `12 items · 340 MB`. Primary action: **Move 12 items to Trash**.

**Result** — `12 items moved to trash. Recoverable until at least 15 Sep.` plus the standing
caveat that space is freed only when the trash is emptied, and a link into the Bin.

**Bin** — items Swipey trashed, newest first, each showing size and `Recoverable until at
least <date>`. Multi-select and **Restore**. A footer notes how many *other* items sit in the
system trash that Swipey did not put there. No delete action of any kind.

## 5. Data layer

### 5.1 Model

```kotlin
data class MediaItem(
    val id: Long,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val durationMs: Long?,     // videos only
    val bucketId: Long,
    val bucketName: String,
    val displayName: String,
)
```

**`MediaItem` holds no Android types.** It deliberately does not carry a `Uri` field —
`android.net.Uri` is a framework class, and holding one here would make `SwipeSession` (§10)
impossible to unit test on the JVM. The URI is derived at the Android edge:

```kotlin
fun MediaItem.contentUri(): Uri = ContentUris.withAppendedId(
    if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    id,
)
```

This is enforced, not merely intended: a test asserts that no source file under `domain/`
imports `android.*` (§13).

### 5.2 Queries

Media is read with **two separate queries** — `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`
and `MediaStore.Video.Media.EXTERNAL_CONTENT_URI` — merged and sorted in memory.

`MediaStore.Files.getContentUri("external")` must **not** be used. The Files collection has no
whole-collection access shortcut in `MediaProvider.appendAccessCheckQuery()`, so it falls
through to ownership filtering and would return almost nothing for the Bin query.

Sorting therefore happens in Kotlin rather than SQL, over the merged list. This is required
anyway to sort images and videos together by size.

The deck query passes **no** match-trashed argument: on collection URIs `MATCH_DEFAULT`
resolves to `MATCH_EXCLUDE`, so trashed items are excluded for free.

The Bin query passes `QUERY_ARG_MATCH_TRASHED = MATCH_ONLY` against both collections.

**Constraint on every query:** the substring `owner_package_name` must never appear in
`QUERY_ARG_SQL_SELECTION`, `QUERY_ARG_SQL_SORT_ORDER`, `QUERY_ARG_SQL_GROUP_BY`, or
`QUERY_ARG_SQL_HAVING`. On targetSdk 34+, `MediaProvider.shouldFilterOwnerPackageNameInSelection()`
silently ANDs in an ownership predicate if it does, collapsing the Bin to self-owned rows.

Bin projection: `_ID`, `DISPLAY_NAME`, `SIZE`, `MIME_TYPE`, `DATE_ADDED`, `DATE_EXPIRES`,
`IS_TRASHED`, `DURATION`. **`DATE_EXPIRES` is in Unix seconds** — multiply by 1000 for
`Instant`. It is never written; `FileUtils.computeDateExpires()` strips app-supplied values
silently.

### 5.3 Never infer trashed state from an item URI

For `*_ID` URIs, `MediaProvider` forces `matchTrashed = MATCH_INCLUDE` and ignores whatever
argument was passed. Trashed state is always read from the explicit `IS_TRASHED` column.

## 6. Permissions

The manifest declares `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and
`READ_MEDIA_VISUAL_USER_SELECTED`. The third is inert on API 33 and takes effect from API 34.

**All of them are requested in one `RequestMultiplePermissions` launch**, per Android's
own guidance that requesting them separately produces multiple stacked system dialogs:

```kotlin
val perms = if (Build.VERSION.SDK_INT >= 34)
    arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED)
else
    arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)
```

**Deviation from research recommendation, deliberate.** The research advised *not* declaring
`READ_MEDIA_VISUAL_USER_SELECTED`, on the grounds that opting into partial access is what
breaks the Bin query. We declare it anyway, for detectability. On Android 14+ the system
offers "Select photos and videos" whether or not the app declares it; an app that does not
declare it is run in a **compatibility mode** where the selected-photos grant reports
`READ_MEDIA_IMAGES` as `GRANTED`, making partial access indistinguishable from full access at
runtime. That is the worst outcome available: the app would silently operate in a state where
its recoverability guarantee does not hold. Declaring the permission makes the state explicit:

| `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` | `READ_MEDIA_VISUAL_USER_SELECTED` | State |
|---|---|---|
| granted | — | **Full** — proceed |
| denied | granted | **Partial** — block with explanation |
| denied | denied | **Denied** — rationale + retry |

Partial access blocks rather than degrades, per the approved decision: without full access the
Bin cannot enumerate trashed items, so a deletion could not be shown to be recoverable.

`ACCESS_MEDIA_LOCATION` is not requested — Swipey never reads pixel data or EXIF, it only
displays and trashes. Nothing is copied, so no metadata can be lost.

`MANAGE_MEDIA` is not requested. It would suppress the consent dialogs, but it removes a
system-level confirmation on the most destructive action in the app and buys no recoverability.

## 7. Local database

Room, two tables.

```kotlin
@Entity(tableName = "reviewed_media")
data class ReviewedMedia(
    @PrimaryKey val mediaId: Long,
    val decision: String,      // "KEEP" | "TRASHED"
    val reviewedAt: Long,      // epoch millis
)

@Entity(tableName = "trashed_by_swipey")
data class TrashedItem(
    @PrimaryKey val mediaId: Long,
    val isVideo: Boolean,
    val displayName: String,
    val sizeBytes: Long,
    val trashedAt: Long,       // epoch millis
    val state: String,         // "PENDING_TRASH" | "TRASHED" | "PENDING_RESTORE"
)
```

`reviewed_media` filters the deck so kept items never reappear. Settings offers "Reset review
history", which clears this table only.

`trashed_by_swipey` is how the Bin knows which trashed items are Swipey's. **Ownership cannot
be used for this** — `createTrashRequest` transfers no ownership, and `OWNER_PACKAGE_NAME`
reads `NULL` for foreign packages on targetSdk 34+. `displayName` and `sizeBytes` are stored
as a cross-check against MediaStore ID reuse.

The `state` column exists because the consent dialog runs in **another process**
(`MediaProvider`'s `PermissionActivity`), so the trash can complete while Swipey is dead. See
§8.1 — without it, a routine low-memory kill would silently and permanently lose items from
the Bin.

## 8. Trash and restore pipeline

Both directions are the same API with the boolean inverted. There is no
`createUntrashRequest` in the public API.

```kotlin
// Trash
MediaStore.createTrashRequest(resolver, uris, true)
// Restore
MediaStore.createTrashRequest(resolver, uris, false)
```

Each returns a `PendingIntent`, launched via `ActivityResultContracts.StartIntentSenderForResult`
with `IntentSenderRequest.Builder(pendingIntent.intentSender).build()`.

**Chunking.** URIs are chunked to **500 per request**. targetSdk 36 enables the
`LIMIT_CREATE_REQUEST_URIS` compat change, which throws `IllegalArgumentException` above 2000.
Each chunk is one consent dialog; the UI states how many confirmations to expect when a
session exceeds one chunk.

**`RESULT_OK` is not proof of success.** `PermissionActivity` builds each operation with
`.withExceptionAllowed(true)`, wraps `applyBatch` in a swallowing catch, and calls
`setResult(RESULT_OK)` unconditionally. After every trash and every restore, the affected IDs
are re-queried and `IS_TRASHED` is read explicitly. Local state is written only from that
verified result, and per-item failures are reported per item.

### 8.1 Durability across process death

The consent dialog runs in `MediaProvider`'s `PermissionActivity` — a **different process**.
The trash is applied there via `applyBatch` and completes whether or not Swipey is still
alive. Swipey's activity is stopped behind that dialog and is an ordinary candidate for a
low-memory kill. If the process dies after the user taps Allow but before the result callback
runs, the items are trashed on disk while Swipey has recorded nothing.

Those items would then be invisible everywhere in the app: the deck excludes them for free
(§5.2), and the Bin renders only rows present in `trashed_by_swipey`. **That is a silent,
permanent hole in the guarantee in §1** — so it is closed by design, not left to the
implementer:

1. **Before** launching the `PendingIntent`, write every candidate to `trashed_by_swipey`
   with `state = PENDING_TRASH` (and, for restore, flip the rows to `PENDING_RESTORE`).
2. On the result callback, run the §8.2 verification and resolve each row.
3. **On every app start and on every Bin open**, run the same verification over any rows
   still in a `PENDING_*` state and resolve them.

Resolution rules, identical in both paths:

| Verified state | `PENDING_TRASH` row | `PENDING_RESTORE` row |
|---|---|---|
| `IS_TRASHED = 1` | → `TRASHED` | stays `TRASHED` (restore did not happen) |
| `IS_TRASHED = 0` | delete row (user declined) | delete row, and delete its `reviewed_media` row |
| Row absent entirely | delete row, report "no longer on device" | delete row, report "no longer on device" |

This recovery pass is what makes the recoverability guarantee survive process death, and it
is why the naive alternative — adopting any live trashed row that has no local row — is
wrong: the Bin must distinguish Swipey's items from ones the user trashed elsewhere, and §7
establishes that ownership cannot make that distinction.

### 8.2 The verification query, exactly

Ambiguity here is dangerous, because the spec defines two paths that behave *oppositely*: a
bare collection query resolves to `MATCH_EXCLUDE` (§5.2) while an `*_ID` URI query is forced
to `MATCH_INCLUDE` (§5.3). Verifying a trash with a bare collection query would find no rows
and report every *successful* trash as a failure.

Verification is therefore always:

```kotlin
resolver.query(
    collectionUri,                                  // Images or Video, per item type
    arrayOf(_ID, IS_TRASHED, DATE_EXPIRES, DISPLAY_NAME, SIZE),
    Bundle().apply {
        putString(QUERY_ARG_SQL_SELECTION, "_id IN (...)")
        putInt(QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)   // required
    },
    null,
)
```

Read the `IS_TRASHED` value per row. **An absent row means "gone from the device", never "not
trashed"** — the two are opposite conclusions and conflating them corrupts local state. A
per-ID URI query would also work, since MediaProvider forces `MATCH_INCLUDE` on those, but
one batched collection query is cheaper.

**Cancellation** (`RESULT_CANCELED`) leaves marks intact and returns to Review with a
snackbar. If chunk 2 of 3 is cancelled, chunk 1 stays trashed and is recorded; the UI reports
exactly what went through.

**A verified restore makes the item eligible again.** It deletes both the item's
`trashed_by_swipey` row *and* its `reviewed_media` row, so the item re-enters future decks.
Restoring something and never seeing it again would be its own kind of data loss.

**Bin reconciliation.** On every Bin open, local rows are joined against the live `MATCH_ONLY`
result. Local rows with no live match were restored elsewhere or purged; they are pruned from
Room and surfaced as "no longer in trash" rather than rendered as dead tiles. When the cause
was an external restore (the row still exists in MediaStore with `IS_TRASHED = 0`), its
`reviewed_media` row is deleted too, for the same reason.

## 9. Retention, and what the UI may claim

`createTrashRequest` sets `IS_TRASHED=1` and stamps `DATE_EXPIRES = now + 30 days`. The value
is `readOnly` — no app, owner or not, can lengthen or shorten it.

Thirty days is a **floor, not a deadline**. Purging happens only inside
`MediaProvider.onIdleMaintenance()`, on a `JobScheduler` job requiring charging *and* device
idle, and that sweep deletes only rows whose expiry fell within the last 7 days — anything
staler is renewed for another 7. Items linger longer than 30 days far more often than they
vanish sooner.

**Trashing frees zero bytes.** The file is renamed in place to `.trashed-<epochSeconds>-<name>`
in its original directory. Space is reclaimed at purge.

UI copy rules, binding:

1. Say "moved to trash", never "deleted".
2. Never claim space was freed. Say: *"4.2 GB moved to trash — space is freed when the trash is emptied (about 30 days)."*
3. Show per-item expiry from `DATE_EXPIRES` as a minimum: *"Recoverable until at least 12 Sep."* Never promise a hard 30 days.
4. State that this is the **system** trash, shared with Google Photos and Files — emptying it there removes items from Swipey's Bin immediately and permanently.
5. Warn before restoring that a second system confirmation will appear.
6. Report partial failures per item, from the `IS_TRASHED` re-check, never blanket success from `RESULT_OK`.
7. State plainly that Swipey has no permanent-delete function.

## 10. Session logic

`SwipeSession` is pure Kotlin with no Android imports, which is what makes the core logic unit
testable:

```kotlin
class SwipeSession(private val queue: List<MediaItem>) {
    val current: MediaItem?
    val position: Int
    val isExhausted: Boolean   // drives the Deck terminal states in §4
    val markedCount: Int
    val markedBytes: Long
    fun swipeLeft()      // mark for trash
    fun swipeRight()     // keep
    fun undo()           // reverse last decision
    fun unmark(id: Long) // from the Review screen; also un-marks mid-session
    fun marked(): List<MediaItem>
}
```

`KEEP` is persisted to Room immediately on right-swipe (a single cheap insert), so a crash
mid-session loses nothing. `undo()` deletes that row. `TRASHED` rows are written only after
the `IS_TRASHED` re-check confirms it.

Shuffle applies a session-seeded shuffle to the filtered list.

## 11. Swipe deck mechanics

The top card takes a `pointerInput` drag. Horizontal offset drives rotation (±12°) and a
red/green overlay whose alpha tracks progress toward the threshold. A decision commits at 30%
of screen width **or** on a velocity fling, then the card animates off-screen.

Exactly one card is rendered beneath the top card at 0.95 scale for depth. Never more — this
is what keeps a 20,000-item queue flat in memory.

**Video:** a single ExoPlayer instance bound only to the top card. Muted, looping, autoplay on
arrival; tap toggles play/pause; a mute button and duration badge sit in the corner. The
player is released on lifecycle pause and rebound on resume.

Thumbnails load from the `content://` ID URI directly through Coil. Trashing sets
`triggerInvalidate=true`, so cached thumbnails are dropped and regenerated.

## 12. Error handling

| Condition | Response |
|---|---|
| Permission revoked mid-session | Return to PermissionGate; session state preserved |
| Cursor returns null | Empty state with retry; never crash |
| Item deleted by another app mid-session | Skip silently, decrement queue total |
| `RESULT_CANCELED` from consent dialog | Marks preserved, snackbar, stay on Review |
| Process killed while the consent dialog is showing | `PENDING_*` rows resolved by the §8.1 recovery pass on next start |
| Partial batch failure | Per-item report from `IS_TRASHED` re-check |
| Chunk N cancelled after chunk N-1 succeeded | Report exactly what was trashed; earlier chunks recorded |
| Bin item no longer in trash | Prune from Room, tell the user it left the trash |
| ExoPlayer failure on a corrupt video | Fall back to static thumbnail; the card stays swipeable |

## 13. Testing

**JVM unit tests** (no Android dependencies, written test-first):

- `SwipeSessionTest` — decisions, undo across boundaries, marked set, byte totals, exhaustion with and without marks
- `SortModeTest` — the four orders over a merged image+video list, ties, missing sizes
- `ChunkingTest` — chunk boundaries at 0, 1, 500, 501, 1000 items
- `BinReconciliationTest` — local vs live joins: present, purged, restored-elsewhere, ID reuse caught by name+size mismatch
- `PendingOpRecoveryTest` — the §8.1 resolution table, both directions, including the absent-row case
- `RestoreEligibilityTest` — a verified restore clears `reviewed_media` and the item re-enters the deck
- `ByteFormatTest` — formatting boundaries
- `PermissionStateTest` — the three-state resolution table in §6
- `DomainPurityTest` — no file under `domain/` imports `android.*`

**Instrumented / on-device.** The rest is verified on the physical device against the
checklist in §14. `MediaProvider` behaviour cannot be faked meaningfully, so pretending
otherwise with mocks would be theatre.

## 14. Device verification checklist

These are unresolved from documentation and **must** be confirmed on the target phone before
the MVP is called done. Each is a known risk, not a formality.

1. **Partial-access detection.** Grant "Select photos and videos" and confirm the block screen appears — i.e. that declaring `READ_MEDIA_VISUAL_USER_SELECTED` really does make the state detectable (§6).
2. **Foreign trashed thumbnails.** Confirm Coil can load a trashed item owned by the Camera app or WhatsApp. The FUSE path uses a private `MATCH_VISIBLE_FOR_FILEPATH` value with its own ownership clause; if this fails, the Bin needs a placeholder tile design.
3. **Trash round-trip.** Trash an item, confirm `IS_TRASHED=1`, confirm it leaves the deck, confirm it appears in the Bin.
4. **Restore fidelity.** Restore it and confirm it returns to its original album with its original date, and that the second consent dialog appears as expected.
5. **Mixed batch.** Confirm an images+videos batch is accepted in one request and check the dialog wording matches our UI copy.
6. **Chunk cap.** Confirm 500 URIs per request does not trip `IllegalArgumentException` on this device's MediaProvider module version.
7. **OEM bin interaction.** Observe whether the device's own gallery bin shows Swipey-trashed items, and whether emptying it purges them. Affects caveat wording only.
8. **Long filenames.** Untrash runs `trimFilename(255)` and `ensureUniqueFileColumns`, so a long name can return truncated or suffixed `(1)`. Confirm whether realistic camera/WhatsApp filenames can reach this.
9. **Process-death recovery.** With the consent dialog showing, kill the app (`adb shell am kill com.swipey.app`), tap Allow, reopen Swipey, and confirm the §8.1 recovery pass finds the items and shows them in the Bin.

## 15. Build and install

```
gradle wrapper bootstrap (no gradle CLI installed; wrapper fetched during setup)
JAVA_HOME → JDK 17    # never JDK 23, which is the system default here
./gradlew assembleDebug
adb pair <host>:<port>       # phone: Developer options → Wireless debugging → Pair with code
adb connect <host>:<port>
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 16. Risks

| Risk | Mitigation |
|---|---|
| Partial access proves undetectable despite declaring the permission | Checklist item 1 runs first; fallback is a count-based heuristic comparing collection size against the picker selection |
| OEM MediaProvider fork behaves differently | MediaProvider is a Mainline module and OEMs fork it; everything in §14 is verified on the actual device rather than assumed |
| MediaStore ID reuse after a rescan points a Bin row at the wrong file | `displayName` + `sizeBytes` cross-check; mismatches are pruned, not shown |
| User expects space back immediately | UI copy rules §9 make this explicit at every step where it could mislead |
