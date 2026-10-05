package io.github.isaiahyoder.recipebox.cards

import io.github.isaiahyoder.recipebox.util.runCatchingCancellable
import io.github.isaiahyoder.recipebox.settings.AppSettings
import java.io.File

enum class CardReaderKind(val label: String) {
    GEMINI("Gemini"),
    ON_DEVICE_AI("on-device AI"),
    TEXT_RECOGNITION("basic text recognition"),
}

/** [problems] lists readers that were tried first and why they didn't work. */
data class CardReadResult(val recipe: CardRecipe, val kind: CardReaderKind, val problems: List<String>)

/**
 * Reads one recipe from its card photos, trying the most accurate reader
 * first: Gemini when she has a key, then on-device AI, then plain text
 * recognition. Whatever is read, she checks it in the editor before saving.
 */
class CardReader(
    private val settings: AppSettings,
    private val gemini: GeminiCardReader,
    private val nano: NanoCardReader,
    private val text: TextCardReader,
) {
    suspend fun read(photos: List<File>, onStep: (CardReaderKind) -> Unit): CardReadResult {
        val problems = mutableListOf<String>()
        settings.geminiKey.value?.let { key ->
            onStep(CardReaderKind.GEMINI)
            runCatchingCancellable { gemini.read(key, photos) }
                .onSuccess { return CardReadResult(it, CardReaderKind.GEMINI, problems) }
                .onFailure { problems += it.message ?: "Gemini didn't work." }
        }
        if (nano.status.value == OnDeviceAiStatus.READY) {
            onStep(CardReaderKind.ON_DEVICE_AI)
            runCatchingCancellable { nano.read(photos) }
                .onSuccess { return CardReadResult(it, CardReaderKind.ON_DEVICE_AI, problems) }
                .onFailure { problems += it.message ?: "On-device AI didn't work." }
        }
        onStep(CardReaderKind.TEXT_RECOGNITION)
        val recipe = runCatchingCancellable { text.read(photos) }.getOrElse { error ->
            problems += error.message ?: "Text recognition didn't work."
            throw CardReadException(problems.joinToString(" "))
        }
        return CardReadResult(recipe, CardReaderKind.TEXT_RECOGNITION, problems)
    }
}

/** A read card waiting in the editor. Kept in memory between the scan and editor screens. */
data class CardDraft(val recipe: CardRecipe, val photos: List<String>, val kind: CardReaderKind?, val problems: List<String>)

object CardDrafts {
    private val drafts = mutableMapOf<Long, CardDraft>()
    private var nextId = 1L

    @Synchronized
    fun put(draft: CardDraft): Long = nextId++.also { drafts[it] = draft }

    @Synchronized
    fun get(id: Long): CardDraft? = drafts[id]

    @Synchronized
    fun remove(id: Long) {
        drafts.remove(id)
    }
}
