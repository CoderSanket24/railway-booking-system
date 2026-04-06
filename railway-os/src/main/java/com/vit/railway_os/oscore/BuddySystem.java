package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * OS CONCEPT: Buddy System Memory Allocation (Unit V)
 *
 * RAILWAY CONTEXT:
 *   Simulates memory allocation for booking session objects.
 *   Memory = 1024 KB total.
 *   All allocation sizes are rounded UP to the next power of 2 (buddy block).
 *
 * HOW BUDDY SYSTEM WORKS:
 *   1. Start with one block of size N (must be power-of-2).
 *   2. When a request of size k comes in, find smallest block >= k.
 *   3. If block is too large, SPLIT it into two equal "buddies".
 *   4. Keep splitting until block size == smallest power-of-2 >= k.
 *   5. Allocate that block.
 *
 * DEALLOCATION / MERGING:
 *   When a block is freed, check if its "buddy" is also free.
 *   If yes → MERGE them back into one larger block.
 *   Repeat recursively upward.
 *
 * KEY TERM — "Buddy Address":
 *   buddy(block at address A, size S) = A XOR S
 *   (flips the bit at position log2(S), giving the partner's start address)
 */
@Component
public class BuddySystem {

    private static final int TOTAL_MEMORY = 1024; // KB

    // Free lists indexed by order (order k → block size 2^k)
    // Max order = log2(1024) = 10
    private static final int MAX_ORDER = 10;
    private final List<Set<Integer>> freeLists = new ArrayList<>(); // freeLists[order] = set of free block start addresses

    // Track allocations: start address → size
    private final Map<Integer, AllocationEntry> allocated = new LinkedHashMap<>();
    private final List<String> log = new ArrayList<>();

    public BuddySystem() {
        for (int i = 0; i <= MAX_ORDER; i++) {
            freeLists.add(new LinkedHashSet<>());
        }
        // Whole memory is one free block of order 10 (1024 KB)
        freeLists.get(MAX_ORDER).add(0);
        log.add("[BUDDY] Initialised: 1 free block of " + TOTAL_MEMORY + "KB (order " + MAX_ORDER + ")");
    }

    /** Allocate `sizeKB` KB of memory for process `pid`. Returns the block start address or -1. */
    public synchronized int allocate(int pid, int sizeKB, String processName) {
        int order = orderFor(sizeKB);
        int blockSize = 1 << order;
        log.add("[BUDDY] P" + pid + " \"" + processName + "\" requests " + sizeKB + "KB → block size " + blockSize + "KB (order " + order + ")");

        int foundOrder = -1;
        for (int o = order; o <= MAX_ORDER; o++) {
            if (!freeLists.get(o).isEmpty()) {
                foundOrder = o;
                break;
            }
        }

        if (foundOrder == -1) {
            log.add("[BUDDY] ❌ Out of memory for P" + pid);
            return -1;
        }

        // Split from foundOrder down to order
        int address = freeLists.get(foundOrder).iterator().next();
        freeLists.get(foundOrder).remove(address);

        while (foundOrder > order) {
            foundOrder--;
            int buddyAddress = address + (1 << foundOrder);
            freeLists.get(foundOrder).add(buddyAddress);
            log.add("[BUDDY]  Split → block @" + buddyAddress + " (size " + (1<<foundOrder) + "KB) added to free list order " + foundOrder);
        }

        allocated.put(address, new AllocationEntry(pid, processName, sizeKB, blockSize, address, order));
        log.add("[BUDDY] ✅ Allocated P" + pid + " @ address " + address + "KB (block=" + blockSize + "KB, frag=" + (blockSize-sizeKB) + "KB)");
        return address;
    }

    /** Deallocate the block at `address`, then merge with buddy if possible. */
    public synchronized void deallocate(int address) {
        AllocationEntry entry = allocated.remove(address);
        if (entry == null) {
            log.add("[BUDDY] ❌ No allocation found at address " + address);
            return;
        }

        int order = entry.order;
        int blockSize = 1 << order;
        log.add("[BUDDY] 🗑 Freeing P" + entry.pid + " @ " + address + "KB (size " + blockSize + "KB, order " + order + ")");

        // Merge with buddy while possible
        while (order < MAX_ORDER) {
            int buddy = address ^ (1 << order); // XOR to find buddy address
            if (freeLists.get(order).contains(buddy)) {
                freeLists.get(order).remove(buddy);
                address = Math.min(address, buddy); // merged block starts at lower address
                order++;
                log.add("[BUDDY]  Merged with buddy @" + buddy + " → new block @" + address + " (order " + order + ", size " + (1<<order) + "KB)");
            } else {
                break;
            }
        }

        freeLists.get(order).add(address);
        log.add("[BUDDY]  Block @" + address + " (order " + order + ") returned to free list.");
    }

    /** Smallest power-of-2 >= size */
    private int orderFor(int size) {
        int order = 0;
        while ((1 << order) < size) order++;
        return order;
    }

    public synchronized Map<String, Object> getState() {
        List<Map<String, Object>> freeListView = new ArrayList<>();
        for (int o = 0; o <= MAX_ORDER; o++) {
            if (!freeLists.get(o).isEmpty()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("order",     o);
                row.put("blockSize", (1 << o) + " KB");
                row.put("freeBlocks", freeLists.get(o).stream().sorted().toList());
                freeListView.add(row);
            }
        }

        List<Map<String, Object>> allocView = new ArrayList<>();
        for (AllocationEntry e : allocated.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("pid",         "P" + e.pid);
            row.put("process",     e.processName);
            row.put("address",     e.address + "KB");
            row.put("requested",   e.requestedSize + "KB");
            row.put("blockSize",   e.blockSize + "KB");
            row.put("internalFrag", (e.blockSize - e.requestedSize) + "KB");
            allocView.add(row);
        }

        int totalFree = 0;
        for (int o = 0; o <= MAX_ORDER; o++) totalFree += freeLists.get(o).size() * (1 << o);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("totalMemoryKB", TOTAL_MEMORY);
        state.put("freeMemoryKB",  totalFree);
        state.put("usedMemoryKB",  TOTAL_MEMORY - totalFree);
        state.put("freeLists",     freeListView);
        state.put("allocations",   allocView);
        state.put("recentLog",     log.size() > 20 ? log.subList(log.size()-20, log.size()) : log);
        return state;
    }

    private record AllocationEntry(int pid, String processName, int requestedSize,
                                    int blockSize, int address, int order) {}
}
