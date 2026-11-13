$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
& python -m pip install --disable-pip-version-check --target (Join-Path $PSScriptRoot '.local/media') imageio-ffmpeg==0.6.0
if ($LASTEXITCODE -ne 0) { throw 'Project-local FFmpeg install failed.' }
Write-Output 'FFmpeg installed in tools/.local/media. Global PATH was not changed.'
