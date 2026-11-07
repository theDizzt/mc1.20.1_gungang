Add-Type -AssemblyName System.Drawing
$root = (Resolve-Path "$PSScriptRoot/../..").Path
$fonts = New-Object System.Drawing.Text.PrivateFontCollection
$fonts.AddFontFile("$root/font/gungang.ttf")
$fonts.AddFontFile("$root/font/Galmuri11-Bold.ttf")
$fonts.Families | ForEach-Object { Write-Output $_.Name }
$gold = [System.Drawing.ColorTranslator]::FromHtml('#CFB957')
$ivory = [System.Drawing.ColorTranslator]::FromHtml('#E8E7DD')
$dark = [System.Drawing.ColorTranslator]::FromHtml('#171811')
function Brush($c) { return [System.Drawing.SolidBrush]::new($c) }
function TextPath($text, $family, $size, $x, $y, $width) {
    $p = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $p.AddString($text, $family, 0, $size, [System.Drawing.PointF]::new(0,0), [System.Drawing.StringFormat]::GenericTypographic)
    $b = $p.GetBounds()
    $scale = $width / $b.Width
    $m = [System.Drawing.Drawing2D.Matrix]::new($scale,0,0,$scale,($x-$b.X*$scale),($y-$b.Y*$scale))
    $p.Transform($m)
    $m.Dispose()
    return ,$p
}
$titleFamily = $fonts.Families | Where-Object { $_.Name -notmatch 'Galmuri' } | Select-Object -First 1
$subFamily = $fonts.Families | Where-Object { $_.Name -match 'Galmuri' } | Select-Object -First 1
$bmp = [System.Drawing.Bitmap]::new(1600,600)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'
$g.Clear([System.Drawing.Color]::Transparent)
$pen = [System.Drawing.Pen]::new($gold,6)
# Open angular frame echoes the loadout selection borders.
$g.DrawLines($pen,[System.Drawing.Point[]]@([System.Drawing.Point]::new(100,230),[System.Drawing.Point]::new(100,112),[System.Drawing.Point]::new(138,74),[System.Drawing.Point]::new(650,74)))
$g.DrawLines($pen,[System.Drawing.Point[]]@([System.Drawing.Point]::new(950,74),[System.Drawing.Point]::new(1462,74),[System.Drawing.Point]::new(1500,112),[System.Drawing.Point]::new(1500,230)))
$g.DrawLines($pen,[System.Drawing.Point[]]@([System.Drawing.Point]::new(100,380),[System.Drawing.Point]::new(100,490),[System.Drawing.Point]::new(138,528),[System.Drawing.Point]::new(620,528)))
$g.DrawLines($pen,[System.Drawing.Point[]]@([System.Drawing.Point]::new(980,528),[System.Drawing.Point]::new(1462,528),[System.Drawing.Point]::new(1500,490),[System.Drawing.Point]::new(1500,380)))
# Minimal reticle centered over the wordmark.
$g.DrawLine($pen,740,74,780,74)
$g.DrawLine($pen,820,74,860,74)
$g.DrawLine($pen,800,42,800,62)
$g.DrawLine($pen,800,86,800,102)
$g.FillRectangle((Brush $gold),794,68,12,12)
$title = TextPath 'GUNGANG' $titleFamily 200 160 155 1280
$bounds = $title.GetBounds()
$shadow = $title.Clone()
$m = [System.Drawing.Drawing2D.Matrix]::new()
$m.Translate(0,12)
$shadow.Transform($m)
$g.DrawPath([System.Drawing.Pen]::new($dark,14),$shadow)
$g.FillPath((Brush ([System.Drawing.ColorTranslator]::FromHtml('#77682E'))),$shadow)
$g.DrawPath([System.Drawing.Pen]::new($dark,10),$title)
$gradient = [System.Drawing.Drawing2D.LinearGradientBrush]::new($bounds,[System.Drawing.ColorTranslator]::FromHtml('#E5D486'),$gold,90)
$g.FillPath($gradient,$title)
$subY = [Math]::Max(386, $bounds.Bottom + 44)
$subtitle = -join (@(0xC640,0xC815,0xADDC,0xC758,0x20,0xBE44,0xB9AC,0xADF8,0xB77C,0xC6B4,0xB4DC) | ForEach-Object { [char]$_ })
$sub = TextPath $subtitle $subFamily 60 420 $subY 760
$g.DrawPath([System.Drawing.Pen]::new($dark,6),$sub)
$g.FillPath((Brush $ivory),$sub)
$lineY = $sub.GetBounds().Top + $sub.GetBounds().Height / 2
$g.FillRectangle((Brush $gold),160,($lineY-3),200,6)
$g.FillRectangle((Brush $gold),1240,($lineY-3),200,6)
# Three short bottom chevrons provide a military insignia finish.
foreach ($x in @(758,790,822)) {
    $g.FillPolygon((Brush $gold),[System.Drawing.Point[]]@([System.Drawing.Point]::new($x,516),[System.Drawing.Point]::new(($x+20),516),[System.Drawing.Point]::new(($x+8),540),[System.Drawing.Point]::new(($x-12),540)))
}
$bmp.Save("$PSScriptRoot/gungang-logo-transparent.png",[System.Drawing.Imaging.ImageFormat]::Png)
$preview = [System.Drawing.Bitmap]::new(1600,600)
$pg = [System.Drawing.Graphics]::FromImage($preview)
$pg.Clear($dark)
$pg.DrawImageUnscaled($bmp,0,0)
$preview.Save("$PSScriptRoot/gungang-logo-dark.png",[System.Drawing.Imaging.ImageFormat]::Png)
$pg.Dispose(); $preview.Dispose(); $g.Dispose(); $bmp.Dispose(); $fonts.Dispose()
