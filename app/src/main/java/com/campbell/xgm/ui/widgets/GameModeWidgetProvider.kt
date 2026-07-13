package com.campbell.xgm.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.campbell.xgm.R
import com.campbell.xgm.domain.services.PipelineService

class GameModeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_game_mode)

            val gamePrefs = context.getSharedPreferences("saved_games_prefs", Context.MODE_PRIVATE)
            val activeGame = PipelineService.activeTargetPackage
            val isRunning = PipelineService.isRunning

            val gameCount = gamePrefs.all.size
            val firstGameName = if (gameCount > 0) {
                gamePrefs.all.entries.first().value as? String ?: "First game"
            } else null

            views.setTextViewText(
                R.id.widget_status,
                if (isRunning && activeGame != null) "Game Mode: ACTIVE" else "Game Mode: OFF"
            )

            views.setTextViewText(
                R.id.widget_games,
                when {
                    gameCount == 0 -> "No games added"
                    isRunning -> "Freezing background apps"
                    else -> "$gameCount game(s) configured"
                }
            )

            val toggleIntent = Intent(context, GameModeWidgetReceiver::class.java).apply {
                action = "TOGGLE_GAME_MODE"
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context, 0, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_toggle, togglePendingIntent)

            views.setTextViewText(
                R.id.widget_toggle,
                if (isRunning) "STOP GAME MODE" else (firstGameName?.let { "START $it" } ?: "ADD A GAME")
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GameModeWidgetProvider::class.java))
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }
    }
}
