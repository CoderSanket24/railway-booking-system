package com.vit.railway_os.oscore;

import com.vit.railway_os.model.Train;
import com.vit.railway_os.repository.TrainRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * OS CONCEPT: Deadlock Avoidance — Banker's Algorithm (Unit IV)
 *
 * ACTUAL PIPELINE IMPLEMENTATION:
 * This is now directly integrated into the real booking pipeline.
 * Resources = The 4 Trains (EXP-12951, EXP-12952, EXP-12953, EXP-12954)
 * Processes = user booking sessions
 */
@Component
public class BankersAlgorithm {

    @Autowired
    private TrainRepository trainRepository;

    // 4 resource types: The 4 Trains
    public static final int NUM_RESOURCES = 4;
    public static final String[] RESOURCE_NAMES = {"EXP-12951", "EXP-12952", "EXP-12953", "EXP-12954"};

    private int[] totalResources = new int[NUM_RESOURCES];
    private int[] available = new int[NUM_RESOURCES];
    private boolean initialized = false;

    private final Map<Integer, int[]> allocation = new LinkedHashMap<>();
    private final Map<Integer, int[]> max         = new LinkedHashMap<>();
    private final List<String>        log          = new ArrayList<>();

    public BankersAlgorithm() {
        log.add("[BANKER] Component instantiated. Waiting for first actual booking to sync with DB.");
    }

    public static int getTrainIndex(String trainNumber) {
        for (int i = 0; i < NUM_RESOURCES; i++) {
            if (RESOURCE_NAMES[i].equals(trainNumber)) return i;
        }
        return -1;
    }

    private synchronized void ensureInitialized() {
        if (!initialized) {
            List<Train> trains = trainRepository.findAll();
            if (trains.size() >= 4) {
                for (int i = 0; i < NUM_RESOURCES; i++) {
                    String tNum = RESOURCE_NAMES[i];
                    Train t = trains.stream().filter(tr -> tr.getTrainNumber().equals(tNum)).findFirst().orElse(null);
                    if (t != null) {
                        totalResources[i] = t.getTotalSeats();
                        available[i] = t.getAvailableSeats();
                    }
                }
                initialized = true;
                log.add("[BANKER] System synced with actual train database.");
                log.add("[BANKER] Available: " + Arrays.toString(available));
            }
        }
    }

    public synchronized void registerIfNeeded(int pid, int[] request) {
        ensureInitialized();
        
        boolean isNew = !max.containsKey(pid);
        if (isNew) {
            max.put(pid, new int[NUM_RESOURCES]);
            allocation.put(pid, new int[NUM_RESOURCES]);
        }
        
        int[] m = max.get(pid);
        boolean expanded = false;
        
        for (int i = 0; i < NUM_RESOURCES; i++) {
            // If they request a train they never declared demand for, dynamically expand it to 6
            // This prevents a sold-out train from blocking bookings on other unrelated trains.
            if (request[i] > 0 && m[i] == 0) {
                m[i] = Math.max(6, request[i]);
                expanded = true;
            }
        }
        
        if (isNew) {
            log.add("[BANKER] Registered process P" + pid + " with localized max demand " + Arrays.toString(m));
        } else if (expanded) {
            log.add("[BANKER] Process P" + pid + " requested a new train. Expanded max demand " + Arrays.toString(m));
        }
    }

    private int[] computeNeed(int pid) {
        int[] m = max.get(pid);
        int[] a = allocation.get(pid);
        int[] need = new int[NUM_RESOURCES];
        for (int j = 0; j < NUM_RESOURCES; j++) {
            need[j] = m[j] - a[j];
        }
        return need;
    }

