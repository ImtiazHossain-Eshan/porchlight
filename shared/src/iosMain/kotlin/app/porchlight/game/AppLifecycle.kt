package app.porchlight.game

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification

/**
 * The game's lifecycle on iOS, from the app's own state, so it matches Android exactly: going
 * inactive (an incoming call, Control Center, the app switcher) is ON_PAUSE, which pauses the round
 * and stops tilt; the background is ON_STOP; coming back to the front is ON_RESUME.
 */
internal class AppLifecycle : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    init {
        registry.currentState = when (UIApplication.sharedApplication.applicationState) {
            UIApplicationState.UIApplicationStateActive -> Lifecycle.State.RESUMED
            UIApplicationState.UIApplicationStateInactive -> Lifecycle.State.STARTED
            else -> Lifecycle.State.CREATED
        }
        val center = NSNotificationCenter.defaultCenter
        fun on(name: String?, state: Lifecycle.State) {
            center.addObserverForName(name, null, NSOperationQueue.mainQueue) { _ -> registry.currentState = state }
        }
        on(UIApplicationWillResignActiveNotification, Lifecycle.State.STARTED)
        on(UIApplicationDidEnterBackgroundNotification, Lifecycle.State.CREATED)
        on(UIApplicationWillEnterForegroundNotification, Lifecycle.State.STARTED)
        on(UIApplicationDidBecomeActiveNotification, Lifecycle.State.RESUMED)
    }
}
