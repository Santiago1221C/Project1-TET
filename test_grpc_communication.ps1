# Script de prueba para validar la comunicación gRPC Master-Workers
Write-Host "=== PRUEBA DE COMUNICACIÓN gRPC MASTER-WORKERS ===" -ForegroundColor Green

# Esperar a que el Master se inicie
Write-Host "Esperando a que el Master se inicie..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# Probar endpoint de estado gRPC
Write-Host "`n1. Probando estado del servicio gRPC..." -ForegroundColor Cyan
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/grpc/status" -Method GET
    Write-Host "✅ Estado gRPC: $response" -ForegroundColor Green
} catch {
    Write-Host "❌ Error conectando al servicio gRPC: $($_.Exception.Message)" -ForegroundColor Red
}

# Probar registro de worker
Write-Host "`n2. Probando registro de worker..." -ForegroundColor Cyan
$workerData = @{
    workerId = "worker-001"
    host = "192.168.1.100"
    port = 9091
    maxTasks = 4
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/grpc/register-worker" -Method POST -Body $workerData -ContentType "application/json"
    Write-Host "✅ Worker registrado: $($response.success) - $($response.message)" -ForegroundColor Green
} catch {
    Write-Host "❌ Error registrando worker: $($_.Exception.Message)" -ForegroundColor Red
}

# Probar heartbeat
Write-Host "`n3. Probando heartbeat de worker..." -ForegroundColor Cyan
$heartbeatData = @{
    workerId = "worker-001"
    activeTasks = 0
    availableTasks = 4
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/grpc/heartbeat" -Method POST -Body $heartbeatData -ContentType "application/json"
    Write-Host "✅ Heartbeat recibido: $($response.success) - $($response.message)" -ForegroundColor Green
} catch {
    Write-Host "❌ Error enviando heartbeat: $($_.Exception.Message)" -ForegroundColor Red
}

# Probar solicitud de tarea
Write-Host "`n4. Probando solicitud de tarea..." -ForegroundColor Cyan
$taskRequestData = @{
    workerId = "worker-001"
    capabilities = @("map", "reduce")
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/grpc/request-task" -Method POST -Body $taskRequestData -ContentType "application/json"
    Write-Host "✅ Solicitud de tarea: $($response.hasTask) - $($response.message)" -ForegroundColor Green
} catch {
    Write-Host "❌ Error solicitando tarea: $($_.Exception.Message)" -ForegroundColor Red
}

# Probar estado del Master
Write-Host "`n5. Probando estado general del Master..." -ForegroundColor Cyan
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/status" -Method GET
    Write-Host "✅ Estado del Master: $($response.status)" -ForegroundColor Green
    Write-Host "   - Workers activos: $($response.activeWorkers)" -ForegroundColor Green
} catch {
    Write-Host "❌ Error obteniendo estado del Master: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n=== PRUEBA COMPLETADA ===" -ForegroundColor Green
