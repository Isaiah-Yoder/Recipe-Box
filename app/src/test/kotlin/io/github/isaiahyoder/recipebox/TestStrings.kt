package io.github.isaiahyoder.recipebox

/** Strings for unit tests: each text is its resource id and arguments, so tests don't depend on wording. */
object TestStrings : AppStrings {
    override fun get(id: Int, vararg args: Any): String = listOf("string-$id", *args).joinToString(" ")

    override fun plural(id: Int, count: Int, vararg args: Any): String = listOf("plural-$id", count, *args).joinToString(" ")
}
