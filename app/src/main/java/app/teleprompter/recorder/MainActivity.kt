package app.teleprompter.recorder

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(0), navigationBarStyle = SystemBarStyle.dark(0))
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val state = PrompterState(this)
        val engine = CameraEngine(this, this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFFC24A),
                    onPrimary = Color(0xFF0C0E12),
                    surface = Color(0xFF171A21),
                    surfaceContainerLow = Color(0xFF171A21),
                    background = Color(0xFF0C0E12),
                ),
            ) {
                MainScreen(state, engine)
            }
        }
    }
}
