param([string]$ModelFile, [string]$Model2BFile, [string]$Vision2BFile, [string]$Vision4BFile, [string]$Lfm3File, [string]$Serial,
    [ValidatePattern('^[a-zA-Z][a-zA-Z0-9_.]+$')][string]$ApplicationId='io.github.eruditecoder108.ember')
$ErrorActionPreference = 'Stop'
$adbPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$adbArgs = @()
if ($Serial) { $adbArgs += @('-s', $Serial) }
$apkPath = Join-Path $PSScriptRoot 'app/build/outputs/apk/debug/app-debug.apk'
if (-not (Test-Path -LiteralPath $apkPath)) { throw 'Run setup.ps1 first.' }
$metadataPath = Join-Path (Split-Path -Parent $apkPath) 'output-metadata.json'
if (-not (Test-Path -LiteralPath $metadataPath)) { throw 'Build metadata is missing. Run setup.ps1 again.' }
$builtPackage = (Get-Content -Raw -LiteralPath $metadataPath | ConvertFrom-Json).applicationId
if ($builtPackage -ne $ApplicationId) { throw "This APK is $builtPackage. Build with setup.ps1 -ApplicationId $ApplicationId before installing." }
& $adbPath @adbArgs install -r $apkPath
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
& $adbPath @adbArgs shell am force-stop $ApplicationId
function Install-ModelAsset([string]$SourceFile,[string]$TargetName,[string]$ExpectedHash) {
    if (-not $SourceFile) { return }
    if ((Get-FileHash -LiteralPath $SourceFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne $ExpectedHash) { throw "Checksum mismatch: $TargetName" }
    & $adbPath @adbArgs push $SourceFile /data/local/tmp/bonsai-pocket-model.gguf
    if ($LASTEXITCODE -ne 0) { throw 'Model transfer failed.' }
    & $adbPath @adbArgs shell "run-as $ApplicationId mkdir -p files/models"
    & $adbPath @adbArgs shell "cat /data/local/tmp/bonsai-pocket-model.gguf | run-as $ApplicationId sh -c 'cat > files/models/model.installing'"
    if ($LASTEXITCODE -ne 0) { throw 'App-private model installation failed.' }
    $remoteHash = & $adbPath @adbArgs shell run-as $ApplicationId sha256sum files/models/model.installing
    if (-not $remoteHash.StartsWith($ExpectedHash)) { throw 'The phone model copy did not pass checksum verification.' }
    & $adbPath @adbArgs shell run-as $ApplicationId mv files/models/model.installing "files/models/$TargetName"
    if ($LASTEXITCODE -ne 0) { throw 'Could not activate the verified model.' }
    & $adbPath @adbArgs shell rm /data/local/tmp/bonsai-pocket-model.gguf
}
Install-ModelAsset $ModelFile 'bonsai-2-4b.gguf' 'd406b1e912c0d2a4c124ad10bf38a76ae662fdc4d24b176451403dc7b7f30409'
Install-ModelAsset $Model2BFile 'bonsai-2-2b.gguf' 'd5f01a4ecaf49239653308ee732ad1a0c5f802c0ddb997549fc7b694c8b45928'
Install-ModelAsset $Vision2BFile 'bonsai-2-2b-vision.gguf' 'ac9934e8d7966d0d62442c202ec8fdad99a045dea79366c1a4a246a46f5c0533'
Install-ModelAsset $Vision4BFile 'bonsai-2-4b-vision.gguf' 'db939d5f9b7014137810205e91d05e5f54afd72b0b3af9d33bd7d82f7906a55d'
Install-ModelAsset $Lfm3File 'lfm3-1.7b-qad-q4_0.gguf' '43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452'
& $adbPath @adbArgs shell am start -n "$ApplicationId/dev.bonsai.pocket.MainActivity"
