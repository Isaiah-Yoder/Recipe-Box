package io.github.isaiahyoder.recipebox.util

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsTest {
    @Test fun catchesOrdinaryFailures() {
        val result = runCatchingCancellable { error("broken") }
        assertTrue(result.isFailure)
        assertEquals("broken", result.exceptionOrNull()?.message)
    }

    @Test(expected = CancellationException::class)
    fun letsCancellationThrough() {
        runCatchingCancellable { throw CancellationException("left the screen") }
    }
}
