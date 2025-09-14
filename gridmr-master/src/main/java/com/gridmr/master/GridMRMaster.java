package com.gridmr.master;

import com.gridmr.master.components.ResourceManager;
import com.gridmr.master.components.Scheduler;
import com.gridmr.master.components.JobManager;
import com.gridmr.master.components.ChunkManager;
import com.gridmr.master.components.DomainAuthentication;

public class GridMRMaster {
    
    private ResourceManager resourceManager;
    private Scheduler scheduler;
    private JobManager jobManager;
    private ChunkManager chunkManager;
    private DomainAuthentication domainAuthentication;
    
    public GridMRMaster() {
        // Inicialización de los 5 componentes esenciales
        this.resourceManager = new ResourceManager();
        this.chunkManager = new ChunkManager();
        this.scheduler = new Scheduler(resourceManager); // Pasar ResourceManager al Scheduler
        this.jobManager = new JobManager(scheduler);
        this.domainAuthentication = new DomainAuthentication();
    }
    
    public void start() {
        System.out.println("GridMR Master iniciando...");
        System.out.println("Componentes inicializados:");
        System.out.println("- ResourceManager: " + (resourceManager != null ? "OK" : "ERROR"));
        System.out.println("- Scheduler: " + (scheduler != null ? "OK" : "ERROR"));
        System.out.println("- JobManager: " + (jobManager != null ? "OK" : "ERROR"));
        System.out.println("- ChunkManager: " + (chunkManager != null ? "OK" : "ERROR"));
        System.out.println("- DomainAuthentication: " + (domainAuthentication != null ? "OK" : "ERROR"));
        System.out.println("GridMR Master listo para recibir trabajos.");
    }
    
    public void stop() {
        System.out.println("GridMR Master deteniendo...");
        System.out.println("Limpiando recursos...");
        System.out.println("GridMR Master detenido correctamente.");
    }
    
    public static void main(String[] args) {
        GridMRMaster master = new GridMRMaster();
        master.start();
    }
}