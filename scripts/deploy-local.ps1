param(
    [int]$GatewayPort = 18090,
    [int]$TimeoutSeconds = 300,
    [switch]$SkipApiChecks
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$Namespace = "cloudcart"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$NamespaceManifest = Join-Path $RepoRoot "k8s\base\00-namespace.yaml"
$DevOverlay = Join-Path $RepoRoot "k8s\overlays\dev"

function Write-Step {
    param([string]$Message)

    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Invoke-Checked {
    param(
        [string]$Command,
        [string[]]$Arguments
    )

    & $Command @Arguments

    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $Command $($Arguments -join ' ')"
    }
}

function New-RandomSecret {
    param([int]$Bytes = 32)

    $buffer = New-Object byte[] $Bytes
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()

    try {
        $generator.GetBytes($buffer)
        return [Convert]::ToBase64String($buffer)
    }
    finally {
        $generator.Dispose()
    }
}

function Get-SecretValue {
    param(
        [string]$SecretName,
        [string]$Key
    )

    $encodedValue = & kubectl get secret $SecretName `
        --namespace $Namespace `
        -o "jsonpath={.data.$Key}" 2>$null

    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($encodedValue)) {
        return $null
    }

    try {
        $bytes = [Convert]::FromBase64String($encodedValue)
        return [System.Text.Encoding]::UTF8.GetString($bytes)
    }
    catch {
        throw "Could not decode key '$Key' from secret '$SecretName'."
    }
}

function Test-KubernetesResource {
    param(
        [string]$ResourceType,
        [string]$ResourceName
    )

    & kubectl get $ResourceType $ResourceName `
        --namespace $Namespace `
        --output name *> $null

    return $LASTEXITCODE -eq 0
}

function Apply-Secret {
    param(
        [string]$SecretName,
        [hashtable]$Values
    )

    $arguments = @(
        "create",
        "secret",
        "generic",
        $SecretName,
        "--namespace",
        $Namespace
    )

    foreach ($entry in $Values.GetEnumerator()) {
        $arguments += "--from-literal=$($entry.Key)=$($entry.Value)"
    }

    $arguments += @(
        "--dry-run=client",
        "-o",
        "yaml"
    )

    $yaml = & kubectl @arguments

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to generate secret '$SecretName'."
    }

    $yaml | & kubectl apply -f -

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to apply secret '$SecretName'."
    }
}

function Ensure-DatabaseSecrets {
    param(
        [string]$DatabaseSecret,
        [string]$ApplicationSecret,
        [string]$PersistentVolumeClaim,
        [switch]$IncludeJwtSecret
    )

    $databasePassword = Get-SecretValue `
        -SecretName $DatabaseSecret `
        -Key "POSTGRES_PASSWORD"

    $applicationPassword = Get-SecretValue `
        -SecretName $ApplicationSecret `
        -Key "DB_PASSWORD"

    if (
        $databasePassword -and
        $applicationPassword -and
        $databasePassword -ne $applicationPassword
    ) {
        throw @"
Password mismatch detected between:
  $DatabaseSecret
  $ApplicationSecret

The script will not rotate PostgreSQL credentials automatically.
"@
    }

    if (-not $databasePassword -and -not $applicationPassword) {
        if (Test-KubernetesResource `
            -ResourceType "pvc" `
            -ResourceName $PersistentVolumeClaim) {

            throw @"
Both password secrets are missing, but PVC '$PersistentVolumeClaim' exists.

Generating a new password could make the existing database inaccessible.
Restore the original secrets or delete the PVC for a fresh local database.
"@
        }

        $databasePassword = New-RandomSecret
        $applicationPassword = $databasePassword
    }
    elseif (-not $databasePassword) {
        $databasePassword = $applicationPassword
    }
    elseif (-not $applicationPassword) {
        $applicationPassword = $databasePassword
    }

    if (-not (Get-SecretValue $DatabaseSecret "POSTGRES_PASSWORD")) {
        Write-Host "Creating $DatabaseSecret"

        Apply-Secret `
            -SecretName $DatabaseSecret `
            -Values @{
                POSTGRES_PASSWORD = $databasePassword
            }
    }
    else {
        Write-Host "Preserving $DatabaseSecret"
    }

    if ($IncludeJwtSecret) {
        $jwtSecret = Get-SecretValue `
            -SecretName $ApplicationSecret `
            -Key "JWT_SECRET"

        if (-not $jwtSecret) {
            $jwtSecret = New-RandomSecret -Bytes 48
        }

        $existingApplicationPassword = Get-SecretValue `
            -SecretName $ApplicationSecret `
            -Key "DB_PASSWORD"

        $existingJwtSecret = Get-SecretValue `
            -SecretName $ApplicationSecret `
            -Key "JWT_SECRET"

        if (-not $existingApplicationPassword -or -not $existingJwtSecret) {
            Write-Host "Creating or repairing $ApplicationSecret"

            Apply-Secret `
                -SecretName $ApplicationSecret `
                -Values @{
                    DB_PASSWORD = $applicationPassword
                    JWT_SECRET  = $jwtSecret
                }
        }
        else {
            Write-Host "Preserving $ApplicationSecret"
        }
    }
    else {
        if (-not (Get-SecretValue $ApplicationSecret "DB_PASSWORD")) {
            Write-Host "Creating $ApplicationSecret"

            Apply-Secret `
                -SecretName $ApplicationSecret `
                -Values @{
                    DB_PASSWORD = $applicationPassword
                }
        }
        else {
            Write-Host "Preserving $ApplicationSecret"
        }
    }
}

