package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OS CONCEPT: I/O Buffering (Unit VI)
 *
 * RAILWAY CONTEXT:
 *   Booking data flows from the application logic to the MySQL database.
 *   Unbuffered: every single booking record triggers a separate DB write (slow!).
 *   Buffered: OS accumulates records in a buffer; flushes them together (fast!).
 *
 * BUFFERING TYPES SIMULATED:
 *
 * 1. SINGLE BUFFER:
 *    One buffer. Producer fills it, Consumer drains it.
 *    Producer must wait while Consumer is draining → no overlap.
 *
 * 2. DOUBLE BUFFER:
 *    Two alternating buffers. While Consumer writes Buffer-A to DB,
 *    Producer fills Buffer-B simultaneously → CPU and I/O overlap.
 *    Throughput improvement: ~2× over single buffer.
 *
 * 3. CIRCULAR BUFFER:
 *    N-slot ring buffer. Multiple producers and consumers.
 *    writePointer and readPointer advance modulo N.
 *    Producer blocks when buffer full; Consumer blocks when empty.
 *    (Similar to Producer-Consumer but focused on I/O bandwidth.)
 *
 * KEY FORMULAS:
 *   Single Buffer throughput: T = max(C, T_io) + M
 *     where C=compute time, T_io=I/O time, M=move time
 *   Double Buffer throughput: T = max(C, T_io) (M is overlapped!)
 */
@Component
public class IOBufferManager {

    private static final int BUFFER_SIZE = 8;  // slots in circular buffer

    // ── Single Buffer ─────────────────────────────────────────────────────────
    private final List<String> singleBuffer = new ArrayList<>();
    private boolean singleBufferDraining = false;

    // ── Double Buffer ─────────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private final ArrayList<String>[] doubleBuffers = new ArrayList[]{new ArrayList<>(), new ArrayList<>()};
    private int activeBuffer   = 0; // producer fills this
    private int inactiveBuffer = 1; // consumer drains this

    // ── Circular Buffer ───────────────────────────────────────────────────────
    private final String[] circularBuffer = new String[BUFFER_SIZE];
    private final AtomicInteger writePtr = new AtomicInteger(0);
    private final AtomicInteger readPtr  = new AtomicInteger(0);
    private final AtomicInteger count    = new AtomicInteger(0);

    // Stats
    private int totalProduced = 0;
    private int totalConsumed = 0;
    private int bufferFullStalls  = 0;
    private int bufferEmptyStalls = 0;

    private final List<String> log = new ArrayList<>();

    public IOBufferManager() {
        // Pre-fill some demo data
        preloadDemo();
    }

    private void preloadDemo() {
        log.add("[IO-BUFFER] Initialised. Buffer capacity: " + BUFFER_SIZE + " slots");
        log.add("[IO-BUFFER] Buffering modes: SINGLE | DOUBLE | CIRCULAR");
    }

    // ══════════════════════════ SINGLE BUFFER ════════════════════════════════

    public synchronized boolean singleBufferProduce(String record) {
        if (singleBuffer.size() >= BUFFER_SIZE || singleBufferDraining) {
            bufferFullStalls++;
            log.add("[SINGLE] ⚠ STALL — buffer full or draining. Producer waits.");
            return false;
        }
        singleBuffer.add(record);
        totalProduced++;
        log.add("[SINGLE] ➕ Produced: \"" + record + "\" | Buffer: " + singleBuffer.size() + "/" + BUFFER_SIZE);

        if (singleBuffer.size() == BUFFER_SIZE) {
            singleBufferDraining = true;
            log.add("[SINGLE] 🚀 Buffer full → flushing to DB (Consumer active)");
        }
        return true;
    }

    public synchronized List<String> singleBufferConsume() {
        if (!singleBufferDraining) {
            bufferEmptyStalls++;
            log.add("[SINGLE] ⚠ STALL — buffer not ready. Consumer waits.");
            return Collections.emptyList();
        }
        List<String> batch = new ArrayList<>(singleBuffer);
        totalConsumed += singleBuffer.size();
        singleBuffer.clear();
        singleBufferDraining = false;
        log.add("[SINGLE] ✅ Flushed " + batch.size() + " records to DB. Buffer reset.");
        return batch;
    }

    // ══════════════════════════ DOUBLE BUFFER ════════════════════════════════

    /**
     * Producer writes to activeBuffer.
     * When activeBuffer is full, SWAP: producer moves to the other buffer
     * while consumer simultaneously drains the full one (I/O overlap!).
     */
    public synchronized boolean doubleBufferProduce(String record) {
        List<String> active = doubleBuffers[activeBuffer];
        if (active.size() >= BUFFER_SIZE) {
            // Swap buffers
            log.add("[DOUBLE] ↔ Buffer-" + activeBuffer + " full → SWAP. Producer → Buffer-" + inactiveBuffer);
            int tmp = activeBuffer;
            activeBuffer   = inactiveBuffer;
            inactiveBuffer = tmp;
        }
        doubleBuffers[activeBuffer].add(record);
        totalProduced++;
        log.add("[DOUBLE] ➕ Produced to Buffer-" + activeBuffer + ": \"" + record
                + "\" [" + doubleBuffers[activeBuffer].size() + "/" + BUFFER_SIZE + "]");
        return true;
    }

