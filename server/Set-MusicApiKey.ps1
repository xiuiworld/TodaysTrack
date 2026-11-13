$ErrorActionPreference = 'Stop'
$stateDir = Join-Path $PSScriptRoot '.local'
New-Item -ItemType Directory -Force $stateDir | Out-Null
$apiKey = Read-Host 'Music API key (hidden local input; never paste into chat)' -AsSecureString
if ($apiKey.Length -eq 0) { throw 'No key entered.' }
$apiKey | ConvertFrom-SecureString | Set-Content -LiteralPath (Join-Path $stateDir 'music-key.dpapi') -Encoding utf8
Write-Output 'Protected local key saved. No provider request was made; dispatch remains separately controlled.'
