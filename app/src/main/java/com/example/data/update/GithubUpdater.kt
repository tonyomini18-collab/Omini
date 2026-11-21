package com.example.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.data.local.AppReleaseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Where the app looks for new versions.
 * The release workflow (.github/workflows/release.yml) publishes android-version.json
 * to every GitHub Release, and "releases/latest/download" always points at the newest one.
 * The repository must be public for this URL to work without a token.
 */
object UpdateConfig {
    const val GITHUB_OWNER = "tonyomini18-collab"
    const val GITHUB_REPO = "Omini"
    const val VERSION_URL =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest/download/android-version.json"
    const val CHECK_INTERVAL_MS = 60L * 60L * 1000L
    const val OFFLINE_RETRY_MS = 60L * 1000L
}

enum class InstallResult { STARTED, NEEDS_PERMISSION, FAILED }

object GithubUpdater {

    private val shaByUrl = ConcurrentHashMap<String, String>()

    /** Returns the newest published Android release, or null when offline / nothing published. */
    suspend fun fetchLatest(): AppReleaseEntity? = withContext(Dispatchers.IO) {
        val conn: HttpURLConnection = try {
            URL(UpdateConfig.VERSION_URL).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return@withContext null
        }
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode != 200) {
                null
            } else {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val code = json.optInt("versionCode", 0)
                val apkUrl = json.optString("apkUrl", "")
                if (code <= 0 || apkUrl.isBlank()) {
                    null
                } else {
                    val sha = json.optString("sha256", "")
                    if (sha.isNotBlank()) shaByUrl[apkUrl] = sha
                    AppReleaseEntity(
                        id = "github-$code",
                        platform = "android",
                        versionCode = code,
                        versionName = json.optString("versionName", code.toString()),
                        downloadUrl = apkUrl,
                        releaseNotesAr = json.optString("notesAr", ""),
                        releaseNotesEn = json.optString("notesEn", ""),
                        isMandatory = json.optBoolean("mandatory", false)
                    )
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads the APK into the app cache and checks its SHA-256 when the release provides one. */
    suspend fun downloadApk(context: Context, url: String, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports")
            dir.mkdirs()
            val target = File(dir, "omini-update.apk")
            val part = File(dir, "omini-update.apk.part")
            if (part.exists()) part.delete()

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            try {
                if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                val digest = MessageDigest.getInstance("SHA-256")
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPct = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            if (total > 0) {
                                val pct = ((done * 100L) / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                val expected = shaByUrl[url].orEmpty()
                if (expected.isNotBlank() && !expected.equals(actual, ignoreCase = true)) {
                    part.delete()
                    throw IOException("Checksum mismatch")
                }
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) throw IOException("Cannot save update file")
                target
            } finally {
                conn.disconnect()
            }
        }

    /** Opens the system installer for the downloaded APK. Local data is kept by Android on update. */
    fun installApk(context: Context, apk: File): InstallResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                val settings = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(settings)
                InstallResult.NEEDS_PERMISSION
            } else {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apk
                )
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                InstallResult.STARTED
            }
        } catch (e: Exception) {
            InstallResult.FAILED
        }
    }
}
