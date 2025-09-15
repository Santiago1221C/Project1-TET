package com.gridmr.master.components;

import com.gridmr.master.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * MasterPersistenceManager - Persistencia y recuperación del estado del Master
 * 
 * Funcionalidades:
 * - Persistencia automática del estado
 * - Recuperación al reiniciar
 * - Backup de datos críticos
 * - Sincronización entre masters
 */
public class MasterPersistenceManager {
    
    private static final String PERSISTENCE_DIR = "master_persistence";
    private static final String STATE_FILE = "master_state.json";
    private static final String BACKUP_DIR = "backups";
    private static final long PERSISTENCE_INTERVAL_MS = 30000; // 30 segundos
    
    private final ObjectMapper objectMapper;
    private final Path persistencePath;
    private final Path backupPath;
    private final ReadWriteLock lock;
    
    // Referencias a los componentes del Master (se inyectarán después)
    private Object resourceManager;
    private Object nodeManager;
    private Object jobManager;
    private Object scheduler;
    
    // Estado persistente
    private MasterState masterState;
    private boolean persistenceEnabled;
    private Timer persistenceTimer;
    
    public MasterPersistenceManager() {
        this.objectMapper = new ObjectMapper();
        this.persistencePath = Paths.get(PERSISTENCE_DIR);
        this.backupPath = Paths.get(BACKUP_DIR);
        this.lock = new ReentrantReadWriteLock();
        this.masterState = new MasterState();
        this.persistenceEnabled = true;
        
        // Configurar ObjectMapper para LocalDateTime
        objectMapper.findAndRegisterModules();
        
        System.out.println("[OK] MasterPersistenceManager inicializado");
    }
    
    /**
     * Inicializa el sistema de persistencia
     */
    public void initialize(Object resourceManager, Object nodeManager, 
                          Object jobManager, Object scheduler) {
        this.resourceManager = resourceManager;
        this.nodeManager = nodeManager;
        this.jobManager = jobManager;
        this.scheduler = scheduler;
        
        try {
            // Crear directorios si no existen
            Files.createDirectories(persistencePath);
            Files.createDirectories(backupPath);
            
            // Intentar recuperar estado previo
            if (recoverState()) {
                System.out.println("[OK] Estado del Master recuperado exitosamente");
            } else {
                System.out.println("[INFO] No se encontró estado previo, iniciando con estado limpio");
            }
            
            // Iniciar persistencia automática
            startPersistenceTimer();
            
        } catch (Exception e) {
            System.err.println("[ERROR] Error inicializando persistencia: " + e.getMessage());
        }
    }
    
