package com.gridmr.master.controller;

import com.gridmr.master.grpc.GrpcMessages;
import com.gridmr.master.grpc.MasterGrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST que expone los servicios gRPC del Master para Workers
 * Este controlador actúa como un puente entre HTTP y la lógica gRPC
 */
@RestController
@RequestMapping("/api/grpc")
@CrossOrigin(origins = "*")
public class MasterGrpcController {
    
    @Autowired
    private MasterGrpcService grpcService;
    
    /**
     * Endpoint para registro de workers
     * POST /api/grpc/register-worker
     */
    @PostMapping("/register-worker")
    public ResponseEntity<GrpcMessages.RegisterWorkerResponse> registerWorker(@RequestBody GrpcMessages.RegisterWorkerRequest request) {
        try {
            GrpcMessages.RegisterWorkerResponse response = grpcService.registerWorker(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            GrpcMessages.RegisterWorkerResponse errorResponse = new GrpcMessages.RegisterWorkerResponse(
                false, 
                "Error interno: " + e.getMessage(), 
                "master-001"
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Endpoint para desregistro de workers
     * POST /api/grpc/unregister-worker
     */
    @PostMapping("/unregister-worker")
    public ResponseEntity<GrpcMessages.UnregisterWorkerResponse> unregisterWorker(@RequestBody GrpcMessages.UnregisterWorkerRequest request) {
        try {
            GrpcMessages.UnregisterWorkerResponse response = grpcService.unregisterWorker(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            GrpcMessages.UnregisterWorkerResponse errorResponse = new GrpcMessages.UnregisterWorkerResponse(
                false, 
                "Error interno: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Endpoint para heartbeat de workers
     * POST /api/grpc/heartbeat
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<GrpcMessages.HeartbeatResponse> sendHeartbeat(@RequestBody GrpcMessages.HeartbeatRequest request) {
        try {
            GrpcMessages.HeartbeatResponse response = grpcService.sendHeartbeat(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            GrpcMessages.HeartbeatResponse errorResponse = new GrpcMessages.HeartbeatResponse(
                false, 
                "Error interno: " + e.getMessage(), 
                System.currentTimeMillis()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Endpoint para reporte de tarea completada
     * POST /api/grpc/task-completion
     */
    @PostMapping("/task-completion")
    public ResponseEntity<GrpcMessages.TaskCompletionResponse> reportTaskCompletion(@RequestBody GrpcMessages.TaskCompletionRequest request) {
        try {
            GrpcMessages.TaskCompletionResponse response = grpcService.reportTaskCompletion(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            GrpcMessages.TaskCompletionResponse errorResponse = new GrpcMessages.TaskCompletionResponse(
                false, 
                "Error interno: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Endpoint para reporte de tarea fallida
     * POST /api/grpc/task-failure
     */
    @PostMapping("/task-failure")
    public ResponseEntity<GrpcMessages.TaskFailureResponse> reportTaskFailure(@RequestBody GrpcMessages.TaskFailureRequest request) {
        try {
            GrpcMessages.TaskFailureResponse response = grpcService.reportTaskFailure(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            GrpcMessages.TaskFailureResponse errorResponse = new GrpcMessages.TaskFailureResponse(
                false, 
                "Error interno: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Endpoint para solicitud de tarea
     * POST /api/grpc/request-task
     */
    @PostMapping("/request-task")
    public ResponseEntity<GrpcMessages.RequestTaskResponse> requestTask(@RequestBody GrpcMessages.RequestTaskRequest request) {
        try {
            GrpcMessages.RequestTaskResponse response = grpcService.requestTask(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            GrpcMessages.RequestTaskResponse errorResponse = new GrpcMessages.RequestTaskResponse(
                false, 
                "Error interno: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Endpoint de prueba para verificar que el servicio gRPC está funcionando
     * GET /api/grpc/status
     */
    @GetMapping("/status")
    public ResponseEntity<String> getGrpcStatus() {
        return ResponseEntity.ok("✅ Servicio gRPC del Master funcionando correctamente");
    }
}
