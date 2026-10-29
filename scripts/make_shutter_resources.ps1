$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$assets=Join-Path $root 'src/main/resources/assets/gungang'
$utf8=[Text.UTF8Encoding]::new($false)
Add-Type -AssemblyName System.Drawing
foreach($kind in @('panel','frame')){
    $image=[Drawing.Bitmap]::new(16,16)
    for($y=0;$y -lt 16;$y++){for($x=0;$x -lt 16;$x++){
        $value=if($kind -eq 'panel'){145+($y%4)*3+(($x*7+$y*3)%3)}else{66+($y%4)*3}
        $image.SetPixel($x,$y,[Drawing.Color]::FromArgb(255,$value,$value+3,$value+5))
    }}
    $image.Save((Join-Path $assets "textures/block/shutter_$kind.png"),[Drawing.Imaging.ImageFormat]::Png);$image.Dispose()
}
function Cube($from,$to,$texture){
    $faces=@{};foreach($side in @('north','south','east','west','up','down')){$faces[$side]=@{texture=$texture;uv=@(0,0,16,16)}}
    @{from=$from;to=$to;faces=$faces}
}
$elements=@()
for($i=0;$i -lt 8;$i++){
    $elements+=Cube @(0,($i*2),6.7) @(16,($i*2+2),9.3) '#panel'
    $elements+=Cube @(0,($i*2),6.6) @(16,($i*2+.25),9.4) '#frame'
}
$elements+=Cube @(0,0,6.5) @(1,16,9.5) '#frame'
$elements+=Cube @(15,0,6.5) @(16,16,9.5) '#frame'
$elements+=Cube @(0,14,6.5) @(16,16,9.5) '#frame'
$model=@{parent='minecraft:block/block';textures=@{particle='gungang:block/shutter_panel';panel='gungang:block/shutter_panel';frame='gungang:block/shutter_frame'};elements=$elements;display=@{
    gui=@{rotation=@(20,35,0);scale=@(.8,.8,.8)}
    ground=@{translation=@(0,3,0);scale=@(.25,.25,.25)}
    fixed=@{scale=@(.8,.8,.8)}
    thirdperson_righthand=@{rotation=@(75,45,0);translation=@(0,2.5,0);scale=@(.375,.375,.375)}
    firstperson_righthand=@{rotation=@(0,45,0);scale=@(.4,.4,.4)}
}}
[IO.File]::WriteAllText((Join-Path $assets 'models/block/shutter.json'),($model|ConvertTo-Json -Depth 12)+"`n",$utf8)
foreach($locale in @('ko_kr','en_us')){
    $path=Join-Path $assets "lang/$locale.json";$data=[IO.File]::ReadAllText($path)|ConvertFrom-Json
    $name=if($locale -eq 'ko_kr'){[string]([char]0xC154)+[char]0xD130}else{'Rolling Shutter'}
    $data|Add-Member -NotePropertyName 'block.gungang.shutter' -NotePropertyValue $name -Force
    [IO.File]::WriteAllText($path,($data|ConvertTo-Json -Depth 8)+"`n",$utf8)
}
Write-Output 'Generated shutter models, steel textures and translations.'
