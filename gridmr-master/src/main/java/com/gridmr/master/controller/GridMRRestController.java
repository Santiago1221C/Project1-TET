package com.gridmr.master.controller;

import com.gridmr.master.components.JobManager;
import com.gridmr.master.components.ResourceManager;
import com.gridmr.master.components.NodeManager;
import com.gridmr.master.model.Job;
import com.gridmr.master.model.Worker;
import com.gridmr.master.model.NodeInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class GridMRRestController {

    @Autowired
    private JobManager jobManager;

    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private NodeManager nodeManager;

    // ==================== HEALTH CHECK ====================
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("active_workers", resourceManager.getActiveWorkersCount());
        response.put("total_workers", resourceManager.getTotalWorkersCount());
        response.put("uptime", "Running");
        
        return ResponseEntity.ok(response);
    }

    // ==================== WORKERS MANAGEMENT ====================
    
    @GetMapping("/workers")
    public ResponseEntity<Map<String, Object>> listWorkers() {
        Map<String, Object> response = new HashMap<>();
        List<Worker> workers = resourceManager.getAllWorkers();
        
        response.put("workers", workers);
        response.put("total_count", workers.size());
        response.put("active_count", resourceManager.getActiveWorkersCount());
        
        return ResponseEntity.ok(response);
    }

    // ==================== JOB MANAGEMENT ====================
    
    @PostMapping("/jobs/submit")
    public ResponseEntity<Map<String, Object>> submitJob(@RequestBody Map<String, Object> jobRequest) {
        try {
            // Crear Job desde la petición REST
            String jobId = "job_" + UUID.randomUUID().toString().substring(0, 8);
            String clientId = (String) jobRequest.getOrDefault("client_id", "default_client");
            
            Job job = new Job(jobId, clientId);
            
            // Configurar job usando métodos existentes
            String jobType = (String) jobRequest.get("job_type");
            if (jobType != null) {
                // Usar el método setJobType si existe, o configurar manualmente
                job.setMapFunction(jobType);
                job.setReduceFunction(jobType);
            }
            
            job.setNumMappers((Integer) jobRequest.getOrDefault("map_tasks", 2));
            job.setNumReducers((Integer) jobRequest.getOrDefault("reduce_tasks", 1));
            
            // Agregar archivos de entrada
            @SuppressWarnings("unchecked")
            List<String> inputFiles = (List<String>) jobRequest.get("input_files");
            if (inputFiles != null) {
                for (String file : inputFiles) {
                    job.addInputFile(file);
                }
            }
            
            // Enviar trabajo al JobManager usando método existente
            boolean success = jobManager.submitJob(job);
            
            Map<String, Object> response = new HashMap<>();
            if (success) {
                response.put("job_id", jobId);
                response.put("status", "SUBMITTED");
                response.put("message", "Job submitted successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Failed to submit job");
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Error submitting job: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/jobs/{jobId}/status")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
        Job job = jobManager.getJob(jobId);
        
        if (job == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Job not found");
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("job_id", jobId);
        response.put("status", job.getStatus().toString());
        response.put("created_at", job.getCreatedAt().toString());
        response.put("started_at", job.getStartedAt() != null ? job.getStartedAt().toString() : null);
        response.put("completed_at", job.getCompletedAt() != null ? job.getCompletedAt().toString() : null);
        
        // Calcular progreso
        int totalTasks = job.getMapTasks().size() + job.getReduceTasks().size();
        int completedTasks = 0;
        
        for (var task : job.getMapTasks()) {
            if (task.getStatus().toString().equals("COMPLETED")) completedTasks++;
        }
        for (var task : job.getReduceTasks()) {
            if (task.getStatus().toString().equals("COMPLETED")) completedTasks++;
        }
        
        int progress = totalTasks > 0 ? (completedTasks * 100) / totalTasks : 0;
        response.put("progress", progress);
        response.put("tasks_completed", completedTasks);
        response.put("total_tasks", totalTasks);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelJob(@PathVariable String jobId) {
        boolean success = jobManager.cancelJob(jobId, "Cancelled by user");
        
        Map<String, Object> response = new HashMap<>();
        if (success) {
            response.put("job_id", jobId);
            response.put("status", "CANCELLED");
            response.put("message", "Job cancelled successfully");
            return ResponseEntity.ok(response);
        } else {
            response.put("error", "Failed to cancel job");
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/jobs/{jobId}/logs")
    public ResponseEntity<Map<String, Object>> getJobLogs(@PathVariable String jobId) {
        // Implementación básica de logs
        Map<String, Object> response = new HashMap<>();
        response.put("job_id", jobId);
        response.put("logs", "Job logs would be here - not implemented yet");
        
        return ResponseEntity.ok(response);
    }

    // ==================== SYSTEM STATUS ====================
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        Map<String, Object> response = new HashMap<>();
        
        response.put("master_status", "RUNNING");
        response.put("active_workers", resourceManager.getActiveWorkersCount());
        response.put("total_workers", resourceManager.getTotalWorkersCount());
        response.put("active_jobs", jobManager.getActiveJobs().size());
        response.put("total_jobs_submitted", jobManager.getTotalJobsSubmitted());
        response.put("total_jobs_completed", jobManager.getTotalJobsCompleted());
        response.put("total_jobs_failed", jobManager.getTotalJobsFailed());
        response.put("timestamp", LocalDateTime.now().toString());
        
        return ResponseEntity.ok(response);
    }
    
    // ==================== FAULT TOLERANCE ====================
    
    @GetMapping("/fault-tolerance")
    public ResponseEntity<Map<String, Object>> getFaultToleranceStatus() {
        try {
            Map<String, Object> faultToleranceStats = resourceManager.getFaultToleranceStatistics();
            return ResponseEntity.ok(faultToleranceStats);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Error obteniendo estadísticas de tolerancia a fallos: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @GetMapping("/fault-tolerance/text")
    public ResponseEntity<String> getFaultToleranceStatusText() {
        try {
            String faultToleranceStats = resourceManager.getSystemStatistics();
            return ResponseEntity.ok(faultToleranceStats);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("Error obteniendo estadísticas de tolerancia a fallos: " + e.getMessage());
        }
    }
    
    // ==================== NODE MANAGEMENT ====================
    
    @GetMapping("/nodes")
    public ResponseEntity<Map<String, Object>> listNodes() {
        Map<String, Object> response = new HashMap<>();
        List<NodeInfo> nodes = nodeManager.getAllNodes();
        
        response.put("nodes", nodes);
        response.put("total_count", nodes.size());
        response.put("active_count", nodeManager.getActiveNodesCount());
        response.put("inactive_count", nodeManager.getInactiveNodesCount());
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/nodes/register")
    public ResponseEntity<Map<String, Object>> registerNode(@RequestBody Map<String, Object> nodeRequest) {
        try {
            String nodeId = (String) nodeRequest.get("node_id");
            String host = (String) nodeRequest.get("host");
            Integer port = (Integer) nodeRequest.get("port");
            Integer maxWorkers = (Integer) nodeRequest.getOrDefault("max_workers", 10);
            String nodeType = (String) nodeRequest.getOrDefault("node_type", "WORKER");
            
            if (nodeId == null || host == null || port == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("error", "node_id, host y port son requeridos");
                return ResponseEntity.badRequest().body(response);
            }
            
            boolean success = nodeManager.registerNode(nodeId, host, port, maxWorkers, nodeType);
            
            Map<String, Object> response = new HashMap<>();
            if (success) {
                response.put("node_id", nodeId);
                response.put("status", "REGISTERED");
                response.put("message", "Nodo registrado exitosamente");
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Error registrando nodo");
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Error registrando nodo: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping("/nodes/{nodeId}/heartbeat")
    public ResponseEntity<Map<String, Object>> updateNodeHeartbeat(@PathVariable String nodeId) {
        try {
            boolean success = nodeManager.updateNodeHeartbeat(nodeId);
            
            Map<String, Object> response = new HashMap<>();
            if (success) {
                response.put("node_id", nodeId);
                response.put("status", "HEARTBEAT_UPDATED");
                response.put("message", "Heartbeat actualizado exitosamente");
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Nodo no encontrado");
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Error actualizando heartbeat: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @GetMapping("/nodes/statistics")
    public ResponseEntity<Map<String, Object>> getNodeStatistics() {
        try {
            Map<String, Object> nodeStats = nodeManager.getNodeStatisticsJson();
            return ResponseEntity.ok(nodeStats);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Error obteniendo estadísticas de nodos: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @GetMapping("/nodes/statistics/text")
    public ResponseEntity<String> getNodeStatisticsText() {
        try {
            String nodeStats = nodeManager.getNodeStatistics();
            return ResponseEntity.ok(nodeStats);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("Error obteniendo estadísticas de nodos: " + e.getMessage());
        }
    }
}
