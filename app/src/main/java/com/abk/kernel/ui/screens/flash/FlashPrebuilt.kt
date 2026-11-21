@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens.flash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.data.model.KernelSupport
import com.abk.kernel.data.model.PrebuiltGkiAsset
import com.abk.kernel.data.model.PrebuiltGkiRelease
import com.abk.kernel.ui.blur.blurredCardBackground
import com.abk.kernel.ui.blur.blurredCardSurfaceColor
import com.abk.kernel.ui.components.AbkInlineLoadingPill
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.utils.DownloadUtils
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkCheckbox
import io.github.xingguangcuican6666.abk.ui.components.AbkCluster
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
import io.github.xingguangcuican6666.abk.ui.components.AbkOption
import io.github.xingguangcuican6666.abk.ui.components.AbkSelect
import io.github.xingguangcuican6666.abk.ui.components.AbkTabItem
import io.github.xingguangcuican6666.abk.ui.components.AbkTabs
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

@Composable
internal fun FlashContentTabs(active: FlashContentTab, onSelect: (FlashContentTab) -> Unit) {
    AbkTabs(
        items = FlashContentTab.entries.map { AbkTabItem(it.name, stringResource(it.labelRes)) },
        selectedId = active.name,
        onSelect = { onSelect(FlashContentTab.valueOf(it)) },
        scrollable = true,
    )
}

@Composable
internal fun PrebuiltReleaseListHeader(
    releaseCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    ExpressiveSectionCard(
        title = stringResource(R.string.flash_prebuilt_gki),
        subtitle = stringResource(R.string.flash_prebuilt_list_desc, releaseCount),
        icon = AbkIcons.Download,
    ) {
        FlashAction(
            stringResource(R.string.refresh),
            AbkIcons.Refresh,
            onRefresh,
            loading = isLoading,
        )
    }
}

@Composable
internal fun PrebuiltReleaseCard(release: PrebuiltGkiRelease, onClick: () -> Unit) {
    AbkCard(
        modifier = Modifier.fillMaxWidth().blurredCardBackground(AbkShapes.Large),
        onClick = onClick,
        containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised),
    ) {
        ArtifactHeader(
            icon = AbkIcons.Download,
            title = release.name,
            subtitle =
                "${release.tagName} · ${releaseDateLabel(release.publishedAt, stringResource(R.string.flash_unknown_date))}",
        )
        AbkCluster(Modifier.fillMaxWidth()) {
            AbkBadge(
                text =
                    if (release.assetCount > 0) {
                        stringResource(R.string.flash_asset_count, release.assetCount)
                    } else {
                        stringResource(R.string.flash_asset_load_later)
                    },
                dot = false,
            )
            AbkBadge(stringResource(R.string.flash_manual_download), dot = false)
            AbkBadge(stringResource(R.string.flash_filter_by_release), dot = false)
        }
    }
}

@Composable
internal fun PrebuiltReleaseDetailHeader(
    release: PrebuiltGkiRelease,
    sourceCount: Int,
    visibleCount: Int,
    onBack: () -> Unit,
    onShowParameters: () -> Unit,
    onRefresh: () -> Unit,
) {
    ExpressiveSectionCard(
        title = release.name,
        subtitle =
            "${release.tagName} · ${releaseDateLabel(release.publishedAt, stringResource(R.string.flash_unknown_date))}",
        icon = AbkIcons.Download,
    ) {
        Text(
            stringResource(R.string.flash_visible_assets_count, visibleCount, sourceCount),
            style = MaterialTheme.typography.bodySmall,
            color = AbkTheme.colors.inkMuted,
        )
        AbkCluster(Modifier.fillMaxWidth()) {
            AbkIconButton(AbkIcons.Back, stringResource(R.string.flash_back), onBack)
            FlashAction(
                stringResource(R.string.flash_parameter_details),
                AbkIcons.Controls,
                onShowParameters,
            )
            FlashAction(stringResource(R.string.refresh), AbkIcons.Refresh, onRefresh)
        }
    }
}

