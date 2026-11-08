param(
    [string]$MinecraftConfigDir = "$env:APPDATA\.minecraft\config"
)

# Builds SMTCBridge.exe and copies it to the Minecraft config folder.
# Requires the .NET 10 SDK (https://dotnet.microsoft.com/download/dotnet/10.0)

dotnet publish .\EchoSmtcBridge.csproj -c Release -r win-x64 --self-contained false /p:PublishSingleFile=true
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$exe = Join-Path $PSScriptRoot "bin\Release\net10.0-windows10.0.19041.0\win-x64\publish\SMTCBridge.exe"
if (-not (Test-Path -LiteralPath $exe)) {
    Write-Error "Build output not found: $exe"
    exit 1
}

New-Item -ItemType Directory -Force -Path $MinecraftConfigDir | Out-Null
Copy-Item -LiteralPath $exe -Destination (Join-Path $MinecraftConfigDir "SMTCBridge.exe") -Force
Write-Host "Copied SMTCBridge.exe to $MinecraftConfigDir\SMTCBridge.exe"
