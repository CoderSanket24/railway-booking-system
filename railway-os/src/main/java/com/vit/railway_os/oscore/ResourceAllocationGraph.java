package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * OS CONCEPT: Deadlock Detection — Resource Allocation Graph (Unit IV)
 *
 * RAILWAY CONTEXT:
 *   Processes = booking threads (users trying to book seats)
 *   Resources = shared railway resources (Booking DB Lock, Payment Gateway, Seat Lock)
 *
 * Graph contains two types of edges:
 *   Assignment Edge  (R → P) : resource R is held by process P
 *   Request Edge     (P → R) : process P is waiting for resource R
 *
 * DEADLOCK DETECTION:
 *   Deadlock ⟺ the Wait-For Graph (WFG) contains a CYCLE.
 *   WFG is derived from RAG: if P waits for R and R is held by Q → edge P → Q.
 *   We detect cycles using DFS with a recursion stack (visited + inStack).
 */
@Component
public class ResourceAllocationGraph {

    // Resource names in our railway system
    public static final String[] RESOURCES = {"DB_LOCK", "PAYMENT_GATEWAY", "SEAT_LOCK"};

    // assignment[resource] = pid holding it (-1 = free)
    private final int[] assignment = {-1, -1, -1};

    // requestEdges[pid] = list of resource indices pid is waiting for
    private final Map<Integer, Set<Integer>> requestEdges = new LinkedHashMap<>();

    // All known pids
    private final Set<Integer> processes = new LinkedHashSet<>();

    private final List<String> log = new ArrayList<>();

    // ── Graph Manipulation ────────────────────────────────────────────────────

    /** Process pid acquires resource r */
    public synchronized void acquire(int pid, int resource) {
        processes.add(pid);
        if (assignment[resource] == -1) {
            assignment[resource] = pid;
            log.add("[RAG] P" + pid + " ACQUIRED " + RESOURCES[resource]);
        } else {
            // Already held — pid must wait
            requestEdges.computeIfAbsent(pid, k -> new LinkedHashSet<>()).add(resource);
            log.add("[RAG] P" + pid + " WAITING for " + RESOURCES[resource] + " (held by P" + assignment[resource] + ")");
        }
    }

    /** Process pid releases resource r */
    public synchronized void release(int pid, int resource) {
        if (assignment[resource] == pid) {
            assignment[resource] = -1;
            log.add("[RAG] P" + pid + " RELEASED " + RESOURCES[resource]);
            // Grant to a waiting process if any
            for (Map.Entry<Integer, Set<Integer>> entry : requestEdges.entrySet()) {
                int waiter = entry.getKey();
                if (entry.getValue().contains(resource)) {
                    assignment[resource] = waiter;
                    entry.getValue().remove(resource);
                    log.add("[RAG] GRANTED " + RESOURCES[resource] + " to waiting P" + waiter);
                    break;
                }
            }
        }
    }

    /** Remove process from graph (used by recovery) */
    public synchronized void terminateProcess(int pid) {
        processes.remove(pid);
        requestEdges.remove(pid);
        for (int r = 0; r < RESOURCES.length; r++) {
            if (assignment[r] == pid) {
                assignment[r] = -1;
                log.add("[RAG] P" + pid + " TERMINATED — released " + RESOURCES[r]);
            }
        }
    }

    // ── Deadlock Detection ───────────────────────────────────────────────────

    /**
     * Build the Wait-For Graph and run DFS cycle detection.
     *
     * Wait-For edge: P → Q  if P waits for a resource held by Q
     *
     * DFS cycle detection:
     *   visited[] = node has been processed
     *   inStack[] = node is on the current DFS path
     *   If we reach a node that is already inStack → cycle detected.
     */
    public synchronized DeadlockResult detectDeadlock() {
        // Build wait-for graph: pid → list of pids it waits on
        Map<Integer, List<Integer>> waitFor = new LinkedHashMap<>();
        for (int pid : processes) {
            waitFor.put(pid, new ArrayList<>());
        }
        for (Map.Entry<Integer, Set<Integer>> entry : requestEdges.entrySet()) {
            int waiter = entry.getKey();
            for (int resource : entry.getValue()) {
                int holder = assignment[resource];
                if (holder != -1 && holder != waiter) {
                    waitFor.computeIfAbsent(waiter, k -> new ArrayList<>()).add(holder);
                }
            }
        }

        // DFS on all nodes
        Set<Integer>  visited = new HashSet<>();
        Set<Integer>  inStack = new HashSet<>();
        List<Integer> cycle   = new ArrayList<>();

        for (int pid : processes) {
            if (!visited.contains(pid)) {
                List<Integer> path = new ArrayList<>();
                if (dfsCycle(pid, waitFor, visited, inStack, path, cycle)) {
                    String msg = "🔴 DEADLOCK DETECTED! Cycle: " + cycle;
                    log.add(msg);
                    return new DeadlockResult(true, cycle, waitFor, log);
                }
            }
        }

        log.add("✅ NO DEADLOCK — System is safe.");
        return new DeadlockResult(false, Collections.emptyList(), waitFor, log);
    }

