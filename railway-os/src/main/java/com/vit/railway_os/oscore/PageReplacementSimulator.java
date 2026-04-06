package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * OS CONCEPT: Page Replacement Algorithms (Unit V)
 *
 * RAILWAY CONTEXT:
 *   Pages = train route timetable pages cached in main memory.
 *   Page frames = limited slots in RAM for route data.
 *   Page fault = requesting a route not currently cached → load from disk.
 *
 *   Reference string = list of train route IDs accessed in order by users.
 *   e.g. [12951, 15001, 12952, 12951, 16032, 15001, 16032, 12951 ...]
 *
 * ALGORITHMS IMPLEMENTED:
 *   FIFO    — evict the page that has been in memory the longest
 *   LRU     — evict the page that was Least Recently Used
 *   OPTIMAL — evict the page that won't be used for the longest time (Belady)
 *   CLOCK   — "second chance" approximation of LRU using a circular list + reference bit
 *
 * KEY METRIC:
 *   Page Fault Rate = (pageFaults / totalReferences) × 100%
 *   Fewer page faults → better algorithm for this reference string.
 */
@Component
public class PageReplacementSimulator {

    // Default reference string: train route IDs
    private static final int[] DEFAULT_REFERENCES = {
        1, 2, 3, 4, 1, 2, 5, 1, 2, 3, 4, 5,
        3, 2, 1, 4, 5, 1, 2, 3, 4, 5, 2, 1
    };
    private static final int DEFAULT_FRAMES = 4;

    // ── FIFO ─────────────────────────────────────────────────────────────────

    public SimulationResult fifo(int[] refs, int frames) {
        Queue<Integer> queue = new LinkedList<>();     // tracks insertion order
        Set<Integer>   inMem = new LinkedHashSet<>();
        List<FrameSnapshot> snapshots = new ArrayList<>();
        int faults = 0;

        for (int page : refs) {
            boolean fault = !inMem.contains(page);
            if (fault) {
                faults++;
                if (inMem.size() == frames) {
                    int evict = queue.poll();
                    inMem.remove(evict);
                }
                inMem.add(page);
                queue.add(page);
            }
            snapshots.add(new FrameSnapshot(page, new ArrayList<>(inMem), fault, "FIFO",
                fault ? (inMem.size() > 1 ? "Evicted: " + queue.peek() : "Loaded") : "Hit"));
        }
        return new SimulationResult("FIFO", refs.length, frames, faults, snapshots);
    }

    // ── LRU ─────────────────────────────────────────────────────────────────

    public SimulationResult lru(int[] refs, int frames) {
        LinkedHashSet<Integer> memory = new LinkedHashSet<>(); // LRU order: head=LRU
        List<FrameSnapshot> snapshots = new ArrayList<>();
        int faults = 0;

        for (int page : refs) {
            boolean fault = !memory.contains(page);
            String detail;
            if (fault) {
                faults++;
                int evicted = -1;
                if (memory.size() == frames) {
                    evicted = memory.iterator().next(); // head = least recently used
                    memory.remove(evicted);
                }
                memory.add(page);
                detail = evicted == -1 ? "Loaded" : "Evicted: Route-" + evicted;
            } else {
                // Move to end (most recently used)
                memory.remove(page);
                memory.add(page);
                detail = "Hit";
            }
            snapshots.add(new FrameSnapshot(page, new ArrayList<>(memory), fault, "LRU", detail));
        }
        return new SimulationResult("LRU", refs.length, frames, faults, snapshots);
    }

    // ── OPTIMAL (Belady) ──────────────────────────────────────────────────────

    public SimulationResult optimal(int[] refs, int frames) {
        Set<Integer> memory = new LinkedHashSet<>();
        List<FrameSnapshot> snapshots = new ArrayList<>();
        int faults = 0;

        for (int i = 0; i < refs.length; i++) {
            int page = refs[i];
            boolean fault = !memory.contains(page);
            String detail;
            if (fault) {
                faults++;
                if (memory.size() == frames) {
                    // Evict page used farthest in the future (or never again)
                    int evict = findOptimalEvict(memory, refs, i + 1);
                    memory.remove(evict);
                    detail = "Evicted: Route-" + evict + " (used farthest/never)";
                } else {
                    detail = "Loaded";
                }
                memory.add(page);
            } else {
                detail = "Hit";
            }
            snapshots.add(new FrameSnapshot(page, new ArrayList<>(memory), fault, "OPTIMAL", detail));
        }
        return new SimulationResult("OPTIMAL", refs.length, frames, faults, snapshots);
    }

