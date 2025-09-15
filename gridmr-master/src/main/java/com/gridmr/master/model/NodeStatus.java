package com.gridmr.master.model;

/**
 * NodeStatus - Estados posibles de un nodo en el sistema GridMR
 */
public enum NodeStatus {
    
    /**
     * Nodo recién registrado, inicializando
     */
    REGISTERED("Registrado"),
    
    /**
     * Nodo activo y funcionando correctamente
     */
    ACTIVE("Activo"),
    
    /**
     * Nodo ocupado procesando tareas
     */
    BUSY("Ocupado"),
    
    /**
     * Nodo inactivo (no responde a heartbeats)
     */
    OFFLINE("Desconectado"),
    
    /**
     * Nodo en mantenimiento
     */
    MAINTENANCE("Mantenimiento"),
    
    /**
     * Nodo con errores críticos
     */
    ERROR("Error"),
    
    /**
     * Nodo dado de baja del sistema
     */
    UNREGISTERED("Dado de baja");
    
    private final String description;
    
    NodeStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return description;
    }
    
    /**
     * Verifica si el nodo está disponible para recibir workers
     */
    public boolean isAvailable() {
        return this == ACTIVE || this == BUSY;
    }
    
    /**
     * Verifica si el nodo está funcionando correctamente
     */
    public boolean isHealthy() {
        return this == ACTIVE || this == BUSY;
    }
    
    /**
     * Verifica si el nodo está inactivo
     */
    public boolean isInactive() {
        return this == OFFLINE || this == ERROR;
    }
    
    /**
     * Verifica si el nodo está en mantenimiento
     */
    public boolean isInMaintenance() {
        return this == MAINTENANCE;
    }
}
