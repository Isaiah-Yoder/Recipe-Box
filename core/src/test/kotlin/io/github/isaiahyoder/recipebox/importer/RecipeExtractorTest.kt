package io.github.isaiahyoder.recipebox.importer

import io.github.isaiahyoder.recipebox.model.RecipeLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class IsoDurationTest {
    @Test fun readsCommonForms() {
        assertEquals(90, IsoDuration.minutes("PT1H30M"))
        assertEquals(20, IsoDuration.minutes("P0DT0H20M"))
        assertEquals(380, IsoDuration.minutes("PT380M"))
        assertEquals(1440, IsoDuration.minutes("P1D"))
        assertNull(IsoDuration.minutes("PT0M"))
        assertNull(IsoDuration.minutes("about an hour"))
        assertNull(IsoDuration.minutes(null))
    }
}

/** These fixtures are written for the tests; they aren't copies of real pages. */
class RecipeExtractorTest {
    private fun page(jsonLd: String, head: String = "") =
        """<html><head>$head<script type="application/ld+json">$jsonLd</script></head>
           <body><p>A long story about my grandmother.</p></body></html>"""

    @Test fun readsAFlatRecipeObject() {
        val html = page(
            """
            {"@context":"https://schema.org","@type":"Recipe","name":"Test Soup &amp; Bread",
             "image":["https://example.com/a.jpg","https://example.com/b.jpg"],
             "recipeYield":["6","1 large pot"],"prepTime":"PT10M","cookTime":"PT40M","totalTime":"PT50M",
             "recipeIngredient":["2 cups broth","1 onion, diced"],
             "recipeInstructions":[{"@type":"HowToStep","text":"Chop the onion."},
                                   {"@type":"HowToStep","text":"Simmer for 40 minutes."}],
             "recipeCategory":["Soup"],"recipeCuisine":"American","keywords":"easy, weeknight"}
            """,
            head = """<meta property="og:site_name" content="Example Kitchen">""",
        )
        val r = RecipeExtractor.extract(html, "https://www.example.com/soup")!!
        assertEquals("Test Soup & Bread", r.title)
        assertEquals("https://example.com/a.jpg", r.imageUrl)
        assertEquals("Example Kitchen", r.siteName)
        assertEquals(6, r.servings)
        assertEquals("1 large pot", r.yieldText)
        assertEquals(10, r.prepMinutes)
        assertEquals(50, r.totalMinutes)
        assertEquals(listOf(RecipeLine("2 cups broth"), RecipeLine("1 onion, diced")), r.ingredients)
        assertEquals(listOf("Chop the onion.", "Simmer for 40 minutes."), r.steps.map { it.text })
        assertEquals(listOf("Soup"), r.categories)
        assertEquals(listOf("American"), r.cuisines)
        assertEquals(listOf("easy", "weeknight"), r.keywords)
        assertTrue(r.isComplete)
        assertTrue(r.ingredients.none { "grandmother" in it.text })
    }

    @Test fun findsARecipeInsideAGraphWithAnArrayType() {
        val html = page(
            """
            {"@context":"https://schema.org","@graph":[
              {"@type":"WebPage","name":"Not a recipe"},
              {"@type":["Recipe","NewsArticle"],"name":"Graph Cake",
               "image":{"@type":"ImageObject","url":"https://example.com/cake.jpg"},
               "recipeYield":12,
               "recipeIngredient":["1 cup sugar"],
               "recipeInstructions":"Mix everything.\nBake for 30 minutes."}]}
            """
        )
        val r = RecipeExtractor.extract(html, "https://example.com/cake")!!
        assertEquals("Graph Cake", r.title)
        assertEquals("https://example.com/cake.jpg", r.imageUrl)
        assertEquals(12, r.servings)
        assertEquals("example.com", r.siteName)
        assertEquals(listOf("Mix everything.", "Bake for 30 minutes."), r.steps.map { it.text })
    }

    @Test fun keepsSectionNamesAsHeaders() {
        val html = page(
            """
            {"@type":"Recipe","name":"Pie","recipeIngredient":["1 crust"],
             "recipeInstructions":[
               {"@type":"HowToSection","name":"Crust","itemListElement":[
                 {"@type":"HowToStep","text":"Roll the dough."}]},
               {"@type":"HowToSection","name":"Filling","itemListElement":[
                 {"@type":"HowToStep","text":"Stir the filling."}]}]}
            """
        )
        val steps = RecipeExtractor.extract(html, "https://example.com/pie")!!.steps
        assertEquals(
            listOf(
                RecipeLine("Crust", isHeader = true), RecipeLine("Roll the dough."),
                RecipeLine("Filling", isHeader = true), RecipeLine("Stir the filling."),
            ),
            steps,
        )
    }

