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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abk.kernel.R
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSecurityUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Composable
    private fun NarrowRtlTheme(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, 2f),
            LocalLayoutDirection provides LayoutDirection.Rtl,
        ) {
            AbkTheme(reduceMotion = true, content = content)
        }
    }

    @Test
    fun securityActionsRespectForkSigningAndIndependentBusyGates() {
        var canManage by mutableStateOf(false)
        var signing by mutableStateOf(false)
        var signingBusy by mutableStateOf(false)
        var secretBusy by mutableStateOf(false)
        val actions = mutableListOf<String>()
        compose.setContent {
            NarrowRtlTheme {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    SettingsSecurityCard(
                        canManageKeys = canManage,
                        signingEnabled = signing,
                        signingConfigured = false,
                        signingBusy = signingBusy,
                        secretConfigured = true,
                        secretBusy = secretBusy,
                        onSigningChange = {
                            signing = it
                            actions += "signing:$it"
                        },
                        onImportKeys = { actions += "import" },
                        onResetKeys = { actions += "reset" },
                        onEditSecret = { actions += "edit" },
                        onDeleteSecret = { actions += "delete" },
                    )
                }
            }
        }
        fun action(id: Int) = compose.onNode(hasClickAction() and hasText(context.getString(id)))
        val signingSwitch = action(R.string.settings_security_signing_title)
        val import = action(R.string.settings_security_import_keys)
        val reset = action(R.string.settings_security_reset_keys)
        val edit = action(R.string.settings_custom_source_secret_title)
        val delete = action(R.string.settings_custom_source_secret_delete)
        listOf(signingSwitch, import, reset, edit, delete).forEach {
            it.performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        }
        compose.runOnIdle {
            assertEquals(emptyList<String>(), actions)
            canManage = true
        }
        import.assertIsNotEnabled()
        reset.assertIsNotEnabled()
        signingSwitch.performScrollTo().performClick().assertIsOn()
        import.performScrollTo().performClick()
        reset.performScrollTo().performClick()
        compose.runOnIdle { signingBusy = true }
        listOf(signingSwitch, import, reset).forEach {
            it.performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        }
        edit.performScrollTo().performClick()
        delete.performScrollTo().performClick()
        compose.runOnIdle {
            signingBusy = false
            secretBusy = true
        }
        listOf(edit, delete).forEach {
            it.performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        }
        import.assertIsEnabled()
        compose.runOnIdle {
            assertEquals(listOf("signing:true", "import", "reset", "edit", "delete"), actions)
        }
    }

    @Test
    fun twoTimedConfirmationsEachWaitBeforeDispatchingTheirOwnAction() {
        compose.mainClock.autoAdvance = false
        var stage by mutableStateOf(0)
        val actions = mutableListOf<String>()
        compose.setContent {
            NarrowRtlTheme {
                if (stage == 0) {
                    TimedConfirmationDialog(
                        title = "Fixture first warning",
                        message = "Fixture first confirmation message",
                        confirmLabel = "Fixture confirm",
                        onDismiss = { actions += "dismiss:first" },
                        onConfirm = {
                            actions += "first"
                            stage = 1
                        },
                    )
                } else if (stage == 1) {
                    TimedConfirmationDialog(
                        title = "Fixture final warning",
                        message = "Fixture second confirmation message",
                        confirmLabel = "Fixture confirm",
                        onDismiss = { actions += "dismiss:second" },
                        onConfirm = {
                            actions += "second"
                            stage = 2
                        },
                    )
                }
            }
        }
        val confirm =
            compose.onNode(hasClickAction() and hasText("Fixture confirm", substring = true))
        repeat(2) { index ->
            confirm.assertIsNotEnabled().performTouchInput { click() }
            compose.mainClock.advanceTimeBy(4_000)
            confirm.assertIsNotEnabled()
            compose.runOnIdle {
                assertEquals(if (index == 0) emptyList() else listOf("first"), actions)
            }
            compose.mainClock.advanceTimeBy(1_200)
            confirm.assertIsEnabled().performClick()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.runOnIdle { assertEquals(listOf("first", "second"), actions) }
        confirm.assertDoesNotExist()
    }

    @Test
    fun timedConfirmationCancelAndCloseDoNotConfirmAndReopenRestartsDelay() {
        compose.mainClock.autoAdvance = false
        var open by mutableStateOf(true)
        var confirmations = 0
        compose.setContent {
            NarrowRtlTheme {
                if (open)
                    TimedConfirmationDialog(
                        title = "Fixture warning",
                        message = "Fixture warning message",
                        confirmLabel = "Fixture confirm",
                        onDismiss = { open = false },
                        onConfirm = { confirmations++ },
                    )
            }
        }
        compose.mainClock.advanceTimeBy(5_200)
        compose.onNodeWithText("Fixture confirm").assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { open = true }
        compose.mainClock.advanceTimeByFrame()
        compose
            .onNode(hasClickAction() and hasText("Fixture confirm", substring = true))
            .assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(0, confirmations) }
    }

    @Test
    fun importFieldsAndPickersAreControlledAndPrivateKeyIsMasked() {
        var publicKey by mutableStateOf("")
        var privateKey by mutableStateOf("")
        val actions = mutableListOf<String>()
        val imports = mutableListOf<Pair<String, String>>()
        compose.setContent {
            NarrowRtlTheme {
                ImportArtifactSigningKeysDialog(
                    publicKeyText = publicKey,
                    privateKeyText = privateKey,
                    error = "Fixture key validation error",
                    importing = false,
                    onPublicKeyTextChange = { publicKey = it },
                    onPrivateKeyTextChange = { privateKey = it },
                    onPickPublicKey = { actions += "public" },
                    onPickPrivateKey = { actions += "private" },
                    onImport = { imports += publicKey to privateKey },
                    onDismiss = { actions += "dismiss" },
                )
            }
        }
        val import = compose.onNodeWithText(context.getString(R.string.settings_import))
        import.assertIsNotEnabled()
        compose
            .onNodeWithContentDescription(
                context.getString(R.string.settings_security_import_public_key)
            )
            .performScrollTo()
            .performTextReplacement(" fixture-public ")
        import.assertIsNotEnabled()
        compose
            .onNodeWithContentDescription(
                context.getString(R.string.settings_security_import_private_key)
            )
            .performScrollTo()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
            .performTextReplacement(" fixture-private ")
        compose
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Error,
                    "Fixture key validation error",
                )
            )
            .assertExists()
        compose
            .onNodeWithText(context.getString(R.string.settings_security_import_pick_public_key))
            .performScrollTo()
            .performClick()
        compose
            .onNodeWithText(context.getString(R.string.settings_security_import_pick_private_key))
            .performScrollTo()
            .performClick()
        compose.runOnIdle { assertEquals(emptyList<Pair<String, String>>(), imports) }
        import.performClick()
        compose.runOnIdle {
            assertEquals(listOf("public", "private"), actions)
            assertEquals(listOf(" fixture-public " to " fixture-private "), imports)
        }
    }

    @Test
    fun importInFlightLocksEditingPickersCancelAndBackWithoutDispatch() {
        var busy by mutableStateOf(true)
        val actions = mutableListOf<String>()
        compose.setContent {
            NarrowRtlTheme {
                ImportArtifactSigningKeysDialog(
                    publicKeyText = "fixture-public",
                    privateKeyText = "fixture-private",
                    error = null,
                    importing = busy,
                    onPublicKeyTextChange = { actions += "public-edit" },
                    onPrivateKeyTextChange = { actions += "private-edit" },
                    onPickPublicKey = { actions += "public-pick" },
                    onPickPrivateKey = { actions += "private-pick" },
                    onImport = { actions += "import" },
                    onDismiss = { actions += "dismiss" },
                )
            }
        }
        val close = compose.onNodeWithContentDescription(context.getString(R.string.close))
        close.assertDoesNotExist()
        listOf(
                R.string.settings_security_import_public_key,
                R.string.settings_security_import_private_key,
            )
            .forEach {
                compose
                    .onNodeWithContentDescription(context.getString(it))
                    .performScrollTo()
                    .assertIsNotEnabled()
            }
        listOf(
                R.string.settings_security_import_pick_public_key,
                R.string.settings_security_import_pick_private_key,
            )
            .forEach {
                compose
                    .onNode(hasClickAction() and hasText(context.getString(it)))
                    .performScrollTo()
                    .assertIsNotEnabled()
                    .performTouchInput { click() }
            }
        listOf(R.string.settings_import, R.string.cancel).forEach {
            compose
                .onNode(hasClickAction() and hasText(context.getString(it)))
                .assertIsNotEnabled()
                .performTouchInput { click() }
        }
        pressBack()
        compose
            .onNodeWithText(context.getString(R.string.settings_security_import_dialog_title))
            .assertExists()
        compose.runOnIdle {
            assertEquals(emptyList<String>(), actions)
            busy = false
        }
        close.performClick()
        compose.runOnIdle { assertEquals(listOf("dismiss"), actions) }
    }

    @Test
    fun sourceSecretIsMaskedAndOnlyExplicitSaveUsesUntrimmedHostValue() {
        var value by mutableStateOf("")
        var open by mutableStateOf(true)
        val saved = mutableListOf<String>()
        compose.setContent {
            NarrowRtlTheme {
                if (open)
                    SettingsSourceSecretDialog(
                        value = value,
                        busy = false,
                        onValueChange = { value = it },
                        onSave = { saved += value },
                        onDismiss = { open = false },
                    )
            }
        }
        val save = compose.onNodeWithText(context.getString(R.string.save))
        save.assertIsNotEnabled()
        compose
            .onNodeWithContentDescription(context.getString(R.string.build_source_pat))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
            .performTextReplacement(" fixture-pat ")
        compose.runOnIdle { assertEquals(emptyList<String>(), saved) }
        save.performClick()
        compose.runOnIdle { assertEquals(listOf(" fixture-pat "), saved) }
        compose.onNodeWithText(context.getString(android.R.string.cancel)).performClick()
        save.assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, saved.size) }
    }

    @Test
    fun sourceSecretBusyLocksSaveEditingCancelAndBack() {
        var busy by mutableStateOf(true)
        val actions = mutableListOf<String>()
        compose.setContent {
            NarrowRtlTheme {
                SettingsSourceSecretDialog(
                    value = "fixture-pat",
                    busy = busy,
                    onValueChange = { actions += "edit" },
                    onSave = { actions += "save" },
                    onDismiss = { actions += "dismiss" },
                )
            }
        }
        val close = compose.onNodeWithContentDescription(context.getString(R.string.close))
        close.assertDoesNotExist()
        compose
            .onNodeWithContentDescription(context.getString(R.string.build_source_pat))
            .assertIsNotEnabled()
        listOf(R.string.save, android.R.string.cancel).forEach {
            compose
                .onNode(hasClickAction() and hasText(context.getString(it)))
                .assertIsNotEnabled()
                .performTouchInput { click() }
        }
        pressBack()
        compose.runOnIdle {
            assertEquals(emptyList<String>(), actions)
            busy = false
        }
        close.performClick()
        compose.runOnIdle { assertEquals(listOf("dismiss"), actions) }
    }

    @Test
    fun sourceSecretDeleteRequiresConfirmationAndRespectsBusyGate() {
        var open by mutableStateOf(true)
        var busy by mutableStateOf(false)
        var deletes = 0
        compose.setContent {
            NarrowRtlTheme {
                if (open)
                    SettingsDeleteSourceSecretDialog(
                        busy = busy,
                        onConfirm = { deletes++ },
                        onDismiss = { open = false },
                    )
            }
        }
        compose.runOnIdle { assertEquals(0, deletes) }
        compose.onNodeWithText(context.getString(android.R.string.cancel)).performClick()
        compose.runOnIdle {
            assertEquals(0, deletes)
            open = true
            busy = true
        }
        val delete =
            compose.onNode(hasClickAction() and hasText(context.getString(R.string.delete)))
        delete.assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithContentDescription(context.getString(R.string.close)).assertDoesNotExist()
        pressBack()
        compose.runOnIdle {
            assertEquals(0, deletes)
            busy = false
        }
        delete.performClick()
        compose.runOnIdle { assertEquals(1, deletes) }
    }
}
