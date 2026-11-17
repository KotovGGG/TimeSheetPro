$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $ProjectRoot

function Test-Jdk21([string]$Path) {
    if (-not $Path -or -not (Test-Path (Join-Path $Path 'bin/java.exe'))) { return $false }
    $Release = Get-Content (Join-Path $Path 'release') -ErrorAction SilentlyContinue
    $VersionLine = $Release | Where-Object { $_ -match '^JAVA_VERSION="(\d+)' } | Select-Object -First 1
    return $VersionLine -and $VersionLine -match '^JAVA_VERSION="(\d+)' -and [int]$Matches[1] -ge 21
}
if (-not (Test-Jdk21 $env:JAVA_HOME)) {
    $localJdk = Get-ChildItem (Join-Path $ProjectRoot '.work/jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
}
if (-not (Test-Jdk21 $env:JAVA_HOME)) {
    throw 'JDK 21+ is required for the Android 36 Robolectric tests. Set JAVA_HOME to a complete JDK 21 installation.'
}
if (-not $env:ANDROID_HOME) {
    $localSdk = Join-Path $ProjectRoot '.work/sdk'
    $installedSdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
    if (Test-Path (Join-Path $localSdk 'platform-tools/adb.exe')) { $env:ANDROID_HOME = $localSdk }
    elseif (Test-Path $installedSdk) { $env:ANDROID_HOME = $installedSdk }
    else { throw 'Android SDK is required. Install API 37 and build-tools 36.0.0, and set ANDROID_HOME.' }
}
$env:GRADLE_USER_HOME = Join-Path $ProjectRoot '.work/gradle-home'
$env:ANDROID_USER_HOME = Join-Path $ProjectRoot '.work/android-user'
$env:ANDROID_AVD_HOME = Join-Path $ProjectRoot '.work/avd'
New-Item -ItemType Directory -Force $env:ANDROID_USER_HOME,$env:ANDROID_AVD_HOME | Out-Null
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
$Adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'

function Assert-ExitCode([string]$Operation) {
    if ($LASTEXITCODE -ne 0) { throw "$Operation failed (exit $LASTEXITCODE). See the command output above." }
}
