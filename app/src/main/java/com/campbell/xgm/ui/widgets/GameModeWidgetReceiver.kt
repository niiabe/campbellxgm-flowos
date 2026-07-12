package com.campbell.xgm.ui.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.campbell.xgm.domain.services.PipelineService

class GameModeWidgetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "TOGGLE_GAME_MODE") {
            if (PipelineService.isRunning) {
                // Stop game mode
                val serviceIntent = Intent(context, PipelineService::class.java).apply {
                    action = "STOP_GAME_MODE"
                }
                context.startService(serviceIntent)
            } else {
                // Start game mode with first available game
                val gamePrefs = context.getSharedPreferences("saved_games_prefs", Context.MODE_PRIVATE)
                val allowedGames = gamePrefs.all.keys.toList()

                if (allowedGames.isNotEmpty()) {
                    val targetPackage = allowedGames.first()
                    val serviceIntent = Intent(context, PipelineService::class.java).apply {
                        putExtra("TARGET_PACKAGE", targetPackage)
                    }
                    context.startForegroundService(serviceIntent)
                }
            }

            // Update all widgets
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GameModeWidgetProvider::class.java))
            for (id in ids) {
                GameModeWidgetProvider.updateWidget(context, manager, id)
            }
        }
    }
}
