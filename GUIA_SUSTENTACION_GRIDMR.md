# GUÍA COMPLETA PARA SUSTENTACIÓN DEL PROYECTO GRIDMR

## 📋 INFORMACIÓN GENERAL DEL PROYECTO

### **¿Qué es GridMR?**
GridMR es un sistema distribuido de procesamiento de datos que implementa el patrón MapReduce en un entorno de Grid Computing. El sistema permite procesar grandes volúmenes de datos de manera paralela utilizando múltiples nodos de computación.

### **Arquitectura del Sistema**
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   CLIENTE       │    │     MASTER      │    │    WORKERS      │
│   (Python)      │◄──►│   (Java/Spring) │◄──►│    (C++/gRPC)   │
│   REST API      │    │   gRPC + REST   │    │   Map/Reduce    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   NFS/EFS       │    │   PERSISTENCIA  │    │   ALMACENAMIENTO│
│   (Compartido)  │    │   (Estado)      │    │   (Local)       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

---

## 🏗️ ESTRUCTURA COMPLETA DEL PROYECTO

### **1. Directorio Principal (`Project1-TET/`)**
```
Project1-TET/
├── gridmr-master/          # Nodo Master (Java/Spring Boot)
├── workers/                # Workers (C++/gRPC)
├── client/                 # Cliente (Python)
├── serv_persistor/         # Servicio de Persistencia (Python)
├── aws-deployment/         # Scripts de despliegue AWS
├── scripts/                # Scripts de configuración
├── nfs_shared/             # Almacenamiento compartido
└── generated/              # Archivos generados por protobuf
```

### **2. Nodo Master (`gridmr-master/`)**
```
gridmr-master/
├── src/main/java/com/gridmr/master/
│   ├── components/         # Componentes principales
│   │   ├── ResourceManager.java      # Gestión de workers
│   │   ├── NodeManager.java          # Gestión de nodos
│   │   ├── Scheduler.java            # Programador de tareas
│   │   ├── JobManager.java           # Gestión de trabajos
│   │   ├── ChunkManager.java         # Gestión de chunks
│   │   ├── MasterPersistenceManager.java  # Persistencia
│   │   └── MasterFailoverManager.java     # Tolerancia a fallos
│   ├── controller/         # Controladores REST
│   │   ├── GridMRRestController.java      # API REST principal
│   │   └── MasterGrpcController.java      # API gRPC
│   ├── grpc/               # Servicios gRPC
│   │   ├── MasterGrpcService.java         # Servicio gRPC
│   │   └── GrpcMessages.java              # Mensajes gRPC
│   ├── model/              # Modelos de datos
│   │   ├── Worker.java                    # Modelo Worker
│   │   ├── NodeInfo.java                  # Modelo Nodo
│   │   ├── Job.java                       # Modelo Trabajo
│   │   ├── Task.java                      # Modelo Tarea
│   │   └── [otros modelos...]
│   ├── config/             # Configuración Spring
│   │   └── GridMRConfiguration.java       # Configuración DI
│   ├── GridMRMaster.java           # Clase principal
│   └── GridMRMasterApplication.java       # Aplicación Spring Boot
├── src/main/proto/         # Archivos Protocol Buffers
│   ├── master_internal.proto              # Servicio interno
│   └── worker/                            # Servicios worker
├── pom.xml                 # Configuración Maven
└── README.md               # Documentación
```

### **3. Workers (`workers/`)**
```
workers/
├── src/
│   ├── worker_main.cpp     # Punto de entrada principal
│   ├── worker.cpp          # Implementación del worker
│   ├── map_task.cpp        # Tareas Map
│   └── reduce_task.cpp     # Tareas Reduce
├── generated/cpp/          # Archivos generados por protobuf
├── Makefile               # Compilación
└── WorkerServer.cpp       # Servidor gRPC
```

### **4. Cliente (`client/`)**
```
client/
├── client.py              # Cliente principal
├── requirements.txt       # Dependencias Python
└── test_input.txt         # Archivo de prueba
```

---

## 🔧 COMPONENTES PRINCIPALES Y SU FUNCIÓN

### **A. NODO MASTER (Java/Spring Boot)**

#### **1. COMPONENTES PRINCIPALES (`components/`)**

##### **1.1 ResourceManager.java** - Gestión de Workers
- **Función**: Gestiona la vida útil completa de los workers en el sistema
- **Responsabilidades**:
  - **Registro de Workers**: Registra nuevos workers con sus capacidades (CPU, memoria, disco)
  - **Monitoreo de Heartbeats**: Verifica cada 5 segundos que los workers estén activos
  - **Detección de Fallos**: Identifica workers inactivos con timeout de 10 segundos
  - **Asignación de Tareas**: Encuentra el mejor worker disponible para cada tarea
  - **Balanceamiento de Carga**: Distribuye tareas según capacidad y disponibilidad
