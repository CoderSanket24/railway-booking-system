package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * OS CONCEPT: Memory Partitioning — Fixed & Dynamic (Unit V)
 *
 * RAILWAY CONTEXT:
 *   Total RAM = 1024 KB (simulated)
 *   Each user's booking session needs memory to hold their booking data.
 *   OS partitions this RAM and assigns partitions to sessions.
 *
 * FIXED PARTITIONING:
 *   RAM is divided into equal-sized partitions at startup.
 *   Each partition = 128 KB. 8 partitions total.
 *   Problem: internal fragmentation (process smaller than partition → waste).
 *
 * DYNAMIC PARTITIONING:
 *   Partitions are created exactly as large as the process needs.
 *   Problem: external fragmentation (free holes scattered across memory).
 *
 * PLACEMENT STRATEGIES (for Dynamic):
 *   First Fit  — allocate first hole large enough (fastest)
 *   Best Fit   — smallest hole that fits (least internal waste)
 *   Next Fit   — like First Fit but starts from last allocation
 *   Worst Fit  — largest hole (leaves biggest leftover — avoids tiny fragments)
 */
@Component
public class MemoryPartitionManager {

    private static final int TOTAL_MEMORY_KB = 1024;
    private static final int FIXED_PARTITION_SIZE_KB = 128; // 8 equal partitions
    private static final int NUM_FIXED_PARTITIONS = TOTAL_MEMORY_KB / FIXED_PARTITION_SIZE_KB;

    // ── Fixed Partitioning State ───────────────────────────────────────────────
    private final FixedPartition[] fixedPartitions = new FixedPartition[NUM_FIXED_PARTITIONS];

    // ── Dynamic Partitioning State ─────────────────────────────────────────────
    private final List<DynamicBlock> dynamicMemory = new ArrayList<>();
    private int nextFitPointer = 0; // used by Next Fit strategy

    private final List<String> log = new ArrayList<>();

    public MemoryPartitionManager() {
        resetFixed();
        resetDynamic();
    }

    // ══════════════════════════ FIXED PARTITIONING ═══════════════════════════

    public synchronized void resetFixed() {
        for (int i = 0; i < NUM_FIXED_PARTITIONS; i++) {
            fixedPartitions[i] = new FixedPartition(i, FIXED_PARTITION_SIZE_KB);
        }
        log.add("[FIXED] Memory reset: " + NUM_FIXED_PARTITIONS + " partitions × " + FIXED_PARTITION_SIZE_KB + " KB");
    }

    /** Allocate a fixed partition to a process (first free partition that fits) */
    public synchronized AllocationResult allocateFixed(int pid, int sizeKB, String processName) {
        if (sizeKB > FIXED_PARTITION_SIZE_KB) {
            log.add("[FIXED] ❌ P" + pid + " (" + sizeKB + "KB) exceeds partition size " + FIXED_PARTITION_SIZE_KB + "KB");
            return new AllocationResult(false, -1, 0, "SIZE_EXCEEDS_PARTITION", log);
        }
        for (FixedPartition p : fixedPartitions) {
            if (!p.occupied) {
                p.occupied     = true;
                p.pid          = pid;
                p.processName  = processName;
                p.processSize  = sizeKB;
                p.internalFrag = FIXED_PARTITION_SIZE_KB - sizeKB;
                log.add("[FIXED] ✅ P" + pid + " \"" + processName + "\" (" + sizeKB + "KB) → Partition " + p.id
                        + " | InternalFrag: " + p.internalFrag + "KB");
                return new AllocationResult(true, p.id, p.internalFrag, "ALLOCATED", log);
            }
        }
        log.add("[FIXED] ❌ No free partition for P" + pid);
        return new AllocationResult(false, -1, 0, "NO_FREE_PARTITION", log);
    }

    public synchronized void deallocateFixed(int partitionId) {
        FixedPartition p = fixedPartitions[partitionId];
        log.add("[FIXED] 🗑 Partition " + partitionId + " freed (was: P" + p.pid + ")");
        p.occupied = false; p.pid = -1; p.processName = ""; p.processSize = 0; p.internalFrag = 0;
    }

    // ══════════════════════════ DYNAMIC PARTITIONING ═════════════════════════

    public synchronized void resetDynamic() {
        dynamicMemory.clear();
        dynamicMemory.add(new DynamicBlock(0, TOTAL_MEMORY_KB, true, -1, "")); // one big free block
        nextFitPointer = 0;
        log.add("[DYNAMIC] Memory reset: 1 free block of " + TOTAL_MEMORY_KB + "KB");
    }

    /**
     * Allocate using the specified placement strategy.
     * @param strategy "FIRST_FIT" | "BEST_FIT" | "NEXT_FIT" | "WORST_FIT"
     */
    public synchronized AllocationResult allocateDynamic(int pid, int sizeKB,
                                                          String processName, String strategy) {
        int index = switch (strategy.toUpperCase()) {
            case "BEST_FIT"  -> findBestFit(sizeKB);
            case "NEXT_FIT"  -> findNextFit(sizeKB);
            case "WORST_FIT" -> findWorstFit(sizeKB);
            default          -> findFirstFit(sizeKB);  // FIRST_FIT
        };

        if (index == -1) {
            log.add("[DYNAMIC][" + strategy + "] ❌ No suitable hole for P" + pid + " (" + sizeKB + "KB)");
            return new AllocationResult(false, -1, 0, "NO_SUITABLE_HOLE", log);
        }

        DynamicBlock hole = dynamicMemory.get(index);
        int remaining = hole.size - sizeKB;

        // Replace hole with allocated block + (possibly) a smaller free hole
        DynamicBlock allocated = new DynamicBlock(hole.start, sizeKB, false, pid, processName);
        dynamicMemory.set(index, allocated);

        if (remaining > 0) {
            DynamicBlock leftover = new DynamicBlock(hole.start + sizeKB, remaining, true, -1, "");
            dynamicMemory.add(index + 1, leftover);
        }

        nextFitPointer = index + 1;
        log.add("[DYNAMIC][" + strategy + "] ✅ P" + pid + " \"" + processName + "\" (" + sizeKB
                + "KB) @ " + hole.start + "KB | Hole was " + hole.size + "KB → leftover " + remaining + "KB");
        return new AllocationResult(true, index, 0, "ALLOCATED", log);
    }

