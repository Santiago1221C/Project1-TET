package com.gridmr.master.components;

/**
 * JobManager - Coordina trabajos MapReduce
 * Responsable de la coordinación de fases Map y Reduce
 */
public class JobManager {
    
    public JobManager() {
        System.out.println("JobManager inicializado");
    }
    
    public void start() {
        System.out.println("JobManager iniciado");
    }
    
    public void stop() {
        System.out.println("JobManager detenido");
    }
}

