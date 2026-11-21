package com.abk.kernel.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abk.kernel.R
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAboutUiTest {
    @get:Rule val compose = createComposeRule()
    private val context
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun text(id: Int) = context.getString(id)

    private fun row(title: String) = compose.onNode(hasClickAction() and hasText(title))

    private fun assertNotLink(title: String) {
        compose.onAllNodes(hasClickAction() and hasText(title)).assertCountEquals(0)
        compose.onNodeWithText(title).assertExists()
    }

    @Composable
    private fun NarrowContent(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, 2f),
            LocalLayoutDirection provides LayoutDirection.Rtl,
        ) {
            AbkTheme(reduceMotion = true) { Column(Modifier.width(320.dp)) { content() } }
        }
    }

    private val links =
        listOf(
            SettingsAboutLink("Source repository", "https://example.test/repo"),
            SettingsAboutLink("README", "https://example.test/repo/blob/main/README.md"),
        )

    private val groups =
        listOf(
            SettingsLicenseGroup(
                R.string.settings_license_group_repository,
                listOf(
                    SettingsLicenseNotice(
                        "Fixture Kernel",
                        "GPL-3.0",
                        "LICENSE",
                        "https://example.test/license",
                    ),
                    SettingsLicenseNotice(
                        "Bridge header",
                        "GPL-2.0",
                        "app/src/main/cpp/uapi/bridge.h",
                    ),
                    SettingsLicenseNotice("Blank source", "MIT", "", "  "),
                ),
            ),
            SettingsLicenseGroup(
                R.string.settings_license_group_web,
                listOf(SettingsLicenseNotice("webpack", "MIT", "web/package-lock.json")),
            ),
        )

    @Test
    fun aboutPageShowsVersionLinksAndEveryContributorInOriginalOrder() {
        val opened = mutableListOf<String>()
        compose.setContent {
            NarrowContent {
                SettingsAboutContent(
                    topBarHeight = 0.dp,
                    appName = "Fixture App",
                    intro = "Fixture intro",
                    versionLabel = "Current version: 9.9.9",
                    repositoryLinks = links,
                    onOpenLicenses = {},
                    contributors =
                        listOf(SettingsContributor("alpha"), SettingsContributor("beta-user")),
                    onOpenUrl = { opened += it },
                )
            }
        }
        compose.onNodeWithText("Fixture App").assertExists()
        compose.onNodeWithText("Fixture intro").assertExists()
        compose.onNodeWithText("Current version: 9.9.9").assertExists()
        row("README").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("https://example.test/repo/blob/main/README.md"), opened)
        }
        compose.onNodeWithText("@alpha").performScrollTo().assertExists()
        compose.onNodeWithText("https://github.com/alpha").assertExists()
        compose.onNodeWithText("@beta-user").performScrollTo().assertExists()
        row("@beta-user").performClick()
        compose.runOnIdle { assertEquals("https://github.com/beta-user", opened.last()) }
    }

    @Test
    fun licensesEntryOpensSubPageWithoutOpeningAUrl() {
        var opened = 0
        var licenses = 0
        compose.setContent {
            NarrowContent {
                SettingsAboutContent(
                    topBarHeight = 0.dp,
                    appName = "Fixture App",
                    intro = "Fixture intro",
                    versionLabel = "v",
                    repositoryLinks = links,
                    onOpenLicenses = { licenses++ },
                    contributors = emptyList(),
                    onOpenUrl = { opened++ },
                )
            }
        }
        row(text(R.string.settings_open_source_licenses)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, licenses)
            assertEquals(0, opened)
        }
    }

    @Test
    fun collapsedGroupsHideNoticesUntilExpandedAndDoNotDropAnyOfThem() {
        val opened = mutableListOf<String>()
        compose.setContent {
            NarrowContent {
                var expanded by rememberSaveable { mutableStateOf(emptySet<Int>()) }
                SettingsLicensesContent(
                    topBarHeight = 0.dp,
                    intro = "Fixture intro",
                    groups = groups,
                    expanded = expanded,
                    onExpandedChange = { index, open ->
                        expanded = if (open) expanded + index else expanded - index
                    },
                    onOpenUrl = { opened += it },
                )
            }
        }
        compose.onNodeWithText("Fixture Kernel").assertDoesNotExist()
        row(text(R.string.settings_license_group_repository)).performScrollTo().performClick()
        compose.onNodeWithText("Fixture Kernel").performScrollTo().assertExists()
        compose.onNodeWithText("GPL-3.0 · LICENSE").assertExists()
        assertNotLink("Bridge header")
        compose.onNodeWithText("GPL-2.0 · app/src/main/cpp/uapi/bridge.h").assertExists()
        assertNotLink("Blank source")
        compose.onNodeWithText("MIT").assertExists()
        row(text(R.string.settings_license_group_web)).performScrollTo().assertExists()
        compose.onNodeWithText("webpack").assertDoesNotExist()
        compose.runOnIdle { assertEquals(emptyList<String>(), opened) }
    }

    @Test
    fun noticeWithUrlOpensExactlyThatUrlAndBlankUrlIsNotClickable() {
        val opened = mutableListOf<String>()
        compose.setContent {
            NarrowContent {
                SettingsLicensesContent(
                    topBarHeight = 0.dp,
                    intro = "Fixture intro",
                    groups = groups,
                    expanded = setOf(0),
                    onExpandedChange = { _, _ -> },
                    onOpenUrl = { opened += it },
                )
            }
        }
        row("Fixture Kernel").performScrollTo().assertIsEnabled().performClick()
        assertNotLink("Bridge header")
        assertNotLink("Blank source")
        compose.runOnIdle { assertEquals(listOf("https://example.test/license"), opened) }
    }

    @Test
    fun everyRequestedSubtitleRuleIsAppliedToRealLicenseData() {
        val repository =
            SettingsLicenseGroup(
                R.string.settings_license_group_repository,
                listOf(SettingsLicenseNotice("Named", "Apache-2.0", "file.c")),
            )
        compose.setContent {
            NarrowContent {
                SettingsLicensesContent(
                    topBarHeight = 0.dp,
                    intro = "i",
                    groups = listOf(repository),
                    expanded = setOf(0),
                    onExpandedChange = { _, _ -> },
                    onOpenUrl = {},
                )
            }
        }
        compose.onNodeWithText("Apache-2.0 · file.c").assertExists()
    }

    @Test
    fun expandedGroupsSurviveStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            NarrowContent {
                SettingsLicensesContent(
                    topBarHeight = 0.dp,
                    intro = "i",
                    groups = groups,
                    expanded = rememberSaveable { mutableStateOf(setOf(1)) }.value,
                    onExpandedChange = { _, _ -> },
                    onOpenUrl = {},
                )
            }
        }
        compose.onNodeWithText("webpack").performScrollTo().assertExists()
        compose.onNodeWithText("Fixture Kernel").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("webpack").performScrollTo().assertExists()
        compose.onNodeWithText("Fixture Kernel").assertDoesNotExist()
    }
}
