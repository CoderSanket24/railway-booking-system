package com.vit.railway_os.oscore;

import com.vit.railway_os.model.Train;
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

    private final ReentrantLock mutex = new ReentrantLock();

    // We will use this specific train for the simulation
    private final String SIMULATION_TRAIN_NO = "EXP-12951";

    public boolean bookSeat(int userId, int seatsNeeded) {
        mutex.lock(); // ENTERING CRITICAL SECTION (Locks out other threads)
        try {
            System.out.println("[MUTEX LOCKED] Process " + userId + " is safely accessing the database.");

            // 1. READ: Fetch live data from MySQL
            Optional<Train> trainOpt = trainRepository.findByTrainNumber(SIMULATION_TRAIN_NO);

            if (trainOpt.isEmpty()) {
                System.out.println("FAILED: Train not found in DB.");
                return false;
            }

            Train train = trainOpt.get();

            // 2. CHECK: Are there enough seats?
            if (train.getAvailableSeats() >= seatsNeeded) {

                // Simulate processing/network time to prove the lock holds off concurrent requests
                Thread.sleep(3000);

                // 3. MODIFY: Deduct the seats
                train.setAvailableSeats(train.getAvailableSeats() - seatsNeeded);

                // 4. WRITE: Save the updated row back to MySQL
                trainRepository.save(train);

                System.out.println("SUCCESS: User " + userId + " booked " + seatsNeeded + " seats. DB Remaining: " + train.getAvailableSeats());
                return true;
            } else {
                System.out.println("FAILED: User " + userId + " - Not enough seats in DB.");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            System.out.println("[MUTEX UNLOCKED] Process " + userId + " released the database.\n");
            mutex.unlock(); // EXITING CRITICAL SECTION
        }
    }

    // --- HELPER METHOD TO SEED THE DATABASE ---
    // This runs exactly once when Spring Boot starts up.
    @PostConstruct
    public void initializeDatabase() {
        if (trainRepository.findByTrainNumber(SIMULATION_TRAIN_NO).isEmpty()) {
            System.out.println("[SYSTEM] Seeding MySQL database with initial train data...");
            Train newTrain = new Train(SIMULATION_TRAIN_NO, 100, 100);
            trainRepository.save(newTrain);
            System.out.println("[SYSTEM] Train " + SIMULATION_TRAIN_NO + " created with 100 seats.");
        }
    }
}