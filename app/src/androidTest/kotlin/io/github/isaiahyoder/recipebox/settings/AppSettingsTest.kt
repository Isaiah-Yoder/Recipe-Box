package io.github.isaiahyoder.recipebox.settings

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun cleanUp() {
        context.deleteSharedPreferences(FILE)
        context.deleteSharedPreferences("$FILE-secrets")
    }

    @Test fun aKeySavedBefore052MovesOutOfTheBackedUpSettings() {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit(commit = true) {
            putString("gemini_api_key", " test-key ")
            putString("theme_mode", "DARK")
        }

        val settings = AppSettings(context, FILE)

        assertEquals("test-key", settings.geminiKey.value)
        val backedUp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        assertFalse(backedUp.contains("gemini_api_key"))
        assertEquals("DARK", backedUp.getString("theme_mode", null))
        assertEquals("test-key", context.getSharedPreferences("$FILE-secrets", Context.MODE_PRIVATE).getString("gemini_api_key", null))
    }

    @Test fun aNewKeyIsSavedOnlyInTheSecretsFile() {
        val settings = AppSettings(context, FILE)
        settings.setGeminiKey("another-key")

        assertFalse(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).contains("gemini_api_key"))
        assertEquals("another-key", AppSettings(context, FILE).geminiKey.value)
    }

    private companion object {
        const val FILE = "settings-test"
    }
}
