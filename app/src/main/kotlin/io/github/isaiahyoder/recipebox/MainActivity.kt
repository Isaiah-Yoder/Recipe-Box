package io.github.isaiahyoder.recipebox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.isaiahyoder.recipebox.ui.RecipeBoxNavHost
import io.github.isaiahyoder.recipebox.ui.RecipeBoxTheme
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
            RecipeBoxTheme {
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