    public synchronized void deallocateDynamic(int pid) {
        boolean freed = false;
        for (DynamicBlock b : dynamicMemory) {
            if (!b.free && b.pid == pid) {
                b.free = true; b.pid = -1; b.processName = "FREE";
                log.add("[DYNAMIC] 🗑 Freed " + b.size + "KB block that was P" + pid);
                freed = true;
            }
        }
        if (freed) mergeFreeBlocks();
    }

    /** Merge adjacent free blocks to reduce external fragmentation (compaction step) */
    private void mergeFreeBlocks() {
        for (int i = 0; i < dynamicMemory.size() - 1; ) {
            DynamicBlock a = dynamicMemory.get(i);
            DynamicBlock b = dynamicMemory.get(i + 1);
            if (a.free && b.free) {
                DynamicBlock merged = new DynamicBlock(a.start, a.size + b.size, true, -1, "FREE");
                dynamicMemory.set(i, merged);
                dynamicMemory.remove(i + 1);
                log.add("[DYNAMIC] 🔗 Merged free blocks → " + merged.size + "KB @ " + merged.start + "KB");
            } else {
                i++;
            }
        }
    }

    // ── Placement Strategy Finders ─────────────────────────────────────────────

    private int findFirstFit(int size) {
        for (int i = 0; i < dynamicMemory.size(); i++) {
            if (dynamicMemory.get(i).free && dynamicMemory.get(i).size >= size) return i;
        }
        return -1;
    }

    private int findBestFit(int size) {
        int best = -1;
        int bestSize = Integer.MAX_VALUE;
        for (int i = 0; i < dynamicMemory.size(); i++) {
            DynamicBlock b = dynamicMemory.get(i);
            if (b.free && b.size >= size && b.size < bestSize) {
                bestSize = b.size; best = i;
            }
        }
        return best;
    }

    private int findNextFit(int size) {
        int n = dynamicMemory.size();
        for (int i = 0; i < n; i++) {
            int idx = (nextFitPointer + i) % n;
            DynamicBlock b = dynamicMemory.get(idx);
            if (b.free && b.size >= size) return idx;
        }
        return -1;
    }

    private int findWorstFit(int size) {
        int worst = -1;
        int worstSize = -1;
        for (int i = 0; i < dynamicMemory.size(); i++) {
            DynamicBlock b = dynamicMemory.get(i);
            if (b.free && b.size >= size && b.size > worstSize) {
                worstSize = b.size; worst = i;
            }
        }
        return worst;
    }

    // ── Fragmentation Stats ────────────────────────────────────────────────────

    public synchronized int totalInternalFragmentation() {
        int frag = 0;
        for (FixedPartition p : fixedPartitions) {
            if (p.occupied) frag += p.internalFrag;
        }
        return frag;
    }

    public synchronized int totalExternalFragmentation() {
        // External frag = sum of free holes too small to satisfy any pending request
        return dynamicMemory.stream().filter(b -> b.free && b.size < 64).mapToInt(b -> b.size).sum();
    }

    // ── State Snapshot ─────────────────────────────────────────────────────────

    public synchronized Map<String, Object> getState() {
        // Fixed partitions
        List<Map<String, Object>> fixed = new ArrayList<>();
        for (FixedPartition p : fixedPartitions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",       p.id);
            m.put("size",     FIXED_PARTITION_SIZE_KB);
            m.put("occupied", p.occupied);
            m.put("pid",      p.pid);
            m.put("process",  p.processName);
            m.put("used",     p.processSize);
            m.put("internalFrag", p.internalFrag);
            fixed.add(m);
        }

        // Dynamic blocks
        List<Map<String, Object>> dynamic = new ArrayList<>();
        for (DynamicBlock b : dynamicMemory) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("start",   b.start);
            m.put("size",    b.size);
            m.put("free",    b.free);
            m.put("pid",     b.pid);
            m.put("process", b.processName);
            dynamic.add(m);
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("totalMemoryKB",         TOTAL_MEMORY_KB);
        state.put("fixedPartitionSizeKB",  FIXED_PARTITION_SIZE_KB);
        state.put("fixedPartitions",       fixed);
        state.put("dynamicBlocks",         dynamic);
        state.put("internalFragmentation", totalInternalFragmentation());
        state.put("externalFragmentation", totalExternalFragmentation());
        state.put("recentLog",             log.size() > 20 ? log.subList(log.size()-20, log.size()) : log);
        return state;
    }

    // ── Inner types ────────────────────────────────────────────────────────────

    private static class FixedPartition {
        int id, processSize, internalFrag, pid = -1;
        boolean occupied = false;
        String processName = "";
        FixedPartition(int id, int size) { this.id = id; this.processSize = 0; this.internalFrag = 0; }
    }

    private static class DynamicBlock {
        int start, size, pid;
        boolean free;
        String processName;
        DynamicBlock(int start, int size, boolean free, int pid, String name) {
            this.start = start; this.size = size; this.free = free;
            this.pid = pid; this.processName = name;
        }
    }

    public record AllocationResult(boolean success, int blockIndex, int fragmentation,
                                    String status, List<String> log) {}
}
