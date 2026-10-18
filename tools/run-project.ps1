param(
    [string]$TomcatHome = $(if ($env:CATALINA_HOME) { $env:CATALINA_HOME } else { "$env:USERPROFILE\tools\apache-tomcat-10.1.60" })
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $TomcatHome = [IO.Path]::GetFullPath($TomcatHome)
    if (-not (Test-Path -LiteralPath "$TomcatHome\bin\bootstrap.jar")) {
        throw "Tomcat not found at $TomcatHome. Set CATALINA_HOME or pass -TomcatHome."
    }
    if (-not (Test-Path -LiteralPath 'src\main\resources\application.properties')) {
        throw 'Create src\main\resources\application.properties and configure the database first.'
    }
    Get-Command java -ErrorAction Stop | Out-Null
    Get-Command mvn -ErrorAction Stop | Out-Null
    Write-Host '[1/4] Building project and running tests...'
    & mvn -B -ntp clean package "-Dams.build.directory=$projectRoot\.local\run-build"
    if ($LASTEXITCODE -ne 0) { throw 'Build failed. The running deployment has not been replaced.' }

    Write-Host '[2/4] Deploying the new WAR...'
    $listeners = @(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)
    foreach ($processId in ($listeners.OwningProcess | Select-Object -Unique)) {
        $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
        if (-not $processInfo -or $processInfo.Name -ne 'java.exe' -or
            $processInfo.CommandLine.IndexOf($TomcatHome, [StringComparison]::OrdinalIgnoreCase) -lt 0 -or
            $processInfo.CommandLine -notlike '*org.apache.catalina.startup.Bootstrap*') {
            throw "Port 8080 is used by another process (PID $processId). Stop it before running AMS."
        }
        Stop-Process -Id $processId -ErrorAction Stop
        Wait-Process -Id $processId -Timeout 15 -ErrorAction SilentlyContinue
    }
    Copy-Item -LiteralPath '.local\run-build\ams.war' -Destination "$TomcatHome\webapps\ams.war" -Force
    Write-Host '[3/4] Starting Tomcat...'
    # Invoke in a child shell because the startup helper uses exit when a port is occupied.
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\start-tomcat.ps1" -TomcatHome $TomcatHome
    if ($LASTEXITCODE -ne 0) { throw 'Tomcat could not start.' }
    Write-Host '[4/4] Waiting for AMS...'
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        try {
            $response = Invoke-WebRequest 'http://localhost:8080/ams/auth/signin' -UseBasicParsing -TimeoutSec 10
            if ($response.StatusCode -eq 200 -and $response.Content.Contains('auth/google/start')) {
                $ready = $true
                break
            }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "AMS is not ready. Check $TomcatHome\logs\ams-console.err." }
    Write-Host 'AMS is ready: http://localhost:8080/ams/auth/signin' -ForegroundColor Green
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
