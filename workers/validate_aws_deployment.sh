#!/bin/bash

# Script de Validación para Despliegue AWS de GridMR Workers
# Este script valida que los workers estén configurados correctamente para AWS

set -e

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Validación de Workers para Despliegue AWS ===${NC}"

# Variables de configuración
WORKER_ID="worker-001"
WORKER_PORT="9090"
NFS_PATH="/mnt/gridmr_nfs"
MASTER_HOST="localhost"
MASTER_PORT="8080"

# Contador de errores
ERRORS=0

# Función para reportar errores
report_error() {
    echo -e "${RED}❌ ERROR: $1${NC}"
    ((ERRORS++))
}

# Función para reportar éxito
report_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

# Función para reportar advertencia
report_warning() {
    echo -e "${YELLOW}⚠️  ADVERTENCIA: $1${NC}"
}

echo -e "\n${BLUE}1. Verificando estructura de archivos...${NC}"

# Verificar archivos fuente
if [ ! -f "src/worker.cpp" ]; then
    report_error "src/worker.cpp no encontrado"
else
    report_success "src/worker.cpp encontrado"
fi

if [ ! -f "WorkerServer.cpp" ]; then
    report_error "WorkerServer.cpp no encontrado"
else
    report_success "WorkerServer.cpp encontrado"
fi

# Verificar archivos protobuf
if [ ! -f "generated/cpp/worker.pb.h" ]; then
    report_error "generated/cpp/worker.pb.h no encontrado"
else
    report_success "Archivos protobuf encontrados"
fi

# Verificar Makefile
if [ ! -f "Makefile" ]; then
    report_error "Makefile no encontrado"
else
    report_success "Makefile encontrado"
fi

echo -e "\n${BLUE}2. Verificando configuración para AWS...${NC}"

# Verificar que el worker use 0.0.0.0 para escuchar (importante para AWS)
if grep -q "0.0.0.0" src/worker.cpp; then
    report_success "Worker configurado para escuchar en 0.0.0.0 (correcto para AWS)"
else
    report_warning "Worker podría no estar configurado para escuchar en 0.0.0.0"
fi

# Verificar que use gRPC
if grep -q "grpc" src/worker.cpp; then
    report_success "Worker usa gRPC (correcto para comunicación distribuida)"
else
    report_error "Worker no parece usar gRPC"
fi

# Verificar configuración de NFS
if grep -q "nfs_path" src/worker.cpp; then
    report_success "Worker configurado para usar NFS"
else
    report_error "Worker no parece estar configurado para NFS"
fi

echo -e "\n${BLUE}3. Verificando funcionalidades MapReduce...${NC}"

# Verificar funciones Map
if grep -q "wordcount" src/worker.cpp; then
    report_success "Función wordcount implementada"
else
    report_error "Función wordcount no encontrada"
fi

if grep -q "sort" src/worker.cpp; then
    report_success "Función sort implementada"
else
    report_error "Función sort no encontrada"
fi

# Verificar funciones Reduce
if grep -q "execute_wordcount_reduce" src/worker.cpp; then
    report_success "Función wordcount reduce implementada"
else
    report_error "Función wordcount reduce no encontrada"
fi

if grep -q "execute_sort_reduce" src/worker.cpp; then
    report_success "Función sort reduce implementada"
else
    report_error "Función sort reduce no encontrada"
fi

echo -e "\n${BLUE}4. Verificando configuración de puertos...${NC}"

# Verificar que el puerto sea configurable
if grep -q "worker_port" src/worker.cpp; then
    report_success "Puerto configurable via argumentos"
else
    report_warning "Puerto podría no ser configurable"
fi

echo -e "\n${BLUE}5. Verificando manejo de errores...${NC}"

# Verificar manejo de excepciones
if grep -q "try.*catch" src/worker.cpp; then
    report_success "Manejo de excepciones implementado"
else
    report_warning "Manejo de excepciones podría estar incompleto"
fi

echo -e "\n${BLUE}6. Verificando compatibilidad con AWS...${NC}"

# Verificar que no use rutas hardcodeadas de Windows
if grep -q "C:\\\\" src/worker.cpp; then
    report_error "Rutas hardcodeadas de Windows encontradas (no compatible con AWS)"
else
    report_success "No hay rutas hardcodeadas de Windows"
fi

# Verificar que use rutas relativas o configurables
if grep -q "nfs_path" src/worker.cpp; then
    report_success "Usa rutas configurables (compatible con AWS)"
else
    report_error "No usa rutas configurables"
fi

echo -e "\n${BLUE}7. Verificando scripts de AWS...${NC}"

# Verificar scripts de despliegue
if [ -f "../aws-deployment/efs-setup.sh" ]; then
    report_success "Script EFS encontrado"
else
    report_error "Script EFS no encontrado"
fi

if [ -f "../aws-deployment/ec2-nfs-client.sh" ]; then
    report_success "Script NFS client encontrado"
else
    report_error "Script NFS client no encontrado"
fi

echo -e "\n${BLUE}8. Verificando configuración de Security Groups...${NC}"

# Verificar que el worker use puerto estándar
if grep -q "9090" src/worker.cpp; then
    report_success "Worker usa puerto 9090 (estándar para gRPC)"
else
    report_warning "Worker no usa puerto 9090"
fi

echo -e "\n${BLUE}=== RESUMEN DE VALIDACIÓN ===${NC}"

if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}✅ Todos los checks pasaron. Workers listos para AWS.${NC}"
    echo -e "\n${BLUE}Próximos pasos para despliegue:${NC}"
    echo "1. Crear instancias EC2 (Master + Workers)"
    echo "2. Configurar EFS o NFS Server"
    echo "3. Ejecutar scripts de configuración"
    echo "4. Compilar workers en las instancias EC2"
    echo "5. Iniciar Master y Workers"
    echo "6. Probar con cliente Python"
    exit 0
else
    echo -e "${RED}❌ Se encontraron $ERRORS errores. Revisar antes del despliegue.${NC}"
    exit 1
fi
