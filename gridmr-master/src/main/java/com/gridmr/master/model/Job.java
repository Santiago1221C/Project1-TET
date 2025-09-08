package com.gridmr.master.model;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;

/**
 * Representa un trabajo MapReduce completo
 * Un Job contiene múltiples tareas Map y Reduce
 */
public class Job {
    
    private String jobId;
    private String clientId;
    private JobStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    
    // Datos de entrada
    private List<String> inputFiles;
    private String outputDirectory;
    
    // Configuración del trabajo
    private int numMappers;
    private int numReducers;
    private String mapFunction;
    private String reduceFunction;
    
    // Tareas del trabajo
    private List<Task> mapTasks;
    private List<Task> reduceTasks;
    
    // Resultados
    private List<String> intermediateResults;
    private List<String> finalResults;
    
    public Job(String jobId, String clientId) {
        this.jobId = jobId;
        this.clientId = clientId;
        this.status = JobStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.inputFiles = new ArrayList<>();
        this.mapTasks = new ArrayList<>();
        this.reduceTasks = new ArrayList<>();
        this.intermediateResults = new ArrayList<>();
        this.finalResults = new ArrayList<>();
    }
    
    // Getters y Setters básicos
    public String getJobId() { return jobId; }
    public String getClientId() { return clientId; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    
    public List<String> getInputFiles() { return inputFiles; }
    public void addInputFile(String inputFile) { this.inputFiles.add(inputFile); }
    
    public String getOutputDirectory() { return outputDirectory; }
    public void setOutputDirectory(String outputDirectory) { this.outputDirectory = outputDirectory; }
    
    public int getNumMappers() { return numMappers; }
    public void setNumMappers(int numMappers) { this.numMappers = numMappers; }
    
    public int getNumReducers() { return numReducers; }
    public void setNumReducers(int numReducers) { this.numReducers = numReducers; }
    
    public String getMapFunction() { return mapFunction; }
    public void setMapFunction(String mapFunction) { this.mapFunction = mapFunction; }
    
    public String getReduceFunction() { return reduceFunction; }
    public void setReduceFunction(String reduceFunction) { this.reduceFunction = reduceFunction; }
    
    public List<Task> getMapTasks() { return mapTasks; }
    public void addMapTask(Task task) { this.mapTasks.add(task); }
    
    public List<Task> getReduceTasks() { return reduceTasks; }
    public void addReduceTask(Task task) { this.reduceTasks.add(task); }
    
    public List<String> getIntermediateResults() { return intermediateResults; }
    public void addIntermediateResult(String result) { this.intermediateResults.add(result); }
    
    public List<String> getFinalResults() { return finalResults; }
    public void addFinalResult(String result) { this.finalResults.add(result); }
    
    // Verifica si el trabajo está completado
    public boolean isCompleted() {
        return status == JobStatus.COMPLETED;
    }
    
    // Verifica si el trabajo está en progreso
    public boolean isInProgress() {
        return status == JobStatus.MAP_PHASE || status == JobStatus.REDUCE_PHASE;
    }
    
    // Verifica si todas las tareas Map están completadas
    public boolean areMapTasksCompleted() {
        return mapTasks.stream().allMatch(task -> task.getStatus() == TaskStatus.COMPLETED);
    }
    
    // Verifica si todas las tareas Reduce están completadas
    public boolean areReduceTasksCompleted() {
        return reduceTasks.stream().allMatch(task -> task.getStatus() == TaskStatus.COMPLETED);
    }
}