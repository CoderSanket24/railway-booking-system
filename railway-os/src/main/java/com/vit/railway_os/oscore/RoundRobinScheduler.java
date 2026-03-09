package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.LinkedList;
import java.util.Queue;

/**
 * MEMBER 2: Round Robin Scheduler
 * Preemptive scheduling with time quantum
 * Each process gets equal CPU time slice
 */
@Component
public class RoundRobinScheduler {
    
    private static final int TIME_QUANTUM = 2000; // 2 seconds per process
    private Queue<BookingProcess> readyQueue = new LinkedList<>();

    public synchronized void addProcess(BookingProcess process) {
        process.getPCB().setState(ProcessState.READY);
        readyQueue.add(process);
        System.out.println("[ROUND ROBIN] Process " + process.getPCB().getProcessId() + 
                          " added to queue. Time Quantum: " + TIME_QUANTUM + "ms");
    }

    public synchronized void dispatch() {
        System.out.println("[ROUND ROBIN] Starting dispatcher with " + readyQueue.size() + " processes");
        
        while (!readyQueue.isEmpty()) {
            BookingProcess process = readyQueue.poll();
            
            if (process != null && process.getPCB().getRemainingTime() > 0) {
                System.out.println("[ROUND ROBIN] Executing Process " + process.getPCB().getProcessId() + 
                                  " for " + TIME_QUANTUM + "ms (Remaining: " + 
                                  process.getPCB().getRemainingTime() + "ms)");
                
                // Simulate time quantum execution
                int executionTime = Math.min(TIME_QUANTUM, process.getPCB().getRemainingTime());
                
                if (process.getPCB().getState() == ProcessState.READY) {
                    process.start();
                }
                
                try {
                    Thread.sleep(executionTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                process.getPCB().setRemainingTime(process.getPCB().getRemainingTime() - executionTime);
                
                // If process not finished, add back to queue
                if (process.getPCB().getRemainingTime() > 0) {
                    System.out.println("[ROUND ROBIN] Process " + process.getPCB().getProcessId() + 
                                      " preempted. Back to ready queue.");
                    readyQueue.add(process);
                } else {
                    System.out.println("[ROUND ROBIN] Process " + process.getPCB().getProcessId() + " completed.");
                }
            }
        }
    }

    public int getQueueSize() {
        return readyQueue.size();
    }
}
