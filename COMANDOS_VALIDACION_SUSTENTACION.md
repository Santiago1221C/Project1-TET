# 📋 COMANDOS COMPLETOS PARA VALIDACIÓN EN SUSTENTACIÓN GRIDMR - AWS

## 🌐 CONFIGURACIÓN PARA AWS

### **Prerequisitos en AWS**
- **Instancia Master**: t3.medium o superior (2 vCPU, 4GB RAM)
- **Instancias Workers**: t3.small o superior (1 vCPU, 2GB RAM cada una)
- **EFS Montado**: En `/mnt/gridmr_nfs` en todas las instancias
- **Puertos Abiertos**: 8080 (Master), 9090-9092 (Workers)
- **Repositorio Clonado**: En todas las instancias

### **Configuración de Red**
```bash
# Verificar conectividad entre instancias
ping <IP_MASTER>
ping <IP_WORKER1>
ping <IP_WORKER2>
ping <IP_WORKER3>

# Verificar puertos abiertos
telnet <IP_MASTER> 8080
telnet <IP_WORKER1> 9090
telnet <IP_WORKER2> 9091
telnet <IP_WORKER3> 9092
```

---

## 🎯 ORDEN DE EJECUCIÓN PARA EL VIDEO EN AWS

### **FASE 1: PREPARACIÓN DEL SISTEMA EN AWS (2-3 minutos)**

#### **1.1 Verificar Estado Inicial en Master**
```bash
# En la instancia Master
# Verificar que no hay procesos en puerto 8080
sudo netstat -tulpn | grep :8080

# Si hay procesos, terminarlos
sudo pkill -f "spring-boot"

# Verificar EFS montado
df -h | grep gridmr_nfs
mount | grep gridmr_nfs
```

#### **1.2 Compilar y Preparar Master**
```bash
# En la instancia Master
# Navegar al directorio del proyecto
cd /home/ubuntu/Project1-TET

# Verificar que el repositorio está clonado
ls -la
git status

# Navegar al directorio del master
cd gridmr-master

# Instalar dependencias si es necesario
sudo apt update
sudo apt install -y openjdk-17-jdk maven

# Compilar el proyecto
mvn clean compile

# Verificar que compiló correctamente
echo "✅ Master compilado exitosamente"
```

#### **1.3 Compilar Workers en cada instancia**
```bash
# En cada instancia Worker (Worker1, Worker2, Worker3)
# Navegar al directorio del proyecto
cd /home/ubuntu/Project1-TET

# Instalar dependencias si es necesario
sudo apt update
sudo apt install -y build-essential cmake libgrpc++-dev libprotobuf-dev protobuf-compiler-grpc

# Navegar al directorio de workers
cd workers

# Compilar workers
make clean
make

# Verificar que compiló correctamente
echo "✅ Workers compilados exitosamente en $(hostname)"
```

---

### **FASE 2: INICIALIZACIÓN DEL SISTEMA EN AWS (3-4 minutos)**

#### **2.1 Iniciar Master**
```bash
# En la instancia Master
# Navegar al directorio del master
cd /home/ubuntu/Project1-TET/gridmr-master

# Iniciar Master en background
nohup mvn spring-boot:run > master.log 2>&1 &

# Obtener PID del proceso
MASTER_PID=$!
echo "Master iniciado con PID: $MASTER_PID"

# Esperar 30 segundos para que inicie
sleep 30

# Verificar que el Master está funcionando
curl http://localhost:8080/api/health

# Verificar logs del Master
tail -f master.log
```

#### **2.2 Iniciar Workers en cada instancia**
```bash
# En la instancia Worker 1
cd /home/ubuntu/Project1-TET/workers
nohup ./worker worker-001 9090 /mnt/gridmr_nfs > worker-001.log 2>&1 &
echo "Worker 1 iniciado en $(hostname)"

# En la instancia Worker 2
cd /home/ubuntu/Project1-TET/workers
nohup ./worker worker-002 9091 /mnt/gridmr_nfs > worker-002.log 2>&1 &
echo "Worker 2 iniciado en $(hostname)"

# En la instancia Worker 3
cd /home/ubuntu/Project1-TET/workers
nohup ./worker worker-003 9092 /mnt/gridmr_nfs > worker-003.log 2>&1 &
echo "Worker 3 iniciado en $(hostname)"

# Esperar 15 segundos para que se registren
sleep 15
```

