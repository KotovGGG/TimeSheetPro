param([switch]$RecordSnapshots)
. "$PSScriptRoot/Android-Environment.ps1"
$ScreenshotTask = if ($RecordSnapshots) { ':app:recordRoborazziDebug' } else { ':app:verifyRoborazziDebug' }
$Log = Join-Path $ProjectRoot '.work/verify.log'
# Windows PowerShell represents native stderr as ErrorRecord even for successful tools.
# Gradle's exit code, not the presence of a warning on stderr, determines success.
$PreviousPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    & ./gradlew.bat :app:assembleDebug $ScreenshotTask :app:lintDebug --no-daemon --console=plain 2>&1 |
        ForEach-Object {
            if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.Exception.Message }
            else { "$_" }
        } | Tee-Object -FilePath $Log
    $BuildExitCode = $LASTEXITCODE
} finally { $ErrorActionPreference = $PreviousPreference }
if ($BuildExitCode -ne 0) { throw "Build and verification failed (exit $BuildExitCode). Log: $Log" }
Write-Host "Preview APK: $ProjectRoot/app/build/outputs/apk/debug/app-debug.apk"
Write-Host "Screenshot baselines: $ProjectRoot/app/src/test/screenshots/design-system"
Write-Host "Screenshot comparison report: $ProjectRoot/app/build/reports/roborazzi"
