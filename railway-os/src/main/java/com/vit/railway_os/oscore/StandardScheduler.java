package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.LinkedList;
import java.util.Queue;

@Component
public class StandardScheduler {
    private Queue<BookingProcess> readyQueue = new LinkedList<>();

    public synchronized void addProcess(BookingProcess process) {
        readyQueue.add(process);
        System.out.println("[FCFS QUEUE] General Process added. Queue size: " + readyQueue.size());
        dispatch();
    }

    private void dispatch() {
        BookingProcess process = readyQueue.poll();
        if (process != null) {
            process.start(); // Moves process from READY to RUNNING
        }
    }
}