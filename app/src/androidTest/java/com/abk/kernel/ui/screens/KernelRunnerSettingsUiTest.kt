package com.abk.kernel.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abk.kernel.R
import com.abk.kernel.data.model.KernelRunnerConfig
import com.abk.kernel.data.model.KernelRunnerSettings
import com.abk.kernel.data.model.KernelRunnerTarget
import com.abk.kernel.viewmodel.KernelRunnerSettingsContext
import com.abk.kernel.viewmodel.KernelRunnerSettingsUiState
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KernelRunnerSettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val fork = KernelRunnerSettingsContext("fixture-owner", "fixture-repo", "dev", 1L)
    private val config =
        KernelRunnerConfig("fixture-runner", selfHostedEnabled = true, supported = true)

    private fun text(id: Int) = context.getString(id)

    private fun action(id: Int) = compose.onNode(hasClickAction() and hasText(text(id)))

    private fun labels() =
        compose.onNodeWithContentDescription(text(R.string.settings_kernel_runner_labels))

    private fun toggle() =
        compose.onNode(
            hasClickAction() and hasText(text(R.string.settings_kernel_runner_self_hosted))
        )

    @Composable
    private fun RtlContent(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, 2f),
            LocalLayoutDirection provides LayoutDirection.Rtl,
        ) {
            AbkTheme(reduceMotion = true, content = content)
        }
    }

    @Composable
    private fun NarrowEditor(
        state: KernelRunnerSettingsUiState,
        config: KernelRunnerConfig = this.config,
        fork: KernelRunnerSettingsContext = this.fork,
        onSave: (KernelRunnerTarget, String, Boolean) -> Unit,
    ) {
        RtlContent {
            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                KernelRunnerEditor(fork, KernelRunnerTarget.GKI, config, state, onSave)
            }
        }
    }

    @Test
    fun missingForkShowsRequirementWithoutEditableSnapshotOrRefreshAction() {
        var refreshes = 0
        var saves = 0
        compose.setContent {
            RtlContent {
                KernelRunnerSettingsDialog(
                    state = KernelRunnerSettingsUiState(),
                    onRefresh = { refreshes++ },
                    onSave = { _, _, _ -> saves++ },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText(text(R.string.settings_kernel_runner_requires_fork)).assertExists()
        labels().assertDoesNotExist()
        action(R.string.refresh).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, refreshes)
            assertEquals(0, saves)
        }
    }

    @Test
    fun refreshRunsOnContextChangesAndExplicitClicksNotOrdinaryStateUpdates() {
        var state by mutableStateOf(KernelRunnerSettingsUiState(context = fork))
        var refreshes = 0
        compose.setContent {
            RtlContent { KernelRunnerSettingsDialog(state, { refreshes++ }, { _, _, _ -> }, {}) }
        }
        compose.runOnIdle {
            assertEquals(1, refreshes)
            state = state.copy(error = "Fixture failure")
        }
        compose
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Fixture failure"))
            .assertExists()
        labels().assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, refreshes) }
        action(R.string.refresh).performClick()
        compose.runOnIdle {
            assertEquals(2, refreshes)
            state = state.copy(context = fork.copy(repository = "other", repositoryId = 2L))
        }
        compose.runOnIdle { assertEquals(3, refreshes) }
    }

    @Test
    fun loadingRetainsProgressAndDisablesRefreshWithoutInventingEditors() {
        var refreshes = 0
        compose.setContent {
            RtlContent {
                KernelRunnerSettingsDialog(
                    KernelRunnerSettingsUiState(context = fork, loading = true),
                    { refreshes++ },
                    { _, _, _ -> },
                    {},
                )
            }
        }
        compose
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertExists()
        labels().assertDoesNotExist()
        action(R.string.refresh).assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test
    fun saveOnlyDispatchesExplicitTrimmedDraftAndDoesNotClaimItWasSaved() {
        val saves = mutableListOf<Triple<KernelRunnerTarget, String, Boolean>>()
        compose.setContent {
            NarrowEditor(KernelRunnerSettingsUiState(context = fork)) { target, labels, enabled ->
                saves += Triple(target, labels, enabled)
            }
        }
        action(R.string.save).performScrollTo().assertIsNotEnabled()
        labels().performScrollTo().performTextReplacement("  fixture-new  ")
        compose.onNodeWithText(text(R.string.settings_kernel_runner_unsaved)).assertExists()
        compose.runOnIdle {
            assertEquals(emptyList<Triple<KernelRunnerTarget, String, Boolean>>(), saves)
        }
        action(R.string.save).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(Triple(KernelRunnerTarget.GKI, "fixture-new", true)), saves)
        }
        labels().assertTextEquals("  fixture-new  ")
        compose.onNodeWithText(text(R.string.settings_kernel_runner_saved)).assertDoesNotExist()
    }

    @Test
    fun missingLabelsBlockEnableSaveAndHostedModeKeepsExistingLabels() {
        val saves = mutableListOf<Triple<KernelRunnerTarget, String, Boolean>>()
        compose.setContent {
            NarrowEditor(KernelRunnerSettingsUiState(context = fork)) { target, labels, enabled ->
                saves += Triple(target, labels, enabled)
            }
        }
        labels().performScrollTo().performTextReplacement("  ")
        labels()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Error,
                    text(R.string.settings_kernel_runner_labels_required),
                )
            )
        action(R.string.save).performScrollTo().assertIsNotEnabled()
        labels().performScrollTo().performTextReplacement("fixture-retained")
        toggle().performScrollTo().performClick().assertIsOff()
        labels().performScrollTo().assertIsNotEnabled().assertTextEquals("fixture-retained")
        action(R.string.save).performScrollTo().performClick()
        toggle().performScrollTo().performClick().assertIsOn()
        labels().performScrollTo().assertIsEnabled().assertTextEquals("fixture-retained")
        compose.runOnIdle {
            assertEquals(listOf(Triple(KernelRunnerTarget.GKI, "fixture-retained", false)), saves)
        }
    }

    @Test
    fun disablingSelfHostedModeCanExplicitlySaveBlankDraft() {
        val saves = mutableListOf<Triple<KernelRunnerTarget, String, Boolean>>()
        compose.setContent {
            NarrowEditor(KernelRunnerSettingsUiState(context = fork)) { target, labels, enabled ->
                saves += Triple(target, labels, enabled)
            }
        }
        labels().performScrollTo().performTextReplacement("")
        toggle().performScrollTo().performClick()
        action(R.string.save).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(Triple(KernelRunnerTarget.GKI, "", false)), saves) }
    }

    @Test
    fun unchangedHostedModeCanStillSaveAndDoesNotEraseLabels() {
        val saves = mutableListOf<Triple<KernelRunnerTarget, String, Boolean>>()
        compose.setContent {
            NarrowEditor(
                KernelRunnerSettingsUiState(context = fork),
                config = config.copy(selfHostedEnabled = false),
            ) { target, labels, enabled ->
                saves += Triple(target, labels, enabled)
            }
        }
        labels().assertIsNotEnabled().assertTextEquals("fixture-runner")
        compose.onNodeWithText(text(R.string.settings_kernel_runner_unsaved)).assertDoesNotExist()
        action(R.string.save).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(Triple(KernelRunnerTarget.GKI, "fixture-runner", false)), saves)
        }
    }

    @Test
    fun unsupportedAndBusyEditorsCannotEditOrDispatchAndSavingHasProgress() {
        var supported by mutableStateOf(false)
        var state by mutableStateOf(KernelRunnerSettingsUiState(context = fork))
        var saves = 0
        compose.setContent {
            NarrowEditor(state, config = config.copy(supported = supported)) { _, _, _ -> saves++ }
        }
        toggle().performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        labels().performScrollTo().assertIsNotEnabled()
        action(R.string.save).performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(0, saves)
            supported = true
        }
        labels().performScrollTo().performTextReplacement("fixture-dirty")
        compose.runOnIdle { state = state.copy(savingTarget = KernelRunnerTarget.GKI) }
        toggle().performScrollTo().assertIsNotEnabled()
        labels().performScrollTo().assertIsNotEnabled()
        action(R.string.settings_kernel_runner_saving).performScrollTo().assertIsNotEnabled()
        compose
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertExists()
        compose.runOnIdle {
            assertEquals(0, saves)
            state = state.copy(savingTarget = null, loading = true)
        }
        action(R.string.save).assertIsNotEnabled()
    }

    @Test
    fun ordinaryUpdatesKeepDraftAndSaveRevisionResetsEvenIdenticalSnapshot() {
        var state by mutableStateOf(KernelRunnerSettingsUiState(context = fork))
        compose.setContent { NarrowEditor(state) { _, _, _ -> } }
        labels().performScrollTo().performTextReplacement("fixture-draft")
        compose.runOnIdle { state = state.copy(savedTarget = KernelRunnerTarget.GKI) }
        labels().assertTextEquals("fixture-draft")
        compose.onNodeWithText(text(R.string.settings_kernel_runner_unsaved)).assertExists()
        compose.onNodeWithText(text(R.string.settings_kernel_runner_saved)).assertDoesNotExist()
        compose.runOnIdle {
            state = state.copy(saveRevisions = mapOf(KernelRunnerTarget.GKI to 1L))
        }
        labels().assertTextEquals("fixture-runner")
        toggle().assertIsOn()
        compose.onNodeWithText(text(R.string.settings_kernel_runner_saved)).assertExists()
        action(R.string.save).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun forkAndConfigChangesResetDraftInsteadOfReusingAnotherSnapshot() {
        var activeFork by mutableStateOf(fork)
        var activeConfig by mutableStateOf(config)
        compose.setContent {
            NarrowEditor(
                KernelRunnerSettingsUiState(context = activeFork),
                config = activeConfig,
                fork = activeFork,
            ) { _, _, _ ->
            }
        }
        labels().performScrollTo().performTextReplacement("fixture-draft")
        compose.runOnIdle { activeFork = fork.copy(repositoryId = 2L, defaultBranch = "other") }
        labels().assertTextEquals("fixture-runner")
        labels().performScrollTo().performTextReplacement("fixture-second-draft")
        compose.runOnIdle { activeConfig = config.copy(labels = "fixture-host-update") }
        labels().assertTextEquals("fixture-host-update")
        compose.onNodeWithText(text(R.string.settings_kernel_runner_unsaved)).assertDoesNotExist()
    }

    @Test
    fun targetDraftsAndSaveRevisionsStayIndependent() {
        var state by mutableStateOf(KernelRunnerSettingsUiState(context = fork))
        val saves = mutableListOf<Triple<KernelRunnerTarget, String, Boolean>>()
        compose.setContent {
            RtlContent {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    KernelRunnerTarget.entries.forEach { target ->
                        KernelRunnerEditor(fork, target, config, state) { selected, labels, enabled
                            ->
                            saves += Triple(selected, labels, enabled)
                        }
                    }
                }
            }
        }
        val fields =
            compose.onAllNodesWithContentDescription(text(R.string.settings_kernel_runner_labels))
        fields[0].performScrollTo().performTextReplacement("fixture-gki-draft")
        fields[1].performScrollTo().performTextReplacement("fixture-oneplus-draft")
        compose.runOnIdle {
            state = state.copy(saveRevisions = mapOf(KernelRunnerTarget.GKI to 1L))
        }
        fields[0].assertTextEquals("fixture-runner")
        fields[1].assertTextEquals("fixture-oneplus-draft")
        compose
            .onAllNodes(hasClickAction() and hasText(text(R.string.save)))[1]
            .performScrollTo()
            .performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(Triple(KernelRunnerTarget.ONEPLUS, "fixture-oneplus-draft", true)),
                saves,
            )
        }
    }

    @Test
    fun dialogUsesBothHostSnapshotsAndDiscardsEditorsWhenHostClearsSettings() {
        var state by
            mutableStateOf(
                KernelRunnerSettingsUiState(
                    context = fork,
                    settings =
                        KernelRunnerSettings(
                            config,
                            config.copy(labels = "fixture-oneplus", selfHostedEnabled = false),
                        ),
                )
            )
        val saves = mutableListOf<Triple<KernelRunnerTarget, String, Boolean>>()
        compose.setContent {
            RtlContent {
                KernelRunnerSettingsDialog(
                    state,
                    onRefresh = {},
                    onSave = { target, labels, enabled ->
                        saves += Triple(target, labels, enabled)
                    },
                    onDismiss = {},
                )
            }
        }
        val fields =
            compose.onAllNodesWithContentDescription(text(R.string.settings_kernel_runner_labels))
        fields.assertCountEquals(2)
        fields[0].assertTextEquals("fixture-runner")
        fields[1].assertTextEquals("fixture-oneplus").assertIsNotEnabled()
        val saveButtons = compose.onAllNodes(hasClickAction() and hasText(text(R.string.save)))
        saveButtons[0].assertIsNotEnabled()
        saveButtons[1].performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(Triple(KernelRunnerTarget.ONEPLUS, "fixture-oneplus", false)),
                saves,
            )
            state = state.copy(settings = null, error = "Fixture partial write")
        }
        fields.assertCountEquals(0)
        compose
            .onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.Error, "Fixture partial write")
            )
            .assertExists()
        compose.runOnIdle { assertEquals(1, saves.size) }
    }

    @Test
    fun busyDialogStillAllowsCloseAndBackWithoutSavingOrCancellingHostWork() {
        var visible by mutableStateOf(true)
        var dismisses = 0
        var saves = 0
        compose.setContent {
            RtlContent {
                if (visible) {
                    KernelRunnerSettingsDialog(
                        KernelRunnerSettingsUiState(
                            context = fork,
                            savingTarget = KernelRunnerTarget.GKI,
                        ),
                        onRefresh = {},
                        onSave = { _, _, _ -> saves++ },
                        onDismiss = {
                            dismisses++
                            visible = false
                        },
                    )
                }
            }
        }
        action(R.string.close).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, dismisses)
            visible = true
        }
        pressBack()
        compose.runOnIdle {
            assertEquals(2, dismisses)
            assertEquals(0, saves)
        }
    }
}
