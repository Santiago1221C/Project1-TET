package com.gridmr.master.model;

import java.time.LocalDateTime;

/**
 * MasterInfo - Información de un master en el sistema
 */
public class MasterInfo {
    
    private String masterId;
    private String host;
    private int port;
    private MasterRole role;
    private MasterStatus status;
    private LocalDateTime lastHeartbeat;
    private LocalDateTime registeredAt;
    private int priority;
    
    public MasterInfo(String masterId, String host, int port, MasterRole role) {
        this.masterId = masterId;
        this.host = host;
        this.port = port;
        this.role = role;
        this.status = MasterStatus.ACTIVE;
        this.lastHeartbeat = LocalDateTime.now();
        this.registeredAt = LocalDateTime.now();
        this.priority = 0;
    }
    
    public void updateHeartbeat() {
        this.lastHeartbeat = LocalDateTime.now();
    }
    
    public boolean isActive(int timeoutSeconds) {
        if (lastHeartbeat == null) return false;
        long secondsSinceHeartbeat = java.time.Duration.between(lastHeartbeat, LocalDateTime.now()).getSeconds();
        return secondsSinceHeartbeat < timeoutSeconds;
    }
    
    // Getters y Setters
    public String getMasterId() { return masterId; }
    public void setMasterId(String masterId) { this.masterId = masterId; }
    
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public MasterRole getRole() { return role; }
    public void setRole(MasterRole role) { this.role = role; }
    
    public MasterStatus getStatus() { return status; }
    public void setStatus(MasterStatus status) { this.status = status; }
    
    public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
    
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    
    @Override
    public String toString() {
        return "MasterInfo{" +
                "masterId='" + masterId + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", role=" + role +
                ", status=" + status +
                ", lastHeartbeat=" + lastHeartbeat +
                '}';
    }
}
