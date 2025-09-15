package com.gridmr.master.components;

import com.gridmr.master.model.Worker;
import com.gridmr.master.model.WorkerStatus;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ResourceManager {
    
    // Mapa de workers registrados (workerId -> Worker)
    private final Map<String, Worker> registeredWorkers;
    
    // Mapa de workers disponibles para asignación (workerId -> Worker)
    private final Map<String, Worker> availableWorkers;
    
    // Mapa de workers ocupados (workerId -> Worker)
    private final Map<String, Worker> busyWorkers;
    
    // Mapa de workers inactivos (workerId -> Worker)
    private final Map<String, Worker> inactiveWorkers;
    
    // Scheduler para tareas periódicas (heartbeats, limpieza)
    private ScheduledExecutorService scheduler;
    
    // Configuración mejorada para tolerancia a fallos
    private static final int HEARTBEAT_INTERVAL_SECONDS = 5; // Reducido de 10 a 5 segundos
    private static final int WORKER_TIMEOUT_SECONDS = 10; // Reducido de 30 a 10 segundos
    private static final int CLEANUP_INTERVAL_SECONDS = 15; // Reducido de 60 a 15 segundos
    private static final int MAX_RETRY_ATTEMPTS = 3; // Máximo de reintentos por worker
    private static final int HEALTH_CHECK_INTERVAL_SECONDS = 3; // Health checks cada 3 segundos
    
    // Estadísticas
    private int totalWorkersRegistered;
    private int totalWorkersActive;
    private int totalTasksAssigned;
    
    // Contador de reintentos por worker para tolerancia a fallos
    private final Map<String, Integer> workerRetryCount;
    
    public ResourceManager() {
        this.registeredWorkers = new ConcurrentHashMap<>();
        this.availableWorkers = new ConcurrentHashMap<>();
        this.busyWorkers = new ConcurrentHashMap<>();
        this.inactiveWorkers = new ConcurrentHashMap<>();
        this.workerRetryCount = new ConcurrentHashMap<>();
        
        this.totalWorkersRegistered = 0;
        this.totalWorkersActive = 0;
        this.totalTasksAssigned = 0;
        
        System.out.println("ResourceManager inicializado con tolerancia a fallos mejorada");
    }
    
    public void start() {
        System.out.println("Iniciando ResourceManager con tolerancia a fallos...");
        
        // Iniciar scheduler para tareas periódicas
        this.scheduler = Executors.newScheduledThreadPool(3); // Aumentado para health checks
        
        // Tarea periódica para verificar heartbeats (más frecuente)
        scheduler.scheduleAtFixedRate(
            this::checkWorkerHeartbeats,
            HEARTBEAT_INTERVAL_SECONDS,
            HEARTBEAT_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        // Tarea periódica para health checks proactivos
        scheduler.scheduleAtFixedRate(
            this::performProactiveHealthChecks,
            HEALTH_CHECK_INTERVAL_SECONDS,
            HEALTH_CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        // Tarea periódica para limpieza de workers inactivos (más frecuente)
        scheduler.scheduleAtFixedRate(
            this::cleanupInactiveWorkers,
            CLEANUP_INTERVAL_SECONDS,
            CLEANUP_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        System.out.println("[OK] ResourceManager iniciado - Monitoreo avanzado activo:");
        System.out.println("   - Heartbeats cada " + HEARTBEAT_INTERVAL_SECONDS + "s");
        System.out.println("   - Health checks cada " + HEALTH_CHECK_INTERVAL_SECONDS + "s");
        System.out.println("   - Timeout: " + WORKER_TIMEOUT_SECONDS + "s");
        System.out.println("   - Cleanup cada " + CLEANUP_INTERVAL_SECONDS + "s");
    }
    
    public void stop() {
        System.out.println("Deteniendo ResourceManager...");
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.println("ResourceManager detenido");
    }
    
    // MÉTODOS DE REGISTRO DE WORKERS
    
    /**
     * Registra un nuevo worker en el sistema
     * @param workerId ID único del worker
     * @param host Dirección IP del worker
     * @param port Puerto del worker
     * @param cpuCores Número de cores de CPU
     * @param memoryMB Memoria disponible en MB
     * @param diskSpaceGB Espacio en disco disponible en GB
     * @param computePower Poder de cómputo simulado (1-100)
     * @param maxConcurrentTasks Máximo número de tareas concurrentes
     * @return true si el worker se registró exitosamente
     */
    public boolean registerWorker(String workerId, String host, int port, int cpuCores, long memoryMB, long diskSpaceGB, int computePower, int maxConcurrentTasks) {
        
        // Verificar si el worker ya está registrado
        if (registeredWorkers.containsKey(workerId)) {
            System.out.println("Worker " + workerId + " ya está registrado");
            return false;
        }
        
        // Creación de nuevo worker
        Worker worker = new Worker(workerId, host, port);
        worker.setCpuCores(cpuCores);
        worker.setMemoryMB(memoryMB);
        worker.setDiskSpaceGB(diskSpaceGB);
        worker.setComputePower(computePower);
        worker.setMaxConcurrentTasks(maxConcurrentTasks);
        worker.setStatus(WorkerStatus.READY);
        
        registeredWorkers.put(workerId, worker);
        availableWorkers.put(workerId, worker);
        
        totalWorkersRegistered++;
        totalWorkersActive++;
        
        System.out.println("Worker registrado: " + workerId + " (" + host + ":" + port + ") - CPU: " + cpuCores + ", Memoria: " + memoryMB + "MB, " + "Poder: " + computePower + ", MaxTareas: " + maxConcurrentTasks);
        
        return true;
    }
    
    /**
     * Dar de baja a un worker en el sistema
     * @param workerId ID del worker a borrar
     * @return true si el worker se borró exitosamente
     */
    public boolean unregisterWorker(String workerId) {
        Worker worker = registeredWorkers.remove(workerId);
        if (worker == null) {
            System.out.println("Worker " + workerId + " no está registrado");
            return false;
        }
        
        // Remover de todos los mapas
        availableWorkers.remove(workerId);
        busyWorkers.remove(workerId);
        inactiveWorkers.remove(workerId);
        
        totalWorkersActive--;
        
        System.out.println("Worker dado de baja: " + workerId);
        return true;
    }
    
    /**
     * Actualiza el heartbeat de un worker con tolerancia a fallos mejorada
     * @param workerId ID del worker
     * @return true si el worker existe y se actualizó el heartbeat
     */
    public boolean updateWorkerHeartbeat(String workerId) {
        Worker worker = registeredWorkers.get(workerId);
        if (worker == null) {
            System.out.println("[WARN] Heartbeat de worker inexistente: " + workerId);
            return false;
        }
        
        worker.updateHeartbeat();
        
        // Resetear contador de reintentos al recibir heartbeat
        workerRetryCount.put(workerId, 0);
        
        // Si el worker estaba inactivo, moverlo a disponible
        if (worker.getStatus() == WorkerStatus.OFFLINE) {
            worker.setStatus(WorkerStatus.READY);
            inactiveWorkers.remove(workerId);
            availableWorkers.put(workerId, worker);
            totalWorkersActive++;
            System.out.println("[OK] Worker " + workerId + " reactivado exitosamente");
        } else {
            System.out.println("[INFO] Heartbeat recibido de worker " + workerId + " - Estado: " + worker.getStatus());
        }
        
        return true;
    }
    
    /**
     * Obtiene información de un worker específico
     * @param workerId ID del worker
     * @return Worker o null si no existe
     */
    public Worker getWorker(String workerId) {
        return registeredWorkers.get(workerId);
    }
    
    /**
     * Obtiene todos los workers registrados
     * @return Lista de workers
     */
    public List<Worker> getAllWorkers() {
        return new ArrayList<>(registeredWorkers.values());
    }
    
    /**
     * Obtiene todos los workers disponibles
     * @return Lista de workers disponibles
     */
    public List<Worker> getAvailableWorkers() {
        return new ArrayList<>(availableWorkers.values());
    }
    
    /**
     * Obtiene todos los workers ocupados
     * @return Lista de workers ocupados
     */
    public List<Worker> getBusyWorkers() {
        return new ArrayList<>(busyWorkers.values());
    }
    
    // MÉTODOS DE ASIGNACIÓN DE TAREAS
    
    /**
     * Encuentra el mejor worker disponible para una tarea
     * @param taskType Tipo de tarea (MAP o REDUCE)
     * @param taskPriority Prioridad de la tarea (1-10, mayor = más prioridad)
     * @return Worker disponible o null si no hay workers disponibles
     */
    public Worker findBestAvailableWorker(String taskType, int taskPriority) {
        if (availableWorkers.isEmpty()) {
            return null;
        }
        
        // Filtrar workers que pueden manejar el tipo de tarea
        List<Worker> candidates = availableWorkers.values().stream()
            .filter(Worker::isAvailable)
            .filter(worker -> worker.isActive(WORKER_TIMEOUT_SECONDS))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        if (candidates.isEmpty()) {
            return null;
        }
        
        // Ordenar por score de prioridad (mayor score = mejor candidato)
        candidates.sort((w1, w2) -> Double.compare(w2.getPriorityScore(), w1.getPriorityScore()));
        
        return candidates.get(0);
    }
    
    /**
     * Asigna una tarea a un worker específico
     * @param workerId ID del worker
     * @param taskId ID de la tarea
     * @return true si la asignación fue exitosa
     */
    public boolean assignTaskToWorker(String workerId, String taskId) {
        Worker worker = registeredWorkers.get(workerId);
        if (worker == null || !worker.isAvailable()) {
            return false;
        }
        
        // Asignar tarea al worker
        if (worker.assignTask(taskId)) {
            // Mover worker de disponible a ocupado si está al límite
            if (worker.isOverloaded()) {
                availableWorkers.remove(workerId);
                busyWorkers.put(workerId, worker);
                worker.setStatus(WorkerStatus.BUSY);
            }
            
            totalTasksAssigned++;
            System.out.println("Tarea " + taskId + " asignada a worker " + workerId);
            return true;
        }
        
        return false;
    }
    
    /**
     * Libera una tarea de un worker
     * @param workerId ID del worker
     * @param taskId ID de la tarea
     * @param executionTimeMs Tiempo de ejecución en milisegundos
     * @param success true si la tarea se completó exitosamente
     * @return true si la liberación fue exitosa
     */
    public boolean releaseTaskFromWorker(String workerId, String taskId, long executionTimeMs, boolean success) {
        Worker worker = registeredWorkers.get(workerId);
        if (worker == null) {
            return false;
        }
        
        // Liberar tarea del worker
        if (worker.releaseTask(taskId)) {
            // Registrar estadísticas
            if (success) {
                worker.recordTaskCompletion(executionTimeMs);
            } else {
                worker.recordTaskFailure();
            }
            
            // Mover worker de ocupado a disponible si tiene capacidad
            if (worker.isAvailable() && busyWorkers.containsKey(workerId)) {
                busyWorkers.remove(workerId);
                availableWorkers.put(workerId, worker);
                worker.setStatus(WorkerStatus.READY);
            }
            
            System.out.println("Tarea " + taskId + " liberada del worker " + workerId + " (éxito: " + success + ", tiempo: " + executionTimeMs + "ms)");
            return true;
        }
        
        return false;
    }
    
    /**
     * Obtiene el número de tareas activas de un worker
     * @param workerId ID del worker
     * @return Número de tareas activas o -1 si el worker no existe
     */
    public int getWorkerActiveTaskCount(String workerId) {
        Worker worker = registeredWorkers.get(workerId);
        return worker != null ? worker.getCurrentLoad() : -1;
    }
    
    /**
     * Verifica si un worker está disponible para recibir tareas
     * @param workerId ID del worker
     * @return true si el worker está disponible
     */
    public boolean isWorkerAvailable(String workerId) {
        Worker worker = registeredWorkers.get(workerId);
        return worker != null && worker.isAvailable();
    }
    
    // MÉTODOS DE MONITOREO Y LIMPIEZA
    
    /**
     * Verifica los heartbeats de todos los workers
     * Se ejecuta periódicamente para detectar workers inactivos
     */
    private void checkWorkerHeartbeats() {
        List<String> inactiveWorkerIds = new ArrayList<>();
        
        for (Worker worker : registeredWorkers.values()) {
            if (!worker.isActive(WORKER_TIMEOUT_SECONDS)) {
                inactiveWorkerIds.add(worker.getWorkerId());
            }
        }
        
        // Marcar workers inactivos
        for (String workerId : inactiveWorkerIds) {
            markWorkerAsInactive(workerId);
        }
        
        if (!inactiveWorkerIds.isEmpty()) {
            System.out.println("[WARN] Workers inactivos detectados: " + inactiveWorkerIds);
        }
    }
    
    /**
     * Realiza health checks proactivos para detectar problemas temprano
     * Se ejecuta más frecuentemente que el check de heartbeats
     */
    private void performProactiveHealthChecks() {
        try {
            long currentTime = System.currentTimeMillis();
            List<String> problematicWorkers = new ArrayList<>();
            
            // Verificar workers disponibles
            for (Map.Entry<String, Worker> entry : availableWorkers.entrySet()) {
                String workerId = entry.getKey();
                Worker worker = entry.getValue();
                
                if (isWorkerProblematic(worker, currentTime)) {
                    problematicWorkers.add(workerId);
                    incrementRetryCount(workerId);
                }
            }
            
            // Verificar workers ocupados
            for (Map.Entry<String, Worker> entry : busyWorkers.entrySet()) {
                String workerId = entry.getKey();
                Worker worker = entry.getValue();
                
                if (isWorkerProblematic(worker, currentTime)) {
                    problematicWorkers.add(workerId);
                    incrementRetryCount(workerId);
                }
            }
            
            // Manejar workers problemáticos
            for (String workerId : problematicWorkers) {
                handleProblematicWorker(workerId);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error en health check proactivo: " + e.getMessage());
        }
    }
    
    /**
     * Verifica si un worker tiene problemas
     */
    private boolean isWorkerProblematic(Worker worker, long currentTime) {
        if (worker == null || worker.getLastHeartbeat() == null) {
            return true;
        }
        
        long timeSinceLastHeartbeat = currentTime - worker.getLastHeartbeat().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        return timeSinceLastHeartbeat > (WORKER_TIMEOUT_SECONDS * 1000);
    }
    
    /**
     * Incrementa el contador de reintentos de un worker
     */
    private void incrementRetryCount(String workerId) {
        int currentRetries = workerRetryCount.getOrDefault(workerId, 0);
        workerRetryCount.put(workerId, currentRetries + 1);
        System.out.println("[WARN] Worker " + workerId + " - Reintento #" + (currentRetries + 1));
    }
    
    /**
     * Maneja un worker problemático según su número de reintentos
     */
    private void handleProblematicWorker(String workerId) {
        int retryCount = workerRetryCount.getOrDefault(workerId, 0);
        
        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            System.out.println("[ERROR] Worker " + workerId + " excedió reintentos máximos (" + MAX_RETRY_ATTEMPTS + ") - Marcando como inactivo");
            markWorkerAsInactive(workerId);
            workerRetryCount.remove(workerId);
        } else {
            System.out.println("[WARN] Worker " + workerId + " problemático - Reintento " + retryCount + "/" + MAX_RETRY_ATTEMPTS);
        }
    }
    
    /**
     * Marca un worker como inactivo con información de tolerancia a fallos
     * @param workerId ID del worker
     */
    private void markWorkerAsInactive(String workerId) {
        Worker worker = registeredWorkers.get(workerId);
        if (worker == null) {
            System.out.println("[WARN] Intento de marcar worker inexistente como inactivo: " + workerId);
            return;
        }
        
        // Obtener información de reintentos
        int retryCount = workerRetryCount.getOrDefault(workerId, 0);
        
        // Cambiar estado a OFFLINE
        worker.setStatus(WorkerStatus.OFFLINE);
        
        // Mover de disponible/ocupado a inactivo
        boolean wasAvailable = availableWorkers.remove(workerId) != null;
        boolean wasBusy = busyWorkers.remove(workerId) != null;
        inactiveWorkers.put(workerId, worker);
        
        totalWorkersActive--;
        
        // Limpiar contador de reintentos
        workerRetryCount.remove(workerId);
        
        System.out.println("[ERROR] Worker " + workerId + " marcado como inactivo:");
        System.out.println("   - Estado anterior: " + (wasAvailable ? "disponible" : (wasBusy ? "ocupado" : "desconocido")));
        System.out.println("   - Reintentos fallidos: " + retryCount);
        System.out.println("   - Workers activos restantes: " + totalWorkersActive);
    }
    
    /**
     * Limpia workers inactivos del sistema
     * Se ejecuta periódicamente para liberar recursos
     */
    private void cleanupInactiveWorkers() {
        if (inactiveWorkers.isEmpty()) {
            return;
        }
        
        List<String> workersToRemove = new ArrayList<>();
        
        for (Worker worker : inactiveWorkers.values()) {
            // Remover workers que han estado inactivos por más del timeout configurado
            long inactiveTime = java.time.Duration.between(worker.getLastHeartbeat(), java.time.LocalDateTime.now()).getSeconds();
            if (inactiveTime > WORKER_TIMEOUT_SECONDS) {
                workersToRemove.add(worker.getWorkerId());
            }
        }
        
        // Remover workers inactivos
        for (String workerId : workersToRemove) {
            unregisterWorker(workerId);
            System.out.println("Worker " + workerId + " removido por inactividad prolongada");
        }
    }
    
    // MÉTODOS DE ESTADÍSTICAS
    
    /**
     * Obtiene estadísticas del sistema con información de tolerancia a fallos
     * @return String con estadísticas detalladas
     */
    public String getSystemStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== ESTADÍSTICAS DEL SISTEMA (TOLERANCIA A FALLOS) ===\n");
        stats.append("Workers registrados: ").append(totalWorkersRegistered).append("\n");
        stats.append("Workers activos: ").append(totalWorkersActive).append("\n");
        stats.append("Workers disponibles: ").append(availableWorkers.size()).append("\n");
        stats.append("Workers ocupados: ").append(busyWorkers.size()).append("\n");
        stats.append("Workers inactivos: ").append(inactiveWorkers.size()).append("\n");
        stats.append("Tareas asignadas: ").append(totalTasksAssigned).append("\n");
        
        // Configuración de tolerancia a fallos
        stats.append("\n=== CONFIGURACIÓN DE TOLERANCIA A FALLOS ===\n");
        stats.append("Heartbeat interval: ").append(HEARTBEAT_INTERVAL_SECONDS).append("s\n");
        stats.append("Worker timeout: ").append(WORKER_TIMEOUT_SECONDS).append("s\n");
        stats.append("Health check interval: ").append(HEALTH_CHECK_INTERVAL_SECONDS).append("s\n");
        stats.append("Cleanup interval: ").append(CLEANUP_INTERVAL_SECONDS).append("s\n");
        stats.append("Max retry attempts: ").append(MAX_RETRY_ATTEMPTS).append("\n");
        
        // Estadísticas por worker con información de reintentos
        stats.append("\n=== DETALLES POR WORKER ===\n");
        for (Worker worker : registeredWorkers.values()) {
            int retryCount = workerRetryCount.getOrDefault(worker.getWorkerId(), 0);
            boolean isHealthy = worker.getLastHeartbeat() != null && 
                              (System.currentTimeMillis() - worker.getLastHeartbeat().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) < (WORKER_TIMEOUT_SECONDS * 1000);
            
            stats.append("Worker ").append(worker.getWorkerId())
                .append(" - Estado: ").append(worker.getStatus())
                .append(", Salud: ").append(isHealthy ? "✅" : "❌")
                .append(", Carga: ").append(worker.getCurrentLoad())
                .append("/").append(worker.getMaxConcurrentTasks())
                .append(", Score: ").append(String.format("%.2f", worker.getPriorityScore()))
                .append(", Tareas completadas: ").append(worker.getCompletedTasks())
                .append(", Reintentos: ").append(retryCount)
                .append("\n");
        }
        
        return stats.toString();
    }
    
    /**
     * Obtiene el número total de workers registrados
     * @return Número de workers
     */
    public int getTotalWorkersCount() {
        return totalWorkersRegistered;
    }
    
    /**
     * Obtiene el número de workers activos
     * @return Número de workers activos
     */
    public int getActiveWorkersCount() {
        return totalWorkersActive;
    }
    
    /**
     * Obtiene el número de workers disponibles
     * @return Número de workers disponibles
     */
    public int getAvailableWorkersCount() {
        return availableWorkers.size();
    }
    
    /**
     * Obtiene el número total de tareas asignadas
     * @return Número de tareas asignadas
     */
    public int getTotalTasksAssigned() {
        return totalTasksAssigned;
    }
    
    /**
     * Marca un worker como disponible
     * @param workerId ID del worker
     * @return true si el worker fue marcado como disponible
     */
    public boolean markWorkerAvailable(String workerId) {
        try {
            Worker worker = busyWorkers.remove(workerId);
            if (worker != null) {
                availableWorkers.put(workerId, worker);
                // Resetear contador de reintentos al marcar como disponible
                workerRetryCount.put(workerId, 0);
                System.out.println("[OK] Worker " + workerId + " marcado como disponible");
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("[ERROR] Error marcando worker como disponible: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Obtiene estadísticas de tolerancia a fallos en formato JSON
     * @return Map con estadísticas detalladas
     */
    public Map<String, Object> getFaultToleranceStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Estadísticas básicas
        stats.put("totalWorkers", totalWorkersRegistered);
        stats.put("activeWorkers", totalWorkersActive);
        stats.put("availableWorkers", availableWorkers.size());
        stats.put("busyWorkers", busyWorkers.size());
        stats.put("inactiveWorkers", inactiveWorkers.size());
        stats.put("totalTasksAssigned", totalTasksAssigned);
        
        // Configuración de tolerancia a fallos
        Map<String, Object> config = new HashMap<>();
        config.put("heartbeatIntervalSeconds", HEARTBEAT_INTERVAL_SECONDS);
        config.put("workerTimeoutSeconds", WORKER_TIMEOUT_SECONDS);
        config.put("healthCheckIntervalSeconds", HEALTH_CHECK_INTERVAL_SECONDS);
        config.put("cleanupIntervalSeconds", CLEANUP_INTERVAL_SECONDS);
        config.put("maxRetryAttempts", MAX_RETRY_ATTEMPTS);
        stats.put("faultToleranceConfig", config);
        
        // Estadísticas por worker
        List<Map<String, Object>> workerStats = new ArrayList<>();
        for (Worker worker : registeredWorkers.values()) {
            Map<String, Object> workerInfo = new HashMap<>();
            workerInfo.put("id", worker.getWorkerId());
            workerInfo.put("status", worker.getStatus().toString());
            workerInfo.put("host", worker.getHost());
            workerInfo.put("port", worker.getPort());
            workerInfo.put("cpuCores", worker.getCpuCores());
            workerInfo.put("memoryMB", worker.getMemoryMB());
            workerInfo.put("computePower", worker.getComputePower());
            workerInfo.put("currentLoad", worker.getCurrentLoad());
            workerInfo.put("maxConcurrentTasks", worker.getMaxConcurrentTasks());
            workerInfo.put("completedTasks", worker.getCompletedTasks());
            workerInfo.put("priorityScore", worker.getPriorityScore());
            workerInfo.put("retryCount", workerRetryCount.getOrDefault(worker.getWorkerId(), 0));
            
            // Verificar salud del worker
            boolean isHealthy = worker.getLastHeartbeat() != null && 
                              (System.currentTimeMillis() - worker.getLastHeartbeat().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) < (WORKER_TIMEOUT_SECONDS * 1000);
            workerInfo.put("isHealthy", isHealthy);
            
            if (worker.getLastHeartbeat() != null) {
                long timeSinceHeartbeat = System.currentTimeMillis() - worker.getLastHeartbeat().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                workerInfo.put("timeSinceLastHeartbeatMs", timeSinceHeartbeat);
            } else {
                workerInfo.put("timeSinceLastHeartbeatMs", -1);
            }
            
            workerStats.add(workerInfo);
        }
        stats.put("workers", workerStats);
        
        return stats;
    }
}