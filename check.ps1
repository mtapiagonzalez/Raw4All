param(
    [string]$JavaPath = (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr')
)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force build\checks | Out-Null
    $checkNames = @('FpsPolicy', 'HorizonFilter', 'Raw10', 'RecordingClock')
    $checkSources = foreach ($checkName in $checkNames) {
        "src\local\s23probe\$checkName.java"
        "checks\${checkName}Check.java"
    }
    & "$JavaPath\bin\javac.exe" --release 8 -d build\checks @checkSources
    if ($LASTEXITCODE -ne 0) { throw 'Check compilation failed' }
    foreach ($checkName in $checkNames) {
        & "$JavaPath\bin\java.exe" -cp build\checks "local.s23probe.${checkName}Check"
        if ($LASTEXITCODE -ne 0) { throw "$checkName check failed" }
    }
} finally {
    Pop-Location
}
