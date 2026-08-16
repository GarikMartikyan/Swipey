package com.swipey.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swipey.app.SwipeyApp
import com.swipey.app.data.RecoveryReport
import com.swipey.app.domain.Album
import com.swipey.app.domain.SortMode
import com.swipey.app.domain.toAlbums
import com.swipey.app.ui.albums.AlbumsScreen
import com.swipey.app.ui.albums.SortChooserScreen
import com.swipey.app.ui.bin.BinScreen
import com.swipey.app.ui.bin.BinViewModel
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.common.queryCatching
import com.swipey.app.ui.common.rememberTrashLauncher
import com.swipey.app.ui.deck.DeckScreen
import com.swipey.app.ui.deck.DeckViewModel
import com.swipey.app.ui.design.SwipeyButton
import com.swipey.app.ui.design.SwipeyButtonVariant
import com.swipey.app.ui.design.SwipeyProgressBar
import com.swipey.app.ui.design.SwipeyScreen
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme
import com.swipey.app.ui.home.HomeScreen
import com.swipey.app.ui.permission.PermissionGate
import com.swipey.app.ui.result.ResultScreen
import com.swipey.app.ui.review.ReviewScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The composable root. Owns navigation and the two pieces of state that outlive a
 * single destination:
 *  - [DeckViewModel], shared by the Deck and Review routes (built once, scoped to the
 *    Activity via the default [LocalViewModelStoreOwner][androidx.compose.ui.platform.LocalViewModelStoreOwner]
 *    at this call site — SwipeyRoot sits outside every NavHost `composable{}` block, so
 *    it isn't re-scoped to a NavBackStackEntry). Deck.load() rebuilds its session on
 *    every entry, so Activity-lifetime reuse of the instance is safe.
 *  - the last commit's [RecoveryReport]/expiry, which the Result route has no route
 *    argument to carry (Routes.RESULT takes none) so it is hoisted here instead. Fix
 *    round 2, Important 3: both are `rememberSaveable`, not plain `remember` — Result's
 *    self-heal-to-Home branch below exists for a genuine process death, and a plain
 *    `remember` was firing it on *every* Activity recreation (rotation, a dark-mode
 *    switch at sunset, a font-size or locale change — `MainActivity` declares no
 *    `android:configChanges`), silently discarding the outcome of a commit just made.
 *    [RecoveryReport] is `Serializable` specifically so this can hold it directly.
 */
@Composable
fun SwipeyRoot(app: SwipeyApp) {
    val navController = rememberNavController()
    var binCount by remember { mutableIntStateOf(0) }
    var trashReport by rememberSaveable { mutableStateOf<RecoveryReport?>(null) }
    var trashExpirySec by rememberSaveable { mutableStateOf<Long?>(null) }

    val deckViewModel: DeckViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DeckViewModel(app.mediaRepository, app.database) }
        },
    )

    PermissionGate {
        // Spec §8.1 recovery pass. Ruling I4: this must run INSIDE PermissionGate's
        // content, not before it — TrashRepository guards itself against missing full
        // media access too, but the call site itself must not race ahead of the
        // permission check. Home must not read binCount before this completes.
        //
        // Whole-branch review, I4: wrapped. A throw here does not stay local — a
        // LaunchedEffect's exception propagates to the Recomposer and kills the Activity,
        // and since this effect runs on every entry into the FULL-access branch that is a
        // crash *loop* on every launch. MediaProvider throws readily (SecurityException on
        // a permission race, IllegalArgumentException on a selection it rejects). The pass
        // computes every live row before it writes anything, so a throw leaves the
        // database untouched: binCount simply keeps its previous value and the next entry
        // into this branch retries. Nothing is lost, and the app stays usable.
        LaunchedEffect(Unit) {
            queryCatching {
                app.trashRepository.verifyAndResolve()
                binCount = app.trashRepository.trashedCount()
            }
        }

        NavHost(navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                // Refresh on every (re)entry — not just app start — so a restore done in
                // the Bin, or a commit done in Review, is reflected the moment the user
                // is back on Home.
                // I4: same Recomposer exposure as the recovery pass above. A Room read
                // that throws must leave the count stale, not take the Activity down.
                LaunchedEffect(Unit) {
                    queryCatching { binCount = app.trashRepository.trashedCount() }
                }
                HomeScreen(
                    binCount = binCount,
                    onAllMedia = { navController.navigate(Routes.SORT) },
                    onAlbums = { navController.navigate(Routes.ALBUMS) },
                    onShuffle = { navController.navigate(Routes.deck(shuffle = true)) },
                    onBin = { navController.navigate(Routes.BIN) },
                )
            }

            composable(Routes.SORT) {
                SortChooserScreen(
                    onPick = { mode -> navController.navigate(Routes.deck(sort = mode.name)) },
                )
            }

            composable(Routes.ALBUMS) {
                var albums by remember { mutableStateOf<List<Album>?>(null) }
                // I4: the third state this route was missing. Without it a throw from
                // queryAll() killed the Activity; catching it without a flag would instead
                // leave `albums` null forever, i.e. a spinner that never resolves. `attempt`
                // is the retry: bumping it re-keys the effect below, which is the only way
                // to re-run a LaunchedEffect(Unit) without leaving the route.
                var albumsFailed by remember { mutableStateOf(false) }
                var attempt by remember { mutableIntStateOf(0) }
                LaunchedEffect(attempt) {
                    albumsFailed = false
                    albums = null
                    // Off Main: queryAll() can return up to ~20,000 rows, and toAlbums()
                    // groups/sums/sorts every one of them.
                    queryCatching {
                        withContext(Dispatchers.Default) {
                            app.mediaRepository.queryAll().toAlbums()
                        }
                    }.fold(
                        onSuccess = { albums = it },
                        onFailure = { albumsFailed = true },
                    )
                }
                val loaded = albums
                if (albumsFailed) {
                    // The same shape the deck's own failure state uses (DeckScreen's
                    // DeckMessage): centred, capped at a readable measure, one ghost
                    // action. These two were the app's last Material widgets.
                    SwipeyScreen {
                        Column(
                            Modifier.align(Alignment.Center).widthIn(max = 420.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SwipeyText(
                                Copy.LOAD_FAILED,
                                style = SwipeyTheme.typography.body,
                                color = SwipeyTheme.colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(SwipeySpacing.lg))
                            SwipeyButton(
                                text = Copy.RETRY,
                                onClick = { attempt++ },
                                variant = SwipeyButtonVariant.Ghost,
                            )
                        }
                    }
                } else if (loaded == null) {
                    // A 2dp rule at the top edge rather than a spinner, matching the deck
                    // and both grids: nothing in Swipey rotates while you wait for it.
                    SwipeyScreen(contentPadding = PaddingValues(0.dp)) {
                        SwipeyProgressBar(
                            progress = null,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                } else {
                    AlbumsScreen(
                        albums = loaded,
                        onPick = { album -> navController.navigate(Routes.deck(bucketId = album.bucketId)) },
                    )
                }
            }

            composable(
                route = Routes.DECK,
                arguments = listOf(
                    navArgument("bucketId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("sort") { type = NavType.StringType; defaultValue = "NEWEST" },
                    navArgument("shuffle") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { backStackEntry ->
                val bucketIdArg = backStackEntry.arguments?.getLong("bucketId") ?: -1L
                val sortArg = backStackEntry.arguments?.getString("sort") ?: "NEWEST"
                val shuffleArg = backStackEntry.arguments?.getBoolean("shuffle") ?: false

                // Fix round 2 re-review, Critical 1 residue: this must run inside `remember`,
                // during composition, NOT inside a `LaunchedEffect`. `load()`'s synchronous
                // `_state` reset (DeckViewModel.kt) only closes the race if it lands before
                // `DeckScreen` below ever reads the ViewModel's StateFlow — and
                // `collectAsStateWithLifecycle` seeds its `State` from `flow.value` at the
                // moment DeckScreen itself composes. A `LaunchedEffect` here is an *effect*:
                // it is enqueued onto the same AndroidUiDispatcher trampoline as
                // `collectAsStateWithLifecycle`'s own collector, and — because DeckScreen's
                // terminal `LaunchedEffect(state.exhausted, state.markedCount)` is registered
                // first (it composes before the queued reset is drained) — it can read the
                // *previous* Deck entry's stale, possibly-exhausted-with-marks state and
                // bounce straight to Review before this reset ever takes effect (Sequence A).
                // `remember`'s initializer, unlike an effect, runs inline as this composable
                // executes — strictly before `DeckScreen(...)` below is even called — so by
                // the time DeckScreen composes and seeds its collected `State`, the reset has
                // already landed.
                remember(bucketIdArg, sortArg, shuffleArg) {
                    deckViewModel.load(
                        bucketId = bucketIdArg.takeIf { it != -1L },
                        sort = SortMode.valueOf(sortArg),
                        shuffle = shuffleArg,
                        // Routes carries only the shuffle flag, not a seed (Routes.deck has
                        // no seed parameter) — the seed is generated here, at the point the
                        // shuffled load actually happens, per the brief's
                        // "seed = System.currentTimeMillis()" note on Home's Shuffle entry.
                        seed = System.currentTimeMillis(),
                    )
                }

                DeckScreen(
                    viewModel = deckViewModel,
                    onReview = {
                        // The re-entry trap: without this popUpTo, Back from Review lands
                        // back on this same (still-exhausted) Deck entry, whose
                        // LaunchedEffect(state.exhausted, state.markedCount) fires again
                        // and bounces straight back to Review — an inescapable loop. Popping
                        // Deck off the back stack here means Back from Review skips it
                        // entirely, landing on whatever picked the album/sort instead.
                        navController.navigate(Routes.REVIEW) {
                            popUpTo(Routes.DECK) { inclusive = true }
                        }
                    },
                    onDone = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    },
                    // Fix round 2, Important 5: DeckScreen only calls this once the user
                    // has confirmed discarding pending marks in its own dialog (or there
                    // was nothing to confirm, since its BackHandler is disabled at
                    // markedCount == 0) — so a plain pop here reproduces exactly what an
                    // un-intercepted Back press would have done.
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.REVIEW) {
                // Re-derive the marked snapshot whenever markedCount changes (swipe, undo,
                // or ReviewScreen's own onUnmark all funnel through DeckViewModel.publish()),
                // rather than once at first composition.
                val markedCount = deckViewModel.state.collectAsStateWithLifecycle().value.markedCount
                val items = remember(markedCount) { deckViewModel.marked() }

                // Fix round 2, Critical 2: `preparing` only ever covers the short window
                // between the Commit tap and `trashLauncher.start()` actually running —
                // the suspend `prepareTrash()` Room write plus PendingIntent build. It is
                // reset in a `finally` on every path out of that window (success,
                // cancellation, or a thrown exception), so it can never be left stuck.
                // Once `start()` has run, `trashLauncher.inFlight` — `rememberSaveable`,
                // and already Compose-observable and recreation-safe on its own — is the
                // single source of truth for "is a commit in flight"; `committing` below
                // is the OR of the two rather than a second flag tracked independently
                // (the previous plain-`remember` `committing` could desync from
                // `inFlight` across an Activity recreation and latch true forever, since
                // nothing anywhere caught the exception that could also leave it stuck).
                var preparing by remember { mutableStateOf(false) }
                // Fix round 2 re-review, Minor: `rememberSaveable`, not plain `remember` —
                // unlike `preparing` (a genuinely sub-second window where losing it fails
                // safe), this is the terminal outcome of a failed commit. A rotation while
                // it's on screen must not silently erase the only record that the commit
                // failed and leave a live Commit button with no explanation.
                var commitError by rememberSaveable { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()

                val trashLauncher = rememberTrashLauncher(
                    repository = app.trashRepository,
                ) { report ->
                    scope.launch {
                        // Whole-branch review, I1. Spec §8.2: "Cancellation (RESULT_CANCELED)
                        // leaves marks intact and returns to Review"; §12 repeats it. This
                        // handler used to navigate to Result unconditionally, with
                        // popUpTo(REVIEW) { inclusive = true } — and Routes.REVIEW is
                        // navigated to from exactly one place (the Deck route's onReview),
                        // whose own popUpTo already removed Deck on the way in. So popping
                        // Review left no route back to the marks at all: they survived in
                        // the Activity-scoped DeckViewModel.session with nothing able to
                        // render them, until the next load() reset the session outright.
                        // Tapping "Don't allow" once cost the user every swipe they'd made.
                        //
                        // confirmedTrashed is the only field that means "MediaStore says
                        // these are now in the trash" — it is populated from the
                        // MarkTrashed resolutions, which require IS_TRASHED = 1, never from
                        // RESULT_OK. Empty therefore means nothing was trashed, whatever the
                        // cause: declined, backed out of, or a verification query that threw
                        // (TrashLauncher reports an empty report rather than crashing, and
                        // the PENDING_TRASH rows are untouched in that case). There is no
                        // outcome for Result to describe honestly, so stay put and say so.
                        // A partially-approved chunked commit has a non-empty
                        // confirmedTrashed and still goes to Result, which reports the
                        // shortfall via Copy.cancelled().
                        if (report.confirmedTrashed.isEmpty()) {
                            commitError = Copy.COMMIT_CANCELLED
                            // I4: a throw here would otherwise crash from inside the
                            // launcher's terminal callback. The count is cosmetic.
                            queryCatching { binCount = app.trashRepository.trashedCount() }
                            return@launch
                        }
                        // I4: binView()/trashedCount() are two more unguarded MediaStore +
                        // Room reads, on the path immediately after a successful commit.
                        // Falling back to the report alone still renders an honest Result —
                        // the expiry line is simply omitted (it is nullable already).
                        // Cleared first: this state outlives the destination, and leaving a
                        // previous commit's date in place would date *this* commit wrongly
                        // if the query below fails.
                        trashExpirySec = null
                        queryCatching {
                            // TrashLauncher.finish() already ran verifyAndResolve() to produce
                            // [report]; binView() is queried again here only to attach each
                            // newly-confirmed item's expiry date for ResultScreen.
                            val view = app.trashRepository.binView()
                            val confirmed = report.confirmedTrashed.toSet()
                            trashExpirySec = view.entries
                                .filter { it.record.mediaId in confirmed }
                                .mapNotNull { it.expiresAtSec }
                                .minOrNull()
                            binCount = app.trashRepository.trashedCount()
                        }
                        trashReport = report
                        navController.navigate(Routes.RESULT) {
                            // Prevents Back from Result returning to a Review grid full of
                            // items that were already just committed.
                            popUpTo(Routes.REVIEW) { inclusive = true }
                        }
                    }
                }

                ReviewScreen(
                    items = items,
                    onUnmark = { id -> deckViewModel.unmark(id) },
                    onCommit = {
                        commitError = null
                        preparing = true
                        scope.launch {
                            try {
                                // prepareTrash() writes the PENDING_TRASH rows before returning
                                // the intents — that ordering is what survives a process kill
                                // during the consent dialog, so it must not be split apart here.
                                val requests = app.trashRepository.prepareTrash(items, System.currentTimeMillis())
                                trashLauncher.start(requests)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // Fix round 2, Critical 2: without this, a throw here (e.g.
                                // prepareTrash's Room write) would leave nothing to reset
                                // `preparing` — trashLauncher.start() is never reached, so
                                // `inFlight` never becomes true either, and the user would
                                // be stuck looking at a live screen with no record of what
                                // happened and no way to tell the commit failed.
                                commitError = Copy.COMMIT_FAILED
                            } finally {
                                preparing = false
                            }
                        }
                    },
                    committing = preparing || trashLauncher.inFlight,
                    commitError = commitError,
                )
            }

            composable(Routes.RESULT) {
                val report = trashReport
                if (report == null) {
                    // Fix round 2, Important 3: now reachable only if the process was
                    // killed outright (no saved-state restore at all) or this route is
                    // entered with nothing ever committed — trashReport/trashExpirySec are
                    // `rememberSaveable` now, so an ordinary Activity recreation (rotation,
                    // dark mode, font size, locale) no longer lands here. Kept as a
                    // last-resort self-heal back to Home rather than rendering nothing.
                    LaunchedEffect(Unit) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                } else {
                    ResultScreen(
                        report = report,
                        earliestExpirySec = trashExpirySec,
                        onHome = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        },
                        onBin = { navController.navigate(Routes.BIN) },
                    )
                }
            }

            composable(Routes.BIN) {
                val binViewModel: BinViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { BinViewModel(app.trashRepository, app.mediaRepository, app) }
                    },
                )
                BinScreen(viewModel = binViewModel)
            }
        }
    }
}
