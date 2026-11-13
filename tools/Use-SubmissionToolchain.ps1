# Process-local setup. Honors the developer's existing toolchain; never changes user/system PATH.
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME -or -not(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    $knownJdk = Join-Path $env:USERPROFILE 'Documents/Codex/Tools/Java/jdk-17.0.21+9'
    if (Test-Path -LiteralPath (Join-Path $knownJdk 'bin/java.exe')) { $env:JAVA_HOME = $knownJdk }
    else { throw 'Set JAVA_HOME to an installed JDK 17 before building or running the local server.' }
}
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + (Join-Path $env:ANDROID_HOME 'platform-tools') + ';' + $env:Path