    @Test fun fallsBackToMicrodata() {
        val html = """
            <html><body><div itemscope itemtype="https://schema.org/Recipe">
              <h1 itemprop="name">Micro Muffins</h1>
              <ul><li itemprop="recipeIngredient">2 cups flour</li><li itemprop="recipeIngredient">1 egg</li></ul>
              <ol itemprop="recipeInstructions"><li>Mix.</li><li>Bake.</li></ol>
            </div></body></html>
        """
        val r = RecipeExtractor.extract(html, "https://example.com/muffins")!!
        assertEquals("Micro Muffins", r.title)
        assertEquals(2, r.ingredients.size)
        assertEquals(listOf("Mix.", "Bake."), r.steps.map { it.text })
    }

    @Test fun returnsNullWithoutRecipeData() {
        assertNull(RecipeExtractor.extract("<html><body><p>Hello</p></body></html>", "https://example.com"))
    }

    @Test fun joinsStepNamesThatStartTheSentence() {
        assertEquals("Heat oven to 400° F.", RecipeExtractor.stepText("Heat oven", "to 400° F."))
        assertEquals("Cut into 8, and shape.", RecipeExtractor.stepText("Cut into 8", ", and shape."))
        assertEquals("Line a pan.", RecipeExtractor.stepText("Prep pan", "Line a pan."))
        assertEquals("Line a pan.", RecipeExtractor.stepText("Step 1", "Line a pan."))
        assertEquals("Line a pan with paper.", RecipeExtractor.stepText("Line a pan...", "Line a pan with paper."))
        assertEquals("Rest the dough", RecipeExtractor.stepText("Rest the dough", ""))
    }

    @Test fun prefersAWprmCardWithGroupsAndFullSteps() {
        val html = page(
            """{"@type":"Recipe","name":"Chili Recipe","recipeIngredient":["1 lb chicken","2 cups broth ((low sodium))","sliced avocado"],
               "recipeInstructions":[{"@type":"HowToStep","name":"Add","text":"chicken to the pot."}]}""",
        ).replace(
            "<body>",
            """<body><div class="wprm-recipe-container"><div class="wprm-recipe">
            <div class="wprm-recipe-ingredient-group"><ul>
              <li class="wprm-recipe-ingredient"><span class="wprm-recipe-ingredient-amount">1</span>
                <span class="wprm-recipe-ingredient-unit">lb</span> <span class="wprm-recipe-ingredient-name">chicken</span></li>
              <li class="wprm-recipe-ingredient"><span class="wprm-recipe-ingredient-amount">2</span>
                <span class="wprm-recipe-ingredient-unit">cups</span> <span class="wprm-recipe-ingredient-name">broth</span>
                <span class="wprm-recipe-ingredient-notes">(low sodium)</span></li></ul></div>
            <div class="wprm-recipe-ingredient-group"><h4 class="wprm-recipe-group-name">Toppings</h4><ul>
              <li class="wprm-recipe-ingredient"><span class="wprm-recipe-ingredient-name">sliced avocado</span></li></ul></div>
            <div class="wprm-recipe-instruction-group"><h4 class="wprm-recipe-group-name">Instructions</h4><ul>
              <li class="wprm-recipe-instruction"><div class="wprm-recipe-instruction-text"><strong>Add</strong> chicken to the pot.</div></li>
              <li class="wprm-recipe-instruction"><div class="wprm-recipe-instruction-text">Simmer 8 hours.</div></li></ul></div>
            </div></div>""",
        )
        val r = RecipeExtractor.extract(html, "https://example.com/chili")!!
        assertEquals("Chili", r.title)
        assertEquals(
            listOf(RecipeLine("1 lb chicken"), RecipeLine("2 cups broth (low sodium)"), RecipeLine("Toppings", isHeader = true), RecipeLine("sliced avocado")),
            r.ingredients,
        )
        assertEquals(listOf("Add chicken to the pot.", "Simmer 8 hours."), r.steps.map { it.text })
    }

    @Test fun readsATastyRecipesCard() {
        val html = page("""{"@type":"Recipe","name":"Cookies","recipeIngredient":["1 egg","1 cup flour","1 cup sugar"],"recipeInstructions":"Mix."}""")
            .replace(
                "<body>",
                """<body><div class="tasty-recipes-ingredients"><div class="tasty-recipes-ingredients-body">
                <h4>Dough</h4><ul><li><span class="tr-ingredient-checkbox-container"><input type="checkbox"></span>1 egg</li>
                <li>1 cup flour</li></ul><h4>Topping:</h4><ul><li>1 cup sugar</li></ul></div></div>
                <div class="tasty-recipes-instructions"><div class="tasty-recipes-instructions-body"><ol>
                <li><strong>Heat oven</strong> to 350°F.</li><li>Mix.</li></ol></div></div>""",
            )
        val r = RecipeExtractor.extract(html, "https://example.com/cookies")!!
        assertEquals(listOf("Dough", "1 egg", "1 cup flour", "Topping", "1 cup sugar"), r.ingredients.map { it.text })
        assertEquals(listOf(true, false, false, true, false), r.ingredients.map { it.isHeader })
        assertEquals(listOf("Heat oven to 350°F.", "Mix."), r.steps.map { it.text })
    }

