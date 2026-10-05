package io.github.isaiahyoder.recipebox.data

/**
 * Converts ingredient and step lines to and from the plain text she edits.
 *
 * One line is one ingredient or one step. A short line ending in a colon,
 * such as "For the sauce:", is a group heading.
 */
object RecipeText {
    private const val MAX_HEADER_LENGTH = 60

    fun toText(lines: List<RecipeLine>): String =
        lines.joinToString("\n") { if (it.isHeader) "${it.text}:" else it.text }

    fun fromText(text: String): List<RecipeLine> =
        text.lines()
            .map { it.trim().trimStart('•', '-', '*').trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                if (line.endsWith(":") && line.length <= MAX_HEADER_LENGTH) {
                    RecipeLine(line.dropLast(1).trim(), isHeader = true)
                } else {
                    RecipeLine(line)
                }
            }
}
