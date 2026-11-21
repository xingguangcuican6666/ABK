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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abk.kernel.R
import com.abk.kernel.data.model.RootGrantApp
import com.abk.kernel.data.model.RootGrantProfile
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootAuthorizationUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun text(id: Int) = context.getString(id)

    private val fixture =
        RootGrantApp(
            packageName = "com.example.fixture.with.a.long.package.name",
            label = "Fixture application with a long display name",
            uid = 10123,
            userName = "fixture-user",
            isSystemApp = true,
            profileLoaded = true,
            profile = RootGrantProfile(allowSu = true, flags = 17L, groups = listOf(0, 1000)),
        )

    @Test
    fun cardSeparatesDetailAndGrantAndDisablesGrantWhileAnySaveRuns() {
        var current by mutableStateOf(fixture)
        var saving by mutableStateOf(false)
        var anySaving by mutableStateOf(false)
        val actions = mutableListOf<String>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                AbkTheme(reduceMotion = true) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        RootGrantAppCard(
                            current,
                            saving,
                            anySaving,
                            {
                                current = current.copy(profile = current.profile.copy(allowSu = it))
                                actions += "toggle"
                            },
                            { actions += "detail" },
                            icon = {},
                        )
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
        compose.onNodeWithText(fixture.label).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("detail"), actions) }
        compose.onNode(isToggleable()).performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.runOnIdle {
            assertEquals(listOf("detail", "toggle"), actions)
            anySaving = true
        }
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.runOnIdle {
            anySaving = false
            saving = true
        }
        compose.onNode(isToggleable()).assertIsNotEnabled()
    }

    @Test
    fun profileDraftOnlySavesExplicitlyAndPreservesHiddenFieldsAndFlags() {
        val profiles = mutableListOf<RootGrantProfile>()
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                RootGrantProfilePage(
                    fixture,
                    0.dp,
                    false,
                    "Fixture warning",
                    profiles::add,
                    icon = {},
                )
            }
        }
        compose.onNodeWithText("Fixture warning").assertExists()
        compose.runOnIdle { assertEquals(emptyList<RootGrantProfile>(), profiles) }
        compose
            .onNode(isSelectable() and hasText(text(R.string.root_auth_custom)))
            .performScrollTo()
            .performClick()
        compose.onNodeWithContentDescription("UID").performScrollTo().performTextReplacement("2000")
        compose.onNodeWithContentDescription("GID").performScrollTo().performTextReplacement("3000")
        compose
            .onNodeWithContentDescription("Groups")
            .performScrollTo()
            .performTextReplacement("1,2 2\n3")
        compose
            .onNodeWithContentDescription("Capabilities")
            .performScrollTo()
            .performTextReplacement("4 5,4")
        compose
            .onNodeWithContentDescription("SELinux Context")
            .performScrollTo()
            .performTextReplacement(" u:r:fixture:s0 ")
        compose
            .onNodeWithContentDescription("Namespace")
            .performScrollTo()
            .performTextReplacement("2")
        compose
            .onNodeWithContentDescription("SEPolicy Rules")
            .performScrollTo()
            .performTextReplacement("fixture rule")
        compose.runOnIdle { assertTrue(profiles.isEmpty()) }
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()
        compose.runOnIdle {
            val saved = profiles.single()
            assertEquals(fixture.packageName, saved.name)
            assertEquals(fixture.uid, saved.currentUid)
            assertEquals(2000, saved.uid)
            assertEquals(3000, saved.gid)
            assertEquals(listOf(1, 2, 3), saved.groups)
            assertEquals(listOf(4, 5), saved.capabilities)
            assertEquals("u:r:fixture:s0", saved.context)
            assertEquals(2, saved.namespace)
            assertEquals("fixture rule", saved.rules)
            assertEquals(17L, saved.flags)
            assertFalse(saved.rootUseDefault)
        }
    }

    @Test
    fun templateChoiceAndExistingNumericFallbacksKeepTheirOriginalMeaning() {
        val profiles = mutableListOf<RootGrantProfile>()
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                RootGrantProfilePage(fixture, 0.dp, false, null, profiles::add, icon = {})
            }
        }
        compose.onNodeWithText(text(R.string.root_auth_template)).performScrollTo().performClick()
        compose
            .onNode(hasSetTextAction() and hasContentDescription(text(R.string.root_auth_template)))
            .performScrollTo()
            .performTextReplacement(" fixture-template ")
        compose
            .onNodeWithContentDescription("UID")
            .performScrollTo()
            .performTextReplacement("invalid")
        compose
            .onNodeWithContentDescription("SELinux Context")
            .performScrollTo()
            .performTextReplacement(" ")
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("fixture-template", profiles.single().rootTemplate)
            assertEquals(0, profiles.single().uid)
            assertEquals("u:r:ksu:s0", profiles.single().context)
            assertEquals(17L, profiles.single().flags)
        }
    }

    @Test
    fun nonRootOptionsRemainDraftUntilSaveAndDoNotReplaceRootConfiguration() {
        val profiles = mutableListOf<RootGrantProfile>()
        val denied =
            fixture.copy(
                profile =
                    fixture.profile.copy(
                        allowSu = false,
                        rootTemplate = "keep-template",
                        uid = 2000,
                    )
            )
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                RootGrantProfilePage(denied, 0.dp, false, null, profiles::add, icon = {})
            }
        }
        compose
            .onNode(isToggleable() and hasText(text(R.string.root_auth_use_default_non_root)))
            .performScrollTo()
            .assertIsOn()
            .performClick()
            .assertIsOff()
        compose
            .onNode(isToggleable() and hasText(text(R.string.root_auth_umount_modules)))
            .performScrollTo()
            .assertIsOn()
            .performClick()
            .assertIsOff()
        compose.runOnIdle { assertTrue(profiles.isEmpty()) }
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()
        compose.runOnIdle {
            val saved = profiles.single()
            assertFalse(saved.allowSu)
            assertFalse(saved.nonRootUseDefault)
            assertFalse(saved.umountModules)
            assertEquals("keep-template", saved.rootTemplate)
            assertEquals(2000, saved.uid)
            assertEquals(17L, saved.flags)
        }
    }

    @Test
    fun savingDisablesProfileSelectionAndInputsWithoutAutomaticSave() {
        var saving by mutableStateOf(false)
        var saves = 0
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                RootGrantProfilePage(fixture, 0.dp, saving, null, { saves++ }, icon = {})
            }
        }
        compose
            .onNode(isSelectable() and hasText(text(R.string.root_auth_custom)))
            .performScrollTo()
            .performClick()
        compose.runOnIdle { saving = true }
        compose
            .onNode(isSelectable() and hasText(text(R.string.root_auth_custom)))
            .assertIsNotEnabled()
        compose.onNodeWithContentDescription("UID").assertIsNotEnabled()
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose
            .onNodeWithText(text(R.string.save))
            .performScrollTo()
            .assertIsNotEnabled()
            .performClick()
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test
    fun profileDraftSurvivesStateRestorationWithoutGrantOrSaveSideEffects() {
        var saves = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AbkTheme(reduceMotion = true) {
                RootGrantProfilePage(fixture, 0.dp, false, null, { saves++ }, icon = {})
            }
        }
        compose
            .onNode(isSelectable() and hasText(text(R.string.root_auth_custom)))
            .performScrollTo()
            .performClick()
        compose.onNodeWithContentDescription("UID").performScrollTo().performTextReplacement("2000")
        restoration.emulateSavedInstanceStateRestore()
        compose
            .onNode(isSelectable() and hasText(text(R.string.root_auth_custom)))
            .assertIsSelected()
        compose.onNodeWithContentDescription("UID").assertTextContains("2000")
        compose.runOnIdle { assertEquals(0, saves) }
    }
}
