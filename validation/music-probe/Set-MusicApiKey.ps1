$ErrorActionPreference = 'Stop'
$localDir = Join-Path $PSScriptRoot '.local'
New-Item -ItemType Directory -Path $localDir -Force | Out-Null
$apiKey = Read-Host 'Google AI Studio API key (hidden input)' -AsSecureString
if ($apiKey.Length -eq 0) { throw 'No key entered.' }
$keyPath = Join-Path $localDir 'gemini-key.dpapi'
$apiKey | ConvertFrom-SecureString | Set-Content -LiteralPath $keyPath -Encoding utf8
Write-Output 'API key saved with Windows user encryption. The key was not printed.'
Write-Output 'Billing must already be enabled on the corresponding Google project.'

