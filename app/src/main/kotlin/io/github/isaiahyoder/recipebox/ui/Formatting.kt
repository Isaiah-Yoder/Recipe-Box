package io.github.isaiahyoder.recipebox.ui

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