#### **2.3 Verificar Conectividad entre Instancias**
```bash
# En la instancia Master
# Obtener IPs de las instancias (reemplazar con IPs reales)
MASTER_IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)
echo "Master IP: $MASTER_IP"

# Verificar que los workers pueden conectarse al master
# (Esto se verifica automáticamente cuando los workers se registran)
```

---

### **FASE 3: VALIDACIÓN BÁSICA DEL SISTEMA EN AWS (5-6 minutos)**

#### **3.1 Health Check del Master**
```bash
# En la instancia Master
echo "=== VERIFICANDO SALUD DEL MASTER ==="
curl -s http://localhost:8080/api/health | python3 -m json.tool

# Verificar que el Master está respondiendo desde otras instancias
# En cada instancia Worker
MASTER_IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)
curl -s http://$MASTER_IP:8080/api/health | python3 -m json.tool
```

#### **3.2 Listar Workers Registrados**
```bash
# En la instancia Master
echo "=== WORKERS REGISTRADOS ==="
curl -s http://localhost:8080/api/workers | python3 -m json.tool

# Verificar desde instancias Worker
curl -s http://$MASTER_IP:8080/api/workers | python3 -m json.tool
```

#### **3.3 Listar Nodos Disponibles**
```bash
# En la instancia Master
echo "=== NODOS DISPONIBLES ==="
curl -s http://localhost:8080/api/nodes | python3 -m json.tool

# Verificar desde instancias Worker
curl -s http://$MASTER_IP:8080/api/nodes | python3 -m json.tool
```

#### **3.4 Estadísticas del Sistema**
```bash
# En la instancia Master
echo "=== ESTADÍSTICAS DEL SISTEMA ==="
curl -s http://localhost:8080/api/statistics | python3 -m json.tool

# Verificar desde instancias Worker
curl -s http://$MASTER_IP:8080/api/statistics | python3 -m json.tool
```

#### **3.5 Verificar EFS Compartido**
```bash
# En todas las instancias
echo "=== VERIFICANDO EFS COMPARTIDO ==="
# Crear archivo de prueba en Master
echo "Test from Master $(hostname) at $(date)" > /mnt/gridmr_nfs/test_shared.txt

# Verificar desde Workers
cat /mnt/gridmr_nfs/test_shared.txt

# Crear archivo desde Worker
echo "Test from Worker $(hostname) at $(date)" >> /mnt/gridmr_nfs/test_shared.txt

# Verificar desde Master
cat /mnt/gridmr_nfs/test_shared.txt
```

---

### **FASE 4: VALIDACIÓN DE TOLERANCIA A FALLOS (4-5 minutos)**

#### **4.1 Estado de Persistencia**
```bash
echo "=== ESTADO DE PERSISTENCIA ==="
curl -s http://localhost:8080/api/persistence/status | python -m json.tool
```

#### **4.2 Estado de Failover**
```bash
echo "=== ESTADO DE FAILOVER ==="
curl -s http://localhost:8080/api/failover/status | python -m json.tool
```

#### **4.3 Estadísticas de Tolerancia a Fallos**
```bash
echo "=== ESTADÍSTICAS DE TOLERANCIA A FALLOS ==="
curl -s http://localhost:8080/api/fault-tolerance/text
```

#### **4.4 Estadísticas de Workers**
```bash
echo "=== ESTADÍSTICAS DE WORKERS ==="
curl -s http://localhost:8080/api/workers/statistics | python -m json.tool
```

#### **4.5 Estadísticas de Nodos**
```bash
echo "=== ESTADÍSTICAS DE NODOS ==="
curl -s http://localhost:8080/api/nodes/statistics | python -m json.tool
```

---

### **FASE 5: DEMOSTRACIÓN DE MAPREDUCE EN AWS (6-8 minutos)**

#### **5.1 Crear Archivo de Prueba**
```bash
# En la instancia Master
# Navegar al directorio del cliente
cd /home/ubuntu/Project1-TET/client

# Instalar dependencias Python si es necesario
pip3 install requests

# Crear archivo de prueba
echo "hello world hello grid computing map reduce distributed processing hello world" > test_input.txt
echo "map reduce is a programming model for processing large datasets" >> test_input.txt
echo "grid computing enables distributed processing across multiple nodes" >> test_input.txt
echo "hello world distributed systems are powerful" >> test_input.txt
echo "aws cloud computing provides scalable infrastructure" >> test_input.txt
echo "elastic compute cloud ec2 instances for workers" >> test_input.txt

echo "✅ Archivo de prueba creado"
```

