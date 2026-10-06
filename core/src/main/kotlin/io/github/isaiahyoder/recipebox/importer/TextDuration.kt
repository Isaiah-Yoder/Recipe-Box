package io.github.isaiahyoder.recipebox.importer

/** Reads times written in words, such as "20 mins", "1 hr 30 min", or "1 1/2 hours". */
object TextDuration {
    private val number = """(\d+(?:\.\d+)?(?:\s+\d/\d)?|\d/\d|[½¼¾⅓⅔])"""
    private val part = Regex(
        """$number(?:\s*(?:-|–|to)\s*$number)?\s*(hours?|hrs?|h|minutes?|mins?|m)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Total minutes in [text], using the longer end of a range such as "10-12 mins"; null when none. */
    fun minutes(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        var total = 0.0
        var found = false
        for (match in part.findAll(text)) {
            val amount = value(match.groupValues[2].ifEmpty { match.groupValues[1] }) ?: continue
            val unit = match.groupValues[3].lowercase()
            total += if (unit.startsWith("h")) amount * 60 else amount
            found = true
        }
        return if (found && total > 0) Math.round(total).toInt() else null
    }

    private fun value(text: String): Double? {
        val trimmed = text.trim()
        when (trimmed) {
            "½" -> return 0.5
            "¼" -> return 0.25
            "¾" -> return 0.75
            "⅓" -> return 1.0 / 3
            "⅔" -> return 2.0 / 3
        }
        return trimmed.split(Regex("""\s+""")).sumOf { piece ->
            if ('/' in piece) {
                val (top, bottom) = piece.split('/').map { it.toDoubleOrNull() ?: return null }
                if (bottom == 0.0) return null else top / bottom
            } else {
                piece.toDoubleOrNull() ?: return null
            }
        }
    }
}
