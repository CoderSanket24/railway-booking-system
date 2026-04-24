package com.vit.railway_os.controller;

import com.vit.railway_os.model.Booking;
import com.vit.railway_os.model.Train;
import com.vit.railway_os.oscore.*;
import com.vit.railway_os.repository.BookingRepository;
import com.vit.railway_os.repository.TrainRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class BookingController {

    @Autowired
    private CriticalSectionGuard guard;

    @Autowired
    private OsStateTracker tracker;

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

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private OsEventLog eventLog;

    @Autowired
    private TicketBuffer ticketBuffer;

    @Autowired
    private TrainPlatformLock platformLock;

    @Autowired
    private SmartSchedulerSelector smartScheduler;

    // ─────────────────────────────────────────────────────────────
    // GET /api/trains — Return live seat counts for all trains
    // Wrapped in Readers-Writers protocol so Admin Dashboard shows active readers
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/trains")
    public List<Map<String, Object>> getAllTrains(@RequestParam(required = false) Integer userId) {
        int readerId = userId != null ? userId : (int)(Math.random() * 9000 + 1000);

        // OS CONCEPT: Enter reader critical section (Readers-Writers lock)
        eventLog.pushReader("Reader P" + readerId + " acquiring shared lock on train table", readerId);
        availabilityMonitor.startRead(readerId);
        List<Train> trains;
        try {
            trains = trainRepository.findAll(); // Read shared data
            eventLog.pushTLB("TLB lookup: seat table page for P" + readerId + " → frame cached", readerId, true);
        } finally {
            availabilityMonitor.endRead(readerId); // Exit reader critical section
            eventLog.pushReader("Reader P" + readerId + " released shared lock", readerId);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Train t : trains) {
            Map<String, Object> m = new HashMap<>();
            m.put("trainNumber", t.getTrainNumber());
            m.put("trainName", t.getTrainName());
            m.put("from", t.getFromStation());
            m.put("to", t.getToStation());
            m.put("departure", t.getDeparture());
            m.put("arrival", t.getArrival());
            m.put("duration", t.getDuration());
            m.put("type", t.getType());
            m.put("price", t.getPrice());
            m.put("totalSeats", t.getTotalSeats());
            m.put("availableSeats", t.getAvailableSeats());
            result.add(m);
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/book — Book a ticket via the OS scheduling pipeline
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/book")
    public Map<String, Object> bookTicket(@RequestBody Map<String, Object> payload) {
        int userId         = Integer.parseInt(payload.get("userId").toString());
        int seatsNeeded    = Integer.parseInt(payload.get("seatsNeeded").toString());
        boolean isTatkal   = Boolean.parseBoolean(payload.getOrDefault("isTatkal", false).toString());
        String trainNumber = payload.getOrDefault("trainNumber", "EXP-12951").toString();
        String passengerName = payload.getOrDefault("passengerName", "Passenger").toString();

        int pid = userId;

        // ── STEP 1: New booking request logged ──
        eventLog.pushBooking("New booking request: " + passengerName + " → " + trainNumber + " (" + seatsNeeded + " seat" + (seatsNeeded > 1 ? "s" : ") "), pid, true);

        // ── STEP 2: Producer puts booking request into the TicketBuffer ──
        try {
            TicketBuffer.Ticket ticket = new TicketBuffer.Ticket(pid, trainNumber, seatsNeeded);
            ticketBuffer.produce(ticket);
            eventLog.push(OsEventLog.TYPE_BOOKING, "Unit-II", "📦 Producer placed booking P" + pid + " into buffer (size=" + ticketBuffer.getBufferSize() + ")", pid, "📦", true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // ── STEP 3: Smart / Manual Scheduler Selection ──
        String schedulerType;
        String schedulerReason;
        if (AdminController.isAutoMode()) {
            SmartSchedulerSelector.Decision decision =
                smartScheduler.select(seatsNeeded, isTatkal, trainNumber);
            schedulerType   = decision.algorithm;
            schedulerReason = decision.reason;
            AdminController.recordAutoDecision(schedulerType, schedulerReason);
            eventLog.pushScheduler("[AUTO→" + schedulerType + "] dispatching P" + pid
                + " (burst=" + seatsNeeded * 10 + "ms) — " + schedulerReason, pid);
        } else {
            schedulerType   = AdminController.getSystemScheduler();
            schedulerReason = "Manual override by admin";
            eventLog.pushScheduler("[MANUAL→" + schedulerType + "] dispatching P" + pid
                + " (burst=" + seatsNeeded * 10 + "ms)", pid);
        }

        // ── STEP 4: Memory allocated for session ──
        eventLog.pushMemory("Allocating session memory for P" + pid + " (booking context ~64KB)", pid);

        // ── STEP 5: Banker's Algorithm safety check ──
        eventLog.pushBanker("Banker’s check: P" + pid + " requesting " + seatsNeeded + " " + trainNumber + " seats — running safety algo", pid, true);

        // ── STEP 6: Mutex lock acquired (critical section) ──
        eventLog.pushMutex("P" + pid + " acquiring mutex lock on seat table (critical section)", pid, true);

        // ── STEP 6b: Dining Philosophers — acquire both platform locks ──
        // This is the real Dining Philosophers implementation:
        // train i must acquire platformLock[i] AND platformLock[(i+1)%N]
        // Asymmetric rule on last train prevents circular wait / deadlock.
        int trainIndex = platformLock.getTrainIndex(trainNumber);
        if (trainIndex >= 0) {
            platformLock.acquirePlatformLocks(trainIndex, pid, eventLog);
        }

        // ── STEP 7: Consumer removes from buffer when the scheduler picks it up ──
        try {
            ticketBuffer.consume();
            eventLog.push(OsEventLog.TYPE_BOOKING, "Unit-II", "✅ Consumer dequeued booking P" + pid + " from buffer (size=" + ticketBuffer.getBufferSize() + ")", pid, "✅", true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // MEMBER 1: Create process with PCB
        BookingProcess process = new BookingProcess(userId, seatsNeeded, isTatkal,
                trainNumber, passengerName, guard, tracker);

        // Run synchronously so we can return the result
        try {
            switch (schedulerType.toUpperCase()) {
                case "SJF":
                    sjfScheduler.addProcess(process);
                    break;
                case "RR":
                    roundRobinScheduler.addProcess(process);
                    break;
                case "PRIORITY":
                    priorityScheduler.addProcess(process);
                    break;
                default:
                    fcfsScheduler.addProcess(process);
                    break;
            }

            // Run the process directly and wait
            process.start();
            process.join();

            CriticalSectionGuard.BookingResult result = process.getResult();
            Map<String, Object> response = new HashMap<>();
            if (result != null && result.success && result.booking != null) {
                eventLog.pushMutex("P" + pid + " releasing mutex lock — seat table updated", pid, false);
                eventLog.pushFileIO("Writing booking record " + result.booking.getPnr() + " to disk (INDEXED file org)", pid, true);
                eventLog.pushTLB("TLB hit: booking page for P" + pid + " → physical frame 0x" + Integer.toHexString(pid % 256), pid, true);
                eventLog.pushMemory("Deallocating session memory for P" + pid + " (booking committed)", pid);
                eventLog.pushBooking("✅ Booking CONFIRMED: " + result.booking.getPnr() + " | " + passengerName + " | " + trainNumber, pid, true);

                response.put("success", true);
                response.put("pnr", result.booking.getPnr());
                response.put("message", "Booking confirmed! (Scheduler: " + schedulerType + ")");
                response.put("trainName", result.booking.getTrainName());
                response.put("from", result.booking.getFromStation());
                response.put("to", result.booking.getToStation());
                response.put("departure", result.booking.getDeparture());
                response.put("seats", result.booking.getSeats());
                response.put("totalPrice", result.booking.getTotalPrice());
                response.put("bookingDate", result.booking.getBookingDate());
                response.put("status", result.booking.getStatus());
                response.put("passengerName", result.booking.getPassengerName());
            } else {
                eventLog.pushMutex("P" + pid + " releasing mutex — booking failed", pid, false);
                eventLog.pushBooking("❌ Booking FAILED for P" + pid + ": " + (result != null ? result.message : "unknown error"), pid, false);
                response.put("success", false);
                response.put("message", result != null ? result.message : "Booking failed.");
            }
            return response;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            eventLog.pushBooking("⚠️ Booking INTERRUPTED for P" + pid, pid, false);
            return Map.of("success", false, "message", "Booking interrupted.");
        } finally {
            // ── ALWAYS release Dining Philosophers platform locks, even on failure/interruption ──
            if (trainIndex >= 0) {
                try {
                    platformLock.releasePlatformLocks(trainIndex, pid, eventLog);
                } catch (IllegalMonitorStateException ignore) {
                    // Lock was already released or was never fully acquired
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/bookings/{userId} — Fetch booking history for a user
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/bookings/{userId}")
    public List<Map<String, Object>> getUserBookings(@PathVariable int userId) {
        List<Booking> bookings = bookingRepository.findByUserIdOrderByIdDesc(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Booking b : bookings) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", b.getId());
            m.put("pnr", b.getPnr());
            m.put("trainNumber", b.getTrainNumber());
            m.put("trainName", b.getTrainName());
            m.put("from", b.getFromStation());
            m.put("to", b.getToStation());
            m.put("departure", b.getDeparture());
            m.put("seats", b.getSeats());
            m.put("totalPrice", b.getTotalPrice());
            m.put("bookingDate", b.getBookingDate());
            m.put("status", b.getStatus());
            m.put("passengerName", b.getPassengerName());
            result.add(m);
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/check — Readers-Writers: check seat availability
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/check")
    public String checkSeats(@RequestParam int userId) {
        new Thread(() -> availabilityMonitor.checkAvailability(userId)).start();
        return "User " + userId + " checking seat availability (Reader)";
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/simulate-write — Readers-Writers: simulate write
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/simulate-write")
    public String triggerWriteBlock(@RequestParam int userId) {
        new Thread(() -> availabilityMonitor.simulateBookingWrite(userId)).start();
        return "User " + userId + " initiated write lock (Writer)";
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/producer-consumer — Demo for Member 5
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/producer-consumer")
    public String demonstrateProducerConsumer(@RequestBody Map<String, Object> payload) {
        String trainNumber     = payload.getOrDefault("trainNumber", "EXP-12951").toString();
        int ticketsToGenerate  = Integer.parseInt(payload.getOrDefault("ticketsToGenerate", "20").toString());
        int numConsumers       = Integer.parseInt(payload.getOrDefault("numConsumers", "3").toString());

        // Use the shared Spring bean so the admin dashboard buffer count is visible
        TicketProducer producer = new TicketProducer();
        producer.initialize(ticketBuffer, trainNumber, ticketsToGenerate);
        producer.start();

        for (int i = 1; i <= numConsumers; i++) {
            TicketConsumer consumer = new TicketConsumer();
            consumer.initialize(ticketBuffer, i, ticketsToGenerate / numConsumers);
            consumer.start();
        }

        return "Producer-Consumer started: " + ticketsToGenerate + " tickets, " + numConsumers + " consumers";
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/stats — Scheduler queue sizes + reader count
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/stats")
    public Map<String, Integer> getStats() {
        return Map.of(
            "fcfsQueueSize",     fcfsScheduler.getQueueSize(),
            "sjfQueueSize",      sjfScheduler.getQueueSize(),
            "rrQueueSize",       roundRobinScheduler.getQueueSize(),
            "priorityQueueSize", priorityScheduler.getQueueSize(),
            "activeReaders",     availabilityMonitor.getActiveReaders()
        );
    }
}