function Test-TcpPort {
    param([int]$Port)

    $client = New-Object System.Net.Sockets.TcpClient

    try {
        $asyncResult = $client.BeginConnect(
            "127.0.0.1",
            $Port,
            $null,
            $null
        )

        $connected = $asyncResult.AsyncWaitHandle.WaitOne(300)

        if ($connected) {
            $client.EndConnect($asyncResult)
        }

        return $connected
    }
    catch {
        return $false
    }
    finally {
        $client.Close()
    }
}

Write-Step "Checking required tools"

foreach ($command in @("kubectl.exe", "minikube.exe")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required command was not found: $command"
    }
}

if (-not (Test-Path $NamespaceManifest)) {
    throw "Namespace manifest not found: $NamespaceManifest"
}

if (-not (Test-Path $DevOverlay)) {
    throw "Development overlay not found: $DevOverlay"
}

Write-Step "Checking Minikube"

Invoke-Checked `
    -Command "minikube" `
    -Arguments @("status")

$currentContext = (& kubectl config current-context).Trim()

if ($LASTEXITCODE -ne 0) {
    throw "Could not read the active Kubernetes context."
}

if ($currentContext -ne "minikube") {
    throw "Expected Kubernetes context 'minikube', but found '$currentContext'."
}

Write-Host "Active context: $currentContext" -ForegroundColor Green

Write-Step "Creating the CloudCart namespace"

Invoke-Checked `
    -Command "kubectl" `
    -Arguments @(
        "apply",
        "-f",
        $NamespaceManifest
    )

Write-Step "Ensuring Kubernetes Secrets"

Ensure-DatabaseSecrets `
    -DatabaseSecret "user-db-secret" `
    -ApplicationSecret "user-service-secret" `
    -PersistentVolumeClaim "data-user-db-0" `
    -IncludeJwtSecret

Ensure-DatabaseSecrets `
    -DatabaseSecret "product-db-secret" `
    -ApplicationSecret "product-service-secret" `
    -PersistentVolumeClaim "data-product-db-0"

Ensure-DatabaseSecrets `
    -DatabaseSecret "order-db-secret" `
    -ApplicationSecret "order-service-secret" `
    -PersistentVolumeClaim "data-order-db-0"

Write-Step "Applying the development overlay"

Invoke-Checked `
    -Command "kubectl" `
    -Arguments @(
        "apply",
        "-k",
        $DevOverlay
    )

Write-Step "Waiting for all CloudCart Pods"