- **Tolerancia a Fallos**:
  - Timeout de 10 segundos para workers
  - Reintentos automáticos (máximo 3 intentos)
  - Health checks proactivos cada 3 segundos
  - Reasignación automática de tareas cuando un worker falla
- **Métodos Clave**:
  - `registerWorker()`: Registra un nuevo worker
  - `findBestAvailableWorker()`: Encuentra el mejor worker para una tarea
  - `assignTaskToWorker()`: Asigna una tarea específica
  - `performProactiveHealthChecks()`: Monitoreo proactivo de salud

##### **1.2 NodeManager.java** - Gestión de Nodos Físicos
- **Función**: Gestiona los nodos físicos (EC2 instances) donde corren los workers
- **Responsabilidades**:
  - **Registro de Nodos**: Registra nodos con sus capacidades y tipos
  - **Balanceamiento de Carga**: Distribuye workers entre nodos disponibles
  - **Health Checks**: Monitorea la salud de los nodos cada 5 segundos
  - **Descubrimiento Automático**: Detecta nodos que se unen o salen del sistema
  - **Asignación Óptima**: Encuentra el mejor nodo para asignar un worker
- **Tolerancia a Fallos**:
  - Timeout de 30 segundos para nodos
  - Reintentos automáticos (máximo 3 intentos)
  - Migración automática de workers a otros nodos
  - Limpieza automática de nodos inactivos
- **Métodos Clave**:
  - `registerNode()`: Registra un nuevo nodo
  - `findBestNodeForWorker()`: Encuentra el mejor nodo para un worker
  - `calculateNodeScore()`: Calcula score de prioridad para balanceamiento
  - `performNodeHealthChecks()`: Monitoreo de salud de nodos

##### **1.3 Scheduler.java** - Programador de Tareas
- **Función**: Programa y distribuye tareas Map y Reduce a workers disponibles
- **Responsabilidades**:
  - **Gestión de Colas**: Mantiene colas separadas para tareas Map y Reduce
  - **Asignación Inteligente**: Asigna tareas según prioridad y disponibilidad
  - **Monitoreo de Progreso**: Rastrea el estado de todas las tareas asignadas
  - **Reasignación**: Reasigna tareas cuando un worker falla o excede timeout
  - **Gestión de Timeouts**: Detecta tareas que exceden su tiempo límite
- **Características**:
  - Colas separadas para Map y Reduce (Map tiene prioridad)
  - Timeout de 5 minutos por tarea
  - Reasignación automática en caso de fallos
  - Estadísticas detalladas de rendimiento
- **Métodos Clave**:
  - `addTask()`: Agrega una tarea a la cola correspondiente
  - `schedulePendingTasks()`: Asigna tareas pendientes automáticamente
  - `reassignTask()`: Reasigna una tarea a un nuevo worker
  - `checkTaskTimeouts()`: Verifica tareas que excedieron su timeout

##### **1.4 JobManager.java** - Gestión de Trabajos MapReduce
- **Función**: Gestiona el ciclo de vida completo de los trabajos MapReduce
- **Responsabilidades**:
  - **Creación de Trabajos**: Crea trabajos con archivos de entrada y configuración
  - **División en Tareas**: Divide trabajos en tareas Map y Reduce
  - **Monitoreo de Fases**: Rastrea el progreso de las fases Map y Reduce
  - **Gestión de Dependencias**: Asegura que Reduce solo inicie cuando Map termine
  - **Finalización**: Marca trabajos como completados o fallidos
- **Flujo de Trabajo**:
  1. Recibe trabajo del cliente
  2. Crea tareas Map para cada archivo de entrada
  3. Envía tareas Map al Scheduler
  4. Monitorea progreso de fase Map
  5. Cuando Map termina, crea tareas Reduce
  6. Monitorea progreso de fase Reduce
  7. Marca trabajo como completado
- **Métodos Clave**:
  - `submitJob()`: Envía un nuevo trabajo al sistema
  - `createAndSubmitMapTasks()`: Crea y envía tareas Map
  - `createAndSubmitReduceTasks()`: Crea y envía tareas Reduce
  - `checkJobProgress()`: Monitorea el progreso del trabajo

