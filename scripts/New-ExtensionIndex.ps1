#Requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $MetadataPath,
    [Parameter(Mandatory)][string] $ApkPath,
    [Parameter(Mandatory)][string] $JarPath,
    [Parameter(Mandatory)][string] $Repository,
    [Parameter(Mandatory)][string] $Tag,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{64}$')][string] $SigningKey,
    [string] $OutputDirectory = 'release'
)

$ErrorActionPreference = 'Stop'

# Wire fields follow .github/scripts/index.proto. Only varints and length-delimited
# fields occur in this index, so generating it needs no extra runtime or package.
function Write-Varint([IO.Stream] $Stream, [ulong] $Value) {
    while ($Value -ge 128) {
        $Stream.WriteByte([byte](($Value -band 127) -bor 128))
        $Value = $Value -shr 7
    }
    $Stream.WriteByte([byte]$Value)
}

function New-Message([scriptblock] $Body) {
    $stream = [IO.MemoryStream]::new()
    try {
        & $Body $stream
        return ,$stream.ToArray()
    } finally {
        $stream.Dispose()
    }
}

function Write-Number([IO.Stream] $Stream, [int] $Field, [ulong] $Value) {
    Write-Varint $Stream ([ulong]($Field -shl 3))
    Write-Varint $Stream $Value
}

function Write-Bytes([IO.Stream] $Stream, [int] $Field, [byte[]] $Value) {
    Write-Varint $Stream ([ulong](($Field -shl 3) -bor 2))
    Write-Varint $Stream ([ulong]$Value.Length)
    $Stream.Write($Value, 0, $Value.Length)
}

function Write-Text([IO.Stream] $Stream, [int] $Field, [string] $Value) {
    Write-Bytes $Stream $Field ([Text.Encoding]::UTF8.GetBytes($Value))
}

$metadata = Get-Content -LiteralPath $MetadataPath -Raw | ConvertFrom-Json
if ($metadata.packageName -ne 'eu.kanade.tachiyomi.extension.all.personallanraragi') {
    throw 'Expected only the Personal LANraragi extension.'
}
$releaseUrl = "https://github.com/$Repository/releases/download/$Tag"
$apkName = Split-Path -Leaf $ApkPath
$jarName = Split-Path -Leaf $JarPath
$resources = [ordered]@{
    apkUrl = "$releaseUrl/$apkName"
    iconUrl = "$releaseUrl/icon.png"
    jarUrl = "$releaseUrl/$jarName"
}
$resourceBytes = New-Message {
    param($s)
    Write-Text $s 1 $resources.apkUrl
    Write-Text $s 2 $resources.iconUrl
    Write-Text $s 501 $resources.jarUrl
}
$extensionBytes = New-Message {
    param($s)
    Write-Text $s 1 $metadata.name
    Write-Text $s 2 $metadata.packageName
    Write-Bytes $s 3 $resourceBytes
    Write-Text $s 4 $metadata.extensionLib
    Write-Number $s 5 $metadata.versionCode
    Write-Text $s 6 $metadata.versionName
    Write-Number $s 7 $metadata.contentWarning
    foreach ($source in $metadata.sources) {
        $sourceBytes = New-Message {
            param($sourceStream)
            Write-Number $sourceStream 1 ([ulong]$source.id)
            Write-Text $sourceStream 2 $source.name
            Write-Text $sourceStream 3 $source.lang
            Write-Text $sourceStream 4 $source.baseUrl
            foreach ($mirror in $source.mirrorUrls) { Write-Text $sourceStream 5 $mirror }
        }
        Write-Bytes $s 8 $sourceBytes
    }
}
$extensionList = New-Message { param($s) Write-Bytes $s 1 $extensionBytes }
$contact = New-Message { param($s) Write-Text $s 1 "https://github.com/$Repository" }
$indexBytes = New-Message {
    param($s)
    Write-Text $s 1 'Personal LANraragi'
    Write-Text $s 2 'PERSONAL'
    Write-Text $s 3 $SigningKey.ToLowerInvariant()
    Write-Bytes $s 4 $contact
    Write-Bytes $s 101 $extensionList
}

$null = New-Item -ItemType Directory -Path $OutputDirectory -Force
$indexPath = Join-Path $OutputDirectory 'index.pb'
$file = [IO.File]::Create($indexPath)
try {
    $gzip = [IO.Compression.GZipStream]::new($file, [IO.Compression.CompressionLevel]::Optimal, $true)
    try { $gzip.Write($indexBytes, 0, $indexBytes.Length) } finally { $gzip.Dispose() }
} finally { $file.Dispose() }

$warningNames = @('CONTENT_WARNING_UNSPECIFIED', 'CONTENT_WARNING_SAFE', 'CONTENT_WARNING_MIXED', 'CONTENT_WARNING_NSFW')
$index = [ordered]@{
    name = 'Personal LANraragi'
    badgeLabel = 'PERSONAL'
    signingKey = $SigningKey.ToLowerInvariant()
    contact = @{ website = "https://github.com/$Repository" }
    extensionList = @{
        extensions = @(@{
            name = $metadata.name
            packageName = $metadata.packageName
            resources = $resources
            extensionLib = $metadata.extensionLib
            versionCode = [string]$metadata.versionCode
            versionName = $metadata.versionName
            contentWarning = $warningNames[$metadata.contentWarning]
            sources = @($metadata.sources | ForEach-Object {
                @{
                    id = [string]$_.id
                    name = $_.name
                    language = $_.lang
                    homeUrl = $_.baseUrl
                    mirrorUrls = @($_.mirrorUrls)
                }
            })
        })
    }
}
$index | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'index.json') -Encoding utf8NoBOM
Copy-Item -LiteralPath $ApkPath, $JarPath -Destination $OutputDirectory
$icon = Join-Path $PSScriptRoot '../src/all/lanraragi/res/mipmap-xhdpi/ic_launcher.png'
Copy-Item -LiteralPath $icon -Destination (Join-Path $OutputDirectory 'icon.png')
Write-Output "Mihon repository URL: https://github.com/$Repository/releases/latest/download/index.pb"
