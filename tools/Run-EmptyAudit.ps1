param([switch]$SkipBuild, [ValidateSet('empty', 'backup')][string]$Audit = 'empty')
. "$PSScriptRoot/Android-Environment.ps1"
$Serial = 'emulator-5582'
$AvdName = 'TimeSheetPro_EmptyAudit'
$Emulator = Join-Path $env:ANDROID_HOME 'emulator/emulator.exe'
if (-not (Test-Path $Emulator)) { throw 'Android Emulator is required for empty-state verification.' }
if (-not $SkipBuild) {
    & ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
    Assert-ExitCode 'Empty-state test build'
}
$Avds = & $Emulator -list-avds
Assert-ExitCode 'AVD inventory'
if ($Avds -notcontains $AvdName) {
    $Manager = Join-Path $env:ANDROID_HOME 'cmdline-tools/19.0/bin/avdmanager.bat'
    if (-not (Test-Path $Manager)) { $Manager = Join-Path $env:ANDROID_HOME 'cmdline-tools/latest/bin/avdmanager.bat' }
    if (-not (Test-Path $Manager)) { throw 'Android SDK command-line tools are required.' }
    'no' | & $Manager create avd --name $AvdName --package 'system-images;android-36;google_apis;x86_64' --device pixel_9
    Assert-ExitCode 'Empty-state AVD creation'
}
$Devices = & $Adb devices
Assert-ExitCode 'ADB inventory'
if ($Devices -match "^$Serial\s") {
    $Name = & $Adb -s $Serial emu avd name
    Assert-ExitCode 'Empty-state emulator identity'
    if ($Name -notcontains $AvdName) { throw 'Port 5582 belongs to another emulator; no test will run.' }
} else {
    Start-Process -FilePath $Emulator -ArgumentList @('-avd', $AvdName, '-port', '5582', '-no-snapshot-load') -WindowStyle Hidden | Out-Null
}
$Deadline = [DateTime]::UtcNow.AddMinutes(3)
do {
    Start-Sleep -Seconds 2
    $PreviousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $Booted = & $Adb -s $Serial shell getprop sys.boot_completed 2>$null
        $BootExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $PreviousPreference }
    if ([DateTime]::UtcNow -gt $Deadline) { throw 'Empty-state emulator did not boot within 3 minutes.' }
} until ($BootExitCode -eq 0 -and $Booted -match '^1')
foreach ($Apk in @('app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    & $Adb -s $Serial install -r (Join-Path $ProjectRoot $Apk)
    Assert-ExitCode 'Install empty-state test'
}
$Permission = & $Adb -s $Serial shell pm grant com.example.preview android.permission.POST_NOTIFICATIONS
Assert-ExitCode 'Grant preview notification permission'
$TestClass = if ($Audit -eq 'backup') { 'com.example.BackupAuditTest' } else { 'com.example.EmptyStateAuditTest' }
$OutputDirectory = if ($Audit -eq 'backup') { 'backup-audit' } else { 'empty-audit' }
$Log = Join-Path $ProjectRoot ".work/$Audit-state-tests.log"
$Output = & $Adb -s $Serial shell am instrument -w -e class $TestClass -e emptyAudit true com.example.preview.test/androidx.test.runner.AndroidJUnitRunner 2>&1 |
    ForEach-Object { if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.Exception.Message } else { "$_" } }
$TestExitCode = $LASTEXITCODE
$Output | Tee-Object -FilePath $Log
& $Adb -s $Serial pull "/sdcard/Android/data/com.example.preview/files/$OutputDirectory/" (Join-Path $ProjectRoot 'app/build/outputs')
Assert-ExitCode 'Retrieve empty-state screenshots'
if ($TestExitCode -ne 0 -or ($Output -join "`n") -notmatch 'OK \(1 test\)') { throw "Isolated $Audit test failed. See $Log." }
Write-Host "Isolated $Audit test passed; screenshots: app/build/outputs/$OutputDirectory"