    public synchronized SafetyResult isSafeState() {
        ensureInitialized();
        List<Integer> pids = new ArrayList<>(allocation.keySet());
        int n = pids.size();

        int[] work    = available.clone();
        boolean[] finish = new boolean[n];
        List<Integer> safeSequence = new ArrayList<>();
        List<String>  steps        = new ArrayList<>();

        steps.add("Work (available) = " + Arrays.toString(work));

        int count = 0;
        while (count < n) {
            boolean found = false;
            for (int i = 0; i < n; i++) {
                if (!finish[i]) {
                    int pid    = pids.get(i);
                    int[] need = computeNeed(pid);
                    if (canAllocate(need, work)) {
                        int[] alloc = allocation.get(pid);
                        for (int j = 0; j < NUM_RESOURCES; j++) {
                            work[j] += alloc[j];
                        }
                        finish[i] = true;
                        safeSequence.add(pid);
                        steps.add("[SAFE] Process P" + pid + " can finish. Work → " + Arrays.toString(work));
                        count++;
                        found = true;
                    }
                }
            }
            if (!found) break; // No process could proceed → unsafe
        }

        boolean safe = (count == n);
        if (safe) {
            steps.add("✅ SAFE STATE — Safe Sequence: " + safeSequence);
        } else {
            steps.add("❌ UNSAFE STATE — Deadlock may occur!");
        }

        log.addAll(steps);
        return new SafetyResult(safe, safeSequence, steps);
    }

    public synchronized RequestResult requestResources(int pid, int[] request) {
        registerIfNeeded(pid, request);
        List<String> steps = new ArrayList<>();
        steps.add("[REQUEST] Process P" + pid + " requests " + Arrays.toString(request));

        int[] need = computeNeed(pid);
        for (int j = 0; j < NUM_RESOURCES; j++) {
            if (request[j] > need[j]) {
                steps.add("❌ ERROR: Request exceeds maximum declared need!");
                log.addAll(steps);
                return new RequestResult(false, "EXCEEDS_NEED", steps);
            }
        }

        for (int j = 0; j < NUM_RESOURCES; j++) {
            if (request[j] > available[j]) {
                steps.add("⏳ Process P" + pid + " must WAIT — not enough resources available.");
                log.addAll(steps);
                return new RequestResult(false, "WAIT", steps);
            }
        }

        // Pretend allocate
        for (int j = 0; j < NUM_RESOURCES; j++) {
            available[j]            -= request[j];
            allocation.get(pid)[j]  += request[j];
        }
        steps.add("[BANKER] Pretending to allocate. Available now: " + Arrays.toString(available));

        SafetyResult safety = isSafeState();
        if (safety.isSafe()) {
            steps.add("✅ GRANTED — Safe sequence: " + safety.safeSequence());
            log.addAll(steps);
            return new RequestResult(true, "GRANTED", steps);
        } else {
            // Rollback
            for (int j = 0; j < NUM_RESOURCES; j++) {
                available[j]            += request[j];
                allocation.get(pid)[j]  -= request[j];
            }
            steps.add("❌ DENIED — Would lead to UNSAFE STATE. Resources rolled back.");
            log.addAll(steps);
            return new RequestResult(false, "UNSAFE", steps);
        }
    }

    private boolean canAllocate(int[] need, int[] work) {
        for (int j = 0; j < NUM_RESOURCES; j++) {
            if (need[j] > work[j]) return false;
        }
        return true;
    }

    public synchronized Map<String, Object> getState() {
        ensureInitialized();
        List<Map<String, Object>> table = new ArrayList<>();
        for (int pid : allocation.keySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("pid",        "P" + pid);
            row.put("allocation", allocation.get(pid));
            row.put("max",        max.get(pid));
            row.put("need",       computeNeed(pid));
            table.add(row);
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("resourceNames",   RESOURCE_NAMES);
        state.put("totalResources",  totalResources);
        state.put("available",       available);
        state.put("processTable",    table);
        state.put("recentLog",       log.size() > 20 ? log.subList(log.size() - 20, log.size()) : log);
        return state;
    }

    public record SafetyResult(boolean isSafe, List<Integer> safeSequence, List<String> steps) {}
    public record RequestResult(boolean granted, String status, List<String> steps) {}
}
