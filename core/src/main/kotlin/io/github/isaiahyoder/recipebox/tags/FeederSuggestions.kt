package io.github.isaiahyoder.recipebox.tags

/** Feeder tags to offer for a category, from its name. */
object FeederSuggestions {
    /**
     * Feeder tags that fit a category she named herself, such as the Dessert
     * tag for "Dessert" or Main Dish for "Dinner". Used to offer feeders, never applied silently.
     */
    fun forCategory(categoryName: String, tagsInUse: Collection<String>): List<String> {
        val name = categoryName.lowercase().trim()
        val singular = name.removeSuffix("es").takeIf { name.endsWith("ches") || name.endsWith("shes") }
            ?: name.removeSuffix("s")
        val aliases = mapOf(
            "dinner" to listOf(AutoTagger.MAIN_DISH), "dinners" to listOf(AutoTagger.MAIN_DISH),
            "entrees" to listOf(AutoTagger.MAIN_DISH), "mains" to listOf(AutoTagger.MAIN_DISH),
            "lunch" to listOf(AutoTagger.MAIN_DISH), "sides" to listOf(AutoTagger.SIDE_DISH),
            "dough" to listOf(AutoTagger.DOUGH), "crusts" to listOf(AutoTagger.DOUGH),
            "sweets" to listOf(AutoTagger.DESSERT), "baking" to listOf(AutoTagger.BREAD, AutoTagger.DESSERT),
            "cakes" to listOf(AutoTagger.CAKES), "cupcakes" to listOf(AutoTagger.CAKES),
            "pies" to listOf(AutoTagger.PIES), "tarts" to listOf(AutoTagger.PIES),
            "muffins" to listOf(AutoTagger.QUICK_BREADS), "pancakes" to listOf(AutoTagger.PANCAKES),
            "recipe cards" to listOf(AutoTagger.FROM_CARD), "cards" to listOf(AutoTagger.FROM_CARD),
            "family recipes" to listOf(AutoTagger.FROM_CARD), "protein" to listOf(AutoTagger.HIGH_PROTEIN),
        )
        val candidates = aliases[name].orEmpty() + (AutoTagger.VOCABULARY.keys + tagsInUse).filter { tag ->
            val key = tag.lowercase()
            key == name || key == singular || key.removeSuffix("s") == singular
        }
        return candidates.distinct()
    }
}
