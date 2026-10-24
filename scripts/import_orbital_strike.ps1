param([string]$Archive = '')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing
$root=Split-Path $PSScriptRoot -Parent
$source=Join-Path $root 'models/orbital_strike'
$assets=Join-Path $root 'src/main/resources/assets/gungang'
$utf8=[Text.UTF8Encoding]::new($false)
[IO.Directory]::CreateDirectory($source) | Out-Null
if($Archive) {
    $zip=[IO.Compression.ZipFile]::OpenRead($Archive)
    try {foreach($name in @('scene.gltf','scene.bin','license.txt','textures/Scene_-_Root_diffuse.png','textures/Scene_-_Root_normal.jpeg','textures/Scene_-_Root_specularGlossiness.png')) {
        [IO.Directory]::CreateDirectory((Split-Path (Join-Path $source $name) -Parent)) | Out-Null; $inputStream=$zip.GetEntry($name).Open();$outputStream=[IO.File]::Create((Join-Path $source $name))
        try {$inputStream.CopyTo($outputStream)}finally{$inputStream.Dispose();$outputStream.Dispose()}
    }}finally{$zip.Dispose()}
}
$g=Get-Content (Join-Path $source 'scene.gltf') -Raw | ConvertFrom-Json
$bytes=[IO.File]::ReadAllBytes((Join-Path $source 'scene.bin'))
function Read-Accessor($index) {
    $a=$g.accessors[$index];$view=$g.bufferViews[$a.bufferView]
    $components=@{SCALAR=1;VEC2=2;VEC3=3}[$a.type]
    if($a.componentType -notin @(5125,5126)){throw 'Unsupported component'}
    $stride=if($view.byteStride){$view.byteStride}else{4*$components}
    for($i=0;$i -lt $a.count;$i++) {
        $row=for($j=0;$j -lt $components;$j++) {
            $offset=[int]($view.byteOffset+$a.byteOffset+$i*$stride+$j*4)
            if($a.componentType -eq 5126){[BitConverter]::ToSingle($bytes,$offset)}else{[BitConverter]::ToUInt32($bytes,$offset)}
        }; ,@($row)
    }
}
function Number($n){([double]$n).ToString('0.########',[Globalization.CultureInfo]::InvariantCulture)}
# The two exporter root rotations cancel, leaving a uniform 1/600 scale. Verify this rather than silently ignoring transforms.
$matrices=@($g.nodes | Where-Object {$_.matrix} | ForEach-Object {,$_.matrix})
if($matrices.Count -ne 2){throw 'Unexpected scene transforms'}
for($r=0;$r -lt 4;$r++){for($c=0;$c -lt 4;$c++) {
    $value=0;for($k=0;$k -lt 4;$k++){$value+=$matrices[0][$k*4+$r]*$matrices[1][$c*4+$k]}
    $expected=if($r -eq $c){if($r -eq 3){1}else{1/600}}else{0};if([Math]::Abs($value-$expected) -gt 1e-6){throw 'Root transforms no longer cancel'}
}}
foreach($node in $g.nodes){if($node.rotation -or $node.translation -or $node.scale){throw 'Unexpected node transform'}}
$lines=[Collections.Generic.List[string]]::new();$lines.Add('mtllib orbital_strike.mtl')
$mtl=[Collections.Generic.List[string]]::new()
Copy-Item (Join-Path $source 'textures/Scene_-_Root_diffuse.png') (Join-Path $assets 'textures/item/orbital_strike.png') -Force
$mtl.Add("newmtl material_0`nKd 1 1 1`nd 1`nmap_Kd gungang:item/orbital_strike")
$base=1;$triangles=0
foreach($node in $g.nodes) {
    if($null -eq $node.mesh){continue}
    foreach($p in $g.meshes[$node.mesh].primitives) {
        if($p.mode -ne 4){throw 'Expected triangles'}
        $positions=@(Read-Accessor $p.attributes.POSITION);$normals=@(Read-Accessor $p.attributes.NORMAL);$uvs=@(Read-Accessor $p.attributes.TEXCOORD_0);$indices=@(Read-Accessor $p.indices)
        $lines.Add('g part_'+$node.mesh);$lines.Add('usemtl material_'+$p.material)
        # Center at the item origin; longest dimension is 0.884 block. Long axis points forward (-Z).
        foreach($v in $positions){$lines.Add('v '+(Number (.5+$v[2]*.004))+' '+(Number (.5+($v[1]-12.470249)*.004))+' '+(Number (.5-($v[0]+2.54348)*.004)))}
        # Every source UV is in V tile [1,2]; shifting by one preserves REPEAT without seams.
        foreach($uv in $uvs) {
            if($uv[0] -lt 0 -or $uv[0] -gt 1 -or $uv[1] -lt 1 -or $uv[1] -gt 2){throw 'Unexpected UV tile'}
            $lines.Add('vt '+(Number $uv[0])+' '+(Number ($uv[1]-1)))
        }
        foreach($n in $normals) {
            $lines.Add('vn '+(Number $n[2])+' '+(Number $n[1])+' '+(Number (-$n[0])))
            $lines.Add('vn '+(Number (-$n[2]))+' '+(Number (-$n[1]))+' '+(Number $n[0]))
        }
        for($i=0;$i -lt $indices.Count;$i+=3) {
            $face=for($j=0;$j -lt 3;$j++){$k=$base+[int]$indices[$i+$j][0];$normal=2*($k-1)+1;"$k/$k/$normal"}
            $lines.Add('f '+($face -join ' '));$triangles++
            if($g.materials[$p.material].doubleSided) {
                $face=for($j=2;$j -ge 0;$j--){$k=$base+[int]$indices[$i+$j][0];$normal=2*($k-1)+2;"$k/$k/$normal"}
                $lines.Add('f '+($face -join ' '));$triangles++
            }
        }
        $base+=$positions.Count
    }
}
if($base -ne 8514 -or $triangles -ne 17496){throw 'Unexpected model geometry'}
[IO.File]::WriteAllLines((Join-Path $assets 'models/item/orbital_strike.obj'),$lines,$utf8)
[IO.File]::WriteAllLines((Join-Path $assets 'models/item/orbital_strike.mtl'),$mtl,$utf8)
$display=@{
    gui=@{rotation=@(30,135,0);scale=@(1,1,1)}
    firstperson_righthand=@{rotation=@(0,-20,0);translation=@(0,1,-1);scale=@(.65,.65,.65)}
    firstperson_lefthand=@{rotation=@(0,20,0);translation=@(0,1,-1);scale=@(.65,.65,.65)}
    thirdperson_righthand=@{rotation=@(90,0,0);translation=@(0,2,0);scale=@(.65,.65,.65)}
    thirdperson_lefthand=@{rotation=@(90,0,0);translation=@(0,2,0);scale=@(.65,.65,.65)}
    ground=@{translation=@(0,2,0);scale=@(.5,.5,.5)}
    fixed=@{rotation=@(25,135,0)}
}
$model=[ordered]@{loader='forge:obj';model='gungang:models/item/orbital_strike.obj';automatic_culling=$false;flip_v=$false;render_type='minecraft:translucent';textures=@{particle='gungang:item/orbital_strike'};display=$display}
[IO.File]::WriteAllText((Join-Path $assets 'models/item/orbital_strike.json'),($model | ConvertTo-Json -Depth 10)+"`n",$utf8)
$credit=[IO.File]::ReadAllText((Join-Path $source 'license.txt'))+"`nModifications: converted glTF to Forge OBJ, scaled and oriented for handheld use; original diffuse texture and alpha preserved; V coordinates shifted by one repeating tile; normal/specular/gloss maps omitted.`n"
[IO.Directory]::CreateDirectory((Join-Path $root 'src/main/resources/META-INF/licenses')) | Out-Null
[IO.File]::WriteAllText((Join-Path $root 'src/main/resources/META-INF/licenses/orbital_strike.txt'),$credit,$utf8)
Write-Output "Imported orbital strike: $($base-1) vertices, $triangles double-sided triangles, original diffuse RGBA texture."
