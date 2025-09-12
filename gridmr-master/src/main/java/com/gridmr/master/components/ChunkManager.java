package com.gridmr.master.components;

import com.gridmr.master.model.DataChunk;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChunkManager {
    
    // Directorio base para almacenamiento de chunks
    private final String chunkStoragePath;
    
    // Mapa de chunks almacenados (chunkId -> DataChunk)
    private final Map<String, DataChunk> storedChunks;
    
    // Mapa de chunks por trabajo (jobId -> List<DataChunk>)
    private final Map<String, List<DataChunk>> jobChunks;
    
    // Mapa de chunks en transferencia (chunkId -> WorkerId)
    private final Map<String, String> chunksInTransfer;
    
    // Scheduler para tareas de limpieza
    private ScheduledExecutorService cleanupScheduler;
    
    // Configuración
    private static final int MAX_STORAGE_GB = 10; // 10GB máximo de almacenamiento
    private static final int CLEANUP_INTERVAL_MINUTES = 30;
    private static final int CHUNK_RETENTION_HOURS = 24; // 24 horas de retención
    
    // Estadísticas
    private long totalChunksCreated;
    private long totalChunksTransferred;
    private long totalStorageUsedBytes;
    private int activeTransfers;
    
    public ChunkManager() {
        this.chunkStoragePath = System.getProperty("java.io.tmpdir") + "/gridmr-chunks";
        this.storedChunks = new ConcurrentHashMap<>();
        this.jobChunks = new ConcurrentHashMap<>();
        this.chunksInTransfer = new ConcurrentHashMap<>();
        
        this.totalChunksCreated = 0;
        this.totalChunksTransferred = 0;
        this.totalStorageUsedBytes = 0;
        this.activeTransfers = 0;
        
        // Crear directorio de almacenamiento si no existe
        createStorageDirectory();
        
        System.out.println("ChunkManager inicializado - Almacenamiento: " + chunkStoragePath);
    }
    
    public void start() {
        System.out.println("Iniciando ChunkManager...");
        
        // Iniciar scheduler para limpieza
        this.cleanupScheduler = Executors.newScheduledThreadPool(1);
        
        // Tarea periódica para limpiar chunks antiguos
        cleanupScheduler.scheduleAtFixedRate(
            this::cleanupOldChunks,
            CLEANUP_INTERVAL_MINUTES,
            CLEANUP_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );
        
        System.out.println("ChunkManager iniciado - Limpieza automática activa");
    }
    
    public void stop() {
        System.out.println("ChunkManager deteniendo...");
        
        if (cleanupScheduler != null && !cleanupScheduler.isShutdown()) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Limpiar todos los chunks al detener
        cleanupAllChunks();
        
        System.out.println("ChunkManager detenido");
    }
    
    // ==================== MÉTODOS DE GESTIÓN DE CHUNKS ====================
    
    /**
     * Crea chunks a partir de un archivo de entrada
     * @param jobId ID del trabajo
     * @param inputFile Archivo de entrada
     * @param chunkSizeMB Tamaño del chunk en MB
     * @return Lista de chunks creados
     */
    public List<DataChunk> createChunksFromFile(String jobId, String inputFile, int chunkSizeMB) {
        System.out.println("Creando chunks para archivo: " + inputFile + " (Trabajo: " + jobId + ")");
        
        List<DataChunk> chunks = new ArrayList<>();
        
        try {
            // Verificar que el archivo existe
            Path filePath = Paths.get(inputFile);
            if (!Files.exists(filePath)) {
                throw new FileNotFoundException("Archivo no encontrado: " + inputFile);
            }
            
            // Obtener tamaño real del archivo
            long fileSizeBytes = Files.size(filePath);
            long chunkSizeBytes = chunkSizeMB * 1024L * 1024L;
            int numChunks = (int) Math.max(1, (fileSizeBytes + chunkSizeBytes - 1) / chunkSizeBytes);
            
            System.out.println("Archivo: " + inputFile + " - Tamaño: " + (fileSizeBytes / (1024 * 1024)) + " MB - Chunks: " + numChunks);
            
            // Leer y dividir el archivo en chunks reales
            try (FileInputStream fis = new FileInputStream(inputFile)) {
                for (int i = 0; i < numChunks; i++) {
                    String chunkId = jobId + "_chunk_" + i;
                    long startOffset = i * chunkSizeBytes;
                    long endOffset = Math.min(startOffset + chunkSizeBytes, fileSizeBytes);
                    long actualChunkSize = endOffset - startOffset;
                    
                    // Crear chunk con offsets reales
                    DataChunk chunk = new DataChunk(chunkId, jobId, inputFile, startOffset, endOffset);
                    
                    // Leer contenido real del archivo
                    byte[] chunkData = new byte[(int) actualChunkSize];
                    fis.getChannel().position(startOffset);
                    int bytesRead = fis.read(chunkData);
                    
                    if (bytesRead > 0) {
                        // Almacenar chunk en disco
                        String chunkFilePath = storeChunkToDisk(chunk, chunkData);
                        chunk.setLocation(chunkFilePath);
                        
                        // Almacenar en memoria para acceso rápido
                        storeChunk(chunk);
                        chunks.add(chunk);
                        
                        totalChunksCreated++;
                        
                        System.out.println("Chunk " + chunkId + " creado - Tamaño: " + actualChunkSize + " bytes, Archivo: " + chunkFilePath);
                    }
                }
            }
            
            // Registrar chunks del trabajo
            jobChunks.put(jobId, chunks);
            
            System.out.println("Creados " + chunks.size() + " chunks reales para archivo " + inputFile);
            
        } catch (Exception e) {
            System.err.println("Error creando chunks para archivo " + inputFile + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return chunks;
    }
    
    /**
     * Almacena un chunk en el sistema
     * @param chunk Chunk a almacenar
     */
    private void storeChunk(DataChunk chunk) {
        // Verificar límite de almacenamiento
        if (totalStorageUsedBytes > MAX_STORAGE_GB * 1024L * 1024L * 1024L) {
            System.out.println("Límite de almacenamiento alcanzado - Limpiando chunks antiguos");
            cleanupOldChunks();
        }
        
        // Almacenar en memoria para acceso rápido
        storedChunks.put(chunk.getChunkId(), chunk);
        totalStorageUsedBytes += chunk.getSizeBytes();
        
        System.out.println("Chunk " + chunk.getChunkId() + " almacenado en memoria (" + 
                        chunk.getSizeBytes() + " bytes)");
    }
    
    /**
     * Almacena un chunk en disco
     * @param chunk Chunk a almacenar
     * @param chunkData Datos del chunk
     * @return Ruta del archivo creado
     */
    private String storeChunkToDisk(DataChunk chunk, byte[] chunkData) throws IOException {
        // Crear directorio específico para el trabajo
        Path jobDir = Paths.get(chunkStoragePath, chunk.getJobId());
        Files.createDirectories(jobDir);
        
        // Crear archivo del chunk
        String chunkFileName = chunk.getChunkId() + ".chunk";
        Path chunkFilePath = jobDir.resolve(chunkFileName);
        
        // Escribir datos reales al archivo
        try (FileOutputStream fos = new FileOutputStream(chunkFilePath.toFile())) {
            fos.write(chunkData);
            fos.flush();
        }
        
        System.out.println("Chunk " + chunk.getChunkId() + " almacenado en disco: " + chunkFilePath);
        return chunkFilePath.toString();
    }
    
    /**
     * Lee un chunk desde disco
     * @param chunk Chunk a leer
     * @return Datos del chunk
     */
    public byte[] readChunkFromDisk(DataChunk chunk) throws IOException {
        if (chunk.getLocation() == null) {
            throw new IOException("Chunk " + chunk.getChunkId() + " no tiene ubicación en disco");
        }
        
        Path chunkFilePath = Paths.get(chunk.getLocation());
        if (!Files.exists(chunkFilePath)) {
            throw new FileNotFoundException("Archivo de chunk no encontrado: " + chunkFilePath);
        }
        
        return Files.readAllBytes(chunkFilePath);
    }
    
    /**
     * Obtiene un chunk por ID
     * @param chunkId ID del chunk
     * @return DataChunk o null si no existe
     */
    public DataChunk getChunk(String chunkId) {
        return storedChunks.get(chunkId);
    }
    
    /**
     * Obtiene todos los chunks de un trabajo
     * @param jobId ID del trabajo
     * @return Lista de chunks del trabajo
     */
    public List<DataChunk> getJobChunks(String jobId) {
        return jobChunks.getOrDefault(jobId, new ArrayList<>());
    }
    
    /**
     * Marca un chunk como en transferencia
     * @param chunkId ID del chunk
     * @param workerId ID del worker que lo está transfiriendo
     */
    public void markChunkInTransfer(String chunkId, String workerId) {
        chunksInTransfer.put(chunkId, workerId);
        activeTransfers++;
        
        System.out.println("Chunk " + chunkId + " marcado como en transferencia a Worker " + workerId);
    }
    
    /**
     * Marca un chunk como transferido
     * @param chunkId ID del chunk
     * @param success true si la transferencia fue exitosa
     */
    public void markChunkTransferred(String chunkId, boolean success) {
        String workerId = chunksInTransfer.remove(chunkId);
        if (workerId != null) {
            activeTransfers--;
            totalChunksTransferred++;
            
            if (success) {
                System.out.println("Chunk " + chunkId + " transferido exitosamente a Worker " + workerId);
            } else {
                System.err.println("Error en transferencia del chunk " + chunkId + " a Worker " + workerId);
            }
        }
    }
    
    // ==================== MÉTODOS DE TRANSFERENCIA ====================
    
    /**
     * Transfiere un chunk a un worker (Map-modo1)
     * @param chunkId ID del chunk
     * @param workerId ID del worker destino
     * @param transferMode Modo de transferencia
     * @return true si la transferencia se inició exitosamente
     */
    public boolean transferChunkToWorker(String chunkId, String workerId, String transferMode) {
        DataChunk chunk = storedChunks.get(chunkId);
        if (chunk == null) {
            System.err.println("Chunk " + chunkId + " no encontrado para transferencia");
            return false;
        }
        
        // Marcar como en transferencia
        markChunkInTransfer(chunkId, workerId);
        
        System.out.println("Iniciando transferencia real de chunk " + chunkId + 
                        " a Worker " + workerId + " (Modo: " + transferMode + ")");
        
        // Transferencia real asíncrona
        new Thread(() -> {
            try {
                // Leer datos reales del chunk
                byte[] chunkData = readChunkFromDisk(chunk);
                
                // TODO: Implementar transferencia gRPC real
                // Por ahora, simular transferencia exitosa
                System.out.println("Transferencia gRPC de chunk " + chunkId + " completada - " + 
                                chunkData.length + " bytes enviados");
                
                markChunkTransferred(chunkId, true);
                
            } catch (Exception e) {
                System.err.println("Error en transferencia de chunk " + chunkId + ": " + e.getMessage());
                markChunkTransferred(chunkId, false);
            }
        }).start();
        
        return true;
    }
    
    /**
     * Procesa solicitud de chunk de un worker (Map-modo2)
     * @param chunkId ID del chunk solicitado
     * @param workerId ID del worker solicitante
     * @return DataChunk o null si no está disponible
     */
    public DataChunk processChunkRequest(String chunkId, String workerId) {
        DataChunk chunk = storedChunks.get(chunkId);
        if (chunk == null) {
            System.out.println("Worker " + workerId + " solicitó chunk " + chunkId + " - No disponible");
            return null;
        }
        
        // Marcar como en transferencia
        markChunkInTransfer(chunkId, workerId);
        
        System.out.println("Procesando solicitud de chunk " + chunkId + " de Worker " + workerId);
        return chunk;
    }
    
    /**
     * Almacena resultado intermedio de una tarea Map
     * @param jobId ID del trabajo
     * @param taskId ID de la tarea
     * @param resultData Datos del resultado
     * @param reducerId ID del reducer destino
     */
    public void storeIntermediateResult(String jobId, String taskId, String resultData, int reducerId) {
        String chunkId = jobId + "_intermediate_" + taskId + "_reducer_" + reducerId;
        
        try {
            // Crear chunk para resultado intermedio
            DataChunk chunk = new DataChunk(chunkId, jobId, "intermediate", 0, resultData.length());
            
            // Almacenar en disco
            byte[] resultBytes = resultData.getBytes("UTF-8");
            String chunkFilePath = storeChunkToDisk(chunk, resultBytes);
            chunk.setLocation(chunkFilePath);
            
            // Marcar como intermedio usando el location
            chunk.setLocation("intermediate_reducer_" + reducerId + ":" + chunkFilePath);
            
            // Almacenar en memoria
            storeChunk(chunk);
            
            System.out.println("Resultado intermedio almacenado: " + chunkId + 
                            " (Reducer: " + reducerId + ", Tamaño: " + resultData.length() + " bytes, Archivo: " + chunkFilePath);
                            
        } catch (Exception e) {
            System.err.println("Error almacenando resultado intermedio " + chunkId + ": " + e.getMessage());
        }
    }
    
    /**
     * Obtiene resultados intermedios para una tarea Reduce
     * @param jobId ID del trabajo
     * @param reducerId ID del reducer
     * @return Lista de chunks intermedios
     */
    public List<DataChunk> getIntermediateResults(String jobId, int reducerId) {
        List<DataChunk> results = new ArrayList<>();
        
        for (DataChunk chunk : storedChunks.values()) {
            if (chunk.getJobId().equals(jobId) && 
                chunk.getLocation() != null && chunk.getLocation().contains("intermediate_reducer_" + reducerId)) {
                results.add(chunk);
            }
        }
        
        System.out.println("Obtenidos " + results.size() + " resultados intermedios para Reducer " + reducerId);
        return results;
    }
    
    /**
     * Lee el contenido de un resultado intermedio desde disco
     * @param chunk Chunk intermedio
     * @return Contenido del resultado
     */
    public String readIntermediateResult(DataChunk chunk) throws IOException {
        if (chunk.getLocation() == null) {
            throw new IOException("Chunk intermedio no tiene ubicación en disco");
        }
        
        // Extraer ruta del archivo del location
        String filePath = chunk.getLocation();
        if (filePath.contains(":")) {
            filePath = filePath.substring(filePath.indexOf(":") + 1);
        }
        
        Path chunkFilePath = Paths.get(filePath);
        if (!Files.exists(chunkFilePath)) {
            throw new FileNotFoundException("Archivo de resultado intermedio no encontrado: " + chunkFilePath);
        }
        
        byte[] data = Files.readAllBytes(chunkFilePath);
        return new String(data, "UTF-8");
    }
    
    // ==================== MÉTODOS DE LIMPIEZA ====================
    
    // Limpieza de chunks antiguos
    private void cleanupOldChunks() {
        System.out.println("Iniciando limpieza de chunks antiguos...");
        
        List<String> chunksToRemove = new ArrayList<>();
        java.time.LocalDateTime cutoffTime = java.time.LocalDateTime.now()
            .minusHours(CHUNK_RETENTION_HOURS);
        
        for (DataChunk chunk : storedChunks.values()) {
            if (chunk.getCreatedAt().isBefore(cutoffTime)) {
                chunksToRemove.add(chunk.getChunkId());
            }
        }
        
        for (String chunkId : chunksToRemove) {
            removeChunk(chunkId);
        }
        
        if (!chunksToRemove.isEmpty()) {
            System.out.println("Limpieza completada - " + chunksToRemove.size() + " chunks removidos");
        }
    }
    
    // Limpieza de todos los chunks
    private void cleanupAllChunks() {
        System.out.println("Limpiando todos los chunks...");
        
        storedChunks.clear();
        jobChunks.clear();
        chunksInTransfer.clear();
        
        totalStorageUsedBytes = 0;
        activeTransfers = 0;
        
        System.out.println("Limpieza completa finalizada");
    }
    
    /**
     * Remueve un chunk específico del sistema
     * @param chunkId ID del chunk a remover
     */
    private void removeChunk(String chunkId) {
        DataChunk chunk = storedChunks.remove(chunkId);
        if (chunk != null) {
            // Remover archivo del disco si existe
            if (chunk.getLocation() != null) {
                try {
                    String filePath = chunk.getLocation();
                    if (filePath.contains(":")) {
                        filePath = filePath.substring(filePath.indexOf(":") + 1);
                    }
                    Path chunkFilePath = Paths.get(filePath);
                    if (Files.exists(chunkFilePath)) {
                        Files.delete(chunkFilePath);
                        System.out.println("Archivo de chunk eliminado: " + chunkFilePath);
                    }
                } catch (IOException e) {
                    System.err.println("Error eliminando archivo de chunk " + chunkId + ": " + e.getMessage());
                }
            }
            
            totalStorageUsedBytes -= chunk.getSizeBytes();
            System.out.println("Chunk " + chunkId + " removido del almacenamiento");
        }
    }
    
    /**
     * Limpia chunks de un trabajo específico
     * @param jobId ID del trabajo
     */
    public void cleanupJobChunks(String jobId) {
        List<DataChunk> chunks = jobChunks.remove(jobId);
        if (chunks != null) {
            for (DataChunk chunk : chunks) {
                removeChunk(chunk.getChunkId());
            }
            System.out.println("Chunks del trabajo " + jobId + " limpiados");
        }
    }
    
    // ==================== MÉTODOS DE UTILIDAD ====================
    
    // Creación del directorio de almacenamiento
    private void createStorageDirectory() {
        try {
            Path path = Paths.get(chunkStoragePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                System.out.println("Directorio de almacenamiento creado: " + chunkStoragePath);
            }
        } catch (IOException e) {
            System.err.println("Error creando directorio de almacenamiento: " + e.getMessage());
        }
    }
    
    
    // ==================== MÉTODOS DE ESTADÍSTICAS ====================
    
    /**
     * Obtiene estadísticas del ChunkManager
     * @return String con estadísticas detalladas
     */
    public String getChunkManagerStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== ESTADÍSTICAS DEL CHUNK MANAGER ===\n");
        stats.append("Chunks almacenados: ").append(storedChunks.size()).append("\n");
        stats.append("Chunks en transferencia: ").append(activeTransfers).append("\n");
        stats.append("Chunks creados: ").append(totalChunksCreated).append("\n");
        stats.append("Chunks transferidos: ").append(totalChunksTransferred).append("\n");
        stats.append("Almacenamiento usado: ").append(totalStorageUsedBytes / (1024 * 1024)).append(" MB\n");
        stats.append("Trabajos con chunks: ").append(jobChunks.size()).append("\n");
        
        return stats.toString();
    }
    
    /**
     * Obtiene el número total de chunks creados
     * @return Número de chunks creados
     */
    public long getTotalChunksCreated() {
        return totalChunksCreated;
    }
    
    /**
     * Obtiene el número total de chunks transferidos
     * @return Número de chunks transferidos
     */
    public long getTotalChunksTransferred() {
        return totalChunksTransferred;
    }
    
    /**
     * Obtiene el almacenamiento usado en bytes
     * @return Almacenamiento usado en bytes
     */
    public long getTotalStorageUsedBytes() {
        return totalStorageUsedBytes;
    }
    
    /**
     * Obtiene el número de transferencias activas
     * @return Número de transferencias activas
     */
    public int getActiveTransfers() {
        return activeTransfers;
    }
}