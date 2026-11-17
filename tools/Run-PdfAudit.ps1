param([switch]$SkipBuild)
. "$PSScriptRoot/Android-Environment.ps1"
& "$PSScriptRoot/Run-VisualAudit.ps1" -Tests 'com.example.PdfLayoutAuditTest' -SkipBuild:$SkipBuild
# Run-VisualAudit guards the dedicated emulator and the test guards the preview package.
$PreviousPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    & $Adb -s emulator-5580 pull /sdcard/Android/data/com.example.preview/files/pdf-audit/ (Join-Path $ProjectRoot 'app/build/outputs')
    $PullExitCode = $LASTEXITCODE
} finally { $ErrorActionPreference = $PreviousPreference }
if ($PullExitCode -ne 0) { throw 'Unable to retrieve PDF audit documents and rendered pages.' }
Write-Host 'PDF audit passed; documents and Android-rendered pages: app/build/outputs/pdf-audit'
