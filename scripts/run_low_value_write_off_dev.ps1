param([string]$RunId = ('low-value-write-off-' + (Get-Date -Format 'yyyyMMdd-HHmmss')), [string]$RecoveryManifest)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$buildDir = Join-Path $repoRoot "target/$RunId"
$previousJava = $env:JAVA_HOME
$previousPath = $env:PATH
$previousDownload = $env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD
try {
    New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
    $env:JAVA_HOME = 'C:\Users\gigam\.jdks\corretto-21.0.9'
    $env:PATH = "$env:JAVA_HOME\bin;$previousPath"
    $env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'
    Push-Location $repoRoot
    try {
        $ErrorActionPreference = 'Continue'
        $recoveryArgs = @()
        if ($RecoveryManifest) { $recoveryArgs += "-Dmshp.recovery=$RecoveryManifest" }
        & mvn -o test "-Dtest.output.directory=$buildDir" '-Denv=dev' '-Dsuite=low-value-write-off' `
            '-Duse.database=false' '-Dssh.enabled=false' '-Dsuite.artifact.sweep=false' `
            '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dframework.browser.channel=chrome' `
            "-Dallure.results.directory=$buildDir/allure-results" @recoveryArgs *> (Join-Path $buildDir 'maven.log')
        $runExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = 'Stop'; Pop-Location }
    Write-Output "Run directory: $buildDir"
    Write-Output "Maven exit code: $runExit"
} finally {
    $env:JAVA_HOME = $previousJava
    $env:PATH = $previousPath
    $env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = $previousDownload
}
exit $runExit