##### **1.5 ChunkManager.java** - Gestión de Fragmentos de Datos
- **Función**: Gestiona la división y transferencia de datos en fragmentos (chunks)
- **Responsabilidades**:
  - **División de Archivos**: Divide archivos grandes en chunks manejables
  - **Almacenamiento**: Almacena chunks en disco y memoria
  - **Transferencia**: Transfiere chunks a workers para procesamiento
  - **Gestión de Resultados**: Almacena resultados intermedios y finales
  - **Limpieza**: Limpia chunks antiguos automáticamente
- **Características**:
  - Chunks de 64MB por defecto
  - Almacenamiento híbrido (disco + memoria)
  - Transferencia asíncrona a workers
  - Retención de 24 horas para chunks
  - Límite de 10GB de almacenamiento
- **Métodos Clave**:
  - `createChunksFromFile()`: Divide un archivo en chunks
  - `transferChunkToWorker()`: Transfiere chunk a un worker
  - `storeIntermediateResult()`: Almacena resultado intermedio
  - `getIntermediateResults()`: Obtiene resultados para fase Reduce

##### **1.6 MasterPersistenceManager.java** - Persistencia del Estado
- **Función**: Persiste el estado del master para recuperación ante fallos
- **Responsabilidades**:
  - **Guardado Automático**: Guarda estado cada 30 segundos
  - **Recuperación**: Restaura estado desde archivo al reiniciar
  - **Backups**: Crea copias de seguridad del estado
  - **Sincronización**: Mantiene estado consistente entre masters
- **Datos Persistidos**:
  - Lista de workers registrados
  - Lista de nodos activos
  - Trabajos en progreso
  - Tareas asignadas
  - Configuración del sistema

##### **1.7 MasterFailoverManager.java** - Tolerancia a Fallos del Master
- **Función**: Gestiona la alta disponibilidad del master
- **Responsabilidades**:
  - **Elección de Líder**: Implementa algoritmo de elección de líder
  - **Sincronización**: Sincroniza estado entre múltiples masters
  - **Failover**: Cambia automáticamente a master de respaldo
  - **Recuperación**: Restaura operación normal tras fallos
- **Características**:
  - Múltiples masters pueden coexistir
  - Solo un master es líder a la vez
  - Failover automático en caso de fallo del líder
  - Sincronización de estado en tiempo real

#### **2. MODELOS DE DATOS (`model/`)**

##### **2.1 Worker.java** - Modelo de Worker
- **Función**: Representa un worker individual en el sistema
- **Propiedades**:
  - **Identificación**: `workerId`, `host`, `port`
  - **Capacidad**: `cpuCores`, `memoryMB`, `diskSpaceGB`, `computePower`
  - **Estado**: `status`, `currentLoad`, `maxConcurrentTasks`
  - **Metadatos**: `registeredAt`, `lastHeartbeat`, `lastTaskUpdate`
  - **Estadísticas**: `completedTasks`, `failedTasks`, `totalExecutionTimeMs`
- **Métodos Clave**:
  - `isAvailable()`: Verifica si puede recibir tareas
  - `getPriorityScore()`: Calcula score para asignación de tareas
  - `assignTask()`: Asigna una tarea al worker
  - `isActive()`: Verifica si está respondiendo heartbeats

##### **2.2 Job.java** - Modelo de Trabajo MapReduce
- **Función**: Representa un trabajo MapReduce completo
- **Propiedades**:
  - **Identificación**: `jobId`, `clientId`
  - **Estado**: `status`, `createdAt`, `startedAt`, `completedAt`
  - **Datos**: `inputFiles`, `outputDirectory`
  - **Configuración**: `numMappers`, `numReducers`, `mapFunction`, `reduceFunction`
  - **Tareas**: `mapTasks`, `reduceTasks`
  - **Resultados**: `intermediateResults`, `finalResults`
- **Métodos Clave**:
  - `isCompleted()`: Verifica si el trabajo terminó
  - `areMapTasksCompleted()`: Verifica si fase Map terminó
  - `areReduceTasksCompleted()`: Verifica si fase Reduce terminó

##### **2.3 Task.java** - Modelo de Tarea Individual
- **Función**: Representa una tarea Map o Reduce individual
- **Propiedades**:
  - **Identificación**: `taskId`, `jobId`, `workerId`
  - **Tipo**: `type` (MAP o REDUCE)
  - **Estado**: `status`, `createdAt`, `startedAt`, `completedAt`
  - **Datos**: `inputData`, `outputData`, `errorMessage`
  - **Configuración**: `functionCode`, `priority`
- **Métodos Clave**:
  - `isMapTask()`: Verifica si es tarea Map
  - `isReduceTask()`: Verifica si es tarea Reduce
  - `start()`: Marca tarea como iniciada
  - `complete()`: Marca tarea como completada