@Composable
internal fun PrebuiltGkiFilterCard(
    filter: PrebuiltGkiFilter,
    onFilterChange: (PrebuiltGkiFilter) -> Unit,
) {
    val androidOptions = remember { listOf("") + KernelSupport.androidVersions() }
    val kernelOptions = remember { listOf("") + KernelSupport.kernelVersions() }
    val subLevelOptions =
        remember(filter.androidVersion, filter.kernelVersion) {
            listOf("") + prebuiltSubLevelOptions(filter.androidVersion, filter.kernelVersion)
        }
    val patchOptions =
        remember(filter.androidVersion, filter.kernelVersion, filter.subLevel) {
            listOf("") +
                prebuiltPatchOptions(filter.androidVersion, filter.kernelVersion, filter.subLevel)
        }

    fun updateFilter(next: PrebuiltGkiFilter) {
        onFilterChange(sanitizePrebuiltFilter(next))
    }

    ExpressiveSectionCard(
        title = stringResource(R.string.flash_filters),
        subtitle = stringResource(R.string.flash_filters_desc),
        icon = AbkIcons.Controls,
    ) {
        PrebuiltDropdownField(
            stringResource(R.string.build_android_version),
            filter.androidVersion,
            androidOptions,
            onSelect = { updateFilter(filter.copy(androidVersion = it)) },
        )
        PrebuiltDropdownField(
            stringResource(R.string.build_kernel_version),
            filter.kernelVersion,
            kernelOptions,
            onSelect = { updateFilter(filter.copy(kernelVersion = it)) },
        )
        PrebuiltDropdownField(
            stringResource(R.string.flash_minor_version),
            filter.subLevel,
            subLevelOptions,
            onSelect = { updateFilter(filter.copy(subLevel = it)) },
        )
        PrebuiltDropdownField(
            stringResource(R.string.runtime_patch_level),
            filter.osPatchLevel,
            patchOptions,
            onSelect = { updateFilter(filter.copy(osPatchLevel = it)) },
        )
        AbkCheckbox(
            state = ToggleableState(filter.onlyMatches),
            onClick = { updateFilter(filter.copy(onlyMatches = !filter.onlyMatches)) },
            label = stringResource(R.string.flash_only_matching_assets),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PrebuiltDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    AbkSelect(
        label = label,
        selectedId = value,
        placeholder = prebuiltOptionLabel(value),
        options = options.distinct().map { AbkOption(it, prebuiltOptionLabel(it)) },
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun LoadingRow(text: String) {
    AbkInlineLoadingPill(
        text = text,
        modifier = Modifier.padding(vertical = 12.dp),
        compact = false,
    )
}

@Composable
internal fun PrebuiltGkiAssetCard(
    asset: PrebuiltGkiAsset,
    recommended: Boolean,
    downloadedFiles: List<DownloadedArtifact>,
    progress: Int?,
    onDownload: () -> Unit,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean,
) {
    val type = prebuiltArtifactType(asset)
    val animatedProgress by
        animateFloatAsState(
            targetValue = ((progress ?: 0) / 100f).coerceIn(0f, 1f),
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "prebuilt-gki-download",
        )
    AbkCard(
        modifier = Modifier.fillMaxWidth().blurredCardBackground(AbkShapes.Large),
        containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised),
    ) {
        ArtifactHeader(
            icon = artifactIcon(type),
            title = asset.name,
            subtitle = "${asset.releaseTag} · ${DownloadUtils.formatSize(asset.sizeBytes)}",
            chip = if (recommended) stringResource(R.string.flash_device_recommended) else null,
        )
        when {
            progress != null -> FlashDownloadProgress(progress, animatedProgress)
            downloadedFiles.isEmpty() ->
                FlashAction(
                    label = stringResource(R.string.flash_download_prebuilt_gki),
                    icon = AbkIcons.Download,
                    onClick = onDownload,
                    style = AbkButtonStyle.Primary,
                )
            else ->
                downloadedFiles.forEach { file ->
                    AbkDivider()
                    DownloadedOutputRow(
                        artifact = file,
                        onCopyPath = { onCopyPath(file) },
                        onInstall = { onInstall(file) },
                        onFlash = { onFlash(file) },
                        onDelete = { onDelete(file) },
                        allowRootActions = allowRootActions,
                    )
                }
        }
    }
}

internal fun prebuiltArtifactType(asset: PrebuiltGkiAsset): ArtifactType {
    val type = DownloadUtils.classifyArtifact(asset.name)
    return if (type == ArtifactType.OTHER) ArtifactType.KERNEL_PACKAGE else type
}

internal fun defaultPrebuiltFilter(): PrebuiltGkiFilter =
    PrebuiltGkiFilter(androidVersion = "", kernelVersion = "", subLevel = "", osPatchLevel = "")

internal fun sanitizePrebuiltFilter(filter: PrebuiltGkiFilter): PrebuiltGkiFilter {
    val subOptions = prebuiltSubLevelOptions(filter.androidVersion, filter.kernelVersion)
    val subLevel = filter.subLevel.takeIf { it.isBlank() || it in subOptions }.orEmpty()
    val patchOptions = prebuiltPatchOptions(filter.androidVersion, filter.kernelVersion, subLevel)
    val patch = filter.osPatchLevel.takeIf { it.isBlank() || it in patchOptions }.orEmpty()
    return filter.copy(subLevel = subLevel, osPatchLevel = patch)
}

internal fun prebuiltSubLevelOptions(androidVersion: String, kernelVersion: String): List<String> =
    KernelSupport.entries
        .filter { androidVersion.isBlank() || it.androidVersion == androidVersion }
        .filter { kernelVersion.isBlank() || it.kernelVersion == kernelVersion }
        .map { it.subLevel }
        .distinct()
        .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }

internal fun prebuiltPatchOptions(
    androidVersion: String,
    kernelVersion: String,
    subLevel: String,
): List<String> =
    KernelSupport.entries
        .filter { androidVersion.isBlank() || it.androidVersion == androidVersion }
        .filter { kernelVersion.isBlank() || it.kernelVersion == kernelVersion }
        .filter { subLevel.isBlank() || it.subLevel == subLevel }
        .map { it.osPatchLevel }
        .distinct()
        .sortedBy(::patchMonthIndexForUi)

@Composable
internal fun prebuiltOptionLabel(value: String): String =
    value.ifBlank { stringResource(R.string.flash_unlimited) }

internal fun patchMonthIndexForUi(value: String): Int {
    val parts = value.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return Int.MAX_VALUE
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return Int.MAX_VALUE
    return year * 12 + month
}

internal fun releaseDateLabel(value: String, unknownDate: String): String =
    value.takeIf { it.length >= 10 }?.take(10) ?: unknownDate

internal fun isPrebuiltGkiCandidateUi(asset: PrebuiltGkiAsset): Boolean {
    val lower = asset.name.lowercase()
    val type = DownloadUtils.classifyArtifact(asset.name)
    if (!lower.endsWith(".bundle.zip")) return false
    return type in
        setOf(ArtifactType.KERNEL_PACKAGE, ArtifactType.KERNEL_IMG, ArtifactType.ANYKERNEL3) ||
        listOf("gki", "kernel", "boot", "anykernel", "ak3").any { lower.contains(it) }
}

internal fun prebuiltAssetMatchesFilter(
    asset: PrebuiltGkiAsset,
    filter: PrebuiltGkiFilter,
): Boolean {
    val haystack = prebuiltHaystack(asset)
    return prebuiltAndroidMatches(haystack, filter.androidVersion) &&
        prebuiltKernelMatches(haystack, filter.kernelVersion, filter.subLevel) &&
        prebuiltTextMatches(haystack, filter.osPatchLevel)
}

internal fun recommendedPrebuiltAssetIdsForUi(
    assets: List<PrebuiltGkiAsset>,
    recommended: KernelBuildConfig?,
): Set<Long> {
    if (recommended == null) return emptySet()
    val scored =
        assets
            .map { it to prebuiltRecommendationScoreForUi(it, recommended) }
            .filter { it.second > 0 }
    val best = scored.maxOfOrNull { it.second } ?: return emptySet()
    return scored.filter { it.second == best }.map { it.first.id }.toSet()
}

internal fun prebuiltRecommendationScoreForUi(
    asset: PrebuiltGkiAsset,
    recommended: KernelBuildConfig?,
): Int {
    recommended ?: return 0
    if (recommended.subLevel == "X") return 0
    val haystack = prebuiltHaystack(asset)
    val kernelSub =
        Regex(
                """(^|[^0-9])${Regex.escape(recommended.kernelVersion)}[.-]?${Regex.escape(recommended.subLevel)}([^0-9]|$)"""
            )
            .containsMatchIn(haystack)
    if (!kernelSub) return 0

    val androidNumber = recommended.androidVersion.removePrefix("android")
    val hasAndroid =
        haystack.contains(recommended.androidVersion.lowercase()) ||
            haystack.contains("android-$androidNumber") ||
            haystack.contains("a$androidNumber")
    val hasPatch =
        recommended.osPatchLevel.isNotBlank() &&
            haystack.contains(recommended.osPatchLevel.lowercase())
    return 10 + (if (hasAndroid) 5 else 0) + (if (hasPatch) 8 else 0)
}

internal fun prebuiltHaystack(asset: PrebuiltGkiAsset): String =
    listOf(asset.name, asset.releaseTag, asset.releaseName, asset.releaseBody)
        .joinToString(" ")
        .lowercase()
        .replace('_', '-')

internal fun prebuiltAndroidMatches(haystack: String, value: String): Boolean {
    val android = value.trim().lowercase().replace('_', '-')
    if (android.isBlank()) return true
    val number = android.removePrefix("android").removePrefix("-")
    return haystack.contains(android) ||
        (number.isNotBlank() &&
            (haystack.contains("android$number") ||
                haystack.contains("android-$number") ||
                haystack.contains("a$number")))
}

internal fun prebuiltKernelMatches(
    haystack: String,
    kernelVersion: String,
    subLevel: String,
): Boolean {
    val kernel = kernelVersion.trim()
    val sub = subLevel.trim()
    if (kernel.isBlank()) return true
    if (sub.isBlank()) return haystack.contains(kernel.lowercase())
    return Regex("""(^|[^0-9])${Regex.escape(kernel)}[.-]?${Regex.escape(sub)}([^0-9]|$)""")
        .containsMatchIn(haystack)
}

internal fun prebuiltTextMatches(haystack: String, value: String): Boolean {
    val text = value.trim().lowercase().replace('_', '-')
    return text.isBlank() || haystack.contains(text)
}