Invoke-Checked `
    -Command "kubectl" `
    -Arguments @(
        "wait",
        "--for=condition=Ready",
        "pod",
        "--all",
        "--namespace",
        $Namespace,
        "--timeout=$($TimeoutSeconds)s"
    )

Write-Step "Checking workload rollouts"

foreach ($deployment in @(
    "user-service",
    "product-service",
    "order-service",
    "payment-service",
    "notification-service"
)) {
    Invoke-Checked `
        -Command "kubectl" `
        -Arguments @(
            "rollout",
            "status",
            "deployment/$deployment",
            "--namespace",
            $Namespace,
            "--timeout=$($TimeoutSeconds)s"
        )
}

foreach ($statefulSet in @(
    "user-db",
    "product-db",
    "order-db"
)) {
    Invoke-Checked `
        -Command "kubectl" `
        -Arguments @(
            "rollout",
            "status",
            "statefulset/$statefulSet",
            "--namespace",
            $Namespace,
            "--timeout=$($TimeoutSeconds)s"
        )
}

Write-Step "Current CloudCart resources"

Invoke-Checked `
    -Command "kubectl" `
    -Arguments @(
        "get",
        "pods,svc,pvc,ingress",
        "--namespace",
        $Namespace
    )

if (-not $SkipApiChecks) {
    Write-Step "Checking the NGINX Ingress controller"

    Invoke-Checked `
        -Command "kubectl" `
        -Arguments @(
            "rollout",
            "status",
            "deployment/ingress-nginx-controller",
            "--namespace",
            "ingress-nginx",
            "--timeout=180s"
        )

    while (Test-TcpPort -Port $GatewayPort) {
        Write-Host "Port $GatewayPort is already in use; trying the next port."
        $GatewayPort++
    }

    $kubectlExecutable = (Get-Command kubectl.exe).Source
    $stdoutLog = Join-Path $env:TEMP "cloudcart-port-forward-$PID.out.log"
    $stderrLog = Join-Path $env:TEMP "cloudcart-port-forward-$PID.err.log"

    Remove-Item $stdoutLog, $stderrLog `
        -Force `
        -ErrorAction SilentlyContinue

    Write-Step "Starting temporary Ingress tunnel on port $GatewayPort"

    $portForward = Start-Process `
        -FilePath $kubectlExecutable `
        -ArgumentList @(
            "port-forward",
            "service/ingress-nginx-controller",
            "$($GatewayPort):80",
            "--namespace",
            "ingress-nginx"
        ) `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -WindowStyle Hidden `
        -PassThru

    try {
        $portReady = $false

        for ($attempt = 1; $attempt -le 30; $attempt++) {
            if ($portForward.HasExited) {
                $errorOutput = Get-Content $stderrLog `
                    -Raw `
                    -ErrorAction SilentlyContinue

                throw "Ingress port-forward exited unexpectedly: $errorOutput"
            }

            if (Test-TcpPort -Port $GatewayPort) {
                $portReady = $true
                break
            }

            Start-Sleep -Seconds 1
        }

        if (-not $portReady) {
            throw "Timed out waiting for local port $GatewayPort."
        }

        Start-Sleep -Seconds 3

        Write-Step "Testing CloudCart APIs through Ingress"

        foreach ($path in @(
            "/api/products",
            "/api/orders",
            "/api/payments",
            "/api/notifications"
        )) {
            $uri = "http://localhost:$GatewayPort$path"

            $response = Invoke-WebRequest `
                -Uri $uri `
                -UseBasicParsing `
                -TimeoutSec 20

            if ($response.StatusCode -ne 200) {
                throw "$uri returned HTTP $($response.StatusCode)."
            }

            Write-Host "PASS  HTTP 200  $uri" -ForegroundColor Green
        }
    }
    finally {
        if ($portForward -and -not $portForward.HasExited) {
            Stop-Process `
                -Id $portForward.Id `
                -Force `
                -ErrorAction SilentlyContinue
        }

        Remove-Item $stdoutLog, $stderrLog `
            -Force `
            -ErrorAction SilentlyContinue
    }
}

Write-Host ""
Write-Host "CloudCart local deployment completed successfully." `
    -ForegroundColor Green