##### **2.4 DataChunk.java** - Modelo de Fragmento de Datos
- **Función**: Representa un fragmento de datos para procesamiento
- **Propiedades**:
  - **Identificación**: `chunkId`, `jobId`, `originalFileName`
  - **Ubicación**: `startOffset`, `endOffset`, `sizeBytes`
  - **Estado**: `location`, `assignedWorkerId`, `isProcessed`
  - **Metadatos**: `createdAt`, `assignedAt`, `processedAt`
- **Métodos Clave**:
  - `isAssigned()`: Verifica si está asignado a un worker
  - `isAvailable()`: Verifica si está disponible para asignación
  - `getRangeHeader()`: Obtiene header HTTP Range para transferencia

##### **2.5 NodeInfo.java** - Modelo de Nodo Físico
- **Función**: Representa un nodo físico en el sistema
- **Propiedades**:
  - **Identificación**: `nodeId`, `host`, `port`, `nodeType`
  - **Capacidad**: `maxWorkers`, `currentWorkers`, `cpuCores`, `memoryGB`
  - **Estado**: `status`, `registeredAt`, `lastHeartbeat`
  - **Estadísticas**: `totalWorkersAssigned`, `totalTasksCompleted`
- **Métodos Clave**:
  - `hasCapacity()`: Verifica si puede recibir más workers
  - `getPriorityScore()`: Calcula score para asignación de workers
  - `assignWorker()`: Asigna un worker al nodo

##### **2.6 Enums de Estado**
- **WorkerStatus**: `REGISTERED`, `READY`, `BUSY`, `OFFLINE`, `FAILED`
- **JobStatus**: `PENDING`, `MAP_PHASE`, `REDUCE_PHASE`, `COMPLETED`, `FAILED`, `CANCELLED`
- **TaskStatus**: `PENDING`, `ASSIGNED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`
- **TaskType**: `MAP`, `REDUCE`
- **NodeStatus**: `REGISTERED`, `ACTIVE`, `BUSY`, `OFFLINE`, `MAINTENANCE`, `ERROR`, `UNREGISTERED`

#### **3. IMPORTANCIA DE CADA ARCHIVO EN LA IMPLEMENTACIÓN**

##### **3.1 Archivos Críticos para Grid Computing**
- **ResourceManager.java**: **CRÍTICO** - Sin este archivo no hay Grid Computing. Gestiona la distribución de workers y tareas.
- **NodeManager.java**: **CRÍTICO** - Esencial para Grid Computing. Gestiona nodos físicos y balanceamiento de carga.
- **Scheduler.java**: **CRÍTICO** - Core del Grid Computing. Distribuye tareas entre workers disponibles.
- **ChunkManager.java**: **CRÍTICO** - Fundamental para procesamiento distribuido. Divide datos en chunks para procesamiento paralelo.

##### **3.2 Archivos Críticos para MapReduce**
- **JobManager.java**: **CRÍTICO** - Implementa el patrón MapReduce completo. Sin este no hay MapReduce.
- **Task.java**: **CRÍTICO** - Representa las tareas Map y Reduce individuales.
- **Job.java**: **CRÍTICO** - Representa un trabajo MapReduce completo con sus fases.
- **DataChunk.java**: **CRÍTICO** - Permite dividir datos para procesamiento Map paralelo.

##### **3.3 Archivos de Tolerancia a Fallos**
- **MasterPersistenceManager.java**: **IMPORTANTE** - Permite recuperación ante fallos del master.
- **MasterFailoverManager.java**: **IMPORTANTE** - Implementa alta disponibilidad del master.
- **Worker.java**: **IMPORTANTE** - Incluye lógica de health checks y disponibilidad.

##### **3.4 Archivos de Modelado**
- **NodeInfo.java**: **IMPORTANTE** - Modela nodos físicos para Grid Computing.
- **Enums**: **NECESARIOS** - Definen estados y tipos para el sistema.

##### **3.5 Flujo de Dependencias**
```
Cliente → JobManager → Scheduler → ResourceManager → Workers
    ↓         ↓           ↓            ↓
Job.java → Task.java → Worker.java → DataChunk.java
    ↓         ↓           ↓            ↓
NodeManager ← ChunkManager ← Scheduler ← JobManager
```

##### **3.6 Evidencia de Grid Computing en los Archivos**
1. **ResourceManager**: Distribuye workers en múltiples nodos
2. **NodeManager**: Gestiona nodos físicos distribuidos
3. **Scheduler**: Asigna tareas a workers en diferentes nodos
4. **ChunkManager**: Divide datos para procesamiento paralelo
5. **Worker**: Representa unidades de cómputo distribuidas

