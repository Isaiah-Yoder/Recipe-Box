package io.github.isaiahyoder.recipebox.grocery

/**
 * Store sections, in the order a typical trip passes them. The app shows each
 * section's name from its string resources.
 */
enum class StoreSection {
    PRODUCE,
    MEAT_SEAFOOD,
    DAIRY_EGGS,
    BAKERY,
    PANTRY,
    SPICES,
    FROZEN,
    OTHER,
    ;

    companion object {
        fun fromName(name: String?): StoreSection? = entries.firstOrNull { it.name == name }

        /**
         * Guesses a section from an ingredient's cleaned name. [originalText]
         * is the full line, which can say "frozen" or "canned" when the name
         * doesn't. Her own corrections, stored separately, always win.
         */
        fun guess(nameKey: String, originalText: String = nameKey): StoreSection {
            val original = originalText.lowercase()
            fun has(vararg words: String) = words.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(nameKey) }
            return when {
                "frozen" in original -> FROZEN
                // Packaged versions of fresh things: chicken broth, tomato sauce, canned beans.
                has("broth", "stock", "bouillon", "sauce", "paste", "soup", "peanut butter", "salsa") ||
                    Regex("""\b(can|cans|canned|jar|jars)\b""").containsMatchIn(original) -> PANTRY
                has("powder", "seasoning", "extract", "dried", "paprika", "bay leaf", "salt", "black pepper",
                    "cumin", "cinnamon", "nutmeg", "oregano", "thyme", "allspice", "cayenne", "turmeric",
                    "coriander", "cardamom", "red pepper flake", "chili flake", "vanilla", "ground ginger",
                    "ground clove", "whole clove", "ground mustard", "ground sage") || nameKey == "clove" -> SPICES
                has("chicken", "beef", "pork", "bacon", "sausage", "turkey", "ham", "steak", "lamb", "salmon",
                    "shrimp", "crab", "fish", "tuna", "cod", "tilapia", "chorizo", "prosciutto", "pancetta",
                    "brisket", "veal", "scallop", "lobster", "anchovy", "ground meat") -> MEAT_SEAFOOD
                has("milk", "butter", "cheese", "cream", "yogurt", "egg", "buttermilk", "half-and-half",
                    "parmesan", "cheddar", "mozzarella", "ricotta", "feta", "sour cream") -> DAIRY_EGGS
                has("bread", "bun", "roll", "tortilla", "bagel", "pita", "croissant", "baguette", "naan",
                    "english muffin", "brioche") -> BAKERY
                has("onion", "garlic", "potato", "tomato", "lettuce", "carrot", "celery", "lemon", "lime",
                    "apple", "banana", "berry", "strawberry", "blueberry", "raspberry", "avocado", "cucumber",
                    "mushroom", "spinach", "cabbage", "corn", "asparagus", "green onion", "ginger", "zucchini",
                    "squash", "broccoli", "cauliflower", "kale", "parsley", "cilantro", "basil", "mint", "dill",
                    "rosemary", "chive", "shallot", "leek", "orange", "peach", "pear", "grape", "bell pepper",
                    "jalapeno", "jalapeño", "poblano", "serrano", "pepper", "radish", "arugula", "herb",
                    "sprout", "green bean", "pea", "eggplant", "fennel", "beet", "watermelon", "melon",
                    "pineapple", "mango", "cherry") -> PRODUCE
                has("flour", "sugar", "rice", "pasta", "noodle", "spaghetti", "macaroni", "oil", "vinegar",
                    "bean", "honey", "syrup", "baking soda", "baking powder", "yeast", "oat", "cornstarch",
                    "chocolate", "chocolate chip", "cocoa", "nut", "almond", "walnut", "pecan", "peanut", "raisin",
                    "ketchup", "mustard", "mayonnaise", "chickpea", "lentil", "cracker", "breadcrumb", "panko",
                    "cereal", "coconut", "wine", "water", "molasses", "gelatin") -> PANTRY
                else -> OTHER
            }
        }
    }
}
