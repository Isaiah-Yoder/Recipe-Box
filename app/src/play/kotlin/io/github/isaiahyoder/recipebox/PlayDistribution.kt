package io.github.isaiahyoder.recipebox

import androidx.compose.runtime.Composable

/** Google Play updates the app itself, so the app shows no update banner or Settings item. */
object PlayDistribution : AppDistribution {
    override fun onAppStart() = Unit

    @Composable
    override fun UpdateBanner() = Unit

    @Composable
    override fun SettingsItem() = Unit
}
