param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$RunId = ('crew-inventory-access-' + (Get-Date -Format 'yyyyMMdd-HHmmss')),
    [string]$Suite = 'crew-inventory-access'
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'Set JAVA_HOME to a Java 21 JDK or pass -JavaHome.'
}
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
        # Windows PowerShell wraps native stderr (including JVM notices) as errors.
        $ErrorActionPreference = 'Continue'
        & mvn test "-Dtest.output.directory=$buildDir" '-Denv=dev' "-Dsuite=$Suite" `
            '-Duse.database=false' '-Dssh.enabled=false' '-Dsuite.artifact.sweep=false' `
            '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dframework.browser.channel=chrome' `
            "-Dallure.results.directory=$buildDir/allure-results" *> (Join-Path $buildDir 'maven.log')
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
