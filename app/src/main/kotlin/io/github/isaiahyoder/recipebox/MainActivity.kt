package io.github.isaiahyoder.recipebox

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                RecipeBoxNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appContainer.distribution.onAppStart()
    }
}
