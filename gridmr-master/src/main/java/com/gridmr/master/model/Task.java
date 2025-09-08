package com.gridmr.master.model;

import java.time.LocalDateTime;

// Representa una tarea individual Map o Reduce, cada tarea se ejecuta en un worker específico
public class Task {
    
    private String taskId;
    private String jobId;
    private TaskType type;
    private TaskStatus status;
    private String workerId;
    
    // Datos de entrada y salida
    private String inputData;
    private String outputData;
    private String errorMessage;
    
    // Metadatos de ejecución
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private long executionTimeMs;
    
    // Configuración de la tarea
    private String functionCode;
    private int priority;
    
    public Task(String taskId, String jobId, TaskType type) {
        this.taskId = taskId;
        this.jobId = jobId;
        this.type = type;
        this.status = TaskStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.priority = 1; // Prioridad por defecto
    }
    
    // Getters y Setters básicos
    public String getTaskId() {
        return taskId;
    }
    public String getJobId() {
        return jobId;
    }
    public TaskType getType() {
        return type;
    }
    
    public TaskStatus getStatus() {
        return status;
    }
    public void setStatus(TaskStatus status) {
        this.status = status;
    }
    
    public String getWorkerId() {
        return workerId;
    }
    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }
    
    public String getInputData() {
        return inputData;
    }
    public void setInputData(String inputData) {
        this.inputData = inputData;
    }
    
    public String getOutputData() {
        return outputData;
    }
    public void setOutputData(String outputData) {
        this.outputData = outputData;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public LocalDateTime getStartedAt() {
        return startedAt;
    }
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }
    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }
    
    public String getFunctionCode() {
        return functionCode;
    }
    public void setFunctionCode(String functionCode) {
        this.functionCode = functionCode;
    }
    
    public int getPriority() {
        return priority;
    }
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    // Verifica si la tarea está completada
    public boolean isCompleted() {
        return status == TaskStatus.COMPLETED;
    }
    
    // Si está en progreso
    public boolean isInProgress() {
        return status == TaskStatus.RUNNING;
    }
    
    // Si está asignada a un worker
    public boolean isAssigned() {
        return workerId != null && !workerId.isEmpty();
    }
    
    // Si es de tipo Map
    public boolean isMapTask() {
        return type == TaskType.MAP;
    }
    
    // Si es de tipo Reduce
    public boolean isReduceTask() {
        return type == TaskType.REDUCE;
    }
    
    // Marca la tarea como iniciada
    public void start() {
        this.status = TaskStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }
    
    // Marca la tarea como completada
    public void complete(String outputData) {
        this.status = TaskStatus.COMPLETED;
        this.outputData = outputData;
        this.completedAt = LocalDateTime.now();
        if (this.startedAt != null) {
            this.executionTimeMs = java.time.Duration.between(this.startedAt, this.completedAt).toMillis();
        }
    }
    
    // Marca la tarea como fallida
    public void fail(String errorMessage) {
        this.status = TaskStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }
}