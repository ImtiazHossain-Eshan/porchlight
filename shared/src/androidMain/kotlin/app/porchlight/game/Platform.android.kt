package app.porchlight.game

import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

internal actual fun now(): Long = SystemClock.elapsedRealtime()

internal actual fun log(message: String) {
    Log.i("Porchlight", message)
}

@Composable
internal actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled, onBack)
}

/** Animations switched off for the whole phone (Developer options, or Remove animations). */
@Composable
internal actual fun systemReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
internal actual fun displayRefreshRate(): Float {
    val view = LocalView.current
    return view.display?.refreshRate?.takeIf { it > 1f } ?: 60f
}

@Composable
internal actual fun rememberChangingConfiguration(): () -> Boolean {
    val activity = LocalActivity.current
    return { activity?.isChangingConfigurations == true }
}
