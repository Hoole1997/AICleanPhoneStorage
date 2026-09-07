package com.example.aicleanphonestorage.feature.notifications.service

/** 轻量候选，绝不把整个StatusBarNotification（含正文/图片）留在队列或缓存。 */
data class NotificationCandidate(
    val key: String,
    val packageName: String,
    val currentUser: Boolean,
    val clearable: Boolean,
    val ongoing: Boolean,
    val foregroundService: Boolean,
)

internal object NotificationClearPolicy {
    fun shouldClear(candidate: NotificationCandidate, selected: Set<String>, ownPackage: String): Boolean =
        candidate.packageName != ownPackage && candidate.packageName in selected && candidate.currentUser &&
            candidate.clearable && !candidate.ongoing && !candidate.foregroundService
}
