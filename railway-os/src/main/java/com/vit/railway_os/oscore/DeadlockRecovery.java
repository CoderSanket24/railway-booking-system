package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * OS CONCEPT: Deadlock Recovery (Unit IV)
 *
 * RAILWAY CONTEXT:
 *   When the ResourceAllocationGraph detects a deadlock (cycle), the system
 *   must recover. Two strategies are modelled here:
 *
 *   1. PROCESS TERMINATION:
 *      Abort one or more processes in the deadlock cycle until the cycle breaks.
 *      "Victim" is chosen by lowest priority (STANDARD user before TATKAL).
 *      Aborted process loses its booking — seats released back to pool.
 *
 *   2. RESOURCE PREEMPTION:
 *      Forcibly take a resource away from a process (rollback that process)
 *      and give it to another to break the cycle.
 *
 * RECOVERY STEPS:
 *   1. Run deadlock detection (RAG cycle check)
 *   2. If deadlock: select victim using priority heuristic
 *   3. Terminate victim (or preempt its resource)
 *   4. Re-run detection — repeat until safe
 */
@Component
public class DeadlockRecovery {

    // Simulated processes in the deadlock set with priority and resources held
    private final List<DeadlockedProcess> deadlockedSet = new ArrayList<>();
    private final List<String> recoveryLog = new ArrayList<>();

    public DeadlockRecovery() {
        // Pre-populate for demo
        resetDemo();
    }

    public synchronized void resetDemo() {
        deadlockedSet.clear();
        recoveryLog.clear();
        // Simulate 3 processes in deadlock
        deadlockedSet.add(new DeadlockedProcess(101, "STANDARD", 5, List.of("DB_LOCK"), List.of("PAYMENT_GATEWAY")));
        deadlockedSet.add(new DeadlockedProcess(102, "STANDARD", 5, List.of("PAYMENT_GATEWAY"), List.of("SEAT_LOCK")));
        deadlockedSet.add(new DeadlockedProcess(103, "TATKAL",   1, List.of("SEAT_LOCK"), List.of("DB_LOCK")));
        recoveryLog.add("[RECOVERY] Deadlock detected among: P101, P102, P103");
        recoveryLog.add("[RECOVERY] Circular wait: P101→P102→P103→P101");
    }

    /**
     * STRATEGY 1: Process Termination
     * Abort processes one by one (lowest priority first) until deadlock cycle breaks.
     * Returns recovery steps taken.
     */
    public synchronized RecoveryResult recoverByTermination() {
        List<String> steps = new ArrayList<>();
        List<DeadlockedProcess> remaining = new ArrayList<>(deadlockedSet);
        List<Integer> terminated = new ArrayList<>();

        steps.add("=== RECOVERY: Process Termination Strategy ===");
        steps.add("Deadlocked processes: " + remaining.stream().map(p -> "P" + p.pid).toList());

        // Sort by priority descending (higher number = lower priority = first victim)
        remaining.sort(Comparator.comparingInt(p -> -p.priority));

        while (remaining.size() > 1) {
            DeadlockedProcess victim = remaining.remove(0); // lowest priority first
            terminated.add(victim.pid);
            steps.add("🔪 TERMINATE P" + victim.pid + " [" + victim.type + ", priority=" + victim.priority + "]");
            steps.add("   Released resources: " + victim.holds);
            steps.add("   Processes still in set: " + remaining.stream().map(p -> "P" + p.pid).toList());

            // Check if cycle is broken (simplified: cycle breaks when < 2 processes remain
            // or no process is waiting on a resource now held by another in the set)
            if (!hasCycle(remaining)) {
                steps.add("✅ DEADLOCK RESOLVED after terminating: " + terminated);
                steps.add("   Remaining processes continue normally.");
                recoveryLog.addAll(steps);
                return new RecoveryResult(true, "TERMINATION", terminated, steps);
            }
        }

        if (!remaining.isEmpty()) {
            terminated.add(remaining.get(0).pid);
            steps.add("🔪 TERMINATE last process P" + remaining.get(0).pid);
            steps.add("✅ DEADLOCK RESOLVED (all deadlocked processes terminated): " + terminated);
        }

        recoveryLog.addAll(steps);
        return new RecoveryResult(true, "TERMINATION", terminated, steps);
    }

    /**
     * STRATEGY 2: Resource Preemption
     * Forcibly take a resource from one process and give it to another.
     * The preempted process is rolled back to its initial state.
     */
    public synchronized RecoveryResult recoverByPreemption() {
        List<String> steps = new ArrayList<>();
        steps.add("=== RECOVERY: Resource Preemption Strategy ===");

        if (deadlockedSet.isEmpty()) {
            steps.add("No deadlocked processes to recover.");
            return new RecoveryResult(false, "PREEMPTION", List.of(), steps);
        }

        // Pick the process holding the most contested resource → preempt it
        DeadlockedProcess victim = deadlockedSet.stream()
            .filter(p -> !p.holds.isEmpty())
            .min(Comparator.comparingInt(p -> p.priority))  // lowest priority = victim
            .orElse(deadlockedSet.get(0));

        steps.add("⚡ PREEMPT resource '" + victim.holds.get(0) + "' from P" + victim.pid);
        steps.add("   P" + victim.pid + " rolled back to initial booking state.");
        steps.add("   Resource '" + victim.holds.get(0) + "' granted to waiting process P"
                  + deadlockedSet.stream()
                      .filter(p -> p.pid != victim.pid && !p.waitsFor.isEmpty()
                                   && p.waitsFor.contains(victim.holds.get(0)))
                      .map(p -> "" + p.pid).findFirst().orElse("?"));
        steps.add("✅ DEADLOCK RESOLVED via preemption. P" + victim.pid + " re-queued.");
        steps.add("   NOTE: Starvation prevention needed — P" + victim.pid + " gets priority boost.");

        recoveryLog.addAll(steps);
        return new RecoveryResult(true, "PREEMPTION", List.of(victim.pid), steps);
    }

    private boolean hasCycle(List<DeadlockedProcess> procs) {
        // Simplified cycle check: deadlock exists if any process in the set
        // waits for a resource held by another process in the set
        Set<String> held = new HashSet<>();
        for (DeadlockedProcess p : procs) held.addAll(p.holds);
        for (DeadlockedProcess p : procs) {
            for (String want : p.waitsFor) {
                if (held.contains(want)) return true;
            }
        }
        return false;
    }

    public synchronized Map<String, Object> getState() {
        List<Map<String, Object>> procList = new ArrayList<>();
        for (DeadlockedProcess p : deadlockedSet) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("pid",      "P" + p.pid);
            row.put("type",     p.type);
            row.put("priority", p.priority);
            row.put("holds",    p.holds);
            row.put("waitsFor", p.waitsFor);
            procList.add(row);
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("deadlockedProcesses", procList);
        state.put("recoveryLog", recoveryLog.size() > 20
                  ? recoveryLog.subList(recoveryLog.size() - 20, recoveryLog.size())
                  : recoveryLog);
        return state;
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private record DeadlockedProcess(int pid, String type, int priority,
                                      List<String> holds, List<String> waitsFor) {}

    public record RecoveryResult(boolean resolved, String strategy,
                                  List<Integer> affected, List<String> steps) {}
}
