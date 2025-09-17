package com.gridmr.master.grpc;

import com.gridmr.master.components.ResourceManager;
import com.gridmr.master.components.Scheduler;
import com.gridmr.master.components.JobManager;
import com.gridmr.master.model.Task;
import com.gridmr.master.model.TaskType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MasterGrpcServiceFixed {
    
    @Autowired
    private ResourceManager resourceManager;
    
    @Autowired
    private Scheduler scheduler;
    
    @Autowired
    private JobManager jobManager;
    
    /**
     * Registra un worker en el sistema - VERSIÓN CORREGIDA
     */
    public GrpcMessages.RegisterWorkerResponse registerWorker(GrpcMessages.RegisterWorkerRequest request) {
        try {
            System.out.println("📝 Worker registrándose: " + request.getWorkerId());
            
            // Usar el método correcto del ResourceManager con todos los parámetros requeridos
            boolean success = resourceManager.registerWorker(
                request.getWorkerId(),
                request.getHost(),
                request.getPort(),
                4, // cpuCores - valor por defecto
                8192, // memoryMB - valor por defecto
                100, // diskSpaceGB - valor por defecto
                100, // computePower - valor por defecto
                request.getMaxTasks() // maxConcurrentTasks
            );
            
            return new GrpcMessages.RegisterWorkerResponse(
                success,
                success ? "Worker registrado correctamente" : "Error al registrar worker",
                "master-001"
            );
            
        } catch (Exception e) {
            System.err.println("❌ Error registrando worker: " + e.getMessage());
            return new GrpcMessages.RegisterWorkerResponse(false, "Error interno: " + e.getMessage(), "master-001");
        }
    }
    
    /**
     * Desregistra un worker del sistema
     */
    public GrpcMessages.UnregisterWorkerResponse unregisterWorker(GrpcMessages.UnregisterWorkerRequest request) {
        try {
            System.out.println("📝 Worker desregistrándose: " + request.getWorkerId());
            
            boolean success = resourceManager.unregisterWorker(request.getWorkerId());
            
            return new GrpcMessages.UnregisterWorkerResponse(
                success,
                success ? "Worker desregistrado correctamente" : "Worker no encontrado"
            );
            
        } catch (Exception e) {
            System.err.println("❌ Error desregistrando worker: " + e.getMessage());
            return new GrpcMessages.UnregisterWorkerResponse(false, "Error interno: " + e.getMessage());
        }
    }
    
    /**
     * Procesa heartbeat de un worker
     */
    public GrpcMessages.HeartbeatResponse sendHeartbeat(GrpcMessages.HeartbeatRequest request) {
        try {
            boolean success = resourceManager.updateWorkerHeartbeat(request.getWorkerId());
            
            return new GrpcMessages.HeartbeatResponse(
                success,
                success ? "Heartbeat recibido" : "Worker no encontrado",
                System.currentTimeMillis()
            );
            
        } catch (Exception e) {
            System.err.println("❌ Error procesando heartbeat: " + e.getMessage());
            return new GrpcMessages.HeartbeatResponse(false, "Error interno: " + e.getMessage(), System.currentTimeMillis());
        }
    }
    
    /**
     * Reporta completación de una tarea
     */
    public GrpcMessages.TaskCompletionResponse reportTaskCompletion(GrpcMessages.TaskCompletionRequest request) {
        try {
            System.out.println("✅ Tarea completada: " + request.getTaskId() + " por worker: " + request.getWorkerId());
            
            // Notificar al scheduler que la tarea está completa
            boolean success = scheduler.markTaskCompleted(request.getTaskId(), request.getWorkerId());
            
            return new GrpcMessages.TaskCompletionResponse(
                success,
                success ? "Tarea marcada como completada" : "Error al procesar tarea"
            );
            
        } catch (Exception e) {
            System.err.println("❌ Error procesando tarea completada: " + e.getMessage());
            return new GrpcMessages.TaskCompletionResponse(false, "Error interno: " + e.getMessage());
        }
    }
    
    /**
     * Reporta fallo de una tarea
     */
    public GrpcMessages.TaskFailureResponse reportTaskFailure(GrpcMessages.TaskFailureRequest request) {
        try {
            System.out.println("❌ Tarea falló: " + request.getTaskId() + " por worker: " + request.getWorkerId() + " - Error: " + request.getErrorMessage());
            
            // Notificar al scheduler que la tarea falló
            boolean success = scheduler.markTaskFailed(request.getTaskId(), request.getWorkerId(), request.getErrorMessage());
            
            return new GrpcMessages.TaskFailureResponse(
                success,
                success ? "Fallo de tarea registrado" : "Error al procesar fallo"
            );
            
        } catch (Exception e) {
            System.err.println("❌ Error procesando fallo de tarea: " + e.getMessage());
            return new GrpcMessages.TaskFailureResponse(false, "Error interno: " + e.getMessage());
        }
    }
    
    /**
     * Solicita una tarea para un worker - VERSIÓN CORREGIDA
     */
    public GrpcMessages.RequestTaskResponse requestTask(GrpcMessages.RequestTaskRequest request) {
        try {
            System.out.println("📋 Worker solicitando tarea: " + request.getWorkerId());
            
            // Verificar que el worker esté registrado
            if (resourceManager.getWorker(request.getWorkerId()) == null) {
                return new GrpcMessages.RequestTaskResponse(
                    false,
                    "Worker no registrado: " + request.getWorkerId()
                );
            }
            
            // Buscar tareas Map disponibles primero
            if (scheduler.getPendingTaskCount(TaskType.MAP) > 0) {
                // Aquí deberías implementar la lógica para obtener la siguiente tarea Map
                // Por ahora, retornamos que hay tareas disponibles pero no las asignamos automáticamente
                return new GrpcMessages.RequestTaskResponse(
                    true,
                    "Hay tareas Map disponibles. El scheduler las asignará automáticamente."
                );
            }
            
            // Buscar tareas Reduce disponibles
            if (scheduler.getPendingTaskCount(TaskType.REDUCE) > 0) {
                return new GrpcMessages.RequestTaskResponse(
                    true,
                    "Hay tareas Reduce disponibles. El scheduler las asignará automáticamente."
                );
            }
            
            return new GrpcMessages.RequestTaskResponse(
                false,
                "No hay tareas disponibles en este momento"
            );
            
        } catch (Exception e) {
            System.err.println("❌ Error procesando solicitud de tarea: " + e.getMessage());
            return new GrpcMessages.RequestTaskResponse(false, "Error interno: " + e.getMessage());
        }
    }
}
