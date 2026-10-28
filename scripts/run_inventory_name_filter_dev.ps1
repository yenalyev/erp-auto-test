param(
    [string]$JavaHome = 'C:\Users\gigam\.jdks\corretto-21.0.9',
    [string]$RunId = ('inventory-name-filter-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$buildDir = Join-Path $repoRoot "target/$RunId"
$previousJava = $env:JAVA_HOME
$previousPath = $env:PATH
$previousDownload = $env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD
try {
    New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
    $env:JAVA_HOME = $JavaHome
    $env:PATH = "$JavaHome\bin;$previousPath"
    $env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'
    Push-Location $repoRoot
    try {
        & mvn test "-Dtest.output.directory=$buildDir" '-Denv=dev' '-Dsuite=inventory-name-filter' `
            '-Duse.database=false' '-Dssh.enabled=false' '-Dsuite.artifact.sweep=false' `
            '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dframework.browser.channel=chrome' `
            "-Dallure.results.directory=$buildDir/allure-results" *> (Join-Path $buildDir 'maven.log')
        $runExit = $LASTEXITCODE
    } finally { Pop-Location }
    Write-Output "Run directory: $buildDir"
    Write-Output "Maven exit code: $runExit"
} finally {
    $env:JAVA_HOME = $previousJava
    $env:PATH = $previousPath
    $env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = $previousDownload
}
exit $runExit
