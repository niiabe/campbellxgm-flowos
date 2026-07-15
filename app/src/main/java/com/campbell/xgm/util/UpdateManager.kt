package com.campbell.xgm.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

/**
 * Configuration for the in-app updater. The app checks the GitHub "latest release"
 * endpoint and downloads the signed APK asset directly from GitHub Releases.
 */
object GitHubReleaseConfig {
    const val OWNER = "niiabe"
    const val REPO = "campbellxgm-flowos"
    const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$OWNER/$REPO/releases/latest"
    const val FILE_PROVIDER_AUTHORITY = "com.campbell.xgm.fileprovider"
}

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val name: String,
    val body: String,
    val apkUrl: String?,
    val apkSize: Long,
    val htmlUrl: String
)

sealed class UpdateResult {
    object UpToDate : UpdateResult()
    data class Available(val release: ReleaseInfo) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

object UpdateManager {

    fun currentVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        } catch (_: PackageManager.NameNotFoundException) {
            "0"
        }
    }

    suspend fun checkForUpdate(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val connection = URL(GitHubReleaseConfig.API_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "CampbellXGM-Updater")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return@withContext UpdateResult.Error("GitHub API returned $code")
            }
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val release = parseRelease(json)
                ?: return@withContext UpdateResult.Error("No compatible release asset found")
            val current = currentVersion(context)
            return@withContext if (compareVersions(release.version, current) > 0) {
                UpdateResult.Available(release)
            } else {
                UpdateResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Network error")
        }
    }

    private fun parseRelease(json: String): ReleaseInfo? {
        return try {
            val obj = JSONObject(json)
            val tag = obj.optString("tag_name", "")
            val version = sanitizeVersion(tag)
            val name = obj.optString("name", tag).ifBlank { tag }
            val body = obj.optString("body", "")
            val assets = obj.optJSONArray("assets") ?: JSONArray()
            var apkUrl: String? = null
            var apkSize = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "")
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
            if (apkUrl.isNullOrBlank()) return null
            ReleaseInfo(
                tag = tag,
                version = version,
                name = name,
                body = body,
                apkUrl = apkUrl,
                apkSize = apkSize,
                htmlUrl = obj.optString("html_url", GitHubReleaseConfig.RELEASES_PAGE)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun sanitizeVersion(tag: String): String {
        val cleaned = tag.trim().removePrefix("v").removePrefix("V")
        val parts = cleaned.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }
        return if (parts.isEmpty()) "0" else parts.joinToString(".")
    }

    fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        val n = max(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    suspend fun downloadApk(
        url: String,
        dest: File,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "CampbellXGM-Updater")
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L

            val input = BufferedInputStream(connection.inputStream)
            val output = FileOutputStream(dest)
            val buffer = ByteArray(8 * 1024)
            var downloaded = 0L
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                downloaded += read
                onProgress(downloaded, total)
            }
            output.flush()
            output.close()
            input.close()
            connection.disconnect()
            dest.exists() && dest.length() > 0
        } catch (e: Exception) {
            try { dest.delete() } catch (_: Exception) { }
            false
        }
    }

    fun updatesDir(context: Context): File {
        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        dir.mkdirs()
        return dir
    }

    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, GitHubReleaseConfig.FILE_PROVIDER_AUTHORITY, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
