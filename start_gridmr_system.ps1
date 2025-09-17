# Script de inicio completo para el sistema GridMR
# Este script inicia el master, registra workers y ejecuta pruebas

param(
    [string]$MasterHost = "localhost",
    [int]$MasterPort = 8080,
    [string]$WorkerCount = "2",
    [switch]$SkipTests = $false,
    [switch]$Verbose = $false
)

Write-Host "🚀 Iniciando sistema GridMR completo" -ForegroundColor Green
Write-Host "=" * 50

# Configurar variables de entorno
$env:GRIDMR_MASTER_HOST = $MasterHost
$env:GRIDMR_MASTER_PORT = $MasterPort.ToString()

Write-Host "📋 Configuración:" -ForegroundColor Yellow
Write-Host "   Master: $MasterHost:$MasterPort"
Write-Host "   Workers: $WorkerCount"
Write-Host "   Tests: $(if ($SkipTests) { 'Deshabilitados' } else { 'Habilitados' })"
Write-Host ""

# Función para verificar si un puerto está en uso
function Test-Port {
    param([int]$Port)
    try {
        $connection = New-Object System.Net.Sockets.TcpClient
        $connection.Connect("localhost", $Port)
        $connection.Close()
        return $true
    } catch {
        return $false
    }
}

# Función para esperar a que un servicio esté disponible
function Wait-ForService {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 30,
        [string]$ServiceName = "Servicio"
    )
    
    Write-Host "⏳ Esperando que $ServiceName esté disponible..." -ForegroundColor Yellow
    $startTime = Get-Date
    $timeout = $startTime.AddSeconds($TimeoutSeconds)
    
    while ((Get-Date) -lt $timeout) {
        try {
            $response = Invoke-WebRequest -Uri $Url -Method GET -TimeoutSec 5 -UseBasicParsing
            if ($response.StatusCode -eq 200) {
                Write-Host "✅ $ServiceName está disponible" -ForegroundColor Green
                return $true
            }
        } catch {
            # Servicio no disponible aún
        }
        
        Start-Sleep -Seconds 2
    }
    
    Write-Host "❌ $ServiceName no está disponible después de $TimeoutSeconds segundos" -ForegroundColor Red
    return $false
}

# 1. Verificar que el master no esté ya ejecutándose
Write-Host "🔍 Verificando estado del master..." -ForegroundColor Yellow
if (Test-Port -Port $MasterPort) {
    Write-Host "⚠️  El puerto $MasterPort ya está en uso. ¿El master ya está ejecutándose?" -ForegroundColor Yellow
    $response = Read-Host "¿Continuar de todos modos? (y/N)"
    if ($response -ne "y" -and $response -ne "Y") {
        Write-Host "❌ Operación cancelada" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "✅ Puerto $MasterPort está disponible" -ForegroundColor Green
}

# 2. Iniciar el master
Write-Host ""
Write-Host "🎯 Iniciando Master GridMR..." -ForegroundColor Yellow

# Cambiar al directorio del master
Push-Location "gridmr-master"

try {
    # Compilar el master si es necesario
    Write-Host "🔨 Compilando master..." -ForegroundColor Yellow
    $mvnResult = mvn clean compile -q
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Error compilando master" -ForegroundColor Red
        exit 1
    }
    Write-Host "✅ Master compilado exitosamente" -ForegroundColor Green
    
    # Iniciar el master en segundo plano
    Write-Host "🚀 Iniciando master en segundo plano..." -ForegroundColor Yellow
    $masterProcess = Start-Process -FilePath "mvn" -ArgumentList "spring-boot:run" -PassThru -WindowStyle Hidden
    Write-Host "   PID del master: $($masterProcess.Id)" -ForegroundColor Gray
    
    # Esperar a que el master esté disponible
    if (-not (Wait-ForService -Url "http://$MasterHost`:$MasterPort/api/health" -ServiceName "Master GridMR")) {
        Write-Host "❌ No se pudo iniciar el master" -ForegroundColor Red
        Stop-Process -Id $masterProcess.Id -Force -ErrorAction SilentlyContinue
        exit 1
    }
    
} finally {
    Pop-Location
}

# 3. Registrar workers de prueba
Write-Host ""
Write-Host "👥 Registrando workers de prueba..." -ForegroundColor Yellow

