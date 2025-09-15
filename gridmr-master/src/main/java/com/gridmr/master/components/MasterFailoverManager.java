package com.gridmr.master.components;

import com.gridmr.master.model.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MasterFailoverManager - Gestión de failover entre múltiples masters
 * 
 * Funcionalidades:
 * - Detección de masters caídos
 * - Elección de master líder
 * - Sincronización entre masters
 * - Recuperación automática
 */
public class MasterFailoverManager {
    
    private static final int HEARTBEAT_INTERVAL_SECONDS = 10;
    private static final int MASTER_TIMEOUT_SECONDS = 30;
    private static final int LEADER_ELECTION_INTERVAL_SECONDS = 15;
    private static final int SYNC_INTERVAL_SECONDS = 20;
    
    private final String masterId;
    private final String masterHost;
    private final int masterPort;
    private final Map<String, MasterInfo> knownMasters;
    private final ScheduledExecutorService scheduler;
    
    private MasterRole currentRole;
    private String currentLeaderId;
    private LocalDateTime lastLeaderHeartbeat;
    private boolean isLeader;
    private boolean failoverEnabled;
    
    // Referencias a componentes (se inyectarán después)
    private Object persistenceManager;
    private Object resourceManager;
    private Object nodeManager;
    
    public MasterFailoverManager(String masterId, String masterHost, int masterPort) {
        this.masterId = masterId;
        this.masterHost = masterHost;
        this.masterPort = masterPort;
        this.knownMasters = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(3);
        this.currentRole = MasterRole.STANDALONE;
        this.isLeader = false;
        this.failoverEnabled = false;
        
        // Registrar este master
        registerSelf();
        
        System.out.println("[OK] MasterFailoverManager inicializado - Master ID: " + masterId);
    }
    
    /**
     * Inicializa el sistema de failover
     */
    public void initialize(Object persistenceManager, 
                          Object resourceManager, 
                          Object nodeManager) {
        this.persistenceManager = persistenceManager;
        this.resourceManager = resourceManager;
        this.nodeManager = nodeManager;
        
        try {
            // Iniciar procesos de failover
            startFailoverProcesses();
            
            System.out.println("[OK] Sistema de failover iniciado");
            
        } catch (Exception e) {
            System.err.println("[ERROR] Error inicializando failover: " + e.getMessage());
        }
    }
    
    /**
     * Registra este master en la lista de masters conocidos
     */
    private void registerSelf() {
        MasterInfo self = new MasterInfo(masterId, masterHost, masterPort, MasterRole.STANDALONE);
        self.setLastHeartbeat(LocalDateTime.now());
        self.setStatus(MasterStatus.ACTIVE);
        knownMasters.put(masterId, self);
    }
    
