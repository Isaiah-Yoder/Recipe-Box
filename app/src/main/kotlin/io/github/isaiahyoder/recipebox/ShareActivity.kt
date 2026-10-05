package io.github.isaiahyoder.recipebox

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.isaiahyoder.recipebox.importer.Links
import kotlinx.coroutines.launch

/**
 * Receives links shared from other apps, adds them to the import queue, and
 * closes right away, so she stays in her browser and can keep sharing.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val urls = Links.findAllUrls(text)
        if (urls.isEmpty()) {
            Toast.makeText(this, "Recipe Box didn't find a link to save.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val queue = appContainer.importQueue
        lifecycleScope.launch {
            val added = queue.enqueue(urls)
            val message = when {
                added == 0 -> "That recipe is already in the Recipe Box queue."
                added == 1 -> "Saving to Recipe Box…"
                else -> "Saving $added recipes to Recipe Box…"
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