$workerCount = [int]$WorkerCount
for ($i = 1; $i -le $workerCount; $i++) {
    $workerId = "worker-$i"
    $workerPort = 9090 + $i - 1
    
    Write-Host "📝 Registrando $workerId en puerto $workerPort..." -ForegroundColor Gray
    
    $workerData = @{
        worker_id = $workerId
        host = "localhost"
        port = $workerPort
        cpu_cores = 4
        memory_mb = 8192
        disk_space_gb = 100
        compute_power = 100
        max_concurrent_tasks = 5
    } | ConvertTo-Json
    
    try {
        $response = Invoke-RestMethod -Uri "http://$MasterHost`:$MasterPort/api/workers/register" -Method POST -Body $workerData -ContentType "application/json"
        Write-Host "✅ $workerId registrado: $($response.message)" -ForegroundColor Green
    } catch {
        Write-Host "❌ Error registrando $workerId`: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# 4. Verificar estado del sistema
Write-Host ""
Write-Host "📊 Verificando estado del sistema..." -ForegroundColor Yellow

try {
    $status = Invoke-RestMethod -Uri "http://$MasterHost`:$MasterPort/api/status" -Method GET
    Write-Host "✅ Estado del sistema:" -ForegroundColor Green
    Write-Host "   Master: $($status.master_status)" -ForegroundColor Gray
    Write-Host "   Workers activos: $($status.active_workers)" -ForegroundColor Gray
    Write-Host "   Workers totales: $($status.total_workers)" -ForegroundColor Gray
    Write-Host "   Trabajos activos: $($status.active_jobs)" -ForegroundColor Gray
} catch {
    Write-Host "❌ Error obteniendo estado del sistema: $($_.Exception.Message)" -ForegroundColor Red
}

# 5. Ejecutar pruebas si están habilitadas
if (-not $SkipTests) {
    Write-Host ""
    Write-Host "🧪 Ejecutando pruebas de comunicación..." -ForegroundColor Yellow
    
    if (Test-Path "test_worker_communication.py") {
        try {
            python test_worker_communication.py
            if ($LASTEXITCODE -eq 0) {
                Write-Host "✅ Pruebas completadas exitosamente" -ForegroundColor Green
            } else {
                Write-Host "⚠️  Algunas pruebas fallaron" -ForegroundColor Yellow
            }
        } catch {
            Write-Host "❌ Error ejecutando pruebas: $($_.Exception.Message)" -ForegroundColor Red
        }
    } else {
        Write-Host "⚠️  Archivo de pruebas no encontrado: test_worker_communication.py" -ForegroundColor Yellow
    }
}

# 6. Mostrar información de uso
Write-Host ""
Write-Host "🎉 Sistema GridMR iniciado exitosamente!" -ForegroundColor Green
Write-Host ""
Write-Host "📋 Información de uso:" -ForegroundColor Yellow
Write-Host "   Master API: http://$MasterHost`:$MasterPort/api" -ForegroundColor Gray
Write-Host "   Health Check: http://$MasterHost`:$MasterPort/api/health" -ForegroundColor Gray
Write-Host "   Workers: http://$MasterHost`:$MasterPort/api/workers" -ForegroundColor Gray
Write-Host "   Jobs: http://$MasterHost`:$MasterPort/api/jobs" -ForegroundColor Gray
Write-Host ""
Write-Host "🔧 Comandos útiles:" -ForegroundColor Yellow
Write-Host "   Ver workers: Invoke-RestMethod -Uri 'http://$MasterHost`:$MasterPort/api/workers'" -ForegroundColor Gray
Write-Host "   Ver estado: Invoke-RestMethod -Uri 'http://$MasterHost`:$MasterPort/api/status'" -ForegroundColor Gray
Write-Host "   Enviar trabajo: python client/client.py --create-sample --job-type wordcount" -ForegroundColor Gray
Write-Host ""
Write-Host "⏹️  Para detener el sistema, presiona Ctrl+C" -ForegroundColor Yellow

# Mantener el script ejecutándose
try {
    while ($true) {
        Start-Sleep -Seconds 10
        
        # Verificar que el master siga ejecutándose
        if ($masterProcess.HasExited) {
            Write-Host "❌ El master se detuvo inesperadamente" -ForegroundColor Red
            break
        }
    }
} catch {
    Write-Host ""
    Write-Host "🛑 Deteniendo sistema GridMR..." -ForegroundColor Yellow
    
    # Detener el master
    if ($masterProcess -and -not $masterProcess.HasExited) {
        Write-Host "⏹️  Deteniendo master (PID: $($masterProcess.Id))..." -ForegroundColor Yellow
        Stop-Process -Id $masterProcess.Id -Force -ErrorAction SilentlyContinue
    }
    
    Write-Host "✅ Sistema GridMR detenido" -ForegroundColor Green
}