##### **3.7 Evidencia de MapReduce en los Archivos**
1. **JobManager**: Implementa fases Map y Reduce secuenciales
2. **Task**: Distingue entre tareas Map y Reduce
3. **Job**: Contiene tareas Map y Reduce separadas
4. **DataChunk**: Permite procesamiento Map paralelo
5. **Scheduler**: Prioriza tareas Map sobre Reduce

### **B. WORKERS (C++)**

#### **1. worker_main.cpp**
- **Función**: Punto de entrada del worker
- **Responsabilidades**:
  - Inicialización del servidor gRPC
  - Configuración de puertos y NFS
  - Manejo de argumentos de línea de comandos

#### **2. worker.cpp**
- **Función**: Implementación del worker
- **Responsabilidades**:
  - Ejecución de tareas Map
  - Ejecución de tareas Reduce
  - Comunicación con el master
  - Procesamiento de archivos

### **C. CLIENTE (Python)**

#### **1. client.py**
- **Función**: Interfaz de usuario del sistema
- **Responsabilidades**:
  - Subida de archivos al NFS
  - Envío de trabajos al master
  - Monitoreo del progreso
  - Descarga de resultados

---

## 🌐 COMUNICACIÓN ENTRE COMPONENTES

### **1. Cliente ↔ Master (REST API)**
- **Protocolo**: HTTP/HTTPS
- **Formato**: JSON
- **Endpoints principales**:
  - `POST /api/jobs/submit` - Enviar trabajo
  - `GET /api/jobs/{id}/status` - Estado del trabajo
  - `GET /api/workers` - Listar workers
  - `GET /api/health` - Health check

### **2. Master ↔ Workers (gRPC)**
- **Protocolo**: gRPC sobre HTTP/2
- **Formato**: Protocol Buffers
- **Servicios**:
  - `RegisterWorker` - Registro de worker
  - `Heartbeat` - Latido de vida
  - `ExecuteMapTask` - Ejecutar tarea Map
  - `ExecuteReduceTask` - Ejecutar tarea Reduce

### **3. Workers ↔ NFS (Sistema de archivos)**
- **Protocolo**: NFS/EFS
- **Uso**: Almacenamiento compartido de datos
- **Directorios**:
  - `/input/` - Archivos de entrada
  - `/intermediate/` - Resultados intermedios
  - `/output/` - Resultados finales

## 🔧 ¿POR QUÉ REST PARA CLIENTE-MASTER Y gRPC PARA MASTER-WORKERS?

### **1. REST API: Cliente ↔ Master**

#### **1.1 ¿Por qué REST para el Cliente?**
- **Simplicidad**: REST es fácil de entender y usar para desarrolladores
- **Estándar Web**: HTTP es universalmente soportado
- **Herramientas**: Muchas herramientas (Postman, curl, navegadores) soportan REST
- **Caching**: HTTP permite caching para mejorar rendimiento
- **Stateless**: Cada request es independiente, fácil de escalar

#### **1.2 Características del Cliente**
- **Interacción Humana**: El cliente es usado por humanos, no por máquinas
- **Frecuencia Baja**: No necesita comunicación constante como los workers
- **Datos Simples**: Envía configuraciones y recibe estados, no datos masivos
- **Debugging**: Fácil de debuggear con herramientas web estándar

#### **1.3 Endpoints REST Implementados**
```http
POST /api/jobs/submit          # Enviar trabajo
GET  /api/jobs/{id}/status     # Estado del trabajo
GET  /api/workers              # Listar workers
GET  /api/health               # Health check
GET  /api/nodes                # Listar nodos
POST /api/workers/register     # Registrar worker
```

### **2. gRPC: Master ↔ Workers**

#### **2.1 ¿Por qué gRPC para Workers?**
- **Alto Rendimiento**: Workers necesitan comunicación eficiente
- **Frecuencia Alta**: Heartbeats cada 5 segundos, asignación constante de tareas
- **Datos Binarios**: Transferencia de chunks de datos grandes
- **Tipado Fuerte**: Workers son máquinas, necesitan contratos claros
- **Streaming**: Monitoreo en tiempo real del progreso

#### **2.2 Características de los Workers**
- **Comunicación Constante**: Heartbeats, reportes de progreso, asignación de tareas
- **Datos Masivos**: Transferencia de chunks de datos
- **Automatización**: Los workers son procesos automatizados
- **Rendimiento Crítico**: La eficiencia es crucial para el Grid Computing

### **3. Comparación de Arquitecturas**

#### **3.1 REST vs gRPC - Cuándo Usar Cada Uno**

