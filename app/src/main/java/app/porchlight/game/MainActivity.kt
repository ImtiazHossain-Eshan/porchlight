package app.porchlight.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    private val sfx = Sfx()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(getSharedPreferences("porchlight", MODE_PRIVATE))
        setContent { GameScreen(store, sfx) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) sfx.release()
    }
}
