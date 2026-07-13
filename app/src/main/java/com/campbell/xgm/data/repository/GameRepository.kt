package com.campbell.xgm.data.repository

import android.content.Context
import androidx.core.content.edit
import com.campbell.xgm.data.local.GameTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

interface GameRepository {
    fun getSavedGames(): List<GameTarget>
    fun observeSavedGames(): StateFlow<List<GameTarget>>
    fun saveGame(packageName: String, gameName: String)
    fun removeGame(packageName: String)
}

class SharedPrefsGameRepository(context: Context) : GameRepository {

    private val prefs = context.getSharedPreferences("saved_games_prefs", Context.MODE_PRIVATE)
    private val _savedGames = MutableStateFlow(loadGames())

    override fun getSavedGames(): List<GameTarget> = _savedGames.value

    override fun observeSavedGames(): StateFlow<List<GameTarget>> = _savedGames

    override fun saveGame(packageName: String, gameName: String) {
        prefs.edit().putString(packageName, gameName).apply()
        _savedGames.value = loadGames()
    }

    override fun removeGame(packageName: String) {
        prefs.edit { remove(packageName) }
        _savedGames.value = loadGames()
    }

    private fun loadGames(): List<GameTarget> {
        return prefs.all.mapNotNull { entry ->
            val gameName = entry.value as? String
            if (gameName != null) {
                GameTarget(packageName = entry.key, gameName = gameName)
            } else null
        }.sortedBy { it.gameName }
    }
}
