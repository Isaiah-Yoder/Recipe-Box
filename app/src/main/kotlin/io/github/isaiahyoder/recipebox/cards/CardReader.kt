package io.github.isaiahyoder.recipebox.cards

import io.github.isaiahyoder.recipebox.AppStrings
import io.github.isaiahyoder.recipebox.model.RecipeDraft
import io.github.isaiahyoder.recipebox.util.runCatchingCancellable
import io.github.isaiahyoder.recipebox.settings.AppSettings
import java.io.File

/**
 * Which reader read a card, shown to her as "Read with Gemini". Readers are
 * an open set, so a build can add one, such as a Gemini reader that runs
 * through the developer's own cloud project.
 */
data class CardReaderKind(val id: String, val label: String) {
    companion object {
        val GEMINI = CardReaderKind("gemini", "Gemini")
        val ON_DEVICE_AI = CardReaderKind("on-device", "on-device AI")
        val TEXT_RECOGNITION = CardReaderKind("text", "basic text recognition")
    }
}

/** [problems] lists readers that were tried first and why they didn't work. */
data class CardReadResult(val recipe: CardRecipe, val kind: CardReaderKind, val problems: List<String>)

/** One way to read a recipe from card photos. */
interface RecipeCardReader {
    val kind: CardReaderKind

    /** Whether it can run now, such as when a key is set or the on-device model is ready. */
    fun isAvailable(): Boolean

    suspend fun read(photos: List<File>): CardRecipe
}

/** Her own Gemini key's reader, available when she has entered a key. */
class GeminiKeyCardReader(
    private val settings: AppSettings,
    private val gemini: GeminiCardReader,
    private val strings: AppStrings,
) : RecipeCardReader {
    override val kind = CardReaderKind.GEMINI

    override fun isAvailable() = settings.geminiKey.value != null

    override suspend fun read(photos: List<File>): CardRecipe =
        gemini.read(settings.geminiKey.value ?: throw CardReadException("There's no Gemini key."), photos)
}

/**
 * Reads one recipe from its card photos, trying [readers] in order, most
 * accurate first, and skipping any that can't run now. The last reader is
 * the fallback that always runs. Whatever is read, she checks it in the
 * editor before saving.
 */
class CardReader(private val readers: List<RecipeCardReader>, private val strings: AppStrings) {
    init {
        require(readers.isNotEmpty()) { "At least one card reader is needed." }
    }

    /** The reader that would run first right now. */
    fun firstKind(): CardReaderKind = (readers.firstOrNull { it.isAvailable() } ?: readers.last()).kind

    suspend fun read(photos: List<File>, onStep: (CardReaderKind) -> Unit): CardReadResult {
        val problems = mutableListOf<String>()
        for ((index, reader) in readers.withIndex()) {
            val isFallback = index == readers.lastIndex
            if (!isFallback && !reader.isAvailable()) continue
            onStep(reader.kind)
            runCatchingCancellable { reader.read(photos) }
                .onSuccess { return CardReadResult(it, reader.kind, problems) }
                .onFailure { problems += it.message ?: "${reader.kind.label.replaceFirstChar(Char::titlecase)} didn't work." }
        }
        throw CardReadException(problems.joinToString(" "))
    }
}

/** A read card waiting in the editor. Kept in memory between the scan and editor screens. */
data class CardDraft(val draft: RecipeDraft, val kind: CardReaderKind?, val problems: List<String>) {
    /** The card photos, front first. */
    val photos: List<String> get() = draft.sourcePhotos
}

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
