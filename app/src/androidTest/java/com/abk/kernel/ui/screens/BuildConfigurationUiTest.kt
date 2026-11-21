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
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abk.kernel.R
import com.abk.kernel.data.model.BUILD_TARGET_CUSTOM_SOURCE
import com.abk.kernel.data.model.KSU_VARIANT_SUKISU
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.data.model.SOURCE_ACCESS_GITHUB_PRIVATE
import com.abk.kernel.viewmodel.toInputMap
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BuildConfigurationUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun sourceEditsAreControlledAndOnlyRealFocusLossRequestsDetection() {
        var config by mutableStateOf(KernelBuildConfig(buildTarget = BUILD_TARGET_CUSTOM_SOURCE))
        var detections = 0
        var defconfigOpens = 0
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BuildSourceConfiguration(
                        config,
                        false,
                        null,
                        false,
                        { config = it },
                        { detections++ },
                        { defconfigOpens++ },
                    )
                }
            }
        }
        compose.runOnIdle { assertEquals(0, detections) }
        val ref = compose.onNodeWithContentDescription(context.getString(R.string.build_source_ref))
        ref.performScrollTo().requestFocus().performTextInput("lineage-23.2")
        ref.assertTextEquals("lineage-23.2")
        compose.runOnIdle {
            assertEquals("lineage-23.2", config.sourceRef)
            assertEquals(0, detections)
        }
        compose
            .onNodeWithContentDescription(context.getString(R.string.build_source_repo_url))
            .performScrollTo()
            .requestFocus()
            .performTextInput("https://github.com/example/kernel.git")
        compose.runOnIdle {
            assertEquals(1, detections)
            assertEquals("https://github.com/example/kernel.git", config.sourceUrl)
        }
        compose
            .onNodeWithText(context.getString(R.string.build_source_defconfigs))
            .performScrollTo()
            .performClick()
        compose.runOnIdle { assertEquals(1, defconfigOpens) }
    }

    @Test
    fun detectingHidesStaleErrorAndPrivateCredentialStateIsExplicitAtLargeFontInRtl() {
        var detecting by mutableStateOf(true)
        var secretConfigured by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                AbkTheme(reduceMotion = true) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        BuildSourceConfiguration(
                            KernelBuildConfig(sourceAccessMode = SOURCE_ACCESS_GITHUB_PRIVATE),
                            detecting,
                            "stale-error",
                            secretConfigured,
                            {},
                            {},
                            {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("stale-error").assertDoesNotExist()
        compose
            .onNodeWithText(context.getString(R.string.build_source_secret_missing))
            .performScrollTo()
            .assertExists()
        compose.runOnIdle {
            detecting = false
            secretConfigured = true
        }
        compose.onNodeWithText("stale-error").performScrollTo().assertExists()
        compose
            .onNodeWithText(context.getString(R.string.build_source_secret_exists))
            .performScrollTo()
            .assertExists()
        compose
            .onNodeWithText(context.getString(R.string.build_source_secret_missing))
            .assertDoesNotExist()
    }

    @Test
    fun previewUsesDispatchSerializerRedactsPasswordAndRestoresDisclosure() {
        val config =
            KernelBuildConfig(
                kernelsuVariant = KSU_VARIANT_SUKISU,
                useKpm = true,
                kpmPassword = "test-only-secret",
                ksuhookModules = listOf("https://github.com/example/patch@main"),
            )
        assertEquals(config.kpmPassword, config.toInputMap()["kpm_password"])
        val redacted = context.getString(R.string.build_preview_redacted)
        val expected =
            config.toInputMap().entries.joinToString("\n\n") { (key, value) ->
                "$key = ${if (key == "kpm_password" && value.isNotEmpty()) redacted else value}"
            }
        assertFalse(expected.contains(config.kpmPassword))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AbkTheme(reduceMotion = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BuildWorkflowInputPreview(config)
                }
            }
        }
        compose.onNodeWithText(expected).assertDoesNotExist()
        val label = context.getString(R.string.build_dispatch_preview)
        compose.onNodeWithText(label).performClick()
        compose.onNodeWithText(expected).assertExists()
        compose.onNodeWithText(config.kpmPassword, substring = true).assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(expected).assertExists()
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.onNodeWithText(expected).assertDoesNotExist()
    }
}
