param([switch]$SkipBuild)
. "$PSScriptRoot/Android-Environment.ps1"
$Serial = 'emulator-5580'
$AvdName = & $Adb -s $Serial emu avd name
if ($LASTEXITCODE -ne 0 -or $AvdName -notcontains 'TimeSheetPro_Pixel9') {
    throw 'Start Preview.cmd first. Motion capture only runs on the dedicated preview emulator.'
}
if (-not $SkipBuild) {
    & ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
    Assert-ExitCode 'Motion test build'
}
$Destination = Join-Path $ProjectRoot 'app/build/outputs/visual-audit/motion'
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
foreach ($Theme in @('Light', 'Dark')) {
    $RemoteFile = "/sdcard/timesheet-motion-$($Theme.ToLowerInvariant()).mp4"
    $Recorder = Start-Process -FilePath $Adb -ArgumentList @('-s', $Serial, 'shell', 'screenrecord', '--size', '540x1212', '--time-limit', '24', $RemoteFile) -WindowStyle Hidden -PassThru
    try {
        & "$PSScriptRoot/Run-VisualAudit.ps1" -SkipBuild -Tests "com.example.VisualAuditTest#timerLifecycle$Theme"
        if (-not $Recorder.WaitForExit(30000)) { throw 'Screen recording did not stop.' }
        if ($Recorder.ExitCode -ne 0) { throw "Screen recording failed (exit $($Recorder.ExitCode))." }
        & $Adb -s $Serial pull $RemoteFile $Destination
        Assert-ExitCode 'Retrieve motion video'
    } finally {
        if (-not $Recorder.HasExited) { Stop-Process -Id $Recorder.Id }
        $Recorder.Dispose()
    }
}
Write-Host 'Timer motion videos: app/build/outputs/visual-audit/motion'
