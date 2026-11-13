param(
    [ValidateSet('vocal', 'instrumental', 'sdk-journey')][string]$Sample = 'vocal',
    [string]$JourneyFile,
    [switch]$Generate
)
$ErrorActionPreference = 'Stop'
$probeArgs = @('--sample', $Sample)
if ($JourneyFile) { $probeArgs += @('--journey-file', $JourneyFile) }
if (-not $Generate) {
    & python (Join-Path $PSScriptRoot 'probe.py') @probeArgs
    exit $LASTEXITCODE
}
$keyPath = Join-Path $PSScriptRoot '.local\gemini-key.dpapi'
if (-not(Test-Path -LiteralPath $keyPath)) { throw 'Run Set-MusicApiKey.ps1 first. Never paste the API key into chat.' }
$secret = (Get-Content -LiteralPath $keyPath -Raw).Trim() | ConvertTo-SecureString
$keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
$oldKey = [Environment]::GetEnvironmentVariable('GEMINI_API_KEY')
try {
    $env:GEMINI_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
    & python (Join-Path $PSScriptRoot 'probe.py') @probeArgs --generate
    $probeExit = $LASTEXITCODE
} finally {
    if ($null -eq $oldKey) { Remove-Item Env:GEMINI_API_KEY -ErrorAction SilentlyContinue }
    else { $env:GEMINI_API_KEY = $oldKey }
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer)
}
exit $probeExit


