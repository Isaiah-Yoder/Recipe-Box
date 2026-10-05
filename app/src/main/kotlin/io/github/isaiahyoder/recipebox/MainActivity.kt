package io.github.isaiahyoder.recipebox

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.isaiahyoder.recipebox.ui.RecipeBoxNavHost
import io.github.isaiahyoder.recipebox.ui.RecipeBoxTheme
import io.github.isaiahyoder.recipebox.ui.isDark
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Text shared to the app, such as a recipe link from the browser, waiting to be imported. */
    private val sharedText = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a rotation the share was already handled; don't import it twice.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val themeMode by appContainer.settings.themeMode.collectAsStateWithLifecycle()
            val dark = themeMode.isDark()
            // Status bar icons follow the app's theme, which can differ from the phone's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            RecipeBoxTheme(dark) {
                RecipeBoxNavHost(sharedText)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            sharedText.value = intent.getStringExtra(Intent.EXTRA_TEXT)
        }
    }
}
