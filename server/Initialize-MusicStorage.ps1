param([string]$StateDirectory)
$ErrorActionPreference = 'Stop'
$state = if ($StateDirectory) { (Resolve-Path -LiteralPath $StateDirectory).Path } else { Join-Path $PSScriptRoot '.local' }
New-Item -ItemType Directory -Force $state | Out-Null
$target = Join-Path $state 'music-v2-storage-key.dpapi'
if (Test-Path -LiteralPath $target) {
    Write-Output 'Existing protected music result key preserved. No request sent.'
    exit 0
}
if (Get-ChildItem -LiteralPath $state -Filter '*.enc' -File -Recurse | Select-Object -First 1) {
    throw 'Encrypted results already exist. Restore their original protected key; do not replace it.'
}
$bytes = [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
try {
    $secret = ConvertTo-SecureString -String ([Convert]::ToBase64String($bytes)) -AsPlainText -Force
    $secret | ConvertFrom-SecureString | Set-Content -LiteralPath $target -Encoding utf8
} finally { [Array]::Clear($bytes, 0, $bytes.Length) }
Write-Output 'Local DPAPI music result key prepared. No key displayed; no paid execution enabled.'
