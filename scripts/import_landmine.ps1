param([string]$Source = 'C:\Users\eorhk\Downloads\landmine.glb')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$assets = Join-Path $root 'src/main/resources/assets/gungang'
$bytes = [IO.File]::ReadAllBytes($Source)
if ([Text.Encoding]::ASCII.GetString($bytes,0,4) -ne 'glTF' -or [BitConverter]::ToInt32($bytes,4) -ne 2) { throw 'Expected GLB 2.0' }
$jsonLength = [BitConverter]::ToInt32($bytes,12)
$g = [Text.Encoding]::UTF8.GetString($bytes,20,$jsonLength) | ConvertFrom-Json
$binStart = 20 + $jsonLength + 8
function Accessor([int]$index) {
    $a=$g.accessors[$index]; $v=$g.bufferViews[$a.bufferView]
    $width=@{SCALAR=1;VEC2=2;VEC3=3;VEC4=4}[$a.type]
    if($a.componentType -notin @(5125,5126)) { throw 'Unsupported component type' }
    $stride=4*$width; if($v.byteStride) { $stride=$v.byteStride }
    $offset=$binStart+[int]$v.byteOffset+[int]$a.byteOffset
    for($i=0;$i -lt $a.count;$i++) {
        $row=for($j=0;$j -lt $width;$j++) {
            if($a.componentType -eq 5126) { [double][BitConverter]::ToSingle($bytes,$offset+$i*$stride+$j*4) }
            else { [int][BitConverter]::ToUInt32($bytes,$offset+$i*$stride+$j*4) }
        }
        ,$row
    }
}
# This asset's two axis-conversion matrices cancel. Verify the entire single-node chain.
$world=[double[]]@(1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1)
$nodeIndex=$g.scenes[$g.scene].nodes[0]
while($true) {
    $node=$g.nodes[$nodeIndex]
    if($node.translation -or $node.rotation -or $node.scale) { throw 'Unexpected TRS transform' }
    if($node.matrix) {
        $next=New-Object double[] 16
        for($c=0;$c -lt 4;$c++) { for($r=0;$r -lt 4;$r++) { for($k=0;$k -lt 4;$k++) { $next[$c*4+$r]+=$world[$k*4+$r]*$node.matrix[$c*4+$k] } } }
        $world=$next
    }
    if($null -ne $node.mesh) { $mesh=$g.meshes[$node.mesh]; break }
    if($node.children.Count -ne 1) { throw 'Expected one mesh node chain' }
    $nodeIndex=$node.children[0]
}
for($i=0;$i -lt 16;$i++) { $expected=0; if($i%5 -eq 0){$expected=1}; if([Math]::Abs($world[$i]-$expected) -gt 0.000001){throw 'Unexpected world transform'} }
if($mesh.primitives.Count -ne 1) { throw 'Expected one primitive' }
$p=$mesh.primitives[0]
if($p.mode -ne 4) { throw 'Expected triangle mesh' }
$positions=@(Accessor $p.attributes.POSITION); $normals=@(Accessor $p.attributes.NORMAL)
$uv=@(Accessor $p.attributes.TEXCOORD_0); $indices=@(Accessor $p.indices)
$low=@(0,0,0);$high=@(0,0,0)
for($axis=0;$axis -lt 3;$axis++) { $bounds=$positions | ForEach-Object { $_[$axis] } | Measure-Object -Minimum -Maximum; $low[$axis]=$bounds.Minimum;$high[$axis]=$bounds.Maximum }
# Original installed plate: 14/16 wide, scaled by 0.7 => 0.6125 blocks.
# Preserve the new model's proportions, centre it on the entity and anchor its underside at Y=0.
$scale=0.6125/[Math]::Max($high[0]-$low[0],$high[2]-$low[2])
$culture=[Globalization.CultureInfo]::InvariantCulture
function Number([double]$value) { $value.ToString('0.0000000',$culture) }
$lines=[Collections.Generic.List[string]]::new()
$lines.Add('# Landmine by FeelsBadMan, CC-BY-4.0; see META-INF/licenses/landmine.txt')
$lines.Add('mtllib landmine.mtl');$lines.Add('o landmine');$lines.Add('usemtl landmine')
foreach($v in $positions) { $x=.5+($v[0]-($low[0]+$high[0])/2)*$scale;$y=($v[1]-$low[1])*$scale;$z=.5+($v[2]-($low[2]+$high[2])/2)*$scale; $lines.Add("v $(Number $x) $(Number $y) $(Number $z)") }
foreach($v in $uv) { $lines.Add("vt $(Number $v[0]) $(Number (1-$v[1]))") }
foreach($v in $normals) { $lines.Add("vn $(Number $v[0]) $(Number $v[1]) $(Number $v[2])") }
for($i=0;$i -lt $indices.Count;$i+=3) { $face=for($j=0;$j -lt 3;$j++) { $n=[int]$indices[$i+$j][0]+1;if($n -gt $positions.Count){throw 'Invalid index'};"$n/$n/$n" };$lines.Add('f '+($face -join ' ')) }
$utf8=[Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllLines((Join-Path $assets 'models/landmine.obj'),$lines,$utf8)
[IO.File]::WriteAllText((Join-Path $assets 'models/landmine.mtl'),"newmtl landmine`nKd 1 1 1`nKa 0 0 0`nd 1`nmap_Kd gungang:item/landmine`n",$utf8)
$material=$g.materials[$p.material];$image=$g.images[$g.textures[$material.pbrMetallicRoughness.baseColorTexture.index].source]
if($image.mimeType -ne 'image/png'){throw 'Expected PNG texture'}
$view=$g.bufferViews[$image.bufferView];$png=New-Object byte[] $view.byteLength
[Array]::Copy($bytes,$binStart+[int]$view.byteOffset,$png,0,$png.Length)
[IO.File]::WriteAllBytes((Join-Path $assets 'textures/item/landmine.png'),$png)
$original=Join-Path $root 'models/landmine';[IO.Directory]::CreateDirectory($original)|Out-Null
Copy-Item -LiteralPath $Source -Destination (Join-Path $original 'landmine.glb') -Force
$credit="Landmine by $($g.asset.extras.author)`nSource: $($g.asset.extras.source)`nLicense: $($g.asset.extras.license)`nModifications: converted GLB triangles to Forge OBJ; uniformly scaled to the existing installed mine footprint; grounded at Y=0; UVs converted; original base-color PNG preserved. PBR normal/metallic/emissive maps are not used.`n"
[IO.File]::WriteAllText((Join-Path $root 'src/main/resources/META-INF/licenses/landmine.txt'),$credit,$utf8)
Write-Output "Imported $($positions.Count) vertices, $($indices.Count/3) triangles. Installed width/depth=0.6125 blocks; height=$(Number (($high[1]-$low[1])*$scale)) blocks."
