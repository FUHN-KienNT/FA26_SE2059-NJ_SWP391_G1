# Run in an Administrator PowerShell terminal. Updates only this local AMS deployment.
$ErrorActionPreference = 'Stop'
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Open VS Code or PowerShell with Run as administrator, then run this script again.'
}

$warSource = Join-Path $PSScriptRoot 'target\ams.war'
$expandedSource = Join-Path $PSScriptRoot 'target\ams'
$tomcatRoot = 'C:\Program Files\Apache Software Foundation\Tomcat 10.1'
$webappsRoot = Join-Path $tomcatRoot 'webapps'
$warDestination = Join-Path $webappsRoot 'ams.war'
$expandedDestination = Join-Path $webappsRoot 'ams'
if (-not (Test-Path -LiteralPath $warSource -PathType Leaf)) { throw 'Missing target\ams.war. Build the project first.' }
if (-not (Test-Path -LiteralPath $webappsRoot -PathType Container)) { throw "Tomcat webapps not found: $webappsRoot" }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($warSource)
try {
    if (-not $archive.GetEntry('WEB-INF/classes/vn/edu/fpt/ams/controller/AssignmentServlet.class')) {
        throw 'This WAR does not contain Assignment List. Build the updated source first.'
    }
} finally { $archive.Dispose() }

$service = Get-Service -Name Tomcat10
Write-Host 'Stopping Tomcat10 before updating AMS...'
if ($service.Status -ne 'Stopped') {
    Stop-Service -Name Tomcat10
    $service.WaitForStatus('Stopped', [TimeSpan]::FromSeconds(30))
}
try {
    Copy-Item -LiteralPath $warSource -Destination $warDestination -Force
    # Also refresh an existing expanded app, even if unpackWARs/autoDeploy was disabled.
    # No folders are deleted; other applications are untouched.
    if (Test-Path -LiteralPath $expandedDestination -PathType Container) {
        if (-not (Test-Path -LiteralPath $expandedSource -PathType Container)) {
            throw 'Missing target\ams directory. Rebuild the project before deployment.'
        }
        Get-ChildItem -LiteralPath $expandedSource -Force | ForEach-Object {
            Copy-Item -LiteralPath $_.FullName -Destination $expandedDestination -Recurse -Force
        }
    }
    $sourceHash = (Get-FileHash -LiteralPath $warSource -Algorithm SHA256).Hash
    $destinationHash = (Get-FileHash -LiteralPath $warDestination -Algorithm SHA256).Hash
    if ($sourceHash -ne $destinationHash) { throw 'WAR verification failed: file hashes differ.' }
    Write-Host "WAR VERIFIED: $destinationHash"
} finally {
    Start-Service -Name Tomcat10
}

$url = 'http://127.0.0.1:8080/ams/teacher/assignments'
$lastResult = 'No HTTP response'
for ($attempt = 0; $attempt -lt 15; $attempt++) {
    try {
        $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -eq 200 -and $response.Content.Contains('/auth/signin"')) {
            Write-Host 'SUCCESS: AMS deployed; Assignment List requires sign-in.' -ForegroundColor Green
            Write-Host 'Open http://localhost:8080/ams/auth/signin'
            exit 0
        }
        $lastResult = "HTTP $($response.StatusCode), sign-in page missing"
    } catch { $lastResult = $_.Exception.Message }
    Start-Sleep -Seconds 2
}
Write-Host "Deployment copied, but verification failed: $lastResult" -ForegroundColor Yellow
Write-Host 'Recent Tomcat deployment errors:'
Get-ChildItem -LiteralPath (Join-Path $tomcatRoot 'logs') -Filter '*.log' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 3 | ForEach-Object {
        Write-Host $_.Name
        Get-Content -LiteralPath $_.FullName -Tail 60 |
            Select-String -Pattern 'ams|SEVERE|ERROR|Exception|Caused by|Deploy|fail'
    }
exit 1
