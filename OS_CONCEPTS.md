# 🚄 Railway Booking System — OS Concepts

> **Tech Stack:** Spring Boot (Java 21) · React + TypeScript · MySQL · JWT Security

All OS concepts are implemented in `railway-os/src/main/java/com/vit/railway_os/oscore/`

---

## 👥 Team Distribution (1 Concept Each)

| Member | OS Concept | Files |
|--------|-----------|-------|
| Member 1 | PCB + 5-State Process Model | `ProcessControlBlock.java`, `ProcessState.java`, `BookingProcess.java` |
| Member 2 | CPU Scheduling (FCFS, SJF, RR, Priority) | `FCFSScheduler.java`, `SJFScheduler.java`, `RoundRobinScheduler.java`, `PriorityScheduler.java` |
| Member 3 | Mutex Lock & Critical Section | `CriticalSectionGuard.java` |
| Member 4 | Readers-Writers Problem + Semaphores | `AvailabilityMonitor.java` |
| Member 5 | Producer-Consumer Problem | `TicketBuffer.java`, `TicketProducer.java`, `TicketConsumer.java` |

---

## 📌 Concept 1 — Process Control Block (PCB) + 5-State Model
**Owner:** Member 1

Every HTTP booking request is treated as an OS **process** with its own PCB.

### PCB Fields (`ProcessControlBlock.java`)
| Field | OS Meaning |
|---|---|
| `processId` | PID assigned by OS |
| `state` | Current state (see 5-state model below) |
| `priority` | Scheduling priority |
| `burstTime` | Expected execution time |
| `arrivalTime`, `startTime`, `completionTime` | Timing attributes |
| `processType` | `STANDARD` (normal user) or `TATKAL` (high priority) |

**Computed Metrics:**
- `turnaroundTime = completionTime - arrivalTime`
- `waitingTime = turnaroundTime - burstTime`
- `responseTime = startTime - arrivalTime`

### 5-State Model (`ProcessState.java`)
```
NEW → READY → RUNNING → WAITING → TERMINATED
```
| State | Meaning in this project |
|---|---|
| `NEW` | Booking request received |
| `READY` | Added to scheduler queue |
| `RUNNING` | Actively booking seats in DB |
| `WAITING` | Blocked waiting for mutex |
| `TERMINATED` | Booking completed/failed |

### Console Output (shown when booking)
```
[PCB] Process 42 state: NEW → READY
[FCFS DISPATCH] Running process 42
[PCB] Process 42 state: READY → RUNNING
[PCB] Process 42 state: RUNNING → TERMINATED
```

---

## 📌 Concept 2 — CPU Scheduling Algorithms
**Owner:** Member 2

Admin can switch the active algorithm live from the dashboard. Each booking process is dispatched according to the selected algorithm.

| Algorithm | Type | Data Structure | Real-world Meaning |
|---|---|---|---|
| **FCFS** | Non-preemptive | `LinkedList` (FIFO Queue) | First booked = first processed |
| **SJF** | Non-preemptive | `PriorityQueue` (min burstTime) | Fewest seats needed = done first |
| **Round Robin** | Preemptive | Circular Queue (quantum = 2s) | Fair CPU time for all users |
| **Priority** | Preemptive | Max-Heap | Tatkal = HIGH, jumps the queue |

### How to Demo
1. Go to `http://localhost:5173/admin-login` (admin / 123456)
2. Admin Dashboard → **Scheduler Configuration**
3. Click FCFS / SJF / RR / PRIORITY — shows active one with pulsing dot
4. Queue sizes update live every 2 seconds

### Sample — FCFS (`FCFSScheduler.java`)
```java
private Queue<BookingProcess> readyQueue = new LinkedList<>();

public synchronized void addProcess(BookingProcess process) {
    process.getPCB().setState(ProcessState.READY);
    readyQueue.add(process);   // Add to end of FIFO queue
}

public synchronized void dispatch() {
    while (!readyQueue.isEmpty()) {
        BookingProcess process = readyQueue.poll(); // Take from front
        process.start();
        process.join();  // Wait for completion before next
    }
}
```

---

## 📌 Concept 3 — Mutex Lock & Critical Section
**Owner:** Member 3

Prevents two users from simultaneously booking the same seats (race condition / oversell).

### The Problem (without mutex)
```
User A reads: 5 seats available ─┐
User B reads: 5 seats available ─┤ Both pass the check!
User A books 5 seats            ─┤ → DB has 0 seats
User B books 5 seats            ─┘ → DB has -5 seats ❌ OVERSELL
```

