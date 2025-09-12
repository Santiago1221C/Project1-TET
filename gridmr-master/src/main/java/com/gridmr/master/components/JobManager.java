package com.gridmr.master.components;

import com.gridmr.master.model.Job;
import com.gridmr.master.model.JobStatus;
import com.gridmr.master.model.Task;
import com.gridmr.master.model.TaskType;
import com.gridmr.master.model.TaskStatus;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class JobManager {
    
    // Referencia al Scheduler para enviar tareas
    private Scheduler scheduler;
    
    // Mapa de trabajos activos (jobId -> Job)
    private final Map<String, Job> activeJobs;
    
    // Mapa de tareas por trabajo (jobId -> List<Task>)
    private final Map<String, List<Task>> jobTasks;
    
    // Scheduler para tareas periódicas (monitoreo de progreso)
    private ScheduledExecutorService progressScheduler;
    
    // Configuración
    private static final int PROGRESS_CHECK_INTERVAL_SECONDS = 5;
    private static final int DEFAULT_CHUNK_SIZE_MB = 64; // 64MB por chunk
    private static final int MAX_CONCURRENT_JOBS = 10;
    
    // Estadísticas
    private int totalJobsSubmitted;
    private int totalJobsCompleted;
    private int totalJobsFailed;
    
    public JobManager(Scheduler scheduler) {
        this.scheduler = scheduler;
        this.activeJobs = new ConcurrentHashMap<>();
        this.jobTasks = new ConcurrentHashMap<>();
        
        this.totalJobsSubmitted = 0;
        this.totalJobsCompleted = 0;
        this.totalJobsFailed = 0;
        
        System.out.println("JobManager inicializado");
    }
    
    public void start() {
        System.out.println("JobManager iniciando...");
        
        // Iniciar scheduler para tareas periódicas
        this.progressScheduler = Executors.newScheduledThreadPool(2);
        
        // Tarea periódica para verificar progreso de trabajos
        progressScheduler.scheduleAtFixedRate(
            this::checkJobProgress,
            PROGRESS_CHECK_INTERVAL_SECONDS,
            PROGRESS_CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        System.out.println("JobManager iniciado - Monitoreo de trabajos activo");
    }
    
    public void stop() {
        System.out.println("JobManager deteniendo...");
        
        if (progressScheduler != null && !progressScheduler.isShutdown()) {
            progressScheduler.shutdown();
            try {
                if (!progressScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    progressScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                progressScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.println("JobManager detenido");
    }
    
    // ==================== MÉTODOS DE GESTIÓN DE TRABAJOS ====================
    
    /**
     * Envía un nuevo trabajo MapReduce al sistema
     * @param job Trabajo a procesar
     * @return true si el trabajo se envió exitosamente
     */
    public boolean submitJob(Job job) {
        if (job == null) {
            System.out.println("Error: Trabajo nulo no puede ser enviado");
            return false;
        }
        
        // Verificar límite de trabajos concurrentes
        if (activeJobs.size() >= MAX_CONCURRENT_JOBS) {
            System.out.println("Error: Límite de trabajos concurrentes alcanzado (" + MAX_CONCURRENT_JOBS + ")");
            return false;
        }
        
        // Verificar que el trabajo tenga archivos de entrada
        if (job.getInputFiles().isEmpty()) {
            System.out.println("Error: Trabajo " + job.getJobId() + " no tiene archivos de entrada");
            return false;
        }
        
        // Configurar trabajo
        job.setStatus(JobStatus.PENDING);
        // El createdAt se establece en el constructor de Job
        
        // Registrar trabajo
        activeJobs.put(job.getJobId(), job);
        jobTasks.put(job.getJobId(), new ArrayList<>());
        
        // Actualizar estadísticas
        totalJobsSubmitted++;
        
        System.out.println("Trabajo " + job.getJobId() + " enviado - Archivos: " + 
                        job.getInputFiles().size() + ", Mappers: " + job.getNumMappers() + 
                        ", Reducers: " + job.getNumReducers());
        
        // Iniciar procesamiento del trabajo
        processJob(job);
        
        return true;
    }
    
    /**
     * Obtiene el estado de un trabajo
     * @param jobId ID del trabajo
     * @return Job o null si no existe
     */
    public Job getJob(String jobId) {
        return activeJobs.get(jobId);
    }
    
    /**
     * Obtiene todos los trabajos activos
     * @return Lista de trabajos activos
     */
    public List<Job> getActiveJobs() {
        return new ArrayList<>(activeJobs.values());
    }
    
    /**
     * Cancela un trabajo
     * @param jobId ID del trabajo
     * @param reason Razón de la cancelación
     * @return true si el trabajo se canceló exitosamente
     */
    public boolean cancelJob(String jobId, String reason) {
        Job job = activeJobs.get(jobId);
        if (job == null) {
            System.out.println("Trabajo " + jobId + " no encontrado");
            return false;
        }
        
        // Cambiar estado a cancelado
        job.setStatus(JobStatus.CANCELLED);
        
        // TODO: Cancelar tareas pendientes en el Scheduler
        
        System.out.println("Trabajo " + jobId + " cancelado - Razón: " + reason);
        return true;
    }
    
    // ==================== MÉTODOS DE PROCESAMIENTO DE TRABAJOS ====================
    
    /**
     * Procesa un trabajo MapReduce completo
     * @param job Trabajo a procesar
     */
    private void processJob(Job job) {
        System.out.println("Iniciando procesamiento del trabajo " + job.getJobId());
        
        try {
            // Fase 1: Crear y enviar tareas Map
            createAndSubmitMapTasks(job);
            
            // El progreso se monitoreará en checkJobProgress()
            
        } catch (Exception e) {
            System.err.println("Error procesando trabajo " + job.getJobId() + ": " + e.getMessage());
            job.setStatus(JobStatus.FAILED);
            totalJobsFailed++;
        }
    }
    
    /**
     * Crea y envía tareas Map para un trabajo
     * @param job Trabajo del cual crear tareas Map
     */
    private void createAndSubmitMapTasks(Job job) {
        System.out.println("Creando tareas Map para trabajo " + job.getJobId());
        
        // Cambiar estado a fase Map
        job.setStatus(JobStatus.MAP_PHASE);
        job.setStartedAt(java.time.LocalDateTime.now());
        
        List<Task> mapTasks = new ArrayList<>();
        int taskIndex = 0;
        
        // Crear tareas Map para cada archivo de entrada
        for (String inputFile : job.getInputFiles()) {
            // Simular división en chunks (en un sistema real, esto sería más complejo)
            int numChunks = calculateNumberOfChunks(inputFile, DEFAULT_CHUNK_SIZE_MB);
            
            for (int chunkIndex = 0; chunkIndex < numChunks; chunkIndex++) {
                String taskId = job.getJobId() + "_map_" + taskIndex;
                
                // Crear tarea Map
                Task mapTask = new Task(taskId, job.getJobId(), TaskType.MAP);
                mapTask.setInputData(inputFile + "_chunk_" + chunkIndex);
                mapTask.setFunctionCode(job.getMapFunction());
                mapTask.setPriority(5); // Prioridad media para tareas Map
                
                // Agregar a la lista de tareas del trabajo
                mapTasks.add(mapTask);
                job.addMapTask(mapTask);
                
                // Enviar tarea al Scheduler
                if (scheduler.addTask(mapTask)) {
                    System.out.println("Tarea Map " + taskId + " enviada al Scheduler");
                } else {
                    System.err.println("Error enviando tarea Map " + taskId + " al Scheduler");
                }
                
                taskIndex++;
            }
        }
        
        // Registrar tareas del trabajo
        jobTasks.put(job.getJobId(), mapTasks);
        
        System.out.println("Creadas " + mapTasks.size() + " tareas Map para trabajo " + job.getJobId());
    }
    
    /**
     * Calcula el número de chunks para un archivo
     * @param fileName Nombre del archivo
     * @param chunkSizeMB Tamaño del chunk en MB
     * @return Número de chunks
     */
    private int calculateNumberOfChunks(String fileName, int chunkSizeMB) {
        // Simulación: asumir que cada archivo tiene 100MB por defecto
        // En un sistema real, esto consultaría el tamaño real del archivo
        int fileSizeMB = 100;
        return Math.max(1, (fileSizeMB + chunkSizeMB - 1) / chunkSizeMB);
    }
    
    /**
     * Crea y envía tareas Reduce para un trabajo
     * @param job Trabajo del cual crear tareas Reduce
     */
    private void createAndSubmitReduceTasks(Job job) {
        System.out.println("Creando tareas Reduce para trabajo " + job.getJobId());
        
        // Cambiar estado a fase Reduce
        job.setStatus(JobStatus.REDUCE_PHASE);
        
        List<Task> reduceTasks = new ArrayList<>();
        
        // Crear tareas Reduce (una por cada reducer configurado)
        for (int i = 0; i < job.getNumReducers(); i++) {
            String taskId = job.getJobId() + "_reduce_" + i;
            
            // Crear tarea Reduce
            Task reduceTask = new Task(taskId, job.getJobId(), TaskType.REDUCE);
            reduceTask.setInputData("intermediate_results_reducer_" + i);
            reduceTask.setFunctionCode(job.getReduceFunction());
            reduceTask.setPriority(3); // Prioridad menor para tareas Reduce
            
            // Agregar a la lista de tareas del trabajo
            reduceTasks.add(reduceTask);
            job.addReduceTask(reduceTask);
            
            // Enviar tarea al Scheduler
            if (scheduler.addTask(reduceTask)) {
                System.out.println("Tarea Reduce " + taskId + " enviada al Scheduler");
            } else {
                System.err.println("Error enviando tarea Reduce " + taskId + " al Scheduler");
            }
        }
        
        // Agregar tareas Reduce a las existentes
        List<Task> allTasks = jobTasks.get(job.getJobId());
        allTasks.addAll(reduceTasks);
        
        System.out.println("Creadas " + reduceTasks.size() + " tareas Reduce para trabajo " + job.getJobId());
    }
    
    // ==================== MÉTODOS DE MONITOREO ====================
    
    /**
     * Verifica el progreso de todos los trabajos activos
     * Se ejecuta periódicamente
     */
    private void checkJobProgress() {
        List<String> jobsToComplete = new ArrayList<>();
        
        for (Job job : activeJobs.values()) {
            if (job.getStatus() == JobStatus.MAP_PHASE) {
                checkMapPhaseProgress(job);
            } else if (job.getStatus() == JobStatus.REDUCE_PHASE) {
                checkReducePhaseProgress(job);
            }
            
            // Verificar si el trabajo está completado
            if (job.getStatus() == JobStatus.COMPLETED || 
                job.getStatus() == JobStatus.FAILED || 
                job.getStatus() == JobStatus.CANCELLED) {
                jobsToComplete.add(job.getJobId());
            }
        }
        
        // Completar trabajos terminados
        for (String jobId : jobsToComplete) {
            completeJob(jobId);
        }
    }
    
    /**
     * Verifica el progreso de la fase Map de un trabajo
     * @param job Trabajo a verificar
     */
    private void checkMapPhaseProgress(Job job) {
        List<Task> mapTasks = job.getMapTasks();
        int completedTasks = 0;
        int failedTasks = 0;
        
        for (Task task : mapTasks) {
            if (task.getStatus() == TaskStatus.COMPLETED) {
                completedTasks++;
            } else if (task.getStatus() == TaskStatus.FAILED) {
                failedTasks++;
            }
        }
        
        // Si todas las tareas Map están completadas, iniciar fase Reduce
        if (completedTasks == mapTasks.size()) {
            System.out.println("Fase Map completada para trabajo " + job.getJobId() + 
                            " - Iniciando fase Reduce");
            createAndSubmitReduceTasks(job);
        } else if (failedTasks > 0) {
            System.out.println("Trabajo " + job.getJobId() + " falló en fase Map - " + 
                            failedTasks + " tareas fallidas");
            job.setStatus(JobStatus.FAILED);
        }
    }
    
    /**
     * Verifica el progreso de la fase Reduce de un trabajo
     * @param job Trabajo a verificar
     */
    private void checkReducePhaseProgress(Job job) {
        List<Task> reduceTasks = job.getReduceTasks();
        int completedTasks = 0;
        int failedTasks = 0;
        
        for (Task task : reduceTasks) {
            if (task.getStatus() == TaskStatus.COMPLETED) {
                completedTasks++;
            } else if (task.getStatus() == TaskStatus.FAILED) {
                failedTasks++;
            }
        }
        
        // Si todas las tareas Reduce están completadas, marcar trabajo como completado
        if (completedTasks == reduceTasks.size()) {
            System.out.println("Fase Reduce completada para trabajo " + job.getJobId());
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(java.time.LocalDateTime.now());
        } else if (failedTasks > 0) {
            System.out.println("Trabajo " + job.getJobId() + " falló en fase Reduce - " + 
                            failedTasks + " tareas fallidas");
            job.setStatus(JobStatus.FAILED);
        }
    }
    
    /**
     * Completa un trabajo y limpia recursos
     * @param jobId ID del trabajo
     */
    private void completeJob(String jobId) {
        Job job = activeJobs.remove(jobId);
        jobTasks.remove(jobId);
        
        if (job != null) {
            if (job.getStatus() == JobStatus.COMPLETED) {
                totalJobsCompleted++;
                System.out.println("Trabajo " + jobId + " completado exitosamente");
            } else {
                totalJobsFailed++;
                System.out.println("Trabajo " + jobId + " terminado con estado: " + job.getStatus());
            }
        }
    }
    
    // ==================== MÉTODOS DE ESTADÍSTICAS ====================
    
    /**
     * Obtiene estadísticas del JobManager
     * @return String con estadísticas detalladas
     */
    public String getJobManagerStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== ESTADÍSTICAS DEL JOB MANAGER ===\n");
        stats.append("Trabajos activos: ").append(activeJobs.size()).append("\n");
        stats.append("Trabajos enviados: ").append(totalJobsSubmitted).append("\n");
        stats.append("Trabajos completados: ").append(totalJobsCompleted).append("\n");
        stats.append("Trabajos fallidos: ").append(totalJobsFailed).append("\n");
        
        // Estadísticas por trabajo
        stats.append("\n=== TRABAJOS ACTIVOS ===\n");
        for (Job job : activeJobs.values()) {
            stats.append("Trabajo ").append(job.getJobId())
                .append(" - Estado: ").append(job.getStatus())
                .append(", Tareas Map: ").append(job.getMapTasks().size())
                .append(", Tareas Reduce: ").append(job.getReduceTasks().size())
                .append("\n");
        }
        
        return stats.toString();
    }
    
    /**
     * Obtiene el número total de trabajos enviados
     * @return Número de trabajos enviados
     */
    public int getTotalJobsSubmitted() {
        return totalJobsSubmitted;
    }
    
    /**
     * Obtiene el número total de trabajos completados
     * @return Número de trabajos completados
     */
    public int getTotalJobsCompleted() {
        return totalJobsCompleted;
    }
    
    /**
     * Obtiene el número total de trabajos fallidos
     * @return Número de trabajos fallidos
     */
    public int getTotalJobsFailed() {
        return totalJobsFailed;
    }
}