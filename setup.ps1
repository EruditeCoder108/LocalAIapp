param([string]$ApplicationId = 'io.github.eruditecoder108.ember')
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$runtimePath = Join-Path $projectRoot 'vendor/llama.cpp'
$sdkPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
if (-not (Test-Path -LiteralPath $runtimePath)) {
    New-Item -ItemType Directory -Force (Join-Path $projectRoot 'vendor') | Out-Null
    & git clone --depth 1 --branch prism-b10777-e3de715 https://github.com/PrismML-Eng/llama.cpp.git $runtimePath
    if ($LASTEXITCODE -ne 0) { throw 'Could not fetch the pinned PrismML runtime.' }
}
$runtimeRevision = & git -C $runtimePath rev-parse HEAD
if ($runtimeRevision -ne 'e3de715222b2667c54a267441159a5729f11155e') { throw 'The vendor runtime does not match the tested revision e3de715222b2667c54a267441159a5729f11155e.' }
$liquidPath = Join-Path $projectRoot 'vendor/liquid.cpp'
if (-not (Test-Path -LiteralPath $liquidPath)) {
    & git clone --depth 1 --branch b11938 https://github.com/ggml-org/llama.cpp.git $liquidPath
    if ($LASTEXITCODE -ne 0) { throw 'Could not fetch the pinned Liquid runtime.' }
}
$liquidRevision = & git -C $liquidPath rev-parse HEAD
if ($liquidRevision -ne '19cce6919992fdffd92e0ac152caaf1e2b8f254e') { throw 'The Liquid runtime does not match the tested revision 19cce6919992fdffd92e0ac152caaf1e2b8f254e.' }
$phononRoot = Join-Path $projectRoot 'vendor/phonon-android'
$phononPath = Join-Path $phononRoot 'phonon2-android-arm64-v8a-0.2.11'
if (-not (Test-Path -LiteralPath (Join-Path $phononPath 'phonon2.h'))) {
    $runtimeZip = Join-Path $projectRoot 'vendor/phonon-android-runtime.zip'
    try {
        Invoke-WebRequest -Uri 'https://github.com/fermionresearch/phonon/releases/download/android-arm64-v8a-0.2.11/phonon2-android-arm64-v8a-0.2.11.zip' -OutFile $runtimeZip
        if ((Get-FileHash -LiteralPath $runtimeZip -Algorithm SHA256).Hash.ToLowerInvariant() -ne '06d75b2d0e88f4e2194400e9c6c1b5987056702b36a5f95720e56ce8a11d220b') { throw 'Phonon Android runtime checksum mismatch.' }
        Expand-Archive -LiteralPath $runtimeZip -DestinationPath $phononRoot -Force
    } finally { if (Test-Path -LiteralPath $runtimeZip) { Remove-Item -LiteralPath $runtimeZip } }
}
$phononPins = @{
    'libphonon2_enc-android-arm64-v8a.so'='797810ddf895e6845c43b39f7c31eeed0306cb4f1093b55e836f58d3306245db'
    'libphonon2_enc-android-arm64-v8a-base.so'='f5875524f1f4b6d30b42b8bdb79b4c53866a25d4d0e271f109dfaf89f1629a12'
    'libphonon2_tdt-android-arm64-v8a.so'='ec025ba0f5f4ad7b62420f800aaafcb243d4fdc5784c745982b588ad44ee662f'
    'libphonon2_tdt-android-arm64-v8a-base.so'='d3a130f603dce8946822e5c68f177a911b1bab53e3645af958ddf00526d34d48'
    'phonon2.h'='91a37e44cd8829fd68cd5838bd51ec3277074a4efa2db4c97bc8e80b0ba50192'
}
foreach ($entry in $phononPins.GetEnumerator()) {
    if ((Get-FileHash -LiteralPath (Join-Path $phononPath $entry.Key) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.Value) { throw "Pinned Phonon file changed: $($entry.Key)" }
}
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'local.properties'))) {
    if (-not (Test-Path -LiteralPath $sdkPath)) { throw 'Install the Android SDK and set sdk.dir in local.properties.' }
    $escapedSdkPath = $sdkPath.Replace([char]92, [char]47).Replace(':', ([string][char]92 + ':'))
    [IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), "sdk.dir=$escapedSdkPath`n", [Text.UTF8Encoding]::new($false))
}
Push-Location $projectRoot
try {
    & .\gradlew.bat :app:assembleDebug "-PemberApplicationId=$ApplicationId" --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
} finally { Pop-Location }
