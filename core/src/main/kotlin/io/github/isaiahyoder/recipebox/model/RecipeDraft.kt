package io.github.isaiahyoder.recipebox.model

/**
 * The kinds of places a recipe can come from. Each import source has its own
 * kind, recorded with the recipe, so features can treat sources differently,
 * such as refreshing only web recipes. New sources, such as videos, social
 * posts, cookbook pages, or other recipe apps, add a kind here.
 */
object SourceKind {
    /** A recipe web page. */
    const val WEB = "web"

    /** Photos of a recipe card, read by AI or text recognition. */
    const val CARD = "card"

    /** Typed in the editor. */
    const val TYPED = "typed"
}

/**
 * A recipe read from any source, before it's saved. Every import source
 * produces one, and the app saves every draft the same way, with the same
 * duplicate check, tags, and categories.
 */
data class RecipeDraft(
    val title: String,
    /** A [SourceKind] name. */
    val sourceKind: String,
    /** The link it was read from, if any. */
    val sourceUrl: String? = null,
    /** The site, app, book, or person it's from, such as "Allrecipes" or "Recipe card". */
    val siteName: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val yieldText: String? = null,
    val servings: Int? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val ingredients: List<RecipeLine> = emptyList(),
    val steps: List<RecipeLine> = emptyList(),
    val notes: String = "",
    /** The source's own course, cuisine, and keyword labels, which automatic tags use. */
    val categories: List<String> = emptyList(),
    val cuisines: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    /** Raw structured data from a web page, so a later reader can read it again offline. */
    val rawJsonLd: String? = null,
    /** The page's structured data and recipe card, small enough to keep and read again later. */
    val pageSnapshot: String? = null,
    /** File names of photos the recipe was read from, such as card photos, front first. */
    val sourcePhotos: List<String> = emptyList(),
) {
    /** True when the recipe has everything the requirements call a recipe. */
    val isComplete: Boolean
        get() = title.isNotBlank() && ingredients.isNotEmpty() && steps.isNotEmpty()

    /** The title reduced to letters and digits, for spotting the same recipe saved twice. */
    val titleKey: String
        get() = titleKey(title)

    companion object {
        fun titleKey(title: String): String = title.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    }
}
