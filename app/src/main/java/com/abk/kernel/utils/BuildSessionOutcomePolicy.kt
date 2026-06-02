package com.abk.kernel.utils

/**
 * Terminal outcomes for monitored workflow runs in a session.
 * Used by [BuildMonitorService] to pick the final notification when all monitors stop.
 */
enum class BuildSessionOutcome {
    Success,
    Failure,
    Cancelled,
}

enum class BuildSessionNotificationAction {
    NotifySuccess,
    NotifyFailure,
    CancelNotification,
}

fun resolveBuildSessionNotificationAction(
    outcomes: Collection<BuildSessionOutcome>,
): BuildSessionNotificationAction? {
    if (outcomes.isEmpty()) return null
    return when {
        outcomes.any { it == BuildSessionOutcome.Failure } -> BuildSessionNotificationAction.NotifyFailure
        outcomes.any { it == BuildSessionOutcome.Success } -> BuildSessionNotificationAction.NotifySuccess
        outcomes.all { it == BuildSessionOutcome.Cancelled } -> BuildSessionNotificationAction.CancelNotification
        else -> BuildSessionNotificationAction.CancelNotification
    }
}
