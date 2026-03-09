package com.vit.railway_os.oscore;

/**
 * OS CONCEPT: Process Control Block (PCB)
 * Data structure maintained by OS for each process
 */
public class ProcessControlBlock {
    private int processId;
    private ProcessState state;
    private int priority;
    private long arrivalTime;
    private long startTime;
    private long completionTime;
    private int burstTime;  // Expected execution time
    private int remainingTime;
    private String processType; // "STANDARD" or "TATKAL"
    
    public ProcessControlBlock(int processId, int burstTime, int priority, String processType) {
        this.processId = processId;
        this.state = ProcessState.NEW;
        this.priority = priority;
        this.burstTime = burstTime;
        this.remainingTime = burstTime;
        this.arrivalTime = System.currentTimeMillis();
        this.processType = processType;
    }
    
    // Scheduling Metrics
    public long getTurnaroundTime() {
        return completionTime - arrivalTime;
    }
    
    public long getWaitingTime() {
        return getTurnaroundTime() - burstTime;
    }
    
    public long getResponseTime() {
        return startTime - arrivalTime;
    }
    
    // Getters and Setters
    public int getProcessId() { return processId; }
    public ProcessState getState() { return state; }
    public void setState(ProcessState state) { 
        System.out.println("[PCB] Process " + processId + " state: " + this.state + " → " + state);
        this.state = state; 
    }
    
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    
    public long getArrivalTime() { return arrivalTime; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    
    public long getCompletionTime() { return completionTime; }
    public void setCompletionTime(long completionTime) { this.completionTime = completionTime; }
    
    public int getBurstTime() { return burstTime; }
    public int getRemainingTime() { return remainingTime; }
    public void setRemainingTime(int remainingTime) { this.remainingTime = remainingTime; }
    
    public String getProcessType() { return processType; }
    
    @Override
    public String toString() {
        return String.format("PCB[PID=%d, State=%s, Priority=%d, BurstTime=%d, Type=%s]",
                processId, state, priority, burstTime, processType);
    }
}
