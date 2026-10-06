package io.github.isaiahyoder.recipebox.ingredients

import kotlinx.serialization.Serializable

/** The units amounts are shown in: US cups, ounces, and °F, or metric milliliters, grams, and °C. */
@Serializable
enum class UnitSystem { US, METRIC }
