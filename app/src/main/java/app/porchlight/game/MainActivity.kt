package app.porchlight.game

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/** The Android host: the splash, edge to edge, and the shared game. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        // Debug builds can act like an older phone to check the fallbacks:
        //   adb shell am start -n app.porchlight.game/.MainActivity --ei pretendApi 30
        val platform = AndroidPlatform(this, pretendApi = if (debuggable) intent.getIntExtra("pretendApi", 0) else 0)
        val debug = if (debuggable) {
            DebugLaunch(
                //   ... --ei quality 2   (start at a lower quality: 1 half-size bloom, 2 also fewer particles)
                quality = intent.getIntExtra("quality", 0),
                //   ... --ef tiltX 0.8 --ef tiltY -0.5   (a fixed tilt, -1..1, instead of the sensor)
                fakeTilt = if (intent.hasExtra("tiltX") || intent.hasExtra("tiltY")) {
                    intent.getFloatExtra("tiltX", 0f) to intent.getFloatExtra("tiltY", 0f)
                } else null,
                //   ... --ei setXp 6500   (set the XP, to see the level emblem at a given level)
                setXp = if (intent.hasExtra("setXp")) intent.getIntExtra("setXp", 0) else null,
                //   ... --ei level 36   (open every level up to 36 and pick it, to try it out)
                level = if (intent.hasExtra("level")) intent.getIntExtra("level", 1) else null,
                //   ... --es screen daily   (open Levels, Daily, Moths, Badges or Settings, or start a Round)
                screen = intent.getStringExtra("screen"),
            )
        } else null
        setContent { PorchlightApp(platform, debug) }
    }
}