    public synchronized List<String> doubleBufferFlush() {
        List<String> inactive = doubleBuffers[inactiveBuffer];
        if (inactive.isEmpty()) {
            log.add("[DOUBLE] ⚠ Inactive buffer empty — nothing to flush.");
            return Collections.emptyList();
        }
        List<String> batch = new ArrayList<>(inactive);
        totalConsumed += inactive.size();
        inactive.clear();
        log.add("[DOUBLE] ✅ Flushed Buffer-" + inactiveBuffer + " (" + batch.size() + " records) to DB "
                + "| Buffer-" + activeBuffer + " being filled simultaneously (OVERLAP!)");
        return batch;
    }

    // ══════════════════════════ CIRCULAR BUFFER ═══════════════════════════════

    public synchronized boolean circularWrite(String record) {
        if (count.get() == BUFFER_SIZE) {
            bufferFullStalls++;
            log.add("[CIRCULAR] ⚠ FULL (write=" + writePtr + ", read=" + readPtr + ") — producer blocks");
            return false;
        }
        circularBuffer[writePtr.get()] = record;
        writePtr.set((writePtr.get() + 1) % BUFFER_SIZE);
        count.incrementAndGet();
        totalProduced++;
        log.add("[CIRCULAR] ➕ Write @ slot " + (writePtr.get() - 1 + BUFFER_SIZE) % BUFFER_SIZE
                + ": \"" + record + "\" | count=" + count);
        return true;
    }

    public synchronized String circularRead() {
        if (count.get() == 0) {
            bufferEmptyStalls++;
            log.add("[CIRCULAR] ⚠ EMPTY — consumer blocks");
            return null;
        }
        String record = circularBuffer[readPtr.get()];
        circularBuffer[readPtr.get()] = null;
        readPtr.set((readPtr.get() + 1) % BUFFER_SIZE);
        count.decrementAndGet();
        totalConsumed++;
        log.add("[CIRCULAR] ➖ Read @ slot " + (readPtr.get()-1+BUFFER_SIZE)%BUFFER_SIZE
                + ": \"" + record + "\" | count=" + count);
        return record;
    }

    // ── Demo: simulate a full workload ────────────────────────────────────────

    public synchronized Map<String, Object> runDemo(String type) {
        log.clear();
        totalProduced = totalConsumed = bufferFullStalls = bufferEmptyStalls = 0;

        String[] bookings = {
            "BK001:User42→Train12951:2AC:2seats",
            "BK002:User17→Train15001:SL:4seats",
            "BK003:User88→Train16032:GN:1seat",
            "BK004:User55→Train12952:3A:3seats",
            "BK005:User23→Train12951:SL:2seats",
            "BK006:User91→Train16032:2AC:1seat",
            "BK007:User34→Train15001:GN:5seats",
            "BK008:User67→Train12951:3A:2seats"
        };

        switch (type.toUpperCase()) {
            case "DOUBLE" -> {
                for (String b : bookings) doubleBufferProduce(b);
                doubleBufferFlush();
            }
            case "CIRCULAR" -> {
                for (int i = 0; i < 5; i++) circularWrite(bookings[i]);
                for (int i = 0; i < 3; i++) circularRead();
                for (int i = 5; i < 8; i++) circularWrite(bookings[i]);
            }
            default -> { // SINGLE
                for (String b : bookings) singleBufferProduce(b);
                singleBufferConsume();
            }
        }

        return getState();
    }

    public synchronized Map<String, Object> getState() {
        // Circular buffer snapshot
        List<String> circSnap = new ArrayList<>();
        for (int i = 0; i < BUFFER_SIZE; i++) {
            circSnap.add(circularBuffer[i] == null ? "[empty]" : circularBuffer[i]);
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("bufferCapacity",    BUFFER_SIZE);
        state.put("totalProduced",     totalProduced);
        state.put("totalConsumed",     totalConsumed);
        state.put("bufferFullStalls",  bufferFullStalls);
        state.put("bufferEmptyStalls", bufferEmptyStalls);

        // Single buffer
        state.put("singleBuffer", Map.of(
            "contents",  singleBuffer,
            "size",      singleBuffer.size(),
            "draining",  singleBufferDraining
        ));

        // Double buffer
        state.put("doubleBuffer", Map.of(
            "buffer0",        doubleBuffers[0],
            "buffer1",        doubleBuffers[1],
            "activeBuffer",   "Buffer-" + activeBuffer,
            "inactiveBuffer", "Buffer-" + inactiveBuffer
        ));

        // Circular buffer
        state.put("circularBuffer", Map.of(
            "slots",       circSnap,
            "writePtr",    writePtr.get(),
            "readPtr",     readPtr.get(),
            "itemCount",   count.get()
        ));

        state.put("recentLog", log.size() > 25 ? log.subList(log.size()-25, log.size()) : log);
        return state;
    }
}
