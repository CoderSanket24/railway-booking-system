package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class CriticalSectionGuard {
    private int availableSeats = 50; // Shared Resource
    private final ReentrantLock mutex = new ReentrantLock();

    public boolean bookSeat(int userId, int seatsNeeded) {
        mutex.lock(); // ENTERING CRITICAL SECTION
        try {
            System.out.println("[MUTEX LOCKED] Process " + userId + " is in the critical section.");
            if (availableSeats >= seatsNeeded) {
                Thread.sleep(500); // Simulate processing time to prove the lock works
                availableSeats -= seatsNeeded;
                System.out.println("SUCCESS: User " + userId + " booked " + seatsNeeded + " seats. Remaining: " + availableSeats);
                return true;
            } else {
                System.out.println("FAILED: User " + userId + " - Not enough seats.");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            System.out.println("[MUTEX UNLOCKED] Process " + userId + " left the critical section.\n");
            mutex.unlock(); // EXITING CRITICAL SECTION
        }
    }
}