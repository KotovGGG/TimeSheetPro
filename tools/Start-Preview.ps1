. "$PSScriptRoot/Android-Environment.ps1"
& ./gradlew.bat :app:assembleDebug --no-daemon --console=plain
Assert-ExitCode 'Preview build'
$Emulator = Join-Path $env:ANDROID_HOME 'emulator/emulator.exe'
if (-not (Test-Path $Emulator)) { throw 'Install Android Emulator and system-images;android-36;google_apis;x86_64 in SDK Manager.' }
$AvdName = 'TimeSheetPro_Pixel9'
$Avds = & $Emulator -list-avds
Assert-ExitCode 'AVD inventory'
if ($Avds -notcontains $AvdName) {
    $Manager = Join-Path $env:ANDROID_HOME 'cmdline-tools/19.0/bin/avdmanager.bat'
    if (-not (Test-Path $Manager)) { $Manager = Join-Path $env:ANDROID_HOME 'cmdline-tools/latest/bin/avdmanager.bat' }
    if (-not (Test-Path $Manager)) { throw 'Android SDK command-line tools are required to create the Pixel 9 emulator.' }
    'no' | & $Manager create avd --name $AvdName --package 'system-images;android-36;google_apis;x86_64' --device pixel_9
    Assert-ExitCode 'Pixel 9 emulator creation'
}
# An explicit emulator serial prevents accidentally targeting a physical phone.
$Serial = 'emulator-5580'
$Devices = & $Adb devices
Assert-ExitCode 'ADB inventory'
if ($Devices -match "^$Serial\s") {
    $RunningName = & $Adb -s $Serial emu avd name
    Assert-ExitCode 'Emulator identity check'
    if ($RunningName -notcontains $AvdName) { throw 'Port 5580 is already used by another emulator. Close that emulator before continuing.' }
} else {
    Start-Process -FilePath $Emulator -ArgumentList @('-avd', $AvdName, '-port', '5580', '-no-snapshot-load') -WindowStyle Normal | Out-Null
}
$Deadline = [DateTime]::UtcNow.AddMinutes(3)
do {
    Start-Sleep -Seconds 2
    # ADB may report "offline" while the emulator starts; only the deadline is fatal here.
    $PreviousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $Booted = & $Adb -s $Serial shell getprop sys.boot_completed 2>$null
        $BootExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $PreviousPreference }
    if ([DateTime]::UtcNow -gt $Deadline) { throw 'Emulator did not boot within 3 minutes. Check virtualization and the emulator window.' }
} until ($BootExitCode -eq 0 -and $Booted -match '^1')
& $Adb -s $Serial install -r (Join-Path $ProjectRoot 'app/build/outputs/apk/debug/app-debug.apk')
Assert-ExitCode 'Install preview on emulator'
& $Adb -s $Serial shell am start -n com.example.preview/com.example.MainActivity
Assert-ExitCode 'Launch preview'
