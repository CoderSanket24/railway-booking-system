package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * OS CONCEPT: Disk Scheduling Algorithms (Unit VI)
 *
 * RAILWAY CONTEXT:
 *   Disk = the MySQL storage storing passenger booking records.
 *   Disk head = read/write head that moves between tracks.
 *   Tracks 0-199 represent cylinder positions.
 *   Each booking record read/write generates a disk I/O request.
 *   Disk scheduling decides the ORDER in which these I/O requests are served.
 *
 * ALGORITHMS:
 *   FCFS   — serve requests in arrival order (simple, fair but high seek time)
 *   SSTF   — serve nearest track first (min seek, may starve far requests)
 *   SCAN   — head sweeps left→right→left (elevator algorithm)
 *   C-SCAN — head sweeps only left→right, then jumps back to 0 (circular, fairer)
 *
 * KEY METRIC:
 *   Total Head Movement = sum of |track_i - track_{i-1}| for all moves (lower = better)
 */
@Component
public class DiskScheduler {

    public static final int MIN_TRACK = 0;
    public static final int MAX_TRACK = 199;

    // Default demo: initial head position and request queue
    private static final int DEFAULT_HEAD = 50;
    private static final int[] DEFAULT_REQUESTS = {82, 170, 43, 140, 24, 16, 190};

    // ── FCFS ─────────────────────────────────────────────────────────────────

    /**
     * First Come First Serve — process requests in order of arrival.
     * Simple but ignores proximity → high average seek time.
     */
    public SchedulingResult fcfs(int head, int[] requests) {
        List<Integer> order      = new ArrayList<>();
        List<String>  steps      = new ArrayList<>();
        int totalMovement = 0;
        int current = head;

        steps.add("FCFS: Initial head position = " + head);
        for (int req : requests) {
            int move = Math.abs(req - current);
            totalMovement += move;
            steps.add("Head " + current + " → " + req + " (move: " + move + ")");
            order.add(req);
            current = req;
        }

        steps.add("✅ Total head movement: " + totalMovement + " cylinders");
        return new SchedulingResult("FCFS", head, requests, order, totalMovement, steps);
    }

    // ── SSTF ─────────────────────────────────────────────────────────────────

    /**
     * Shortest Seek Time First — always serve the request closest to current head.
     * Better average seek time; risk of starvation for far-away tracks.
     */
    public SchedulingResult sstf(int head, int[] requests) {
        List<Integer> remaining = new ArrayList<>();
        for (int r : requests) remaining.add(r);
        List<Integer> order      = new ArrayList<>();
        List<String>  steps      = new ArrayList<>();
        int totalMovement = 0;
        int current = head;

        steps.add("SSTF: Initial head position = " + head);
        while (!remaining.isEmpty()) {
            final int pos = current;  // capture for lambda (must be effectively final)
            int nearest = remaining.stream()
                .min(Comparator.comparingInt(r -> Math.abs(r - pos)))
                .orElseThrow();
            int move = Math.abs(nearest - current);
            totalMovement += move;
            steps.add("Head " + current + " → " + nearest + " (nearest, move: " + move + ")");
            order.add(nearest);
            remaining.remove(Integer.valueOf(nearest));
            current = nearest;
        }

        steps.add("✅ Total head movement: " + totalMovement + " cylinders");
        return new SchedulingResult("SSTF", head, requests, order, totalMovement, steps);
    }

    // ── SCAN (Elevator) ───────────────────────────────────────────────────────

    /**
     * SCAN — head moves in one direction serving all requests, then reverses.
     * Like an elevator: go up serving floors, hit top, come back down.
     * @param direction "LEFT" or "RIGHT" (initial direction)
     */
    public SchedulingResult scan(int head, int[] requests, String direction) {
        List<Integer> sorted    = new ArrayList<>();
        for (int r : requests) sorted.add(r);
        Collections.sort(sorted);

        List<Integer> order     = new ArrayList<>();
        List<String>  steps     = new ArrayList<>();
        int totalMovement = 0;
        int current = head;

        steps.add("SCAN: Initial head position = " + head + ", direction = " + direction);

        int idx = Collections.binarySearch(sorted, head);
        if (idx < 0) idx = -(idx + 1);

        List<Integer> left  = new ArrayList<>(sorted.subList(0, idx));
        List<Integer> right = new ArrayList<>(sorted.subList(idx, sorted.size()));
        Collections.reverse(left); // serve right-to-left on the way back

        List<List<Integer>> pass = direction.equalsIgnoreCase("RIGHT")
            ? List.of(right, left) : List.of(left, right);

        for (List<Integer> side : pass) {
            for (int track : side) {
                int move = Math.abs(track - current);
                totalMovement += move;
                steps.add("Head " + current + " → " + track + " (move: " + move + ")");
                order.add(track);
                current = track;
            }
            if (!side.isEmpty()) steps.add("--- Direction reversed ---");
        }

        steps.add("✅ Total head movement: " + totalMovement + " cylinders");
        return new SchedulingResult("SCAN", head, requests, order, totalMovement, steps);
    }

