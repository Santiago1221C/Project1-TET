package com.gridmr.master.components;

import com.gridmr.master.model.Task;
import com.gridmr.master.model.TaskType;
import com.gridmr.master.model.TaskStatus;
import com.gridmr.master.model.Worker;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler - Programa tareas para los workers
 * 
 * Responsabilidades:
 * - Recibir tareas del JobManager (Map y Reduce)
 * - Asignar tareas a workers disponibles usando ResourceManager
 * - Manejar colas de tareas por prioridad y tipo
 * - Balancear carga entre workers
 * - Monitorear progreso de tareas asignadas
 * - Reasignar tareas si un worker falla
 */
public class Scheduler {
    
    // Referencia al ResourceManager para obtener workers
    private ResourceManager resourceManager;
    
    // Colas de tareas por tipo
    private final Queue<Task> mapTaskQueue;
    private final Queue<Task> reduceTaskQueue;
    
    // Tareas asignadas (taskId -> Worker)
    private final Map<String, Worker> assignedTasks;
    
    // Referencias a tareas asignadas (taskId -> Task)
    private final Map<String, Task> assignedTaskReferences;
    
    // Scheduler para tareas periódicas
    private ScheduledExecutorService scheduler;
    
    // Configuración
    private static final int SCHEDULING_INTERVAL_SECONDS = 2;
    private static final int TASK_TIMEOUT_SECONDS = 300; // 5 minutos
    
    // Estadísticas
    private int totalTasksScheduled;
    private int totalTasksCompleted;
    private int totalTasksFailed;
    
    public Scheduler(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        this.mapTaskQueue = new ConcurrentLinkedQueue<>();
        this.reduceTaskQueue = new ConcurrentLinkedQueue<>();
        this.assignedTasks = new HashMap<>();
        this.assignedTaskReferences = new HashMap<>();
        
        this.totalTasksScheduled = 0;
        this.totalTasksCompleted = 0;
        this.totalTasksFailed = 0;
        
        System.out.println("Scheduler inicializado");
    }
    
