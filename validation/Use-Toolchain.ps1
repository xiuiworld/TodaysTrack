# Dot-source this file in PowerShell: . .\Use-Toolchain.ps1
$env:JAVA_HOME = 'C:\Users\xiui\Documents\Codex\Tools\Java\jdk-17.0.21+9'
$env:ANDROID_HOME = 'C:\Users\xiui\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + (Join-Path $env:ANDROID_HOME 'platform-tools') + ';' + (Join-Path $env:ANDROID_HOME 'emulator') + ';' + (Join-Path $env:ANDROID_HOME 'cmdline-tools\latest\bin') + ';' + $env:Path
Write-Output 'TodaysTrack toolchain selected for this PowerShell process only.'
