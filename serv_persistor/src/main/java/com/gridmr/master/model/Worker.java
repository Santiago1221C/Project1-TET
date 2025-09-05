package com.gridmr.master.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * Representa un nodo trabajador (worker) en el grid
 * Cada worker puede ejecutar tareas Map o Reduce
 */
public class Worker {
    
    private String workerId;
    private String host;
    private int port;
    private WorkerStatus status;
    
    // Capacidad de cómputo
    private int cpuCores;
    private long memoryMB;
    private long diskSpaceGB;
    private int computePower; // Valor simulado de capacidad (1-100)
    
    // Estado actual
    private int currentLoad; // Número de tareas activas
    private int maxConcurrentTasks;
    private List<String> activeTaskIds;
    
    // Metadatos
    private LocalDateTime registeredAt;
    private LocalDateTime lastHeartbeat;
    private LocalDateTime lastTaskUpdate;
    
    // Estadísticas
    private int completedTasks;
    private long totalExecutionTimeMs;
    private int failedTasks;
    
    public Worker(String workerId, String host, int port) {
        this.workerId = workerId;
        this.host = host;
        this.port = port;
        this.status = WorkerStatus.REGISTERED;
        this.registeredAt = LocalDateTime.now();
        this.lastHeartbeat = LocalDateTime.now();
        this.activeTaskIds = new ArrayList<>();
        this.completedTasks = 0;
        this.failedTasks = 0;
        this.totalExecutionTimeMs = 0;
    }
    
    // Getters y Setters básicos
    public String getWorkerId() { return workerId; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    
    public WorkerStatus getStatus() { return status; }
    public void setStatus(WorkerStatus status) { this.status = status; }
    
    public int getCpuCores() { return cpuCores; }
    public void setCpuCores(int cpuCores) { this.cpuCores = cpuCores; }
    
    public long getMemoryMB() { return memoryMB; }
    public void setMemoryMB(long memoryMB) { this.memoryMB = memoryMB; }
    
    public long getDiskSpaceGB() { return diskSpaceGB; }
    public void setDiskSpaceGB(long diskSpaceGB) { this.diskSpaceGB = diskSpaceGB; }
    
    public int getComputePower() { return computePower; }
    public void setComputePower(int computePower) { this.computePower = computePower; }
    
    public int getCurrentLoad() { return currentLoad; }
    public void setCurrentLoad(int currentLoad) { this.currentLoad = currentLoad; }
    
    public int getMaxConcurrentTasks() { return maxConcurrentTasks; }
    public void setMaxConcurrentTasks(int maxConcurrentTasks) { this.maxConcurrentTasks = maxConcurrentTasks; }
    
    public List<String> getActiveTaskIds() { return activeTaskIds; }
    
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    
    public LocalDateTime getLastTaskUpdate() { return lastTaskUpdate; }
    public void setLastTaskUpdate(LocalDateTime lastTaskUpdate) { this.lastTaskUpdate = lastTaskUpdate; }
    
    public int getCompletedTasks() { return completedTasks; }
    public int getFailedTasks() { return failedTasks; }
    public long getTotalExecutionTimeMs() { return totalExecutionTimeMs; }
    
    /**
     * Verifica si el worker está disponible para recibir tareas
     */
    public boolean isAvailable() {
        return status == WorkerStatus.READY && currentLoad < maxConcurrentTasks;
    }
    
    /**
     * Verifica si el worker está sobrecargado
     */
    public boolean isOverloaded() {
        return currentLoad >= maxConcurrentTasks;
    }
    
    /**
     * Verifica si el worker está activo (respondiendo heartbeats)
     */
    public boolean isActive() {
        if (lastHeartbeat == null) return false;
        long secondsSinceHeartbeat = java.time.Duration.between(lastHeartbeat, LocalDateTime.now()).getSeconds();
        return secondsSinceHeartbeat < 30; // Considerar activo si respondió en los últimos 30 segundos
    }
    
    /**
     * Calcula la disponibilidad del worker (0.0 a 1.0)
     */
    public double getAvailability() {
        if (maxConcurrentTasks == 0) return 0.0;
        return Math.max(0.0, 1.0 - ((double) currentLoad / maxConcurrentTasks));
    }
    
    /**
     * Calcula el score de prioridad para asignación de tareas
     * Mayor score = mejor candidato para recibir tareas
     */
    public double getPriorityScore() {
        double availability = getAvailability();
        double health = isActive() ? 1.0 : 0.0;
        double performance = computePower / 100.0;
        
        // Fórmula: 40% disponibilidad + 30% salud + 30% rendimiento
        return (availability * 0.4) + (health * 0.3) + (performance * 0.3);
    }
    
    /**
     * Asigna una tarea al worker
     */
    public boolean assignTask(String taskId) {
        if (!isAvailable()) return false;
        
        activeTaskIds.add(taskId);
        currentLoad++;
        lastTaskUpdate = LocalDateTime.now();
        return true;
    }
    
    /**
     * Libera una tarea del worker
     */
    public boolean releaseTask(String taskId) {
        if (activeTaskIds.remove(taskId)) {
            currentLoad--;
            lastTaskUpdate = LocalDateTime.now();
            return true;
        }
        return false;
    }
    
    /**
     * Registra una tarea completada
     */
    public void recordTaskCompletion(long executionTimeMs) {
        completedTasks++;
        totalExecutionTimeMs += executionTimeMs;
        lastTaskUpdate = LocalDateTime.now();
    }
    
    /**
     * Registra una tarea fallida
     */
    public void recordTaskFailure() {
        failedTasks++;
        lastTaskUpdate = LocalDateTime.now();
    }
    
    /**
     * Actualiza el heartbeat del worker
     */
    public void updateHeartbeat() {
        this.lastHeartbeat = LocalDateTime.now();
    }
}
