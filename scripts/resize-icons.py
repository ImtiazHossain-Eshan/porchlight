"""Resizes the rendered 1024 px icons into the web and iOS sizes. Called by render-icons.ps1."""
import os
import sys

from PIL import Image

src, root = sys.argv[1], sys.argv[2]
icon = Image.open(os.path.join(src, "icon.png")).convert("RGB")  # iOS app icons may not be transparent
maskable = Image.open(os.path.join(src, "maskable.png")).convert("RGB")
splash = Image.open(os.path.join(src, "splash.png")).convert("RGBA")

web = os.path.join(root, "webApp", "src", "wasmJsMain", "resources", "icons")
os.makedirs(web, exist_ok=True)
for size in (192, 512):
    icon.resize((size, size), Image.LANCZOS).save(os.path.join(web, f"icon-{size}.png"), optimize=True)
    maskable.resize((size, size), Image.LANCZOS).save(os.path.join(web, f"maskable-{size}.png"), optimize=True)
icon.resize((180, 180), Image.LANCZOS).save(os.path.join(web, "apple-touch-icon.png"), optimize=True)
icon.resize((32, 32), Image.LANCZOS).save(os.path.join(web, "favicon-32.png"), optimize=True)

assets = os.path.join(root, "iosApp", "iosApp", "Assets.xcassets")
appicon = os.path.join(assets, "AppIcon.appiconset")
if os.path.isdir(appicon):
    icon.save(os.path.join(appicon, "AppIcon-1024.png"), optimize=True)
mark = os.path.join(assets, "SplashMark.imageset")
if os.path.isdir(mark):
    for scale in (1, 2, 3):
        side = 160 * scale
        splash.resize((side, side), Image.LANCZOS).save(os.path.join(mark, f"SplashMark@{scale}x.png"), optimize=True)
print("icons written")
