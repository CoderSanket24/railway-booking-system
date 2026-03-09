package com.vit.railway_os.oscore;

public class BookingProcess extends Thread implements Comparable<BookingProcess> {
    private int userId;
    private int seatsNeeded;
    private boolean isTatkal;
    private CriticalSectionGuard guard;
    private ProcessControlBlock pcb;

    public BookingProcess(int userId, int seatsNeeded, boolean isTatkal, CriticalSectionGuard guard) {
        this.userId = userId;
        this.seatsNeeded = seatsNeeded;
        this.isTatkal = isTatkal;
        this.guard = guard;
        
        // OS CONCEPT: Create PCB when process is created
        int burstTime = seatsNeeded * 1000; // Estimate: 1 second per seat
        int priority = isTatkal ? 1 : 5; // Lower number = higher priority
        this.pcb = new ProcessControlBlock(userId, burstTime, priority, isTatkal ? "TATKAL" : "STANDARD");
        
        System.out.println("[PROCESS CREATED] " + pcb);
    }

    @Override
    public void run() {
        // NEW → READY (handled by scheduler)
        // READY → RUNNING
        pcb.setState(ProcessState.RUNNING);
        pcb.setStartTime(System.currentTimeMillis());
        System.out.println("[RUNNING] Process " + userId + " executing booking...");
        
        // RUNNING → WAITING (when accessing critical section)
        pcb.setState(ProcessState.WAITING);
        System.out.println("[WAITING] Process " + userId + " waiting for database access...");
        
        guard.bookSeat(userId, seatsNeeded);
        
        // WAITING → RUNNING (after getting resource)
        pcb.setState(ProcessState.RUNNING);
        
        // RUNNING → TERMINATED
        pcb.setState(ProcessState.TERMINATED);
        pcb.setCompletionTime(System.currentTimeMillis());
        
        System.out.println("[TERMINATED] Process " + userId + " completed.");
        System.out.println("[METRICS] Turnaround Time: " + pcb.getTurnaroundTime() + "ms, " +
                          "Waiting Time: " + pcb.getWaitingTime() + "ms, " +
                          "Response Time: " + pcb.getResponseTime() + "ms");
    }

    // For Priority Queue: Tatkal gets higher priority
    @Override
    public int compareTo(BookingProcess other) {
        return Integer.compare(this.pcb.getPriority(), other.pcb.getPriority());
    }
    
    public ProcessControlBlock getPCB() {
        return pcb;
    }
    
    public boolean isTatkal() {
        return isTatkal;
    }
    
    public int getBurstTime() {
        return pcb.getBurstTime();
    }
}
