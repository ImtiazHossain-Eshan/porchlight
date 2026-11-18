# Icon art

Vector sources for the web and iOS icons. They are the Android launcher vectors
(`app/src/main/res/drawable/ic_launcher_*.xml`) written as SVG with the same paths and transforms.

| File | Used for |
|---|---|
| `icon.svg` | Square icons: iOS app icon, apple-touch-icon, web `any` icons (the middle 86 units, so the moth stays whole) |
| `icon-maskable.svg` | Web `maskable` icons (the full 108-unit adaptive canvas) |
| `splash-mark.svg` | The iOS launch screen mark (the bulb and moth without the wall, like the Android splash) |

`scripts/render-icons.ps1` renders the PNGs with headless Chrome or Edge.
