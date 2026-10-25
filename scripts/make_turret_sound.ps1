# Original synthetic rifle report; no third-party recordings. Requires ffmpeg on PATH.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$wavePath = Join-Path $root 'build/turret_shot.wav'
$outDir = Join-Path $root 'src/main/resources/assets/gungang/sounds'
New-Item -ItemType Directory -Force $outDir | Out-Null
$rate = 44100
$count = 5292
$rng = [Random]::new(4106)
$stream = [IO.File]::Create($wavePath)
$writer = [IO.BinaryWriter]::new($stream)
try {
    $writer.Write([Text.Encoding]::ASCII.GetBytes('RIFF')); $writer.Write([int](36+2*$count))
    $writer.Write([Text.Encoding]::ASCII.GetBytes('WAVEfmt ')); $writer.Write([int]16)
    $writer.Write([int16]1); $writer.Write([int16]1); $writer.Write([int]$rate); $writer.Write([int]($rate*2))
    $writer.Write([int16]2); $writer.Write([int16]16)
    $writer.Write([Text.Encoding]::ASCII.GetBytes('data')); $writer.Write([int]($count*2))
    for($i=0;$i -lt $count;$i++) {
        $t=$i/[double]$rate
        $noise=($rng.NextDouble()*2-1)
        $attack=[Math]::Min(1,$t*8000)
        $tail=[Math]::Min(1,($count-$i)/441.0)
        $signal=$attack*$tail*(.55*$noise*[Math]::Exp(-$t*95)+.35*[Math]::Sin(2*[Math]::PI*(150*$t-220*$t*$t))*[Math]::Exp(-$t*48)+.12*$noise*[Math]::Exp(-$t*35))
        $writer.Write([int16]([Math]::Max(-.9,[Math]::Min(.9,$signal))*32767))
    }
} finally {$writer.Dispose();$stream.Dispose()}
& ffmpeg -y -loglevel error -i $wavePath -c:a libvorbis -q:a 5 (Join-Path $outDir 'turret_shot.ogg')
if($LASTEXITCODE -ne 0){throw 'Sound conversion failed'}
