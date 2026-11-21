package com.abk.kernel.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
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
import com.abk.kernel.data.model.SusfsConfig
import com.abk.kernel.data.model.SusfsKstatEntry
import com.abk.kernel.data.model.SusfsOpenRedirectRule
import com.abk.kernel.data.model.SusfsPathRule
import com.abk.kernel.data.model.SusfsPresetOptions
import com.abk.kernel.data.model.SusfsRuntimeStatus
import com.abk.kernel.data.model.SusfsSupportMatrix
import com.abk.kernel.utils.SUSFS_HIDE_MOUNTS_NON_SU
import com.abk.kernel.utils.SUSFS_SPOOF_UNAME_POST_FS_DATA
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SusfsControlUiTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun text(id: Int) = context.getString(id)

    private val support =
        SusfsSupportMatrix(
            hideSusMountsForAll = true,
            hideSusMountsForNonSu = true,
            susPath = true,
            susPathLoop = true,
            susMap = true,
            susMount = true,
            tryUmount = true,
            openRedirect = true,
            staticKstat = true,
            setUname = true,
            sdcardRootPath = true,
            androidDataRootPath = true,
            avcLogSpoofing = true,
            autoTryUmountPreset = true,
            umountForZygoteIsoService = true,
        )

    private val runtime =
        SusfsRuntimeStatus(
            available = true,
            kernelVersion = "fixture-kernel-version-with-a-long-suffix",
            support = support,
            bundledBinaryVersion = "fixture-binary-version",
            bundledBinaryRef = "fixture-ref",
            installedBinaryPath = "/fixture/installed/binary/with/a/long/path",
            configPath = "/fixture/configuration/config.json",
            featureFlags = listOf("CONFIG_FIXTURE"),
            rawFeatureText = "CONFIG_FIXTURE",
        )

    private val fixture = SusfsConfig(legitMounts = listOf("/fixture/legit"))

    @Test
    fun narrowRtlLayoutKeepsActionsExplicitAndIndependent() {
        val actions = mutableListOf<String>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                AbkTheme(reduceMotion = true) {
                    Box(Modifier.width(320.dp)) {
                        SusfsControlContent(
                            0.dp,
                            runtime,
                            fixture,
                            false,
                            "Fixture runtime error",
                            listOf("Fixture output"),
                            true,
                            { actions += "apply" },
                            { actions += "reset" },
                            { actions += "refresh" },
                        )
                    }
                }
            }
        }
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        compose
            .onNodeWithContentDescription(text(R.string.settings_manager_loading_title))
            .performScrollTo()
            .assertExists()
        compose.onNodeWithText("Fixture runtime error").assertExists()
        compose
            .onNode(hasClickAction() and hasText(text(R.string.susfs_action_refresh)))
            .performScrollTo()
            .performClick()
        compose.runOnIdle { assertEquals(listOf("refresh"), actions) }
        compose
            .onNode(hasClickAction() and hasText(text(R.string.susfs_action_reset)))
            .performScrollTo()
            .performClick()
        compose.runOnIdle { assertEquals(listOf("refresh", "reset"), actions) }
        compose
            .onNode(hasClickAction() and hasText(text(R.string.susfs_action_apply)))
            .performScrollTo()
            .performClick()
        compose.runOnIdle { assertEquals(listOf("refresh", "reset", "apply"), actions) }
        compose.onNodeWithText("Fixture output").performScrollTo().assertExists()
    }

    @Test
    fun editsOnlyApplyExplicitlyUsingExistingParsersAndNormalization() {
        val applied = mutableListOf<SusfsConfig>()
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                SusfsControlContent(
                    0.dp,
                    runtime,
                    fixture,
                    false,
                    null,
                    emptyList(),
                    false,
                    applied::add,
                    {},
                    {},
                )
            }
        }
        compose
            .onNode(isToggleable() and hasText(text(R.string.susfs_toggle_auto_replay)))
            .performScrollTo()
            .performClick()
        compose
            .onNode(isSelectable() and hasText(text(R.string.susfs_option_non_su_processes)))
            .performScrollTo()
            .performClick()
        compose
            .onNode(isSelectable() and hasText(text(R.string.susfs_option_post_fs_data)))
            .performScrollTo()
            .performClick()
        compose
            .onNodeWithContentDescription(text(R.string.susfs_field_uname_value))
            .performScrollTo()
            .performTextReplacement("fixture-uname")
        compose
            .onNodeWithContentDescription(text(R.string.susfs_field_sus_path_rules))
            .performScrollTo()
            .performTextReplacement("# fixture comment\n/fixture/path 3")
        compose
            .onNodeWithContentDescription(text(R.string.susfs_field_sus_maps))
            .performScrollTo()
            .performTextReplacement(" /fixture/map \n# fixture comment\n/fixture/another-map")
        compose
            .onNodeWithContentDescription(text(R.string.susfs_field_open_redirect))
            .performScrollTo()
            .performTextReplacement("/fixture/original /fixture/redirected service 9")
        compose
            .onNodeWithContentDescription(text(R.string.susfs_field_kstat_json))
            .performScrollTo()
            .performTextReplacement("[{\"path\":\"/fixture/stat\",\"ino\":\"123\"}]")
        compose.runOnIdle { assertTrue(applied.isEmpty()) }
        compose
            .onNode(hasClickAction() and hasText(text(R.string.susfs_action_apply)))
            .performScrollTo()
            .performClick()
        compose.runOnIdle {
            val saved = applied.single()
            assertFalse(saved.autoReplayEnabled)
            assertEquals(SUSFS_HIDE_MOUNTS_NON_SU, saved.hideSusMountsMode)
            assertEquals(SUSFS_SPOOF_UNAME_POST_FS_DATA, saved.spoofUnameStage)
            assertEquals("fixture-uname", saved.unameValue)
            assertEquals(listOf(SusfsPathRule("/fixture/path", 3)), saved.pathRules)
            assertEquals(listOf("/fixture/map", "/fixture/another-map"), saved.maps)
            assertEquals(
                listOf(
                    SusfsOpenRedirectRule("/fixture/original", "/fixture/redirected", "service", 4)
                ),
                saved.openRedirects,
            )
            assertEquals(listOf(SusfsKstatEntry("/fixture/stat", ino = "123")), saved.kstatEntries)
            assertEquals(fixture.legitMounts, saved.legitMounts)
        }
    }

    @Test
    fun invalidRuleShowsFormErrorWithoutApplyingThenAllowsCorrection() {
        val applied = mutableListOf<SusfsConfig>()
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                SusfsControlContent(
                    0.dp,
                    runtime,
                    fixture,
                    false,
                    null,
                    emptyList(),
                    false,
                    applied::add,
                    {},
                    {},
                )
            }
        }
        val rules = compose.onNodeWithContentDescription(text(R.string.susfs_field_sus_path_rules))
        rules.performScrollTo().performTextReplacement("/fixture/path invalid")
        val apply = compose.onNode(hasClickAction() and hasText(text(R.string.susfs_action_apply)))
        apply.performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.susfs_form_error_title)).assertExists()
        compose.runOnIdle { assertTrue(applied.isEmpty()) }
        rules.performScrollTo().performTextReplacement("/fixture/path 2")
        apply.performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.susfs_form_error_title)).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(SusfsPathRule("/fixture/path", 2)), applied.single().pathRules)
        }
    }

    @Test
    fun capabilityGatesHideUnsupportedFieldsAndKeepTheirExistingPayload() {
        val applied = mutableListOf<SusfsConfig>()
        val savedConfig =
            fixture.copy(
                unameValue = "keep-uname",
                pathRules = listOf(SusfsPathRule("/fixture/hidden", 2)),
                openRedirects = listOf(SusfsOpenRedirectRule("/fixture/a", "/fixture/b")),
                kstatEntries = listOf(SusfsKstatEntry("/fixture/hidden-stat")),
                presets = SusfsPresetOptions(autoTryUmount = true, umountForZygoteIsoService = true),
            )
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                SusfsControlContent(
                    0.dp,
                    runtime.copy(support = SusfsSupportMatrix(susPath = false)),
                    savedConfig,
                    false,
                    null,
                    emptyList(),
                    false,
                    applied::add,
                    {},
                    {},
                )
            }
        }
        listOf(
                R.string.susfs_field_uname_value,
                R.string.susfs_field_sus_path_rules,
                R.string.susfs_field_sus_path_loop_rules,
                R.string.susfs_field_sus_maps,
                R.string.susfs_field_sus_mounts,
                R.string.susfs_field_try_umount,
                R.string.susfs_field_open_redirect,
                R.string.susfs_field_kstat_json,
            )
            .forEach { compose.onNodeWithContentDescription(text(it)).assertDoesNotExist() }
        compose.onNodeWithText(text(R.string.susfs_toggle_auto_try_umount)).assertDoesNotExist()
        compose
            .onNodeWithText(text(R.string.susfs_toggle_umount_for_zygote_iso_service))
            .assertDoesNotExist()
        compose
            .onNode(hasClickAction() and hasText(text(R.string.susfs_action_apply)))
            .performScrollTo()
            .performClick()
        compose.runOnIdle { assertEquals(savedConfig, applied.single()) }
    }

    @Test
    fun kernelUmountFallbackKeepsExistingRuleAndPresetControlsVisible() {
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                SusfsControlContent(
                    0.dp,
                    runtime.copy(
                        support =
                            SusfsSupportMatrix(susPath = false, ksudKernelUmountFallback = true)
                    ),
                    fixture,
                    false,
                    null,
                    emptyList(),
                    false,
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithContentDescription(text(R.string.susfs_field_try_umount)).assertExists()
        compose.onNodeWithText(text(R.string.susfs_toggle_auto_try_umount)).assertExists()
        compose.onNodeWithText(text(R.string.susfs_toggle_skip_legit_mounts)).assertExists()
    }

    @Test
    fun savingDisablesEveryInputChoiceAndActionWithoutSideEffects() {
        var saving by mutableStateOf(false)
        val actions = mutableListOf<String>()
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                SusfsControlContent(
                    0.dp,
                    runtime,
                    fixture,
                    saving,
                    null,
                    emptyList(),
                    false,
                    { actions += "apply" },
                    { actions += "reset" },
                    { actions += "refresh" },
                )
            }
        }
        compose.runOnIdle { saving = true }
        compose.onAllNodes(isToggleable()).assertCountEquals(13).assertAll(isNotEnabled())
        compose.onAllNodes(isSelectable()).assertCountEquals(15).assertAll(isNotEnabled())
        listOf(
                R.string.susfs_field_uname_value,
                R.string.susfs_field_build_time_value,
                R.string.susfs_field_sdcard_root_path,
                R.string.susfs_field_android_data_root_path,
                R.string.susfs_field_sus_path_rules,
                R.string.susfs_field_sus_path_loop_rules,
                R.string.susfs_field_sus_maps,
                R.string.susfs_field_sus_mounts,
                R.string.susfs_field_try_umount,
                R.string.susfs_field_legit_mounts,
                R.string.susfs_field_open_redirect,
                R.string.susfs_field_kstat_json,
            )
            .forEach { compose.onNodeWithContentDescription(text(it)).assertIsNotEnabled() }
        listOf(
                R.string.susfs_action_applying,
                R.string.susfs_action_reset,
                R.string.susfs_action_refresh,
            )
            .forEach { id ->
                compose
                    .onNodeWithText(text(id))
                    .performScrollTo()
                    .assertIsNotEnabled()
                    .performTouchInput { click() }
            }
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }

    @Test
    fun savedConfigChangesResynchronizeDraftsWithoutAutomaticApply() {
        var config by mutableStateOf(fixture)
        var applies = 0
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                SusfsControlContent(
                    0.dp,
                    runtime,
                    config,
                    false,
                    null,
                    emptyList(),
                    false,
                    { applies++ },
                    {},
                    {},
                )
            }
        }
        compose
            .onNodeWithContentDescription(text(R.string.susfs_field_uname_value))
            .performScrollTo()
            .performTextReplacement("unsaved-fixture")
        compose.runOnIdle {
            config =
                fixture.copy(
                    unameValue = "new-saved-fixture",
                    hideSusMountsMode = SUSFS_HIDE_MOUNTS_NON_SU,
                )
        }
        compose
            .onNodeWithContentDescription(text(R.string.susfs_field_uname_value))
            .assertTextContains("new-saved-fixture")
        compose
            .onNode(isSelectable() and hasText(text(R.string.susfs_option_non_su_processes)))
            .assertIsSelected()
        compose
            .onNode(isToggleable() and hasText(text(R.string.susfs_toggle_auto_replay)))
            .assertIsOn()
        compose.runOnIdle { assertEquals(0, applies) }
    }
}
