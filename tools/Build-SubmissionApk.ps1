$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Use-SubmissionToolchain.ps1')
$signingDir = Join-Path $projectRoot '.local/signing'
New-Item -ItemType Directory -Force $signingDir | Out-Null
$passPath = Join-Path $signingDir 'password.dpapi'
if (-not(Test-Path -LiteralPath $passPath)) {
    $newPassword = [Guid]::NewGuid().ToString('N')
    ConvertTo-SecureString -String $newPassword -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath $passPath -Encoding utf8
    $newPassword = $null
}
$secret = (Get-Content -LiteralPath $passPath -Raw).Trim() | ConvertTo-SecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
    $env:TODAYSTRACK_SIGNING_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $env:TODAYSTRACK_SIGNING_STORE = Join-Path $signingDir 'submission.p12'
    if (-not(Test-Path -LiteralPath $env:TODAYSTRACK_SIGNING_STORE)) {
        & "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -alias submission -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=TodaysTrack Submission,O=Local Development,C=KR' -storetype PKCS12 -keystore $env:TODAYSTRACK_SIGNING_STORE -storepass:env TODAYSTRACK_SIGNING_PASSWORD *> (Join-Path $signingDir 'initialize.log')
        if ($LASTEXITCODE -ne 0) { throw 'Submission signing key creation failed.' }
    }
    Push-Location (Join-Path $projectRoot 'vehicle')
    try {
        & ./gradlew.bat :app:testReleaseUnitTest :app:assembleRelease --no-daemon --max-workers=2
        if ($LASTEXITCODE -ne 0) { throw 'Release build or affected tests failed.' }
    } finally { Pop-Location }
    New-Item -ItemType Directory -Force (Join-Path $projectRoot 'apk') | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectRoot 'vehicle/app/build/outputs/apk/release/app-release.apk') -Destination (Join-Path $projectRoot 'apk/TodaysTrack-release.apk') -Force
    Write-Output 'Signed release APK prepared at apk/TodaysTrack-release.apk; install/run verification remains separate.'
} finally {
    Remove-Item Env:TODAYSTRACK_SIGNING_PASSWORD,Env:TODAYSTRACK_SIGNING_STORE -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}
