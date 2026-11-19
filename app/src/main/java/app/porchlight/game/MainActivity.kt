package app.porchlight.game

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    private val sfx = Sfx()
    private var store: Store? = null

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
        val store = Store(getSharedPreferences("porchlight", MODE_PRIVATE)).also { this.store = it }
        //   ... --ei setXp 6500   (debug only: set the XP, to see the level emblem at a given level)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent.hasExtra("setXp")) {
            store.change { it.copy(xp = intent.getIntExtra("setXp", 0).coerceAtLeast(0)) }
        }
        //   ... --ei birthYear 2015   (debug only: answer the age screen; 0 shows it again)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent.hasExtra("birthYear")) {
            store.change { it.copy(birthYear = intent.getIntExtra("birthYear", 0).coerceAtLeast(0)) }
        }
        //   ... --ei gems 900   (debug only: set the Fireflies balance, to try the shop)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent.hasExtra("gems")) {
            store.change { it.copy(wallet = it.wallet.copy(balance = intent.getIntExtra("gems", 0).coerceAtLeast(0))) }
        }
        //   ... --ei level 36   (debug only: open every level up to 36 and pick it, to try it out)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent.hasExtra("level")) {
            val n = intent.getIntExtra("level", 1).coerceIn(1, LEVELS.size)
            store.change {
                it.copy(
                    reached = maxOf(it.reached, n),
                    current = n,
                    chapters = maxOf(it.chapters, levelAt(n).chapter),
                    levelStars = it.levelStars.mapIndexed { i, s -> if (i < n - 1) maxOf(s, 1) else s },
                    tutorialDone = true,
                )
            }
        }
        val ads = Ads.get(this, BuildConfig.REWARDED_AD_UNIT)
        //   ... --ez eea true   (debug only: run the consent check as if the phone were in the EEA)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent.getBooleanExtra("eea", false)) ads.pretendEea = true
        setContent { GameScreen(store, sfx, ads) }
    }

    override fun onResume() {
        super.onResume()
        // The daily gift: the first time the app comes to the front on a day (also past midnight).
        store?.open(todaySeed())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) sfx.release()
    }
}
