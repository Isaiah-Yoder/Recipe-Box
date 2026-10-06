package io.github.isaiahyoder.recipebox.cards

/**
 * Sorts plain text read from card photos into a recipe without AI.
 *
 * Text recognition returns lines without meaning, so this uses the cues a
 * card usually has: a title line first, labels such as "Ingredients" and
 * "Directions", and ingredient lines that start with an amount. The result
 * always goes to the editor for her to check.
 */
object CardTextParser {
    private val sectionLabels = mapOf(
        "ingredients" to Section.INGREDIENTS,
        "ingredient" to Section.INGREDIENTS,
        "directions" to Section.STEPS,
        "instructions" to Section.STEPS,
        "method" to Section.STEPS,
        "steps" to Section.STEPS,
        "preparation" to Section.STEPS,
    )

    /** Printed words on blank recipe cards that aren't part of the recipe. */
    private val decorations = setOf("recipe", "recipes", "from the kitchen of", "notes")

    private val fieldLabel = Regex(
        """\b(title|serves|servings|yield|makes|prep\.?\s*time|cook\.?\s*time|bake\.?\s*time|total\.?\s*time)\s*:""",
        RegexOption.IGNORE_CASE,
    )

    private val startsWithAmount = Regex("""^(\d|[½¼¾⅓⅔⅛]|a\s|an\s|one\s|two\s|three\s|pinch|dash)""", RegexOption.IGNORE_CASE)

    private val dottedRule = Regex("""[._]{3,}""")
    private val dotBeforeWord = Regex("""(?<=[A-Za-z0-9])\.(?=[A-Za-z])""")

    private enum class Section { NONE, INGREDIENTS, STEPS }

    /** [photos] holds each photo's lines in reading order, front photo first. */
    fun parse(photos: List<List<String>>): CardRecipe {
        var title = ""
        var servings = ""
        var prepTime = ""
        var cookTime = ""
        val ingredients = mutableListOf<CardLine>()
        val steps = mutableListOf<CardLine>()
        var section = Section.NONE

        for ((index, photo) in photos.withIndex()) {
            // The back of a card usually holds the directions, so ingredients don't carry over to it.
            if (index > 0 && section == Section.INGREDIENTS) section = Section.NONE
            for (raw in photo) {
                // Template cards print dotted or underlined writing lines, which come through as runs of
                // dots, underscores, or single dots between words.
                val line = raw.replace(dottedRule, " ").replace(dotBeforeWord, " ").replace('_', ' ')
                    .replace(Regex("""\s+"""), " ").trim()
                if (line.isEmpty()) continue
                val plain = line.lowercase().trimEnd(':').trim()
                if (plain in decorations) continue
                sectionLabels[plain]?.let {
                    section = it
                    continue
                }

                val fields = labeledFields(line)
                if (fields.isNotEmpty()) {
                    for ((label, value) in fields) {
                        val key = label.lowercase().replace(Regex("""[\s.]"""), "")
                        when {
                            key == "title" -> title = value
                            key in setOf("serves", "servings", "yield", "makes") -> servings = value
                            key == "preptime" -> prepTime = value
                            key == "cooktime" || key == "baketime" -> cookTime = value
                        }
                    }
                    continue
                }

                if (title.isEmpty() && section == Section.NONE && !startsWithAmount.containsMatchIn(line)) {
                    title = line.trimEnd(':').trim()
                    continue
                }

                val isHeading = line.endsWith(":") && line.length <= 40
                val target = when (section) {
                    // A long line without an amount under "Ingredients" is the first step.
                    Section.INGREDIENTS -> if (!isHeading && !startsWithAmount.containsMatchIn(line) && line.length > 50) {
                        section = Section.STEPS
                        Section.STEPS
                    } else {
                        Section.INGREDIENTS
                    }
                    Section.STEPS -> Section.STEPS
                    // Without labels, amounts mark ingredients until the first step appears.
                    Section.NONE -> if (steps.isEmpty() && (isHeading || isIngredientLike(line))) Section.INGREDIENTS else Section.STEPS
                }
                if (target == Section.INGREDIENTS) {
                    ingredients += CardLine(line.trimEnd(':').trim().takeIf { isHeading } ?: line, isHeading)
                } else {
                    appendStep(steps, line, isHeading)
                }
            }
        }
        return CardRecipe(
            title = title,
            servings = servings,
            prepTime = prepTime,
            cookTime = cookTime,
            ingredients = ingredients,
            steps = steps,
        )
    }

    private fun isIngredientLike(line: String) = startsWithAmount.containsMatchIn(line) && line.length <= 70

    /** Splits "SERVES: 12 PREP TIME: 10 mins" into its labeled values. */
    private fun labeledFields(line: String): List<Pair<String, String>> {
        val matches = fieldLabel.findAll(line).toList()
        // A printed icon, such as a stopwatch, can come through as a stray character before the label.
        if (matches.isEmpty() || line.substring(0, matches.first().range.first).trim().length > 2) return emptyList()
        return matches.mapIndexed { index, match ->
            val end = matches.getOrNull(index + 1)?.range?.first ?: line.length
            match.groupValues[1] to line.substring(match.range.last + 1, end).trim()
        }
    }

    /** Joins a line that continues the previous step's sentence; otherwise starts a new step. */
    private fun appendStep(steps: MutableList<CardLine>, line: String, isHeading: Boolean) {
        val previous = steps.lastOrNull()
        val continues = previous != null && !previous.isHeading && !isHeading &&
            !previous.text.trimEnd().let { it.endsWith(".") || it.endsWith("!") || it.endsWith("?") } &&
            line.first().isLowerCase()
        if (continues) {
            steps[steps.lastIndex] = previous.copy(text = previous.text.trimEnd() + " " + line)
        } else {
            steps += CardLine(if (isHeading) line.trimEnd(':').trim() else line, isHeading)
        }
    }
}
