# 🚄 Railway Booking System — OS Concepts Detailed Explanation

> **Tech Stack:** Spring Boot (Java 21) · React + TypeScript · MySQL · JWT  
> **All OS code lives in:** `railway-os/src/main/java/com/vit/railway_os/oscore/`

---

## 📌 Concept 1 — Process Control Block (PCB) + 5-State Process Model

### What is a PCB?
In a real OS, when a program starts, the OS creates a **Process Control Block (PCB)** — a data structure that stores all information about that process. In our project, **every booking request = one process** with its own PCB.

### Files
- `ProcessControlBlock.java` — the PCB data structure
- `ProcessState.java` — the 5 states as a Java enum
- `BookingProcess.java` — extends `Thread`, each booking is a process

### How it works — Step by Step

```
User clicks "Book Now"
        ↓
BookingProcess is created (new PCB assigned)
        ↓
     [NEW]  ← PCB created, PID assigned, burst time calculated
        ↓
    [READY] ← Added to scheduler queue (waiting for CPU)
        ↓
   [RUNNING] ← Scheduler dispatches it, thread.start() called
        ↓
   [WAITING] ← Thread tries to lock the mutex (database busy)
        ↓
   [RUNNING] ← Mutex acquired, executing the DB operation
        ↓
[TERMINATED] ← Booking saved, completionTime recorded
```

### PCB Fields Explained
| Field | Meaning |
|-------|---------|
| `processId` | Unique PID (= userId in our demo) |
| `state` | Current state in the 5-state model |
| `priority` | 1 = TATKAL (high), 5 = STANDARD (normal) |
| `burstTime` | `seatsNeeded × 1000ms` = estimated CPU time |
| `arrivalTime` | When booking request came in |
| `startTime` | When scheduler started running it |
| `completionTime` | When booking finished |

### Computed Metrics
```
Turnaround Time = completionTime − arrivalTime
Waiting Time    = Turnaround Time − burstTime
Response Time   = startTime − arrivalTime
```

### Key Code (`ProcessControlBlock.java`)
```java
public void setState(ProcessState state) {
    System.out.println("[PCB] Process " + processId + " state: " + this.state + " → " + state);
    tracker.recordProcessStateChange(processId, this.state, state, burstTime, processType);
    this.state = state;
}
```

### What you see in console
```
[PROCESS CREATED] PCB[PID=42, State=NEW, Priority=5, BurstTime=1000, Type=STANDARD]
[PCB] Process 42 state: NEW → READY
[PCB] Process 42 state: READY → RUNNING
[PCB] Process 42 state: RUNNING → WAITING
[PCB] Process 42 state: WAITING → RUNNING
[PCB] Process 42 state: RUNNING → TERMINATED
[METRICS] Turnaround: 2145ms, Waiting: 1145ms, Response: 3ms
```

---

## 📌 Concept 2 — CPU Scheduling Algorithms (FCFS, SJF, RR, Priority)

### What is CPU Scheduling?
When multiple users book at the same time, the OS must decide the **order** in which to process them. This is CPU scheduling.

### Files
- `FCFSScheduler.java` — First Come First Serve
- `SJFScheduler.java` — Shortest Job First
- `RoundRobinScheduler.java` — Round Robin
- `PriorityScheduler.java` — Priority Scheduling

### Algorithm 1 — FCFS (First Come First Serve)
- **Type:** Non-preemptive
- **Data Structure:** `LinkedList` (FIFO Queue)
- **Rule:** First booking request received = first processed
- **Real-world:** Normal queue at a station counter

```java
private Queue<BookingProcess> readyQueue = new LinkedList<>();

public synchronized void addProcess(BookingProcess p) {
    p.getPCB().setState(ProcessState.READY);
    readyQueue.add(p);  // Goes to END of queue
}

public synchronized void dispatch() {
    while (!readyQueue.isEmpty()) {
        BookingProcess p = readyQueue.poll();  // Takes from FRONT
        p.start();
        p.join();  // Wait for it to finish before next
    }
}
```

### Algorithm 2 — SJF (Shortest Job First)
- **Type:** Non-preemptive
- **Data Structure:** `PriorityQueue` sorted by `burstTime` (ascending)
- **Rule:** User booking fewest seats = processed first (less burst time)
- **Real-world:** Express checkout lane (fewer items = faster)

```java
private PriorityQueue<BookingProcess> readyQueue =
    new PriorityQueue<>(Comparator.comparingInt(BookingProcess::getBurstTime));
// burstTime = seatsNeeded × 1000, so 1 seat → processed before 5 seats
```

### Algorithm 3 — Round Robin (Preemptive)
- **Type:** Preemptive
- **Data Structure:** Circular `LinkedList` queue
- **Time Quantum:** 2 seconds
- **Rule:** Every process gets 2 seconds of CPU time, then goes back to queue
- **Real-world:** Fair token system at a government office

```java
private static final int TIME_QUANTUM = 2000; // 2 seconds

// Each process runs for max 2s, then yields
process.start();
Thread.sleep(TIME_QUANTUM); // Wait one quantum
if (process.isAlive()) {
    process.interrupt(); // Preempt if still running
    readyQueue.add(process); // Back to end of queue
}
```

