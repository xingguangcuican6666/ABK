package com.abk.kernel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.screens.flash.FlashAction
import io.github.xingguangcuican6666.abk.ui.components.AbkAccordion
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.components.AbkListItem
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

internal data class SettingsAboutLink(val title: String, val url: String)

internal data class SettingsContributor(val username: String)

internal data class SettingsLicenseNotice(
    val name: String,
    val license: String,
    val source: String,
    val url: String? = null,
)

internal data class SettingsLicenseGroup(val titleRes: Int, val items: List<SettingsLicenseNotice>)

@Composable
internal fun SettingsAboutContent(
    topBarHeight: Dp,
    appName: String,
    intro: String,
    versionLabel: String,
    repositoryLinks: List<SettingsAboutLink>,
    onOpenLicenses: () -> Unit,
    contributors: List<SettingsContributor>,
    onOpenUrl: (String) -> Unit,
) {
    SettingsAboutPage(topBarHeight) {
        SettingsMainCard(appName) {
            Text(intro, color = AbkTheme.colors.inkMuted)
            Text(versionLabel, style = MaterialTheme.typography.titleSmall)
        }
        SettingsMainCard(stringResource(R.string.settings_repository_info)) {
            repositoryLinks.forEach { link ->
                SettingsLinkRow(link.title, link.url, link.url, onOpenUrl)
                AbkDivider()
            }
            Text(
                stringResource(R.string.settings_open_source_licenses_desc),
                color = AbkTheme.colors.inkMuted,
            )
            FlashAction(
                stringResource(R.string.settings_open_source_licenses),
                AbkIcons.Info,
                onOpenLicenses,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SettingsMainCard(stringResource(R.string.settings_contributors)) {
            Text(
                stringResource(R.string.settings_contributors_desc),
                color = AbkTheme.colors.inkMuted,
            )
            contributors.forEach { contributor ->
                SettingsLinkRow(
                    "@${contributor.username}",
                    "https://github.com/${contributor.username}",
                    "https://github.com/${contributor.username}",
                    onOpenUrl,
                )
                AbkDivider()
            }
        }
    }
}

@Composable
internal fun SettingsLicensesContent(
    topBarHeight: Dp,
    intro: String,
    groups: List<SettingsLicenseGroup>,
    expanded: Set<Int>,
    onExpandedChange: (Int, Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    SettingsAboutPage(topBarHeight) {
        SettingsMainCard(stringResource(R.string.settings_open_source_licenses)) {
            Text(intro, color = AbkTheme.colors.inkMuted)
        }
        groups.forEachIndexed { groupIndex, group ->
            val open = groupIndex in expanded
            AbkAccordion(
                title = stringResource(group.titleRes),
                expanded = open,
                onExpandedChange = { onExpandedChange(groupIndex, it) },
                modifier = Modifier.fillMaxWidth(),
                expandedDescription = stringResource(R.string.settings_license_group_expanded),
                collapsedDescription = stringResource(R.string.settings_license_group_collapsed),
            ) {
                group.items.forEachIndexed { index, notice ->
                    if (index > 0) AbkDivider()
                    SettingsLinkRow(
                        title = notice.name,
                        description =
                            listOfNotNull(notice.license, notice.source.takeIf { it.isNotBlank() })
                                .joinToString(" · "),
                        url = notice.url?.takeIf { it.isNotBlank() },
                        onOpenUrl = onOpenUrl,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsAboutPage(topBarHeight: Dp, content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(topBarHeight + 16.dp))
        content()
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SettingsLinkRow(
    title: String,
    description: String,
    url: String?,
    onOpenUrl: (String) -> Unit,
) {
    AbkListItem(
        title = title,
        description = description,
        onClick = url?.let { target -> { onOpenUrl(target) } },
        trailing = url?.let { { AbkIcon(AbkIcons.ArrowRight, null) } },
    )
}
