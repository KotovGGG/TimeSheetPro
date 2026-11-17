param(
    [switch]$SkipBuild,
    [string]$Tests = 'com.example.VisualAuditTest#adaptiveWindows',
    [ValidateSet('font150', 'font200', 'landscape')]
    [string[]]$Variants = @('font150', 'font200', 'landscape')
)
. "$PSScriptRoot/Android-Environment.ps1"
$Serial = 'emulator-5580'
$AvdName = & $Adb -s $Serial emu avd name
if ($LASTEXITCODE -ne 0 -or $AvdName -notcontains 'TimeSheetPro_Pixel9') {
    throw 'Start Preview.cmd first. Adaptive tests only run on the dedicated preview emulator.'
}
if (-not $SkipBuild) {
    & ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
    Assert-ExitCode 'Adaptive test build'
}
foreach ($Apk in @('app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    & $Adb -s $Serial install -r (Join-Path $ProjectRoot $Apk)
    Assert-ExitCode 'Adaptive test installation'
}
$OriginalFont = & $Adb -s $Serial shell settings get system font_scale
Assert-ExitCode 'Read font scale'
$OriginalRotation = & $Adb -s $Serial shell settings get system user_rotation
Assert-ExitCode 'Read rotation'
$OriginalAccelerometer = & $Adb -s $Serial shell settings get system accelerometer_rotation
Assert-ExitCode 'Read automatic rotation'
$PreviousPreference = $ErrorActionPreference
try {
    # Native stderr also contains successful adb progress in Windows PowerShell.
    $ErrorActionPreference = 'Continue'
    $Cases = @(@{Name='font150';Font='1.5';Rotation='0'}, @{Name='font200';Font='2.0';Rotation='0'}, @{Name='landscape';Font='1.0';Rotation='1'})
    foreach ($Case in $Cases | Where-Object { $_.Name -in $Variants }) {
        & $Adb -s $Serial shell settings put system font_scale $Case.Font
        Assert-ExitCode 'Set font scale'
        & $Adb -s $Serial shell settings put system accelerometer_rotation 0
        Assert-ExitCode 'Disable automatic rotation'
        & $Adb -s $Serial shell settings put system user_rotation $Case.Rotation
        Assert-ExitCode 'Set rotation'
        $Log = Join-Path $ProjectRoot ".work/audit-$($Case.Name).log"
        $Output = & $Adb -s $Serial shell am instrument -w -e class $Tests -e auditVariant $Case.Name com.example.preview.test/androidx.test.runner.AndroidJUnitRunner 2>&1 |
            ForEach-Object { if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.Exception.Message } else { "$_" } }
        $TestExitCode = $LASTEXITCODE
        $Output | Tee-Object -FilePath $Log
        if ($TestExitCode -ne 0 -or ($Output -join "`n") -notmatch 'OK \([1-9][0-9]* tests?\)') {
            throw "Adaptive test failed: $($Case.Name). See $Log."
        }
    }
} finally {
    & $Adb -s $Serial shell settings put system font_scale $OriginalFont
    & $Adb -s $Serial shell settings put system user_rotation $OriginalRotation
    & $Adb -s $Serial shell settings put system accelerometer_rotation $OriginalAccelerometer
    $ErrorActionPreference = $PreviousPreference
}
$Destination = Join-Path $ProjectRoot 'app/build/outputs'
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
& $Adb -s $Serial pull /sdcard/Android/data/com.example.preview/files/visual-audit/ $Destination
Assert-ExitCode 'Retrieve adaptive screenshots'
Write-Host 'Adaptive tests passed; screenshots: app/build/outputs/visual-audit'