### Algorithm 4 — Priority Scheduling (Preemptive)
- **Type:** Preemptive  
- **Data Structure:** `PriorityQueue` sorted by priority (ascending = higher priority first)
- **Rule:** TATKAL booking (priority=1) jumps ahead of STANDARD (priority=5)
- **Real-world:** Emergency lane / VIP queue

```java
private PriorityQueue<BookingProcess> readyQueue =
    new PriorityQueue<>(); // Uses compareTo() from BookingProcess

// In BookingProcess.compareTo():
return Integer.compare(this.pcb.getPriority(), other.pcb.getPriority());
// priority 1 (TATKAL) < priority 5 (STANDARD) → TATKAL goes first
```

### How Admin Switches Algorithms
```
Admin Dashboard → Click FCFS/SJF/RR/PRIORITY
    → POST /api/admin/scheduler { "scheduler": "SJF" }
    → AdminController.currentScheduler = "SJF"
    → Next BookingController.bookTicket() uses SJF
```

---

## 📌 Concept 3 — Mutex Lock & Critical Section

### The Problem (Race Condition)
Without synchronization, two users can simultaneously:
```
User A reads: 1 seat available ─┐
User B reads: 1 seat available  ├── Both pass the check!
User A books 1 seat             ├── DB has 0 seats
User B books 1 seat             ┘── DB has -1 seats ❌ OVERSELL
```

### The Solution — ReentrantLock (Mutex)
**File:** `CriticalSectionGuard.java`

```java
private final ReentrantLock mutex = new ReentrantLock();

public BookingResult bookSeat(int userId, int seatsNeeded, ...) {
    mutex.lock();  // ← ALL other threads BLOCK here
    try {
        // ═══════════ CRITICAL SECTION ═══════════
        Train train = trainRepository.findByTrainNumber(...);
        // READ → CHECK → WAIT 2s → DEDUCT → SAVE → CREATE BOOKING
        // Only ONE thread can be here at a time
        // ════════════════════════════════════════
    } finally {
        mutex.unlock();  // ← Next thread unblocks
    }
}
```

### Flow with 2 simultaneous users
```
User A: mutex.lock() → SUCCESS → enters critical section
User B: mutex.lock() → BLOCKED (waiting)

[User A working for 2 seconds]
User A: mutex.unlock()

User B: mutex.lock() → SUCCESS → enters critical section
[User B now works safely]
```

### Console output
```
[MUTEX LOCKED]   Process 101 is safely accessing the database.
SUCCESS: User 101 booked 1 seats. DB Remaining: 99
[MUTEX UNLOCKED] Process 101 released the database.

[MUTEX LOCKED]   Process 102 is safely accessing the database.
SUCCESS: User 102 booked 1 seats. DB Remaining: 98
[MUTEX UNLOCKED] Process 102 released the database.
```

---

## 📌 Concept 4 — Readers-Writers Problem + Semaphores

