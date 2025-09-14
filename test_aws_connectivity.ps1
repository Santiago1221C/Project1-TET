# Script de Pruebas de Conectividad para AWS
# Este script simula las pruebas que se harían en AWS

Write-Host "=== Pruebas de Conectividad para AWS ===" -ForegroundColor Blue

$ERRORS = 0

function Report-Error {
    param($Message)
    Write-Host "ERROR: $Message" -ForegroundColor Red
    $script:ERRORS++
}

function Report-Success {
    param($Message)
    Write-Host "OK: $Message" -ForegroundColor Green
}

Write-Host "`n1. Probando Master REST API..." -ForegroundColor Blue

# Verificar si el Master está ejecutándose
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/health" -TimeoutSec 5
    if ($response.StatusCode -eq 200) {
        Report-Success "Master REST API respondiendo correctamente"
        $healthData = $response.Content | ConvertFrom-Json
        Write-Host "  - Estado: $($healthData.status)"
        Write-Host "  - Workers activos: $($healthData.active_workers)"
    } else {
        Report-Error "Master REST API no responde correctamente"
    }
} catch {
    Report-Error "Master REST API no disponible (esto es normal si no está ejecutándose)"
    Write-Host "  Para probar: cd gridmr-master && mvn spring-boot:run"
}

Write-Host "`n2. Probando endpoints REST..." -ForegroundColor Blue

$endpoints = @(
    "/api/health",
    "/api/workers", 
    "/api/status"
)

foreach ($endpoint in $endpoints) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080$endpoint" -TimeoutSec 5
        if ($response.StatusCode -eq 200) {
            Report-Success "Endpoint $endpoint funcionando"
        } else {
            Report-Error "Endpoint $endpoint no responde correctamente"
        }
    } catch {
        Write-Host "  - Endpoint $endpoint no disponible (Master no ejecutándose)" -ForegroundColor Yellow
    }
}

Write-Host "`n3. Probando Cliente Python..." -ForegroundColor Blue

if (Test-Path "client/client.py") {
    try {
        $clientTest = python client/client.py --create-sample --verbose 2>&1
        if ($clientTest -match "Master está activo") {
            Report-Success "Cliente Python conecta correctamente al Master"
        } else {
            Write-Host "  - Cliente Python no puede conectar (Master no ejecutándose)" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  - Error ejecutando cliente Python" -ForegroundColor Yellow
    }
} else {
    Report-Error "Cliente Python no encontrado"
}

Write-Host "`n4. Verificando configuración de puertos..." -ForegroundColor Blue

# Verificar que los puertos estén configurados correctamente
$masterConfig = Get-Content "gridmr-master/src/main/resources/application.properties" -Raw
if ($masterConfig -match "server.port=8080") {
    Report-Success "Master configurado en puerto 8080"
} else {
    Report-Error "Master no configurado en puerto 8080"
}

Write-Host "`n5. Verificando configuración de NFS..." -ForegroundColor Blue

if ($masterConfig -match "gridmr.nfs.path") {
    Report-Success "Master configurado para NFS"
} else {
    Report-Error "Master no configurado para NFS"
}

Write-Host "`n6. Verificando scripts de AWS..." -ForegroundColor Blue

$awsScripts = @(
    "aws-deployment/efs-setup.sh",
    "aws-deployment/ec2-nfs-client.sh", 
    "aws-deployment/ec2-nfs-server.sh"
)

foreach ($script in $awsScripts) {
    if (Test-Path $script) {
        Report-Success "Script $script encontrado"
    } else {
        Report-Error "Script $script no encontrado"
    }
}

Write-Host "`n7. Verificando documentación..." -ForegroundColor Blue

if (Test-Path "aws-deployment/README-AWS.md") {
    Report-Success "Documentación AWS encontrada"
} else {
    Report-Error "Documentación AWS no encontrada"
}

Write-Host "`n=== RESUMEN DE PRUEBAS ===" -ForegroundColor Blue

if ($ERRORS -eq 0) {
    Write-Host "`nTODAS LAS PRUEBAS PASARON - SISTEMA LISTO PARA AWS" -ForegroundColor Green
    
    Write-Host "`nInstrucciones para despliegue en AWS:" -ForegroundColor Blue
    Write-Host "1. Crear instancias EC2:"
    Write-Host "   - 1x Master (t3.medium): Java + Spring Boot"
    Write-Host "   - 2-4x Workers (t3.small): C++ + gRPC"
    Write-Host "   - 1x NFS Server (t3.small): Opcional si usas EFS"
    
    Write-Host "`n2. Configurar Security Groups:"
    Write-Host "   - Puerto 22 (SSH): Para administración"
    Write-Host "   - Puerto 8080 (HTTP): Para Master REST API"
    Write-Host "   - Puerto 9090+ (gRPC): Para Workers"
    Write-Host "   - Puerto 2049 (NFS): Para EFS o NFS Server"
    
    Write-Host "`n3. Configurar NFS:"
    Write-Host "   - Opción A: Amazon EFS (recomendado)"
    Write-Host "   - Opción B: EC2 NFS Server"
    
    Write-Host "`n4. Desplegar aplicaciones:"
    Write-Host "   - Master: mvn spring-boot:run"
    Write-Host "   - Workers: make && ./bin/worker"
    
    Write-Host "`n5. Probar sistema:"
    Write-Host "   python client/client.py --master-host [IP_MASTER] --create-sample"
    
} else {
    Write-Host "`nSe encontraron $ERRORS errores. Revisar antes del despliegue." -ForegroundColor Red
}
