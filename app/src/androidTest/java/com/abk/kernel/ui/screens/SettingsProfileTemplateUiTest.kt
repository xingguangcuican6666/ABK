package com.abk.kernel.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abk.kernel.R
import com.abk.kernel.data.model.AppProfileTemplateItem
import com.abk.kernel.viewmodel.MainUiState
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsProfileTemplateUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun text(id: Int) = context.getString(id)

    private fun action(id: Int) = compose.onNode(hasClickAction() and hasText(text(id)))

    private fun field(id: Int) = compose.onNodeWithContentDescription(text(id))

    private fun template(id: String) =
        compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected) and
                hasClickAction() and
                hasText(id)
        )

    private fun progress() =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))

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

    @Composable
    private fun Host(
        state: MainUiState = MainUiState(),
        refreshing: Boolean = false,
        onRefresh: () -> Unit = {},
        onSelect: (String?) -> Unit = {},
        onSave: (String, String) -> Unit = { _, _ -> },
        onDelete: (String) -> Unit = {},
    ) {
        AppProfileTemplateSettingsScreen(
            topBarHeight = 0.dp,
            state = state,
            showRefreshLoading = refreshing,
            onRefresh = onRefresh,
            onSelect = onSelect,
            onSave = onSave,
            onDelete = onDelete,
        )
    }

    private fun selectedState() =
        MainUiState(
            appProfileTemplates =
                listOf(
                    AppProfileTemplateItem("fixture-one"),
                    AppProfileTemplateItem("fixture-two"),
                ),
            selectedAppProfileTemplateId = "fixture-one",
            selectedAppProfileTemplateContent = "{\n  \"uid\": 1000\n}",
        )

    @Test
    fun emptyPageDoesNotInventAnEditorOrDispatchWork() {
        var operations = 0
        compose.setContent {
            NarrowContent {
                Host(
                    onRefresh = { operations++ },
                    onSelect = { operations++ },
                    onSave = { _, _ -> operations++ },
                    onDelete = { operations++ },
                )
            }
        }
        compose.onNodeWithText(text(R.string.settings_templates_empty)).assertExists()
        action(R.string.settings_new_template).assertIsEnabled()
        field(R.string.settings_template_name).assertDoesNotExist()
        action(R.string.save).assertDoesNotExist()
        action(R.string.delete).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, operations) }
    }

    @Test
    fun newTemplateUsesOriginalDefaultAndSavesOnlyExplicitUntrimmedDraft() {
        val selections = mutableListOf<String?>()
        val saves = mutableListOf<Pair<String, String>>()
        compose.setContent {
            NarrowContent {
                Host(
                    onSelect = { selections += it },
                    onSave = { id, content -> saves += id to content },
                )
            }
        }
        action(R.string.settings_new_template).performScrollTo().performClick()
        field(R.string.settings_template_name).assertIsEnabled().assertTextEquals("")
        field(R.string.settings_template_json)
            .assertTextEquals(
                """
                {
                  "uid": 0,
                  "gid": 0,
                  "groups": [],
                  "capabilities": [],
                  "context": "u:r:ksu:s0",
                  "namespace": 0,
                  "rules": ""
                }
                """
                    .trimIndent()
            )
        action(R.string.save).assertIsNotEnabled()
        field(R.string.settings_template_name)
            .performScrollTo()
            .performTextReplacement(" fixture new ")
        field(R.string.settings_template_json)
            .performScrollTo()
            .performTextReplacement("  not parsed by UI\n ")
        compose.runOnIdle {
            assertEquals(listOf<String?>(null), selections)
            assertEquals(emptyList<Pair<String, String>>(), saves)
        }
        action(R.string.save).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(" fixture new " to "  not parsed by UI\n "), saves)
        }
        field(R.string.settings_template_name).assertTextEquals(" fixture new ")
    }

    @Test
    fun blankTemplateNameCannotSaveAndDraftEditsDoNotAutoSubmit() {
        var saves = 0
        compose.setContent { NarrowContent { Host(onSave = { _, _ -> saves++ }) } }
        action(R.string.settings_new_template).performScrollTo().performClick()
        field(R.string.settings_template_name).performScrollTo().performTextReplacement("   ")
        action(R.string.save).performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        field(R.string.settings_template_json).performScrollTo().performTextReplacement("{}")
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test
    fun selectingExistingTemplateUsesHostSelectionAndContentUpdates() {
        var state by
            mutableStateOf(MainUiState(appProfileTemplates = selectedState().appProfileTemplates))
        val selections = mutableListOf<String?>()
        compose.setContent { NarrowContent { Host(state, onSelect = { selections += it }) } }
        template("fixture-one").performScrollTo().performClick()
        field(R.string.settings_template_name).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf<String?>("fixture-one"), selections)
            state = selectedState()
        }
        template("fixture-one").assertIsSelected()
        field(R.string.settings_template_name).assertTextEquals("fixture-one").assertIsNotEnabled()
        field(R.string.settings_template_json)
            .assertTextEquals(state.selectedAppProfileTemplateContent)
        compose.runOnIdle {
            state =
                state.copy(
                    selectedAppProfileTemplateId = "fixture-two",
                    selectedAppProfileTemplateContent = "second host snapshot",
                )
        }
        template("fixture-two").assertIsSelected()
        field(R.string.settings_template_name).assertTextEquals("fixture-two")
        field(R.string.settings_template_json).assertTextEquals("second host snapshot")
        compose.runOnIdle { assertEquals(listOf<String?>("fixture-one"), selections) }
    }

    @Test
    fun ordinaryHostUpdatesKeepDraftButChangedSelectedContentStillResynchronizes() {
        var state by mutableStateOf(selectedState())
        var saves = 0
        compose.setContent { NarrowContent { Host(state, onSave = { _, _ -> saves++ }) } }
        field(R.string.settings_template_json)
            .performScrollTo()
            .performTextReplacement("local unsaved draft")
        compose.runOnIdle {
            state = state.copy(appProfileTemplatesError = "Fixture non-selection update")
        }
        field(R.string.settings_template_json).assertTextEquals("local unsaved draft")
        compose.runOnIdle {
            state = state.copy(selectedAppProfileTemplateContent = "new host content")
        }
        field(R.string.settings_template_json).assertTextEquals("new host content")
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test
    fun newActionClearsSelectedHostAndResetsDraftWithoutSavingOrDeleting() {
        var state by mutableStateOf(selectedState())
        val selections = mutableListOf<String?>()
        var operations = 0
        compose.setContent {
            NarrowContent {
                Host(
                    state,
                    onSelect = { id ->
                        selections += id
                        state =
                            state.copy(
                                selectedAppProfileTemplateId = id,
                                selectedAppProfileTemplateContent = "",
                            )
                    },
                    onSave = { _, _ -> operations++ },
                    onDelete = { operations++ },
                )
            }
        }
        field(R.string.settings_template_json)
            .performScrollTo()
            .performTextReplacement("discarded draft")
        action(R.string.settings_new_template).performScrollTo().performClick()
        field(R.string.settings_template_name).assertIsEnabled().assertTextEquals("")
        action(R.string.delete).assertDoesNotExist()
        action(R.string.save).assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(listOf<String?>(null), selections)
            assertEquals(0, operations)
        }
    }

    @Test
    fun saveAndDeleteUseOriginalExplicitPayloadsAndDoNotClaimHostSuccess() {
        val saves = mutableListOf<Pair<String, String>>()
        val deletions = mutableListOf<String>()
        compose.setContent {
            NarrowContent {
                Host(
                    selectedState(),
                    onSave = { id, content -> saves += id to content },
                    onDelete = { deletions += it },
                )
            }
        }
        field(R.string.settings_template_json)
            .performScrollTo()
            .performTextReplacement(" {\n \"uid\": 42\n} ")
        action(R.string.save).performScrollTo().performClick()
        action(R.string.delete).performScrollTo().performClick()
        field(R.string.settings_template_name).assertTextEquals("fixture-one")
        field(R.string.settings_template_json).assertTextEquals(" {\n \"uid\": 42\n} ")
        compose.runOnIdle {
            assertEquals(listOf("fixture-one" to " {\n \"uid\": 42\n} "), saves)
            assertEquals(listOf("fixture-one"), deletions)
        }
    }

    @Test
    fun savingDisablesSaveAndDeleteButPreservesExistingFieldAndNavigationGates() {
        var operations = 0
        compose.setContent {
            NarrowContent {
                Host(
                    selectedState().copy(appProfileTemplateSaving = true),
                    onSave = { _, _ -> operations++ },
                    onDelete = { operations++ },
                )
            }
        }
        field(R.string.settings_template_name).assertIsNotEnabled()
        field(R.string.settings_template_json)
            .assertIsEnabled()
            .performScrollTo()
            .performTextReplacement("editable during request")
        template("fixture-two").assertIsEnabled()
        action(R.string.settings_new_template).assertIsEnabled()
        progress().assertExists()
        action(R.string.save).performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        action(R.string.delete).performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, operations) }
    }

    @Test
    fun listLoadingKeepsOriginalActionsWhileInteractiveRefreshHidesThePage() {
        var state by mutableStateOf(selectedState().copy(appProfileTemplatesLoading = true))
        var refreshing by mutableStateOf(false)
        var operations = 0
        compose.setContent {
            NarrowContent {
                Host(
                    state,
                    refreshing,
                    onSelect = { operations++ },
                    onSave = { _, _ -> operations++ },
                )
            }
        }
        progress().assertExists()
        template("fixture-two").assertIsEnabled()
        action(R.string.settings_new_template).assertIsEnabled()
        field(R.string.settings_template_json).assertExists()
        compose.runOnIdle { refreshing = true }
        progress().assertExists()
        template("fixture-two").assertDoesNotExist()
        action(R.string.settings_new_template).assertDoesNotExist()
        field(R.string.settings_template_json).assertDoesNotExist()
        compose.runOnIdle {
            refreshing = false
            state = state.copy(appProfileTemplatesLoading = false)
        }
        template("fixture-one").assertIsSelected()
        compose.runOnIdle { assertEquals(0, operations) }
    }

    @Test
    fun errorFeedbackHasSemanticsAndRefreshDoesNotSubmitOrClearDraft() {
        var refreshes = 0
        var operations = 0
        compose.setContent {
            NarrowContent {
                Host(
                    selectedState().copy(appProfileTemplatesError = "Fixture template failure"),
                    onRefresh = { refreshes++ },
                    onSave = { _, _ -> operations++ },
                    onDelete = { operations++ },
                )
            }
        }
        field(R.string.settings_template_json).performScrollTo().performTextReplacement("unsaved")
        compose
            .onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.Error, "Fixture template failure")
            )
            .assertExists()
        compose.onNodeWithText("Fixture template failure").assertExists()
        action(R.string.refresh).performScrollTo().performClick()
        field(R.string.settings_template_json).assertTextEquals("unsaved")
        compose.runOnIdle {
            assertEquals(1, refreshes)
            assertEquals(0, operations)
        }
    }

    @Test
    fun newDraftRestoresThroughOriginalSaveableHostWithoutAutomaticOperations() {
        val restoration = StateRestorationTester(compose)
        val selections = mutableListOf<String?>()
        val saves = mutableListOf<Pair<String, String>>()
        restoration.setContent {
            NarrowContent {
                Host(
                    onSelect = { selections += it },
                    onSave = { id, content -> saves += id to content },
                )
            }
        }
        action(R.string.settings_new_template).performScrollTo().performClick()
        field(R.string.settings_template_name)
            .performScrollTo()
            .performTextReplacement("restored-name")
        field(R.string.settings_template_json)
            .performScrollTo()
            .performTextReplacement("restored\nJSON draft")
        restoration.emulateSavedInstanceStateRestore()
        field(R.string.settings_template_name).assertTextEquals("restored-name")
        field(R.string.settings_template_json).assertTextEquals("restored\nJSON draft")
        compose.runOnIdle {
            assertEquals(listOf<String?>(null), selections)
            assertEquals(emptyList<Pair<String, String>>(), saves)
        }
        action(R.string.save).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("restored-name" to "restored\nJSON draft"), saves) }
    }
}
