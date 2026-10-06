package io.github.isaiahyoder.recipebox.data

import io.github.isaiahyoder.recipebox.model.RecipeDraft

/** A draft from any source as a new library recipe. The one mapping every source's recipes go through. */
fun RecipeDraft.toEntity(now: Long): RecipeEntity = RecipeEntity(
    title = title,
    sourceKind = sourceKind,
    sourceUrl = sourceUrl,
    siteName = siteName,
    description = description,
    imageUrl = imageUrl,
    yieldText = yieldText,
    servings = servings,
    prepMinutes = prepMinutes,
    cookMinutes = cookMinutes,
    totalMinutes = totalMinutes,
    ingredients = ingredients,
    steps = steps,
    notes = notes,
    siteCategories = categories,
    siteCuisines = cuisines,
    siteKeywords = keywords,
    rawJsonLd = rawJsonLd,
    cardPhotos = sourcePhotos,
    createdAt = now,
    updatedAt = now,
)
