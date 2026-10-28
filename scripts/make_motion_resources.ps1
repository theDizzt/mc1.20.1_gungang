$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$root=Join-Path $PSScriptRoot '../src/main/resources'
$utf8=New-Object Text.UTF8Encoding $false
function Json($relative,$value){$path=Join-Path $root $relative;[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path))|Out-Null;[IO.File]::WriteAllText($path,($value|ConvertTo-Json -Depth 40),$utf8)}
function Texture($name,$paint){
    $bmp=New-Object Drawing.Bitmap 32,32;$g=[Drawing.Graphics]::FromImage($bmp)
    try{& $paint $bmp $g;$path=Join-Path $root "assets/gungang/textures/$name.png";[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path))|Out-Null;$bmp.Save($path,[Drawing.Imaging.ImageFormat]::Png)}finally{$g.Dispose();$bmp.Dispose()}
}
function Color($hex){[Drawing.ColorTranslator]::FromHtml($hex)}
function Pen($hex,$width){New-Object Drawing.Pen (Color $hex),$width}
function Element($from,$to,$top,$side,$glow=$false){
    $faces=@{};foreach($d in 'north','east','south','west','down'){$faces[$d]=@{texture=$side}}
    $faces.up=@{texture=$top;uv=@(0,0,16,16)}
    if($glow){$faces.up.forge_data=@{block_light=15;sky_light=15;ambient_occlusion=$false}}
    @{from=$from;to=$to;faces=$faces;shade=$false}
}
Texture 'block/conveyor_side' {param($b,$g)
    $g.Clear((Color '#283342'));$p=Pen '#657889' 2;$g.DrawLine($p,0,5,32,5);$g.DrawLine($p,0,26,32,26);$p.Dispose()
    for($x=3;$x -lt 32;$x+=8){$g.FillRectangle([Drawing.Brushes]::Black,$x,11,5,10);$g.FillRectangle([Drawing.Brushes]::Gold,$x,13,3,2)}
}
$ports=@{north_south=@(0,-1,0,1);east_west=@(-1,0,1,0);north_east=@(0,-1,1,0);north_west=@(0,-1,-1,0);south_east=@(0,1,1,0);south_west=@(0,1,-1,0)}
. "$PSScriptRoot/make_conveyor_textures.ps1"
$variants=@{}
foreach($shape in $ports.Keys){foreach($reverse in $false,$true){
    $v=$ports[$shape];if($reverse){$v=@($v[2],$v[3],$v[0],$v[1])}
    $suffix=if($reverse){'reverse'}else{'forward'};$texture="conveyor_${shape}_${suffix}"
    ConveyorTexture $texture $v
    foreach($half in 'bottom','top'){
        $min=if($half -eq 'top'){8}else{0};$model="${texture}_${half}"
        Json "assets/gungang/models/block/$model.json" @{parent='minecraft:block/block';textures=@{top="gungang:block/$texture";side='gungang:block/conveyor_side';particle='gungang:block/conveyor_side'};elements=@((Element @(0,$min,0) @(16,($min+8),16) '#top' '#side'))}
        $variants["half=$half,reversed=$($reverse.ToString().ToLower()),shape=$shape"]=@{model="gungang:block/$model"}
    }
}}
Json 'assets/gungang/blockstates/conveyor_belt.json' @{variants=$variants}
Json 'assets/gungang/models/item/conveyor_belt.json' @{parent='gungang:block/conveyor_north_south_forward_bottom'}
foreach($color in 'blue','lime'){
    $main=if($color -eq 'blue'){'#19C9FF'}else{'#A4FF25'};$dark=if($color -eq 'blue'){'#07528C'}else{'#396B12'}
    Texture "block/${color}_pad" {param($b,$g)
        $g.Clear((Color $dark));$p=Pen $main 1
        for($i=-32;$i -le 64;$i+=8){$g.DrawLine($p,$i,0,($i+32),32);$g.DrawLine($p,$i,0,($i-32),32)};$p.Dispose()
        $p=Pen $main 2;$g.DrawRectangle($p,12,12,8,8);$p.Dispose();$g.FillRectangle([Drawing.Brushes]::White,15,15,2,2)
    }
    Texture "block/${color}_pad_edge" {param($b,$g) $g.Clear((Color $main));$p=Pen '#E5FFF9' 2;$g.DrawLine($p,0,16,32,16);$p.Dispose()}
    $name="${color}_jump_pad"
    Json "assets/gungang/models/block/$name.json" @{parent='minecraft:block/block';ambientocclusion=$false;textures=@{top="gungang:block/${color}_pad";side='gungang:block/conveyor_side';particle="gungang:block/${color}_pad"};elements=@((Element @(0,0,0) @(16,2,16) '#top' '#side' $true))}
    Json "assets/gungang/models/block/${name}_edge.json" @{parent='minecraft:block/block';ambientocclusion=$false;textures=@{edge="gungang:block/${color}_pad_edge"};elements=@((Element @(0,2,0) @(16,2.02,1) '#edge' '#edge' $true))}
    $parts=@(@{apply=@{model="gungang:block/$name"}});$angle=0
    foreach($d in 'north','east','south','west'){$parts+=@{when=@{$d='false'};apply=@{model="gungang:block/${name}_edge";y=$angle}};$angle+=90}
    Json "assets/gungang/blockstates/$name.json" @{multipart=$parts}
    Json "assets/gungang/models/item/$name.json" @{parent="gungang:block/$name"}
    Json "assets/gungang/particles/${color}_hardlight.json" @{textures=@('gungang:hardlight')}
}
Texture 'particle/hardlight' {param($b,$g)
    $g.Clear([Drawing.Color]::Transparent);$p=Pen '#FFFFFF' 2;$g.DrawRectangle($p,10,8,12,16);$g.DrawLine($p,16,2,16,10);$g.DrawLine($p,16,22,16,30);$p.Dispose()
}
Texture 'item/wrench' {param($b,$g)
    $g.Clear([Drawing.Color]::Transparent);$p=Pen '#202B38' 8;$g.DrawLine($p,7,26,24,8);$p.Dispose();$p=Pen '#B4CDD9' 5;$g.DrawLine($p,7,26,24,8);$p.Dispose()
    $p=Pen '#B4CDD9' 4;$g.DrawArc($p,17,1,12,12,0,270);$p.Dispose();$g.FillRectangle([Drawing.Brushes]::Cyan,10,18,3,3)
}
Json 'assets/gungang/models/item/wrench.json' @{parent='minecraft:item/handheld';textures=@{layer0='gungang:item/wrench'}}
foreach($id in 'conveyor_belt','blue_jump_pad','lime_jump_pad'){
    Json "data/gungang/loot_tables/blocks/$id.json" @{type='minecraft:block';pools=@(@{rolls=1;entries=@(@{type='minecraft:item';name="gungang:$id"});conditions=@(@{condition='minecraft:survives_explosion'})})}
}
Json 'data/gungang/recipes/wrench.json' @{type='minecraft:crafting_shaped';pattern=@('I I',' I ',' I ');key=@{I=@{item='minecraft:iron_ingot'}};result=@{item='gungang:wrench'}}
Json 'data/gungang/recipes/conveyor_belt.json' @{type='minecraft:crafting_shaped';pattern=@('KKK','IRI');key=@{K=@{item='minecraft:dried_kelp'};I=@{item='minecraft:iron_ingot'};R=@{item='minecraft:redstone'}};result=@{item='gungang:conveyor_belt';count=6}}
foreach($c in 'blue','lime'){Json "data/gungang/recipes/${c}_jump_pad.json" @{type='minecraft:crafting_shaped';pattern=@('DGD','IRI');key=@{D=@{item="minecraft:${c}_dye"};G=@{item='minecraft:glowstone_dust'};I=@{item='minecraft:iron_ingot'};R=@{item='minecraft:redstone'}};result=@{item="gungang:${c}_jump_pad";count=4}}}
Write-Output 'Generated conveyor, wrench, connected pad models and hardlight textures.'
