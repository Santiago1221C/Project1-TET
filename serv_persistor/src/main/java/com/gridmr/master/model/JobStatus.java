package com.gridmr.master.model;

/**
 * Estados posibles de un trabajo MapReduce
 */
public enum JobStatus {
    PENDING,        // Trabajo creado, esperando ser procesado
    MAP_PHASE,      // Fase Map en progreso
    MAP_COMPLETED,  // Fase Map completada
    REDUCE_PHASE,   // Fase Reduce en progreso
    COMPLETED,      // Trabajo completado exitosamente
    FAILED,         // Trabajo falló
    CANCELLED       // Trabajo cancelado
}