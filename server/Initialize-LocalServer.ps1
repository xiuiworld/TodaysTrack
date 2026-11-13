$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $projectRoot 'tools/Use-SubmissionToolchain.ps1')
$stateDir = Join-Path $PSScriptRoot '.local'
$tlsDir = Join-Path $stateDir 'tls'
$publicDir = Join-Path $projectRoot 'vehicle/app/src/main/res/raw'
New-Item -ItemType Directory -Force $tlsDir,$publicDir | Out-Null
$passwordFile = Join-Path $tlsDir 'password.dpapi'
if (-not(Test-Path -LiteralPath $passwordFile)) {
    $newPassword = [Guid]::NewGuid().ToString('N')
    ConvertTo-SecureString -String $newPassword -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath $passwordFile -Encoding utf8
    $newPassword = $null
}
$secret = (Get-Content -LiteralPath $passwordFile -Raw).Trim() | ConvertTo-SecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
    $env:TODAYSTRACK_TLS_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $keyFile = Join-Path $tlsDir 'server.p12'
    if (-not(Test-Path -LiteralPath $keyFile)) {
        & "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=localhost,OU=TodaysTrack Local,O=Development,C=KR' -ext 'SAN=dns:localhost,ip:127.0.0.1,ip:10.0.2.2' -storetype PKCS12 -keystore $keyFile -storepass:env TODAYSTRACK_TLS_PASSWORD *> (Join-Path $stateDir 'tls-initialize.log')
        if ($LASTEXITCODE -ne 0) { throw 'Local TLS certificate creation failed. See private initialization log.' }
    }
    & "$env:JAVA_HOME/bin/keytool.exe" -exportcert -rfc -alias server -keystore $keyFile -storepass:env TODAYSTRACK_TLS_PASSWORD -file (Join-Path $publicDir 'development_server.pem') *> (Join-Path $stateDir 'tls-export.log')
    if ($LASTEXITCODE -ne 0) { throw 'Local public certificate export failed.' }
} finally {
    Remove-Item Env:TODAYSTRACK_TLS_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}
Write-Output 'Local TLS material prepared. Only the public certificate is packaged in the APK.'
