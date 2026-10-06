package io.github.isaiahyoder.recipebox.model

import kotlinx.serialization.Serializable

/** One ingredient or step line. A header line names a group, such as "For the sauce". */
@Serializable
data class RecipeLine(
    val text: String,
    val isHeader: Boolean = false,
)
