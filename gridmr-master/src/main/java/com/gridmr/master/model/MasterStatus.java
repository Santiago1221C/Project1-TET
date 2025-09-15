package com.gridmr.master.model;

/**
 * MasterStatus - Estados de un master en el sistema
 */
public enum MasterStatus {
    ACTIVE,      // Master activo y funcionando
    OFFLINE,     // Master caído o inaccesible
    MAINTENANCE, // Master en mantenimiento
    SYNCHRONIZING // Master sincronizando estado
}
