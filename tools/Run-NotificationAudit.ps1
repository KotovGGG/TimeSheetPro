param([switch]$SkipBuild)
. "$PSScriptRoot/Android-Environment.ps1"
$Serial = 'emulator-5580'
$AvdName = & $Adb -s $Serial emu avd name
Assert-ExitCode 'Notification audit emulator identity'
if ($AvdName -notcontains 'TimeSheetPro_Pixel9') { throw 'Start Preview.cmd first; only the dedicated preview emulator is supported.' }
$NightModeOutput = & $Adb -s $Serial shell cmd uimode night
Assert-ExitCode 'Read system night mode'
if (($NightModeOutput -join "`n") -notmatch 'Night mode:\s*(no|yes|auto)\s*$') {
    throw 'Unable to preserve the current system night mode; no settings were changed.'
}
$OriginalNightMode = $Matches[1]
try {
    $First = $true
    foreach ($Mode in @('no', 'yes')) {
        & $Adb -s $Serial shell cmd uimode night $Mode
        Assert-ExitCode 'Set system night mode'
        & "$PSScriptRoot/Run-VisualAudit.ps1" -Tests 'com.example.SystemSurfaceAuditTest#notificationActionsAndReturn' -SkipBuild:($SkipBuild -or -not $First)
        $First = $false
    }
} finally {
    & $Adb -s $Serial shell cmd uimode night $OriginalNightMode
    Assert-ExitCode 'Restore system night mode'
}
Write-Host 'Notification actions passed in both system themes. Preview settings and the test session were restored by the native test.'
