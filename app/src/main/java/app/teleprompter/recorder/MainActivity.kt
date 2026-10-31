package app.teleprompter.recorder

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme

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
                    primary = Accent,
                    onPrimary = OnAccent,
                    surface = CardColor,
                    surfaceContainerLow = CardColor,
                    surfaceContainerHigh = CardRaised,
                    background = Ink,
                    onSurface = Fg,
                    onSurfaceVariant = Muted,
                    outline = Line,
                ),
            ) {
                MainScreen(state, engine)
            }
        }
    }
}
