package io.github.isaiahyoder.recipebox.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StallWatchdogTest {
    @Test fun keepsTheTopOfTheStackThenTheAppsOwnFrames() {
        val stack = (1..30).map { "android.os.Frame$it" } +
            listOf("io.github.isaiahyoder.recipebox.AppContainer.getPhotos(RecipeBoxApp.kt:85)") +
            (31..60).map { "android.os.Frame$it" }
        val kept = StallWatchdog.appFramesFirst(stack)
        assertEquals("android.os.Frame1", kept.first())
        assertEquals(13, kept.size)
        assertTrue(kept.last().contains("AppContainer.getPhotos"))
    }

    @Test fun describesEachPause() {
        val report = StallReport(at = 0, millis = 3_250, versionName = "0.5.2", stack = listOf("a.b.C.d(C.kt:1)"))
        val text = StallWatchdog.describe(listOf(report))
        assertTrue(text, text.startsWith("Recipe Box 0.5.2 paused for 3.3 s"))
        assertTrue(text, text.contains("  at a.b.C.d(C.kt:1)"))
    }
}
