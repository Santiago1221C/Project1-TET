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
    
    // Configuración
    private static final int HEARTBEAT_INTERVAL_SECONDS = 10;
    private static final int WORKER_TIMEOUT_SECONDS = 30;
    private static final int CLEANUP_INTERVAL_SECONDS = 60;
    
    // Estadísticas
    private int totalWorkersRegistered;
    private int totalWorkersActive;
    private int totalTasksAssigned;
    
    public ResourceManager() {
        this.registeredWorkers = new ConcurrentHashMap<>();
        this.availableWorkers = new ConcurrentHashMap<>();
        this.busyWorkers = new ConcurrentHashMap<>();
        this.inactiveWorkers = new ConcurrentHashMap<>();
        
        this.totalWorkersRegistered = 0;
        this.totalWorkersActive = 0;
        this.totalTasksAssigned = 0;
        
        System.out.println("ResourceManager inicializado");
    }
    
    public void start() {
        System.out.println("Iniciando ResourceManager...");
        
        // Iniciar scheduler para tareas periódicas
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // Tarea periódica para verificar heartbeats
        scheduler.scheduleAtFixedRate(
            this::checkWorkerHeartbeats,
            HEARTBEAT_INTERVAL_SECONDS,
            HEARTBEAT_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        // Tarea periódica para limpieza de workers inactivos
        scheduler.scheduleAtFixedRate(
            this::cleanupInactiveWorkers,
            CLEANUP_INTERVAL_SECONDS,
            CLEANUP_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        System.out.println("ResourceManager iniciado - Monitoreo de workers activo");
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
     * Actualiza el heartbeat de un worker
     * @param workerId ID del worker
     * @return true si el worker existe y se actualizó el heartbeat
     */
    public boolean updateWorkerHeartbeat(String workerId) {
        Worker worker = registeredWorkers.get(workerId);
        if (worker == null) {
            return false;
        }
        
        worker.updateHeartbeat();
        
        // Si el worker estaba inactivo, moverlo a disponible
        if (worker.getStatus() == WorkerStatus.OFFLINE) {
            worker.setStatus(WorkerStatus.READY);
            inactiveWorkers.remove(workerId);
            availableWorkers.put(workerId, worker);
            totalWorkersActive++;
            System.out.println("Worker " + workerId + " reactivado");
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
            System.out.println("Workers inactivos detectados: " + inactiveWorkerIds);
        }
    }
    
    /**
     * Marca un worker como inactivo
     * @param workerId ID del worker
     */
    private void markWorkerAsInactive(String workerId) {
        Worker worker = registeredWorkers.get(workerId);
        if (worker == null) {
            return;
        }
        
        // Cambiar estado a OFFLINE
        worker.setStatus(WorkerStatus.OFFLINE);
        
        // Mover de disponible/ocupado a inactivo
        availableWorkers.remove(workerId);
        busyWorkers.remove(workerId);
        inactiveWorkers.put(workerId, worker);
        
        totalWorkersActive--;
        
        System.out.println("Worker " + workerId + " marcado como inactivo");
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
     * Obtiene estadísticas del sistema
     * @return String con estadísticas detalladas
     */
    public String getSystemStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== ESTADÍSTICAS DEL SISTEMA ===\n");
        stats.append("Workers registrados: ").append(totalWorkersRegistered).append("\n");
        stats.append("Workers activos: ").append(totalWorkersActive).append("\n");
        stats.append("Workers disponibles: ").append(availableWorkers.size()).append("\n");
        stats.append("Workers ocupados: ").append(busyWorkers.size()).append("\n");
        stats.append("Workers inactivos: ").append(inactiveWorkers.size()).append("\n");
        stats.append("Tareas asignadas: ").append(totalTasksAssigned).append("\n");
        
        // Estadísticas por worker
        stats.append("\n=== DETALLES POR WORKER ===\n");
        for (Worker worker : registeredWorkers.values()) {
            stats.append("Worker ").append(worker.getWorkerId())
                .append(" - Estado: ").append(worker.getStatus())
                .append(", Carga: ").append(worker.getCurrentLoad())
                .append("/").append(worker.getMaxConcurrentTasks())
                .append(", Score: ").append(String.format("%.2f", worker.getPriorityScore()))
                .append(", Tareas completadas: ").append(worker.getCompletedTasks())
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
                System.out.println("✅ Worker " + workerId + " marcado como disponible");
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("❌ Error marcando worker como disponible: " + e.getMessage());
            return false;
        }
    }
}