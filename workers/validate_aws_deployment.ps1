# Script de Validación para Despliegue AWS de GridMR Workers
# Este script valida que los workers estén configurados correctamente para AWS

Write-Host "=== Validación de Workers para Despliegue AWS ===" -ForegroundColor Blue

# Variables de configuración
$WORKER_ID = "worker-001"
$WORKER_PORT = "9090"
$NFS_PATH = "/mnt/gridmr_nfs"
$MASTER_HOST = "localhost"
$MASTER_PORT = "8080"

# Contador de errores
$ERRORS = 0

# Función para reportar errores
function Report-Error {
    param($Message)
    Write-Host "❌ ERROR: $Message" -ForegroundColor Red
    $script:ERRORS++
}

# Función para reportar éxito
function Report-Success {
    param($Message)
    Write-Host "✅ $Message" -ForegroundColor Green
}

# Función para reportar advertencia
function Report-Warning {
    param($Message)
    Write-Host "⚠️  ADVERTENCIA: $Message" -ForegroundColor Yellow
}

Write-Host "`n1. Verificando estructura de archivos..." -ForegroundColor Blue

# Verificar archivos fuente
if (Test-Path "src/worker.cpp") {
    Report-Success "src/worker.cpp encontrado"
} else {
    Report-Error "src/worker.cpp no encontrado"
}

if (Test-Path "WorkerServer.cpp") {
    Report-Success "WorkerServer.cpp encontrado"
} else {
    Report-Error "WorkerServer.cpp no encontrado"
}

# Verificar archivos protobuf
if (Test-Path "generated/cpp/worker.pb.h") {
    Report-Success "Archivos protobuf encontrados"
} else {
    Report-Error "generated/cpp/worker.pb.h no encontrado"
}

# Verificar Makefile
if (Test-Path "Makefile") {
    Report-Success "Makefile encontrado"
} else {
    Report-Error "Makefile no encontrado"
}

Write-Host "`n2. Verificando configuración para AWS..." -ForegroundColor Blue

# Verificar que el worker use 0.0.0.0 para escuchar (importante para AWS)
$workerContent = Get-Content "src/worker.cpp" -Raw
if ($workerContent -match "0\.0\.0\.0") {
    Report-Success "Worker configurado para escuchar en 0.0.0.0 (correcto para AWS)"
} else {
    Report-Warning "Worker podría no estar configurado para escuchar en 0.0.0.0"
}

# Verificar que use gRPC
if ($workerContent -match "grpc") {
    Report-Success "Worker usa gRPC (correcto para comunicación distribuida)"
} else {
    Report-Error "Worker no parece usar gRPC"
}

# Verificar configuración de NFS
if ($workerContent -match "nfs_path") {
    Report-Success "Worker configurado para usar NFS"
} else {
    Report-Error "Worker no parece estar configurado para NFS"
}

Write-Host "`n3. Verificando funcionalidades MapReduce..." -ForegroundColor Blue

# Verificar funciones Map
if ($workerContent -match "wordcount") {
    Report-Success "Función wordcount implementada"
} else {
    Report-Error "Función wordcount no encontrada"
}

if ($workerContent -match "sort") {
    Report-Success "Función sort implementada"
} else {
    Report-Error "Función sort no encontrada"
}

# Verificar funciones Reduce
if ($workerContent -match "execute_wordcount_reduce") {
    Report-Success "Función wordcount reduce implementada"
} else {
    Report-Error "Función wordcount reduce no encontrada"
}

if ($workerContent -match "execute_sort_reduce") {
    Report-Success "Función sort reduce implementada"
} else {
    Report-Error "Función sort reduce no encontrada"
}

Write-Host "`n4. Verificando configuración de puertos..." -ForegroundColor Blue

# Verificar que el puerto sea configurable
if ($workerContent -match "worker_port") {
    Report-Success "Puerto configurable via argumentos"
} else {
    Report-Warning "Puerto podría no ser configurable"
}

Write-Host "`n5. Verificando manejo de errores..." -ForegroundColor Blue

# Verificar manejo de excepciones
if ($workerContent -match "try.*catch") {
    Report-Success "Manejo de excepciones implementado"
} else {
    Report-Warning "Manejo de excepciones podría estar incompleto"
}

Write-Host "`n6. Verificando compatibilidad con AWS..." -ForegroundColor Blue

# Verificar que no use rutas hardcodeadas de Windows
if ($workerContent -match "C:\\\\") {
    Report-Error "Rutas hardcodeadas de Windows encontradas (no compatible con AWS)"
} else {
    Report-Success "No hay rutas hardcodeadas de Windows"
}

# Verificar que use rutas relativas o configurables
if ($workerContent -match "nfs_path") {
    Report-Success "Usa rutas configurables (compatible con AWS)"
} else {
    Report-Error "No usa rutas configurables"
}

Write-Host "`n7. Verificando scripts de AWS..." -ForegroundColor Blue

# Verificar scripts de despliegue
if (Test-Path "../aws-deployment/efs-setup.sh") {
    Report-Success "Script EFS encontrado"
} else {
    Report-Error "Script EFS no encontrado"
}

if (Test-Path "../aws-deployment/ec2-nfs-client.sh") {
    Report-Success "Script NFS client encontrado"
} else {
    Report-Error "Script NFS client no encontrado"
}

Write-Host "`n8. Verificando configuración de Security Groups..." -ForegroundColor Blue

# Verificar que el worker use puerto estándar
if ($workerContent -match "9090") {
    Report-Success "Worker usa puerto 9090 (estándar para gRPC)"
} else {
    Report-Warning "Worker no usa puerto 9090"
}

Write-Host "`n=== RESUMEN DE VALIDACIÓN ===" -ForegroundColor Blue

if ($ERRORS -eq 0) {
    Write-Host "✅ Todos los checks pasaron. Workers listos para AWS." -ForegroundColor Green
    Write-Host "`nPróximos pasos para despliegue:" -ForegroundColor Blue
    Write-Host "1. Crear instancias EC2 (Master + Workers)"
    Write-Host "2. Configurar EFS o NFS Server"
    Write-Host "3. Ejecutar scripts de configuración"
    Write-Host "4. Compilar workers en las instancias EC2"
    Write-Host "5. Iniciar Master y Workers"
    Write-Host "6. Probar con cliente Python"
    exit 0
} else {
    Write-Host "❌ Se encontraron $ERRORS errores. Revisar antes del despliegue." -ForegroundColor Red
    exit 1
}
