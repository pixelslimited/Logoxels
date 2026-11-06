package ng.pixelslimited.logoxels

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import ng.pixelslimited.logoxels.engine.Editor
import ng.pixelslimited.logoxels.ui.EditorScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF0F766E))) {
                val editor = remember { Editor() }
                EditorScreen(editor)
            }
        }
    }
}
