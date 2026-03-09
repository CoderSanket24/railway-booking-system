package com.vit.railway_os.controller;

import com.vit.railway_os.oscore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class BookingController {

    @Autowired
    private CriticalSectionGuard guard;
    
    // MEMBER 2: Scheduling Algorithms
    @Autowired
    private FCFSScheduler fcfsScheduler;
    @Autowired
    private SJFScheduler sjfScheduler;
    @Autowired
    private RoundRobinScheduler roundRobinScheduler;
    
    // MEMBER 5: Priority Scheduler
    @Autowired
    private PriorityScheduler priorityScheduler;
    
    // MEMBER 4: Readers-Writers
    @Autowired
    private AvailabilityMonitor availabilityMonitor;

    /**
     * MEMBER 1, 2, 3: Book ticket with different scheduling algorithms
     * schedulerType: "FCFS", "SJF", "RR", "PRIORITY"
     */
    @PostMapping("/book")
    public String bookTicket(@RequestBody Map<String, Object> payload) {
        int userId = Integer.parseInt(payload.get("userId").toString());
        int seatsNeeded = Integer.parseInt(payload.get("seatsNeeded").toString());
        boolean isTatkal = (boolean) payload.getOrDefault("isTatkal", false);
        String schedulerType = payload.getOrDefault("schedulerType", "FCFS").toString();

        // MEMBER 1: Create process with PCB
        BookingProcess process = new BookingProcess(userId, seatsNeeded, isTatkal, guard);

        // MEMBER 2 & 5: Route to appropriate scheduler
        switch (schedulerType.toUpperCase()) {
            case "FCFS":
                fcfsScheduler.addProcess(process);
                new Thread(() -> fcfsScheduler.dispatch()).start();
                return "Process " + userId + " submitted to FCFS Scheduler";
                
            case "SJF":
                sjfScheduler.addProcess(process);
                new Thread(() -> sjfScheduler.dispatch()).start();
                return "Process " + userId + " submitted to SJF Scheduler (Shortest Job First)";
                
            case "RR":
                roundRobinScheduler.addProcess(process);
                new Thread(() -> roundRobinScheduler.dispatch()).start();
                return "Process " + userId + " submitted to Round Robin Scheduler";
                
            case "PRIORITY":
                priorityScheduler.addProcess(process);
                new Thread(() -> priorityScheduler.dispatch()).start();
                return "Process " + userId + " submitted to Priority Scheduler (Tatkal: " + isTatkal + ")";
                
            default:
                return "Invalid scheduler type. Use: FCFS, SJF, RR, or PRIORITY";
        }
    }

    /**
     * MEMBER 4: Check seat availability (Readers-Writers Problem)
     */
    @GetMapping("/check")
    public String checkSeats(@RequestParam int userId) {
        new Thread(() -> {
            availabilityMonitor.checkAvailability(userId);
        }).start();
        return "User " + userId + " checking seat availability (Reader)";
    }

    /**
     * MEMBER 4: Simulate write operation (Readers-Writers Problem)
     */
    @PostMapping("/simulate-write")
    public String triggerWriteBlock(@RequestParam int userId) {
        new Thread(() -> {
            availabilityMonitor.simulateBookingWrite(userId);
        }).start();
        return "User " + userId + " initiated write lock (Writer)";
    }

    /**
     * MEMBER 5: Producer-Consumer demonstration
     */
    @PostMapping("/producer-consumer")
    public String demonstrateProducerConsumer(@RequestBody Map<String, Object> payload) {
        String trainNumber = payload.getOrDefault("trainNumber", "EXP-12951").toString();
        int ticketsToGenerate = Integer.parseInt(payload.getOrDefault("ticketsToGenerate", "20").toString());
        int numConsumers = Integer.parseInt(payload.getOrDefault("numConsumers", "3").toString());
        
        TicketBuffer buffer = new TicketBuffer();
        
        // Start Producer
        TicketProducer producer = new TicketProducer();
        producer.initialize(buffer, trainNumber, ticketsToGenerate);
        producer.start();
        
        // Start Multiple Consumers
        for (int i = 1; i <= numConsumers; i++) {
            TicketConsumer consumer = new TicketConsumer();
            consumer.initialize(buffer, i, ticketsToGenerate / numConsumers);
            consumer.start();
        }
        
        return "Producer-Consumer simulation started: " + ticketsToGenerate + 
               " tickets, " + numConsumers + " consumers";
    }

    /**
     * Get scheduler statistics
     */
    @GetMapping("/stats")
    public Map<String, Integer> getStats() {
        return Map.of(
            "fcfsQueueSize", fcfsScheduler.getQueueSize(),
            "sjfQueueSize", sjfScheduler.getQueueSize(),
            "rrQueueSize", roundRobinScheduler.getQueueSize(),
            "priorityQueueSize", priorityScheduler.getQueueSize(),
            "activeReaders", availabilityMonitor.getActiveReaders()
        );
    }
}