    public void start() {
        System.out.println("Scheduler iniciando...");
        
        // Iniciar scheduler para tareas periódicas
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // Tarea periódica para asignar tareas pendientes
        scheduler.scheduleAtFixedRate(
            this::schedulePendingTasks,
            SCHEDULING_INTERVAL_SECONDS,
            SCHEDULING_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        // Tarea periódica para verificar tareas con timeout
        scheduler.scheduleAtFixedRate(
            this::checkTaskTimeouts,
            TASK_TIMEOUT_SECONDS,
            TASK_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        );
        
        System.out.println("Scheduler iniciado - Asignación automática de tareas activa");
    }
    
    public void stop() {
        System.out.println("Scheduler deteniendo...");
        
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
        
        System.out.println("Scheduler detenido");
    }
    
    // MÉTODOS DE GESTIÓN DE TAREAS 
    
    /**
     * Agrega una tarea a la cola correspondiente
     * @param task Tarea a agregar
     * @return true si la tarea se agregó exitosamente
     */
    public boolean addTask(Task task) {
        if (task == null) {
            return false;
        }
        
        // Agregar a la cola según el tipo
        if (task.getType() == TaskType.MAP) {
            mapTaskQueue.offer(task);
            System.out.println("Tarea Map agregada a la cola: " + task.getTaskId());
        } else if (task.getType() == TaskType.REDUCE) {
            reduceTaskQueue.offer(task);
            System.out.println("Tarea Reduce agregada a la cola: " + task.getTaskId());
        } else {
            System.out.println("Tipo de tarea no soportado: " + task.getType());
            return false;
        }
        
        return true;
    }
    
    /**
     * Obtiene el número de tareas pendientes por tipo
     * @param taskType Tipo de tarea
     * @return Número de tareas pendientes
     */
    public int getPendingTaskCount(TaskType taskType) {
        if (taskType == TaskType.MAP) {
            return mapTaskQueue.size();
        } else if (taskType == TaskType.REDUCE) {
            return reduceTaskQueue.size();
        }
        return 0;
    }
    
    /**
     * Obtiene el número total de tareas asignadas
     * @return Número de tareas asignadas
     */
    public int getAssignedTaskCount() {
        return assignedTasks.size();
    }
    
    // MÉTODOS DE ASIGNACIÓN
    
    /**
     * Asigna tareas pendientes a workers disponibles
     * Se ejecuta periódicamente
     */
    private void schedulePendingTasks() {
        // Asignar tareas Map primero (mayor prioridad)
        scheduleTasksFromQueue(mapTaskQueue, TaskType.MAP);
        
        // Luego asignar tareas Reduce
        scheduleTasksFromQueue(reduceTaskQueue, TaskType.REDUCE);
    }
    
    /**
     * Asigna tareas de una cola específica
     * @param taskQueue Cola de tareas
     * @param taskType Tipo de tarea
     */
    private void scheduleTasksFromQueue(Queue<Task> taskQueue, TaskType taskType) {
        while (!taskQueue.isEmpty()) {
            Task task = taskQueue.peek(); // Ver sin remover
            
            // Buscar worker disponible
            Worker availableWorker = resourceManager.findBestAvailableWorker(
                taskType.name(), 
                task.getPriority()
            );
            
            if (availableWorker != null) {
                // Asignar tarea al worker
                if (assignTaskToWorker(task, availableWorker)) {
                    taskQueue.poll(); // Remover de la cola
                    System.out.println("Tarea " + task.getTaskId() + " asignada a worker " + 
                                    availableWorker.getWorkerId());
                } else {
                    break;
                }
            } else {
                break;
            }
        }
    }
    
    /**
     * Asigna una tarea específica a un worker
     * @param task Tarea a asignar
     * @param worker Worker que ejecutará la tarea
     * @return true si la asignación fue exitosa
     */
    private boolean assignTaskToWorker(Task task, Worker worker) {
        // Verificar que el worker esté disponible
        if (!resourceManager.isWorkerAvailable(worker.getWorkerId())) {
            return false;
        }
        
        // Asignar tarea usando ResourceManager
        if (resourceManager.assignTaskToWorker(worker.getWorkerId(), task.getTaskId())) {
            // Actualizar estado de la tarea
            task.setStatus(TaskStatus.ASSIGNED);
            task.setWorkerId(worker.getWorkerId());
            
            // Registrar asignación
            assignedTasks.put(task.getTaskId(), worker);
            assignedTaskReferences.put(task.getTaskId(), task);
            totalTasksScheduled++;
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Libera una tarea de un worker
     * @param taskId ID de la tarea
     * @param success true si la tarea se completó exitosamente
     * @param executionTimeMs Tiempo de ejecución en milisegundos
     * @return true si la liberación fue exitosa
     */
    public boolean releaseTask(String taskId, boolean success, long executionTimeMs) {
        Worker worker = assignedTasks.remove(taskId);
        assignedTaskReferences.remove(taskId); // Limpiar referencia
        if (worker == null) {
            System.out.println("Tarea " + taskId + " no está asignada");
            return false;
        }
        
        // Liberar tarea del worker usando ResourceManager
        if (resourceManager.releaseTaskFromWorker(worker.getWorkerId(), taskId, executionTimeMs, success)) {
            // Actualizar estadísticas
            if (success) {
                totalTasksCompleted++;
            } else {
                totalTasksFailed++;
            }
            
            System.out.println("Tarea " + taskId + " liberada del worker " + worker.getWorkerId() + 
                            " (éxito: " + success + ")");
            return true;
        }
        
        return false;
    }
    
    // MÉTODOS DE GESTIÓN DE TAREAS
    
    /**
     * Obtiene una tarea por su ID
     * @param taskId ID de la tarea
     * @return Task o null si no se encuentra
     */
    private Task getTaskById(String taskId) {
        // Primero buscar en tareas asignadas
        Task assignedTask = assignedTaskReferences.get(taskId);
        if (assignedTask != null) {
            return assignedTask;
        }
        
        // Buscar en la cola de tareas Map
        for (Task task : mapTaskQueue) {
            if (task.getTaskId().equals(taskId)) {
                return task;
            }
        }
        
        // Buscar en la cola de tareas Reduce
        for (Task task : reduceTaskQueue) {
            if (task.getTaskId().equals(taskId)) {
                return task;
            }
        }
        
        return null;
    }
    
    // MÉTODOS DE MONITOREO
    
    /**
     * Verifica tareas que han excedido su timeout
     * Se ejecuta periódicamente
     */
    private void checkTaskTimeouts() {
        List<String> timedOutTasks = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<String, Worker> entry : assignedTasks.entrySet()) {
            String taskId = entry.getKey();
            Worker worker = entry.getValue();
            
            // Verificar si el worker sigue activo
            if (!worker.isActive()) {
                timedOutTasks.add(taskId);
                System.out.println("Tarea " + taskId + " marcada para reasignación - Worker inactivo");
                continue;
            }
            
            // Verificar si la tarea ha excedido su timeout
            // Necesitamos obtener la tarea original para verificar su tiempo de asignación
            Task task = getTaskById(taskId);
            if (task != null && task.getStatus() == TaskStatus.ASSIGNED) {
                // Calcular tiempo transcurrido desde la asignación
                long timeSinceAssigned = currentTime - task.getStartedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                long timeoutMs = TASK_TIMEOUT_SECONDS * 1000;
                
                if (timeSinceAssigned > timeoutMs) {
                    timedOutTasks.add(taskId);
                    System.out.println("Tarea " + taskId + " marcada para reasignación - Timeout excedido (" + 
                                    (timeSinceAssigned / 1000) + "s > " + TASK_TIMEOUT_SECONDS + "s)");
                }
            }
        }
        
        // Reasignar tareas con timeout
        for (String taskId : timedOutTasks) {
            reassignTask(taskId, "Timeout excedido o worker inactivo");
        }
    }
    
    /**
     * Reasigna una tarea a un nuevo worker
     * @param taskId ID de la tarea
     * @param reason Razón de la reasignación
     */
    private void reassignTask(String taskId, String reason) {
        Worker oldWorker = assignedTasks.remove(taskId);
        Task task = assignedTaskReferences.remove(taskId);
        if (oldWorker == null) {
            System.out.println("No se pudo reasignar tarea " + taskId + " - No está asignada");
            return;
        }
        
        // Liberar tarea del worker anterior
        resourceManager.releaseTaskFromWorker(oldWorker.getWorkerId(), taskId, 0, false);
        
        // Usar la tarea obtenida de assignedTaskReferences
        if (task == null) {
            System.out.println("No se pudo reasignar tarea " + taskId + " - Tarea no encontrada");
            totalTasksFailed++;
            return;
        }
        
        // Buscar nuevo worker disponible
        Worker newWorker = resourceManager.findBestAvailableWorker(
            task.getType().name(), 
            task.getPriority()
        );
        
        if (newWorker != null) {
            // Reasignar la tarea al nuevo worker
            if (assignTaskToWorker(task, newWorker)) {
                System.out.println("Tarea " + taskId + " reasignada de " + oldWorker.getWorkerId() + 
                                " a " + newWorker.getWorkerId() + " - Razón: " + reason);
            } else {
                System.out.println("No se pudo reasignar tarea " + taskId + " - Fallo en asignación");
                // Volver a agregar la tarea a la cola correspondiente
                addTask(task);
                totalTasksFailed++;
            }
        } else {
            System.out.println("No se pudo reasignar tarea " + taskId + " - No hay workers disponibles");
            // Volver a agregar la tarea a la cola correspondiente
            addTask(task);
            totalTasksFailed++;
        }
    }
    
    // MÉTODOS DE ESTADÍSTICAS
    
    /**
     * Obtiene estadísticas del scheduler
     * @return String con estadísticas detalladas
     */
    public String getSchedulerStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== ESTADÍSTICAS DEL SCHEDULER ===\n");
        stats.append("Tareas Map pendientes: ").append(mapTaskQueue.size()).append("\n");
        stats.append("Tareas Reduce pendientes: ").append(reduceTaskQueue.size()).append("\n");
        stats.append("Tareas asignadas: ").append(assignedTasks.size()).append("\n");
        stats.append("Tareas programadas: ").append(totalTasksScheduled).append("\n");
        stats.append("Tareas completadas: ").append(totalTasksCompleted).append("\n");
        stats.append("Tareas fallidas: ").append(totalTasksFailed).append("\n");
        
        return stats.toString();
    }
    
    /**
     * Obtiene el número total de tareas programadas
     * @return Número de tareas programadas
     */
    public int getTotalTasksScheduled() {
        return totalTasksScheduled;
    }
    
    /**
     * Obtiene el número total de tareas completadas
     * @return Número de tareas completadas
     */
    public int getTotalTasksCompleted() {
        return totalTasksCompleted;
    }
    
    /**
     * Obtiene el número total de tareas fallidas
     * @return Número de tareas fallidas
     */
    public int getTotalTasksFailed() {
        return totalTasksFailed;
    }
    
    /**
     * Marca una tarea como completada
     * @param taskId ID de la tarea
     * @param workerId ID del worker que completó la tarea
     * @return true si la tarea fue marcada como completada
     */
    public boolean markTaskCompleted(String taskId, String workerId) {
        try {
            Worker worker = assignedTasks.get(taskId);
            if (worker != null && worker.getWorkerId().equals(workerId)) {
                // Marcar tarea como completada
                Task task = assignedTaskReferences.get(taskId);
                if (task != null) {
                    task.setStatus(TaskStatus.COMPLETED);
                    task.setCompletedAt(java.time.LocalDateTime.now());
                }
                
                // Liberar worker
                resourceManager.markWorkerAvailable(workerId);
                
                // Remover de tareas asignadas
                assignedTasks.remove(taskId);
                assignedTaskReferences.remove(taskId);
                
                totalTasksCompleted++;
                
                System.out.println("✅ Tarea " + taskId + " completada por worker " + workerId);
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("❌ Error marcando tarea como completada: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Marca una tarea como fallida
     * @param taskId ID de la tarea
     * @param workerId ID del worker que falló
     * @param errorMessage Mensaje de error
     * @return true si la tarea fue marcada como fallida
     */
    public boolean markTaskFailed(String taskId, String workerId, String errorMessage) {
        try {
            Worker worker = assignedTasks.get(taskId);
            if (worker != null && worker.getWorkerId().equals(workerId)) {
                // Marcar tarea como fallida
                Task task = assignedTaskReferences.get(taskId);
                if (task != null) {
                    task.setStatus(TaskStatus.FAILED);
                    task.setErrorMessage(errorMessage);
                    task.setCompletedAt(java.time.LocalDateTime.now());
                }
                
                // Liberar worker
                resourceManager.markWorkerAvailable(workerId);
                
                // Remover de tareas asignadas
                assignedTasks.remove(taskId);
                assignedTaskReferences.remove(taskId);
                
                totalTasksFailed++;
                
                System.out.println("❌ Tarea " + taskId + " falló en worker " + workerId + ": " + errorMessage);
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("❌ Error marcando tarea como fallida: " + e.getMessage());
            return false;
        }
    }
}