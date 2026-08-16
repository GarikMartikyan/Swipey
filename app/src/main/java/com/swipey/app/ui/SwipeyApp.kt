package com.swipey.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.swipey.app.ui.common.rememberTrashLauncher
import com.swipey.app.ui.deck.DeckScreen
import com.swipey.app.ui.deck.DeckViewModel
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
 *    argument to carry (Routes.RESULT takes none) so it is hoisted here instead.
 */
@Composable
fun SwipeyRoot(app: SwipeyApp) {
    val navController = rememberNavController()
    var binCount by remember { mutableIntStateOf(0) }
    var trashReport by remember { mutableStateOf<RecoveryReport?>(null) }
    var trashExpirySec by remember { mutableStateOf<Long?>(null) }

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
        LaunchedEffect(Unit) {
            app.trashRepository.verifyAndResolve()
            binCount = app.trashRepository.trashedCount()
        }

        NavHost(navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                // Refresh on every (re)entry — not just app start — so a restore done in
                // the Bin, or a commit done in Review, is reflected the moment the user
                // is back on Home.
                LaunchedEffect(Unit) {
                    binCount = app.trashRepository.trashedCount()
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
                LaunchedEffect(Unit) {
                    // Off Main: queryAll() can return up to ~20,000 rows, and toAlbums()
                    // groups/sums/sorts every one of them.
                    albums = withContext(Dispatchers.Default) {
                        app.mediaRepository.queryAll().toAlbums()
                    }
                }
                val loaded = albums
                if (loaded == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
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

                LaunchedEffect(bucketIdArg, sortArg, shuffleArg) {
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
                )
            }

            composable(Routes.REVIEW) {
                // Re-derive the marked snapshot whenever markedCount changes (swipe, undo,
                // or ReviewScreen's own onUnmark all funnel through DeckViewModel.publish()),
                // rather than once at first composition.
                val markedCount = deckViewModel.state.collectAsStateWithLifecycle().value.markedCount
                val items = remember(markedCount) { deckViewModel.marked() }

                var committing by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                val trashLauncher = rememberTrashLauncher(
                    repository = app.trashRepository,
                ) { report ->
                    committing = false
                    scope.launch {
                        // TrashLauncher.finish() already ran verifyAndResolve() to produce
                        // [report]; binView() is queried again here only to attach each
                        // newly-confirmed item's expiry date for ResultScreen.
                        val view = app.trashRepository.binView()
                        trashExpirySec = view.entries
                            .filter { it.record.mediaId in report.confirmedTrashed }
                            .mapNotNull { it.expiresAtSec }
                            .minOrNull()
                        trashReport = report
                        binCount = app.trashRepository.trashedCount()
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
                        committing = true
                        scope.launch {
                            // prepareTrash() writes the PENDING_TRASH rows before returning
                            // the intents — that ordering is what survives a process kill
                            // during the consent dialog, so it must not be split apart here.
                            val requests = app.trashRepository.prepareTrash(items, System.currentTimeMillis())
                            trashLauncher.start(requests)
                        }
                    },
                    committing = committing,
                )
            }

            composable(Routes.RESULT) {
                val report = trashReport
                if (report == null) {
                    // Defensive only: reachable if the process died on this exact screen,
                    // since the report is held in plain `remember`, not restored state.
                    // Self-heals back to Home rather than rendering nothing.
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
