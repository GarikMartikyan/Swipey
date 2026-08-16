package com.swipey.app.ui.common

import kotlinx.coroutines.CancellationException

/**
 * Whole-branch review, I4. Spec §12 requires "*Cursor returns null → empty state with
 * retry; **never crash***". The null half was handled everywhere (`resolver.query(...)?.use`);
 * a **throw** was handled nowhere, and MediaProvider throws readily —
 * `IllegalArgumentException` on a projection or selection it doesn't like,
 * `SecurityException` on a permission race (a revoke landing between the access check and
 * the query). Room can throw too, on a corrupt or locked database.
 *
 * Every MediaStore/Room call site outside an existing `try/catch` funnels through here so
 * a throw becomes a user-visible empty or error state. The startup recovery pass is the
 * one that mattered most: a throw inside a `LaunchedEffect` propagates to the Recomposer
 * and takes the Activity down, i.e. a crash *loop* on every launch until the system trash
 * happens to drain.
 *
 * Data-safety note: swallowing here is safe by construction. `verifyAndResolve()` computes
 * every live row *before* it writes or deletes anything, so a throw aborts with the
 * database untouched — the local records that point at trashed files all survive. This
 * only converts "crash" into "try again", never "crash" into "silently lose the record".
 *
 * [CancellationException] is rethrown rather than caught: it is how coroutine cancellation
 * (a scrapped `LaunchedEffect`, a cleared ViewModel) propagates, and swallowing it would
 * keep cancelled work running and report a failure that never happened.
 */
suspend fun <T> queryCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
