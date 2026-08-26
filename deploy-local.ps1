<#
Builds the current source into a jar, rebuilds the api Docker image with the
SAME tag compose.yml already points at, and recreates the container — all
on this machine, so nothing is pushed to or pulled from the registry.

Run this every time you change backend code and want the local docker
compose stack (api.fluxibiz.store labels included) to reflect it.
#>

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Test-Path ".env")) {
    Write-Host "No .env found next to compose.yml." -ForegroundColor Red
    Write-Host "Copy .env.example to .env and fill in the blanks first, then re-run this script." -ForegroundColor Red
    exit 1
}

Write-Host "==> Building jar (gradlew bootJar)" -ForegroundColor Cyan
& .\gradlew.bat clean bootJar --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }

$jar = Get-ChildItem "build\libs" -Filter "*.jar" | Where-Object { $_.Name -notlike "*plain*" } | Select-Object -First 1
if (-not $jar) { throw "No jar found in build\libs after build" }
Write-Host "==> Using $($jar.Name)" -ForegroundColor Cyan
Copy-Item $jar.FullName "app.jar" -Force

$image = "asia-southeast1-docker.pkg.dev/project-6f8e390c-7ad4-4d23-b1b/backend-images/ipos-api:latest"

Write-Host "==> Building docker image $image" -ForegroundColor Cyan
docker build -t $image .
if ($LASTEXITCODE -ne 0) { throw "docker build failed" }

Write-Host "==> Recreating the api container from the freshly built image" -ForegroundColor Cyan
docker compose up -d --force-recreate api
if ($LASTEXITCODE -ne 0) { throw "docker compose up failed" }

Write-Host "==> Done. Tailing logs (Ctrl+C to stop tailing, containers keep running)" -ForegroundColor Green
docker compose logs -f api
