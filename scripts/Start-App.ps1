param([ValidateSet('All','Backend','Frontend')][string]$Mode = 'All')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

# Refresh settings so an old VS Code terminal does not force an old Java PATH.
$machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$env:Path = "$machinePath;$userPath;$env:Path"

function Find-Jdk {
    $candidates = New-Object 'System.Collections.Generic.List[string]'
    foreach ($candidateRoot in @($env:JAVA_HOME,
        [Environment]::GetEnvironmentVariable('JAVA_HOME','User'),
        [Environment]::GetEnvironmentVariable('JAVA_HOME','Machine'))) {
        if ($candidateRoot) { $candidates.Add((Join-Path $candidateRoot 'bin\java.exe')) }
    }
    foreach ($commandName in @('java.exe','javac.exe')) {
        foreach ($command in @(Get-Command $commandName -All -ErrorAction SilentlyContinue)) {
            if ($command.Source) { $candidates.Add((Join-Path (Split-Path $command.Source) 'java.exe')) }
        }
    }
    $roots = @(
        "$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Amazon Corretto",
        "$env:ProgramFiles\BellSoft", "$env:ProgramFiles\Semeru",
        "$env:USERPROFILE\.jdks", "$env:LOCALAPPDATA\Programs\Eclipse Adoptium"
    )
    foreach ($folder in $roots) {
        if (Test-Path -LiteralPath $folder) {
            foreach ($directory in @(Get-ChildItem -LiteralPath $folder -Directory -ErrorAction SilentlyContinue)) {
                $candidates.Add((Join-Path $directory.FullName 'bin\java.exe'))
            }
        }
    }
    foreach ($executable in @($candidates | Select-Object -Unique)) {
        if (!(Test-Path -LiteralPath $executable)) { continue }
        try {
            # Java writes its version information to stderr, including on success.
            $oldPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            $details = (& $executable '-XshowSettings:properties' '-version' 2>&1 | Out-String)
            $ErrorActionPreference = $oldPreference
            $rootMatch = [regex]::Match($details, '(?m)^\s*java\.home\s*=\s*(.+?)\s*$')
            $versionMatch = [regex]::Match($details, '(?m)^\s*java\.specification\.version\s*=\s*(\d+)\s*$')
            if (!$rootMatch.Success -or !$versionMatch.Success) { continue }
            $major = [int]$versionMatch.Groups[1].Value
            $jdkRoot = $rootMatch.Groups[1].Value.Trim()
            if ($major -ge 17 -and $major -le 25 -and (Test-Path -LiteralPath (Join-Path $jdkRoot 'bin\javac.exe'))) {
                return $jdkRoot
            }
        } catch { continue }
    }
    throw 'No supported JDK found. Install a JDK 17 or 21 (not a JRE), enable JAVA_HOME and PATH in its installer, then retry. An installed JDK 25 also works. If your JDK is in a custom folder, set JAVA_HOME to that folder.'
}

function Check-Port([int]$Port) {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
    try { $listener.Start() }
    catch { throw "Port $Port is unavailable. Close the previous Bill Split server terminal and try again." }
    finally { $listener.Stop() }
}

function Check-Node {
    if (!(Get-Command node.exe -ErrorAction SilentlyContinue) -or !(Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
        throw 'Node.js is missing. Install Node.js 24 LTS with npm, close this window, and retry.'
    }
    $version = (& node.exe -p 'process.versions.node').Trim().Split('.')
    $major = [int]$version[0]; $minor = [int]$version[1]
    if (!(($major -eq 20 -and $minor -ge 19) -or ($major -eq 22 -and $minor -ge 12) -or ($major -ge 24))) {
        throw 'This frontend needs Node 20.19+, 22.12+, or 24+. Node.js 24 LTS is recommended.'
    }
}

try {
    Set-Location -LiteralPath $projectRoot
    if ($projectRoot -match '(?i)OneDrive') {
        Write-Host 'Tip: extract this project under C:\Projects to avoid OneDrive file locks.' -ForegroundColor Yellow
    }
    if ($Mode -eq 'All' -or $Mode -eq 'Backend') {
        $env:JAVA_HOME = Find-Jdk
        $env:Path = "$env:JAVA_HOME\bin;$env:Path"
        Write-Host "Using JDK: $env:JAVA_HOME" -ForegroundColor Green
        Check-Port 8085
    }
    if ($Mode -eq 'All' -or $Mode -eq 'Frontend') { Check-Node; Check-Port 5175 }

    if ($Mode -eq 'All') {
        # Each child runs one server and stays open if it fails, so errors are visible.
        $scriptFile = Join-Path $PSScriptRoot 'Start-App.ps1'
        foreach ($serverMode in @('Backend','Frontend')) {
            $arguments = '-NoProfile -NoExit -ExecutionPolicy Bypass -File "{0}" -Mode {1}' -f $scriptFile, $serverMode
            Start-Process -FilePath 'powershell.exe' -ArgumentList $arguments -WorkingDirectory $projectRoot | Out-Null
        }
        Write-Host ''
        Write-Host 'Two server windows are opening. Keep them open.'
        Write-Host 'Wait for Started BillsplitApplication in the backend window.'
        Write-Host 'Then open http://127.0.0.1:5175 and register your account.' -ForegroundColor Green
        Write-Host 'Close both server windows (or press Ctrl+C in each) to stop the app.'
    } elseif ($Mode -eq 'Backend') {
        $Host.UI.RawUI.WindowTitle = 'Bill Split - Backend - 8085'
        Set-Location -LiteralPath (Join-Path $projectRoot 'backend')
        # Do not run clean: Windows sync tools may lock generated files during deletion.
        & '.\mvnw.cmd' 'spring-boot:run'
        if ($LASTEXITCODE -ne 0) { throw 'Backend did not start. Read the error above.' }
    } else {
        $Host.UI.RawUI.WindowTitle = 'Bill Split - Frontend - 5175'
        Set-Location -LiteralPath (Join-Path $projectRoot 'frontend')
        if (!(Test-Path -LiteralPath 'node_modules\vite\bin\vite.js')) {
            & npm.cmd ci
            if ($LASTEXITCODE -ne 0) { throw 'npm install failed. Check the connection and the error above.' }
        }
        & npm.cmd run dev
        if ($LASTEXITCODE -ne 0) { throw 'Frontend did not start. Read the error above.' }
    }
} catch {
    Write-Host ''
    Write-Host $_.Exception.Message -ForegroundColor Red
    Read-Host 'Press Enter to close'
    exit 1
}
