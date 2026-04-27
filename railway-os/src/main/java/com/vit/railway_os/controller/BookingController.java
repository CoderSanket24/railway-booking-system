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

    @Autowired
    private BuddySystem buddySystem;

    @Autowired
    private TLBSimulator tlbSimulator;

    @Autowired
    private DiskScheduler diskScheduler;

    private static final List<Integer> diskRequestQueue = Collections.synchronizedList(new ArrayList<>());
    private static int currentDiskHead = 50;
    // GET /api/trains — Return live seat counts for all trains
    // Wrapped in Readers-Writers protocol so Admin Dashboard shows active readers
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/trains")
    public List<Map<String, Object>> getAllTrains(@RequestParam(required = false) Integer userId) {
        int readerId = userId != null ? userId : (int)(Math.random() * 9000 + 1000);

        // OS CONCEPT: Enter reader critical section (Readers-Writers lock)
        eventLog.pushReader("Reader P" + readerId + " acquiring shared lock on train table", readerId);
        availabilityMonitor.startRead(readerId);
        List<Train> trains = new ArrayList<>();
        try {
            trains = trainRepository.findAll(); // Read shared data
            // OS CONCEPT: Model realistic read I/O time — lock held during data processing.
            // Real DB reads involve disk seek + transfer latency. This 300ms hold makes
            // concurrent readers visible to the Admin monitor (which polls every 400ms).
            Thread.sleep(300);
            
            // ACTUAL TLB LOOKUP FOR MEMORY READ
            int logicalAddr = 0x0020; // Assume Page 0 holds the main trains list
            TLBSimulator.TranslationResult tlbResult = tlbSimulator.translate(logicalAddr);
            String hitMiss = tlbResult.tlbHit() ? "HIT" : "MISS";
            eventLog.pushTLB("TLB " + hitMiss + ": seat table page for P" + readerId + " → physical frame 0x" + Integer.toHexString(tlbResult.physicalAddress()), readerId, tlbResult.tlbHit());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

        // ── STEP 4: Memory allocated for session (Real Buddy System) ──
        int memoryRequired = 64; // KB for session context
        int address = buddySystem.allocate(pid, memoryRequired, passengerName);
        
        if (address == -1) {
            eventLog.pushMemory("❌ OUT OF MEMORY: Buddy System denied P" + pid + " (" + memoryRequired + "KB)", pid);
            return Map.of("success", false, "message", "Server out of memory (Buddy System full). Please wait for other users to finish.");
        }
        eventLog.pushMemory("Buddy System allocated " + memoryRequired + "KB for P" + pid + " at physical address 0x" + Integer.toHexString(address), pid);

        // ── STEP 5: Banker's Algorithm safety check (Now happens securely inside CriticalSectionGuard) ──

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
            // ── STEP 8: Add to selected scheduler queue, then dispatch ──
            // dispatch() calls queue.poll() internally, so queue depth returns
            // to 0 after the booking completes (fixes the accumulating queue bug).
            switch (schedulerType.toUpperCase()) {
                case "SJF":
                    sjfScheduler.addProcess(process);
                    sjfScheduler.dispatch();     // polls + runs + joins
                    break;
                case "RR":
                    roundRobinScheduler.addProcess(process);
                    roundRobinScheduler.dispatch();
                    break;
                case "PRIORITY":
                    priorityScheduler.addProcess(process);
                    priorityScheduler.dispatch();
                    break;
                default:
                    fcfsScheduler.addProcess(process);
                    fcfsScheduler.dispatch();
                    break;
            }

            CriticalSectionGuard.BookingResult result = process.getResult();
            Map<String, Object> response = new HashMap<>();
            if (result != null && result.success && result.booking != null) {
                eventLog.pushMutex("P" + pid + " releasing mutex lock — seat table updated", pid, false);
                eventLog.pushFileIO("Writing booking record " + result.booking.getPnr() + " to disk (INDEXED file org)", pid, true);
                
                // ACTUAL TLB LOOKUP FOR MEMORY WRITE
                int pageNum = (pid % 15) + 1; // Different users access different booking pages
                int logicalAddr = (pageNum << 8) | 0x40;
                TLBSimulator.TranslationResult tlbResult = tlbSimulator.translate(logicalAddr);
                String hitMiss = tlbResult.tlbHit() ? "HIT" : "MISS";
                
                eventLog.pushTLB("TLB " + hitMiss + ": booking page for P" + pid + " → physical frame 0x" + Integer.toHexString(tlbResult.physicalAddress()), pid, tlbResult.tlbHit());
                eventLog.pushBooking("✅ Booking CONFIRMED: " + result.booking.getPnr() + " | " + passengerName + " | " + trainNumber, pid, true);

                // ── STEP 8b: Disk Scheduling (Unit VI) ──
                int trackReq = (int)(Math.random() * 200); // Disk has 200 tracks (0-199)
                diskRequestQueue.add(trackReq);
                eventLog.pushFileIO("Added disk track " + trackReq + " to IO queue (Size: " + diskRequestQueue.size() + "/3)", pid, false);
                
                if (diskRequestQueue.size() >= 3) {
                    List<Integer> batch;
                    synchronized (diskRequestQueue) {
                        batch = new ArrayList<>(diskRequestQueue);
                        diskRequestQueue.clear();
                    }
                    int[] reqArray = batch.stream().mapToInt(i -> i).toArray();
                    DiskScheduler.SchedulingResult diskRes = diskScheduler.cscan(currentDiskHead, reqArray);
                    
                    eventLog.pushFileIO("🚀 FLUSHING DISK QUEUE using C-SCAN algorithm!", 0, true);
                    eventLog.pushFileIO("Head moved: " + currentDiskHead + " → " + diskRes.serviceOrder() + " (Total seek: " + diskRes.totalMovement() + " tracks)", 0, false);
                    if (!diskRes.serviceOrder().isEmpty()) {
                        currentDiskHead = diskRes.serviceOrder().get(diskRes.serviceOrder().size() - 1);
                    }
                }

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

        } catch (Exception e) {
            Thread.currentThread().interrupt();
            eventLog.pushBooking("⚠️ Booking ERROR for P" + pid + ": " + e.getMessage(), pid, false);
            return Map.of("success", false, "message", "Booking failed: " + e.getMessage());
        } finally {
            // ── ALWAYS release Dining Philosophers platform locks, even on failure/interruption ──
            if (trainIndex >= 0) {
                try {
                    platformLock.releasePlatformLocks(trainIndex, pid, eventLog);
                } catch (IllegalMonitorStateException ignore) {
                    // Lock was already released or was never fully acquired
                }
            }
            // ── STEP 9: Free Buddy System Memory ──
            buddySystem.deallocate(address);
            eventLog.pushMemory("Deallocated session memory for P" + pid + " @ 0x" + Integer.toHexString(address) + " (Buddy merged)", pid);
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
