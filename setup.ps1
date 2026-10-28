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
