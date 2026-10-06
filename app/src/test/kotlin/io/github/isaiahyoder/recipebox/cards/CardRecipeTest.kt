package io.github.isaiahyoder.recipebox.cards

import io.github.isaiahyoder.recipebox.data.toEntity
import io.github.isaiahyoder.recipebox.model.RecipeLine
import io.github.isaiahyoder.recipebox.model.SourceKind
import io.github.isaiahyoder.recipebox.importer.TextDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRecipeTest {
    @Test fun readsAiAnswersWithFencesAndUnknownFields() {
        val answer = """
            ```json
            {"title": "Pancakes", "servings": "10-12 pancakes", "prepTime": "5 mins", "cookTime": "20 mins",
             "ingredients": [{"text": "Dry:", "isHeading": true}, {"text": "2 cups flour (250 g)", "isHeading": false}],
             "steps": [{"text": "Mix.", "isHeading": false}], "notes": "", "confidence": "high"}
            ```
        """.trimIndent()
        val recipe = CardJson.parse(answer)!!
        assertEquals("Pancakes", recipe.title)
        assertEquals(2, recipe.ingredients.size)
        assertNull(CardJson.parse("I couldn't read that."))
    }

    @Test fun becomesARecipeWithItsPhotos() {
        val entity = CardRecipe(
            title = "Pancakes:",
            servings = "10-12 pancakes",
            prepTime = "5 mins",
            cookTime = "20 mins",
            ingredients = listOf(CardLine("Ingredients", isHeading = true), CardLine("DRY:", isHeading = true), CardLine("• 2 cups flour")),
            steps = listOf(CardLine("Mix."), CardLine("   ")),
        ).toDraft(listOf("card-1.jpg", "card-2.jpg")).toEntity(now = 5)
        assertEquals("Pancakes", entity.title)
        assertEquals("10-12 pancakes", entity.yieldText)
        assertEquals(10, entity.servings)
        assertEquals(25, entity.totalMinutes)
        assertEquals(listOf(RecipeLine("Dry", isHeader = true), RecipeLine("2 cups flour")), entity.ingredients)
        assertEquals(listOf(RecipeLine("Mix.")), entity.steps)
        assertEquals(listOf("card-1.jpg", "card-2.jpg"), entity.cardPhotos)
        assertEquals(CardRecipe.SITE_NAME, entity.siteName)
        assertEquals(SourceKind.CARD, entity.sourceKind)
    }

    @Test fun theReadersCourseAndCuisineBecomeLabelsForTags() {
        val entity = CardRecipe(title = "Grandma's Special", course = "Dessert", cuisine = "Eastern European")
            .toDraft(emptyList()).toEntity(now = 5)
        assertEquals(listOf("Dessert"), entity.siteCategories)
        assertEquals(listOf("Eastern European"), entity.siteCuisines)
        val unsure = CardRecipe(title = "Soup", course = " ", cuisine = "").toDraft(emptyList()).toEntity(now = 5)
        assertTrue(unsure.siteCategories.isEmpty())
        assertTrue(unsure.siteCuisines.isEmpty())
    }

    /** The answer layout's lists must stay within the names the tagger knows. */
    @Test fun theAnswerLayoutOffersOnlyTagNames() {
        val schema = kotlinx.serialization.json.Json.parseToJsonElement(
            listOf(java.io.File("src/main/assets/cards/schema.json"), java.io.File("app/src/main/assets/cards/schema.json"))
                .first { it.exists() }.readText()
        )
        fun enumOf(key: String) = (schema as kotlinx.serialization.json.JsonObject)["properties"]!!
            .let { it as kotlinx.serialization.json.JsonObject }[key]!!
            .let { it as kotlinx.serialization.json.JsonObject }["enum"]!!
            .let { it as kotlinx.serialization.json.JsonArray }
            .map { (it as kotlinx.serialization.json.JsonPrimitive).content }
            .filter { it.isNotEmpty() }
            .toSet()
        val vocabulary = io.github.isaiahyoder.recipebox.tags.AutoTagger.VOCABULARY
        val courses = vocabulary.filterValues { it == io.github.isaiahyoder.recipebox.tags.TagGroup.COURSE }.keys -
            io.github.isaiahyoder.recipebox.tags.AutoTagger.DOUGH
        val cuisines = vocabulary.filterValues { it == io.github.isaiahyoder.recipebox.tags.TagGroup.CUISINE }.keys
        assertEquals(courses, enumOf("course"))
        assertEquals(cuisines, enumOf("cuisine"))
    }

    @Test fun readsWrittenDurations() {
        assertEquals(20, TextDuration.minutes("20 mins"))
        assertEquals(90, TextDuration.minutes("1 hr 30 min"))
        assertEquals(90, TextDuration.minutes("1 1/2 hours"))
        assertEquals(12, TextDuration.minutes("10-12 mins"))
        assertEquals(5, TextDuration.minutes("5mins"))
        assertNull(TextDuration.minutes("2 medium onions"))
        assertNull(TextDuration.minutes(""))
    }

    @Test fun sortsTemplateCardText() {
        val recipe = CardTextParser.parse(
            listOf(
                listOf(
                    "Recipe",
                    "TITLE: Banana Bread",
                    "SERVES: 10 PREP.TIME: 15 mins COOK TIME: 60 mins",
                    "INGREDIENTS:",
                    "DRY:",
                    "2 cups flour 250g",
                    "1 tsp baking soda",
                ),
                listOf(
                    "Directions",
                    "Preheat oven to 350°F",
                    "In a large bowl, mash the bananas,",
                    "then stir in the butter and eggs.",
                    "Bake for 60 mins at 350°F",
                ),
            )
        )
        assertEquals("Banana Bread", recipe.title)
        assertEquals("10", recipe.servings)
        assertEquals("15 mins", recipe.prepTime)
        assertEquals("60 mins", recipe.cookTime)
        assertEquals(CardLine("DRY", isHeading = true), recipe.ingredients.first())
        assertEquals(3, recipe.ingredients.size)
        assertEquals(
            listOf(
                "Preheat oven to 350°F",
                "In a large bowl, mash the bananas, then stir in the butter and eggs.",
                "Bake for 60 mins at 350°F",
            ),
            recipe.steps.map { it.text },
        )
    }

    @Test fun cleansDottedWritingLines() {
        val recipe = CardTextParser.parse(
            listOf(listOf("TITLE: Pancakes", "Ö PREP.TIME: 5.mins COOK TIME: 20 mins", "Ingredients", "2.cups.milk____", "1.5 cups flour"))
        )
        assertEquals("5 mins", recipe.prepTime)
        assertEquals("20 mins", recipe.cookTime)
        assertEquals(listOf("2 cups milk", "1.5 cups flour"), recipe.ingredients.map { it.text })
    }

    @Test fun sortsUnlabeledCardsByAmounts() {
        val recipe = CardTextParser.parse(
            listOf(
                listOf("Chocolate Frosting:", "Ingredients", "1 cup butter (225 g)", "1/2 cup cocoa"),
                listOf("Beat the butter and cocoa together until smooth", "then add the sugar slowly.", "Taste and adjust"),
            )
        )
        assertEquals("Chocolate Frosting", recipe.title)
        assertEquals(listOf("1 cup butter (225 g)", "1/2 cup cocoa"), recipe.ingredients.map { it.text })
        assertEquals(2, recipe.steps.size)
        assertTrue(recipe.steps.first().text.endsWith("sugar slowly."))
    }
}