    private boolean dfsCycle(int node, Map<Integer, List<Integer>> graph,
                             Set<Integer> visited, Set<Integer> inStack,
                             List<Integer> path, List<Integer> cycle) {
        visited.add(node);
        inStack.add(node);
        path.add(node);

        List<Integer> neighbors = graph.getOrDefault(node, Collections.emptyList());
        for (int neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                if (dfsCycle(neighbor, graph, visited, inStack, path, cycle)) {
                    return true;
                }
            } else if (inStack.contains(neighbor)) {
                // Cycle found — extract it
                int idx = path.indexOf(neighbor);
                cycle.addAll(path.subList(idx, path.size()));
                cycle.add(neighbor); // close the cycle
                return true;
            }
        }

        inStack.remove(node);
        path.remove(path.size() - 1);
        return false;
    }

    // ── Demo Setup ────────────────────────────────────────────────────────────

    /** Reset and simulate a classic circular-wait deadlock for demo purposes */
    public synchronized void simulateDeadlock() {
        resetState();
        // P1 holds DB_LOCK, waits for PAYMENT_GATEWAY
        assignment[0] = 1; processes.add(1);
        requestEdges.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(1);
        // P2 holds PAYMENT_GATEWAY, waits for SEAT_LOCK
        assignment[1] = 2; processes.add(2);
        requestEdges.computeIfAbsent(2, k -> new LinkedHashSet<>()).add(2);
        // P3 holds SEAT_LOCK, waits for DB_LOCK → circular wait → DEADLOCK
        assignment[2] = 3; processes.add(3);
        requestEdges.computeIfAbsent(3, k -> new LinkedHashSet<>()).add(0);

        log.add("[RAG] Demo deadlock simulated: P1→P2→P3→P1 (circular wait)");
    }

    /** Set up a safe (no deadlock) scenario */
    public synchronized void simulateSafe() {
        resetState();
        // P1 holds DB_LOCK (not waiting)
        assignment[0] = 1; processes.add(1);
        // P2 holds PAYMENT_GATEWAY, waits for DB_LOCK (P1 will release first)
        assignment[1] = 2; processes.add(2);
        requestEdges.computeIfAbsent(2, k -> new LinkedHashSet<>()).add(0);
        // P3 waits for SEAT_LOCK (free)
        processes.add(3);
        log.add("[RAG] Safe scenario loaded: P2 waits on P1 (no cycle)");
    }

    private void resetState() {
        Arrays.fill(assignment, -1);
        requestEdges.clear();
        processes.clear();
    }

    // ── State Snapshot ────────────────────────────────────────────────────────

    public synchronized Map<String, Object> getState() {
        List<Map<String, Object>> resourceList = new ArrayList<>();
        for (int r = 0; r < RESOURCES.length; r++) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("name",   RESOURCES[r]);
            res.put("holder", assignment[r] == -1 ? "FREE" : "P" + assignment[r]);
            List<String> waiters = new ArrayList<>();
            for (Map.Entry<Integer, Set<Integer>> e : requestEdges.entrySet()) {
                if (e.getValue().contains(r)) waiters.add("P" + e.getKey());
            }
            res.put("waiters", waiters);
            resourceList.add(res);
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("processes",  processes.stream().map(p -> "P" + p).toList());
        state.put("resources",  resourceList);
        state.put("recentLog",  log.size() > 15 ? log.subList(log.size() - 15, log.size()) : log);
        return state;
    }

    // ── Inner result ──────────────────────────────────────────────────────────

    public record DeadlockResult(boolean deadlocked, List<Integer> cycle,
                                  Map<Integer, List<Integer>> waitForGraph, List<String> log) {}
}
