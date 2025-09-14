package com.gridmr.master.config;

import com.gridmr.master.components.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GridMRConfiguration {

    @Bean
    public ResourceManager resourceManager() {
        ResourceManager rm = new ResourceManager();
        rm.start();
        return rm;
    }

    @Bean
    public ChunkManager chunkManager() {
        ChunkManager cm = new ChunkManager();
        cm.start();
        return cm;
    }

    @Bean
    public Scheduler scheduler(ResourceManager resourceManager) {
        Scheduler scheduler = new Scheduler(resourceManager);
        scheduler.start();
        return scheduler;
    }

    @Bean
    public JobManager jobManager(Scheduler scheduler) {
        JobManager jm = new JobManager(scheduler);
        jm.start();
        return jm;
    }

    @Bean
    public DomainAuthentication domainAuthentication() {
        DomainAuthentication da = new DomainAuthentication();
        da.start();
        return da;
    }
}