#### **5.2 Ejecutar Trabajo MapReduce - WordCount**
```bash
# En la instancia Master
echo "=== EJECUTANDO TRABAJO MAPREDUCE - WORDCOUNT ==="
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
python3 client.py --create-sample --job-type wordcount --input-file test_input.txt --output-dir /mnt/gridmr_nfs/output/wordcount_$TIMESTAMP

# Verificar que el archivo se subió al EFS
ls -la /mnt/gridmr_nfs/input/
```

#### **5.3 Monitorear Progreso del Trabajo**
```bash
# En la instancia Master
echo "=== MONITOREANDO PROGRESO ==="
# Obtener ID del trabajo
JOB_ID=$(curl -s http://localhost:8080/api/jobs | python3 -c "import sys, json; data=json.load(sys.stdin); print(data[0]['jobId'] if data else 'NO_JOBS')")

echo "Job ID: $JOB_ID"

# Verificar estado del trabajo
curl -s http://localhost:8080/api/jobs/$JOB_ID/status | python3 -m json.tool

# Monitorear workers procesando
echo "=== WORKERS PROCESANDO ==="
curl -s http://localhost:8080/api/workers | python3 -m json.tool
```

#### **5.4 Listar Trabajos Activos**
```bash
# En la instancia Master
echo "=== TRABAJOS ACTIVOS ==="
curl -s http://localhost:8080/api/jobs | python3 -m json.tool

# Verificar desde instancias Worker
curl -s http://$MASTER_IP:8080/api/jobs | python3 -m json.tool
```

#### **5.5 Ejecutar Trabajo MapReduce - Sort**
```bash
# En la instancia Master
echo "=== EJECUTANDO TRABAJO MAPREDUCE - SORT ==="
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
python3 client.py --create-sample --job-type sort --input-file test_input.txt --output-dir /mnt/gridmr_nfs/output/sort_$TIMESTAMP
```

#### **5.6 Verificar Resultados en EFS**
```bash
# En todas las instancias
echo "=== VERIFICANDO RESULTADOS EN EFS ==="
# Listar directorios de salida
ls -la /mnt/gridmr_nfs/output/

# Mostrar resultados de WordCount
echo "=== RESULTADO WORDCOUNT ==="
find /mnt/gridmr_nfs/output/wordcount_* -name "part-*" -exec cat {} \;

# Mostrar resultados de Sort
echo "=== RESULTADO SORT ==="
find /mnt/gridmr_nfs/output/sort_* -name "part-*" -exec cat {} \;
```

---

### **FASE 6: DEMOSTRACIÓN DE TOLERANCIA A FALLOS EN AWS (5-6 minutos)**

#### **6.1 Simular Fallo de Worker**
```bash
# En la instancia Worker 2
echo "=== SIMULANDO FALLO DE WORKER 2 ==="
# Detener el worker
pkill -f "worker-002"

# Verificar que se detuvo
ps aux | grep worker-002

echo "Worker 2 detenido en $(hostname)"

# Esperar 15 segundos para que se detecte el fallo
sleep 15
```

#### **6.2 Verificar Detección de Fallo**
```bash
# En la instancia Master
echo "=== VERIFICANDO DETECCIÓN DE FALLO ==="
curl -s http://localhost:8080/api/workers | python3 -m json.tool

# Verificar desde otras instancias Worker
curl -s http://$MASTER_IP:8080/api/workers | python3 -m json.tool
```

#### **6.3 Verificar Reasignación de Tareas**
```bash
# En la instancia Master
echo "=== VERIFICANDO REASIGNACIÓN DE TAREAS ==="
curl -s http://localhost:8080/api/tasks | python3 -m json.tool

# Verificar logs del Master para ver reasignación
tail -f master.log | grep -i "reassign\|failover\|timeout"
```

#### **6.4 Restaurar Worker**
```bash
# En la instancia Worker 2
echo "=== RESTAURANDO WORKER 2 ==="
cd /home/ubuntu/Project1-TET/workers
nohup ./worker worker-002 9091 /mnt/gridmr_nfs > worker-002-restored.log 2>&1 &

echo "Worker 2 restaurado en $(hostname)"

# Esperar 10 segundos para que se registre
sleep 10
```

