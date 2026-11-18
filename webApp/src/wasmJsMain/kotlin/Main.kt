import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import app.porchlight.game.PorchlightWeb
import app.porchlight.game.WebPlatform

/** The web host: the whole game in the page's #porchlight element. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val platform = WebPlatform()
    ComposeViewport("porchlight") {
        PorchlightWeb(platform)
    }
}
