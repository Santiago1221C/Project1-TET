package com.gridmr.master.model;

import java.time.LocalDateTime;

/**
 * Representa un fragmento (chunk) de datos para procesamiento
 * Los datos se dividen en chunks para distribuir entre workers
 */
public class DataChunk {
    
    private String chunkId;
    private String jobId;
    private String originalFileName;
    private long startOffset;
    private long endOffset;
    private long sizeBytes;
    
    // Ubicación y estado
    private String location; // URI o path donde está almacenado
    private String assignedWorkerId;
    private boolean isProcessed;
    
    // Metadatos
    private LocalDateTime createdAt;
    private LocalDateTime assignedAt;
    private LocalDateTime processedAt;
    
    // Contenido (para chunks pequeños)
    private String content;
    private boolean hasContent;
    
    public DataChunk(String chunkId, String jobId, String originalFileName, 
                    long startOffset, long endOffset) {
        this.chunkId = chunkId;
        this.jobId = jobId;
        this.originalFileName = originalFileName;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.sizeBytes = endOffset - startOffset;
        this.createdAt = LocalDateTime.now();
        this.isProcessed = false;
        this.hasContent = false;
    }
    
    // Getters y Setters básicos
    public String getChunkId() { return chunkId; }
    public String getJobId() { return jobId; }
    public String getOriginalFileName() { return originalFileName; }
    
    public long getStartOffset() { return startOffset; }
    public long getEndOffset() { return endOffset; }
    public long getSizeBytes() { return sizeBytes; }
    
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    
    public String getAssignedWorkerId() { return assignedWorkerId; }
    public void setAssignedWorkerId(String assignedWorkerId) { 
        this.assignedWorkerId = assignedWorkerId; 
        this.assignedAt = LocalDateTime.now();
    }
    
    public boolean isProcessed() { return isProcessed; }
    public void setProcessed(boolean processed) { 
        this.isProcessed = processed; 
        if (processed) {
            this.processedAt = LocalDateTime.now();
        }
    }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    
    public String getContent() { return content; }
    public void setContent(String content) { 
        this.content = content; 
        this.hasContent = true;
    }
    
    public boolean hasContent() { return hasContent; }
    
    // Verifica si el chunk está asignado a un worker
    public boolean isAssigned() {
        return assignedWorkerId != null && !assignedWorkerId.isEmpty();
    }
    
    // Verifica si el chunk está disponible para asignación
    public boolean isAvailable() {
        return !isAssigned() && !isProcessed;
    }
    
    // Obtiene el rango de bytes del chunk
    public String getRangeHeader() {
        return "bytes=" + startOffset + "-" + (endOffset - 1);
    }
    
    // Calcula el tamaño en MB
    public double getSizeMB() {
        return sizeBytes / (1024.0 * 1024.0);
    }
    
    // Obtiene información resumida del chunk
    public String getSummary() {
        return String.format("Chunk %s: %s bytes (%.2f MB) from %s", 
                        chunkId, sizeBytes, getSizeMB(), originalFileName);
    }
}
