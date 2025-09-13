package com.gridmr.master.model;

// Estados posibles de un worker
public enum WorkerStatus {
    REGISTERED,  // Worker registrado pero no listo
    READY,       // Worker listo para recibir tareas
    BUSY,        // Worker ocupado ejecutando tareas
    OFFLINE,     // Worker no disponible
    FAILED       // Worker en estado de error
}