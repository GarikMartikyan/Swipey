package com.swipey.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.swipey.app.data.ResumePoint
import com.swipey.app.domain.SortMode
import com.swipey.app.ui.albums.SortChooserScreen
import com.swipey.app.ui.bin.BinScreen
import com.swipey.app.ui.bin.BinViewModel
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.common.queryCatching
import com.swipey.app.ui.common.rememberTrashLauncher
import com.swipey.app.ui.deck.DeckScreen
import com.swipey.app.ui.deck.DeckViewModel
import com.swipey.app.ui.deck.forgetVideoSoundChoice
import com.swipey.app.ui.home.HomeScreen
import com.swipey.app.ui.home.HomeViewModel
import com.swipey.app.ui.permission.PermissionGate
import com.swipey.app.ui.review.ReviewScreen
import com.swipey.app.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

/**
 * The composable root. Owns navigation and the one piece of state that outlives a single
 * destination: [DeckViewModel], shared by the Deck and Review routes (built once, scoped to
 * the Activity via the default [LocalViewModelStoreOwner][androidx.compose.ui.platform.LocalViewModelStoreOwner]
 * at this call site — SwipeyRoot sits outside every NavHost `composable{}` block, so it
 * isn't re-scoped to a NavBackStackEntry).
 *
 * Deck.load() rebuilds its session on every entry, without exception, which is what makes
 * Activity-lifetime reuse of the instance safe. A commit used to be the one way back into a
 * live session — it returned the user to the card they broke off from — and it goes to Home
 * now, so nothing skips the load any more; see `Routes.deck`.
 *
 * There used to be a third thing here: the last commit's [RecoveryReport] and expiry date,
 * hoisted because the Result route had no argument to carry them. That screen is gone — a
 * commit now hands the user straight back to whatever they were doing — and the state went
 * with it.
 */
