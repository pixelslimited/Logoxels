package ng.pixelslimited.logoxels.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Newest build published on the Logoxels site. [sha256] is the expected hash of the APK. */
data class Release(val build: Int, val url: String, val size: Long, val sha256: String)

/**
 * Manual, opt-in updater. Nothing here runs unless the user taps "Check for updates":
 * it asks logoxels.pixelslimited.ng for the newest build, downloads the APK into app
 * cache, checks its SHA-256, and hands it to the system installer. Android itself only
 * installs it over the existing app if it is signed with the same key.
 */
object Updater {
    private const val HOST = "logoxels.pixelslimited.ng"
    private const val LATEST = "https://$HOST/update.json"
    private const val FILE = "Logoxels.apk"

    fun isNewer(remote: Int, installed: Long): Boolean = remote.toLong() > installed

    fun installedBuild(ctx: Context): Long = ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode

    fun installedName(ctx: Context): String = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    /** Blocking; call off the main thread. */
    fun fetchLatest(): Release {
        val c = URL(LATEST).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) throw IOException("Update server answered ${c.responseCode}")
            return parse(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    private fun parse(json: String): Release {
        val o = JSONObject(json)
        val url = o.getString("url")
        if (URL(url).let { it.protocol != "https" || it.host != HOST }) throw IOException("Update file points somewhere unexpected")
        val sha = o.getString("sha256").lowercase()
        if (sha.length != 64) throw IOException("Update file has no checksum")
        return Release(o.getInt("build"), url, o.optLong("size", -1), sha)
    }

    /** Blocking; call off the main thread. [onProgress] gets 0..1 (or -1 when the size is unknown). */
    fun download(ctx: Context, rel: Release, onProgress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, FILE)
        val c = URL(rel.url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            if (c.responseCode != 200) throw IOException("Download answered ${c.responseCode}")
            val total = if (rel.size > 0) rel.size else c.contentLengthLong
            val sha = MessageDigest.getInstance("SHA-256")
            var done = 0L
            c.inputStream.use { input ->
                out.outputStream().use { sink ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        sink.write(buf, 0, n)
                        sha.update(buf, 0, n)
                        done += n
                        onProgress(if (total > 0) (done.toFloat() / total).coerceAtMost(1f) else -1f)
                    }
                }
            }
            val got = sha.digest().joinToString("") { "%02x".format(it) }
            if (got != rel.sha256) {
                out.delete()
                throw IOException("Downloaded file failed its integrity check")
            }
            return out
        } catch (e: Exception) {
            out.delete()
            throw e
        } finally {
            c.disconnect()
        }
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user lets Logoxels install apps. */
    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
