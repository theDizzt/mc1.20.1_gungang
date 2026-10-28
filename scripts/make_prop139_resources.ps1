$ErrorActionPreference='Stop'
. "$PSScriptRoot/make_motion_resources.ps1"
# Dried ghast uses the official hydration-3 assets. Regenerate with import_dried_ghast.ps1.
Texture 'block/wildflowers' {param($b,$g)
    $g.Clear([Drawing.Color]::Transparent)
    foreach($p in @(@(10,10),@(23,21),@(8,24))){$x=$p[0];$y=$p[1];$g.FillRectangle([Drawing.Brushes]::White,($x-5),($y-2),11,5);$g.FillRectangle([Drawing.Brushes]::White,($x-2),($y-5),5,11);$g.FillRectangle([Drawing.Brushes]::Gold,($x-2),($y-2),5,5)}
}
$parts=@();foreach($x in 0,8){foreach($z in 0,8){$parts+=@{from=@($x,1,$z);to=@(($x+8),1,($z+8));shade=$false;faces=@{up=@{texture='#flowers';uv=@(0,0,16,16)};down=@{texture='#flowers';uv=@(0,0,16,16)}}}}}
Json 'assets/gungang/models/block/wildflowers.json' @{parent='minecraft:block/block';render_type='minecraft:cutout';textures=@{flowers='gungang:block/wildflowers';particle='gungang:block/wildflowers'};elements=$parts}
Texture 'block/mannequin' {param($b,$g)
    $g.Clear((Color '#C9B997'));$p=Pen '#95846C' 1;$g.DrawRectangle($p,1,1,29,29);$g.DrawLine($p,15,0,15,32);$p.Dispose()
}
$parts=@();foreach($bounds in @(@(4,21,4,12,29,12),@(4,10,6,12,21,10),@(0,10,6,4,21,10),@(12,10,6,16,21,10),@(4,0,6,8,10,10),@(8,0,6,12,10,10))){$parts+=Element $bounds[0..2] $bounds[3..5] '#wood' '#wood'}
Json 'assets/gungang/models/block/mannequin.json' @{parent='minecraft:block/block';textures=@{wood='gungang:block/mannequin';particle='gungang:block/mannequin'};elements=$parts}
foreach($id in 'dried_ghast','wildflowers','mannequin'){
    Json "assets/gungang/blockstates/$id.json" @{variants=@{''=@{model="gungang:block/$id"}}}
    Json "assets/gungang/models/item/$id.json" @{parent="gungang:block/$id"}
    Json "data/gungang/loot_tables/blocks/$id.json" @{type='minecraft:block';pools=@(@{rolls=1;entries=@(@{type='minecraft:item';name="gungang:$id"});conditions=@(@{condition='minecraft:survives_explosion'})})}
}
foreach($lang in 'ko_kr','en_us'){
    $file=Join-Path $root "assets/gungang_acoustics/lang/$lang.json";$s=[IO.File]::ReadAllText($file).TrimEnd()
    if($s.Contains('block.gungang.mannequin')){continue}
    $extra=if($lang -eq 'ko_kr'){@{'block.gungang.mannequin'='마네킹';'block.gungang.dried_ghast'='마른 가스트';'block.gungang.wildflowers'='들꽃'}}else{@{'block.gungang.mannequin'='Mannequin';'block.gungang.dried_ghast'='Dried Ghast';'block.gungang.wildflowers'='Wildflowers'}}
    $j=$extra|ConvertTo-Json;[IO.File]::WriteAllText($file,$s.Substring(0,$s.LastIndexOf('}')).TrimEnd()+","+"`n"+$j.Substring($j.IndexOf('{')+1),$utf8)
}
Write-Output 'Generated u139 custom props.'