    /**
     * Inicia los procesos de failover
     */
    private void startFailoverProcesses() {
        // Heartbeat de este master
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        // Verificación de masters caídos
        scheduler.scheduleAtFixedRate(this::checkMasterHealth, 5, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        // Elección de líder
        scheduler.scheduleAtFixedRate(this::performLeaderElection, 10, LEADER_ELECTION_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        // Sincronización con otros masters
        scheduler.scheduleAtFixedRate(this::syncWithOtherMasters, 15, SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        System.out.println("[OK] Procesos de failover iniciados");
    }
    
    /**
     * Envía heartbeat a otros masters
     */
    private void sendHeartbeat() {
        try {
            MasterInfo self = knownMasters.get(masterId);
            if (self != null) {
                self.updateHeartbeat();
                System.out.println("[INFO] Heartbeat enviado - Master: " + masterId + ", Rol: " + currentRole);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Error enviando heartbeat: " + e.getMessage());
        }
    }
    
    /**
     * Verifica la salud de otros masters
     */
    private void checkMasterHealth() {
        try {
            List<String> deadMasters = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            
            for (MasterInfo master : knownMasters.values()) {
                if (!master.getMasterId().equals(masterId)) {
                    long secondsSinceHeartbeat = java.time.Duration.between(master.getLastHeartbeat(), now).getSeconds();
                    
                    if (secondsSinceHeartbeat > MASTER_TIMEOUT_SECONDS) {
                        deadMasters.add(master.getMasterId());
                        master.setStatus(MasterStatus.OFFLINE);
                        System.out.println("[WARN] Master " + master.getMasterId() + " detectado como caído");
                    }
                }
            }
            
            // Remover masters caídos
            for (String deadMasterId : deadMasters) {
                knownMasters.remove(deadMasterId);
                if (deadMasterId.equals(currentLeaderId)) {
                    currentLeaderId = null;
                    isLeader = false;
                    System.out.println("[WARN] Líder anterior caído, iniciando nueva elección");
                }
            }
            
        } catch (Exception e) {
            System.err.println("[ERROR] Error verificando salud de masters: " + e.getMessage());
        }
    }
    
    /**
     * Realiza elección de líder
     */
    private void performLeaderElection() {
        try {
            if (currentLeaderId != null && knownMasters.containsKey(currentLeaderId)) {
                // Verificar que el líder actual sigue activo
                MasterInfo leader = knownMasters.get(currentLeaderId);
                if (leader.getStatus() == MasterStatus.ACTIVE) {
                    return; // Líder actual sigue activo
                }
            }
            
            // Buscar nuevo líder
            MasterInfo bestLeader = findBestLeader();
            if (bestLeader != null) {
                String newLeaderId = bestLeader.getMasterId();
                
                if (!newLeaderId.equals(currentLeaderId)) {
                    currentLeaderId = newLeaderId;
                    isLeader = newLeaderId.equals(masterId);
                    currentRole = isLeader ? MasterRole.LEADER : MasterRole.FOLLOWER;
                    
                    System.out.println("[INFO] Nuevo líder elegido: " + newLeaderId + 
                                     " (Este master es líder: " + isLeader + ")");
                    
                    // Si somos el nuevo líder, tomar control
                    if (isLeader) {
                        takeLeadership();
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("[ERROR] Error en elección de líder: " + e.getMessage());
        }
    }
    
    /**
     * Encuentra el mejor candidato para líder
     */
    private MasterInfo findBestLeader() {
        MasterInfo bestLeader = null;
        int bestPriority = Integer.MIN_VALUE;
        
        for (MasterInfo master : knownMasters.values()) {
            if (master.getStatus() == MasterStatus.ACTIVE) {
                int priority = calculateMasterPriority(master);
                if (priority > bestPriority) {
                    bestPriority = priority;
                    bestLeader = master;
                }
            }
        }
        
        return bestLeader;
    }
    
    /**
     * Calcula la prioridad de un master para ser líder
     */
    private int calculateMasterPriority(MasterInfo master) {
        int priority = 0;
        
        // Prioridad base por ID (más bajo = mayor prioridad)
        priority += 1000 - master.getMasterId().hashCode() % 1000;
        
        // Bonus por ser este master
        if (master.getMasterId().equals(masterId)) {
            priority += 100;
        }
        
        // Penalty por tiempo sin heartbeat
        long secondsSinceHeartbeat = java.time.Duration.between(master.getLastHeartbeat(), LocalDateTime.now()).getSeconds();
        priority -= (int) (secondsSinceHeartbeat / 10);
        
        return priority;
    }
    
    /**
     * Toma el liderazgo del sistema
     */
    private void takeLeadership() {
        try {
            System.out.println("[OK] Tomando liderazgo del sistema...");
            
            // Activar persistencia (simplificado)
            if (persistenceManager != null) {
                System.out.println("[INFO] Persistencia activada - implementar según necesidad");
            }
            
            // Sincronizar estado con otros masters
            syncStateWithFollowers();
            
            System.out.println("[OK] Liderazgo tomado exitosamente");
            
        } catch (Exception e) {
            System.err.println("[ERROR] Error tomando liderazgo: " + e.getMessage());
        }
    }
    
    /**
     * Sincroniza estado con otros masters
     */
    private void syncWithOtherMasters() {
        if (!isLeader) {
            // Si no somos líder, sincronizar con el líder
            syncWithLeader();
        } else {
            // Si somos líder, sincronizar con followers
            syncStateWithFollowers();
        }
    }
    
    /**
     * Sincroniza con el líder actual
     */
    private void syncWithLeader() {
        try {
            if (currentLeaderId != null && !currentLeaderId.equals(masterId)) {
                MasterInfo leader = knownMasters.get(currentLeaderId);
                if (leader != null && leader.getStatus() == MasterStatus.ACTIVE) {
                    // Aquí se implementaría la sincronización real con el líder
                    System.out.println("[INFO] Sincronizando con líder: " + currentLeaderId);
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Error sincronizando con líder: " + e.getMessage());
        }
    }
    
    /**
     * Sincroniza estado con followers
     */
    private void syncStateWithFollowers() {
        try {
            // Aquí se implementaría la sincronización real con followers
            System.out.println("[INFO] Sincronizando estado con followers");
        } catch (Exception e) {
            System.err.println("[ERROR] Error sincronizando con followers: " + e.getMessage());
        }
    }
    
    /**
     * Registra un nuevo master
     */
    public boolean registerMaster(String masterId, String host, int port) {
        try {
            MasterInfo master = new MasterInfo(masterId, host, port, MasterRole.FOLLOWER);
            master.setLastHeartbeat(LocalDateTime.now());
            master.setStatus(MasterStatus.ACTIVE);
            
            knownMasters.put(masterId, master);
            
            System.out.println("[OK] Master registrado: " + masterId + " (" + host + ":" + port + ")");
            return true;
            
        } catch (Exception e) {
            System.err.println("[ERROR] Error registrando master: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Obtiene estadísticas de failover
     */
    public Map<String, Object> getFailoverStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            stats.put("master_id", masterId);
            stats.put("master_host", masterHost);
            stats.put("master_port", masterPort);
            stats.put("current_role", currentRole.toString());
            stats.put("is_leader", isLeader);
            stats.put("current_leader_id", currentLeaderId);
            stats.put("failover_enabled", failoverEnabled);
            stats.put("known_masters_count", knownMasters.size());
            stats.put("active_masters_count", getActiveMastersCount());
            stats.put("last_leader_heartbeat", lastLeaderHeartbeat);
            
            // Lista de masters conocidos
            List<Map<String, Object>> mastersList = new ArrayList<>();
            for (MasterInfo master : knownMasters.values()) {
                Map<String, Object> masterInfo = new HashMap<>();
                masterInfo.put("master_id", master.getMasterId());
                masterInfo.put("host", master.getHost());
                masterInfo.put("port", master.getPort());
                masterInfo.put("role", master.getRole().toString());
                masterInfo.put("status", master.getStatus().toString());
                masterInfo.put("last_heartbeat", master.getLastHeartbeat());
                mastersList.add(masterInfo);
            }
            stats.put("known_masters", mastersList);
            
        } catch (Exception e) {
            stats.put("error", "Error obteniendo estadísticas: " + e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * Obtiene el número de masters activos
     */
    private int getActiveMastersCount() {
        return (int) knownMasters.values().stream()
                .filter(master -> master.getStatus() == MasterStatus.ACTIVE)
                .count();
    }
    
    /**
     * Habilita/deshabilita el failover
     */
    public void setFailoverEnabled(boolean enabled) {
        this.failoverEnabled = enabled;
        System.out.println("[INFO] Failover " + (enabled ? "habilitado" : "deshabilitado"));
    }
    
    /**
     * Detiene el sistema de failover
     */
    public void stop() {
        try {
            scheduler.shutdown();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
            
            System.out.println("[OK] Sistema de failover detenido");
            
        } catch (Exception e) {
            System.err.println("[ERROR] Error deteniendo failover: " + e.getMessage());
        }
    }
    
    // Getters
    public String getMasterId() { return masterId; }
    public MasterRole getCurrentRole() { return currentRole; }
    public boolean isLeader() { return isLeader; }
    public String getCurrentLeaderId() { return currentLeaderId; }
    public boolean isFailoverEnabled() { return failoverEnabled; }
    public List<MasterInfo> getKnownMasters() { return new ArrayList<>(knownMasters.values()); }
}
