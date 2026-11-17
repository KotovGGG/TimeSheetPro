param(
    [Parameter(Mandatory=$true)][string]$OldApk,
    [Parameter(Mandatory=$true)][string]$NewApk
)
. "$PSScriptRoot/Android-Environment.ps1"
$OldApk = (Resolve-Path -LiteralPath $OldApk).Path
$NewApk = (Resolve-Path -LiteralPath $NewApk).Path
$Analyzer = Join-Path $env:ANDROID_HOME 'cmdline-tools/19.0/bin/apkanalyzer.bat'
if (-not (Test-Path $Analyzer)) { $Analyzer = Join-Path $env:ANDROID_HOME 'cmdline-tools/latest/bin/apkanalyzer.bat' }
$Signer = Join-Path $env:ANDROID_HOME 'build-tools/36.0.0/apksigner.bat'
function Read-ApkIdentity([string]$Apk) {
    $Package = & $Analyzer manifest application-id $Apk
    Assert-ExitCode 'Read application ID'
    $Version = & $Analyzer manifest version-code $Apk
    Assert-ExitCode 'Read version code'
    $Certificate = & $Signer verify --print-certs $Apk
    Assert-ExitCode 'APK signature verification'
    $Digest = @($Certificate | Select-String '^Signer #\d+ certificate SHA-256 digest:' | ForEach-Object { ($_.Line -split 'digest:')[1].Trim() })
    if ($Digest.Count -ne 1) { throw 'This checker requires a single signer; review multi-signer/rotation compatibility separately.' }
    return @{ Package = "$Package".Trim(); Version = [int]"$Version"; Digest = $Digest[0] }
}
$Old = Read-ApkIdentity $OldApk
$New = Read-ApkIdentity $NewApk
if ($Old.Package -ne 'com.example' -or $New.Package -ne $Old.Package) { throw 'Application IDs do not match the production app. Do not uninstall the old app.' }
if ($New.Version -le $Old.Version) { throw 'The new versionCode must be greater than the installed APK versionCode.' }
if ($New.Digest -ne $Old.Digest) { throw 'Signatures differ. Do not install or uninstall anything. Obtain the original signing key/build channel.' }
Write-Host "Compatible APK identities: $($New.Package), version $($Old.Version) -> $($New.Version)."
Write-Host 'This checks metadata only. Verify the old APK is the installed version, back up data, and rehearse an in-place update before updating the phone.'
