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
    @Autowired
    private StandardScheduler standardScheduler;
    @Autowired
    private TatkalScheduler tatkalScheduler;
    @Autowired
    private AvailabilityMonitor availabilityMonitor;

    @PostMapping("/book")
    public String bookTicket(@RequestBody Map<String, Object> payload) {
        int userId = (int) payload.get("userId");
        int seatsNeeded = (int) payload.get("seatsNeeded");
        boolean isTatkal = (boolean) payload.get("isTatkal");

        System.out.println("[STATE: NEW] Request received for User " + userId);

        // Create the Thread
        BookingProcess process = new BookingProcess(userId, seatsNeeded, isTatkal, guard);

        // OS Dispatcher Logic
        if (isTatkal) {
            tatkalScheduler.addProcess(process);
            return "Tatkal Request for User " + userId + " submitted to Priority Queue.";
        } else {
            standardScheduler.addProcess(process);
            return "General Request for User " + userId + " submitted to FCFS Queue.";
        }
    }

    // Member 3's Endpoint to test Readers-Writers
    @GetMapping("/check")
    public String checkSeats(@RequestParam int userId) {

        // We will spawn a new thread just to simulate a Reader checking the system
        new Thread(() -> {
            availabilityMonitor.checkAvailability(userId);
        }).start();

        return "User " + userId + " requested seat availability.";
    }

    // Member 3's Endpoint to trigger a Write Block
    @PostMapping("/simulate-write")
    public String triggerWriteBlock(@RequestParam int userId) {
        new Thread(() -> {
            availabilityMonitor.simulateBookingWrite(userId);
        }).start();

        return "User " + userId + " initiated a write lock.";
    }
}