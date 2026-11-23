package com.abk.kernel.logic

/**
 * Draft/persisted text helpers for settings inputs: the field only adopts a new
 * persisted value while the user is not editing, and only commits a real change.
 */
fun shouldSyncSettingsTextDraft(
    isEditing: Boolean,
    persistedValue: String,
    draftValue: String
): Boolean = !isEditing && persistedValue != draftValue

fun pendingSettingsTextCommit(
    persistedValue: String,
    draftValue: String
): String? = draftValue.takeIf { it != persistedValue }