    private int findOptimalEvict(Set<Integer> memory, int[] refs, int fromIndex) {
        int evict = -1, farthest = -1;
        for (int page : memory) {
            int nextUse = farthest; // default = never used again = evict candidate
            for (int j = fromIndex; j < refs.length; j++) {
                if (refs[j] == page) { nextUse = j; break; }
            }
            if (nextUse == farthest && evict == -1) { evict = page; farthest = Integer.MAX_VALUE; }
            else if (nextUse > farthest || (nextUse == farthest && evict == -1)) {
                evict = page; farthest = nextUse;
            }
        }
        return evict == -1 ? memory.iterator().next() : evict;
    }

    // ── CLOCK (Second Chance) ─────────────────────────────────────────────────

    public SimulationResult clock(int[] refs, int frames) {
        int[] frame  = new int[frames];    // page in each slot
        int[] refBit = new int[frames];    // reference bit
        Arrays.fill(frame, -1);
        int hand = 0;
        List<FrameSnapshot> snapshots = new ArrayList<>();
        int faults = 0;

        for (int page : refs) {
            // Check if page is already in memory
            boolean hit = false;
            for (int i = 0; i < frames; i++) {
                if (frame[i] == page) {
                    refBit[i] = 1; // give second chance
                    hit = true;
                    // Build snapshot
                    List<Integer> snap = new ArrayList<>();
                    for (int f : frame) if (f != -1) snap.add(f);
                    snapshots.add(new FrameSnapshot(page, snap, false, "CLOCK", "Hit (ref bit set)"));
                    break;
                }
            }
            if (hit) continue;

            // Page fault
            faults++;
            // Find a slot with ref bit = 0 (clock hand sweeps)
            while (refBit[hand] == 1) {
                refBit[hand] = 0; // clear second chance
                hand = (hand + 1) % frames;
            }
            int evicted = frame[hand];
            frame[hand]  = page;
            refBit[hand] = 1;
            hand = (hand + 1) % frames;

            List<Integer> snap = new ArrayList<>();
            for (int f : frame) if (f != -1) snap.add(f);
            String detail = evicted == -1 ? "Loaded" : "Evicted: Route-" + evicted + " (second chance exhausted)";
            snapshots.add(new FrameSnapshot(page, snap, true, "CLOCK", detail));
        }
        return new SimulationResult("CLOCK", refs.length, frames, faults, snapshots);
    }

    // ── Run All Four & Compare ───────────────────────────────────────────────

    public synchronized Map<String, Object> runAll(int[] refs, int frames) {
        if (refs == null || refs.length == 0) refs = DEFAULT_REFERENCES;
        if (frames <= 0) frames = DEFAULT_FRAMES;

        SimulationResult fifoRes    = fifo(refs, frames);
        SimulationResult lruRes     = lru(refs, frames);
        SimulationResult optRes     = optimal(refs, frames);
        SimulationResult clockRes   = clock(refs, frames);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("referenceString", refs);
        result.put("frames",          frames);
        result.put("comparison", List.of(
            summaryOf(fifoRes), summaryOf(lruRes), summaryOf(optRes), summaryOf(clockRes)
        ));
        result.put("FIFO",    fifoRes);
        result.put("LRU",     lruRes);
        result.put("OPTIMAL", optRes);
        result.put("CLOCK",   clockRes);
        return result;
    }

    private Map<String, Object> summaryOf(SimulationResult r) {
        return Map.of(
            "algorithm",    r.algorithm(),
            "pageFaults",   r.pageFaults(),
            "totalRefs",    r.totalRefs(),
            "faultRate",    String.format("%.1f%%", (r.pageFaults() * 100.0 / r.totalRefs()))
        );
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record SimulationResult(String algorithm, int totalRefs, int frames,
                                    int pageFaults, List<FrameSnapshot> snapshots) {}

    public record FrameSnapshot(int page, List<Integer> framesState, boolean fault,
                                 String algorithm, String detail) {}
}
