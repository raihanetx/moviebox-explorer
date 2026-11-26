package com.example.movieboxexplorer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update checking against the public GitHub repository.
 *
 * The `Publish latest APK` workflow keeps a stable `latest` release with
 * `app-debug.apk` + `version.json`. This checks `version.json`, and if it
 * reports a higher `versionCode` than the installed app, downloads the APK and
 * hands it to Android's package installer. Android always asks the user to
 * confirm installation (we can't and shouldn't bypass that).
 */
object UpdateChecker {

    private const val RELEASE_BASE =
        "https://github.com/raihanetx/moviebox-explorer"
    private const val VERSION_JSON_URL =
        "$RELEASE_BASE/releases/latest/download/version.json"
    const val APK_URL =
        "$RELEASE_BASE/releases/latest/download/app-debug.apk"

    /** The repository page, for "no releases yet" / errors. */
    const val RELEASES_PAGE = "$RELEASE_BASE/releases"

    data class UpdateInfo(
        val available: Boolean,
        val latestVersionCode: Int = 0,
        val latestVersionName: String = "",
        val installedVersionCode: Int = 0,
        val installedVersionName: String = "",
        val apkUrl: String = APK_URL,
        val notes: String = "",
    ) {
        val message: String
            get() = when {
                latestVersionCode <= 0 -> "Could not check for updates"
                !available -> "You're on the latest version ($installedVersionName)"
                else -> "Update available: ${latestVersionName.ifBlank { latestVersionCode.toString() }}"
            }
    }

    /** Fetches version.json and compares `versionCode` with the installed app. */
    suspend fun check(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        val installedCode = installedVersionCode(context)
        val installedName = installedVersionName(context)
        val latest = runCatching { fetchVersionJson() }.getOrNull()
            ?: return@withContext UpdateInfo(
                available = false,
                installedVersionCode = installedCode,
                installedVersionName = installedName,
                latestVersionCode = -1,
            )
        UpdateInfo(
            available = latest.first > installedCode,
            latestVersionCode = latest.first,
            latestVersionName = latest.second,
            installedVersionCode = installedCode,
            installedVersionName = installedName,
            apkUrl = latest.third.ifBlank { APK_URL },
            notes = latest.fourth,
        )
    }

    /** Returns (versionCode, versionName, apkUrl, notes). */
    private fun fetchVersionJson(): Quadruple {
        val conn = (URL(VERSION_JSON_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MovieBoxUpdateCheck")
        }
        try {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val code = json.optInt("versionCode", 0)
            val name = json.optString("versionName", "")
            val apk = json.optString("apkUrl", "")
            val notes = json.optString("notes", "")
            return Quadruple(code, name, apk, notes)
        } finally {
            conn.disconnect()
        }
    }

    private data class Quadruple(val first: Int, val second: String, val third: String, val fourth: String)

    /** Downloads the APK to app-private storage, returns the file. */
    suspend fun downloadApk(context: Context, url: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val outFile = File(dir, "app-update.apk")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MovieBoxUpdateCheck")
        }
        try {
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output, 8 * 1024) }
            }
        } finally {
            conn.disconnect()
        }
        outFile
    }

    /** Launches Android's package installer for a downloaded APK. */
    fun launchInstaller(context: Context, apk: File) {
        // On Android 8+ the user must have granted "install unknown apps" to this app.
        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openReleasesPage(context: Context) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun installedVersionCode(context: Context): Int = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    }.getOrDefault(0)

    fun installedVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")
}
