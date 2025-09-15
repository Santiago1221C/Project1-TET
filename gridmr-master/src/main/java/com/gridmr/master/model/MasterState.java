package com.gridmr.master.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * MasterState - Representa el estado persistente del Master
 * 
 * Contiene toda la información crítica que debe ser persistida
 * para permitir la recuperación del sistema.
 */
public class MasterState {
    
    private String version;
    private LocalDateTime timestamp;
    
    // Workers
    private List<Worker> workers;
    private int activeWorkersCount;
    private int totalWorkersCount;
    
    // Nodos
    private List<NodeInfo> nodes;
    private int activeNodesCount;
    private int totalNodesCount;
    
    // Jobs
    private List<Job> jobs;
    private int activeJobsCount;
    private int totalJobsCount;
    
    // Tareas
    private List<Task> tasks;
    private int activeTasksCount;
    private int totalTasksCount;
    
    // Configuración del sistema
    private Map<String, Object> systemConfig;
    
    public MasterState() {
        this.workers = new ArrayList<>();
        this.nodes = new ArrayList<>();
        this.jobs = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.systemConfig = new java.util.HashMap<>();
        this.timestamp = LocalDateTime.now();
        this.version = "1.0";
    }
    
    // Getters y Setters
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public List<Worker> getWorkers() { return workers; }
    public void setWorkers(List<Worker> workers) { this.workers = workers; }
    
    public int getActiveWorkersCount() { return activeWorkersCount; }
    public void setActiveWorkersCount(int activeWorkersCount) { this.activeWorkersCount = activeWorkersCount; }
    
    public int getTotalWorkersCount() { return totalWorkersCount; }
    public void setTotalWorkersCount(int totalWorkersCount) { this.totalWorkersCount = totalWorkersCount; }
    
    public List<NodeInfo> getNodes() { return nodes; }
    public void setNodes(List<NodeInfo> nodes) { this.nodes = nodes; }
    
    public int getActiveNodesCount() { return activeNodesCount; }
    public void setActiveNodesCount(int activeNodesCount) { this.activeNodesCount = activeNodesCount; }
    
    public int getTotalNodesCount() { return totalNodesCount; }
    public void setTotalNodesCount(int totalNodesCount) { this.totalNodesCount = totalNodesCount; }
    
    public List<Job> getJobs() { return jobs; }
    public void setJobs(List<Job> jobs) { this.jobs = jobs; }
    
    public int getActiveJobsCount() { return activeJobsCount; }
    public void setActiveJobsCount(int activeJobsCount) { this.activeJobsCount = activeJobsCount; }
    
    public int getTotalJobsCount() { return totalJobsCount; }
    public void setTotalJobsCount(int totalJobsCount) { this.totalJobsCount = totalJobsCount; }
    
    public List<Task> getTasks() { return tasks; }
    public void setTasks(List<Task> tasks) { this.tasks = tasks; }
    
    public int getActiveTasksCount() { return activeTasksCount; }
    public void setActiveTasksCount(int activeTasksCount) { this.activeTasksCount = activeTasksCount; }
    
    public int getTotalTasksCount() { return totalTasksCount; }
    public void setTotalTasksCount(int totalTasksCount) { this.totalTasksCount = totalTasksCount; }
    
    public Map<String, Object> getSystemConfig() { return systemConfig; }
    public void setSystemConfig(Map<String, Object> systemConfig) { this.systemConfig = systemConfig; }
    
    @Override
    public String toString() {
        return "MasterState{" +
                "version='" + version + '\'' +
                ", timestamp=" + timestamp +
                ", workers=" + workers.size() +
                ", nodes=" + nodes.size() +
                ", jobs=" + jobs.size() +
                ", tasks=" + tasks.size() +
                '}';
    }
}