    @Test fun skipsBrokenJsonAndUsesTheNextBlock() {
        val html = """<html><head>
            <script type="application/ld+json">{ not json </script>
            <script type="application/ld+json">{"@type":"Recipe","name":"OK","recipeIngredient":["1 egg"],"recipeInstructions":"Cook."}</script>
            </head></html>"""
        assertEquals("OK", RecipeExtractor.extract(html, "https://example.com")!!.title)
    }

    private val namesOnly = """
        {"@type":"Recipe","name":"Lamb Curry",
         "recipeIngredient":["Lamb","Salt","Onions","For the curry:","Oil","Curry leaves"],
         "recipeInstructions":"Cook the lamb. Add the onions."}
        """

    @Test fun readsAmountsFromThePageWhenTheDataHasOnlyNames() {
        val html = """<html><head><script type="application/ld+json">$namesOnly</script></head><body>
            <ul class="related"><li>Lamb Biryani</li><li>Onion Rings</li></ul>
            <ul class="ingredients">
              <li>250 Gram Lamb</li><li> to taste Salt</li><li>3 tbsp Onions, chopped</li>
              <li><b>For the curry:</b></li><li>2 tbsp Oil</li><li>10  Curry leaves</li>
            </ul></body></html>"""
        val r = RecipeExtractor.extract(html, "https://example.com/curry")!!
        assertEquals(
            listOf("250 Gram Lamb", "Salt, to taste", "3 tbsp Onions, chopped", "For the curry", "2 tbsp Oil", "10 Curry leaves"),
            r.ingredients.map { it.text },
        )
        assertTrue(r.ingredients[3].isHeader)
        // The saved copy keeps the list, so reading it again offline gives the same amounts.
        val again = RecipeExtractor.extract(r.pageSnapshot!!, "https://example.com/curry")!!
        assertEquals(r.ingredients, again.ingredients)
    }

    @Test fun keepsTheDataWhenNoPageListMatches() {
        val html = """<html><head><script type="application/ld+json">$namesOnly</script></head><body>
            <ul><li>1 Lamb Biryani</li><li>2 Onion Rings</li><li>3 Fish Curry</li><li>4 Salt Cod</li><li>5 Rice</li></ul>
            </body></html>"""
        val r = RecipeExtractor.extract(html, "https://example.com/curry")!!
        assertEquals("Lamb", r.ingredients.first().text)
    }
}

/**
 * Runs the extractor on real pages saved in the ignored testdata/private/pages
 * folder. The pages stay on this computer; the test skips itself without them.
 */
class PrivatePagesTest {
    @Test fun extractsEverySavedPage() {
        val folder = listOf(File("testdata/private/pages"), File("../testdata/private/pages"))
            .firstOrNull { it.isDirectory }
        val pages = folder?.listFiles { file -> file.extension == "html" }.orEmpty()
        assumeTrue("No private test pages", pages.isNotEmpty())
        for (page in pages) {
            val recipe = RecipeExtractor.extract(page.readText(), "https://www.allrecipes.com/${page.nameWithoutExtension}")
            assertNotNull("No recipe in ${page.name}", recipe)
            assertTrue("Incomplete recipe in ${page.name}", recipe!!.isComplete)
            assertNotNull("No image in ${page.name}", recipe.imageUrl)
            println("${page.name}: ${recipe.title} | ${recipe.ingredients.size} ingredients, ${recipe.steps.size} steps, " +
                "${recipe.servings} servings, ${recipe.totalMinutes} min, site=${recipe.siteName}, " +
                "categories=${recipe.categories}, cuisines=${recipe.cuisines}")
        }
    }
}

/**
 * Prints what the extractor reads from pages of her library saved in the
 * ignored testdata/private/pages/library folder, for checking by eye.
 */
class PrivateLibraryPagesTest {
    @Test fun extractsSavedLibraryPages() {
        val folder = listOf(File("testdata/private/pages/library"), File("../testdata/private/pages/library"))
            .firstOrNull { it.isDirectory }
        val pages = folder?.listFiles { file -> file.extension == "html" && file.length() > 50_000 }.orEmpty()
        assumeTrue("No private library pages", pages.isNotEmpty())
        val report = StringBuilder()
        for (page in pages.sortedBy { it.name }) {
            val recipe = RecipeExtractor.extract(page.readText(), "https://example.com/${page.nameWithoutExtension}")
            assertNotNull("No recipe in ${page.name}", recipe)
            report.appendLine("### ${page.name}: ${recipe!!.title} [${recipe.siteName}] yield=${recipe.yieldText}")
            recipe.ingredients.forEach { report.appendLine((if (it.isHeader) "  # " else "  - ") + it.text) }
            recipe.steps.forEach { report.appendLine((if (it.isHeader) "  ## " else "  > ") + it.text.take(120)) }
        }
        File(folder, "report.txt").writeText(report.toString())
    }
}
