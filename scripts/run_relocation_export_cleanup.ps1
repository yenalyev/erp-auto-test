param(
    [string]$BuildDirectory = 'target/rel-columns-ui-verified-20261021',
    [string]$Manifest = 'docs/audits/2026-10-21/relocation-export-columns-pending-cleanup.json',
    [string]$Report = 'docs/audits/2026-10-21/relocation-export-columns-cleanup-recovery.json'
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$javaRoot = 'C:\Users\gigam\.jdks\corretto-21.0.9'
Push-Location $repoRoot
try {
    [xml]$resultXml = Get-Content (Join-Path $BuildDirectory 'surefire-reports/TEST-TestSuite.xml') -Raw
    $runClasspath = ($resultXml.testsuite.properties.property | Where-Object { $_.name -eq 'java.class.path' }).value
    if (-not $runClasspath) { throw 'Maven runtime classpath is missing from the selected build report' }
    $cleanupClasses = Join-Path $repoRoot 'target/rel-columns-cleanup'
    New-Item -ItemType Directory -Force -Path $cleanupClasses | Out-Null
    & "$javaRoot/bin/javac.exe" '-proc:none' '-cp' $runClasspath '-d' $cleanupClasses 'scripts/RelocationExportColumnsCleanup.java'
    if ($LASTEXITCODE -ne 0) { throw 'Cleanup helper compilation failed' }
    & "$javaRoot/bin/java.exe" '-Denv=dev' '-Dframework.browser.channel=chrome' '-Duse.database=false' '-Dssh.enabled=false' `
        '-cp' "$cleanupClasses;$runClasspath" 'RelocationExportColumnsCleanup' $Manifest $Report
    if ($LASTEXITCODE -ne 0) { throw 'Cleanup is incomplete; inspect the error before retrying' }
} finally { Pop-Location }
