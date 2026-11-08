package ng.pixelslimited.logoxels.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import ng.pixelslimited.logoxels.update.Release
import ng.pixelslimited.logoxels.update.Updater
import java.io.File
import kotlin.concurrent.thread

private sealed interface UpdateStep {
    data object Checking : UpdateStep
    data object Current : UpdateStep
    data class Found(val rel: Release) : UpdateStep
    data class Downloading(val progress: Float) : UpdateStep
    data class Ready(val apk: File) : UpdateStep
    data class Failed(val why: String) : UpdateStep
}

/** Check, download and install flow. Only composed after the user asks for it. */
@Composable
internal fun UpdateDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableStateOf<UpdateStep>(UpdateStep.Checking) }
    val installed = remember { Updater.installedBuild(ctx) }
    val name = remember { Updater.installedName(ctx) }

    fun check() {
        step = UpdateStep.Checking
        thread {
            step = try {
                val rel = Updater.fetchLatest()
                if (Updater.isNewer(rel.build, installed)) UpdateStep.Found(rel) else UpdateStep.Current
            } catch (e: Exception) {
                UpdateStep.Failed(e.message ?: "Could not reach the update server")
            }
        }
    }

    fun download(rel: Release) {
        step = UpdateStep.Downloading(0f)
        thread {
            step = try {
                UpdateStep.Ready(Updater.download(ctx, rel) { step = UpdateStep.Downloading(it) })
            } catch (e: Exception) {
                UpdateStep.Failed(e.message ?: "Download failed")
            }
        }
    }

    var started by remember { mutableStateOf(false) }
    if (!started) {
        started = true
        check()
    }

    val s = step
    AlertDialog(
        onDismissRequest = { if (s !is UpdateStep.Downloading) onClose() },
        title = { Text("Updates", fontSize = 16.sp) },
        text = {
            Column {
                when (s) {
                    UpdateStep.Checking -> Text("Checking for updates", fontSize = 14.sp)
                    UpdateStep.Current -> Text("Up to date ($name)", fontSize = 14.sp)
                    is UpdateStep.Found -> Text(
                        "A newer build is available (build ${s.rel.build}, you have $installed). " +
                            "Your work is autosaved and is kept when the app updates.",
                        fontSize = 14.sp,
                    )
                    is UpdateStep.Downloading -> {
                        Text("Downloading", fontSize = 14.sp)
                        if (s.progress >= 0f) LinearProgressIndicator({ s.progress }, Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    is UpdateStep.Ready -> Text(
                        if (Updater.canInstall(ctx)) "Ready to install."
                        else "Android needs your permission to let Logoxels install updates. Allow it, then come back and tap Install.",
                        fontSize = 14.sp,
                    )
                    is UpdateStep.Failed -> Text(s.why, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            when (s) {
                is UpdateStep.Found -> TextButton({ download(s.rel) }) { Text("Download", fontSize = 14.sp) }
                is UpdateStep.Ready -> TextButton({
                    if (Updater.canInstall(ctx)) {
                        Updater.install(ctx, s.apk)
                        onClose()
                    } else {
                        Updater.openInstallPermission(ctx)
                    }
                }) { Text(if (Updater.canInstall(ctx)) "Install" else "Allow", fontSize = 14.sp) }
                is UpdateStep.Failed -> TextButton({ check() }) { Text("Retry", fontSize = 14.sp) }
                else -> Unit
            }
        },
        dismissButton = {
            if (s !is UpdateStep.Downloading) TextButton(onClose) { Text("Close", fontSize = 14.sp) }
        },
    )
}
