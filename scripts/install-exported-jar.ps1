param(
    [string]$ConfigPath = (Join-Path $PSScriptRoot '..\export-paths.json'),
    [string]$JarPath,
    [switch]$WhatIf
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Assert-RegularPath($Item) {
    if ($Item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw "Links are not supported for deployment: $($Item.FullName)"
    }
}
function Test-GunGangJar([string]$Path) {
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entry = $zip.GetEntry('META-INF/mods.toml')
        if ($null -eq $entry) { return $false }
        $reader = New-Object IO.StreamReader($entry.Open())
        try { return $reader.ReadToEnd() -match '(?m)^\s*modId\s*=\s*"gungang_acoustics"\s*(?:#.*)?$' }
        finally { $reader.Dispose() }
    } finally { $zip.Dispose() }
}

try {
    if (-not (Test-Path -LiteralPath $ConfigPath)) {
        Write-Host 'No export-paths.json: installation skipped. Copy export-paths.example.json to configure it.'
        exit 0
    }
    $config = Get-Content -LiteralPath $ConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $dist = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\dist'))
    if ([string]::IsNullOrWhiteSpace($JarPath)) {
        $name = (Get-Content -LiteralPath (Join-Path $dist 'latest-export.txt') -Raw -Encoding UTF8).Trim()
        $source = Join-Path $dist $name
    } else {
        $source = (Get-Item -LiteralPath $JarPath).FullName
        $name = [IO.Path]::GetFileName($source)
    }
    if ($name -notmatch '^gungang-realistic_1\.20\.1_u\d+_\d+\.jar$') { throw 'Invalid latest-export.txt artifact name.' }
    if (-not (Test-GunGangJar $source)) { throw "Export is not a GunGang mod: $source" }
    $hash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    $plans = @()
    $seen = @{}
    # Resolve every destination before changing any files. Blank paths disable that destination.
    foreach ($path in @($config.serverMods, $config.instanceMods)) {
        if ([string]::IsNullOrWhiteSpace($path)) { continue }
        if (-not [IO.Path]::IsPathRooted($path)) { throw "Use an absolute mods directory: $path" }
        $directory = Get-Item -LiteralPath $path
        Assert-RegularPath $directory
        if (-not $directory.PSIsContainer -or $directory.Name -ne 'mods') { throw "Expected an existing mods directory: $path" }
        $target = $directory.FullName
        if ($seen.ContainsKey($target)) { continue }
        $seen[$target] = $true
        $old = @()
        foreach ($file in @(Get-ChildItem -LiteralPath $target -File -Filter '*.jar')) {
            if ($file.Name -notmatch '^gungang-(realistic|acoustics)[_-].*\.jar$') { continue }
            Assert-RegularPath $file
            if (Test-GunGangJar $file.FullName) { $old += $file }
        }
        $destination = Join-Path $target $name
        if ((Test-Path -LiteralPath $destination) -and $destination -notin @($old.FullName)) {
            throw "Destination exists but is not a recognized GunGang JAR: $destination"
        }
        $plans += [pscustomobject]@{ Target=$target; Old=$old; Destination=$destination }
    }
    foreach ($plan in $plans) {
        Write-Host "Install $name -> $($plan.Target) (replace $($plan.Old.Count) GunGang JARs)"
        if ($WhatIf) { continue }
        $token = [Guid]::NewGuid().ToString('N')
        $stage = Join-Path $plan.Target ".gungang-$token.tmp"
        $backupRoot = Join-Path $plan.Target '.gungang-backups'
        if (Test-Path -LiteralPath $backupRoot) { Assert-RegularPath (Get-Item -LiteralPath $backupRoot) }
        $backup = Join-Path $backupRoot $token
        $removed = @()
        $installed = $false
        try {
            Copy-Item -LiteralPath $source -Destination $stage
            if ((Get-FileHash -LiteralPath $stage -Algorithm SHA256).Hash -ne $hash) { throw 'Staged JAR checksum mismatch.' }
            New-Item -ItemType Directory -Path $backup -Force | Out-Null
            foreach ($file in $plan.Old) { Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $backup $file.Name) }
            foreach ($file in $plan.Old) {
                # Enumerated direct children only; never recursively remove a directory.
                if ([IO.Path]::GetDirectoryName($file.FullName) -ne $plan.Target) { throw 'Old JAR escaped the mods directory.' }
                Remove-Item -LiteralPath $file.FullName
                $removed += $file
            }
            Move-Item -LiteralPath $stage -Destination $plan.Destination
            $installed = $true
            Write-Host "Installed. Previous JARs saved in $backup"
        } catch {
            $failure = $_
            if ($installed) { Remove-Item -LiteralPath $plan.Destination }
            foreach ($file in $removed) { Copy-Item -LiteralPath (Join-Path $backup $file.Name) -Destination $file.FullName -Force }
            throw $failure
        } finally {
            if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage }
        }
    }
} catch {
    Write-Error $_ -ErrorAction Continue
    exit 1
}
