param([string]$Tests = 'com.example.VisualAuditTest', [switch]$SkipBuild)
. "$PSScriptRoot/Android-Environment.ps1"
$Serial = 'emulator-5580'
$AvdName = & $Adb -s $Serial emu avd name
if ($LASTEXITCODE -ne 0 -or $AvdName -notcontains 'TimeSheetPro_Pixel9') {
    throw 'Start Preview.cmd first. Visual tests only run on the dedicated preview emulator.'
}
if (-not $SkipBuild) {
    $PreviousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
        $BuildExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $PreviousPreference }
    if ($BuildExitCode -ne 0) { throw "Test APK build failed (exit $BuildExitCode)." }
}
foreach ($Apk in @('app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    & $Adb -s $Serial install -r (Join-Path $ProjectRoot $Apk)
    if ($LASTEXITCODE -ne 0) { throw "Test installation failed: $Apk" }
}
$Log = Join-Path $ProjectRoot '.work/device-tests.log'
$Output = & $Adb -s $Serial shell am instrument -w -e class $Tests com.example.preview.test/androidx.test.runner.AndroidJUnitRunner
$TestExitCode = $LASTEXITCODE
$Output | Tee-Object -FilePath $Log
$Destination = Join-Path $ProjectRoot 'app/build/outputs'
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
& $Adb -s $Serial pull /sdcard/Android/data/com.example.preview/files/visual-audit/ $Destination
if ($LASTEXITCODE -ne 0) { throw 'Unable to retrieve visual audit images.' }
if ($Tests -eq 'com.example.VisualAuditTest' -or $Tests.Contains('deterministicChoiceDialogs')) {
    & $Adb -s $Serial pull /sdcard/Android/data/com.example.preview/files/visual-goldens/ $Destination
    if ($LASTEXITCODE -ne 0) { throw 'Unable to retrieve native screenshot comparison images.' }
}
if ($Tests.Contains('DatabaseErrorAuditTest')) {
    & $Adb -s $Serial pull /sdcard/Android/data/com.example.preview/files/database-error-audit/ $Destination
    if ($LASTEXITCODE -ne 0) { throw 'Unable to retrieve database error screenshots.' }
}
# am instrument can exit zero even when JUnit failed.
if ($TestExitCode -ne 0 -or ($Output -join "`n") -notmatch 'OK \([0-9]+ tests?\)') {
    throw "Device tests failed. See $Log and app/build/outputs/visual-audit."
}
Write-Host 'Device tests passed; screenshots: app/build/outputs/visual-audit'
