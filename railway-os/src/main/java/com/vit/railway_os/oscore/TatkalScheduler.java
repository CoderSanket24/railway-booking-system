package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.concurrent.PriorityBlockingQueue;

@Component
public class TatkalScheduler {
    private PriorityBlockingQueue<BookingProcess> priorityQueue = new PriorityBlockingQueue<>();

    public void addProcess(BookingProcess process) {
        priorityQueue.add(process);
        System.out.println("[PRIORITY QUEUE] Tatkal Process added. Jumping to front!");
        dispatch();
    }

    private void dispatch() {
        BookingProcess process = priorityQueue.poll();
        if (process != null) {
            process.start();
        }
    }
}