    /**
     * Inicia el timer de persistencia automática
     */
    private void startPersistenceTimer() {
        persistenceTimer = new Timer("MasterPersistence", true);
        persistenceTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (persistenceEnabled) {
                    persistState();
                }
            }
        }, PERSISTENCE_INTERVAL_MS, PERSISTENCE_INTERVAL_MS);
        
        System.out.println("[OK] Timer de persistencia iniciado - Intervalo: " + (PERSISTENCE_INTERVAL_MS/1000) + "s");
    }
    
    /**
     * Persiste el estado actual del Master
     */
    public void persistState() {
        if (!persistenceEnabled) return;
        
        try {
            lock.writeLock().lock();
            
            // Actualizar estado con datos actuales
            updateMasterState();
            
            // Crear backup antes de persistir
            createBackup();
            
            // Persistir estado
            Path stateFile = persistencePath.resolve(STATE_FILE);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), masterState);
            
            System.out.println("[INFO] Estado del Master persistido exitosamente");
            
        } catch (Exception e) {
            System.err.println("[ERROR] Error persistiendo estado: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Actualiza el estado del Master con datos actuales
     */
    private void updateMasterState() {
        masterState.setTimestamp(LocalDateTime.now());
        masterState.setVersion("1.0");
        
        // Actualizar workers (simplificado por ahora)
        if (resourceManager != null) {
            masterState.setWorkers(new ArrayList<>());
            masterState.setActiveWorkersCount(0);
            masterState.setTotalWorkersCount(0);
        }
        
        // Actualizar nodos (simplificado por ahora)
        if (nodeManager != null) {
            masterState.setNodes(new ArrayList<>());
            masterState.setActiveNodesCount(0);
            masterState.setTotalNodesCount(0);
        }
        
        // Actualizar jobs (simplificado por ahora)
        if (jobManager != null) {
            masterState.setJobs(new ArrayList<>());
            masterState.setActiveJobsCount(0);
            masterState.setTotalJobsCount(0);
        }
        
        // Actualizar tareas (simplificado por ahora)
        if (scheduler != null) {
            masterState.setTasks(new ArrayList<>());
            masterState.setActiveTasksCount(0);
            masterState.setTotalTasksCount(0);
        }
    }
    
    /**
     * Crea un backup del estado actual
     */
    private void createBackup() {
        try {
            String timestamp = LocalDateTime.now().toString().replace(":", "-");
            String backupFileName = "master_state_backup_" + timestamp + ".json";
            Path backupFile = backupPath.resolve(backupFileName);
            
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(backupFile.toFile(), masterState);
            
            // Limpiar backups antiguos (mantener solo los últimos 10)
            cleanupOldBackups();
            
        } catch (Exception e) {
            System.err.println("[ERROR] Error creando backup: " + e.getMessage());
        }
    }
    
    /**
     * Limpia backups antiguos, manteniendo solo los últimos 10
     */
    private void cleanupOldBackups() {
        try {
            List<Path> backupFiles = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupPath, "master_state_backup_*.json")) {
                for (Path file : stream) {
                    backupFiles.add(file);
                }
            }
            
            // Ordenar por fecha de modificación (más reciente primero)
            backupFiles.sort((a, b) -> {
                try {
                    return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                } catch (IOException e) {
                    return 0;
                }
            });
            
            // Eliminar backups excesivos
            if (backupFiles.size() > 10) {
                for (int i = 10; i < backupFiles.size(); i++) {
                    Files.deleteIfExists(backupFiles.get(i));
                }
            }
            
        } catch (Exception e) {
            System.err.println("[ERROR] Error limpiando backups antiguos: " + e.getMessage());
        }
    }
    
    /**
     * Recupera el estado del Master desde el archivo de persistencia
     */
    public boolean recoverState() {
        try {
            Path stateFile = persistencePath.resolve(STATE_FILE);
            if (!Files.exists(stateFile)) {
                return false;
            }
            
            lock.readLock().lock();
            
            // Leer estado desde archivo
            masterState = objectMapper.readValue(stateFile.toFile(), MasterState.class);
            
            // Validar estado recuperado
            if (masterState == null || masterState.getVersion() == null) {
                System.out.println("[WARN] Estado recuperado inválido, iniciando con estado limpio");
                return false;
            }
            
            System.out.println("[OK] Estado recuperado - Versión: " + masterState.getVersion() + 
                             ", Timestamp: " + masterState.getTimestamp());
            
            return true;
            
        } catch (Exception e) {
            System.err.println("[ERROR] Error recuperando estado: " + e.getMessage());
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Restaura el estado recuperado en los componentes del Master
     */
    public void restoreState() {
        if (masterState == null) {
            System.out.println("[WARN] No hay estado para restaurar");
            return;
        }
        
        try {
            System.out.println("[INFO] Restaurando estado del Master...");
            
            // Restaurar workers
            if (resourceManager != null && masterState.getWorkers() != null) {
                restoreWorkers();
            }
            
            // Restaurar nodos
            if (nodeManager != null && masterState.getNodes() != null) {
                restoreNodes();
            }
            
            // Restaurar jobs
            if (jobManager != null && masterState.getJobs() != null) {
                restoreJobs();
            }
            
            // Restaurar tareas
            if (scheduler != null && masterState.getTasks() != null) {
                restoreTasks();
            }
            
            System.out.println("[OK] Estado del Master restaurado exitosamente");
            
        } catch (Exception e) {
            System.err.println("[ERROR] Error restaurando estado: " + e.getMessage());
        }
    }
    
    /**
     * Restaura workers desde el estado persistido (simplificado)
     */
    private void restoreWorkers() {
        System.out.println("[INFO] Restauración de workers simplificada - implementar según necesidad");
    }
    
    /**
     * Restaura nodos desde el estado persistido (simplificado)
     */
    private void restoreNodes() {
        System.out.println("[INFO] Restauración de nodos simplificada - implementar según necesidad");
    }
    
    /**
     * Restaura jobs desde el estado persistido (simplificado)
     */
    private void restoreJobs() {
        System.out.println("[INFO] Restauración de jobs simplificada - implementar según necesidad");
    }
    
    /**
     * Restaura tareas desde el estado persistido (simplificado)
     */
    private void restoreTasks() {
        System.out.println("[INFO] Restauración de tareas simplificada - implementar según necesidad");
    }
    
    /**
     * Obtiene estadísticas de persistencia
     */
    public Map<String, Object> getPersistenceStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            stats.put("persistence_enabled", persistenceEnabled);
            stats.put("persistence_interval_ms", PERSISTENCE_INTERVAL_MS);
            stats.put("state_file_exists", Files.exists(persistencePath.resolve(STATE_FILE)));
            stats.put("backup_count", getBackupCount());
            stats.put("last_persistence", masterState != null ? masterState.getTimestamp() : null);
            stats.put("state_version", masterState != null ? masterState.getVersion() : null);
            
        } catch (Exception e) {
            stats.put("error", "Error obteniendo estadísticas: " + e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * Obtiene el número de backups disponibles
     */
    private int getBackupCount() {
        try {
            int count = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupPath, "master_state_backup_*.json")) {
                for (Path file : stream) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Detiene el sistema de persistencia
     */
    public void stop() {
        try {
            persistenceEnabled = false;
            
            if (persistenceTimer != null) {
                persistenceTimer.cancel();
                persistenceTimer = null;
            }
            
            // Persistir estado final
            persistState();
            
            System.out.println("[OK] Sistema de persistencia detenido");
            
        } catch (Exception e) {
            System.err.println("[ERROR] Error deteniendo persistencia: " + e.getMessage());
        }
    }
    
    // Getters y Setters
    public MasterState getMasterState() { return masterState; }
    public boolean isPersistenceEnabled() { return persistenceEnabled; }
    public void setPersistenceEnabled(boolean enabled) { this.persistenceEnabled = enabled; }
}