#### **6.5 Verificar Recuperación**
```bash
# En la instancia Master
echo "=== VERIFICANDO RECUPERACIÓN ==="
curl -s http://localhost:8080/api/workers | python3 -m json.tool

# Verificar desde otras instancias Worker
curl -s http://$MASTER_IP:8080/api/workers | python3 -m json.tool

# Verificar logs de recuperación
tail -f master.log | grep -i "recovered\|registered\|active"
```

#### **6.6 Simular Fallo de Instancia Completa**
```bash
# En la instancia Worker 3
echo "=== SIMULANDO FALLO DE INSTANCIA COMPLETA ==="
# Detener todos los procesos del worker
pkill -f "worker-003"

# Simular fallo de instancia (opcional - solo para demostración)
# sudo shutdown -h now

# En la instancia Master, verificar detección
echo "=== VERIFICANDO DETECCIÓN DE FALLO DE INSTANCIA ==="
curl -s http://localhost:8080/api/nodes | python3 -m json.tool
curl -s http://localhost:8080/api/workers | python3 -m json.tool
```

---

### **FASE 7: VALIDACIÓN FINAL DEL SISTEMA EN AWS (3-4 minutos)**

#### **7.1 Ejecutar Script de Validación Completa**
```bash
# En la instancia Master
echo "=== EJECUTANDO VALIDACIÓN COMPLETA ==="
cd /home/ubuntu/Project1-TET

# Crear script de validación para AWS
cat > validacion_aws.ps1 << 'EOF'
#!/bin/bash
echo "=== VALIDACIÓN COMPLETA DEL SISTEMA GRIDMR EN AWS ==="
echo "Fecha: $(date)"
echo "Master: $(hostname)"
echo ""

echo "1. Health Check del Master"
curl -s http://localhost:8080/api/health | python3 -m json.tool
echo ""

echo "2. Workers Registrados"
curl -s http://localhost:8080/api/workers | python3 -m json.tool
echo ""

echo "3. Nodos Disponibles"
curl -s http://localhost:8080/api/nodes | python3 -m json.tool
echo ""

echo "4. Trabajos Activos"
curl -s http://localhost:8080/api/jobs | python3 -m json.tool
echo ""

echo "5. Estado de Persistencia"
curl -s http://localhost:8080/api/persistence/status | python3 -m json.tool
echo ""

echo "6. Estado de Failover"
curl -s http://localhost:8080/api/failover/status | python3 -m json.tool
echo ""

echo "7. Estadísticas de Tolerancia a Fallos"
curl -s http://localhost:8080/api/fault-tolerance/text
echo ""

echo "8. Verificación de EFS"
ls -la /mnt/gridmr_nfs/
echo ""

echo "=== VALIDACIÓN COMPLETADA ==="
EOF

chmod +x validacion_aws.ps1
./validacion_aws.ps1
```

#### **7.2 Verificar Resultados de Procesamiento**
```bash
# En todas las instancias
echo "=== VERIFICANDO RESULTADOS ==="
# Listar archivos de salida
ls -la /mnt/gridmr_nfs/output/

# Mostrar contenido de resultados
echo "=== RESULTADO WORDCOUNT ==="
find /mnt/gridmr_nfs/output/wordcount_* -name "part-*" -exec cat {} \; | head -20

echo "=== RESULTADO SORT ==="
find /mnt/gridmr_nfs/output/sort_* -name "part-*" -exec cat {} \; | head -20

# Verificar que los resultados son consistentes entre instancias
echo "=== VERIFICANDO CONSISTENCIA ENTRE INSTANCIAS ==="
# En cada instancia Worker
echo "Verificando desde $(hostname):"
find /mnt/gridmr_nfs/output/ -name "part-*" -exec wc -l {} \;
```

#### **7.3 Estadísticas Finales**
```bash
# En la instancia Master
echo "=== ESTADÍSTICAS FINALES ==="
curl -s http://localhost:8080/api/statistics | python3 -m json.tool

# Verificar desde instancias Worker
echo "=== ESTADÍSTICAS DESDE WORKERS ==="
curl -s http://$MASTER_IP:8080/api/statistics | python3 -m json.tool

# Mostrar logs importantes
echo "=== LOGS DEL MASTER ==="
tail -20 master.log

echo "=== LOGS DE WORKERS ==="
# En cada instancia Worker
tail -10 worker-*.log
```

