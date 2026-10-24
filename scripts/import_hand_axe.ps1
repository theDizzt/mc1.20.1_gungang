param([string]$Archive = 'C:\Users\eorhk\Downloads\minecraft-axe.zip')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing
$root = Split-Path $PSScriptRoot -Parent
$source = Join-Path $root 'models/hand_axe'
$assets = Join-Path $root 'src/main/resources/assets/gungang'
[IO.Directory]::CreateDirectory($source) | Out-Null
[IO.Directory]::CreateDirectory((Join-Path $assets 'textures/item')) | Out-Null
$zip = [IO.Compression.ZipFile]::OpenRead($Archive)
try {
    $reader = [IO.StreamReader]::new($zip.GetEntry('source/model.gltf').Open())
    $json = $reader.ReadToEnd(); $reader.Dispose()
} finally { $zip.Dispose() }
$utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $source 'model.gltf'),$json,$utf8)
$g = $json | ConvertFrom-Json
$bytes = [Convert]::FromBase64String($g.buffers[0].uri.Split(',')[1])
function Read-Accessor($index) {
    $a = $g.accessors[$index]; $view = $g.bufferViews[$a.bufferView]
    $components = @{SCALAR=1;VEC2=2;VEC3=3}[$a.type]
    $size = if($a.componentType -eq 5126){4}elseif($a.componentType -eq 5123){2}else{throw 'Unsupported component'}
    $stride = if($view.byteStride){$view.byteStride}else{$size*$components}
    for($i=0;$i -lt $a.count;$i++) {
        $row = for($j=0;$j -lt $components;$j++) {
            $offset = [int]($view.byteOffset+$a.byteOffset+$i*$stride+$j*$size)
            if($size -eq 4){[BitConverter]::ToSingle($bytes,$offset)}else{[BitConverter]::ToUInt16($bytes,$offset)}
        }
        ,@($row)
    }
}
function Number($n) { ([double]$n).ToString('0.########',[Globalization.CultureInfo]::InvariantCulture) }
$lines = [Collections.Generic.List[string]]::new()
$lines.Add('mtllib hand_axe.mtl'); $lines.Add('usemtl axe')
$base = 1; $triangles = 0
foreach($node in $g.nodes) {
    if($node.rotation -or $node.scale -or $node.matrix){throw 'Unexpected node transform'}
    $p = $g.meshes[$node.mesh].primitives[0]
    $positions = @(Read-Accessor $p.attributes.POSITION)
    $normals = @(Read-Accessor $p.attributes.NORMAL)
    $uvs = @(Read-Accessor $p.attributes.TEXCOORD_0)
    $indices = @(Read-Accessor $p.indices)
    $lines.Add('g part_'+$node.mesh)
    foreach($v in $positions) {
        $x=$v[0]+$node.translation[0];$y=$v[1]+$node.translation[1];$z=$v[2]+$node.translation[2]
        $lines.Add('v '+(Number (.5+($z-.5)*.47))+' '+(Number (.5+($y-.6875)*.47))+' '+(Number (.5-($x-.5)*.47)))
    }
    # A 3x3 tiled texture preserves glTF REPEAT UVs inside Minecraft's atlas.
    foreach($uv in $uvs) {
        if($uv[0] -lt -1 -or $uv[0] -gt 2 -or $uv[1] -lt -1 -or $uv[1] -gt 2){throw 'UV outside padded texture'}
        $lines.Add('vt '+(Number (($uv[0]+1)/3))+' '+(Number (($uv[1]+1)/3)))
    }
    foreach($n in $normals) {$lines.Add('vn '+(Number $n[2])+' '+(Number $n[1])+' '+(Number (-$n[0])))}
    for($i=0;$i -lt $indices.Count;$i+=3) {
        $face = for($j=0;$j -lt 3;$j++) {$k=$base+[int]$indices[$i+$j][0]; "$k/$k/$k"}
        $lines.Add('f '+($face -join ' '));$triangles++
    }
    $base += $positions.Count
}
if($base -ne 169 -or $triangles -ne 84){throw 'Unexpected geometry counts'}
[IO.File]::WriteAllLines((Join-Path $assets 'models/item/hand_axe.obj'),$lines,$utf8)
[IO.File]::WriteAllText((Join-Path $assets 'models/item/hand_axe.mtl'),"newmtl axe`nKd 1 1 1`nd 1`nmap_Kd gungang:item/hand_axe`n",$utf8)
$imageBytes = [Convert]::FromBase64String($g.images[0].uri.Split(',')[1])
[IO.File]::WriteAllBytes((Join-Path $source 'axe.png'),$imageBytes)
$stream=[IO.MemoryStream]::new($imageBytes)
$image=[Drawing.Image]::FromStream($stream)
$tile=[Drawing.Bitmap]::new($image.Width*3,$image.Height*3)
$graphics=[Drawing.Graphics]::FromImage($tile)
$graphics.CompositingMode=[Drawing.Drawing2D.CompositingMode]::SourceCopy
for($y=0;$y -lt 3;$y++){for($x=0;$x -lt 3;$x++){$graphics.DrawImageUnscaled($image,$x*$image.Width,$y*$image.Height)}}
$tile.Save((Join-Path $assets 'textures/item/hand_axe.png'),[Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose();$tile.Dispose();$image.Dispose();$stream.Dispose()
$modelPath=Join-Path $assets 'models/item/hand_axe.json'
$old=Get-Content $modelPath -Raw | ConvertFrom-Json
$model=[ordered]@{loader='forge:obj';model='gungang:models/item/hand_axe.obj';automatic_culling=$false;flip_v=$false;render_type='minecraft:cutout';textures=@{particle='gungang:item/hand_axe'};display=$old.display}
[IO.File]::WriteAllText($modelPath,($model | ConvertTo-Json -Depth 12)+"`n",$utf8)
Write-Output "Imported $($g.nodes.Count) parts, $($base-1) vertices, $triangles triangles; original alpha and repeating UVs preserved."
