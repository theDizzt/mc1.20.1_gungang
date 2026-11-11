# Convert the user-supplied turret recording; Minecraft positional audio must be mono.
param(
    [Parameter(Mandatory=$true)][string]$InputFile,
    [string]$Ffmpeg = 'ffmpeg'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$source = (Resolve-Path -LiteralPath $InputFile).Path
$outDir = Join-Path $root 'src/main/resources/assets/gungang/sounds'
New-Item -ItemType Directory -Force $outDir | Out-Null
& $Ffmpeg -y -hide_banner -i $source -vn -ac 1 -ar 44100 -c:a libvorbis -q:a 5 (Join-Path $outDir 'turret_shot.ogg')
if($LASTEXITCODE -ne 0){throw 'Sound conversion failed'}