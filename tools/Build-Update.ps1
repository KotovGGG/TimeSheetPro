param([Parameter(Mandatory=$true)][ValidateRange(2,2100000000)][int]$VersionCode)
. "$PSScriptRoot/Android-Environment.ps1"
foreach ($Name in @('KEYSTORE_PATH','STORE_PASSWORD','KEY_PASSWORD')) {
    if (-not [Environment]::GetEnvironmentVariable($Name)) { throw "Set $Name in this local session using the original signing credentials. Do not send passwords in chat." }
}
if (-not (Test-Path -LiteralPath $env:KEYSTORE_PATH)) { throw 'The original signing keystore does not exist at KEYSTORE_PATH.' }
& ./gradlew.bat :app:assembleRelease "-PappVersionCode=$VersionCode" --no-daemon --console=plain
Assert-ExitCode 'Signed update build'
Write-Host 'Run tools/Test-Upgrade.ps1 with the old and new APKs before installation. No phone installation was performed.'
