package com.gridmr.master.components;

/**
 * Scheduler - Programa tareas para los workers
 * Responsable de asignar tareas a los componentes Map, Reduce, TaskExecutor
 */
public class Scheduler {
    
    public Scheduler() {
        System.out.println("Scheduler inicializado");
    }
    
    public void start() {
        System.out.println("Scheduler iniciado");
    }
    
    public void stop() {
        System.out.println("Scheduler detenido");
    }
}

