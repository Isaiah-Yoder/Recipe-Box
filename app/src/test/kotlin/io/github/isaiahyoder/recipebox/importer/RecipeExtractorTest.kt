package io.github.isaiahyoder.recipebox.importer

import io.github.isaiahyoder.recipebox.data.RecipeLine
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

    @Test fun skipsBrokenJsonAndUsesTheNextBlock() {
        val html = """<html><head>
            <script type="application/ld+json">{ not json </script>
            <script type="application/ld+json">{"@type":"Recipe","name":"OK","recipeIngredient":["1 egg"],"recipeInstructions":"Cook."}</script>
            </head></html>"""
        assertEquals("OK", RecipeExtractor.extract(html, "https://example.com")!!.title)
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
