package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * OS CONCEPT: Dining Philosophers — Real Implementation
 *
 * Mapping:
 *   Philosopher  = Train (booking handler for a specific train)
 *   Chopstick    = Platform Lock (shared track resource between two adjacent trains)
 *   Eating       = Processing a booking (holds both platform locks)
 *   Hungry       = Booking arrived, waiting to acquire both platform locks
 *   Thinking     = Idle, no booking in progress
 *
 * We have 4 trains → 4 platform locks arranged in a circle:
 *   [Lock-0] — EXP-12951 — [Lock-1] — EXP-12952 — [Lock-2] — EXP-12953 — [Lock-3] — EXP-12954 — [Lock-0]
 *
 * Each booking on train i must acquire:
 *   LEFT  lock = platformLocks[i]
 *   RIGHT lock = platformLocks[(i+1) % N]
 *
 * Deadlock prevention (Resource Hierarchy / Asymmetric Rule):
 *   Trains 0, 1, 2 : acquire LEFT first, then RIGHT
 *   Train  3 (last) : acquire RIGHT first, then LEFT  ← breaks circular wait
 *
 * This is NOT a simulation — it is wired directly into the real booking flow.
 * Every real booking triggers acquires/releases on these shared ReentrantLocks.
 */
@Component
public class TrainPlatformLock {

    // Train index mapping (order determines adjacency in the philosopher circle)
    public static final String[] TRAIN_NUMBERS = {
        "EXP-12951", "EXP-12952", "EXP-12953", "EXP-12954"
    };
    public static final String[] TRAIN_NAMES = {
        "Mumbai Rajdhani", "Shatabdi Express", "Duronto Express", "Garib Rath Express"
    };

    private static final int N = TRAIN_NUMBERS.length; // 4

    // N shared platform locks arranged in a circle (the "chopsticks")
    private final ReentrantLock[] platformLocks = new ReentrantLock[N];

    // Live state of each train/philosopher for the admin dashboard
    private final String[] states = new String[N];

    public TrainPlatformLock() {
        for (int i = 0; i < N; i++) {
            platformLocks[i] = new ReentrantLock();
            states[i] = "IDLE";
        }
    }

    /**
     * Returns the index of a train number in the philosopher circle.
     * Returns -1 if the train is not in the circle (won't apply the protocol).
     */
    public int getTrainIndex(String trainNumber) {
        for (int i = 0; i < N; i++) {
            if (TRAIN_NUMBERS[i].equals(trainNumber)) return i;
        }
        return -1;
    }

    /**
     * Acquires both platform locks for train i using the asymmetric rule.
     * BLOCKS until both locks are acquired (real contention between concurrent bookings).
     *
     * @param trainIndex index in the philosopher circle
     * @param pid        PID of the booking process (for logging)
     * @param eventLog   for pushing PHILOSOPHER events to the live feed
     */
    public void acquirePlatformLocks(int trainIndex, int pid, OsEventLog eventLog) {
        int left  = trainIndex;
        int right = (trainIndex + 1) % N;

        // ── Asymmetric rule (breaks circular wait) ──────────────────────────
        // Last philosopher (index N-1) picks RIGHT first, then LEFT
        int first, second;
        if (trainIndex == N - 1) {
            first  = right;   // reversed order for last train
            second = left;
        } else {
            first  = left;    // normal order
            second = right;
        }

        setState(trainIndex, "HUNGRY — waiting for platform locks " + first + " & " + second);
        eventLog.push(OsEventLog.TYPE_BOOKING, "Unit-II",
            "🍽️ Train " + trainIndex + " (" + TRAIN_NAMES[trainIndex] + ") HUNGRY — competing for platform locks [" + first + "] & [" + second + "]",
            pid, "🤤", true);

        System.out.println("[DINING] Train " + trainIndex + " acquiring lock[" + first + "] (first)...");
        platformLocks[first].lock();    // ← may BLOCK here if shared with another booking

        System.out.println("[DINING] Train " + trainIndex + " acquired lock[" + first + "], now acquiring lock[" + second + "]...");
        platformLocks[second].lock();   // ← may BLOCK here

        setState(trainIndex, "EATING — processing booking (holds locks " + first + " & " + second + ")");
        eventLog.push(OsEventLog.TYPE_BOOKING, "Unit-II",
            "🚂 Train " + trainIndex + " (" + TRAIN_NAMES[trainIndex] + ") EATING — holds platform locks [" + first + "] & [" + second + "], processing booking P" + pid,
            pid, "🚂", true);

        System.out.println("[DINING] Train " + trainIndex + " holds both locks. Processing booking P" + pid + ".");
    }

    /**
     * Releases both platform locks after booking is done.
     */
    public void releasePlatformLocks(int trainIndex, int pid, OsEventLog eventLog) {
        int left  = trainIndex;
        int right = (trainIndex + 1) % N;

        int first, second;
        if (trainIndex == N - 1) {
            first  = right;
            second = left;
        } else {
            first  = left;
            second = right;
        }

        // Release in reverse acquisition order; guard with isHeldByCurrentThread()
        if (platformLocks[second].isHeldByCurrentThread()) platformLocks[second].unlock();
        if (platformLocks[first].isHeldByCurrentThread())  platformLocks[first].unlock();

        setState(trainIndex, "THINKING — booking completed, platform locks released");
        eventLog.push(OsEventLog.TYPE_BOOKING, "Unit-II",
            "😴 Train " + trainIndex + " (" + TRAIN_NAMES[trainIndex] + ") THINKING — released platform locks [" + first + "] & [" + second + "] after P" + pid,
            pid, "😴", true);

        System.out.println("[DINING] Train " + trainIndex + " released locks [" + first + "] & [" + second + "].");
    }

    private void setState(int index, String state) {
        states[index] = state;
    }

    /** For admin dashboard polling */
    public Map<String, String> getPhilosopherStates() {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < N; i++) {
            result.put(String.valueOf(i), states[i]);
        }
        return result;
    }

    /** For admin dashboard — show which locks are currently held */
    public Map<String, Object> getLockStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        for (int i = 0; i < N; i++) {
            status.put("lock_" + i, platformLocks[i].isLocked());
        }
        return status;
    }
}
