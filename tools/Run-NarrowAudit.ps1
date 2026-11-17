param([switch]$SkipBuild, [string]$Tests = 'com.example.VisualAuditTest#adaptiveWindows')
. "$PSScriptRoot/Android-Environment.ps1"
$Serial = 'emulator-5580'
$AvdName = & $Adb -s $Serial emu avd name
if ($LASTEXITCODE -ne 0 -or $AvdName -notcontains 'TimeSheetPro_Pixel9') {
    throw 'Start Preview.cmd first. Narrow tests only run on the dedicated preview emulator.'
}
$DensityOutput = (& $Adb -s $Serial shell wm density) -join "`n"
Assert-ExitCode 'Read display density'
$Density = if ($DensityOutput -match 'Override density: ([0-9]+)') { [int]$Matches[1] }
    elseif ($DensityOutput -match 'Physical density: ([0-9]+)') { [int]$Matches[1] } else { 0 }
if ($Density -ne 420) { throw 'The narrow audit requires the dedicated AVD density of 420 dpi.' }
if (-not $SkipBuild) {
    & ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
    Assert-ExitCode 'Narrow test build'
}
foreach ($Apk in @('app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    & $Adb -s $Serial install -r (Join-Path $ProjectRoot $Apk)
    Assert-ExitCode 'Narrow test installation'
}
$OriginalSize = (& $Adb -s $Serial shell wm size) -join "`n"
Assert-ExitCode 'Read display size'
$OverrideSize = if ($OriginalSize -match 'Override size: ([0-9]+x[0-9]+)') { $Matches[1] } else { $null }
$OriginalFont = & $Adb -s $Serial shell settings get system font_scale
Assert-ExitCode 'Read font scale'
$OriginalRotation = & $Adb -s $Serial shell settings get system user_rotation
Assert-ExitCode 'Read rotation'
$OriginalAccelerometer = & $Adb -s $Serial shell settings get system accelerometer_rotation
Assert-ExitCode 'Read automatic rotation'
$PreviousPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    # The dedicated Pixel 9 AVD uses 420 dpi: 840 px equals 320 dp.
    & $Adb -s $Serial shell wm size 840x1800
    Assert-ExitCode 'Set narrow display size'
    & $Adb -s $Serial shell settings put system accelerometer_rotation 0
    Assert-ExitCode 'Disable automatic rotation'
    & $Adb -s $Serial shell settings put system user_rotation 0
    Assert-ExitCode 'Set portrait rotation'
    foreach ($Case in @(@{Name='narrow100';Font='1.0'}, @{Name='narrow200';Font='2.0'})) {
        & $Adb -s $Serial shell settings put system font_scale $Case.Font
        Assert-ExitCode 'Set font scale'
        $Output = & $Adb -s $Serial shell am instrument -w -e class $Tests -e auditVariant $Case.Name com.example.preview.test/androidx.test.runner.AndroidJUnitRunner 2>&1 |
            ForEach-Object { if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.Exception.Message } else { "$_" } }
        $TestExitCode = $LASTEXITCODE
        $Log = Join-Path $ProjectRoot ".work/audit-$($Case.Name).log"
        $Output | Tee-Object -FilePath $Log
        if ($TestExitCode -ne 0 -or ($Output -join "`n") -notmatch 'OK \([1-9][0-9]* tests?\)') {
            throw "Narrow test failed: $($Case.Name). See $Log."
        }
    }
} finally {
    if ($OverrideSize) { & $Adb -s $Serial shell wm size $OverrideSize } else { & $Adb -s $Serial shell wm size reset }
    & $Adb -s $Serial shell settings put system font_scale $OriginalFont
    & $Adb -s $Serial shell settings put system user_rotation $OriginalRotation
    & $Adb -s $Serial shell settings put system accelerometer_rotation $OriginalAccelerometer
    $ErrorActionPreference = $PreviousPreference
    $Destination = Join-Path $ProjectRoot 'app/build/outputs'
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    & $Adb -s $Serial pull /sdcard/Android/data/com.example.preview/files/visual-audit/ $Destination
    Assert-ExitCode 'Retrieve narrow screenshots'
    if ($Tests.Contains('DatabaseErrorAuditTest')) {
        & $Adb -s $Serial pull /sdcard/Android/data/com.example.preview/files/database-error-audit/ $Destination
        Assert-ExitCode 'Retrieve narrow database error screenshots'
    }
}
Assert-ExitCode 'Retrieve narrow screenshots'
Write-Host 'Narrow tests passed; screenshots: app/build/outputs/visual-audit'
