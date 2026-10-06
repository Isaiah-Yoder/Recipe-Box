package io.github.isaiahyoder.recipebox.cards

import io.github.isaiahyoder.recipebox.TestStrings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CardReaderTest {
    private class FakeReader(
        override val kind: CardReaderKind,
        private val available: Boolean = true,
        private val failure: String? = null,
    ) : RecipeCardReader {
        var calls = 0

        override fun isAvailable() = available

        override suspend fun read(photos: List<File>): CardRecipe {
            calls++
            failure?.let { throw CardReadException(it) }
            return CardRecipe(title = kind.label)
        }
    }

    private val cloud = CardReaderKind("cloud", "cloud AI")

    @Test fun usesTheFirstReaderThatCanRun() = runBlocking {
        val first = FakeReader(cloud, available = false)
        val second = FakeReader(CardReaderKind.ON_DEVICE_AI)
        val last = FakeReader(CardReaderKind.TEXT_RECOGNITION)
        val reader = CardReader(listOf(first, second, last), TestStrings)

        assertEquals(CardReaderKind.ON_DEVICE_AI, reader.firstKind())
        val steps = mutableListOf<CardReaderKind>()
        val result = reader.read(emptyList()) { steps += it }

        assertEquals(CardReaderKind.ON_DEVICE_AI, result.kind)
        assertEquals(listOf(CardReaderKind.ON_DEVICE_AI), steps)
        assertEquals(0, first.calls)
        assertEquals(0, last.calls)
    }

    @Test fun fallsBackAndKeepsWhyEarlierReadersFailed() = runBlocking {
        val reader = CardReader(
            listOf(
                FakeReader(cloud, failure = "The cloud is busy."),
                FakeReader(CardReaderKind.TEXT_RECOGNITION, available = false),
            ),
            TestStrings,
        )
        // The last reader is the fallback, so it runs even when it reports unavailable.
        val result = reader.read(emptyList()) {}
        assertEquals(CardReaderKind.TEXT_RECOGNITION, result.kind)
        assertEquals(listOf("The cloud is busy."), result.problems)
    }

    @Test fun reportsEveryProblemWhenNothingWorks() = runBlocking {
        val reader = CardReader(
            listOf(
                FakeReader(cloud, failure = "The cloud is busy."),
                FakeReader(CardReaderKind.TEXT_RECOGNITION, failure = "No text found."),
            ),
            TestStrings,
        )
        val error = runCatching { reader.read(emptyList()) {} }.exceptionOrNull()
        assertTrue(error is CardReadException)
        assertEquals("The cloud is busy. No text found.", error!!.message)
    }
}
