package com.gridmr.master.components;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DomainAuthentication {
    
    // Mapa de workers autenticados (workerId -> SessionInfo)
    private final Map<String, SessionInfo> authenticatedWorkers;
    
    // Mapa de tokens activos (token -> workerId)
    private final Map<String, String> activeTokens;
    
    // Configuración de seguridad
    private static final String SECRET_KEY = "GridMR-Secret-Key-2024";
    private static final int TOKEN_EXPIRY_HOURS = 24;
    private static final int MAX_LOGIN_ATTEMPTS = 3;
    private static final int LOCKOUT_DURATION_MINUTES = 30;
    
    // Mapa de intentos de login fallidos (workerId -> LoginAttempts)
    private final Map<String, LoginAttempts> failedAttempts;
    
    // Estadísticas
    private int totalAuthentications;
    private int failedAuthentications;
    private int activeSessions;
    
    public DomainAuthentication() {
        this.authenticatedWorkers = new ConcurrentHashMap<>();
        this.activeTokens = new ConcurrentHashMap<>();
        this.failedAttempts = new ConcurrentHashMap<>();
        
        this.totalAuthentications = 0;
        this.failedAuthentications = 0;
        this.activeSessions = 0;
        
        System.out.println("DomainAuthentication inicializado");
    }
    
    public void start() {
        System.out.println("Iniciando DomainAuthentication");
        
        // Iniciar limpieza periódica de sesiones expiradas
        Timer cleanupTimer = new Timer(true);
        cleanupTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                cleanupExpiredSessions();
            }
        }, 0, 60000); // Cada minuto
        
        System.out.println("DomainAuthentication iniciado - Limpieza automática activa");
    }
    
    public void stop() {
        System.out.println("Deteniendo DomainAuthentication");
        
        // Limpiar todas las sesiones
        authenticatedWorkers.clear();
        activeTokens.clear();
        failedAttempts.clear();
        
        System.out.println("DomainAuthentication detenido");
    }
    
    // ==================== MÉTODOS DE AUTENTICACIÓN ====================
    
    /**
     * Autentica un worker con credenciales
     * @param workerId ID del worker
     * @param password Contraseña del worker
     * @param workerHost Host del worker
     * @return Token de autenticación o null si falla
     */
    public String authenticateWorker(String workerId, String password, String workerHost) {
        System.out.println("Intentando autenticar worker: " + workerId + " desde " + workerHost);
        
        // Verificar si el worker está bloqueado
        if (isWorkerLocked(workerId)) {
            System.out.println("Worker " + workerId + " está bloqueado por intentos fallidos");
            failedAuthentications++;
            return null;
        }
        
        // Verificar credenciales (en un sistema real, esto consultaría una base de datos)
        if (validateCredentials(workerId, password)) {
            // Generar token de autenticación
            String token = generateAuthToken(workerId, workerHost);
            
            // Crear sesión
            SessionInfo session = new SessionInfo(workerId, workerHost, token, LocalDateTime.now());
            authenticatedWorkers.put(workerId, session);
            activeTokens.put(token, workerId);
            
            // Limpiar intentos fallidos
            failedAttempts.remove(workerId);
            
            totalAuthentications++;
            activeSessions++;
            
            System.out.println("Worker " + workerId + " autenticado exitosamente - Token: " + token.substring(0, 8) + "...");
            return token;
        } else {
            // Registrar intento fallido
            recordFailedAttempt(workerId);
            failedAuthentications++;
            
            System.out.println("Autenticación fallida para worker: " + workerId);
            return null;
        }
    }
    
    /**
     * Valida un token de autenticación
     * @param token Token a validar
     * @return true si el token es válido
     */
    public boolean validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        
        String workerId = activeTokens.get(token);
        if (workerId == null) {
            return false;
        }
        
        SessionInfo session = authenticatedWorkers.get(workerId);
        if (session == null || !session.getToken().equals(token)) {
            return false;
        }
        
        // Verificar si la sesión ha expirado
        if (session.isExpired(TOKEN_EXPIRY_HOURS)) {
            logoutWorker(workerId);
            return false;
        }
        
        // Actualizar último acceso
        session.updateLastAccess();
        return true;
    }
    
    /**
     * Obtiene información de sesión de un worker
     * @param workerId ID del worker
     * @return SessionInfo o null si no está autenticado
     */
    public SessionInfo getWorkerSession(String workerId) {
        return authenticatedWorkers.get(workerId);
    }
    
    /**
     * Cierra sesión de un worker
     * @param workerId ID del worker
     * @return true si se cerró la sesión exitosamente
     */
    public boolean logoutWorker(String workerId) {
        SessionInfo session = authenticatedWorkers.remove(workerId);
        if (session != null) {
            activeTokens.remove(session.getToken());
            activeSessions--;
            System.out.println("Sesión cerrada para worker: " + workerId);
            return true;
        }
        return false;
    }
    
    /**
     * Cierra sesión por token
     * @param token Token de la sesión
     * @return true si se cerró la sesión exitosamente
     */
    public boolean logoutByToken(String token) {
        String workerId = activeTokens.remove(token);
        if (workerId != null) {
            authenticatedWorkers.remove(workerId);
            activeSessions--;
            System.out.println("Sesión cerrada por token para worker: " + workerId);
            return true;
        }
        return false;
    }
    
    // ==================== MÉTODOS DE VALIDACIÓN ====================
    
    /**
     * Valida credenciales de un worker
     * @param workerId ID del worker
     * @param password Contraseña
     * @return true si las credenciales son válidas
     */
    private boolean validateCredentials(String workerId, String password) {
        // En un sistema real, esto consultaría una base de datos o servicio de autenticación
        // Por ahora, implementamos una validación básica
        
        if (workerId == null || password == null || workerId.isEmpty() || password.isEmpty()) {
            return false;
        }
        
        // Validación básica: workerId debe tener al menos 3 caracteres y password al menos 6
        if (workerId.length() < 3 || password.length() < 6) {
            return false;
        }
        
        // Se simula la validación de contraseña
        // Por simplicidad, se acepta cualquier password que tenga al menos 6 caracteres
        return true;
    }
    
    /**
     * Genera un token de autenticación
     * @param workerId ID del worker
     * @param workerHost Host del worker
     * @return Token generado
     */
    private String generateAuthToken(String workerId, String workerHost) {
        try {
            String data = workerId + ":" + workerHost + ":" + System.currentTimeMillis() + ":" + SECRET_KEY;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes());
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error generando token: " + e.getMessage());
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
    
    /**
     * Verifica si un worker está bloqueado por intentos fallidos
     * @param workerId ID del worker
     * @return true si está bloqueado
     */
    private boolean isWorkerLocked(String workerId) {
        LoginAttempts attempts = failedAttempts.get(workerId);
        if (attempts == null) {
            return false;
        }
        
        return attempts.isLocked(LOCKOUT_DURATION_MINUTES);
    }
    
    /**
     * Registra un intento de autenticación fallido
     * @param workerId ID del worker
     */
    private void recordFailedAttempt(String workerId) {
        LoginAttempts attempts = failedAttempts.get(workerId);
        if (attempts == null) {
            attempts = new LoginAttempts();
            failedAttempts.put(workerId, attempts);
        }
        
        attempts.addFailedAttempt();
        
        // Información detallada del intento
        long minutesSinceFirst = attempts.getMinutesSinceFirstAttempt();
        long minutesSinceLast = attempts.getMinutesSinceLastAttempt();
        
        System.out.println("Intento fallido registrado para worker: " + workerId + 
                        " (Intentos: " + attempts.getAttemptCount() + 
                        ", Desde primer intento: " + minutesSinceFirst + " min" +
                        ", Desde último intento: " + minutesSinceLast + " min)");
        
        // Si el worker está bloqueado, mostrar información adicional
        if (attempts.isLocked(LOCKOUT_DURATION_MINUTES)) {
            System.out.println("Worker " + workerId + " BLOQUEADO por " + LOCKOUT_DURATION_MINUTES + 
                            " minutos debido a " + attempts.getAttemptCount() + " intentos fallidos");
        }
    }
    
    // Limpieza de sesiones expiradas
    private void cleanupExpiredSessions() {
        List<String> expiredWorkers = new ArrayList<>();
        
        for (Map.Entry<String, SessionInfo> entry : authenticatedWorkers.entrySet()) {
            if (entry.getValue().isExpired(TOKEN_EXPIRY_HOURS)) {
                expiredWorkers.add(entry.getKey());
            }
        }
        
        for (String workerId : expiredWorkers) {
            logoutWorker(workerId);
        }
        
        if (!expiredWorkers.isEmpty()) {
            System.out.println("Limpieza de sesiones: " + expiredWorkers.size() + " sesiones expiradas removidas");
        }
    }
    
    // ==================== MÉTODOS DE ESTADÍSTICAS ====================
    
    /**
     * Obtiene estadísticas de autenticación
     * @return String con estadísticas
     */
    public String getAuthenticationStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== ESTADÍSTICAS DE AUTENTICACIÓN ===\n");
        stats.append("Sesiones activas: ").append(activeSessions).append("\n");
        stats.append("Autenticaciones exitosas: ").append(totalAuthentications).append("\n");
        stats.append("Autenticaciones fallidas: ").append(failedAuthentications).append("\n");
        
        // Estadísticas detalladas de intentos fallidos
        int blockedWorkers = 0;
        int workersWithAttempts = 0;
        for (LoginAttempts attempts : failedAttempts.values()) {
            workersWithAttempts++;
            if (attempts.isLocked(LOCKOUT_DURATION_MINUTES)) {
                blockedWorkers++;
            }
        }
        
        stats.append("Workers con intentos fallidos: ").append(workersWithAttempts).append("\n");
        stats.append("Workers bloqueados: ").append(blockedWorkers).append("\n");
        stats.append("Duración de bloqueo: ").append(LOCKOUT_DURATION_MINUTES).append(" minutos\n");
        stats.append("Máximo intentos permitidos: ").append(MAX_LOGIN_ATTEMPTS).append("\n");
        
        return stats.toString();
    }
    
    /**
     * Obtiene el número de sesiones activas
     * @return Número de sesiones activas
     */
    public int getActiveSessions() {
        return activeSessions;
    }
    
    /**
     * Obtiene el número total de autenticaciones exitosas
     * @return Número de autenticaciones exitosas
     */
    public int getTotalAuthentications() {
        return totalAuthentications;
    }
    
    /**
     * Obtiene el número de autenticaciones fallidas
     * @return Número de autenticaciones fallidas
     */
    public int getFailedAuthentications() {
        return failedAuthentications;
    }
    
    // ==================== CLASES INTERNAS ====================
    
    /**
     * Información de sesión de un worker
     */
    public static class SessionInfo {
        private final String workerId;
        private final String workerHost;
        private final String token;
        private final LocalDateTime createdAt;
        private LocalDateTime lastAccess;
        
        public SessionInfo(String workerId, String workerHost, String token, LocalDateTime createdAt) {
            this.workerId = workerId;
            this.workerHost = workerHost;
            this.token = token;
            this.createdAt = createdAt;
            this.lastAccess = createdAt;
        }
        
        public String getWorkerId() { return workerId; }
        public String getWorkerHost() { return workerHost; }
        public String getToken() { return token; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getLastAccess() { return lastAccess; }
        
        public void updateLastAccess() {
            this.lastAccess = LocalDateTime.now();
        }
        
        public boolean isExpired(int expiryHours) {
            return lastAccess.isBefore(LocalDateTime.now().minusHours(expiryHours));
        }
    }
    
    /**
     * Información de intentos de login fallidos
     */
    private static class LoginAttempts {
        private int attemptCount;
        private LocalDateTime firstAttempt;
        private LocalDateTime lastAttempt;
        
        public LoginAttempts() {
            this.attemptCount = 0;
            this.firstAttempt = LocalDateTime.now();
            this.lastAttempt = LocalDateTime.now();
        }
        
        public void addFailedAttempt() {
            this.attemptCount++;
            this.lastAttempt = LocalDateTime.now();
            
            // Si es el primer intento, actualizar firstAttempt
            if (this.attemptCount == 1) {
                this.firstAttempt = LocalDateTime.now();
            }
        }
        
        public int getAttemptCount() {
            return attemptCount;
        }
        
        public boolean isLocked(int lockoutMinutes) {
            // Si han pasado más de lockoutMinutes desde el primer intento, resetear contador
            if (firstAttempt.isBefore(LocalDateTime.now().minusMinutes(lockoutMinutes))) {
                resetAttempts();
                return false;
            }
            
            // Verificar si hay demasiados intentos en la ventana de tiempo
            return attemptCount >= MAX_LOGIN_ATTEMPTS;
        }
        
        /**
         * Resetea el contador de intentos fallidos
         */
        public void resetAttempts() {
            this.attemptCount = 0;
            this.firstAttempt = LocalDateTime.now();
            this.lastAttempt = LocalDateTime.now();
        }
        
        /**
         * Obtiene el tiempo transcurrido desde el primer intento
         * @return Minutos transcurridos
         */
        public long getMinutesSinceFirstAttempt() {
            return java.time.Duration.between(firstAttempt, LocalDateTime.now()).toMinutes();
        }
        
        /**
         * Obtiene el tiempo transcurrido desde el último intento
         * @return Minutos transcurridos
         */
        public long getMinutesSinceLastAttempt() {
            return java.time.Duration.between(lastAttempt, LocalDateTime.now()).toMinutes();
        }
    }
}


