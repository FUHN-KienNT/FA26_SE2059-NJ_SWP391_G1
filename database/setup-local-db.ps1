[CmdletBinding()]
param(
    [string]$HostName = "localhost",
    [ValidateRange(1, 65535)]
    [int]$Port = 5432,
    [string]$AdminUser = "postgres",
    [string]$DatabaseName = "ams_db",
    [string]$DatabaseUser = "ams_user",
    [string]$PsqlPath,
    [switch]$Reset
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Find-Psql {
    param([string]$RequestedPath)

    if ($RequestedPath) {
        if (-not (Test-Path -LiteralPath $RequestedPath -PathType Leaf)) {
            throw "psql was not found at: $RequestedPath"
        }
        return (Resolve-Path -LiteralPath $RequestedPath).Path
    }

    $command = Get-Command psql -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $candidates = @()
    foreach ($root in @("$env:ProgramFiles\PostgreSQL", "${env:ProgramFiles(x86)}\PostgreSQL")) {
        if ($root -and (Test-Path -LiteralPath $root)) {
            $candidates += Get-ChildItem -LiteralPath $root -Filter psql.exe -Recurse -File -ErrorAction SilentlyContinue
        }
    }
    if (Test-Path -LiteralPath "E:\Pogrel\Pog\bin\psql.exe") {
        $candidates += Get-Item -LiteralPath "E:\Pogrel\Pog\bin\psql.exe"
    }
    if ($candidates.Count -eq 0) {
        throw "psql.exe was not found. Install PostgreSQL command-line tools or pass -PsqlPath."
    }
    return ($candidates | Sort-Object FullName -Descending | Select-Object -First 1).FullName
}

function ConvertTo-PlainText {
    param([Security.SecureString]$SecureValue)

    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Quote-SqlIdentifier {
    param([string]$Value)
    return '"' + $Value.Replace('"', '""') + '"'
}

function Quote-SqlLiteral {
    param([string]$Value)
    return "'" + $Value.Replace("'", "''") + "'"
}

function Invoke-Psql {
    param(
        [string]$Executable,
        [string]$Database,
        [string]$User,
        [string]$Password,
        [string]$Sql,
        [string]$File
    )

    $previousPassword = $env:PGPASSWORD
    try {
        $env:PGPASSWORD = $Password
        $arguments = @(
            "-X", "-v", "ON_ERROR_STOP=1",
            "-h", $HostName,
            "-p", $Port.ToString(),
            "-U", $User,
            "-d", $Database
        )

        if ($File) {
            & $Executable @arguments -f $File
            $exitCode = $LASTEXITCODE
            if ($exitCode -ne 0) {
                throw "psql failed while running $File (exit code $exitCode)."
            }
            return
        }

        $output = $Sql | & $Executable @arguments -qAt
        $exitCode = $LASTEXITCODE
        if ($exitCode -ne 0) {
            throw "psql failed (exit code $exitCode)."
        }
        return $output
    } finally {
        $env:PGPASSWORD = $previousPassword
    }
}

if ($DatabaseName -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
    throw "DatabaseName must contain only letters, numbers and underscores, and cannot start with a number."
}
if ($DatabaseUser -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
    throw "DatabaseUser must contain only letters, numbers and underscores, and cannot start with a number."
}

$psql = Find-Psql -RequestedPath $PsqlPath
$schemaFile = Join-Path $PSScriptRoot "schema.sql"
$seedFile = Join-Path $PSScriptRoot "seed.sql"
if (-not (Test-Path -LiteralPath $schemaFile -PathType Leaf)) { throw "Missing $schemaFile" }
if (-not (Test-Path -LiteralPath $seedFile -PathType Leaf)) { throw "Missing $seedFile" }

Write-Host "AMS local database setup"
Write-Host "PostgreSQL: $HostName`:$Port"
Write-Host "Database:   $DatabaseName"
Write-Host "App user:   $DatabaseUser"
Write-Host "psql:       $psql"
if ($Reset) {
    Write-Warning "-Reset will drop and recreate $DatabaseName."
    $confirmation = Read-Host "Type the database name to confirm"
    if ($confirmation -cne $DatabaseName) {
        throw "Reset cancelled."
    }
}

$adminPassword = ConvertTo-PlainText (Read-Host "Password for PostgreSQL admin '$AdminUser'" -AsSecureString)
$applicationPassword = ConvertTo-PlainText (Read-Host "New local password for '$DatabaseUser'" -AsSecureString)
$applicationPasswordConfirmation = ConvertTo-PlainText (Read-Host "Confirm local password for '$DatabaseUser'" -AsSecureString)

try {
    if ([string]::IsNullOrWhiteSpace($applicationPassword)) {
        throw "The application database password cannot be blank."
    }
    if ($applicationPassword -cne $applicationPasswordConfirmation) {
        throw "The application database passwords do not match."
    }

    [void](Invoke-Psql -Executable $psql -Database "postgres" -User $AdminUser `
        -Password $adminPassword -Sql "SELECT 1;")

    $databaseIdentifier = Quote-SqlIdentifier $DatabaseName
    $databaseLiteral = Quote-SqlLiteral $DatabaseName
    $userIdentifier = Quote-SqlIdentifier $DatabaseUser
    $userLiteral = Quote-SqlLiteral $DatabaseUser
    $passwordLiteral = Quote-SqlLiteral $applicationPassword

    $roleSql = @"
DO `$ams`$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = $userLiteral) THEN
        ALTER ROLE $userIdentifier WITH LOGIN PASSWORD $passwordLiteral;
    ELSE
        CREATE ROLE $userIdentifier WITH LOGIN PASSWORD $passwordLiteral;
    END IF;
END
`$ams`$;
"@
    [void](Invoke-Psql -Executable $psql -Database "postgres" -User $AdminUser `
        -Password $adminPassword -Sql $roleSql)

    $databaseExists = (Invoke-Psql -Executable $psql -Database "postgres" -User $AdminUser `
        -Password $adminPassword -Sql "SELECT 1 FROM pg_database WHERE datname = $databaseLiteral;") -contains "1"

    if ($databaseExists -and $Reset) {
        $dropSql = @"
SELECT pg_terminate_backend(pid) FROM pg_stat_activity
WHERE datname = $databaseLiteral AND pid <> pg_backend_pid();
DROP DATABASE $databaseIdentifier;
"@
        [void](Invoke-Psql -Executable $psql -Database "postgres" -User $AdminUser `
            -Password $adminPassword -Sql $dropSql)
        $databaseExists = $false
    }

    if ($databaseExists) {
        $tableCountOutput = Invoke-Psql -Executable $psql -Database $DatabaseName -User $AdminUser `
            -Password $adminPassword -Sql "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE';"
        $tableCount = [int](($tableCountOutput | Select-Object -First 1).Trim())
        if ($tableCount -gt 0) {
            throw "Database $DatabaseName already contains $tableCount public tables. Re-run with -Reset to recreate it."
        }
        [void](Invoke-Psql -Executable $psql -Database "postgres" -User $AdminUser `
            -Password $adminPassword -Sql "ALTER DATABASE $databaseIdentifier OWNER TO $userIdentifier;")
    } else {
        [void](Invoke-Psql -Executable $psql -Database "postgres" -User $AdminUser `
            -Password $adminPassword -Sql "CREATE DATABASE $databaseIdentifier OWNER $userIdentifier;")
    }

    Write-Host "Applying schema.sql..."
    Invoke-Psql -Executable $psql -Database $DatabaseName -User $DatabaseUser `
        -Password $applicationPassword -File $schemaFile

    foreach ($migration in Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot "migrations") -Filter *.sql | Sort-Object Name) {
        Write-Host "Applying $($migration.Name)..."
        Invoke-Psql -Executable $psql -Database $DatabaseName -User $DatabaseUser `
            -Password $applicationPassword -File $migration.FullName
    }

    Write-Host "Applying seed.sql..."
    Invoke-Psql -Executable $psql -Database $DatabaseName -User $DatabaseUser `
        -Password $applicationPassword -File $seedFile

    $expectedTables = @(
        "account_audit_events", "responsibility_periods", "responsibility_deadline_breaches",
        "academic_terms", "additional_class_assignment_offers", "additional_class_requests",
        "assignment_templates", "assignments", "class_enrollments", "class_experts",
        "overflow_batches", "questions", "student_invitations", "subject_classes",
        "subject_experts", "subject_registrations", "subject_terms", "subjects",
        "submissions", "teacher_subjects", "users"
    )
    $actualTables = @(Invoke-Psql -Executable $psql -Database $DatabaseName -User $DatabaseUser `
        -Password $applicationPassword -Sql "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name;")
    $difference = Compare-Object -ReferenceObject $expectedTables -DifferenceObject $actualTables
    if ($difference) {
        throw "Database verification failed. Expected 18 AMS business tables and 3 implementation audit tables."
    }

    $seededUsers = Invoke-Psql -Executable $psql -Database $DatabaseName -User $DatabaseUser `
        -Password $applicationPassword -Sql "SELECT count(*) FROM users;"
    Write-Host ""
    Write-Host "AMS database setup completed successfully." -ForegroundColor Green
    Write-Host "Verified tables: 18 business + 3 audit"
    Write-Host "Seeded users: $($seededUsers | Select-Object -First 1)"
    Write-Host "Configure the application with:"
    Write-Host "  DB_HOST=$HostName"
    Write-Host "  DB_PORT=$Port"
    Write-Host "  DB_NAME=$DatabaseName"
    Write-Host "  DB_USER=$DatabaseUser"
    Write-Host "  DB_PASSWORD=<the local password entered above>"
} finally {
    $adminPassword = $null
    $applicationPassword = $null
    $applicationPasswordConfirmation = $null
}
