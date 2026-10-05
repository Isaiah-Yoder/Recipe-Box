package io.github.isaiahyoder.recipebox.ui

import android.text.format.DateUtils

/** Formats minutes as "45 min", "1 hr", or "6 hr 20 min". */
fun formatMinutes(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) return null
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$rest min"
        rest == 0 -> "$hours hr"
        else -> "$hours hr $rest min"
    }
}

/** Formats a past moment as "just now", "5 minutes ago", or "yesterday". */
fun formatAgo(millis: Long, now: Long = System.currentTimeMillis()): String =
    if (now - millis < DateUtils.MINUTE_IN_MILLIS) "just now"
    else DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString().lowercase()

/** Copies [text] for pasting into a message, such as an error to send for help. */
fun copyToClipboard(context: android.content.Context, label: String, text: String) {
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    // Android 13 and later show their own confirmation when something is copied.
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
    }
}
