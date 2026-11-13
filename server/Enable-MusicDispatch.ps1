param([Parameter(Mandatory=$true)][string]$ApprovalFile)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
# This is an audit/configuration gate, not a substitute for actual current user authorization.
$approval = Get-Content -LiteralPath $ApprovalFile -Raw | ConvertFrom-Json
if ($approval.budgetUsd -ne 5 -or $approval.paidLyricsApiRequests -ne 0 -or $approval.topUpApproved -or $approval.automaticPaymentApproved -or [string]::IsNullOrWhiteSpace($approval.userInstruction)) { throw 'Approval bounds do not match this authorized local run.' }
$fixture = Get-Content -LiteralPath (Join-Path $projectRoot 'validation/evidence/s3-fixture-gate.json') -Raw | ConvertFrom-Json
$latest = Get-Content -LiteralPath (Join-Path $projectRoot 'validation/journey-probe/offline-latest.json') -Raw | ConvertFrom-Json
$offline = Get-Content -LiteralPath (Join-Path $latest.run_path 'result.json') -Raw | ConvertFrom-Json
if (-not $fixture.saveAndOfflineReplayVerified -or -not $offline.offlineDefaultNetworkAbsent -or -not $offline.paused -or $offline.package -ne 'dev.todaystrack.vehicle.submission') { throw 'Release fixture save/restart/offline replay prerequisites are not verified.' }
$control = [ordered]@{
    enabled=$true;budgetUsd=5;model='lyria-3.5';musicRequestCountLimit=$null;paidLyricsApiRequests=0
    reservationPerAttemptUsd=0.10;historicalConservativeReservationUsd=0.30
    automaticResendAfterUncertainAcceptance=$false;lyrics='Non-paid factual template'
    approvalSource=$approval.source;userInstruction=$approval.userInstruction
    fixtureEvidence='validation/evidence/s3-fixture-gate.json';releaseOfflineEvidence=$latest.run_path
}
$control | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $PSScriptRoot '.local/dispatch.json') -Encoding utf8
Write-Output 'Local music dispatch enabled within the existing total USD5 ledger. No request was sent by this command.'
