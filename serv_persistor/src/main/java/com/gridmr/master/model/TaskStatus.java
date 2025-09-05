package com.gridmr.master.model;

// Estados posibles de una tarea individual
public enum TaskStatus {
    PENDING,    // Tarea creada, esperando ser asignada
    ASSIGNED,   // Tarea asignada a un worker
    RUNNING,    // Tarea ejecutándose en el worker
    COMPLETED,  // Tarea completada exitosamente
    FAILED,     // Tarea falló durante la ejecución
    CANCELLED   // Tarea cancelada
}