| Aspecto | REST (Cliente-Master) | gRPC (Master-Workers) |
|---------|----------------------|----------------------|
| **Frecuencia** | Baja (interacción humana) | Alta (heartbeats, tareas) |
| **Datos** | JSON (configuraciones) | Binary (chunks, streams) |
| **Usuarios** | Humanos (desarrolladores) | Máquinas (workers) |
| **Debugging** | Fácil (herramientas web) | Complejo (herramientas especializadas) |
| **Rendimiento** | Suficiente | Crítico |
| **Caching** | Importante | No relevante |
| **Streaming** | No necesario | Esencial |

#### **3.2 Flujo de Comunicación en GridMR**

```
┌─────────────┐    REST API     ┌─────────────┐    gRPC      ┌─────────────┐
│   Cliente   │◄──────────────►│    Master   │◄───────────►│   Workers   │
│  (Python)   │   HTTP/JSON    │   (Java)    │  HTTP/2/PB  │   (C++)     │
└─────────────┘                └─────────────┘              └─────────────┘
     │                              │                              │
     │ 1. Enviar trabajo            │ 2. Asignar tareas            │ 3. Procesar
     │ 2. Consultar estado          │ 3. Monitorear progreso       │ 4. Reportar
     │ 3. Descargar resultados      │ 4. Gestionar fallos          │ 5. Heartbeat
```

### **4. Ventajas de gRPC para Grid Computing**

#### **4.1 Alto Rendimiento**
- **HTTP/2**: Multiplexación de conexiones, reduce latencia
- **Serialización Binaria**: Protocol Buffers es más eficiente que JSON
- **Streaming**: Permite transferencia de datos en tiempo real
- **Compresión**: Reduce ancho de banda para transferencias de chunks

#### **4.2 Comunicación Bidireccional**
- **Heartbeats**: Workers pueden enviar latidos de vida continuamente
- **Notificaciones**: Master puede notificar cambios de estado instantáneamente
- **Streaming**: Permite monitoreo en tiempo real del progreso de tareas

#### **4.3 Tipado Fuerte**
- **Protocol Buffers**: Define contratos claros entre Master y Workers
- **Validación Automática**: Los datos se validan automáticamente
- **Compatibilidad**: Versiones diferentes pueden coexistir

#### **4.4 Escalabilidad**
- **Conexiones Persistentes**: Reduce overhead de conexiones
- **Balanceamiento**: gRPC soporta load balancing nativo
- **Multiplexación**: Una conexión puede manejar múltiples requests

### **5. Ventajas de REST para Cliente**

#### **5.1 Simplicidad y Usabilidad**
- **Fácil de Usar**: Cualquier desarrollador puede usar REST
- **Herramientas**: Postman, curl, navegadores web
- **Documentación**: Swagger/OpenAPI para documentación automática
- **Debugging**: Fácil de debuggear con herramientas web

#### **5.2 Estándares Web**
- **HTTP**: Protocolo estándar de la web
- **JSON**: Formato de datos universalmente soportado
- **Caching**: HTTP permite caching para mejorar rendimiento
- **Proxies**: Funciona a través de proxies y firewalls

#### **5.3 Flexibilidad**
- **Stateless**: Cada request es independiente
- **Escalabilidad**: Fácil de escalar horizontalmente
- **Versionado**: Fácil de versionar APIs
- **Integración**: Fácil integración con otros sistemas

### **6. Implementación Específica en GridMR**

#### **6.1 REST API - Cliente a Master**
```java
@RestController
@RequestMapping("/api")
public class GridMRRestController {
    @PostMapping("/jobs/submit")
    public ResponseEntity<JobResponse> submitJob(@RequestBody JobRequest request);
    
    @GetMapping("/jobs/{id}/status")
    public ResponseEntity<JobStatus> getJobStatus(@PathVariable String id);
    
    @GetMapping("/workers")
    public ResponseEntity<List<Worker>> getWorkers();
}
```

#### **6.2 gRPC - Master a Workers**
```protobuf
service MasterInternalService {
  rpc RegisterWorker(WorkerRegistrationRequest) returns (WorkerRegistrationResponse);
  rpc Heartbeat(HeartbeatRequest) returns (HeartbeatResponse);
  rpc ExecuteMapTask(MapTaskRequest) returns (MapTaskResponse);
  rpc ExecuteReduceTask(ReduceTaskRequest) returns (ReduceTaskResponse);
}
```

### **7. Evidencia de la Decisión Arquitectónica**

#### **7.1 REST para Cliente - Evidencia**
- **Interfaz Humana**: El cliente es usado por desarrolladores
- **Frecuencia Baja**: No necesita comunicación constante
- **Simplicidad**: Fácil de implementar y mantener
- **Estándares**: Usa protocolos web estándar

