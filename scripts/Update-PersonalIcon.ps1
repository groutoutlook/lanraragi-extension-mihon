#Requires -Version 7.0
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$resourceRoot = Join-Path $PSScriptRoot '../src/all/lanraragi/res'
foreach ($file in Get-ChildItem -LiteralPath $resourceRoot -Recurse -Filter ic_launcher.png) {
    $inputImage = [Drawing.Bitmap]::new($file.FullName)
    $bitmap = [Drawing.Bitmap]::new($inputImage.Width, $inputImage.Height)
    try {
        for ($y = 0; $y -lt $bitmap.Height; $y++) {
            for ($x = 0; $x -lt $bitmap.Width; $x++) {
                $color = $inputImage.GetPixel($x, $y)
                $bitmap.SetPixel($x, $y, $color)
            }
        }
        $inputImage.Dispose()
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        $brush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(255, 14, 116, 144))
        $textBrush = [Drawing.SolidBrush]::new([Drawing.Color]::White)
        $badgeSize = [single]($bitmap.Width * 0.33)
        $font = [Drawing.Font]::new('Arial', [single]($badgeSize * 0.64), [Drawing.FontStyle]::Bold, [Drawing.GraphicsUnit]::Pixel)
        $format = [Drawing.StringFormat]::new()
        try {
            $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
            $graphics.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit
            $left = [single]($bitmap.Width * 0.63)
            $top = [single]($bitmap.Height * 0.63)
            $graphics.FillEllipse($brush, $left, $top, $badgeSize, $badgeSize)
            $format.Alignment = [Drawing.StringAlignment]::Center
            $format.LineAlignment = [Drawing.StringAlignment]::Center
            $rect = [Drawing.RectangleF]::new($left, $top, $badgeSize, $badgeSize)
            $graphics.DrawString('P', $font, $textBrush, $rect, $format)
        } finally {
            $graphics.Dispose()
            $brush.Dispose()
            $textBrush.Dispose()
            $font.Dispose()
            $format.Dispose()
        }
        $bitmap.Save($file.FullName, [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $inputImage.Dispose()
        $bitmap.Dispose()
    }
}
