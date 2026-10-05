package io.github.isaiahyoder.recipebox.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {
    private fun release(tag: String, assets: String = APK, prerelease: Boolean = false) = """
        {"tag_name": "$tag", "name": "Recipe Box", "body": "Adds things.\r\n", "draft": false,
         "prerelease": $prerelease, "assets": [$assets], "author": {"login": "someone"}}
    """.trimIndent()

    @Test fun offersANewerRelease() {
        val update = Releases.newerThan("0.2.0", release("v0.3.0"))!!
        assertEquals("0.3.0", update.versionName)
        assertEquals("Adds things.", update.notes)
        assertEquals("https://example.test/recipe-box-0.3.0.apk", update.apkUrl)
        assertEquals(15_000_000L, update.apkSize)
    }

    @Test fun ignoresTheSameOrAnOlderRelease() {
        assertNull(Releases.newerThan("0.3.0", release("v0.3.0")))
        assertNull(Releases.newerThan("0.3.0", release("v0.2.0")))
    }

    @Test fun ignoresPrereleasesAndReleasesWithoutAnApk() {
        assertNull(Releases.newerThan("0.2.0", release("v0.3.0", prerelease = true)))
        assertNull(Releases.newerThan("0.2.0", release("v0.3.0", assets = """{"name": "notes.txt", "browser_download_url": "x"}""")))
    }

    @Test fun comparesNumbersNotText() {
        assertTrue(Releases.compare("0.10.0", "0.9.2") > 0)
        assertTrue(Releases.compare("1.0", "0.99.99") > 0)
        assertEquals(0, Releases.compare("0.3", "0.3.0"))
        assertEquals(0, Releases.compare("0.3.0-debug", "0.3.0"))
    }

    private companion object {
        const val APK = """{"name": "recipe-box-0.3.0.apk", "size": 15000000,
            "browser_download_url": "https://example.test/recipe-box-0.3.0.apk"}"""
    }
}