### The Problem
- **Readers** = users checking seat availability (many can do this simultaneously — it's safe)
- **Writers** = users actually booking (modifying DB — needs exclusive access)

### Two Semaphores Used
**File:** `AvailabilityMonitor.java`

| Semaphore | Initial Value | Purpose |
|-----------|--------------|---------|
| `readMutex` | 1 (binary) | Protects the `readersCount` variable |
| `writeLock` | 1 (binary) | Grants exclusive write access |

### Reader Logic (P = acquire/wait, V = release/signal)
```java
// ENTRY PROTOCOL
readMutex.acquire();      // P(readMutex) — lock the counter
readersCount++;
if (readersCount == 1)
    writeLock.acquire();  // 1st reader blocks all writers
readMutex.release();      // V(readMutex) — allow more readers in

// ── READ DATA (multiple readers here simultaneously) ──
Thread.sleep(200);        // Simulate reading

// EXIT PROTOCOL
readMutex.acquire();
readersCount--;
if (readersCount == 0)
    writeLock.release();  // Last reader unblocks writers
readMutex.release();
```

### Writer Logic
```java
writeLock.acquire();  // Blocks ALL readers + writers (exclusive)
// ── MODIFY SEAT DATA ──
Thread.sleep(10000);  // Simulate heavy DB write
writeLock.release();  // Readers can come back in
```

### Scenario Example
```
Reader 1 arrives → readersCount=1 → acquires writeLock → reading ✅
Reader 2 arrives → readersCount=2 → reading simultaneously ✅
Writer arrives   → writeLock.acquire() → BLOCKED (readers active)
Reader 1 leaves  → readersCount=1 → writeLock still held
Reader 2 leaves  → readersCount=0 → writeLock.release()
Writer unblocks  → writeLock acquired → exclusive write ✅
```

---

## 📌 Concept 5 — Producer-Consumer Problem

### The Real-World Mapping
> IRCTC generates available ticket slots (Producer) at regular intervals.  
> The booking engine assigns them to users (Consumer).

### Files
- `TicketBuffer.java` — bounded buffer (max 10 tickets)
- `TicketProducer.java` — generates 1 ticket every 500ms
- `TicketConsumer.java` — pulls and "books" a ticket from buffer

### Three Semaphores
```java
Semaphore empty = new Semaphore(BUFFER_SIZE);  // 10 — empty slots available
Semaphore full  = new Semaphore(0);            // 0  — filled slots (starts empty)
Semaphore mutex = new Semaphore(1);            // 1  — mutual exclusion on buffer
```

### Producer Logic
```java
public void produce(Ticket ticket) throws InterruptedException {
    empty.acquire(); // P(empty): wait if buffer is FULL (0 slots left)
    mutex.acquire(); // P(mutex): enter critical section

    buffer.add(ticket);
    System.out.println("[PRODUCER] Generated: " + ticket + " | Buffer: " + buffer.size());

    mutex.release(); // V(mutex): exit critical section
    full.release();  // V(full):  signal consumer that item is ready
}
```

### Consumer Logic
```java
public Ticket consume() throws InterruptedException {
    full.acquire();  // P(full):  wait if buffer is EMPTY (0 items)
    mutex.acquire(); // P(mutex): enter critical section

    Ticket t = buffer.poll();
    System.out.println("[CONSUMER] Booked: " + t + " | Buffer: " + buffer.size());

    mutex.release(); // V(mutex): exit critical section
    empty.release(); // V(empty): signal producer that slot is free
    return t;
}
```

### Buffer States
```
Buffer = [T1, T2, T3] (size=3, capacity=10)
  → Producer: empty=7, full=3  — can still produce 7 more
  → Consumer: full=3           — can consume 3 immediately

Buffer = [T1..T10] (FULL)
  → Producer: empty.acquire() → BLOCKS (waits for consumer)
  → Consumer: processes one → empty.release() → Producer unblocks

Buffer = [] (EMPTY)
  → Consumer: full.acquire() → BLOCKS (waits for producer)
  → Producer: generates one → full.release() → Consumer unblocks
```

### Console output (run via Admin Dashboard or curl)
```
[PRODUCER] Generated ticket: Ticket[ID=1, Train=EXP-12951, Seat=1] | Buffer size: 1
[CONSUMER-1] Booked ticket: Ticket[ID=1, Train=EXP-12951, Seat=1] | Buffer size: 0
[PRODUCER] Generated ticket: Ticket[ID=2, Train=EXP-12951, Seat=2] | Buffer size: 1
[PRODUCER] Generated ticket: Ticket[ID=3, Train=EXP-12951, Seat=3] | Buffer size: 2
[CONSUMER-2] Booked ticket: Ticket[ID=2, Train=EXP-12951, Seat=2] | Buffer size: 1
```

---

## 📌 Bonus — Dining Philosophers (Deadlock Avoidance)

### The Problem
5 philosophers (train crews) sit at a round table. Each needs **2 chopsticks** (shared resources) to eat (prepare the train). If all grab their left chopstick simultaneously → **deadlock**.

### Files
- `TrainPreparationStation.java` — the table, manages 5 crews
- `TrainCrew.java` — each philosopher thread

### Deadlock Prevention Strategy — Asymmetric Ordering
```java
if (i == NUM_CREWS - 1) {
    // LAST crew picks RIGHT first, then LEFT (breaks the cycle)
    firstResource  = resources[(i + 1) % NUM_CREWS];
    secondResource = resources[i];
} else {
    // All others pick LEFT first, then RIGHT
    firstResource  = resources[i];
    secondResource = resources[(i + 1) % NUM_CREWS];
}
```
This ensures a **circular wait can never form** → no deadlock.

### States visible in Admin Dashboard
| Emoji | State | Meaning |
|-------|-------|---------|
| 🤔 | THINKING | Crew is resting between preparations |
| 🤤 | HUNGRY | Crew is waiting for both resources |
| 🚂 | EATING | Crew has both resources, preparing the train |

---

## 🗂️ Summary Table

| Concept | Java File(s) | Key Mechanism | Where to See It |
|---------|-------------|---------------|-----------------|
| PCB + 5-State | `ProcessControlBlock.java`, `BookingProcess.java` | `ProcessState` enum + `setState()` | Spring Boot console + Admin PCB table |
| CPU Scheduling | `FCFSScheduler.java`, `SJFScheduler.java`, `RoundRobinScheduler.java`, `PriorityScheduler.java` | FIFO / PriorityQueue / Circular / MaxHeap | Admin Dashboard scheduler buttons |
| Mutex | `CriticalSectionGuard.java` | `ReentrantLock` | 2-tab simultaneous booking + console |
| Readers-Writers | `AvailabilityMonitor.java` | 2 `Semaphore`s (readMutex + writeLock) | Admin → Active Readers counter |
| Producer-Consumer | `TicketBuffer.java`, `TicketProducer.java`, `TicketConsumer.java` | 3 `Semaphore`s (empty + full + mutex) | Admin → Buffer counter + console |
| Dining Philosophers | `TrainPreparationStation.java`, `TrainCrew.java` | Asymmetric resource ordering | Admin → Crew simulation cards |
