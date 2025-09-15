# Script de validacion del sistema GridMR
Write-Host "===============================================" -ForegroundColor Green
Write-Host "    VALIDACION DEL SISTEMA GRIDMR" -ForegroundColor Green
Write-Host "===============================================" -ForegroundColor Green
Write-Host ""

# 1. Health Check
Write-Host "1. VERIFICANDO ESTADO DEL SISTEMA..." -ForegroundColor Yellow
try {
    $health = Invoke-WebRequest -Uri "http://localhost:8080/api/health" -Method GET
    $healthJson = $health.Content | ConvertFrom-Json
    
    Write-Host "   Estado: $($healthJson.status)" -ForegroundColor Green
    Write-Host "   Workers Activos: $($healthJson.active_workers)" -ForegroundColor Green
    Write-Host "   Total Workers: $($healthJson.total_workers)" -ForegroundColor Green
    Write-Host "   Tiempo de Ejecucion: $($healthJson.uptime)" -ForegroundColor Green
    Write-Host "   Timestamp: $($healthJson.timestamp)" -ForegroundColor Gray
} catch {
    Write-Host "   ERROR: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 2. Estadisticas de Workers
Write-Host "2. ESTADISTICAS DE WORKERS..." -ForegroundColor Yellow
try {
    $workers = Invoke-WebRequest -Uri "http://localhost:8080/api/fault-tolerance/text" -Method GET
    Write-Host $workers.Content -ForegroundColor Cyan
} catch {
    Write-Host "   ERROR: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 3. Estadisticas de Nodos
Write-Host "3. ESTADISTICAS DE NODOS..." -ForegroundColor Yellow
try {
    $nodes = Invoke-WebRequest -Uri "http://localhost:8080/api/nodes/statistics/text" -Method GET
    Write-Host $nodes.Content -ForegroundColor Cyan
} catch {
    Write-Host "   ERROR: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 4. Estado de Persistencia
Write-Host "4. ESTADO DE PERSISTENCIA..." -ForegroundColor Yellow
try {
    $persistence = Invoke-WebRequest -Uri "http://localhost:8080/api/persistence/status" -Method GET
    $persistenceJson = $persistence.Content | ConvertFrom-Json
    
    Write-Host "   Persistencia Habilitada: $($persistenceJson.persistence_enabled)" -ForegroundColor Green
    Write-Host "   Intervalo: $($persistenceJson.persistence_interval_ms)ms" -ForegroundColor Green
    Write-Host "   Backups Disponibles: $($persistenceJson.backup_count)" -ForegroundColor Green
    Write-Host "   Version del Estado: $($persistenceJson.state_version)" -ForegroundColor Green
    Write-Host "   Archivo de Estado Existe: $($persistenceJson.state_file_exists)" -ForegroundColor Green
} catch {
    Write-Host "   ERROR: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 5. Estado de Failover
Write-Host "5. ESTADO DE FAILOVER..." -ForegroundColor Yellow
try {
    $failover = Invoke-WebRequest -Uri "http://localhost:8080/api/failover/status" -Method GET
    $failoverJson = $failover.Content | ConvertFrom-Json
    
    Write-Host "   Master ID: $($failoverJson.master_id)" -ForegroundColor Green
    Write-Host "   Host: $($failoverJson.master_host)" -ForegroundColor Green
    Write-Host "   Puerto: $($failoverJson.master_port)" -ForegroundColor Green
    Write-Host "   Rol Actual: $($failoverJson.current_role)" -ForegroundColor Green
    Write-Host "   Es Lider: $($failoverJson.is_leader)" -ForegroundColor Green
    Write-Host "   Lider Actual: $($failoverJson.current_leader_id)" -ForegroundColor Green
    Write-Host "   Masters Conocidos: $($failoverJson.known_masters_count)" -ForegroundColor Green
    Write-Host "   Masters Activos: $($failoverJson.active_masters_count)" -ForegroundColor Green
} catch {
    Write-Host "   ERROR: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 6. Lista de Workers
Write-Host "6. LISTA DE WORKERS..." -ForegroundColor Yellow
try {
    $workersList = Invoke-WebRequest -Uri "http://localhost:8080/api/workers" -Method GET
    $workersListJson = $workersList.Content | ConvertFrom-Json
    
    Write-Host "   Total Workers: $($workersListJson.total_count)" -ForegroundColor Green
    Write-Host "   Workers Activos: $($workersListJson.active_count)" -ForegroundColor Green
    
    if ($workersListJson.workers.Count -gt 0) {
        Write-Host "   Detalles de Workers:" -ForegroundColor Cyan
        $workersListJson.workers | ForEach-Object {
            Write-Host "     - ID: $($_.workerId) | Host: $($_.host) | Puerto: $($_.port) | Estado: $($_.status)" -ForegroundColor White
        }
    } else {
        Write-Host "   No hay workers registrados" -ForegroundColor Gray
    }
} catch {
    Write-Host "   ERROR: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 7. Lista de Nodos
Write-Host "7. LISTA DE NODOS..." -ForegroundColor Yellow
try {
    $nodesList = Invoke-WebRequest -Uri "http://localhost:8080/api/nodes" -Method GET
    $nodesListJson = $nodesList.Content | ConvertFrom-Json
    
    Write-Host "   Total Nodos: $($nodesListJson.total_count)" -ForegroundColor Green
    Write-Host "   Nodos Activos: $($nodesListJson.active_count)" -ForegroundColor Green
    Write-Host "   Nodos Inactivos: $($nodesListJson.inactive_count)" -ForegroundColor Green
    
    if ($nodesListJson.nodes.Count -gt 0) {
        Write-Host "   Detalles de Nodos:" -ForegroundColor Cyan
        $nodesListJson.nodes | ForEach-Object {
            Write-Host "     - ID: $($_.nodeId) | Host: $($_.host) | Puerto: $($_.port) | Tipo: $($_.nodeType) | Estado: $($_.status)" -ForegroundColor White
        }
    } else {
        Write-Host "   No hay nodos registrados" -ForegroundColor Gray
    }
} catch {
    Write-Host "   ERROR: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 8. Lista de Jobs
Write-Host "8. LISTA DE JOBS..." -ForegroundColor Yellow
try {
    $jobsList = Invoke-WebRequest -Uri "http://localhost:8080/api/jobs" -Method GET
    $jobsListJson = $jobsList.Content | ConvertFrom-Json
    
    Write-Host "   Total Jobs: $($jobsListJson.total_count)" -ForegroundColor Green
    Write-Host "   Jobs Activos: $($jobsListJson.active_count)" -ForegroundColor Green
    Write-Host "   Jobs Completados: $($jobsListJson.completed_count)" -ForegroundColor Green
    Write-Host "   Mensaje: $($jobsListJson.message)" -ForegroundColor Cyan
} catch {
    Write-Host "   ERROR: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

Write-Host "===============================================" -ForegroundColor Green
Write-Host "    VALIDACION COMPLETADA" -ForegroundColor Green
Write-Host "===============================================" -ForegroundColor Green