### The Solution (`CriticalSectionGuard.java`)
```java
private final ReentrantLock mutex = new ReentrantLock();

public boolean bookSeat(int userId, int seatsNeeded) {
    mutex.lock();  // ← ENTER CRITICAL SECTION (all others wait)
    try {
        // 1. READ current seats from MySQL
        // 2. CHECK if enough seats available
        Thread.sleep(3000); // Proves the lock holds!
        // 3. DEDUCT seats
        // 4. SAVE to MySQL
        return true;
    } finally {
        mutex.unlock(); // ← EXIT CRITICAL SECTION
    }
}
```

### Console Output
```
[MUTEX LOCKED]   Process 42 is safely accessing the database.
SUCCESS: User 42 booked 2 seats. DB Remaining: 98
[MUTEX UNLOCKED] Process 42 released the database.
```

### How to Demo
Open **2 browser tabs** → both logged in → click "Book Now" on **Mumbai Rajdhani** simultaneously → one shows a 3-second "Processing..." while the other completes first.

---

## 📌 Concept 4 — Readers-Writers Problem + Semaphores
**Owner:** Member 4

Many users can check seat availability simultaneously (**readers**), but booking (writing to DB) needs exclusive access (**writer**).

### Two Semaphores (`AvailabilityMonitor.java`)
| Semaphore | Value | Purpose |
|---|---|---|
| `readMutex` | Binary (1) | Protects the `readersCount` variable |
| `writeLock` | Binary (1) | Grants exclusive write access |

### Reader Logic (multiple can run simultaneously)
```java
readMutex.acquire();          // P(readMutex)
readersCount++;
if (readersCount == 1)
    writeLock.acquire();      // 1st reader blocks all writers
readMutex.release();          // V(readMutex)

// ── CRITICAL SECTION (READING) ──
// Thousands of users read seat count at the same time ✅

readMutex.acquire();
readersCount--;
if (readersCount == 0)
    writeLock.release();      // Last reader unblocks writers
readMutex.release();
```

### Writer Logic (exclusive access)
```java
writeLock.acquire();   // Blocks ALL readers + writers
// ── CRITICAL SECTION (WRITING) ──
// Seat data being modified — no one else can read
writeLock.release();
```

**`acquire()` = P / wait · `release()` = V / signal**

### How to Demo
Admin Dashboard → **Synchronization Primitives** panel → shows **Active Readers** counter updating live every 2 seconds.

---

## 📌 Concept 5 — Producer-Consumer Problem
**Owner:** Member 5

Ticket generation (Producer) and ticket booking (Consumer) run as **separate threads** sharing a **bounded buffer**.

### Components
| Component | Role |
|---|---|
| `TicketBuffer` | Bounded buffer (fixed-size shared queue) |
| `TicketProducer` (Thread) | Generates 1 ticket every 500ms → `buffer.produce()` |
| `TicketConsumer` (Thread) | Pulls ticket from buffer → processes booking |

### Synchronization (`wait()` / `notify()`)
```java
// Producer — waits if buffer is FULL
public synchronized void produce(Ticket ticket) throws InterruptedException {
    while (buffer.size() == CAPACITY)
        wait();           // ← buffer full, Producer sleeps
    buffer.add(ticket);
    notifyAll();          // ← wake up Consumer
}

// Consumer — waits if buffer is EMPTY
public synchronized Ticket consume() throws InterruptedException {
    while (buffer.isEmpty())
        wait();           // ← buffer empty, Consumer sleeps
    Ticket t = buffer.poll();
    notifyAll();          // ← wake up Producer
    return t;
}
```

### Real-world Mapping
> IRCTC generates available ticket slots (Producer) at regular intervals → Booking engine assigns them to users (Consumer)

---

## 🎬 Live Demo Sequence (15 min presentation)

| Step | Who | What to Show |
|------|-----|-------------|
| 1 | Member 5 | Register at `/register`, login at `/login`, show UI |
| 2 | Member 1 | Book a ticket, show Spring Boot console for PCB state transitions |
| 3 | Member 2 | Admin Dashboard → switch schedulers, explain each algorithm |
| 4 | Member 3 | 2 browser tabs → simultaneous booking → mutex delay visible |
| 5 | Member 4 | Admin Dashboard → Active Readers counter, explain semaphores |
| 6 | Member 5 | Explain Producer-Consumer pipeline, show My Bookings with PNR |

### Running the Project
```bash
# Terminal 1 — Backend
cd railway-os
./mvnw.cmd spring-boot:run

# Terminal 2 — Frontend
cd frontend
npm run dev

# URLs
User App:    http://localhost:5173/login
Admin Panel: http://localhost:5173/admin-login  (admin / 123456)
Backend API: http://localhost:8080
```

---

## 📁 `oscore/` Package — Complete File Reference

