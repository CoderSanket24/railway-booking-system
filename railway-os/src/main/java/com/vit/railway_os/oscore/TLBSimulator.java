package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * OS CONCEPT: Translation Lookaside Buffer (TLB) + Address Translation (Unit V)
 *
 * RAILWAY CONTEXT:
 *   Logical address space = indexed booking record IDs (virtual page numbers).
 *   Physical frames = actual memory locations holding booking data.
 *   Page table = mapping of booking record pages → physical frames.
 *   TLB = a fast cache of recently used page→frame mappings.
 *
 * ADDRESS TRANSLATION FLOW:
 *   Logical Address = [ Page Number | Offset ]
 *   bits: total=16, page=8 upper bits, offset=8 lower bits
 *
 *   1. Extract pageNumber from logicalAddress
 *   2. Check TLB → HIT: get frame number directly (fast)
 *                → MISS: look up page table (slower), update TLB
 *   3. Physical Address = frameNumber × PAGE_SIZE + offset
 *
 * TLB MANAGEMENT:
 *   Capacity = 8 entries (2-way set-associative simplified as direct LRU)
 *   Eviction = LRU (least recently used entry replaced on miss)
 *
 * KEY METRICS:
 *   TLB Hit Rate  = hits  / total accesses × 100%
 *   TLB Miss Rate = misses / total accesses × 100%
 *   Effective Memory Access Time (EMAT):
 *     = Hit Rate × (TLB_ACCESS + MEMORY_ACCESS)
 *     + Miss Rate × (TLB_ACCESS + 2×MEMORY_ACCESS)
 *   (the "2×" is because a miss requires accessing page table ALSO in main memory)
 */
@Component
public class TLBSimulator {

    private static final int OFFSET_BITS = 8;  // lower 8 bits = offset (upper 8 bits = page number)
    private static final int PAGE_SIZE   = 1 << OFFSET_BITS; // 256 bytes
    private static final int TLB_SIZE    = 8;  // TLB can hold 8 entries

    private static final int TLB_ACCESS_TIME    = 10;  // ns
    private static final int MEMORY_ACCESS_TIME = 100; // ns

