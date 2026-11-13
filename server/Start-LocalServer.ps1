param([switch]$EnableMusicKey)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $projectRoot 'tools/Use-SubmissionToolchain.ps1')
$stateDir = Join-Path $PSScriptRoot '.local'
$tlsPasswordFile = Join-Path $stateDir 'tls/password.dpapi'
$keyPath = Join-Path $stateDir 'music-key.dpapi'
if (-not(Test-Path -LiteralPath $keyPath)) { $keyPath = Join-Path $projectRoot 'validation/music-probe/.local/gemini-key.dpapi' }
$tlsPointer = [IntPtr]::Zero
$keyPointer = [IntPtr]::Zero
try {
    $tlsSecret = (Get-Content -LiteralPath $tlsPasswordFile -Raw).Trim() | ConvertTo-SecureString
    $tlsPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($tlsSecret)
    $env:TODAYSTRACK_TLS_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($tlsPointer)
    if ($EnableMusicKey) {
        if (-not(Test-Path -LiteralPath $keyPath)) { throw 'Existing protected music key is unavailable.' }
        $musicSecret = (Get-Content -LiteralPath $keyPath -Raw).Trim() | ConvertTo-SecureString
        $keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($musicSecret)
        $env:GEMINI_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
    }
    $env:TODAYSTRACK_SERVER_STATE = $stateDir
    $env:TODAYSTRACK_FIXTURE_AUDIO = Join-Path $PSScriptRoot 'fixtures/reference.mp3'
    $env:TODAYSTRACK_FFMPEG = Join-Path $projectRoot 'tools/.local/media/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe'
    & (Join-Path $PSScriptRoot 'build/install/TodaysTrackServer/bin/TodaysTrackServer.bat')
    exit $LASTEXITCODE
} finally {
    Remove-Item Env:TODAYSTRACK_TLS_PASSWORD,Env:GEMINI_API_KEY -ErrorAction SilentlyContinue
    if ($tlsPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($tlsPointer) }
    if ($keyPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer) }
}
