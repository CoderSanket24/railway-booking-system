package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.PriorityQueue;
import java.util.Comparator;

/**
 * MEMBER 2: Shortest Job First (SJF) Scheduler
 * Non-preemptive scheduling - shortest burst time gets CPU first
 */
@Component
public class SJFScheduler {
    
    private PriorityQueue<BookingProcess> sjfQueue = new PriorityQueue<>(
        Comparator.comparingInt(BookingProcess::getBurstTime)
    );
    private int totalDispatched = 0;

    public synchronized void addProcess(BookingProcess process) {
        process.getPCB().setState(ProcessState.READY);
        sjfQueue.add(process);
        System.out.println("[SJF SCHEDULER] Process " + process.getPCB().getProcessId() + 
                          " added. Burst Time: " + process.getBurstTime() + "ms. Queue size: " + sjfQueue.size());
        notifyAll();
    }

    public synchronized void dispatch() {
        while (!sjfQueue.isEmpty()) {
            BookingProcess process = sjfQueue.poll();
            if (process != null) {
                System.out.println("[SJF DISPATCH] Running process " + process.getPCB().getProcessId() + 
                                  " (Shortest burst time: " + process.getBurstTime() + "ms)");
                process.start();
                totalDispatched++;
                
                try {
                    process.join(); // Wait for process to complete
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public int getQueueSize()       { return sjfQueue.size(); }
    public int getTotalDispatched()  { return totalDispatched; }
}
