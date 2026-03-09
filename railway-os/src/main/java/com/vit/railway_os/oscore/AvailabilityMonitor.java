package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.concurrent.Semaphore;

@Component
public class AvailabilityMonitor {

    private int readersCount = 0;

    // readMutex protects the 'readersCount' variable from race conditions
    private final Semaphore readMutex = new Semaphore(1);

    // writeLock protects the actual seat data from being read while it's being updated
    private final Semaphore writeLock = new Semaphore(1);

    // THE READER LOGIC: Thousands of users can do this simultaneously
    public String checkAvailability(int userId) {
        try {
            readMutex.acquire(); // Lock the counter
            readersCount++;
            if (readersCount == 1) {
                writeLock.acquire(); // The FIRST reader locks out all writers
            }
            readMutex.release(); // Unlock the counter so other readers can join

            // --- CRITICAL SECTION (READING) ---
            System.out.println("[READING] User " + userId + " checking seats. Concurrent Readers: " + readersCount);
            Thread.sleep(200); // Simulate network delay
            String response = "Seats are available. (Viewed by User " + userId + ")";
            // ----------------------------------

            readMutex.acquire(); // Lock the counter to decrement
            readersCount--;
            if (readersCount == 0) {
                writeLock.release(); // The LAST reader unlocks the writers
            }
            readMutex.release();

            return response;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error checking availability.";
        }
    }

    // THE WRITER LOGIC: Used when someone is actually deducting a seat
    public void simulateBookingWrite(int userId) {
        try {
            writeLock.acquire(); // Locks out ALL readers and other writers

            // --- CRITICAL SECTION (WRITING) ---
            System.out.println("\n[WRITING ALERT] User " + userId + " is modifying seat data! ALL READERS BLOCKED.");
            Thread.sleep(10000); // Simulate a heavy database write
            System.out.println("[WRITING COMPLETE] User " + userId + " finished. Readers can now access again.\n");
            // ----------------------------------

            writeLock.release();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}