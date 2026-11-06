package ng.pixelslimited.logoxels.engine

import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One artboard: a named rectangle in document space with its own size and optional background. */
class Board(name: String, val rect: RectF, bg: Int?) {
    var name by mutableStateOf(name)
    var bg by mutableStateOf(bg)
}
