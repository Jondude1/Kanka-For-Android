package com.jondude1.kankaforandroid.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.jondude1.kankaforandroid.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(val versionName: String, val versionNumber: Int, val apkUrl: String, val notes: String)

/** Checks the public GitHub repo's latest release and installs its APK. No login needed (repo is public). */
object Updater {
    // GitHub redirects asset downloads to a CDN on another host, so redirects are followed by hand.
    private val http = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json").build()
        val text = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("Update check failed (HTTP ${r.code}). Is the repo public?")
            r.body!!.string()
        }
        val json = JSONObject(text)
        val tag = json.optString("tag_name")
        val number = Regex("(\\d+)$").find(tag)?.value?.toIntOrNull() ?: return@withContext null
        if (number <= BuildConfig.VERSION_CODE) return@withContext null
        val assets = json.optJSONArray("assets") ?: return@withContext null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) {
                return@withContext UpdateInfo(tag.trimStart('v'), number, a.getString("browser_download_url"), json.optString("body"))
            }
        }
        null
    }

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "update-${info.versionNumber}.apk")
        var url = info.apkUrl
        repeat(6) {
            val req = Request.Builder().url(url).build()
            http.newCall(req).execute().use { r ->
                if (r.isRedirect) {
                    url = r.header("Location") ?: error("Bad redirect")
                    return@repeat
                }
                if (!r.isSuccessful) error("Download failed (HTTP ${r.code})")
                val body = r.body!!
                val total = body.contentLength().toFloat()
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(32 * 1024); var done = 0L
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            output.write(buf, 0, n); done += n
                            if (total > 0) onProgress(done / total)
                        }
                    }
                }
                return@withContext out
            }
        }
        error("Too many redirects")
    }

    /** Returns false if the user first has to allow installs from this app (settings screen is opened). */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
