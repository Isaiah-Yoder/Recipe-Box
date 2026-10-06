package io.github.isaiahyoder.recipebox

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import io.github.isaiahyoder.recipebox.importer.Links
import kotlinx.coroutines.launch

/**
 * Receives links shared from other apps, or text she selected with a link in
 * it, adds them to the import queue, and closes right away, so she stays in
 * the other app and can keep sharing.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val urls = Links.findAllUrls(sharedText(intent))
        if (urls.isEmpty()) {
            Toast.makeText(this, getString(R.string.library_share_no_link), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val queue = appContainer.importQueue
        lifecycleScope.launch {
            val added = queue.enqueue(urls)
            val message = when {
                added == 0 -> getString(R.string.library_share_already_queued)
                added == 1 -> getString(R.string.library_share_saving_one)
                else -> resources.getQuantityString(R.plurals.library_share_saving, added, added)
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * The text that may hold links. Apps share a link as plain or styled text,
     * sometimes with the link in the subject; selected text arrives through
     * the text selection menu.
     */
    private fun sharedText(intent: Intent?): String = when (intent?.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT),
            intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT),
        ).joinToString("\n")
        Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        else -> ""
    }

    companion object {
        private const val SHORTCUT_ID = "save-recipe"

        /** Matches the share target in res/xml/shortcuts.xml. */
        private const val SHARE_CATEGORY = "io.github.isaiahyoder.recipebox.category.SAVE_RECIPE"

        /**
         * Offers Recipe Box in the top row of Android's share sheet, where
         * frequent targets go, so a link is one tap from saved. Android 10 and
         * later show it; earlier versions list the app with the others.
         */
        fun publishShareShortcut(context: Context) {
            val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
                .setShortLabel(context.getString(R.string.share_target_label))
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
                .setCategories(setOf(SHARE_CATEGORY))
                .setLongLived(true)
                .build()
            runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
        }
    }
}
