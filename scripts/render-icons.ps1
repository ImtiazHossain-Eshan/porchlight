# Renders the web and iOS icons from art/*.svg with headless Chrome (or Edge), then resizes them.
# Run from the repo root:  powershell -File scripts/render-icons.ps1
# Chrome writes harmless warnings to stderr, which Windows PowerShell would treat as errors.
$ErrorActionPreference = "Continue"
$browser = @(
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
    "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $browser) { throw "Needs Chrome or Edge." }
$root = (Resolve-Path "$PSScriptRoot\..").Path
$tmp = Join-Path $env:TEMP "porchlight-icons"
New-Item -ItemType Directory -Force $tmp | Out-Null

function Render($svg, $png) {
    & $browser --headless=new --disable-gpu --hide-scrollbars --default-background-color=00000000 `
        "--screenshot=$png" --window-size=1024,1024 "file:///$($root.Replace([char]92, '/'))/art/$svg" 2>$null | Out-Null
}
Render "icon.svg" "$tmp\icon.png"
Render "icon-maskable.svg" "$tmp\maskable.png"
Render "splash-mark.svg" "$tmp\splash.png"

python "$root\scripts\resize-icons.py" $tmp $root
