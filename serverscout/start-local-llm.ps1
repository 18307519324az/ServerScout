$ErrorActionPreference = 'Stop'

$root = 'D:\local-llm'
$server = Join-Path $root 'server\llama-server.exe'
$model = Join-Path $root 'qwen2.5-1.5b-instruct-q4_k_m.gguf'
$healthUrl = 'http://127.0.0.1:11434/health'

if (-not (Test-Path -LiteralPath $server)) {
    throw "Local model server not found: $server"
}
if (-not (Test-Path -LiteralPath $model)) {
    throw "Local model file not found: $model"
}

try {
    $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 3
    if ($health.status -eq 'ok') {
        Write-Output 'Local LLM is already running at http://127.0.0.1:11434'
        exit 0
    }
} catch {
    # Start the server below.
}

$process = Start-Process `
    -FilePath $server `
    -ArgumentList '-m', $model, '--host', '127.0.0.1', '--port', '11434', '--alias', 'qwen2.5-1.5b-instruct', '--ctx-size', '4096', '--n-predict', '1024' `
    -WorkingDirectory (Split-Path -Parent $server) `
    -RedirectStandardOutput (Join-Path $root 'llama-server.out.log') `
    -RedirectStandardError (Join-Path $root 'llama-server.err.log') `
    -WindowStyle Hidden `
    -PassThru

for ($attempt = 0; $attempt -lt 60; $attempt++) {
    Start-Sleep -Seconds 1
    if ($process.HasExited) {
        throw "Local LLM exited during startup. Read $root\llama-server.err.log"
    }
    try {
        $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 3
        if ($health.status -eq 'ok') {
            Write-Output "Local LLM started. PID: $($process.Id)"
            Write-Output 'Start ServerScout backend with Spring profile: local-ai'
            exit 0
        }
    } catch {
        # Continue waiting for model loading.
    }
}

throw 'Local LLM did not become healthy within 60 seconds.'
