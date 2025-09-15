package com.gridmr.master.model;

import java.time.LocalDateTime;

/**
 * NodeInfo - Representa un nodo en el sistema GridMR
 * 
 * Un nodo puede contener múltiples workers y proporciona
 * recursos de cómputo para el sistema distribuido.
 */
public class NodeInfo {
    
    private String nodeId;
    private String host;
    private int port;
    private String nodeType; // "MASTER", "WORKER", "STORAGE", "HYBRID"
    private NodeStatus status;
    
    // Capacidad del nodo
    private int maxWorkers;
    private int currentWorkers;
    private int cpuCores;
    private long memoryGB;
    private long diskSpaceGB;
    private int computePower; // Valor simulado de capacidad (1-100)
    
    // Metadatos
    private LocalDateTime registeredAt;
    private LocalDateTime lastHeartbeat;
    private LocalDateTime lastUpdate;
    
    // Estadísticas
    private int totalWorkersAssigned;
    private int totalTasksCompleted;
    private int totalTasksFailed;
    private long totalExecutionTimeMs;
    
    public NodeInfo(String nodeId, String host, int port, int maxWorkers, String nodeType) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
        this.maxWorkers = maxWorkers;
        this.nodeType = nodeType;
        this.status = NodeStatus.REGISTERED;
        this.registeredAt = LocalDateTime.now();
        this.lastHeartbeat = LocalDateTime.now();
        this.lastUpdate = LocalDateTime.now();
        this.currentWorkers = 0;
        this.totalWorkersAssigned = 0;
        this.totalTasksCompleted = 0;
        this.totalTasksFailed = 0;
        this.totalExecutionTimeMs = 0;
    }
    
    // Getters y Setters básicos
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    
    public NodeStatus getStatus() { return status; }
    public void setStatus(NodeStatus status) { this.status = status; }
    
    public int getMaxWorkers() { return maxWorkers; }
    public void setMaxWorkers(int maxWorkers) { this.maxWorkers = maxWorkers; }
    
    public int getCurrentWorkers() { return currentWorkers; }
    public void setCurrentWorkers(int currentWorkers) { this.currentWorkers = currentWorkers; }
    
    public int getCpuCores() { return cpuCores; }
    public void setCpuCores(int cpuCores) { this.cpuCores = cpuCores; }
    
    public long getMemoryGB() { return memoryGB; }
    public void setMemoryGB(long memoryGB) { this.memoryGB = memoryGB; }
    
    public long getDiskSpaceGB() { return diskSpaceGB; }
    public void setDiskSpaceGB(long diskSpaceGB) { this.diskSpaceGB = diskSpaceGB; }
    
    public int getComputePower() { return computePower; }
    public void setComputePower(int computePower) { this.computePower = computePower; }
    
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
    
    public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    
    public LocalDateTime getLastUpdate() { return lastUpdate; }
    public void setLastUpdate(LocalDateTime lastUpdate) { this.lastUpdate = lastUpdate; }
    
    public int getTotalWorkersAssigned() { return totalWorkersAssigned; }
    public void setTotalWorkersAssigned(int totalWorkersAssigned) { this.totalWorkersAssigned = totalWorkersAssigned; }
    
    public int getTotalTasksCompleted() { return totalTasksCompleted; }
    public void setTotalTasksCompleted(int totalTasksCompleted) { this.totalTasksCompleted = totalTasksCompleted; }
    
    public int getTotalTasksFailed() { return totalTasksFailed; }
    public void setTotalTasksFailed(int totalTasksFailed) { this.totalTasksFailed = totalTasksFailed; }
    
    public long getTotalExecutionTimeMs() { return totalExecutionTimeMs; }
    public void setTotalExecutionTimeMs(long totalExecutionTimeMs) { this.totalExecutionTimeMs = totalExecutionTimeMs; }
    
    // MÉTODOS DE FUNCIONALIDAD
    
    /**
     * Verifica si el nodo está activo (respondiendo heartbeats)
     * @param timeoutSeconds Tiempo límite en segundos para considerar el nodo activo
     */
    public boolean isActive(int timeoutSeconds) {
        if (lastHeartbeat == null) return false;
        long secondsSinceHeartbeat = java.time.Duration.between(lastHeartbeat, LocalDateTime.now()).getSeconds();
        return secondsSinceHeartbeat < timeoutSeconds;
    }
    
    /**
     * Verifica si el nodo está activo (valor por defecto)
     */
    public boolean isActive() {
        return isActive(30); // Valor por defecto
    }
    
    /**
     * Verifica si el nodo tiene capacidad para más workers
     */
    public boolean hasCapacity() {
        return currentWorkers < maxWorkers;
    }
    
    /**
     * Verifica si el nodo está sobrecargado
     */
    public boolean isOverloaded() {
        return currentWorkers >= maxWorkers;
    }
    
    /**
     * Calcula la disponibilidad del nodo (0.0 = sin capacidad, 1.0 = completamente disponible)
     */
    public double getAvailability() {
        if (maxWorkers == 0) return 0.0;
        return Math.max(0.0, 1.0 - ((double) currentWorkers / maxWorkers));
    }
    
    /**
     * Calcula el score de prioridad para asignación de workers
     */
    public double getPriorityScore() {
        double availability = getAvailability();
        double health = isActive() ? 1.0 : 0.0;
        double performance = computePower / 100.0;
        
        // Fórmula: 40% disponibilidad + 30% salud + 30% rendimiento
        return (availability * 0.4) + (health * 0.3) + (performance * 0.3);
    }
    
    /**
     * Asigna un worker al nodo
     */
    public boolean assignWorker() {
        if (!hasCapacity()) return false;
        
        currentWorkers++;
        totalWorkersAssigned++;
        lastUpdate = LocalDateTime.now();
        return true;
    }
    
    /**
     * Libera un worker del nodo
     */
    public boolean releaseWorker() {
        if (currentWorkers <= 0) return false;
        
        currentWorkers--;
        lastUpdate = LocalDateTime.now();
        return true;
    }
    
    /**
     * Actualiza el heartbeat del nodo
     */
    public void updateHeartbeat() {
        this.lastHeartbeat = LocalDateTime.now();
        this.lastUpdate = LocalDateTime.now();
    }
    
    /**
     * Registra una tarea completada
     */
    public void recordTaskCompletion(long executionTimeMs) {
        totalTasksCompleted++;
        totalExecutionTimeMs += executionTimeMs;
        lastUpdate = LocalDateTime.now();
    }
    
    /**
     * Registra una tarea fallida
     */
    public void recordTaskFailure() {
        totalTasksFailed++;
        lastUpdate = LocalDateTime.now();
    }
    
    /**
     * Obtiene la dirección completa del nodo
     */
    public String getAddress() {
        return host + ":" + port;
    }
    
    /**
     * Obtiene información de estado del nodo
     */
    public String getStatusInfo() {
        return String.format("Nodo %s [%s] - Estado: %s, Workers: %d/%d, Disponibilidad: %.2f, Salud: %s",
            nodeId, getAddress(), status, currentWorkers, maxWorkers, getAvailability(), 
            isActive() ? "✅" : "❌");
    }
    
    @Override
    public String toString() {
        return getStatusInfo();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        NodeInfo nodeInfo = (NodeInfo) obj;
        return nodeId != null ? nodeId.equals(nodeInfo.nodeId) : nodeInfo.nodeId == null;
    }
    
    @Override
    public int hashCode() {
        return nodeId != null ? nodeId.hashCode() : 0;
    }
}