#### **7.4 Verificación de Recursos AWS**
```bash
# En todas las instancias
echo "=== VERIFICACIÓN DE RECURSOS AWS ==="
echo "Instancia: $(hostname)"
echo "IP Pública: $(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)"
echo "IP Privada: $(curl -s http://169.254.169.254/latest/meta-data/local-ipv4)"
echo "Tipo de Instancia: $(curl -s http://169.254.169.254/latest/meta-data/instance-type)"
echo "Zona de Disponibilidad: $(curl -s http://169.254.169.254/latest/meta-data/placement/availability-zone)"
echo "Uso de CPU:"
top -bn1 | grep "Cpu(s)"
echo "Uso de Memoria:"
free -h
echo "Uso de Disco:"
df -h
```

---

### **FASE 8: LIMPIEZA EN AWS (1-2 minutos)**

#### **8.1 Detener Workers**
```bash
# En cada instancia Worker
echo "=== DETENIENDO WORKERS ==="
pkill -f "worker-"
echo "Workers detenidos en $(hostname)"

# Verificar que se detuvieron
ps aux | grep worker
```

#### **8.2 Detener Master**
```bash
# En la instancia Master
echo "=== DETENIENDO MASTER ==="
pkill -f "spring-boot"
echo "Master detenido en $(hostname)"

# Verificar que se detuvo
ps aux | grep spring-boot
```

#### **8.3 Limpiar Archivos Temporales**
```bash
# En la instancia Master
echo "=== LIMPIANDO ARCHIVOS TEMPORALES ==="
rm -f client/test_input.txt
rm -f master.log
rm -f validacion_aws.ps1

# En cada instancia Worker
rm -f worker-*.log

echo "✅ Limpieza completada"
```

#### **8.4 Opcional: Terminar Instancias AWS**
```bash
# Solo si quieres terminar las instancias después de la demostración
echo "=== TERMINANDO INSTANCIAS AWS (OPCIONAL) ==="
# En cada instancia
echo "Para terminar esta instancia, ejecutar:"
echo "sudo shutdown -h now"
echo "O desde AWS Console: Actions > Instance State > Terminate"
```

---

## 🎬 GUÍA PARA EL VIDEO DE SUSTENTACIÓN EN AWS

### **ESTRUCTURA DEL VIDEO (25-30 minutos total)**

#### **1. Introducción y Configuración AWS (3 minutos)**
- Explicar qué es GridMR
- Mostrar arquitectura del sistema en AWS
- Explicar tecnologías utilizadas (EC2, EFS, gRPC, Spring Boot)
- Mostrar instancias EC2 creadas
- Explicar configuración de red y EFS

#### **2. Preparación del Sistema en AWS (4 minutos)**
- Ejecutar comandos de FASE 1 y 2
- Mostrar clonación del repositorio
- Explicar compilación en cada instancia
- Mostrar inicio de Master y Workers
- Verificar conectividad entre instancias

#### **3. Validación Básica del Sistema (4 minutos)**
- Ejecutar comandos de FASE 3
- Explicar cada endpoint y su función
- Mostrar que workers y nodos están registrados
- Verificar EFS compartido entre instancias
- Mostrar estadísticas del sistema

#### **4. Demostración MapReduce en AWS (6 minutos)**
- Ejecutar comandos de FASE 5
- Explicar el patrón MapReduce
- Mostrar procesamiento distribuido en múltiples instancias
- Explicar resultados obtenidos
- Verificar consistencia de resultados entre instancias

#### **5. Tolerancia a Fallos en AWS (5 minutos)**
- Ejecutar comandos de FASE 6
- Simular fallo de worker
- Mostrar detección y recuperación automática
- Simular fallo de instancia completa
- Explicar mecanismos de tolerancia a fallos

#### **6. Validación Final y Recursos AWS (4 minutos)**
- Ejecutar comandos de FASE 7
- Mostrar estadísticas finales
- Verificar resultados de procesamiento
- Mostrar uso de recursos AWS (CPU, memoria, disco)
- Verificar logs del sistema

#### **7. Conclusión y Evidencia (3 minutos)**
- Resumir logros del proyecto
- Explicar evidencia de Grid Computing en AWS
- Explicar evidencia de MapReduce distribuido
- Mencionar tolerancia a fallos implementada
- Mostrar escalabilidad en la nube

