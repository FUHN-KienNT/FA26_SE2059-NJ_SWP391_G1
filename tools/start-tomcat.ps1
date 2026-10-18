param([string]$TomcatHome = "$env:USERPROFILE\tools\apache-tomcat-10.1.60")
$ErrorActionPreference = 'Stop'
if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    Write-Host 'Port 8080 is already in use. Open http://localhost:8080/ams/auth/signin'
    exit
}
$java = (Get-Command java).Source
if (-not (Test-Path -LiteralPath "$TomcatHome\bin\bootstrap.jar")) { throw "Tomcat not found: $TomcatHome" }
$arguments = @(
    '--add-opens=java.base/java.lang=ALL-UNNAMED',
    '--add-opens=java.base/java.io=ALL-UNNAMED',
    '--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED',
    "`"-Dcatalina.home=$TomcatHome`"",
    "`"-Dcatalina.base=$TomcatHome`"",
    "`"-Djava.util.logging.config.file=$TomcatHome\conf\logging.properties`"",
    '-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager',
    '-classpath', "`"$TomcatHome\bin\bootstrap.jar;$TomcatHome\bin\tomcat-juli.jar`"",
    'org.apache.catalina.startup.Bootstrap', 'start'
)
$server = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $TomcatHome -WindowStyle Hidden -RedirectStandardOutput "$TomcatHome\logs\ams-console.out" -RedirectStandardError "$TomcatHome\logs\ams-console.err" -PassThru
Write-Host "Started Tomcat PID $($server.Id). Open http://localhost:8080/ams/auth/signin after a few seconds."
