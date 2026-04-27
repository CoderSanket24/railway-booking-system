package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.PriorityQueue;

/**
 * MEMBER 5: Priority Scheduler
 * Preemptive scheduling - higher priority processes execute first
 * Used for Tatkal/VIP bookings
 */
@Component
public class PriorityScheduler {
    
    private PriorityQueue<BookingProcess> priorityQueue = new PriorityQueue<>();
    private int totalDispatched = 0;

    public synchronized void addProcess(BookingProcess process) {
        process.getPCB().setState(ProcessState.READY);
        priorityQueue.add(process);
        System.out.println("[PRIORITY SCHEDULER] Process " + process.getPCB().getProcessId() + 
                          " added with priority " + process.getPCB().getPriority() + 
                          " (Lower number = Higher priority). Queue size: " + priorityQueue.size());
    }

    public synchronized void dispatch() {
        while (!priorityQueue.isEmpty()) {
            BookingProcess process = priorityQueue.poll();
            if (process != null) {
                System.out.println("[PRIORITY DISPATCH] Running highest priority process " + 
                                  process.getPCB().getProcessId() + " (Priority: " + 
                                  process.getPCB().getPriority() + ")");
                process.start();
                totalDispatched++;
                
                try {
                    process.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public int getQueueSize()       { return priorityQueue.size(); }
    public int getTotalDispatched()  { return totalDispatched; }
}
