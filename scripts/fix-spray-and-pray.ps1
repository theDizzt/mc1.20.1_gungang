param([string]$PackRoot = (Join-Path $PSScriptRoot '../server_resource/tacz'))
$ErrorActionPreference = 'Stop'
$source = Join-Path $PSScriptRoot 'tacz/spray_and_pray_logic.lua'
$root = (Resolve-Path -LiteralPath $PackRoot).Path
$count = 0
foreach ($pack in Get-ChildItem -LiteralPath $root -Directory) {
    $target = Join-Path $pack.FullName 'data/zeta/scripts/spray_and_pray_logic.lua'
    if (Test-Path -LiteralPath $target -PathType Leaf) {
        Copy-Item -LiteralPath $source -Destination $target
        Write-Output "Updated: $target"
        $count++
    }
}
if ($count -eq 0) { throw 'No unpacked Spray N Pray gun pack found in PackRoot.' }
