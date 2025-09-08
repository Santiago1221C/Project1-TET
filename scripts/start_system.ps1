
#!/bin/bash

# GridMR System Startup Script
# Este script inicia el master y los workers del sistema GridMR

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Iniciando Sistema GridMR ===${NC}"

# Configuración por defecto
MASTER_PORT=8080
MASTER_HOST="localhost"
WORKER_COUNT=3
WORKER_BASE_PORT=8081

# Función para limpiar procesos al salir
cleanup() {
    echo -e "\n${YELLOW}Deteniendo procesos...${NC}"
    pkill -f "gridmr"
    pkill -f "master"
    pkill -f "worker"
    exit 0
}

# Capturar Ctrl+C
trap cleanup SIGINT

# Verificar si existe el directorio del proyecto
if [ ! -f "master.py" ] && [ ! -f "master/master.py" ] && [ ! -f "src/master.py" ]; then
    echo -e "${RED}Error: No se encontró master.py. Asegúrate de estar en el directorio correcto.${NC}"
    exit 1
fi

# Crear directorio de logs si no existe
mkdir -p logs

echo "1. Iniciando Master en puerto ${MASTER_PORT}..."

# Intentar diferentes ubicaciones para los archivos
if [ -f "master.py" ]; then
    python master.py --port ${MASTER_PORT} > logs/master.log 2>&1 &
elif [ -f "master/master.py" ]; then
    python master/master.py --port ${MASTER_PORT} > logs/master.log 2>&1 &
elif [ -f "src/master.py" ]; then
    python src/master.py --port ${MASTER_PORT} > logs/master.log 2>&1 &
else
    echo -e "${RED}Error: No se encontró el archivo master.py${NC}"
    exit 1
fi

MASTER_PID=$!
echo "Master iniciado (PID: ${MASTER_PID})"

# Esperar a que el master esté listo
echo "Esperando a que el master esté disponible..."
sleep 3

# Verificar que el master esté ejecutándose
if ! curl -s http://${MASTER_HOST}:${MASTER_PORT}/health > /dev/null; then
    echo -e "${RED}Error: Master no responde en http://${MASTER_HOST}:${MASTER_PORT}${NC}"
    echo "Verifica los logs: tail -f logs/master.log"
    exit 1
fi

echo -e "${GREEN}Master está ejecutándose correctamente${NC}"

# Iniciar workers
echo "2. Iniciando ${WORKER_COUNT} workers..."

for i in $(seq 1 $WORKER_COUNT); do
    WORKER_PORT=$((WORKER_BASE_PORT + i - 1))
    echo "Iniciando Worker ${i} en puerto ${WORKER_PORT}..."
    
    # Intentar diferentes ubicaciones para los archivos
    if [ -f "worker.py" ]; then
        python worker.py --port ${WORKER_PORT} --master http://${MASTER_HOST}:${MASTER_PORT} > logs/worker_${i}.log 2>&1 &
    elif [ -f "worker/worker.py" ]; then
        python worker/worker.py --port ${WORKER_PORT} --master http://${MASTER_HOST}:${MASTER_PORT} > logs/worker_${i}.log 2>&1 &
    elif [ -f "src/worker.py" ]; then
        python src/worker.py --port ${WORKER_PORT} --master http://${MASTER_HOST}:${MASTER_PORT} > logs/worker_${i}.log 2>&1 &
    else
        echo -e "${RED}Error: No se encontró el archivo worker.py${NC}"
        cleanup
        exit 1
    fi
    
    WORKER_PID=$!
    echo "Worker ${i} iniciado (PID: ${WORKER_PID})"
    sleep 1
done

# Esperar a que los workers se registren
echo "Esperando a que los workers se registren..."
sleep 5

# Verificar estado del sistema
echo -e "\n${GREEN}=== Estado del Sistema ===${NC}"
echo "Master: http://${MASTER_HOST}:${MASTER_PORT}"
echo "Workers esperados: ${WORKER_COUNT}"

# Verificar conectividad
if curl -s http://${MASTER_HOST}:${MASTER_PORT}/status > /dev/null; then
    echo -e "${GREEN}✓ Sistema iniciado correctamente${NC}"
    echo ""
    echo "Para verificar el estado:"
    echo "  curl http://${MASTER_HOST}:${MASTER_PORT}/status"
    echo ""
    echo "Para ejecutar un trabajo:"
    echo "  python client.py --input test_input.txt --output result"
    echo ""
    echo "Para detener el sistema: Ctrl+C"
    echo ""
    echo "Logs disponibles en:"
    echo "  - Master: logs/master.log"
    echo "  - Workers: logs/worker_*.log"
else
    echo -e "${RED}✗ Error en la inicialización${NC}"
    cleanup
    exit 1
fi

# Mantener el script ejecutándose
echo "Sistema ejecutándose... Presiona Ctrl+C para detener."
while true; do
    sleep 10
    # Verificar que el master siga activo
    if ! curl -s http://${MASTER_HOST}:${MASTER_PORT}/health > /dev/null; then
        echo -e "${RED}Master no responde. Reiniciando sistema...${NC}"
        cleanup
        exit 1
    fi
done