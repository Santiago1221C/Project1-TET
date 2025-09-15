package com.gridmr.master.components;

import com.gridmr.master.model.NodeInfo;
import com.gridmr.master.model.NodeStatus;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;

public class NodeManager {
    
    // Mapa de nodos registrados (nodeId -> NodeInfo)
    private final Map<String, NodeInfo> registeredNodes;
    
    // Mapa de nodos activos (nodeId -> NodeInfo)
    private final Map<String, NodeInfo> activeNodes;
    
    // Mapa de nodos inactivos (nodeId -> NodeInfo)
    private final Map<String, NodeInfo> inactiveNodes;
    
    // Scheduler para tareas periódicas
    private ScheduledExecutorService scheduler;
    
    // Configuración de tolerancia a fallos de nodos
    private static final int NODE_HEARTBEAT_INTERVAL_SECONDS = 10;
    private static final int NODE_TIMEOUT_SECONDS = 30;
    private static final int NODE_DISCOVERY_INTERVAL_SECONDS = 15;
    private static final int NODE_CLEANUP_INTERVAL_SECONDS = 60;
    private static final int MAX_NODE_RETRY_ATTEMPTS = 3;
    
    // Contador de reintentos por nodo
    private final Map<String, Integer> nodeRetryCount;
    
    // Estadísticas
    private int totalNodesRegistered;
    private int totalNodesActive;
    private int totalWorkersAcrossNodes;
    
    public NodeManager() {
        this.registeredNodes = new ConcurrentHashMap<>();
        this.activeNodes = new ConcurrentHashMap<>();
        this.inactiveNodes = new ConcurrentHashMap<>();
        this.nodeRetryCount = new ConcurrentHashMap<>();
        
        this.totalNodesRegistered = 0;
        this.totalNodesActive = 0;
        this.totalWorkersAcrossNodes = 0;
        
        System.out.println("NodeManager inicializado con tolerancia a fallos de nodos");
    }
    
