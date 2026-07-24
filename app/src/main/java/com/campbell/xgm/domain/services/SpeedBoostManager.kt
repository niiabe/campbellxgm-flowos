package com.campbell.xgm.domain.services

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

data class RunningAppInfo(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean
)

class SpeedBoostManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE)
    private val recentlyClosed = mutableMapOf<String, Long>()

    fun markAsClosed(packages: List<String>) {
        val now = System.currentTimeMillis()
        for (pkg in packages) {
            recentlyClosed[pkg] = now
        }
    }

    fun killApps(packages: List<String>) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (pkg in packages) {
            try {
                am.killBackgroundProcesses(pkg)
            } catch (_: Exception) {}
        }
        markAsClosed(packages)
    }

    fun getRunningApps(): List<RunningAppInfo> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = context.packageManager
        val excludedApps = getExcludedApps()
        val runningProcesses = am.runningAppProcesses ?: return emptyList()

        val seen = mutableSetOf<String>()
        val result = mutableListOf<RunningAppInfo>()

        for (proc in runningProcesses) {
            for (pkg in proc.pkgList) {
                if (pkg == context.packageName) continue
                if (pkg in excludedApps) continue
                if (pkg in seen) continue
                val closedTime = recentlyClosed[pkg]
                if (closedTime != null && (System.currentTimeMillis() - closedTime < 15000)) continue

                seen.add(pkg)
                try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val appName = pm.getApplicationLabel(info).toString()
                    result.add(RunningAppInfo(pkg, appName, isSystem))
                } catch (_: Exception) {}
            }
        }

        return result.sortedBy { it.appName }
    }

    fun getRunningAppsCount(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val excludedApps = getExcludedApps()
        val runningProcesses = am.runningAppProcesses ?: return 0
        val seen = mutableSetOf<String>()
        var count = 0

        for (proc in runningProcesses) {
            for (pkg in proc.pkgList) {
                if (pkg == context.packageName) continue
                if (pkg in excludedApps) continue
                if (pkg in seen) continue
                val closedTime = recentlyClosed[pkg]
                if (closedTime != null && (System.currentTimeMillis() - closedTime < 15000)) continue
                seen.add(pkg)
                try {
                    val info = context.packageManager.getApplicationInfo(pkg, 0)
                    if ((info.flags and ApplicationInfo.FLAG_SYSTEM) == 0) count++
                } catch (_: Exception) {}
            }
        }
        return count
    }

    fun getAppsToKill(): List<String> {
        return getRunningApps()
            .filter { !it.isSystem }
            .map { it.packageName }
    }

    fun getAppsToKillForList(listName: String): List<String> {
        val customLists = getCustomLists()
        val listPackages = customLists[listName] ?: return emptyList()
        val excludedApps = getExcludedApps()
        val pm = context.packageManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = am.runningAppProcesses ?: return emptyList()
        val runningPkgs = runningProcesses.flatMap { it.pkgList.toList() }.toSet()

        return listPackages.filter { pkg ->
            pkg != context.packageName &&
            pkg !in excludedApps &&
            pkg in runningPkgs
        }
    }

    fun getExcludedApps(): Set<String> {
        val raw = prefs.getString("excluded_apps", "") ?: ""
        return if (raw.isEmpty()) emptySet() else raw.split(",").toSet()
    }

    fun setExcludedApps(apps: Set<String>) {
        prefs.edit().putString("excluded_apps", apps.joinToString(",")).apply()
    }

    fun addToExcludedApp(pkg: String) {
        val current = getExcludedApps().toMutableSet()
        current.add(pkg)
        setExcludedApps(current)
    }

    fun removeFromExcludedApp(pkg: String) {
        val current = getExcludedApps().toMutableSet()
        current.remove(pkg)
        setExcludedApps(current)
    }

    fun getCustomLists(): Map<String, List<String>> {
        val jsonStr = prefs.getString("custom_boost_lists", "{}") ?: "{}"
        val map = mutableMapOf<String, List<String>>()
        try {
            val jsonObj = JSONObject(jsonStr)
            for (key in jsonObj.keys()) {
                val jsonArr = jsonObj.getJSONArray(key)
                val list = mutableListOf<String>()
                for (i in 0 until jsonArr.length()) {
                    list.add(jsonArr.getString(i))
                }
                map[key] = list
            }
        } catch (_: Exception) {}
        return map
    }

    fun saveCustomList(name: String, packages: List<String>) {
        val map = getCustomLists().toMutableMap()
        map[name] = packages
        val jsonObj = JSONObject()
        for ((k, v) in map) {
            jsonObj.put(k, JSONArray(v))
        }
        prefs.edit().putString("custom_boost_lists", jsonObj.toString()).apply()
    }

    fun deleteCustomList(name: String) {
        val map = getCustomLists().toMutableMap()
        map.remove(name)
        val jsonObj = JSONObject()
        for ((k, v) in map) {
            jsonObj.put(k, JSONArray(v))
        }
        prefs.edit().putString("custom_boost_lists", jsonObj.toString()).apply()
    }

    fun renameCustomList(oldName: String, newName: String) {
        val map = getCustomLists().toMutableMap()
        val packages = map.remove(oldName) ?: return
        map[newName] = packages
        val jsonObj = JSONObject()
        for ((k, v) in map) {
            jsonObj.put(k, JSONArray(v))
        }
        prefs.edit().putString("custom_boost_lists", jsonObj.toString()).apply()
    }
}
