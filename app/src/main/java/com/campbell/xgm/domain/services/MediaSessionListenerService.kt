package com.campbell.xgm.domain.services

import android.service.notification.NotificationListenerService

/**
 * Minimal NotificationListenerService. Its only purpose is to satisfy the permission requirement
 * of MediaSessionManager.getActiveSessions(...), which lets PipelineService detect which app is
 * currently playing audio so it can be excluded from aggressive app freezing (keeps music alive
 * during game mode). It does not intercept or act on notifications.
 */
class MediaSessionListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        // No-op: we only need the listener to be bound for media-session access.
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) {
        // No-op.
    }
}
