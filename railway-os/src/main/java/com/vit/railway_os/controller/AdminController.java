package com.vit.railway_os.controller;

import com.vit.railway_os.oscore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    // ── Unit I-III (existing) ──────────────────────────────────────────────────
    @Autowired private FCFSScheduler fcfsScheduler;
    @Autowired private SJFScheduler sjfScheduler;
    @Autowired private RoundRobinScheduler roundRobinScheduler;
    @Autowired private PriorityScheduler priorityScheduler;
    @Autowired private AvailabilityMonitor availabilityMonitor;
    @Autowired private OsStateTracker tracker;
    private TrainPreparationStation diningSim;

    // ── Unit IV — Deadlocks ────────────────────────────────────────────────────
    @Autowired private BankersAlgorithm bankersAlgorithm;
    @Autowired private ResourceAllocationGraph resourceAllocationGraph;
    @Autowired private DeadlockRecovery deadlockRecovery;

    // ── Unit V — Memory Management ─────────────────────────────────────────────
    @Autowired private MemoryPartitionManager memoryPartitionManager;
    @Autowired private BuddySystem buddySystem;
    @Autowired private PageReplacementSimulator pageReplacementSimulator;
    @Autowired private TLBSimulator tlbSimulator;

    // ── Unit VI — I/O & File Management ───────────────────────────────────────
    @Autowired private DiskScheduler diskScheduler;
    @Autowired private IOBufferManager ioBufferManager;
    @Autowired private FileManagementSimulator fileManagementSimulator;

    // ── Real-time OS Event Log ─────────────────────────────────────────────────
    @Autowired private OsEventLog osEventLog;

    private static String currentScheduler = "FCFS";

    // ════════════════════════ UNIT I-III ENDPOINTS ═══════════════════════════

    @GetMapping("/monitor")
    public Map<String, Object> getOsMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("fcfsQueueSize", fcfsScheduler.getQueueSize());
        metrics.put("sjfQueueSize", sjfScheduler.getQueueSize());
        metrics.put("roundRobinQueueSize", roundRobinScheduler.getQueueSize());
        metrics.put("priorityQueueSize", priorityScheduler.getQueueSize());
        metrics.put("activeReaders", availabilityMonitor.getActiveReaders());
        metrics.put("recentProcesses", tracker.getRecentProcesses());
        metrics.put("mutexState", tracker.getMutexState());
        metrics.put("ticketsInBuffer", tracker.getTicketsInBuffer());
        metrics.put("philosophers", tracker.getPhilosopherStates());
        return metrics;
    }

    /** Real-time OS event feed — fired by ACTUAL bookings */
    @GetMapping("/live-events")
    public List<OsEventLog.OsEvent> getLiveEvents(@RequestParam(defaultValue = "50") int limit) {
        return osEventLog.getRecent(limit);
    }

    @PostMapping("/live-events/clear")
    public Map<String, String> clearEvents() {
        osEventLog.clear();
        return Map.of("status", "cleared");
    }

    @GetMapping("/scheduler")
    public Map<String, String> getCurrentScheduler() {
        return Map.of("scheduler", currentScheduler);
    }

    @PostMapping("/scheduler")
    public Map<String, String> setScheduler(@RequestBody Map<String, String> payload) {
        String s = payload.get("scheduler");
        if (s != null && (s.equals("FCFS") || s.equals("SJF") || s.equals("RR") || s.equals("PRIORITY"))) {
            currentScheduler = s;
            System.out.println("[ADMIN] Scheduler changed to: " + currentScheduler);
            return Map.of("status", "success", "scheduler", currentScheduler);
        }
        return Map.of("status", "error", "message", "Invalid scheduler type");
    }

    public static String getSystemScheduler() { return currentScheduler; }

    @PostMapping("/start-dining-philosophers")
    public Map<String, String> startDiningPhilosophers() {
        if (diningSim != null) diningSim.stopSimulation();
        for (int i = 0; i < 5; i++) tracker.updatePhilosopherState(i, "THINKING (Resting)");
        diningSim = new TrainPreparationStation(tracker);
        diningSim.startSimulation();
        return Map.of("status", "success", "message", "Dining Philosophers simulation started.");
    }

    // ════════════════════════ UNIT IV: DEADLOCKS ═════════════════════════════

    /** GET /api/admin/bankers — current Banker state (available, allocation, need) */
    @GetMapping("/bankers")
    public Map<String, Object> getBankersState() {
        return bankersAlgorithm.getState();
    }

    /** POST /api/admin/bankers/safety — run safety algorithm */
    @PostMapping("/bankers/safety")
    public Map<String, Object> checkSafety() {
        BankersAlgorithm.SafetyResult result = bankersAlgorithm.isSafeState();
        return Map.of(
            "safe",         result.isSafe(),
            "safeSequence", result.safeSequence(),
            "steps",        result.steps()
        );
    }

    /**
     * POST /api/admin/bankers/request
     * Body: { "pid": 1, "request": [1, 0, 2] }
     * Runs the resource-request algorithm and grants/denies.
     */
    @PostMapping("/bankers/request")
    public Map<String, Object> bankersRequest(@RequestBody Map<String, Object> payload) {
        int pid = ((Number) payload.get("pid")).intValue();
        @SuppressWarnings("unchecked")
        List<Integer> reqList = (List<Integer>) payload.get("request");
        int[] request = reqList.stream().mapToInt(Integer::intValue).toArray();
        BankersAlgorithm.RequestResult result = bankersAlgorithm.requestResources(pid, request);
        return Map.of("granted", result.granted(), "status", result.status(), "steps", result.steps());
    }

    /** GET /api/admin/deadlock — RAG state */
    @GetMapping("/deadlock")
    public Map<String, Object> getRAGState() {
        return resourceAllocationGraph.getState();
    }

    /** POST /api/admin/deadlock/detect — run cycle detection */
    @PostMapping("/deadlock/detect")
    public Map<String, Object> detectDeadlock() {
        ResourceAllocationGraph.DeadlockResult result = resourceAllocationGraph.detectDeadlock();
        return Map.of(
            "deadlocked",   result.deadlocked(),
            "cycle",        result.cycle(),
            "waitForGraph", result.waitForGraph(),
            "log",          result.log()
        );
    }

    /**
     * POST /api/admin/deadlock/simulate
     * Body: { "scenario": "DEADLOCK" | "SAFE" }
     */
    @PostMapping("/deadlock/simulate")
    public Map<String, Object> simulateDeadlock(@RequestBody Map<String, String> payload) {
        String scenario = payload.getOrDefault("scenario", "DEADLOCK");
        if ("SAFE".equalsIgnoreCase(scenario)) {
            resourceAllocationGraph.simulateSafe();
        } else {
            resourceAllocationGraph.simulateDeadlock();
        }
        return resourceAllocationGraph.getState();
    }

    /** GET /api/admin/deadlock/recovery — current recovery state */
    @GetMapping("/deadlock/recovery")
    public Map<String, Object> getRecoveryState() {
        return deadlockRecovery.getState();
    }

    /**
     * POST /api/admin/deadlock/recover
     * Body: { "strategy": "TERMINATION" | "PREEMPTION" }
     */
    @PostMapping("/deadlock/recover")
    public Map<String, Object> recoverFromDeadlock(@RequestBody Map<String, String> payload) {
        String strategy = payload.getOrDefault("strategy", "TERMINATION");
        DeadlockRecovery.RecoveryResult result =
            "PREEMPTION".equalsIgnoreCase(strategy)
                ? deadlockRecovery.recoverByPreemption()
                : deadlockRecovery.recoverByTermination();
        return Map.of("resolved", result.resolved(), "strategy", result.strategy(),
                      "affected", result.affected(), "steps", result.steps());
    }

    /** POST /api/admin/deadlock/recovery/reset — reset demo deadlock set */
    @PostMapping("/deadlock/recovery/reset")
    public Map<String, Object> resetRecoveryDemo() {
        deadlockRecovery.resetDemo();
        return deadlockRecovery.getState();
    }

    // ════════════════════════ UNIT V: MEMORY MANAGEMENT ══════════════════════

    /** GET /api/admin/memory — memory partition state */
    @GetMapping("/memory")
    public Map<String, Object> getMemoryState() {
        return memoryPartitionManager.getState();
    }

    /**
     * POST /api/admin/memory/allocate-fixed
     * Body: { "pid": 10, "sizeKB": 64, "processName": "User10-Booking" }
     */
    @PostMapping("/memory/allocate-fixed")
    public Map<String, Object> allocateFixed(@RequestBody Map<String, Object> payload) {
        int pid        = ((Number) payload.get("pid")).intValue();
        int sizeKB     = ((Number) payload.get("sizeKB")).intValue();
        String name    = (String) payload.getOrDefault("processName", "Process-" + pid);
        MemoryPartitionManager.AllocationResult result =
            memoryPartitionManager.allocateFixed(pid, sizeKB, name);
        return Map.of("success", result.success(), "blockIndex", result.blockIndex(),
                      "status", result.status(), "log", result.log());
    }

    /**
     * POST /api/admin/memory/allocate-dynamic
     * Body: { "pid": 11, "sizeKB": 200, "processName": "User11", "strategy": "BEST_FIT" }
     */
    @PostMapping("/memory/allocate-dynamic")
    public Map<String, Object> allocateDynamic(@RequestBody Map<String, Object> payload) {
        int pid        = ((Number) payload.get("pid")).intValue();
        int sizeKB     = ((Number) payload.get("sizeKB")).intValue();
        String name    = (String) payload.getOrDefault("processName", "Process-" + pid);
        String strategy = (String) payload.getOrDefault("strategy", "FIRST_FIT");
        MemoryPartitionManager.AllocationResult result =
            memoryPartitionManager.allocateDynamic(pid, sizeKB, name, strategy);
        return Map.of("success", result.success(), "status", result.status(), "log", result.log());
    }

    /** POST /api/admin/memory/reset — reset both fixed and dynamic memory */
    @PostMapping("/memory/reset")
    public Map<String, Object> resetMemory() {
        memoryPartitionManager.resetFixed();
        memoryPartitionManager.resetDynamic();
        return memoryPartitionManager.getState();
    }

    /** GET /api/admin/buddy — buddy system state */
    @GetMapping("/buddy")
    public Map<String, Object> getBuddyState() {
        return buddySystem.getState();
    }

    /**
     * POST /api/admin/buddy/allocate
     * Body: { "pid": 5, "sizeKB": 70, "processName": "User5-Session" }
     */
    @PostMapping("/buddy/allocate")
    public Map<String, Object> buddyAllocate(@RequestBody Map<String, Object> payload) {
        int pid     = ((Number) payload.get("pid")).intValue();
        int sizeKB  = ((Number) payload.get("sizeKB")).intValue();
        String name = (String) payload.getOrDefault("processName", "P" + pid);
        int addr = buddySystem.allocate(pid, sizeKB, name);
        return Map.of("address", addr, "success", addr != -1, "state", buddySystem.getState());
    }

    /**
     * POST /api/admin/buddy/deallocate
     * Body: { "address": 128 }
     */
    @PostMapping("/buddy/deallocate")
    public Map<String, Object> buddyDeallocate(@RequestBody Map<String, Object> payload) {
        int address = ((Number) payload.get("address")).intValue();
        buddySystem.deallocate(address);
        return buddySystem.getState();
    }

    /**
     * POST /api/admin/page-replacement
     * Body: { "frames": 4, "references": [1,2,3,4,1,2,5,1,2,3,4,5] }
     * Returns comparison of all four algorithms.
     */
    @PostMapping("/page-replacement")
    public Map<String, Object> pageReplacement(@RequestBody Map<String, Object> payload) {
        int frames = ((Number) payload.getOrDefault("frames", 4)).intValue();
        @SuppressWarnings("unchecked")
        List<Integer> refList = (List<Integer>) payload.get("references");
        int[] refs = refList == null ? null : refList.stream().mapToInt(Integer::intValue).toArray();
        return pageReplacementSimulator.runAll(refs, frames);
    }

    /** GET /api/admin/tlb — current TLB statistics */
    @GetMapping("/tlb")
    public Map<String, Object> getTLBState() {
        return tlbSimulator.getState();
    }

    /** POST /api/admin/tlb/demo — run built-in demo access pattern */
    @PostMapping("/tlb/demo")
    public Map<String, Object> tlbDemo() {
        return tlbSimulator.runDemo();
    }

    /**
     * POST /api/admin/tlb/translate
     * Body: { "logicalAddress": 0x0220 }
     */
    @PostMapping("/tlb/translate")
    public Map<String, Object> tlbTranslate(@RequestBody Map<String, Object> payload) {
        int addr = ((Number) payload.get("logicalAddress")).intValue();
        TLBSimulator.TranslationResult result = tlbSimulator.translate(addr);
        return Map.of(
            "logicalAddress",  "0x" + Integer.toHexString(addr),
            "physicalAddress", result.physicalAddress() == -1 ? "PAGE_FAULT" : "0x" + Integer.toHexString(result.physicalAddress()),
            "pageNumber",      result.pageNumber(),
            "offset",          result.offset(),
            "tlbHit",          result.tlbHit(),
            "pageFault",       result.pageFault(),
            "accessTimeNs",    result.accessTimeNs(),
            "detail",          result.detail(),
            "tlbState",        tlbSimulator.getState()
        );
    }

    /** POST /api/admin/tlb/flush — flush TLB (simulate context switch) */
    @PostMapping("/tlb/flush")
    public Map<String, Object> flushTLB() {
        tlbSimulator.flushTLB();
        return tlbSimulator.getState();
    }

    // ════════════════════════ UNIT VI: I/O & FILE MANAGEMENT ═════════════════

    /**
     * POST /api/admin/disk-scheduling
     * Body: { "head": 50, "requests": [82, 170, 43, 140, 24, 16, 190] }
     * Returns all four algorithm results + comparison table.
     */
    @PostMapping("/disk-scheduling")
    public Map<String, Object> diskScheduling(@RequestBody Map<String, Object> payload) {
        int head = ((Number) payload.getOrDefault("head", 50)).intValue();
        @SuppressWarnings("unchecked")
        List<Integer> reqList = (List<Integer>) payload.get("requests");
        int[] requests = reqList == null ? null : reqList.stream().mapToInt(Integer::intValue).toArray();
        return diskScheduler.runAll(head, requests);
    }

    /** GET /api/admin/io-buffer — I/O buffer state */
    @GetMapping("/io-buffer")
    public Map<String, Object> getIOBufferState() {
        return ioBufferManager.getState();
    }

    /**
     * POST /api/admin/io-buffer/demo
     * Body: { "type": "SINGLE" | "DOUBLE" | "CIRCULAR" }
     */
    @PostMapping("/io-buffer/demo")
    public Map<String, Object> ioBufferDemo(@RequestBody Map<String, String> payload) {
        String type = payload.getOrDefault("type", "DOUBLE");
        return ioBufferManager.runDemo(type);
    }

    /** GET /api/admin/file-system — file directory listing */
    @GetMapping("/file-system")
    public Map<String, Object> getFileSystem() {
        return fileManagementSimulator.getState();
    }

    /**
     * POST /api/admin/file-system/create
     * Body: { "name": "12951_2024-07-01.bkr", "organization": "INDEXED", "owner": "admin" }
     */
    @PostMapping("/file-system/create")
    public Map<String, Object> createFile(@RequestBody Map<String, String> payload) {
        boolean ok = fileManagementSimulator.createFile(
            payload.get("name"),
            payload.getOrDefault("organization", "SEQUENTIAL"),
            payload.getOrDefault("owner", "admin")
        );
        return Map.of("success", ok, "state", fileManagementSimulator.getState());
    }

    /**
     * POST /api/admin/file-system/read
     * Body: { "fileName": "12951_2024-06-15.bkr", "recordIndex": 3 }
     */
    @PostMapping("/file-system/read")
    public Map<String, Object> readRecord(@RequestBody Map<String, Object> payload) {
        String file = (String) payload.get("fileName");
        int idx     = ((Number) payload.getOrDefault("recordIndex", 0)).intValue();
        FileManagementSimulator.AccessResult result = fileManagementSimulator.readRecord(file, idx);
        return Map.of("success", result.success(), "organization", result.organization(),
                      "diskOps", result.diskOps(), "steps", result.steps(),
                      "state", fileManagementSimulator.getState());
    }

    /**
     * POST /api/admin/file-system/write
     * Body: { "fileName": "12951_2024-06-15.bkr", "record": "BK999:User42→Seat-A3" }
     */
    @PostMapping("/file-system/write")
    public Map<String, Object> writeRecord(@RequestBody Map<String, Object> payload) {
        String file   = (String) payload.get("fileName");
        String record = (String) payload.getOrDefault("record", "BK:default");
        FileManagementSimulator.AccessResult result = fileManagementSimulator.writeRecord(file, record);
        return Map.of("success", result.success(), "diskOps", result.diskOps(),
                      "steps", result.steps(), "state", fileManagementSimulator.getState());
    }

    /**
     * POST /api/admin/file-system/share
     * Body: { "fileName": "...", "user": "User42", "mode": "READ" | "WRITE" }
     */
    @PostMapping("/file-system/share")
    public Map<String, Object> shareFile(@RequestBody Map<String, String> payload) {
        String file = payload.get("fileName");
        String user = payload.getOrDefault("user", "anonymous");
        String mode = payload.getOrDefault("mode", "READ");
        boolean ok  = "WRITE".equalsIgnoreCase(mode)
            ? fileManagementSimulator.openWrite(file, user)
            : fileManagementSimulator.openRead(file, user);
        return Map.of("granted", ok, "mode", mode, "state", fileManagementSimulator.getState());
    }
}