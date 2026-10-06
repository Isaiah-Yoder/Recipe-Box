package io.github.isaiahyoder.recipebox.importer

import kotlin.math.roundToInt

/** Reads schema.org durations such as "PT1H30M" or "P0DT0H20M". */
object IsoDuration {
    private val pattern = Regex(
        """^P(?:(\d+(?:\.\d+)?)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?$""",
        RegexOption.IGNORE_CASE,
    )

    /** Returns whole minutes, or null when the value is missing, zero, or unreadable. */
    fun minutes(text: String?): Int? {
        val match = pattern.matchEntire(text?.trim() ?: return null) ?: return null
        val (days, hours, minutes, seconds) = match.destructured
        val total = (days.toDoubleOrNull() ?: 0.0) * 1440 +
            (hours.toDoubleOrNull() ?: 0.0) * 60 +
            (minutes.toDoubleOrNull() ?: 0.0) +
            (seconds.toDoubleOrNull() ?: 0.0) / 60
        return total.roundToInt().takeIf { it > 0 }
    }
}