    // ── C-SCAN (Circular SCAN) ────────────────────────────────────────────────

    /**
     * C-SCAN — head moves only in one direction (right).
     * After reaching the rightmost request, jumps back to track 0 (no service on return).
     * Provides more uniform wait time than SCAN.
     */
    public SchedulingResult cscan(int head, int[] requests) {
        List<Integer> sorted = new ArrayList<>();
        for (int r : requests) sorted.add(r);
        Collections.sort(sorted);

        List<Integer> order = new ArrayList<>();
        List<String>  steps = new ArrayList<>();
        int totalMovement = 0;
        int current = head;

        steps.add("C-SCAN: Initial head position = " + head + " (moves RIGHT only)");

        int idx = Collections.binarySearch(sorted, head);
        if (idx < 0) idx = -(idx + 1);

        List<Integer> right = new ArrayList<>(sorted.subList(idx, sorted.size()));
        List<Integer> wrap  = new ArrayList<>(sorted.subList(0, idx)); // requests < head, served after wrap

        for (int track : right) {
            int move = Math.abs(track - current);
            totalMovement += move;
            steps.add("Head " + current + " → " + track + " (move: " + move + ")");
            order.add(track);
            current = track;
        }

        if (!wrap.isEmpty()) {
            // Jump to beginning
            int jumpMove = current + wrap.get(0); // to track 0 + across to first left request
            totalMovement += jumpMove;
            steps.add("--- JUMP: Head " + current + " → 0 → " + wrap.get(0) + " (wrap: " + jumpMove + ") ---");
            current = wrap.get(0);
            order.add(wrap.get(0));

            for (int i = 1; i < wrap.size(); i++) {
                int track = wrap.get(i);
                int move  = Math.abs(track - current);
                totalMovement += move;
                steps.add("Head " + current + " → " + track + " (move: " + move + ")");
                order.add(track);
                current = track;
            }
        }

        steps.add("✅ Total head movement: " + totalMovement + " cylinders");
        return new SchedulingResult("C-SCAN", head, requests, order, totalMovement, steps);
    }

    // ── Run All & Compare ─────────────────────────────────────────────────────

    public synchronized Map<String, Object> runAll(int head, int[] requests) {
        if (requests == null || requests.length == 0) requests = DEFAULT_REQUESTS;
        if (head < 0) head = DEFAULT_HEAD;

        SchedulingResult fcfsR  = fcfs(head, requests);
        SchedulingResult sstfR  = sstf(head, requests);
        SchedulingResult scanR  = scan(head, requests, "RIGHT");
        SchedulingResult cscanR = cscan(head, requests);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("initialHead",  head);
        result.put("requests",     requests);
        result.put("maxTrack",     MAX_TRACK);
        result.put("comparison", List.of(
            summaryOf(fcfsR), summaryOf(sstfR), summaryOf(scanR), summaryOf(cscanR)
        ));
        result.put("FCFS",   fcfsR);
        result.put("SSTF",   sstfR);
        result.put("SCAN",   scanR);
        result.put("C-SCAN", cscanR);
        return result;
    }

    private Map<String, Object> summaryOf(SchedulingResult r) {
        return Map.of(
            "algorithm",     r.algorithm(),
            "totalMovement", r.totalMovement(),
            "serviceOrder",  r.serviceOrder()
        );
    }

    // ── Result record ─────────────────────────────────────────────────────────

    public record SchedulingResult(String algorithm, int initialHead, int[] requests,
                                    List<Integer> serviceOrder, int totalMovement, List<String> steps) {}
}
