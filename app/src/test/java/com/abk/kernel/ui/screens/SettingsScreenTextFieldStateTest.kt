package com.abk.kernel.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsScreenTextFieldStateTest {

    @Test
    fun doesNotSyncDraftWhileUserIsEditing() {
        assertFalse(
            shouldSyncSettingsTextDraft(
                isEditing = true,
                persistedValue = "/storage/emulated/0/ABK",
                draftValue = "/storage/emulated/0/AB"
            )
        )
    }

    @Test
    fun syncsDraftWhenIdleAndPersistedValueChanged() {
        assertTrue(
            shouldSyncSettingsTextDraft(
                isEditing = false,
                persistedValue = "https://hk.gh-proxy.org/",
                draftValue = "https://hk.gh-proxy.org"
            )
        )
    }

    @Test
    fun commitIsSkippedWhenDraftMatchesPersistedValue() {
        assertNull(
            pendingSettingsTextCommit(
                persistedValue = "https://hk.gh-proxy.org/",
                draftValue = "https://hk.gh-proxy.org/"
            )
        )
    }

    @Test
    fun commitReturnsUpdatedDraftVerbatim() {
        assertEquals(
            " https://hk.gh-proxy.org/ ",
            pendingSettingsTextCommit(
                persistedValue = "https://hk.gh-proxy.org/",
                draftValue = " https://hk.gh-proxy.org/ "
            )
        )
    }
}
