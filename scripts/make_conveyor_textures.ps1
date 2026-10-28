# Flow coordinates follow a straight line or a true quarter-circle. Mapping the
# arrows into that coordinate system prevents overlapping chevrons on bends.
function ConveyorTexture($name,$ports) {
    $size=64;$frames=16;$bmp=New-Object Drawing.Bitmap $size,($size*$frames)
    $palette=@{};foreach($pair in @(@('frame','#202833'),@('rail','#AE9654'),@('edge','#52606B'),@('belt','#303C48'),@('rib','#202B35'),@('arrow','#77C4D6'))){$palette[$pair[0]]=Color $pair[1]}
    $corner=($ports[0]*$ports[2]+$ports[1]*$ports[3]) -eq 0
    try {
        for($y=0;$y -lt $size;$y++){for($x=0;$x -lt $size;$x++){
            $px=$x+.5;$py=$y+.5
            if($corner){
                $cx=32+32*($ports[0]+$ports[2]);$cy=32+32*($ports[1]+$ports[3])
                $rx=$px-$cx;$ry=$py-$cy;$radius=[Math]::Sqrt($rx*$rx+$ry*$ry)
                $sx=-$ports[2];$sy=-$ports[3]
                $s=[Math]::Abs([Math]::Atan2($sx*$ry-$sy*$rx,$sx*$rx+$sy*$ry))/([Math]::PI/2)
                $lateral=$radius-32
            }else{
                $s=(($px-32)*$ports[2]+($py-32)*$ports[3]+32)/64
                $lateral=($px-32)*(-$ports[3])+($py-32)*$ports[2]
            }
            $distance=[Math]::Abs($lateral)
            for($frame=0;$frame -lt $frames;$frame++){
                $color=$palette.frame
                if($distance -lt 25){$color=$palette.rail}
                if($distance -lt 23){$color=$palette.edge}
                if($distance -lt 21){
                    $color=$palette.belt
                    $phase=$s*2-$frame/[double]$frames
                    $rib=($phase*4-[Math]::Floor($phase*4))
                    if($rib -lt .15){$color=$palette.rib}
                    $chevron=$phase-[Math]::Floor($phase)
                    $target=.64-$distance*.026
                    if($distance -le 9 -and [Math]::Abs($chevron-$target) -lt .045){$color=$palette.arrow}
                }
                $bmp.SetPixel($x,($y+$frame*$size),$color)
            }
        }}
        $path=Join-Path $root "assets/gungang/textures/block/$name.png"
        $bmp.Save($path,[Drawing.Imaging.ImageFormat]::Png)
        Json "assets/gungang/textures/block/$name.png.mcmeta" @{animation=@{frametime=1;interpolate=$true}}
    }finally{$bmp.Dispose()}
}