    public void start() {
        System.out.println("Iniciando NodeManager con tolerancia a fallos de nodos...");
        
        // Iniciar scheduler para tareas periódicas
        this.scheduler = Executors.newScheduledThreadPool(4);
        
        // Tarea periódica para descubrimiento de nodos
        scheduler.scheduleAtFixedRate(
            this::discoverNodes,
            NODE_DISCOVERY_INTERVAL_SECONDS,
            NODE_DISCOVERY_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        // Tarea periódica para verificar heartbeats de nodos
        scheduler.scheduleAtFixedRate(
            this::checkNodeHeartbeats,
            NODE_HEARTBEAT_INTERVAL_SECONDS,
            NODE_HEARTBEAT_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        // Tarea periódica para health checks proactivos de nodos
        scheduler.scheduleAtFixedRate(
            this::performNodeHealthChecks,
            5, // Cada 5 segundos
            5,
            TimeUnit.SECONDS
        );
        
        // Tarea periódica para limpieza de nodos inactivos
        scheduler.scheduleAtFixedRate(
            this::cleanupInactiveNodes,
            NODE_CLEANUP_INTERVAL_SECONDS,
            NODE_CLEANUP_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        System.out.println("[OK] NodeManager iniciado - Monitoreo de nodos activo:");
        System.out.println("   - Descubrimiento cada " + NODE_DISCOVERY_INTERVAL_SECONDS + "s");
        System.out.println("   - Heartbeats cada " + NODE_HEARTBEAT_INTERVAL_SECONDS + "s");
        System.out.println("   - Health checks cada 5s");
        System.out.println("   - Timeout: " + NODE_TIMEOUT_SECONDS + "s");
        System.out.println("   - Cleanup cada " + NODE_CLEANUP_INTERVAL_SECONDS + "s");
    }
    
    public void stop() {
        System.out.println("Deteniendo NodeManager...");
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.println("NodeManager detenido");
    }
    
    // MÉTODOS DE REGISTRO DE NODOS
    
    // Registra un nuevo nodo en el sistema
    public boolean registerNode(String nodeId, String host, int port, int maxWorkers, String nodeType) {
        if (registeredNodes.containsKey(nodeId)) {
            System.out.println("Nodo " + nodeId + " ya está registrado");
            return false;
        }
        
        NodeInfo node = new NodeInfo(nodeId, host, port, maxWorkers, nodeType);
        registeredNodes.put(nodeId, node);
        activeNodes.put(nodeId, node);
        
        totalNodesRegistered++;
        totalNodesActive++;
        
        System.out.println("[OK] Nodo registrado: " + nodeId + " (" + host + ":" + port + ") - Tipo: " + nodeType + ", MaxWorkers: " + maxWorkers);
        
        return true;
    }
    
    // Actualiza el heartbeat de un nodo
    public boolean updateNodeHeartbeat(String nodeId) {
        NodeInfo node = registeredNodes.get(nodeId);
        if (node == null) {
            System.out.println("[WARN] Heartbeat de nodo inexistente: " + nodeId);
            return false;
        }
        
        node.updateHeartbeat();
        
        // Resetear contador de reintentos al recibir heartbeat
        nodeRetryCount.put(nodeId, 0);
        
        // Si el nodo estaba inactivo, reactivarlo
        if (node.getStatus() == NodeStatus.OFFLINE) {
            node.setStatus(NodeStatus.ACTIVE);
            inactiveNodes.remove(nodeId);
            activeNodes.put(nodeId, node);
            totalNodesActive++;
            System.out.println("[OK] Nodo " + nodeId + " reactivado exitosamente");
        } else {
            System.out.println("[INFO] Heartbeat recibido de nodo " + nodeId + " - Estado: " + node.getStatus());
        }
        
        return true;
    }
    
    // MÉTODOS DE MONITOREO Y LIMPIEZA
    
    // Descubre nodos automáticamente
    private void discoverNodes() {
        try {
            // En un sistema real, aquí se implementaría el descubrimiento automático
            // Por ahora, simulamos el descubrimiento
            System.out.println("[INFO] Descubrimiento de nodos ejecutándose...");
            
            // Verificar nodos existentes
            for (NodeInfo node : registeredNodes.values()) {
                if (node.getStatus() == NodeStatus.ACTIVE && !activeNodes.containsKey(node.getNodeId())) {
                    activeNodes.put(node.getNodeId(), node);
                    System.out.println("[INFO] Nodo " + node.getNodeId() + " reencontrado en descubrimiento");
                }
            }
            
        } catch (Exception e) {
            System.err.println("[ERROR] Error en descubrimiento de nodos: " + e.getMessage());
        }
    }
    
    // Verifica los heartbeats de todos los nodos
    private void checkNodeHeartbeats() {
        List<String> inactiveNodeIds = new ArrayList<>();
        
        for (NodeInfo node : registeredNodes.values()) {
            if (!node.isActive(NODE_TIMEOUT_SECONDS)) {
                inactiveNodeIds.add(node.getNodeId());
            }
        }
        
        // Marcar nodos inactivos
        for (String nodeId : inactiveNodeIds) {
            markNodeAsInactive(nodeId);
        }
        
        if (!inactiveNodeIds.isEmpty()) {
            System.out.println("[WARN] Nodos inactivos detectados: " + inactiveNodeIds);
        }
    }
    
    // Realiza health checks proactivos de nodos
    private void performNodeHealthChecks() {
        try {
            long currentTime = System.currentTimeMillis();
            List<String> problematicNodes = new ArrayList<>();
            
            // Verificar nodos activos
            for (Map.Entry<String, NodeInfo> entry : activeNodes.entrySet()) {
                String nodeId = entry.getKey();
                NodeInfo node = entry.getValue();
                
                if (isNodeProblematic(node, currentTime)) {
                    problematicNodes.add(nodeId);
                    incrementNodeRetryCount(nodeId);
                }
            }
            
            // Manejar nodos problemáticos
            for (String nodeId : problematicNodes) {
                handleProblematicNode(nodeId);
            }
            
        } catch (Exception e) {
            System.err.println("[ERROR] Error en health check de nodos: " + e.getMessage());
        }
    }
    
    // Verifica si un nodo tiene problemas
    private boolean isNodeProblematic(NodeInfo node, long currentTime) {
        if (node == null || node.getLastHeartbeat() == null) {
            return true;
        }
        
        long timeSinceLastHeartbeat = currentTime - node.getLastHeartbeat().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        return timeSinceLastHeartbeat > (NODE_TIMEOUT_SECONDS * 1000);
    }
    
    // Incrementa el contador de reintentos de un nodo
    private void incrementNodeRetryCount(String nodeId) {
        int currentRetries = nodeRetryCount.getOrDefault(nodeId, 0);
        nodeRetryCount.put(nodeId, currentRetries + 1);
        System.out.println("[WARN] Nodo " + nodeId + " - Reintento #" + (currentRetries + 1));
    }
    
    // Maneja un nodo problemático según su número de reintentos
    private void handleProblematicNode(String nodeId) {
        int retryCount = nodeRetryCount.getOrDefault(nodeId, 0);
        
        if (retryCount >= MAX_NODE_RETRY_ATTEMPTS) {
            System.out.println("[ERROR] Nodo " + nodeId + " excedió reintentos máximos (" + MAX_NODE_RETRY_ATTEMPTS + ") - Marcando como inactivo");
            markNodeAsInactive(nodeId);
            nodeRetryCount.remove(nodeId);
        } else {
            System.out.println("[WARN] Nodo " + nodeId + " problemático - Reintento " + retryCount + "/" + MAX_NODE_RETRY_ATTEMPTS);
        }
    }
    
    // Marca un nodo como inactivo
    private void markNodeAsInactive(String nodeId) {
        NodeInfo node = registeredNodes.get(nodeId);
        if (node == null) {
            System.out.println("[WARN] Intento de marcar nodo inexistente como inactivo: " + nodeId);
            return;
        }
        
        // Obtener información de reintentos
        int retryCount = nodeRetryCount.getOrDefault(nodeId, 0);
        
        // Cambiar estado a OFFLINE
        node.setStatus(NodeStatus.OFFLINE);
        
        // Mover de activo a inactivo
        boolean wasActive = activeNodes.remove(nodeId) != null;
        inactiveNodes.put(nodeId, node);
        
        totalNodesActive--;
        
        // Limpiar contador de reintentos
        nodeRetryCount.remove(nodeId);
        
        System.out.println("[ERROR] Nodo " + nodeId + " marcado como inactivo:");
        System.out.println("   - Estado anterior: " + (wasActive ? "activo" : "desconocido"));
        System.out.println("   - Reintentos fallidos: " + retryCount);
        System.out.println("   - Nodos activos restantes: " + totalNodesActive);
    }
    
    // Limpia nodos inactivos del sistema
    private void cleanupInactiveNodes() {
        if (inactiveNodes.isEmpty()) {
            return;
        }
        
        List<String> nodesToRemove = new ArrayList<>();
        
        for (NodeInfo node : inactiveNodes.values()) {
            // Remover nodos que han estado inactivos por más del timeout configurado
            long inactiveTime = java.time.Duration.between(node.getLastHeartbeat(), LocalDateTime.now()).getSeconds();
            if (inactiveTime > NODE_TIMEOUT_SECONDS) {
                nodesToRemove.add(node.getNodeId());
            }
        }
        
        // Remover nodos inactivos
        for (String nodeId : nodesToRemove) {
            unregisterNode(nodeId);
            System.out.println("[INFO] Nodo " + nodeId + " removido por inactividad prolongada");
        }
    }
    
    // Da de baja a un nodo del sistema
    public boolean unregisterNode(String nodeId) {
        NodeInfo node = registeredNodes.remove(nodeId);
        if (node == null) {
            System.out.println("Nodo " + nodeId + " no está registrado");
            return false;
        }
        
        // Remover de todos los mapas
        activeNodes.remove(nodeId);
        inactiveNodes.remove(nodeId);
        nodeRetryCount.remove(nodeId);
        
        totalNodesActive--;
        
        System.out.println("Nodo dado de baja: " + nodeId);
        return true;
    }
    
    // MÉTODOS DE BALANCEO DE CARGA
    
    // Encuentra el mejor nodo para asignar un worker
    public NodeInfo findBestNodeForWorker() {
        if (activeNodes.isEmpty()) {
            return null;
        }
        
        NodeInfo bestNode = null;
        double bestScore = Double.MIN_VALUE;
        
        for (NodeInfo node : activeNodes.values()) {
            double score = calculateNodeScore(node);
            if (score > bestScore) {
                bestScore = score;
                bestNode = node;
            }
        }
        
        if (bestNode != null) {
            System.out.println("[OK] Nodo " + bestNode.getNodeId() + " seleccionado para worker (score: " + String.format("%.2f", bestScore) + ")");
        }
        
        return bestNode;
    }
    
    // Calcula el score de un nodo para balanceo de carga
    private double calculateNodeScore(NodeInfo node) {
        if (node == null) return Double.MIN_VALUE;
        
        double score = 0.0;
        
        // Capacidad disponible (más workers disponibles = mejor score)
        int availableSlots = node.getMaxWorkers() - node.getCurrentWorkers();
        score += availableSlots * 10.0;
        
        // Salud del nodo
        boolean isHealthy = node.getLastHeartbeat() != null && 
                           (System.currentTimeMillis() - node.getLastHeartbeat().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) < (NODE_TIMEOUT_SECONDS * 1000);
        score += isHealthy ? 50.0 : 0.0;
        
        // Penalizar por reintentos
        int retryCount = nodeRetryCount.getOrDefault(node.getNodeId(), 0);
        score -= retryCount * 5.0;
        
        // Penalizar por tiempo sin heartbeat
        if (node.getLastHeartbeat() != null) {
            long timeSinceHeartbeat = System.currentTimeMillis() - node.getLastHeartbeat().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            if (timeSinceHeartbeat > 10000) { // Más de 10 segundos
                score -= 10.0;
            }
        }
        
        return score;
    }
    
    // MÉTODOS DE ESTADÍSTICAS
    
    // Obtiene estadísticas del sistema de nodos
    public String getNodeStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== ESTADÍSTICAS DE NODOS (TOLERANCIA A FALLOS) ===\n");
        stats.append("Nodos registrados: ").append(totalNodesRegistered).append("\n");
        stats.append("Nodos activos: ").append(totalNodesActive).append("\n");
        stats.append("Nodos inactivos: ").append(inactiveNodes.size()).append("\n");
        stats.append("Workers totales en nodos: ").append(totalWorkersAcrossNodes).append("\n");
        
        // Configuración de tolerancia a fallos
        stats.append("\n=== CONFIGURACIÓN DE TOLERANCIA A FALLOS DE NODOS ===\n");
        stats.append("Heartbeat interval: ").append(NODE_HEARTBEAT_INTERVAL_SECONDS).append("s\n");
        stats.append("Node timeout: ").append(NODE_TIMEOUT_SECONDS).append("s\n");
        stats.append("Discovery interval: ").append(NODE_DISCOVERY_INTERVAL_SECONDS).append("s\n");
        stats.append("Cleanup interval: ").append(NODE_CLEANUP_INTERVAL_SECONDS).append("s\n");
        stats.append("Max retry attempts: ").append(MAX_NODE_RETRY_ATTEMPTS).append("\n");
        
        // Estadísticas por nodo
        stats.append("\n=== DETALLES POR NODO ===\n");
        for (NodeInfo node : registeredNodes.values()) {
            int retryCount = nodeRetryCount.getOrDefault(node.getNodeId(), 0);
            boolean isHealthy = node.getLastHeartbeat() != null && 
                              (System.currentTimeMillis() - node.getLastHeartbeat().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) < (NODE_TIMEOUT_SECONDS * 1000);
            
            stats.append("Nodo ").append(node.getNodeId())
                .append(" - Estado: ").append(node.getStatus())
                .append(", Salud: ").append(isHealthy ? "✅" : "❌")
                .append(", Host: ").append(node.getHost())
                .append(":").append(node.getPort())
                .append(", Workers: ").append(node.getCurrentWorkers())
                .append("/").append(node.getMaxWorkers())
                .append(", Tipo: ").append(node.getNodeType())
                .append(", Reintentos: ").append(retryCount)
                .append("\n");
        }
        
        return stats.toString();
    }
    
    // Obtiene estadísticas de nodos en formato JSON
    public Map<String, Object> getNodeStatisticsJson() {
        Map<String, Object> stats = new HashMap<>();
        
        // Estadísticas básicas
        stats.put("totalNodes", totalNodesRegistered);
        stats.put("activeNodes", totalNodesActive);
        stats.put("inactiveNodes", inactiveNodes.size());
        stats.put("totalWorkersAcrossNodes", totalWorkersAcrossNodes);
        
        // Configuración de tolerancia a fallos
        Map<String, Object> config = new HashMap<>();
        config.put("heartbeatIntervalSeconds", NODE_HEARTBEAT_INTERVAL_SECONDS);
        config.put("nodeTimeoutSeconds", NODE_TIMEOUT_SECONDS);
        config.put("discoveryIntervalSeconds", NODE_DISCOVERY_INTERVAL_SECONDS);
        config.put("cleanupIntervalSeconds", NODE_CLEANUP_INTERVAL_SECONDS);
        config.put("maxRetryAttempts", MAX_NODE_RETRY_ATTEMPTS);
        stats.put("faultToleranceConfig", config);
        
        // Estadísticas por nodo
        List<Map<String, Object>> nodeStats = new ArrayList<>();
        for (NodeInfo node : registeredNodes.values()) {
            Map<String, Object> nodeInfo = new HashMap<>();
            nodeInfo.put("id", node.getNodeId());
            nodeInfo.put("status", node.getStatus().toString());
            nodeInfo.put("host", node.getHost());
            nodeInfo.put("port", node.getPort());
            nodeInfo.put("nodeType", node.getNodeType());
            nodeInfo.put("currentWorkers", node.getCurrentWorkers());
            nodeInfo.put("maxWorkers", node.getMaxWorkers());
            nodeInfo.put("retryCount", nodeRetryCount.getOrDefault(node.getNodeId(), 0));
            
            // Verificar salud del nodo
            boolean isHealthy = node.getLastHeartbeat() != null && 
                              (System.currentTimeMillis() - node.getLastHeartbeat().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) < (NODE_TIMEOUT_SECONDS * 1000);
            nodeInfo.put("isHealthy", isHealthy);
            
            if (node.getLastHeartbeat() != null) {
                long timeSinceHeartbeat = System.currentTimeMillis() - node.getLastHeartbeat().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                nodeInfo.put("timeSinceLastHeartbeatMs", timeSinceHeartbeat);
            } else {
                nodeInfo.put("timeSinceLastHeartbeatMs", -1);
            }
            
            nodeStats.add(nodeInfo);
        }
        stats.put("nodes", nodeStats);
        
        return stats;
    }
    
    // Getters básicos
    public int getTotalNodesCount() {
        return totalNodesRegistered;
    }
    public int getActiveNodesCount() {
        return totalNodesActive;
    }
    public int getInactiveNodesCount() {
        return inactiveNodes.size();
    }
    public List<NodeInfo> getAllNodes() {
        return new ArrayList<>(registeredNodes.values());
    }
    public List<NodeInfo> getActiveNodes() {
        return new ArrayList<>(activeNodes.values());
    }
    public List<NodeInfo> getInactiveNodes() {
        return new ArrayList<>(inactiveNodes.values());
    }
}