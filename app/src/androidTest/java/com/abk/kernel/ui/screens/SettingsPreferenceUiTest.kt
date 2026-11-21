package com.abk.kernel.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abk.kernel.R
import com.abk.kernel.utils.LocaleHelper
import io.github.xingguangcuican6666.abk.ui.components.AbkOption
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsPreferenceUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun draftsCommitOnFocusLossAndImeDoneWithoutTrimming() {
        var directory by mutableStateOf("/fixture/downloads")
        var mirror by mutableStateOf("https://mirror.example")
        val commits = mutableListOf<Pair<String, String>>()
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                Column {
                    SettingsTextDraftField(
                        directory,
                        {
                            commits += "directory" to it
                            directory = it
                        },
                        "Directory",
                        "Fixture directory",
                        "",
                    )
                    SettingsTextDraftField(
                        mirror,
                        {
                            commits += "mirror" to it
                            mirror = it
                        },
                        "Mirror",
                        "Fixture mirror",
                        "",
                    )
                }
            }
        }
        val pathField = compose.onNodeWithContentDescription("Directory")
        val mirrorField = compose.onNodeWithContentDescription("Mirror")
        pathField.requestFocus().performTextReplacement("/fixture/edited")
        compose.runOnIdle {
            directory = "/fixture/updated-by-host"
            assertEquals(emptyList<Pair<String, String>>(), commits)
        }
        pathField.assertTextContains("/fixture/edited")
        mirrorField.requestFocus()
        compose.runOnIdle { assertEquals(listOf("directory" to "/fixture/edited"), commits) }
        mirrorField.performTextReplacement(" https://new.example/ ")
        compose.runOnIdle { assertEquals(1, commits.size) }
        mirrorField.performImeAction()
        compose.runOnIdle {
            assertEquals(
                listOf("directory" to "/fixture/edited", "mirror" to " https://new.example/ "),
                commits,
            )
        }
        mirrorField.requestFocus().performImeAction()
        compose.runOnIdle { assertEquals(2, commits.size) }
    }

    @Test
    fun narrowRtlChoicesAndDirectoryButtonsDispatchOnlyExplicitActions() {
        var selected by mutableStateOf("first")
        var needsAccess by mutableStateOf(false)
        val events = mutableListOf<String>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                AbkTheme(reduceMotion = true) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        SettingsPreferenceChoices(
                            "Fixture preference",
                            listOf(
                                AbkOption("first", "First choice"),
                                AbkOption("second", "Second longer choice"),
                            ),
                            selected,
                        ) {
                            selected = it
                            events += it
                        }
                        SettingsDownloadDirectoryActions(
                            needsAccess,
                            { events += "choose" },
                            { events += "reset" },
                            { events += "access" },
                        )
                    }
                }
            }
        }
        val permission =
            compose.onNodeWithText(
                context.getString(R.string.settings_download_directory_storage_permission)
            )
        permission.assertDoesNotExist()
        compose.runOnIdle { assertEquals(emptyList<String>(), events) }
        val second = compose.onNode(isSelectable() and hasText("Second longer choice"))
        second.performScrollTo().performClick().assertIsSelected()
        compose
            .onNodeWithText(context.getString(R.string.settings_download_directory_choose))
            .performScrollTo()
            .performClick()
        compose
            .onNodeWithText(context.getString(R.string.settings_download_directory_reset))
            .performScrollTo()
            .performClick()
        compose.runOnIdle { needsAccess = true }
        permission.performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("second", "choose", "reset", "access"), events) }
    }

    @Test
    fun downloadThreadSliderPreservesRangeStepsAndExplicitChanges() {
        var count by mutableStateOf(32)
        val changes = mutableListOf<Int>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                AbkTheme(reduceMotion = true) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        SettingsDownloadThreadControl(count) {
                            changes += it
                            count = it
                        }
                    }
                }
            }
        }
        val slider =
            compose.onNodeWithContentDescription(
                context.getString(R.string.settings_download_threads)
            )
        fun assertRange(value: Int) {
            slider.assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(value.toFloat(), 1f..64f, 62),
                )
            )
            compose
                .onNodeWithText(context.getString(R.string.settings_download_threads_desc, value))
                .assertExists()
        }
        assertRange(32)
        compose.runOnIdle { assertEquals(emptyList<Int>(), changes) }
        listOf(64, 1, 17).forEach { value ->
            slider.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) {
                it(value.toFloat())
            }
            assertRange(value)
        }
        compose.runOnIdle { assertEquals(listOf(64, 1, 17), changes) }
    }

    @Test
    fun languageSelectionUsesHostValueWithoutChangingLocaleDuringComposition() {
        var language by mutableStateOf(LocaleHelper.LANG_EN)
        val changes = mutableListOf<String>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                AbkTheme(reduceMotion = true) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        LanguageSettingsItem(language) {
                            changes += it
                            language = it
                        }
                    }
                }
            }
        }
        fun option(label: Int) =
            compose.onNode(isSelectable() and hasText(context.getString(label)))
        option(R.string.settings_language_en).assertIsSelected()
        compose.runOnIdle { assertEquals(emptyList<String>(), changes) }
        option(R.string.settings_language_ru).performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(listOf(LocaleHelper.LANG_RU), changes)
            language = LocaleHelper.LANG_ZH
        }
        option(R.string.settings_language_zh).assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(LocaleHelper.LANG_RU), changes) }
    }

    @Test
    fun directoryActionSlotCommitsSelectionAndResetWithoutLosingHostUpdates() {
        var directory by mutableStateOf("/fixture/downloads")
        val commits = mutableListOf<String>()
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                SettingsTextDraftField(
                    value = directory,
                    onCommit = {
                        commits += it
                        directory = it
                    },
                    label = "Directory",
                    description = "Fixture directory",
                    placeholder = "",
                ) { commitDraft ->
                    SettingsDownloadDirectoryActions(
                        needsAllFilesAccess = false,
                        onChoose = { commitDraft("/fixture/chosen") },
                        onReset = { commitDraft("/fixture/default") },
                        onOpenAllFilesAccess = { error("Permission action must not be available") },
                    )
                }
            }
        }
        val field = compose.onNodeWithContentDescription("Directory")
        compose.runOnIdle {
            assertEquals(emptyList<String>(), commits)
            directory = "/fixture/from-host"
        }
        field.assertTextContains("/fixture/from-host")
        compose
            .onNodeWithText(context.getString(R.string.settings_download_directory_choose))
            .performClick()
        field.assertTextContains("/fixture/chosen")
        compose
            .onNodeWithText(context.getString(R.string.settings_download_directory_reset))
            .performClick()
        field.assertTextContains("/fixture/default")
        compose
            .onNodeWithText(context.getString(R.string.settings_download_directory_reset))
            .performClick()
        compose.runOnIdle { assertEquals(listOf("/fixture/chosen", "/fixture/default"), commits) }
    }

    @Test
    fun clearingDownloadsRequiresExplicitConfirmation() {
        var open by mutableStateOf(true)
        var clears = 0
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                if (open)
                    SettingsClearDownloadsDialog(
                        onDismiss = { open = false },
                        onConfirm = {
                            clears++
                            open = false
                        },
                    )
            }
        }
        val confirm =
            compose.onNodeWithText(context.getString(R.string.settings_clear_artifacts_confirm))
        confirm.assertExists()
        compose.runOnIdle { assertEquals(0, clears) }
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        confirm.assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, clears)
            open = true
        }
        compose.onNodeWithText(context.getString(android.R.string.cancel)).performClick()
        confirm.assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, clears)
            open = true
        }
        confirm.performClick().assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, clears) }
    }
}
