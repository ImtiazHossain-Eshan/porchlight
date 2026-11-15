package app.porchlight.game

import android.content.pm.ApplicationInfo
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
        // Debug builds can act like an older phone to check the fallbacks:
        //   adb shell am start -n app.porchlight.game/.MainActivity --ei pretendApi 30
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Gpu.pretend = intent.getIntExtra("pretendApi", 0)
            //   ... --ei quality 2   (start at a lower quality: 1 half-size bloom, 2 also fewer particles)
            Gpu.quality = intent.getIntExtra("quality", 0).coerceIn(0, 2)
            //   ... --ef tiltX 0.8 --ef tiltY -0.5   (a fixed tilt, -1..1, instead of the sensor)
            if (intent.hasExtra("tiltX") || intent.hasExtra("tiltY")) {
                Gpu.fakeTilt = intent.getFloatExtra("tiltX", 0f) to intent.getFloatExtra("tiltY", 0f)
            }
        }
        val store = Store(getSharedPreferences("porchlight", MODE_PRIVATE))
        setContent { GameScreen(store, sfx) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) sfx.release()
    }
}
