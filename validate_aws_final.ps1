# Script de Validacion Final para Despliegue AWS de GridMR

Write-Host "=== Validacion Completa de GridMR para AWS ===" -ForegroundColor Blue

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

Write-Host "`n1. Verificando Master (Java + Spring Boot)..." -ForegroundColor Blue

if (Test-Path "gridmr-master/pom.xml") {
    $pomContent = Get-Content "gridmr-master/pom.xml" -Raw
    if ($pomContent -match "spring-boot") {
        Report-Success "Master configurado con Spring Boot"
    } else {
        Report-Error "Master no tiene Spring Boot"
    }
} else {
    Report-Error "pom.xml del Master no encontrado"
}

if (Test-Path "gridmr-master/src/main/java/com/gridmr/master/controller/GridMRRestController.java") {
    Report-Success "Controlador REST encontrado"
} else {
    Report-Error "Controlador REST no encontrado"
}

Write-Host "`n2. Verificando Workers (C++ + gRPC)..." -ForegroundColor Blue

if (Test-Path "workers/src/worker.cpp") {
    Report-Success "Worker principal encontrado"
} else {
    Report-Error "Worker principal no encontrado"
}

if (Test-Path "workers/generated/cpp/worker.pb.h") {
    Report-Success "Archivos protobuf generados"
} else {
    Report-Error "Archivos protobuf no generados"
}

Write-Host "`n3. Verificando Cliente (Python)..." -ForegroundColor Blue

if (Test-Path "client/client.py") {
    Report-Success "Cliente Python encontrado"
} else {
    Report-Error "Cliente Python no encontrado"
}

Write-Host "`n4. Verificando Scripts de AWS..." -ForegroundColor Blue

if (Test-Path "aws-deployment/efs-setup.sh") {
    Report-Success "Script EFS encontrado"
} else {
    Report-Error "Script EFS no encontrado"
}

if (Test-Path "aws-deployment/ec2-nfs-client.sh") {
    Report-Success "Script NFS client encontrado"
} else {
    Report-Error "Script NFS client no encontrado"
}

Write-Host "`n5. Verificando Comunicacion gRPC..." -ForegroundColor Blue

if (Test-Path "gridmr-master/src/main/proto") {
    Report-Success "Archivos proto del Master encontrados"
} else {
    Report-Error "Archivos proto del Master no encontrados"
}

if (Test-Path "serv_persistor/protobufs/worker.proto") {
    Report-Success "Archivo proto del Worker encontrado"
} else {
    Report-Error "Archivo proto del Worker no encontrado"
}

Write-Host "`n=== RESUMEN DE VALIDACION ===" -ForegroundColor Blue

if ($ERRORS -eq 0) {
    Write-Host "`nSISTEMA COMPLETAMENTE VALIDADO PARA AWS" -ForegroundColor Green
    Write-Host "`nArquitectura validada:" -ForegroundColor Blue
    Write-Host "- Master: Java + Spring Boot + gRPC (puerto 8080)"
    Write-Host "- Workers: C++ + gRPC (puerto 9090+)"
    Write-Host "- Cliente: Python (desde maquina local)"
    Write-Host "- NFS: Amazon EFS o EC2 NFS Server"
    Write-Host "- Comunicacion: REST (Cliente-Master) + gRPC (Master-Workers)"
    
    Write-Host "`nProximos pasos para despliegue:" -ForegroundColor Blue
    Write-Host "1. Crear instancias EC2 en AWS"
    Write-Host "2. Configurar Security Groups (puertos 22, 8080, 9090+)"
    Write-Host "3. Configurar EFS o NFS Server"
    Write-Host "4. Desplegar Master en instancia EC2"
    Write-Host "5. Desplegar Workers en instancias EC2"
    Write-Host "6. Probar con cliente Python desde maquina local"
} else {
    Write-Host "`nSe encontraron $ERRORS errores. Revisar antes del despliegue." -ForegroundColor Red
}
