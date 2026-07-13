package com.campbell.xgm.util

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log

object MediaUtils {

    private const val TAG = "MediaUtils"

    /**
     * Returns the set of package names that currently have an active (playing) media session.
     * Requires the app's NotificationListenerService to be enabled by the user; if it is not,
     * MediaSessionManager.getActiveSessions throws a SecurityException and we gracefully return
     * an empty set (freezing behaves as before).
     */
    fun getActiveMediaPackages(context: Context): Set<String> {
        return try {
            val component = ComponentName(context, com.campbell.xgm.domain.services.MediaSessionListenerService::class.java)
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return emptySet()
            manager.getActiveSessions(component)
                .filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                .map { it.packageName }
                .toSet()
        } catch (e: Exception) {
            Log.d(TAG, "Cannot read active media sessions (Notification Access not granted?): ${e.message}")
            emptySet()
        }
    }
}
