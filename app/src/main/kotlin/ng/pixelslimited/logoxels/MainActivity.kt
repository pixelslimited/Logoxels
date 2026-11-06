package ng.pixelslimited.logoxels

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import ng.pixelslimited.logoxels.engine.Editor
import ng.pixelslimited.logoxels.ui.EditorScreen
import ng.pixelslimited.logoxels.ui.Ink
import ng.pixelslimited.logoxels.ui.Pal
import ng.pixelslimited.logoxels.ui.uiFont
import ng.pixelslimited.logoxels.ui.withFont

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // First run follows the phone's light/dark setting; after that the toggle in the top bar decides.
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        Pal.dark = getSharedPreferences("ui", MODE_PRIVATE).getBoolean("dark", night)
        val font = uiFont(this)
        setContent {
            val scheme = if (Pal.dark) darkColorScheme(primary = Color(0xFF2DD4BF)) else lightColorScheme(primary = Color(0xFF0F766E))
            MaterialTheme(colorScheme = scheme, typography = Typography().withFont(font)) {
                CompositionLocalProvider(
                    LocalContentColor provides Ink,
                    LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = font),
                ) {
                    val editor = remember { Editor() }
                    EditorScreen(editor)
                }
            }
        }
    }
}