#### **7.2 gRPC para Workers - Evidencia**
- **Alto Rendimiento**: Workers necesitan comunicación eficiente
- **Frecuencia Alta**: Heartbeats cada 5 segundos
- **Datos Binarios**: Transferencia de chunks grandes
- **Automatización**: Workers son procesos automatizados

### **8. Beneficios de la Arquitectura Híbrida**

#### **8.1 Lo Mejor de Ambos Mundos**
- **REST**: Simplicidad para el cliente
- **gRPC**: Rendimiento para workers
- **Flexibilidad**: Cada componente usa la tecnología más apropiada
- **Mantenibilidad**: Fácil de mantener y extender

#### **8.2 Escalabilidad**
- **Cliente**: REST escala horizontalmente
- **Workers**: gRPC maneja múltiples workers eficientemente
- **Master**: Puede manejar ambos tipos de comunicación

#### **8.3 Tolerancia a Fallos**
- **REST**: HTTP tiene retry logic incorporado
- **gRPC**: Reconexión automática y retry logic
- **Híbrido**: Tolerancia a fallos en ambos niveles

---

## 🚀 PROCESO DE EJECUCIÓN COMPLETO

### **1. Inicialización del Sistema**
```bash
# 1. Iniciar Master
cd gridmr-master
mvn spring-boot:run

# 2. Iniciar Workers
cd workers
make
./worker worker-001 9090 /mnt/gridmr_nfs
./worker worker-002 9091 /mnt/gridmr_nfs

# 3. Ejecutar Cliente
cd client
python client.py --create-sample --job-type wordcount
```

### **2. Flujo de un Trabajo MapReduce**

#### **Fase 1: Preparación**
1. Cliente sube archivo al NFS
2. Cliente envía trabajo al Master via REST
3. Master valida el trabajo y crea Job
4. Master divide el trabajo en tareas Map

#### **Fase 2: Ejecución Map**
1. Master asigna tareas Map a workers disponibles
2. Workers procesan chunks de datos en paralelo
3. Workers guardan resultados intermedios en NFS
4. Workers notifican completación al Master

#### **Fase 3: Ejecución Reduce**
1. Master agrupa resultados intermedios por clave
2. Master asigna tareas Reduce a workers
3. Workers procesan grupos de datos intermedios
4. Workers generan resultados finales

#### **Fase 4: Finalización**
1. Master consolida resultados finales
2. Master notifica completación al Cliente
3. Cliente descarga resultados del NFS

---

## 🛡️ TOLERANCIA A FALLOS IMPLEMENTADA

### **1. Tolerancia a Fallos de Workers**
- **Detección**: Heartbeats cada 5 segundos
- **Timeout**: 10 segundos sin respuesta
- **Recuperación**: Reintentos automáticos (máximo 3)
- **Reasignación**: Tareas se reasignan a otros workers

### **2. Tolerancia a Fallos de Nodos**
- **Detección**: Health checks cada 3 segundos
- **Timeout**: 30 segundos sin respuesta
- **Recuperación**: Migración de workers a otros nodos
- **Balanceamiento**: Distribución automática de carga

### **3. Tolerancia a Fallos del Master**
- **Persistencia**: Estado guardado cada 30 segundos
- **Failover**: Elección automática de nuevo líder
- **Recuperación**: Restauración del estado desde backup
- **Sincronización**: Estado compartido entre masters

---

## 📊 EVIDENCIA DE GRID COMPUTING

### **1. Distribución de Carga**
- Múltiples nodos procesando en paralelo
- Balanceamiento automático de tareas
- Escalabilidad horizontal

### **2. Procesamiento Paralelo**
- Tareas Map ejecutándose simultáneamente
- Tareas Reduce procesando grupos en paralelo
- Optimización de recursos disponibles

### **3. Almacenamiento Distribuido**
- NFS compartido entre todos los nodos
- Acceso concurrente a datos
- Persistencia distribuida

---

## 🔄 EVIDENCIA DE MAP REDUCE

### **1. Fase Map**
```cpp
// En worker.cpp - execute_wordcount_map()
void execute_wordcount_map(const std::string& task_id, const std::string& input_file) {
    std::map<std::string, int> word_count;
    // Procesar chunk de datos
    while (std::getline(file, line)) {
        std::istringstream iss(line);
        while (iss >> word) {
            word_count[word]++;  // MAP: contar palabras
        }
    }
    // Guardar resultado intermedio
    for (const auto& pair : word_count) {
        output << pair.first << "\t" << pair.second << std::endl;
    }
}
```

