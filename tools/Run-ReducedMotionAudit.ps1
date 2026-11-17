param([switch]$SkipBuild)
. "$PSScriptRoot/Android-Environment.ps1"
$Serial = 'emulator-5580'
$AvdName = & $Adb -s $Serial emu avd name
if ($LASTEXITCODE -ne 0 -or $AvdName -notcontains 'TimeSheetPro_Pixel9') {
    throw 'Start Preview.cmd first. Motion tests only run on the dedicated preview emulator.'
}
if (-not $SkipBuild) {
    & ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
    Assert-ExitCode 'Reduced-motion test build'
}
foreach ($Apk in @('app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    & $Adb -s $Serial install -r (Join-Path $ProjectRoot $Apk)
    Assert-ExitCode 'Reduced-motion test installation'
}
$Original = @{}
foreach ($Setting in @('animator_duration_scale', 'transition_animation_scale', 'window_animation_scale')) {
    $Original[$Setting] = (& $Adb -s $Serial shell settings get global $Setting).Trim()
    Assert-ExitCode 'Read animation scale'
}
$PreviousPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    foreach ($Setting in $Original.Keys) {
        & $Adb -s $Serial shell settings put global $Setting 0.0
        Assert-ExitCode 'Disable animations'
    }
    $Tests = 'com.example.VisualAuditTest#timerLifecycleLight,com.example.VisualAuditTest#timerLifecycleDark'
    $Output = & $Adb -s $Serial shell am instrument -w -e class $Tests -e auditVariant reduced-motion com.example.preview.test/androidx.test.runner.AndroidJUnitRunner 2>&1 |
        ForEach-Object { if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.Exception.Message } else { "$_" } }
    $TestExitCode = $LASTEXITCODE
    $Output | Tee-Object -FilePath (Join-Path $ProjectRoot '.work/reduced-motion-tests.log')
    if ($TestExitCode -ne 0 -or ($Output -join "`n") -notmatch 'OK \(2 tests\)') { throw 'Reduced-motion tests failed.' }
} finally {
    foreach ($Setting in $Original.Keys) {
        if ($Original[$Setting] -eq 'null') { & $Adb -s $Serial shell settings delete global $Setting }
        else { & $Adb -s $Serial shell settings put global $Setting $Original[$Setting] }
    }
    $ErrorActionPreference = $PreviousPreference
}
& $Adb -s $Serial pull /sdcard/Android/data/com.example.preview/files/visual-audit/ (Join-Path $ProjectRoot 'app/build/outputs')
Assert-ExitCode 'Retrieve reduced-motion screenshots'
Write-Host 'Reduced-motion tests passed; screenshots: reduced-motion-* in app/build/outputs/visual-audit'
