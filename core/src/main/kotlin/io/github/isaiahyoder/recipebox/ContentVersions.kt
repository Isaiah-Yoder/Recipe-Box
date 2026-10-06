package io.github.isaiahyoder.recipebox

/**
 * Versions of the rules that process saved recipes. Raising one in an update
 * makes the app apply the new rules to every saved recipe once, at startup.
 */
object ContentVersions {
    /** Automatic tag and suggestion rules. Applying them is quick and works offline. */
    const val TAG_RULES = 5

    /**
     * How recipes are read from web pages. Recipes with a saved page are read
     * again on the phone; the rest are downloaded again on Wi-Fi.
     */
    const val READING = 3

    /**
     * How ingredient lines are read into the ingredient index: names, amounts,
     * and units. Raising it rebuilds the index on the phone, which is quick.
     */
    const val INGREDIENT_INDEX = 1
}
