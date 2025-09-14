# Script simple para probar el Master
Write-Host "=== PRUEBA SIMPLE DEL MASTER ===" -ForegroundColor Green

# Esperar a que el Master se inicie
Write-Host "Esperando 15 segundos para que el Master se inicie..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

# Probar endpoint básico
Write-Host "`n1. Probando endpoint de salud..." -ForegroundColor Cyan
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -Method GET
    Write-Host "✅ Master funcionando: $($response.status)" -ForegroundColor Green
} catch {
    Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
}

# Probar estado
Write-Host "`n2. Probando estado del Master..." -ForegroundColor Cyan
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/status" -Method GET
    Write-Host "✅ Estado: $($response.status)" -ForegroundColor Green
    Write-Host "   - Workers activos: $($response.activeWorkers)" -ForegroundColor Green
} catch {
    Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
}

# Probar endpoint gRPC
Write-Host "`n3. Probando endpoint gRPC..." -ForegroundColor Cyan
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/grpc/status" -Method GET
    Write-Host "✅ Servicio gRPC: $($response)" -ForegroundColor Green
} catch {
    Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n=== PRUEBA COMPLETADA ===" -ForegroundColor Green