@Composable
fun SwipeyRoot(app: SwipeyApp) {
    val navController = rememberNavController()
    var binCount by remember { mutableIntStateOf(0) }

    val deckViewModel: DeckViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DeckViewModel(app.mediaRepository, app.homePreferences) }
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
                // Home reads the whole gallery now — a hero, a cover per album, and the
                // first card of a shuffle — so it has a ViewModel of its own rather than
                // three LaunchedEffects each paying for queryAll() again. Scoped to this
                // NavBackStackEntry, which survives on the back stack for as long as Home
                // does, so the layout toggle and the loaded gallery outlive a trip into
                // the deck.
                val homeViewModel: HomeViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            HomeViewModel(app.mediaRepository, app.homePreferences)
                        }
                    },
                )

                // Refresh on every (re)entry — not just app start — so a restore done in
                // the Bin, or a commit done in Review, is reflected the moment the user
                // is back on Home. The gallery reload is what re-resolves the hero, the
                // album covers and (with a fresh seed) the shuffle's first card.
                //
                // I4: same Recomposer exposure as the recovery pass above. A Room read
                // that throws must leave the count stale, not take the Activity down —
                // HomeViewModel.load() does its own catching, inside the ViewModel.
                LaunchedEffect(Unit) {
                    homeViewModel.load()
                    queryCatching { binCount = app.trashRepository.trashedCount() }
                }

                HomeScreen(
                    viewModel = homeViewModel,
                    binCount = binCount,
                    // The hero's caption promises "newest first", so its tap says so
                    // explicitly rather than relying on Routes.deck's default.
                    onAllMedia = { navController.navigate(Routes.deck(sort = SortMode.NEWEST.name)) },
                    onSort = { navController.navigate(Routes.SORT) },
                    // The seed Home resolved its Shuffle thumbnail with, not a new one:
                    // this is the half of the handoff that makes that thumbnail true.
                    onShuffle = { seed -> navController.navigate(Routes.deck(shuffle = true, seed = seed)) },
                    // Every field of the bookmark goes back onto the route: the queue has to
                    // be dealt the same way it was dealt last time, or "the card after that
                    // one" names a different photograph. See `ResumePoint`.
                    onResume = { point ->
                        navController.navigate(
                            Routes.deck(
                                bucketId = point.bucketId,
                                sort = point.sort,
                                shuffle = point.shuffle,
                                seed = point.seed,
                                after = point.itemId,
                            ),
                        )
                    },
                    onAlbum = { album -> navController.navigate(Routes.deck(bucketId = album.bucketId)) },
                    onBin = { navController.navigate(Routes.BIN) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(Routes.SORT) {
                SortChooserScreen(
                    onPick = { mode -> navController.navigate(Routes.deck(sort = mode.name)) },
                )
            }

            composable(Routes.SETTINGS) {
                // Read from the store rather than from LocalSettings, so this screen is
                // reading the same object it writes and cannot show a value one frame
                // behind the one it just set.
                val settings by app.settingsPreferences.state.collectAsStateWithLifecycle()
                SettingsScreen(
                    settings = settings,
                    onTheme = app.settingsPreferences::setTheme,
                    onBinSide = app.settingsPreferences::setBinSide,
                    onHaptics = app.settingsPreferences::setHaptics,
                    onVideoSound = { on ->
                        app.settingsPreferences.setVideoSound(on)
                        // The run's own sound decision is thrown away, so the next clip
                        // takes the new setting. Without this, turning sound on here and
                        // going straight back to a deck that had been muted would leave it
                        // muted — a switch that appears not to work.
                        forgetVideoSoundChoice()
                    },
                )
            }

            composable(
                route = Routes.DECK,
                arguments = listOf(
                    navArgument("bucketId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("sort") { type = NavType.StringType; defaultValue = "NEWEST" },
                    navArgument("shuffle") { type = NavType.BoolType; defaultValue = false },
                    // Only consulted when shuffle is true. Every route string is built by
                    // Routes.deck, which always writes a seed, so the default here is a
                    // formality — but a deterministic one rather than a second clock read.
                    navArgument("seed") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("after") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { backStackEntry ->
                val bucketIdArg = backStackEntry.arguments?.getLong("bucketId") ?: -1L
                val sortArg = backStackEntry.arguments?.getString("sort") ?: "NEWEST"
                val shuffleArg = backStackEntry.arguments?.getBoolean("shuffle") ?: false
                val seedArg = backStackEntry.arguments?.getLong("seed") ?: 0L
                val afterArg = backStackEntry.arguments?.getLong("after") ?: -1L

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
                //
                remember(bucketIdArg, sortArg, shuffleArg, seedArg, afterArg) {
                    deckViewModel.load(
                        bucketId = bucketIdArg.takeIf { it != -1L },
                        sort = SortMode.valueOf(sortArg),
                        shuffle = shuffleArg,
                        // The seed now arrives on the route rather than being read off the
                        // clock here. Home resolves the shuffle to show a thumbnail of the
                        // card it opens on, and the only way that stays true is for this
                        // load to deal the seed Home used. As a side effect the shuffle is
                        // now stable across an Activity recreation — a rotation mid-session
                        // used to re-key this `remember` with a fresh clock reading and
                        // silently reshuffle the deck.
                        seed = seedArg,
                        // -1 is "from the top", which is every entry except Home's Recent.
                        startAfterId = afterArg.takeIf { it != -1L },
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
                        // The session has to stop listing what is now in the phone's trash
                        // before the deck is resumed on top of it: the marked count falls to
                        // zero, the grid stops offering photographs MediaStore will no longer
                        // serve, and the card on screen stays the card on screen. See
                        // SwipeSession.drop.
                        deckViewModel.dropCommitted(report.confirmedTrashed.toSet())

                        // I4: trashedCount() is an unguarded Room read on the path
                        // immediately after a successful commit. A throw here must leave the
                        // Bin's badge stale rather than take the Activity down.
                        queryCatching { binCount = app.trashRepository.trashedCount() }

                        // Where a Result screen used to be. It reported the count, the
                        // expiry and any shortfall, then offered "View Bin" and "Done" —
                        // and the honest reading of it was that it interrupted a session to
                        // tell the user something they had just watched happen.
                        //
                        // A commit ends the trip, whether or not the album was finished. It
                        // used to resume the deck when there was more to judge, on the
                        // reasoning that a commit is an interruption in the middle of a pass
                        // — but committing is a deliberate act with a system consent dialog
                        // in the middle of it, and coming out of that back onto a card reads
                        // as never having left. Home is where the deletion is legible: the
                        // Bin's count has gone up, the albums have been re-read, and the
                        // next pass is one tap away.
                        //
                        // popUpTo(HOME) { inclusive } rather than a pop: Review's own entry
                        // popped the Deck on the way in, so there is nothing here to unwind
                        // to, and re-entering Home is what re-runs its load and refreshes
                        // the count this commit just changed.
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
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
