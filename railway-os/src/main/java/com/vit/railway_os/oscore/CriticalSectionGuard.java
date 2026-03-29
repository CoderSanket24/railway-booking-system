package com.vit.railway_os.oscore;

import com.vit.railway_os.model.Booking;
import com.vit.railway_os.model.Train;
import com.vit.railway_os.repository.BookingRepository;
import com.vit.railway_os.repository.TrainRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class CriticalSectionGuard {

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private OsStateTracker tracker;

    private final ReentrantLock mutex = new ReentrantLock();

    /**
     * OS CONCEPT: Mutex Lock — Critical Section
     * Only ONE thread can be inside this method at a time.
     * All other threads block at mutex.lock() until it's released.
     */
    public BookingResult bookSeat(int userId, int seatsNeeded, String trainNumber,
                                  String passengerName) {
        mutex.lock(); // ← ENTER CRITICAL SECTION (all others wait)
        tracker.setMutexLocked(true, userId);
        try {
            System.out.println("[MUTEX LOCKED]   Process " + userId + " is safely accessing the database.");

            // 1. READ current seats from MySQL
            Optional<Train> trainOpt = trainRepository.findByTrainNumber(trainNumber);

            if (trainOpt.isEmpty()) {
                System.out.println("FAILED: Train " + trainNumber + " not found in DB.");
                return new BookingResult(false, null, "Train not found.");
            }

            Train train = trainOpt.get();

            // 2. CHECK if enough seats available
            if (train.getAvailableSeats() >= seatsNeeded) {

                // Simulate processing time — proves the lock blocks concurrent requests
                Thread.sleep(2000);

                // 3. DEDUCT seats
                train.setAvailableSeats(train.getAvailableSeats() - seatsNeeded);

                // 4. SAVE to MySQL
                trainRepository.save(train);

                // 5. PERSIST booking record
                int totalPrice = (train.getPrice() * seatsNeeded) + (seatsNeeded * 30);
                String pnr = "PNR" + System.currentTimeMillis();
                Booking booking = new Booking(pnr, userId, train.getTrainNumber(),
                        train.getTrainName(), train.getFromStation(), train.getToStation(),
                        train.getDeparture(), seatsNeeded, totalPrice, passengerName);
                bookingRepository.save(booking);

                System.out.println("SUCCESS: User " + userId + " booked " + seatsNeeded +
                        " seats on " + trainNumber + ". DB Remaining: " + train.getAvailableSeats());
                return new BookingResult(true, booking, "Booking confirmed!");
            } else {
                System.out.println("FAILED: User " + userId + " - Not enough seats in DB.");
                return new BookingResult(false, null, "Not enough seats available.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new BookingResult(false, null, "Booking interrupted.");
        } finally {
            System.out.println("[MUTEX UNLOCKED] Process " + userId + " released the database.\n");
            tracker.setMutexLocked(false, userId);
            mutex.unlock(); // ← EXIT CRITICAL SECTION
        }
    }

    /**
     * Seed the MySQL database with all 4 trains on first startup.
     * Runs once when Spring Boot starts.
     */
    @PostConstruct
    public void initializeDatabase() {
        seedTrain("EXP-12951", "Mumbai Rajdhani",  "Mumbai", "Delhi",     "16:00", "08:00+1", "16h",      "Rajdhani", 1500, 100);
        seedTrain("EXP-12952", "Shatabdi Express",  "Mumbai", "Pune",     "07:00", "10:30",   "3h 30m",   "Shatabdi", 450,  85);
        seedTrain("EXP-12953", "Duronto Express",   "Mumbai", "Bangalore","20:00", "10:00+1", "14h",      "Duronto",  1200, 120);
        seedTrain("EXP-12954", "Garib Rath Express","Delhi",  "Kolkata",  "22:30", "07:15+1", "8h 45m",   "Express",  650,  42);
    }

    private void seedTrain(String number, String name, String from, String to,
                           String dep, String arr, String dur, String type,
                           int price, int seats) {
        var existing = trainRepository.findByTrainNumber(number);
        if (existing.isEmpty()) {
            // First-time seed
            System.out.println("[SYSTEM] Seeding train: " + name);
            trainRepository.save(new Train(number, name, from, to, dep, arr, dur, type, price, seats, seats));
        } else {
            Train t = existing.get();
            // Update if migrated from old schema (trainName is null/empty)
            if (t.getTrainName() == null || t.getTrainName().isEmpty()) {
                System.out.println("[SYSTEM] Updating migrated train: " + name);
                t.setTrainName(name);
                t.setFromStation(from);
                t.setToStation(to);
                t.setDeparture(dep);
                t.setArrival(arr);
                t.setDuration(dur);
                t.setType(type);
                t.setPrice(price);
                // Keep existing availableSeats so we don't reset live counts
                if (t.getTotalSeats() == 0) { t.setTotalSeats(seats); t.setAvailableSeats(seats); }
                trainRepository.save(t);
            }
        }
    }

    /** Inner class to carry the result back to the controller */
    public static class BookingResult {
        public final boolean success;
        public final Booking booking;
        public final String message;

        public BookingResult(boolean success, Booking booking, String message) {
            this.success = success;
            this.booking = booking;
            this.message = message;
        }
    }
}