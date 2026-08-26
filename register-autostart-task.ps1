<#
Registers a Windows Task Scheduler task that runs `docker compose up -d`
in this folder whenever you log in, so the api/db containers come back up
automatically after a reboot (on top of compose's own `restart:
unless-stopped`, which only takes effect once Docker Desktop itself is
already running).

Run this ONCE, as your normal user, from an elevated PowerShell prompt.
#>

$ErrorActionPreference = "Stop"
$projectDir = $PSScriptRoot
$taskName = "ipos-backend-compose-autostart"

$action = New-ScheduledTaskAction `
    -Execute "docker" `
    -Argument "compose up -d" `
    -WorkingDirectory $projectDir

$trigger = New-ScheduledTaskTrigger -AtLogOn

$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 10)

Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue

Register-ScheduledTask `
    -TaskName $taskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Description "Bring up ipos-backend docker compose stack on login" `
    -RunLevel Highest

Write-Host "Registered task '$taskName' — it will run 'docker compose up -d' in $projectDir at every logon." -ForegroundColor Green
Write-Host "Make sure Docker Desktop itself is also set to 'Start Docker Desktop when you log in' (Docker Desktop Settings > General)." -ForegroundColor Yellow
