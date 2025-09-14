package com.gridmr.master.grpc;

/**
 * Clases de mensajes gRPC simples para comunicación Master-Workers
 */
public class GrpcMessages {
    
    // ============================================================================
    // REQUEST MESSAGES
    // ============================================================================
    
    public static class RegisterWorkerRequest {
        private String workerId;
        private String host;
        private int port;
        private int maxTasks;
        
        public RegisterWorkerRequest() {}
        
        public RegisterWorkerRequest(String workerId, String host, int port, int maxTasks) {
            this.workerId = workerId;
            this.host = host;
            this.port = port;
            this.maxTasks = maxTasks;
        }
        
        // Getters
        public String getWorkerId() { return workerId; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public int getMaxTasks() { return maxTasks; }
        
        // Setters
        public void setWorkerId(String workerId) { this.workerId = workerId; }
        public void setHost(String host) { this.host = host; }
        public void setPort(int port) { this.port = port; }
        public void setMaxTasks(int maxTasks) { this.maxTasks = maxTasks; }
    }
    
    public static class UnregisterWorkerRequest {
        private String workerId;
        
        public UnregisterWorkerRequest() {}
        
        public UnregisterWorkerRequest(String workerId) {
            this.workerId = workerId;
        }
        
        public String getWorkerId() { return workerId; }
        public void setWorkerId(String workerId) { this.workerId = workerId; }
    }
    
    public static class HeartbeatRequest {
        private String workerId;
        private int activeTasks;
        private int availableTasks;
        
        public HeartbeatRequest() {}
        
        public HeartbeatRequest(String workerId, int activeTasks, int availableTasks) {
            this.workerId = workerId;
            this.activeTasks = activeTasks;
            this.availableTasks = availableTasks;
        }
        
        public String getWorkerId() { return workerId; }
        public int getActiveTasks() { return activeTasks; }
        public int getAvailableTasks() { return availableTasks; }
        
        public void setWorkerId(String workerId) { this.workerId = workerId; }
        public void setActiveTasks(int activeTasks) { this.activeTasks = activeTasks; }
        public void setAvailableTasks(int availableTasks) { this.availableTasks = availableTasks; }
    }
    
    public static class TaskCompletionRequest {
        private String taskId;
        private String workerId;
        private String outputFile;
        private long processingTime;
        
        public TaskCompletionRequest() {}
        
        public TaskCompletionRequest(String taskId, String workerId, String outputFile, long processingTime) {
            this.taskId = taskId;
            this.workerId = workerId;
            this.outputFile = outputFile;
            this.processingTime = processingTime;
        }
        
        public String getTaskId() { return taskId; }
        public String getWorkerId() { return workerId; }
        public String getOutputFile() { return outputFile; }
        public long getProcessingTime() { return processingTime; }
        
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public void setWorkerId(String workerId) { this.workerId = workerId; }
        public void setOutputFile(String outputFile) { this.outputFile = outputFile; }
        public void setProcessingTime(long processingTime) { this.processingTime = processingTime; }
    }
    
    public static class TaskFailureRequest {
        private String taskId;
        private String workerId;
        private String errorMessage;
        private String errorType;
        
        public TaskFailureRequest() {}
        
        public TaskFailureRequest(String taskId, String workerId, String errorMessage, String errorType) {
            this.taskId = taskId;
            this.workerId = workerId;
            this.errorMessage = errorMessage;
            this.errorType = errorType;
        }
        
        public String getTaskId() { return taskId; }
        public String getWorkerId() { return workerId; }
        public String getErrorMessage() { return errorMessage; }
        public String getErrorType() { return errorType; }
        
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public void setWorkerId(String workerId) { this.workerId = workerId; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public void setErrorType(String errorType) { this.errorType = errorType; }
    }
    
    public static class RequestTaskRequest {
        private String workerId;
        private String[] capabilities;
        
        public RequestTaskRequest() {}
        
        public RequestTaskRequest(String workerId, String[] capabilities) {
            this.workerId = workerId;
            this.capabilities = capabilities;
        }
        
        public String getWorkerId() { return workerId; }
        public String[] getCapabilities() { return capabilities; }
        
        public void setWorkerId(String workerId) { this.workerId = workerId; }
        public void setCapabilities(String[] capabilities) { this.capabilities = capabilities; }
    }
    
    // ============================================================================
    // RESPONSE MESSAGES
    // ============================================================================
    
    public static class RegisterWorkerResponse {
        private boolean success;
        private String message;
        private String masterId;
        
        public RegisterWorkerResponse() {}
        
        public RegisterWorkerResponse(boolean success, String message, String masterId) {
            this.success = success;
            this.message = message;
            this.masterId = masterId;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getMasterId() { return masterId; }
        
        public void setSuccess(boolean success) { this.success = success; }
        public void setMessage(String message) { this.message = message; }
        public void setMasterId(String masterId) { this.masterId = masterId; }
    }
    
    public static class UnregisterWorkerResponse {
        private boolean success;
        private String message;
        
        public UnregisterWorkerResponse() {}
        
        public UnregisterWorkerResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        
        public void setSuccess(boolean success) { this.success = success; }
        public void setMessage(String message) { this.message = message; }
    }
    
    public static class HeartbeatResponse {
        private boolean success;
        private String message;
        private long timestamp;
        
        public HeartbeatResponse() {}
        
        public HeartbeatResponse(boolean success, String message, long timestamp) {
            this.success = success;
            this.message = message;
            this.timestamp = timestamp;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public long getTimestamp() { return timestamp; }
        
        public void setSuccess(boolean success) { this.success = success; }
        public void setMessage(String message) { this.message = message; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
    
    public static class TaskCompletionResponse {
        private boolean success;
        private String message;
        
        public TaskCompletionResponse() {}
        
        public TaskCompletionResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        
        public void setSuccess(boolean success) { this.success = success; }
        public void setMessage(String message) { this.message = message; }
    }
    
    public static class TaskFailureResponse {
        private boolean success;
        private String message;
        
        public TaskFailureResponse() {}
        
        public TaskFailureResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        
        public void setSuccess(boolean success) { this.success = success; }
        public void setMessage(String message) { this.message = message; }
    }
    
    public static class RequestTaskResponse {
        private boolean hasTask;
        private String message;
        
        public RequestTaskResponse() {}
        
        public RequestTaskResponse(boolean hasTask, String message) {
            this.hasTask = hasTask;
            this.message = message;
        }
        
        public boolean isHasTask() { return hasTask; }
        public String getMessage() { return message; }
        
        public void setHasTask(boolean hasTask) { this.hasTask = hasTask; }
        public void setMessage(String message) { this.message = message; }
    }
}
