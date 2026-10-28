param([Parameter(Mandatory=$true)][string]$ClientJar)
$ErrorActionPreference='Stop'
# Official Minecraft Java 1.21.6 release client. Keep this import reproducible.
if((Get-FileHash -LiteralPath $ClientJar -Algorithm SHA1).Hash -ne '740a125b83dd3447feaa3c5e891ead7fbb21ae28'){throw 'Expected the official 1.21.6 client JAR'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root=Join-Path $PSScriptRoot '../src/main/resources/assets/gungang'
$utf8=New-Object Text.UTF8Encoding $false
$zip=[IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ClientJar))
function ReadEntry($name){$reader=New-Object IO.StreamReader ($zip.GetEntry($name).Open());try{$reader.ReadToEnd()}finally{$reader.Dispose()}}
try {
    $model=ReadEntry 'assets/minecraft/models/block/dried_ghast.json' | ConvertFrom-Json
    $variant=ReadEntry 'assets/minecraft/models/block/dried_ghast_hydration_3.json' | ConvertFrom-Json
    $model.parent='minecraft:block/block'
    foreach($property in $variant.textures.PSObject.Properties){$property.Value=$property.Value.Replace('minecraft:','gungang:')}
    $model | Add-Member -NotePropertyName textures -NotePropertyValue $variant.textures
    [IO.File]::WriteAllText((Join-Path $root 'models/block/dried_ghast.json'),($model|ConvertTo-Json -Depth 40),$utf8)
    foreach($side in 'north','south','east','west','top','bottom','tentacles'){
        $name="dried_ghast_hydration_3_$side.png"
        [IO.Compression.ZipFileExtensions]::ExtractToFile($zip.GetEntry("assets/minecraft/textures/block/$name"),(Join-Path $root "textures/block/$name"),$true)
    }
}finally{$zip.Dispose()}
Write-Output 'Imported official dried ghast hydration-3 geometry and seven textures.'
