package io.github.isaiahyoder.recipebox.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    @Test fun findsALinkInSharedText() {
        assertEquals(
            "https://www.example.com/recipe/123/soup/",
            Links.findUrl("Best Soup Ever - Example https://www.example.com/recipe/123/soup/"),
        )
        assertEquals("https://example.com/a", Links.findUrl("Look: https://example.com/a."))
        assertNull(Links.findUrl("no link here"))
    }

    @Test fun findsEveryLinkInPastedText() {
        val text = """
            https://example.com/a
            Soup https://example.com/b?utm_source=x
            https://example.com/a#comments and https://example.org/c.
        """.trimIndent()
        assertEquals(
            listOf("https://example.com/a", "https://example.com/b", "https://example.org/c"),
            Links.findAllUrls(text),
        )
    }

    @Test fun removesTrackingAndFragments() {
        assertEquals(
            "https://www.example.com/recipe/1/?page=2",
            Links.normalize("https://WWW.Example.com/recipe/1/?utm_source=share&page=2&fbclid=x#comments"),
        )
        assertEquals("https://example.com/", Links.normalize("https://example.com"))
    }
}
