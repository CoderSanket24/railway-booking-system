package com.vit.railway_os.controller;

import com.vit.railway_os.oscore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    @Autowired
    private FCFSScheduler fcfsScheduler;

    @Autowired
    private SJFScheduler sjfScheduler;

    @Autowired
    private RoundRobinScheduler roundRobinScheduler;

    @Autowired
    private PriorityScheduler priorityScheduler;

    @Autowired
    private AvailabilityMonitor availabilityMonitor;

    // System-wide scheduler configuration
    private static String currentScheduler = "FCFS"; // Default scheduler

    @GetMapping("/monitor")
    public Map<String, Integer> getOsMetrics() {
        // Gathers the live OS data from your multithreaded environment
        Map<String, Integer> metrics = new HashMap<>();
        metrics.put("fcfsQueueSize", fcfsScheduler.getQueueSize());
        metrics.put("sjfQueueSize", sjfScheduler.getQueueSize());
        metrics.put("roundRobinQueueSize", roundRobinScheduler.getQueueSize());
        metrics.put("priorityQueueSize", priorityScheduler.getQueueSize());
        metrics.put("activeReaders", availabilityMonitor.getActiveReaders());

        return metrics; // Spring Boot automatically converts this to JSON!
    }

    @GetMapping("/scheduler")
    public Map<String, String> getCurrentScheduler() {
        return Map.of("scheduler", currentScheduler);
    }

    @PostMapping("/scheduler")
    public Map<String, String> setScheduler(@RequestBody Map<String, String> payload) {
        String newScheduler = payload.get("scheduler");
        if (newScheduler != null && 
            (newScheduler.equals("FCFS") || newScheduler.equals("SJF") || 
             newScheduler.equals("RR") || newScheduler.equals("PRIORITY"))) {
            currentScheduler = newScheduler;
            System.out.println("[ADMIN] System scheduler changed to: " + currentScheduler);
            return Map.of("status", "success", "scheduler", currentScheduler);
        }
        return Map.of("status", "error", "message", "Invalid scheduler type");
    }

    public static String getSystemScheduler() {
        return currentScheduler;
    }
}