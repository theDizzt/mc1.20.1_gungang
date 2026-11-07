Add-Type -AssemblyName System.Drawing
$root = (Resolve-Path "$PSScriptRoot/../..").Path
$fonts = [System.Drawing.Text.PrivateFontCollection]::new()
$fonts.AddFontFile("$root/font/gungang.ttf")
function Color($hex) { return [System.Drawing.ColorTranslator]::FromHtml($hex) }
$gold = Color '#CFB957'
foreach ($size in @(256,64)) {
    $bitmap = [System.Drawing.Bitmap]::new($size,$size)
    $g = [System.Drawing.Graphics]::FromImage($bitmap)
    $g.Clear((Color '#141611'))
    $g.ScaleTransform(($size/64.0),($size/64.0))
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
    $grid = [System.Drawing.Pen]::new((Color '#1D2118'),0.25)
    for ($i=8;$i -lt 64;$i+=8) {
        $g.DrawLine($grid,$i,0,$i,64)
        $g.DrawLine($grid,0,$i,64,$i)
    }
    $pen = [System.Drawing.Pen]::new($gold,1)
    foreach ($coords in @(
        @(4,24,4,10,10,4,25,4),
        @(39,4,54,4,60,10,60,24),
        @(4,40,4,54,10,60,24,60),
        @(40,60,54,60,60,54,60,40)
    )) {
        $points = [System.Drawing.PointF[]]@(
            [System.Drawing.PointF]::new($coords[0],$coords[1]),
            [System.Drawing.PointF]::new($coords[2],$coords[3]),
            [System.Drawing.PointF]::new($coords[4],$coords[5]),
            [System.Drawing.PointF]::new($coords[6],$coords[7]))
        $g.DrawLines($pen,$points)
    }
    $g.DrawLine($pen,28,4,30,4)
    $g.DrawLine($pen,34,4,36,4)
    $g.DrawLine($pen,32,1,32,2)
    $g.DrawLine($pen,32,6,32,7)
    $brush = [System.Drawing.SolidBrush]::new($gold)
    $g.FillRectangle($brush,32,4,1,1)
    $glyph = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $glyph.AddString('G',$fonts.Families[0],0,100,[System.Drawing.PointF]::new(0,0),[System.Drawing.StringFormat]::GenericTypographic)
    $bounds = $glyph.GetBounds()
    $scale = 38.0/$bounds.Height
    $left = (64-$bounds.Width*$scale)/2
    $matrix = [System.Drawing.Drawing2D.Matrix]::new($scale,0,0,$scale,($left-$bounds.X*$scale),(13-$bounds.Y*$scale))
    $glyph.Transform($matrix)
    $shadow = $glyph.Clone()
    $offset = [System.Drawing.Drawing2D.Matrix]::new()
    $offset.Translate(0,2)
    $shadow.Transform($offset)
    $shadowBrush = [System.Drawing.SolidBrush]::new((Color '#77682E'))
    $g.FillPath($shadowBrush,$shadow)
    $gradient = [System.Drawing.Drawing2D.LinearGradientBrush]::new($glyph.GetBounds(),(Color '#E5D486'),$gold,90)
    $g.FillPath($gradient,$glyph)
    foreach ($x in @(28,32,36)) {
        $g.FillPolygon($brush,[System.Drawing.PointF[]]@(
            [System.Drawing.PointF]::new($x,58),[System.Drawing.PointF]::new(($x+2),58),
            [System.Drawing.PointF]::new($x,61),[System.Drawing.PointF]::new(($x-2),61)))
    }
    $bitmap.Save("$PSScriptRoot/gungang-icon-$size.png",[System.Drawing.Imaging.ImageFormat]::Png)
    $gradient.Dispose();$shadowBrush.Dispose();$offset.Dispose();$shadow.Dispose();$matrix.Dispose()
    $glyph.Dispose();$brush.Dispose();$pen.Dispose();$grid.Dispose();$g.Dispose();$bitmap.Dispose()
}
$fonts.Dispose()