    // TLB: LinkedHashMap with access-order = true → last-accessed entry at end
    private final LinkedHashMap<Integer, Integer> tlb = new LinkedHashMap<>(TLB_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
            if (size() > TLB_SIZE) {
                translationLog.add("[TLB EVICT] Page " + eldest.getKey() + " evicted from TLB (LRU)");
                return true;
            }
            return false;
        }
    };

    // Simulated page table: page → frame (pre-populated)
    private final Map<Integer, Integer> pageTable = new LinkedHashMap<>();

    private int hits = 0, misses = 0;
    private final List<String> translationLog = new ArrayList<>();

    public TLBSimulator() {
        // Pre-populate page table with 32 pages → frames 0-31
        for (int page = 0; page < 32; page++) {
            pageTable.put(page, page); // simple 1:1 mapping for demo
        }
        translationLog.add("[TLB] Initialised. TLB size=" + TLB_SIZE + " entries, Page size=" + PAGE_SIZE + "B");
        translationLog.add("[TLB] Page table loaded with 32 entries (pages 0-31).");
    }

    /**
     * Translate a 16-bit logical address to a physical address.
     * @param logicalAddress 0..65535
     * @return TranslationResult with physical address and timing info
     */
    public synchronized TranslationResult translate(int logicalAddress) {
        int pageNumber = (logicalAddress >> OFFSET_BITS) & 0xFF;
        int offset     = logicalAddress & (PAGE_SIZE - 1);

        boolean tlbHit = tlb.containsKey(pageNumber);
        int frameNumber;
        int accessTime;
        String detail;

        if (tlbHit) {
            hits++;
            frameNumber = tlb.get(pageNumber);
            accessTime  = TLB_ACCESS_TIME + MEMORY_ACCESS_TIME;
            detail = "TLB HIT  → Page " + pageNumber + " → Frame " + frameNumber
                   + " (time: " + TLB_ACCESS_TIME + "ns TLB + " + MEMORY_ACCESS_TIME + "ns MEM)";
        } else {
            misses++;
            // Look up page table
            if (!pageTable.containsKey(pageNumber)) {
                translationLog.add("[TLB] ❌ PAGE FAULT — page " + pageNumber + " not in page table!");
                return new TranslationResult(logicalAddress, -1, pageNumber, offset, false, true,
                    TLB_ACCESS_TIME + 2 * MEMORY_ACCESS_TIME, "PAGE FAULT — not in page table");
            }
            frameNumber = pageTable.get(pageNumber);
            tlb.put(pageNumber, frameNumber); // bring into TLB
            accessTime  = TLB_ACCESS_TIME + 2 * MEMORY_ACCESS_TIME; // TLB + page table + data
            detail = "TLB MISS → Page " + pageNumber + " looked up in page table → Frame " + frameNumber
                   + " (time: " + TLB_ACCESS_TIME + "ns TLB + " + MEMORY_ACCESS_TIME + "ns table + " + MEMORY_ACCESS_TIME + "ns MEM)";
        }

        int physicalAddress = frameNumber * PAGE_SIZE + offset;
        translationLog.add("[TLB] Logical 0x" + Integer.toHexString(logicalAddress)
            + " → " + detail + " → Physical 0x" + Integer.toHexString(physicalAddress));

        return new TranslationResult(logicalAddress, physicalAddress, pageNumber, offset,
            tlbHit, false, accessTime, detail);
    }

    /** Translate multiple addresses in a batch — useful for showing access pattern */
    public synchronized List<TranslationResult> translateBatch(int[] logicalAddresses) {
        List<TranslationResult> results = new ArrayList<>();
        for (int addr : logicalAddresses) {
            results.add(translate(addr));
        }
        return results;
    }

    /** Run a demo with a realistic booking workload reference pattern */
    public synchronized Map<String, Object> runDemo() {
        // Reset stats
        hits = 0; misses = 0; tlb.clear(); translationLog.clear();
        translationLog.add("[TLB] === Demo started ===");

        // Sample logical addresses: booking record lookups (pages 0-10, repeated)
        int[] addresses = {
            0x0020, 0x0120, 0x0220, 0x0020, 0x0320,  // Page 0,1,2,0,3
            0x0020, 0x0120, 0x0420, 0x0020, 0x0120,  // Page 0,1,4,0,1 (many hits)
            0x0520, 0x0620, 0x0720, 0x0820, 0x0120   // Page 5,6,7,8 (misses), 1 (hit)
        };

        List<TranslationResult> results = translateBatch(addresses);
        return buildState(results);
    }

    /** Flush TLB (OS context switch — new process should not see old mappings) */
    public synchronized void flushTLB() {
        tlb.clear();
        translationLog.add("[TLB] ⚡ TLB FLUSHED (context switch)");
    }

    public synchronized Map<String, Object> getState() {
        return buildState(null);
    }

    private Map<String, Object> buildState(List<TranslationResult> recent) {
        int total = hits + misses;
        double hitRate  = total == 0 ? 0 : (hits  * 100.0 / total);
        double missRate = total == 0 ? 0 : (misses * 100.0 / total);

        // EMAT = h*(t+m) + (1-h)*(t+2m) where h=hit rate, t=TLB time, m=mem time
        double h = total == 0 ? 0 : (double) hits / total;
        double emat = h * (TLB_ACCESS_TIME + MEMORY_ACCESS_TIME)
                    + (1 - h) * (TLB_ACCESS_TIME + 2 * MEMORY_ACCESS_TIME);

        List<Map<String, Object>> tlbEntries = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : tlb.entrySet()) {
            tlbEntries.add(Map.of("page", e.getKey(), "frame", e.getValue()));
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("tlbSize",         TLB_SIZE);
        state.put("pageSize",        PAGE_SIZE + "B");
        state.put("tlbAccessTimeNs", TLB_ACCESS_TIME);
        state.put("memAccessTimeNs", MEMORY_ACCESS_TIME);
        state.put("hits",            hits);
        state.put("misses",          misses);
        state.put("totalAccesses",   total);
        state.put("hitRate",         String.format("%.1f%%", hitRate));
        state.put("missRate",        String.format("%.1f%%", missRate));
        state.put("ematNs",          String.format("%.1fns", emat));
        state.put("tlbContents",     tlbEntries);
        state.put("recentLog",       translationLog.size() > 20
            ? translationLog.subList(translationLog.size()-20, translationLog.size())
            : translationLog);
        if (recent != null) state.put("recentTranslations", recent);
        return state;
    }

    // ── Result record ─────────────────────────────────────────────────────────

    public record TranslationResult(int logicalAddress, int physicalAddress,
                                     int pageNumber, int offset, boolean tlbHit,
                                     boolean pageFault, int accessTimeNs, String detail) {}
}
