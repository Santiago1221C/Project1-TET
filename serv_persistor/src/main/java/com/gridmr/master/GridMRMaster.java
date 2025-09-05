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
        // Inicializar solo los 5 componentes esenciales
        this.resourceManager = new ResourceManager();
        this.scheduler = new Scheduler();
        this.jobManager = new JobManager();
        this.chunkManager = new ChunkManager();
        this.domainAuthentication = new DomainAuthentication();
    }
    
    public void start() {
        System.out.println("GridMR Master iniciando...");
        // Iniciar servicios gRPC y componentes
    }
    
    public void stop() {
        System.out.println("GridMR Master deteniendo...");
        // Limpiar recursos
    }
    
    public static void main(String[] args) {
        GridMRMaster master = new GridMRMaster();
        master.start();
    }
}
