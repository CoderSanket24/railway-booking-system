package com.vit.railway_os.oscore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.concurrent.Semaphore;

@Component
public class AvailabilityMonitor {

    private int readersCount = 0;

    // readMutex protects the 'readersCount' variable from race conditions
    private final Semaphore readMutex = new Semaphore(1);

    // writeLock protects the actual seat data from being read while it's being updated
    private final Semaphore writeLock = new Semaphore(1);

    @Autowired
    private OsStateTracker tracker;

    /**
     * OS CONCEPT: Reader Entry Protocol — call BEFORE reading shared data.
     * Multiple threads may be active simultaneously between startRead / endRead.
     */
    public void startRead(int userId) {
        try {
            readMutex.acquire();           // P(readMutex) — lock the counter
            readersCount++;
            tracker.incrementTotalReaderSessions();        // ← cumulative: never decrements
            tracker.setActiveReaders(readersCount);        // ← real-time: resets to 0 on exit
            if (readersCount == 1) {
                writeLock.acquire();       // 1st reader locks out all writers
            }
            System.out.println("[READER ENTRY] User " + userId +
                    " acquired read access. Active Readers: " + readersCount);
            readMutex.release();           // V(readMutex) — allow more readers in
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * OS CONCEPT: Reader Exit Protocol — call AFTER reading shared data.
     */
    public void endRead(int userId) {
        try {
            readMutex.acquire();           // P(readMutex)
            readersCount--;
            tracker.setActiveReaders(readersCount);   // ← push live count to tracker
            if (readersCount == 0) {
                writeLock.release();       // Last reader unblocks writers
            }
            System.out.println("[READER EXIT] User " + userId +
                    " released read access. Active Readers: " + readersCount);
            readMutex.release();           // V(readMutex)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Full reader cycle — used by /api/check for the demo endpoint.
     * Holds the read lock for 1 second so the Admin Dashboard counter is visible.
     */
    public String checkAvailability(int userId) {
        startRead(userId);
        try {
            System.out.println("[READING] User " + userId +
                    " checking seats. Concurrent Readers: " + readersCount);
            Thread.sleep(1000); // Simulate read delay — visible in Admin Dashboard
            return "Seats available. (Viewed by User " + userId + ")";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error checking availability.";
        } finally {
            endRead(userId);
        }
    }

    /**
     * Writer logic — used by /api/simulate-write for the demo endpoint.
     * All readers are blocked for 5 seconds.
     */
    public void simulateBookingWrite(int userId) {
        try {
            writeLock.acquire(); // Blocks ALL readers and other writers
            System.out.println("\n[WRITING ALERT] User " + userId +
                    " is modifying seat data! ALL READERS BLOCKED.");
            Thread.sleep(5000);
            System.out.println("[WRITING COMPLETE] User " + userId +
                    " finished. Readers can access again.\n");
            writeLock.release();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int getActiveReaders() {
        return readersCount;
    }
}