package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class OsStateTracker {

    // 1. Process & PCB States
    // Store recent ~50 processes
    private final ConcurrentLinkedQueue<Map<String, Object>> recentProcesses = new ConcurrentLinkedQueue<>();
    private static final int MAX_PROCESS_HISTORY = 50;

    // 2. Mutex State
    private boolean isMutexLocked = false;
    private Integer mutexLockedByPid = null;

    // 3. Producer/Consumer State
    private final AtomicInteger ticketsInBuffer = new AtomicInteger(0);

    // 4. Active Readers (Readers-Writers)
    private final AtomicInteger activeReaders      = new AtomicInteger(0);
    private final AtomicInteger totalReaderSessions = new AtomicInteger(0); // cumulative
    private final AtomicInteger peakConcurrentReaders = new AtomicInteger(0);

    // 5. Dining Philosophers State
    private final Map<Integer, String> philosopherStates = new ConcurrentHashMap<>();

    // --- Process State Methods ---
    public void recordProcessStateChange(int pid, ProcessState oldState, ProcessState newState, long burstTime, String type) {
        Map<String, Object> event = new HashMap<>();
        event.put("pid", pid);
        event.put("oldState", oldState != null ? oldState.name() : "NONE");
        event.put("newState", newState.name());
        event.put("burstTime", burstTime);
        event.put("type", type);
        event.put("timestamp", System.currentTimeMillis());

        recentProcesses.offer(event);
        while (recentProcesses.size() > MAX_PROCESS_HISTORY) {
            recentProcesses.poll();
        }
    }

    public List<Map<String, Object>> getRecentProcesses() {
        return new ArrayList<>(recentProcesses);
    }

    // --- Mutex State Methods ---
    public void setMutexLocked(boolean locked, Integer pid) {
        this.isMutexLocked = locked;
        this.mutexLockedByPid = locked ? pid : null;
    }

    public Map<String, Object> getMutexState() {
        Map<String, Object> state = new HashMap<>();
        state.put("isLocked", isMutexLocked);
        state.put("lockedByPid", mutexLockedByPid);
        return state;
    }

    // --- Producer/Consumer State Methods ---
    public void updateBufferCount(int count) {
        this.ticketsInBuffer.set(count);
    }

    public int getTicketsInBuffer() {
        return ticketsInBuffer.get();
    }

    // --- Active Readers Methods ---
    public void setActiveReaders(int count) {
        int c = Math.max(0, count);
        this.activeReaders.set(c);
        // Track peak concurrent readers
        peakConcurrentReaders.updateAndGet(prev -> Math.max(prev, c));
        // Count every new reader entry (count going up = new session)
        // The caller must call incrementTotalReaderSessions() on entry
    }

    public void incrementTotalReaderSessions() {
        totalReaderSessions.incrementAndGet();
    }

    public int getActiveReaders()        { return activeReaders.get(); }
    public int getTotalReaderSessions()  { return totalReaderSessions.get(); }
    public int getPeakConcurrentReaders(){ return peakConcurrentReaders.get(); }

    // --- Dining Philosophers State Methods ---
    public void updatePhilosopherState(int philId, String state) {
        philosopherStates.put(philId, state);
    }

    public Map<Integer, String> getPhilosopherStates() {
        return new HashMap<>(philosopherStates);
    }
}
