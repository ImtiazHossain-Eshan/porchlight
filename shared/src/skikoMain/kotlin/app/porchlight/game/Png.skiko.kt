package app.porchlight.game

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/** The image as PNG bytes, or null if Skia can't encode it. */
internal fun ImageBitmap.encodePng(): ByteArray? =
    runCatching { Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes }.getOrNull()
