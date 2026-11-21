package ng.pixelslimited.logoxels

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reports this app to the iCode dashboard: it opened, and when it crashes. Nothing the person types, scans or stores is sent,
 * only the app version and the crash type with a few code frames. Stays off until the build has both values.
 */
object ICode {
    private var endpoint = ""
    private var token = ""
    private var version = ""
    private var started = false
    private var allowed: () -> Boolean = { true }

    fun start(ctx: Context, endpoint: String, token: String, version: String, allowed: () -> Boolean = { true }) {
        if (started || endpoint.isBlank() || token.isBlank()) return
        started = true; this.allowed = allowed
        this.endpoint = endpoint; this.token = token; this.version = version
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { crash(e) } catch (_: Throwable) { }
            previous?.uncaughtException(t, e)
        }
        post(JSONObject().put("heartbeat", beat()).put("traffic", JSONObject().put("views", 1).put("visitors", 1)
            .put("top_pages", JSONArray().put(JSONObject().put("p", "/app-open").put("n", 1)))), wait = false)
    }

    fun event(level: String, title: String, alert: Boolean = false) {
        post(JSONObject().put("heartbeat", beat()).put("events", JSONArray().put(
            JSONObject().put("level", level).put("title", title.take(190)).put("source", "android").put("alert", alert))), wait = false)
    }

    private fun beat() = JSONObject().put("connector", "android-$version")

    private fun crash(e: Throwable) {
        val frames = JSONArray(e.stackTrace.take(8).map { it.toString() })
        val ev = JSONObject().put("level", "error").put("source", "android").put("alert", true).put("kind", "app_crash")
            .put("title", "Crash in $version: ${e.javaClass.simpleName}: ${e.message.orEmpty().take(120)}")
            .put("detail", JSONObject().put("frames", frames).put("version", version))
        post(JSONObject().put("heartbeat", beat()).put("events", JSONArray().put(ev)), wait = true)
    }

    /** Sent on a short-lived thread; a crash waits up to three seconds so the report leaves before the process ends. */
    private fun post(body: JSONObject, wait: Boolean) {
        if (!allowed()) return
        val th = Thread {
            try {
                val c = URL(endpoint).openConnection() as HttpURLConnection
                c.connectTimeout = 4000; c.readTimeout = 4000; c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("x-icode-token", token)
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                c.responseCode; c.disconnect()
            } catch (_: Exception) { }
        }
        th.start()
        if (wait) try { th.join(3000) } catch (_: InterruptedException) { }
    }
}