### **2. Fase Reduce**
```cpp
// En worker.cpp - execute_wordcount_reduce()
void execute_wordcount_reduce(const std::string& task_id, 
                             const std::vector<std::string>& input_files,
                             const std::string& output_file) {
    std::map<std::string, int> final_count;
    // Procesar todos los archivos intermedios
    for (const std::string& file : input_files) {
        while (input >> word >> count) {
            final_count[word] += count;  // REDUCE: sumar conteos
        }
    }
    // Guardar resultado final
    for (const auto& pair : final_count) {
        output << pair.first << "\t" << pair.second << std::endl;
    }
}
```

---

## 🎯 COMANDOS DE VALIDACIÓN

### **1. Script de Validación Completa**
```powershell
# Ejecutar validación completa
.\validacion_sistema.ps1
```

### **2. Comandos Individuales**
```bash
# 1. Health Check
curl http://localhost:8080/api/health

# 2. Listar Workers
curl http://localhost:8080/api/workers

# 3. Listar Nodos
curl http://localhost:8080/api/nodes

# 4. Estado de Persistencia
curl http://localhost:8080/api/persistence/status

# 5. Estado de Failover
curl http://localhost:8080/api/failover/status

# 6. Estadísticas de Tolerancia a Fallos
curl http://localhost:8080/api/fault-tolerance/text
```

---

## 📈 MÉTRICAS Y MONITOREO

### **1. Métricas del Sistema**
- Workers activos/inactivos
- Nodos disponibles
- Tareas completadas/fallidas
- Tiempo de ejecución
- Uso de recursos

### **2. Logs y Debugging**
- Logs del Master (Spring Boot)
- Logs de Workers (C++)
- Logs del Cliente (Python)
- Trazas de gRPC
- Métricas de tolerancia a fallos

---

## 🔧 CONFIGURACIÓN Y DESPLIEGUE

### **1. Variables de Entorno**
```bash
# Master
GRIDMR_MASTER_HOST=localhost
GRIDMR_MASTER_PORT=8080

# NFS
GRIDMR_NFS_PATH=/mnt/gridmr_nfs

# Workers
GRIDMR_WORKER_PORT=9090
GRIDMR_WORKER_ID=worker-001
```

### **2. Scripts de Despliegue**
- `scripts/start_system.ps1` - Iniciar sistema completo
- `aws-deployment/` - Scripts para AWS
- `build.bat` - Compilación del proyecto

---

## 🎓 PUNTOS CLAVE PARA LA SUSTENTACIÓN

### **1. Arquitectura Distribuida**
- Separación clara de responsabilidades
- Comunicación asíncrona entre componentes
- Escalabilidad horizontal

### **2. Tolerancia a Fallos**
- Múltiples niveles de redundancia
- Recuperación automática
- Persistencia de estado

### **3. Procesamiento Paralelo**
- Implementación real de MapReduce
- Distribución eficiente de tareas
- Optimización de recursos

### **4. Tecnologías Modernas**
- gRPC para comunicación de alto rendimiento
- Spring Boot para microservicios
- Protocol Buffers para serialización eficiente
- NFS/EFS para almacenamiento distribuido

### **5. Monitoreo y Observabilidad**
- Health checks proactivos
- Métricas en tiempo real
- Logs estructurados
- Validación automática

---

## 📝 NOTAS ADICIONALES

### **1. Dependencias del Proyecto**
- Java 17+ (Master)
- C++17+ (Workers)
- Python 3.12+ (Cliente)
- Maven 3.6+ (Build)
- gRPC 1.58.0
- Spring Boot 3.2.12

### **2. Requisitos del Sistema**
- Sistema operativo: Windows/Linux
- Memoria: Mínimo 4GB RAM
- Almacenamiento: 10GB libres
- Red: Conectividad entre nodos

### **3. Limitaciones Conocidas**
- Máximo 100 workers por nodo
- Tamaño máximo de archivo: 1GB
- Timeout de trabajos: 30 minutos
- Reintentos máximos: 3 por componente

---

## 🚀 DEMOSTRACIÓN EN VIVO

### **1. Preparación**
1. Mostrar estructura del proyecto
2. Explicar arquitectura general
3. Describir tecnologías utilizadas

### **2. Ejecución**
1. Iniciar Master
2. Iniciar Workers
3. Ejecutar Cliente con trabajo de prueba
4. Mostrar monitoreo en tiempo real

### **3. Tolerancia a Fallos**
1. Simular fallo de worker
2. Mostrar recuperación automática
3. Demostrar reasignación de tareas

### **4. Validación**
1. Ejecutar script de validación
2. Mostrar métricas del sistema
3. Verificar resultados del procesamiento

---

**¡Esta guía te proporciona toda la información necesaria para una sustentación exitosa del proyecto GridMR!**