---

## 📝 NOTAS IMPORTANTES PARA EL VIDEO EN AWS

### **1. Preparación Antes de Grabar**
- Tener todas las instancias EC2 creadas y configuradas
- Verificar que EFS está montado en todas las instancias
- Tener el repositorio clonado en todas las instancias
- Verificar conectividad entre instancias
- Tener todas las terminales SSH abiertas y organizadas
- Preparar explicaciones para cada sección

### **2. Durante la Grabación**
- Ejecutar comandos paso a paso en cada instancia
- Explicar cada comando mientras se ejecuta
- Mostrar resultados en pantalla de cada instancia
- Explicar qué demuestra cada resultado
- Mostrar la distribución geográfica de las instancias

### **3. Puntos Clave a Destacar en AWS**
- **Grid Computing**: Múltiples instancias EC2 procesando en paralelo
- **MapReduce**: Fases Map y Reduce distribuidas en la nube
- **Tolerancia a Fallos**: Detección y recuperación automática en AWS
- **Comunicación**: REST para cliente, gRPC para workers a través de red
- **Escalabilidad**: Sistema puede manejar múltiples instancias EC2
- **Almacenamiento Distribuido**: EFS compartido entre todas las instancias
- **Cloud Computing**: Aprovechamiento de recursos de AWS

### **4. Comandos de Emergencia para AWS**
```bash
# Si algo falla, reiniciar Master
pkill -f "spring-boot"
cd /home/ubuntu/Project1-TET/gridmr-master
nohup mvn spring-boot:run > master.log 2>&1 &

# Si workers no responden, reiniciarlos
pkill -f "worker-"
cd /home/ubuntu/Project1-TET/workers
nohup ./worker worker-001 9090 /mnt/gridmr_nfs > worker-001.log 2>&1 &

# Verificar conectividad
ping <IP_MASTER>
telnet <IP_MASTER> 8080
```

### **5. Configuración de Seguridad AWS**
- Verificar Security Groups (puertos 8080, 9090-9092 abiertos)
- Verificar que las instancias están en la misma VPC
- Verificar que EFS está en la misma VPC
- Verificar que las instancias tienen acceso a internet

### **6. Monitoreo de Recursos AWS**
- Mostrar uso de CPU en cada instancia
- Mostrar uso de memoria en cada instancia
- Mostrar uso de EFS compartido
- Mostrar logs de CloudWatch (opcional)

---

## 🎯 CHECKLIST PRE-SUSTENTACIÓN PARA AWS

### **Configuración AWS**
- [ ] Instancias EC2 creadas (1 Master + 3 Workers)
- [ ] EFS creado y montado en todas las instancias
- [ ] Security Groups configurados (puertos 8080, 9090-9092)
- [ ] Instancias en la misma VPC
- [ ] Conectividad entre instancias verificada
- [ ] Repositorio clonado en todas las instancias

### **Dependencias y Compilación**
- [ ] Java 17+ instalado en Master
- [ ] Maven instalado en Master
- [ ] C++17+ y gRPC instalados en Workers
- [ ] Python 3.12+ instalado en Master
- [ ] Master compila y ejecuta correctamente
- [ ] Workers compilan y ejecutan correctamente

### **Funcionalidad del Sistema**
- [ ] Cliente Python funciona correctamente
- [ ] EFS está montado y accesible desde todas las instancias
- [ ] Todos los endpoints REST responden
- [ ] Workers se registran correctamente
- [ ] Trabajos MapReduce se ejecutan correctamente
- [ ] Tolerancia a fallos funciona
- [ ] Script de validación funciona
- [ ] Resultados se generan correctamente

### **Validación de Red**
- [ ] Master responde desde Workers
- [ ] Workers pueden comunicarse con Master
- [ ] EFS es accesible desde todas las instancias
- [ ] Puertos están abiertos y funcionando
- [ ] DNS resuelve correctamente

### **Preparación para Video**
- [ ] Todas las terminales SSH abiertas
- [ ] Comandos probados y funcionando
- [ ] Explicaciones preparadas
- [ ] Scripts de validación listos
- [ ] Logs configurados para monitoreo

---

**¡Con esta guía tienes todo lo necesario para una sustentación exitosa! 🚀**
