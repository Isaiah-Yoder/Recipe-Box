package io.github.isaiahyoder.recipebox.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but lets cancellation through. Work that stops because
 * she left a screen, or because WorkManager stopped a job, isn't a failure,
 * so it must not be recorded as one or make a fallback run.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
