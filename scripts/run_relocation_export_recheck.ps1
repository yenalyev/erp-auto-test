param([string]$RunId = ('relocation-export-recheck-' + (Get-Date -Format 'yyyyMMdd-HHmmss')),
      [string]$CompiledBuild = 'target/rel-columns-ui-verified-20261021')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$javaRoot = 'C:\Users\gigam\.jdks\corretto-21.0.9'
Push-Location $repoRoot
try {
    [xml]$resultXml = Get-Content (Join-Path $CompiledBuild 'surefire-reports/TEST-TestSuite.xml') -Raw
    $runClasspath = ($resultXml.testsuite.properties.property | Where-Object { $_.name -eq 'java.class.path' }).value
    if (-not $runClasspath) { throw 'Maven runtime classpath is missing' }
    # Reuse only bytecode newer than these test and page sources; no old implementation can masquerade as a rerun.
    foreach ($pair in @(
        @('src/test/java/com/erp/tests/ui/RelocationExportColumnsUiTest.java','test-classes/com/erp/tests/ui/RelocationExportColumnsUiTest.class'),
        @('src/main/java/com/erp/pages/ExportAnalyticsPage.java','classes/com/erp/pages/ExportAnalyticsPage.class'),
        @('src/main/java/com/erp/fixtures/RelocationExportColumnsFixture.java','classes/com/erp/fixtures/RelocationExportColumnsFixture.class')
    )) {
        if ((Get-Item (Join-Path $CompiledBuild $pair[1])).LastWriteTimeUtc -lt (Get-Item $pair[0]).LastWriteTimeUtc) {
            throw "Compiled class is stale: $($pair[1]); run the Maven suite first"
        }
    }
    $outputRoot = Join-Path $repoRoot "target/$RunId"
    New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
    $weaver = Join-Path $env:USERPROFILE '.m2/repository/org/aspectj/aspectjweaver/1.9.25/aspectjweaver-1.9.25.jar'
    $ErrorActionPreference = 'Continue'
    & "$javaRoot/bin/java.exe" "-javaagent:$weaver" '-Denv=dev' '-Duse.database=false' '-Dssh.enabled=false' `
        '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dsuite.artifact.sweep=false' '-Dframework.browser.channel=chrome' `
        "-Dallure.results.directory=$outputRoot/allure-results" '-cp' $runClasspath 'org.testng.TestNG' `
        '-d' "$outputRoot/surefire-reports" 'src/test/resources/suites/relocation-export-columns-recheck.xml' *> "$outputRoot/testng.log"
    $runExit = $LASTEXITCODE
    Write-Output "Run directory: $outputRoot"
    Write-Output "TestNG exit code: $runExit"
} finally { Pop-Location }
exit $runExit
