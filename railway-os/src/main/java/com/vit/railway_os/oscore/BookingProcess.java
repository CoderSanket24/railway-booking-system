package com.vit.railway_os.oscore;

public class BookingProcess extends Thread implements Comparable<BookingProcess> {
    private int userId;
    private int seatsNeeded;
    private boolean isTatkal;
    private CriticalSectionGuard guard;
    private ProcessControlBlock pcb;

    // Booking context
    private String trainNumber;
    private String passengerName;

    // Result (set by run())
    private CriticalSectionGuard.BookingResult result;

    public BookingProcess(int userId, int seatsNeeded, boolean isTatkal,
                          String trainNumber, String passengerName,
                          CriticalSectionGuard guard, OsStateTracker tracker) {
        this.userId = userId;
        this.seatsNeeded = seatsNeeded;
        this.isTatkal = isTatkal;
        this.trainNumber = trainNumber;
        this.passengerName = passengerName;
        this.guard = guard;

        // OS CONCEPT: Create PCB when process is created
        int burstTime = seatsNeeded * 1000; // Estimate: 1 second per seat
        int priority = isTatkal ? 1 : 5;   // Lower number = higher priority
        this.pcb = new ProcessControlBlock(userId, burstTime, priority,
                isTatkal ? "TATKAL" : "STANDARD", tracker);

        System.out.println("[PROCESS CREATED] " + pcb);
    }

    @Override
    public void run() {
        // READY → RUNNING
        pcb.setState(ProcessState.RUNNING);
        pcb.setStartTime(System.currentTimeMillis());
        System.out.println("[RUNNING] Process " + userId + " executing booking...");

        // RUNNING → WAITING (when accessing critical section)
        pcb.setState(ProcessState.WAITING);
        System.out.println("[WAITING] Process " + userId + " waiting for database access...");

        result = guard.bookSeat(userId, seatsNeeded, trainNumber, passengerName);

        // WAITING → RUNNING (after getting resource)
        pcb.setState(ProcessState.RUNNING);

        // RUNNING → TERMINATED
        pcb.setState(ProcessState.TERMINATED);
        pcb.setCompletionTime(System.currentTimeMillis());

        System.out.println("[TERMINATED] Process " + userId + " completed.");
        System.out.println("[METRICS] Turnaround: " + pcb.getTurnaroundTime() + "ms, " +
                "Waiting: " + pcb.getWaitingTime() + "ms, " +
                "Response: " + pcb.getResponseTime() + "ms");
    }

    // For Priority Queue: Tatkal gets higher priority
    @Override
    public int compareTo(BookingProcess other) {
        return Integer.compare(this.pcb.getPriority(), other.pcb.getPriority());
    }

    public ProcessControlBlock getPCB() { return pcb; }
    public boolean isTatkal() { return isTatkal; }
    public int getBurstTime() { return pcb.getBurstTime(); }
    public CriticalSectionGuard.BookingResult getResult() { return result; }
}