```
railway-os/src/main/java/com/vit/railway_os/oscore/
│
├── ProcessControlBlock.java   # PCB — PID, state, timing, metrics
├── ProcessState.java          # Enum: NEW/READY/RUNNING/WAITING/TERMINATED
├── BookingProcess.java        # extends Thread — each booking = a process
│
├── FCFSScheduler.java         # LinkedList FIFO queue
├── SJFScheduler.java          # PriorityQueue (min burst time)
├── RoundRobinScheduler.java   # Circular queue, time quantum = 2s
├── PriorityScheduler.java     # Max-Heap (Tatkal = HIGH priority)
│
├── CriticalSectionGuard.java  # ReentrantLock mutex — seat booking
│
├── AvailabilityMonitor.java   # Readers-Writers with 2 Semaphores
│
├── TicketBuffer.java          # Bounded buffer (wait/notify)
├── TicketProducer.java        # Producer thread (500ms interval)
├── TicketConsumer.java        # Consumer thread
│
├── TrainPreparationStation.java  # Dining Philosophers table
├── TrainCrew.java                # Philosopher thread
│
│── ─── UNIT IV: DEADLOCKS ────────────────────────────────────────────────────
├── BankersAlgorithm.java         # Banker's Algorithm (Deadlock Avoidance)
├── ResourceAllocationGraph.java  # RAG + DFS cycle detection (Deadlock Detection)
├── DeadlockRecovery.java         # Process Termination + Resource Preemption
│
│── ─── UNIT V: MEMORY MANAGEMENT ─────────────────────────────────────────────
├── MemoryPartitionManager.java   # Fixed/Dynamic + First/Best/Next/Worst Fit
├── BuddySystem.java              # Buddy System (split + merge)
├── PageReplacementSimulator.java # FIFO, LRU, Optimal, Clock
├── TLBSimulator.java             # TLB + Address Translation + EMAT
│
│── ─── UNIT VI: I/O & FILE MANAGEMENT ────────────────────────────────────────
├── DiskScheduler.java            # FCFS, SSTF, SCAN, C-SCAN
├── IOBufferManager.java          # Single / Double / Circular Buffering
└── FileManagementSimulator.java  # Sequential/Indexed/Linked + Directories
```

---

## 📌 Unit IV — Deadlocks

### Concept 6 — Banker's Algorithm (`BankersAlgorithm.java`)
Resources = seat types: `AC_SEATS`, `SLEEPER_SEATS`, `GENERAL_SEATS`

```
POST /api/admin/bankers/safety           → run safety algorithm
POST /api/admin/bankers/request          { "pid":1, "request":[1,0,2] }
```

### Concept 7 — Resource Allocation Graph + Detection (`ResourceAllocationGraph.java`)
```
POST /api/admin/deadlock/simulate        { "scenario":"DEADLOCK" }
POST /api/admin/deadlock/detect          → { deadlocked: true, cycle: [1,2,3,1] }
```

### Concept 8 — Deadlock Recovery (`DeadlockRecovery.java`)
```
POST /api/admin/deadlock/recover         { "strategy":"TERMINATION" | "PREEMPTION" }
POST /api/admin/deadlock/recovery/reset
```

---

## 📌 Unit V — Memory Management

### Concept 9 — Memory Partitioning (`MemoryPartitionManager.java`)
```
POST /api/admin/memory/allocate-fixed    { "pid":10, "sizeKB":64 }
POST /api/admin/memory/allocate-dynamic  { "pid":11, "sizeKB":200, "strategy":"BEST_FIT" }
```

### Concept 10 — Buddy System (`BuddySystem.java`)
```
POST /api/admin/buddy/allocate     { "pid":5, "sizeKB":70 }
POST /api/admin/buddy/deallocate   { "address":128 }
```

### Concept 11 — Page Replacement (`PageReplacementSimulator.java`)
```
POST /api/admin/page-replacement   { "frames":4, "references":[1,2,3,4,1,2,5] }
```
Returns FIFO, LRU, Optimal, Clock results side-by-side.

### Concept 12 — TLB + Address Translation (`TLBSimulator.java`)
```
POST /api/admin/tlb/demo
POST /api/admin/tlb/translate      { "logicalAddress": 544 }
POST /api/admin/tlb/flush
```

---

## 📌 Unit VI — I/O & File Management

### Concept 13 — Disk Scheduling (`DiskScheduler.java`)
```
POST /api/admin/disk-scheduling    { "head":50, "requests":[82,170,43,140,24,16,190] }
```
Returns FCFS, SSTF, SCAN, C-SCAN comparison.

### Concept 14 — I/O Buffering (`IOBufferManager.java`)
```
POST /api/admin/io-buffer/demo     { "type":"DOUBLE" | "SINGLE" | "CIRCULAR" }
```

### Concept 15 — File Management (`FileManagementSimulator.java`)
```
POST /api/admin/file-system/read   { "fileName":"12951_2024-06-15.bkr", "recordIndex":3 }
POST /api/admin/file-system/write  { "fileName":"...", "record":"BK999:User42→Seat-A3" }
POST /api/admin/file-system/share  { "fileName":"...", "user":"User42", "mode":"READ" }
```

