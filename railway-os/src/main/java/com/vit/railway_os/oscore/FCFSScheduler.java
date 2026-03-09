package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.LinkedList;
import java.util.Queue;

/**
 * MEMBER 2: First Come First Serve (FCFS) Scheduler
 * Non-preemptive scheduling - processes execute in arrival order
 */
@Component
public class FCFSScheduler {
    
    private Queue<BookingProcess> readyQueue = new LinkedList<>();

    public synchronized void addProcess(BookingProcess process) {
        process.getPCB().setState(ProcessState.READY);
        readyQueue.add(process);
        System.out.println("[FCFS SCHEDULER] Process " + process.getPCB().getProcessId() + 
                          " added to ready queue. Queue size: " + readyQueue.size());
    }

    public synchronized void dispatch() {
        while (!readyQueue.isEmpty()) {
            BookingProcess process = readyQueue.poll();
            if (process != null) {
                System.out.println("[FCFS DISPATCH] Running process " + process.getPCB().getProcessId());
                process.start();
                
                try {
                    process.join(); // Wait for process to complete
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public int getQueueSize() {
        return readyQueue.size();
    }
}
