package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * OS CONCEPT: Deadlock Avoidance — Banker's Algorithm (Unit IV)
 *
 * RAILWAY CONTEXT:
 *   Resources = seat types: { AC_SEATS=0, SLEEPER_SEATS=1, GENERAL_SEATS=2 }
 *   Processes = user booking sessions (each user declares max seats they may ever need)
 *
 * The Banker's Algorithm checks whether granting a resource request keeps
 * the system in a SAFE STATE. If it does, the request is granted. Otherwise,
 * the process must wait — deadlock avoidance in action.
 *
 * KEY TERMS:
 *   available[]    — seats currently free in the system
 *   max[][]        — maximum seats each process may ever request
 *   allocation[][] — seats currently allocated to each process
 *   need[][]       — remaining seats each process might still request
 *                    need[i][j] = max[i][j] - allocation[i][j]
 */
@Component
public class BankersAlgorithm {

    // 3 resource types: AC, Sleeper, General
    public static final int NUM_RESOURCES = 3;
    public static final String[] RESOURCE_NAMES = {"AC_SEATS", "SLEEPER_SEATS", "GENERAL_SEATS"};

    // Total resources in the system (simulated railway inventory)
    private int[] totalResources = {10, 20, 30};

    // Available (free) resources
    private int[] available = {3, 5, 10};

    // Dynamic process table (keyed by processId = userId)
    private final Map<Integer, int[]> allocation = new LinkedHashMap<>();
    private final Map<Integer, int[]> max         = new LinkedHashMap<>();
    private final List<String>        log          = new ArrayList<>();

    // ── Initialise with 3 demo processes so the UI has data on startup ───────
    public BankersAlgorithm() {
        // Process 1: user who may need up to {7, 5, 3} AC/Sleeper/General
        addProcess(1, new int[]{7, 5, 3}, new int[]{0, 1, 0});
        // Process 2: up to {3, 2, 2}, currently holding {2, 0, 0}
        addProcess(2, new int[]{3, 2, 2}, new int[]{2, 0, 0});
        // Process 3: up to {9, 0, 2}, currently holding {3, 0, 2}
        addProcess(3, new int[]{9, 0, 2}, new int[]{3, 0, 2});

        log.add("[BANKER] System initialised with 3 demo booking sessions.");
        log.add("[BANKER] Available: " + Arrays.toString(available));
    }

    /** Register a new user booking session with max declaration and current allocation. */
    public synchronized void addProcess(int pid, int[] maxDemand, int[] currentAlloc) {
        max.put(pid, maxDemand.clone());
        allocation.put(pid, currentAlloc.clone());
    }

    /** Compute need matrix: need[i] = max[i] - allocation[i] */
    private int[] computeNeed(int pid) {
        int[] m = max.get(pid);
        int[] a = allocation.get(pid);
        int[] need = new int[NUM_RESOURCES];
        for (int j = 0; j < NUM_RESOURCES; j++) {
            need[j] = m[j] - a[j];
        }
        return need;
    }

    /**
     * SAFETY ALGORITHM
     * Returns the safe sequence (list of PIDs) if system is safe, or empty list if unsafe.
     *
     * Steps:
     *  1. work[] = copy of available
     *  2. finish[] = false for all processes
     *  3. Find an i where finish[i]=false AND need[i] <= work
     *  4. If found: work += allocation[i], finish[i]=true, repeat from 3
     *  5. If all finish[i]=true → SAFE, return sequence
     */
    public synchronized SafetyResult isSafeState() {
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
                        // This process can finish — free its resources
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

    /**
     * RESOURCE-REQUEST ALGORITHM
     * Process `pid` requests `request[]` units.
     * Temporarily grants the request and checks if state remains safe.
     * If safe → grant permanently. Otherwise → rollback and make process wait.
     */
    public synchronized RequestResult requestResources(int pid, int[] request) {
        List<String> steps = new ArrayList<>();
        steps.add("[REQUEST] Process P" + pid + " requests " + Arrays.toString(request));

        // Step 1: request must not exceed need
        int[] need = computeNeed(pid);
        for (int j = 0; j < NUM_RESOURCES; j++) {
            if (request[j] > need[j]) {
                String msg = "❌ ERROR: Request exceeds maximum declared need!";
                steps.add(msg);
                log.addAll(steps);
                return new RequestResult(false, "EXCEEDS_NEED", steps);
            }
        }

        // Step 2: request must not exceed available
        for (int j = 0; j < NUM_RESOURCES; j++) {
            if (request[j] > available[j]) {
                String msg = "⏳ Process P" + pid + " must WAIT — not enough resources available.";
                steps.add(msg);
                log.addAll(steps);
                return new RequestResult(false, "WAIT", steps);
            }
        }

        // Step 3: pretend to allocate
        for (int j = 0; j < NUM_RESOURCES; j++) {
            available[j]            -= request[j];
            allocation.get(pid)[j]  += request[j];
        }
        steps.add("[BANKER] Pretending to allocate. Available now: " + Arrays.toString(available));

        // Step 4: safety check
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean canAllocate(int[] need, int[] work) {
        for (int j = 0; j < NUM_RESOURCES; j++) {
            if (need[j] > work[j]) return false;
        }
        return true;
    }

    public synchronized Map<String, Object> getState() {
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

    // ── Inner result classes ──────────────────────────────────────────────────

    public record SafetyResult(boolean isSafe, List<Integer> safeSequence, List<String> steps) {}

    public record RequestResult(boolean granted, String status, List<String> steps) {}
}
