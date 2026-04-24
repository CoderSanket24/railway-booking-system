# 🎬 OS Concept Demonstration Guide — RailBooker OS

> **How to show each implemented OS concept live to a teacher/examiner**  
> Keep both browser tabs open: **User Dashboard** and **Admin Dashboard**

---

## ⚡ Quick Start (Before Any Demo)

**Terminal 1 — Backend:**
```powershell
cd railway-os
java -jar target\railway-os-0.0.1-SNAPSHOT.jar
```

**Terminal 2 — Frontend:**
```powershell
cd frontend
npm run dev
```

| Portal | URL | Credentials |
|---|---|---|
| 🧑 User | http://localhost:5173/login | any registered account |
| 🖥️ Admin | http://localhost:5173/admin-login | admin / 123456 |

> **Pro Tip:** Open User and Admin dashboards **side by side** in split-screen or two monitors for maximum visual impact.

---

## ✅ Concept 1 — Process Control Block (PCB) + 5-State Model

### What to explain
Every booking request becomes a **process** with its own PCB (PID, state, burst time, priority). It transitions through all 5 states: `NEW → READY → RUNNING → WAITING → TERMINATED`.

### How to show it

**Step 1:** Open Admin Dashboard → click **"⚙️ Schedulers & Sync"** tab  
**Step 2:** Scroll to the bottom — find **"🗂 PCB State Transitions"** panel  
**Step 3:** Go to the User Dashboard and book a ticket  
**Step 4:** Watch the PCB panel update in real time:

```
P6218    STANDARD    NEW
P6218    STANDARD    READY
P6218    STANDARD    RUNNING
P6218    STANDARD    WAITING      ← waiting for mutex lock
P6218    STANDARD    TERMINATED
```

**Step 5:** Also check the **Spring Boot terminal** for detailed output:
```
[PROCESS CREATED] PCB[PID=6218, State=NEW, Priority=5, BurstTime=1000, Type=STANDARD]
[PCB] Process 6218 state: NEW → READY
[PCB] Process 6218 state: READY → RUNNING
[PCB] Process 6218 state: RUNNING → WAITING
[PCB] Process 6218 state: WAITING → RUNNING
[PCB] Process 6218 state: RUNNING → TERMINATED
[METRICS] Turnaround: 2145ms, Waiting: 1145ms, Response: 3ms
```

### What to point out
- `PID` = userId (real process identifier)
- `burstTime` = `seatsBooked × 1000ms` — realistic CPU burst estimate
- The `WAITING` state occurs when the mutex is held by another booking
- Real OS PCB fields: `arrivalTime`, `startTime`, `completionTime`, `priority`

---

## ✅ Concept 2 — CPU Scheduling (FCFS, SJF, Round Robin, Priority)

### What to explain
When multiple bookings arrive simultaneously, the OS scheduler decides **which one gets CPU first**. Four algorithms are implemented and switchable live.

### How to show it

**Step 1:** Open Admin Dashboard → **"⚙️ Schedulers & Sync"** tab  
**Step 2:** You see 4 scheduler cards: FCFS, SJF, RR, Priority — the **ACTIVE** badge shows the current one

#### Demo A — Switch to SJF
**Step 3:** Click the **SJF** card  
**Step 4:** Go to User Dashboard — book 1 seat on any train  
**Step 5:** Watch the Live Feed show:
```
⚙️  CPU    [SJF] dispatching booking P6218 (burst=10ms)
```
The "ACTIVE SCHEDULER" stat pill at the top now shows **SJF**

#### Demo B — Show FCFS vs SJF difference (if two accounts available)
- Open two browser tabs with two different user accounts
- Book many seats (e.g., 5) from tab 1, then 1 seat from tab 2 simultaneously
- **FCFS:** tab 1 goes first (arrival order)
- **SJF:** tab 2 goes first (fewer seats = shorter burst time)

#### Demo C — Priority (Tatkal vs Normal)
- The `isTatkal: true` payload gives `priority = 1`
- **Priority scheduler:** Tatkal booking jumps ahead of all normal bookings in queue
- Queue depth dots on each card show how many bookings are waiting

### What to point out
| Scheduler | Data Structure | Key Property |
|---|---|---|
| FCFS | FIFO Queue | Simple, no starvation, convoy effect possible |
| SJF | Min-Heap (PriorityQueue) | Optimal avg waiting time, can starve long jobs |
| Round Robin | Circular Queue | Fair, preemptive, `quantum = 10ms` |
| Priority | Max-Heap | Tatkal(1) > Standard(5), higher priority = lower number |

---

## ✅ Concept 3 — Mutex Lock & Critical Section

### What to explain
Two users booking simultaneously can cause **double-booking** (race condition). A `ReentrantLock` (mutex) prevents this by allowing only one thread inside the critical section at a time.

### How to show it — BEST DEMO

**Step 1:** Open **two browser tabs** — both logged in as different users (register a second account if needed)  
**Step 2:** Open Admin Dashboard in a 3rd tab — go to **"📡 Live OS Feed"**  
**Step 3:** On the Garib Rath Express (EXP-12954, only 42 seats) or any nearly-full train — both tabs book simultaneously  
**Step 4:** Watch the Live Feed:

```
🔒  Sync   P101 acquiring mutex lock on seat table (critical section)
   [P102 is now BLOCKED — see "Mutex: LOCKED P101" in stat bar]

🔓  Sync   P101 releasing mutex lock — seat table updated
   [P102 unblocks automatically]

🔒  Sync   P102 acquiring mutex lock on seat table (critical section)
```

**Step 5:** Point to the **"MUTEX"** stat pill at top:  
- During booking: `🔴 LOCKED — P101 in critical section`  
- After booking: `🟢 OPEN`

**Step 6:** Show the Spring Boot terminal:
```
[MUTEX LOCKED]   Process 101 is safely accessing the database.
SUCCESS: User 101 booked 1 seats. DB Remaining: 41
[MUTEX UNLOCKED] Process 101 released the database.

[MUTEX LOCKED]   Process 102 is safely accessing the database.
SUCCESS: User 102 booked 1 seats. DB Remaining: 40
[MUTEX UNLOCKED] Process 102 released the database.
```

### What to point out
- Without the mutex: both see 42 seats → both book → DB shows 40 (lost one booking) or -1 seats
- `mutex.lock()` = P(semaphore), `mutex.unlock()` = V(semaphore)
- The **2-second `Thread.sleep`** inside the critical section is intentional — proves the lock holds
- `ReentrantLock` is Java's implementation of a binary semaphore/mutex

---

## ✅ Concept 4 — Readers-Writers Problem

### What to explain
**Readers** (checking seat availability) can run concurrently. **Writers** (booking = modifying the DB) need exclusive access. Two semaphores manage this: `readMutex` and `writeLock`.

### How to show it

**Step 1:** Admin Dashboard → **"⚙️ Schedulers & Sync"** → look at the **"Readers-Writers"** section:
```
Readers-Writers — Train Availability
Multiple users can read concurrently; bookings wait for exclusive write
N concurrent readers
```

**Step 2:** Every time the User Dashboard loads (GET /api/trains), it calls `startRead()` / `endRead()`. Watch the Live Feed:
```
👓  Sync   Reader P6218 acquiring shared lock on train table
⚡  Memory TLB lookup: seat table page for P6218 → frame cached
👓  Sync   Reader P6218 released shared lock
```

**Step 3:** Open the User Dashboard in 3 tabs simultaneously — all 3 fetch trains at the same time:
- Watch **"Active Readers"** stat pill briefly show 2 or 3

**Step 4:** While readers are active, start a booking (writer) — the writer's mutex.lock() will block until all readers finish reading

### What to point out
- `readMutex` (binary semaphore) protects the `readersCount` counter
- `writeLock` (binary semaphore) grants exclusive writer access
- First reader acquires `writeLock` (blocks writers); last reader releases it
- **Multiple readers can coexist; one writer excludes all**

### Semaphore Table
| Semaphore | Initial | P (acquire) | V (release) |
|---|---|---|---|
| `readMutex` | 1 | Before changing `readersCount` | After changing `readersCount` |
| `writeLock` | 1 | 1st reader enters / Writer enters | Last reader exits / Writer exits |

---

## ✅ Concept 5 — Producer-Consumer (Bounded Buffer)

### What to explain
In IRCTC, ticket slots are **produced** (generated by the system) and **consumed** (assigned to users). A bounded buffer of size 10 with 3 semaphores (`empty`, `full`, `mutex`) coordinates this.

### How to show it

**Every real booking now uses the Producer-Consumer pattern:**

**Step 1:** Admin Dashboard → **"📡 Live OS Feed"** → click **"↓ Oldest First"** toggle  
**Step 2:** Go to User Dashboard and book 1 ticket  
**Step 3:** In the Live OS Feed, find these 2 events:
```
📦  Sync   Producer placed booking P6218 into buffer (size=1)
✅  Sync   Consumer dequeued booking P6218 from buffer (size=0)
```

**Step 4:** Point to the **"Buffer Items"** stat pill at top — it briefly spikes to `1` then returns to `0`

**Step 5:** Explain the semaphore trace:
```
Producer (BookingController):
  empty.acquire()  → empty: 10→9  (one slot used)
  mutex.acquire()  → buffer.add(ticket) → size=1
  mutex.release()
  full.release()   → full: 0→1   (one item ready)

Consumer (BookingController, same request):
  full.acquire()   → full: 1→0   (consuming item)
  mutex.acquire()  → buffer.poll() → size=0
  mutex.release()
  empty.release()  → empty: 9→10 (slot freed)
```

**Step 6 (Advanced Demo):** Use the standalone Producer-Consumer demo via API:
```powershell
$token = "YOUR_JWT_TOKEN"
Invoke-RestMethod -Uri "http://localhost:8080/api/producer-consumer" `
  -Method POST `
  -Headers @{Authorization="Bearer $token"; "Content-Type"="application/json"} `
  -Body '{"trainNumber":"EXP-12951","ticketsToGenerate":20,"numConsumers":3}'
```
Then watch the Spring Boot terminal:
```
[PRODUCER] Generated ticket: Ticket[ID=1, Train=EXP-12951, Seat=1] | Buffer size: 1
[CONSUMER-1] Booked ticket: Ticket[ID=1, Train=EXP-12951, Seat=1] | Buffer size: 0
[PRODUCER] Generated ticket: Ticket[ID=2, Train=EXP-12951, Seat=2] | Buffer size: 1
[PRODUCER] Generated ticket: Ticket[ID=3, Train=EXP-12951, Seat=3] | Buffer size: 2
[CONSUMER-2] Booked ticket: Ticket[ID=2, Train=EXP-12951, Seat=2] | Buffer size: 1
```

### Semaphore Table
| Semaphore | Initial | Meaning |
|---|---|---|
| `empty` | 10 (BUFFER_SIZE) | How many **free slots** are available |
| `full` | 0 | How many **items** are ready to consume |
| `mutex` | 1 | Mutual exclusion on the buffer itself |

---

## ✅ Concept 6 — Dining Philosophers (Deadlock Avoidance)

### What to explain
5 train crews (philosophers) each need **2 shared track resources** (chopsticks/forks) to prepare a train (eat). If all grab one resource simultaneously → circular wait → **deadlock**. Solution: asymmetric ordering.

### How to show it

**Step 1:** Admin Dashboard → **"⚙️ Schedulers & Sync"** tab → scroll to **"Dining Philosophers — Deadlock Avoidance"**  

**Step 2:** Click the **"▶ Start"** button  

**Step 3:** Watch the 5 crew cards cycle through states:
```
😴  Crew 0 → THINKING (Resting)
🤤  Crew 1 → HUNGRY (Waiting for resources)
🚂  Crew 2 → EATING (Preparing Train — has both resources)
🤤  Crew 3 → HUNGRY (Waiting for resources)
😴  Crew 4 → THINKING (Resting)
```

**Step 4:** Point out that the system **never deadlocks** because of the asymmetric rule:
- Crews 0–3: pick **LEFT resource** first, then RIGHT
- Crew 4 (last): picks **RIGHT resource** first, then LEFT
- This breaks the **circular wait** condition

### Four Deadlock Conditions (all must hold for deadlock):
| Condition | Where it applies | Prevention |
|---|---|---|
| Mutual Exclusion | Each resource = only 1 user | ✓ Necessary (can't remove) |
| Hold and Wait | Crew holds one, waits for other | ✓ Necessary (can't remove) |
| No Preemption | Can't force crew to drop resource | ✓ Necessary (can't remove) |
| **Circular Wait** | Crew A waits for B, B waits for A… | ❌ **Broken by asymmetric ordering** |

### What to point out
- The entire system runs for ~30 seconds then stops automatically
- No crew ever enters a permanent `HUNGRY` (waiting) state — proof of no deadlock
- Real-world analogy: Train crews competing for the same platform/track

---

## ✅ Concept 7 — Banker's Algorithm (Deadlock Avoidance)

### What to explain
Before granting any resource (seat allocation), the OS runs the Banker's Algorithm to check if the system will remain in a **safe state**. If not safe → booking is rejected.

### How to show it

**Step 1:** Admin Dashboard → **"📡 Live OS Feed"** → book any ticket  
**Step 2:** Find this event in the feed:
```
✅  Deadlock   Banker's check: P6218 requesting 1 EXP-12952 seats — running safety algo
```

**Step 3:** For the detailed Banker's state, use the API:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/admin/bankers" `
  -Headers @{Authorization="Bearer $ADMIN_TOKEN"}
```
Response shows: `available[]`, `allocation[][]`, `need[][]`

**Step 4:** Run the safety algorithm manually:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/admin/bankers/safety" `
  -Method POST `
  -Headers @{Authorization="Bearer $ADMIN_TOKEN"}
```
Response:
```json
{
  "safe": true,
  "safeSequence": [0, 2, 3, 1, 4],
  "steps": ["Process 0 can run...", "Process 2 can run..."]
}
```

### What to point out
- Every booking triggers the safety check first — if it would cause deadlock, the booking is refused
- The safe sequence proves **all processes can complete**
- Resources = seat types; Allocation = seats already booked; Need = seats still required

---

## ✅ Concept 8 — TLB & Memory Management

### What to explain
Virtual memory uses a **Translation Lookaside Buffer (TLB)** to cache virtual→physical address mappings. A TLB hit is fast (cache), a TLB miss is slow (page table walk). Memory is allocated/deallocated per booking session.

### How to show it

**Step 1:** Admin Dashboard → **"📡 Live OS Feed"** → book a ticket  
**Step 2:** Find these events:
```
⚡  Memory   TLB lookup: seat table page for P6218 → frame cached       ← during GET /api/trains
🧠  Memory   Allocating session memory for P6218 (booking context ~64KB) ← booking start
⚡  Memory   TLB hit: booking page for P6218 → physical frame 0x4a       ← during booking
🧠  Memory   Deallocating session memory for P6218 (booking committed)   ← booking end
```

**Step 3:** Admin Dashboard → **"📊 OS Metrics"** tab → find:
```
TLB Hit Rate: 95%
EMAT = hit_time + (miss_rate × page_fault_penalty)
Memory Allocations: N
```

**Step 4:** For detailed TLB state:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/admin/tlb" `
  -Headers @{Authorization="Bearer $ADMIN_TOKEN"}
```

### What to point out
- **TLB Hit** = virtual address found in cache (fast) — most page accesses are hits after warm-up
- **TLB Miss** = must walk page table (slow) — first access to a new page
- Memory lifecycle: `allocate` at booking start → `deallocate` at commit — prevents memory leaks
- EMAT formula: `h × cache_time + (1-h) × (cache_time + memory_time)`

---

## ✅ Concept 9 — File Management (Indexed File Organization)

### What to explain
Booking records are stored using **Indexed File Organization** — like IRCTC's PNR system. An index maintains pointers to records for O(log n) lookup instead of O(n) sequential scan.

### How to show it

**Step 1:** Admin Dashboard → **"📡 Live OS Feed"** → book a ticket  
**Step 2:** Find this event:
```
💿  I/O   Writing booking record PNR1776183119333 to disk (INDEXED file org)
```

**Step 3:** Explain the 3 file organization types:

| Type | How it works | Access Time | Used for |
|---|---|---|---|
| **Sequential** | Records stored one after another | O(n) scan | Batch reports |
| **Indexed** | Separate index with PNR→offset pointers | O(log n) | PNR lookup ✅ |
| **Direct/Hashed** | PNR hash → direct block address | O(1) | Very fast lookups |

**Step 4:** For the full file system state:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/admin/filesystem" `
  -Headers @{Authorization="Bearer $ADMIN_TOKEN"}
```

**Step 5:** For disk scheduling visualization:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/admin/disk-scheduling" `
  -Headers @{Authorization="Bearer $ADMIN_TOKEN"}
```
Shows FCFS, SSTF, SCAN, C-SCAN total head movement comparison.

### What to point out
- Every `bookingRepository.save(booking)` = a disk write
- PNR = the index key for O(log n) retrieval
- Real IRCTC uses indexed file organization for PNR lookups

---

## 🗺️ Quick Demo Cheat Sheet

| OS Concept | Where to See It | What to Click/Do |
|---|---|---|
| **PCB + 5 States** | Admin → Schedulers & Sync → PCB panel | Book a ticket; watch state flow |
| **FCFS** | Admin → Schedulers & Sync | Click FCFS card (already default) → Book ticket → see "[FCFS] dispatching" in feed |
| **SJF** | Admin → Schedulers & Sync | Click SJF card → Book ticket → see "[SJF] dispatching" |
| **Round Robin** | Admin → Schedulers & Sync | Click RR card → Book ticket |
| **Priority** | Admin → Schedulers & Sync | Click Priority card → Book ticket |
| **Mutex Lock** | Admin → Live Feed + stat bar | Book from 2 tabs simultaneously; watch LOCKED/OPEN flip |
| **Readers-Writers** | Admin → Live Feed + "Active Readers" pill | Load trains tab (triggers reader lock) |
| **Producer-Consumer** | Admin → Live Feed + "Buffer Items" pill | Book ticket; look for 📦 Producer / ✅ Consumer events |
| **Dining Philosophers** | Admin → Schedulers & Sync → bottom | Click ▶ Start; watch 5 crew cards cycle states |
| **Banker's Algorithm** | Admin → Live Feed (Deadlock events) | Book ticket; see "Banker's check" event |
| **TLB / Memory** | Admin → Live Feed (Memory events) | Book ticket; see Allocate/TLB hit/Deallocate chain |
| **File I/O** | Admin → Live Feed (I/O events) | Book ticket; see "Writing booking record...INDEXED" |
| **OS Metrics** | Admin → 📊 OS Metrics tab | Book 2-3 tickets; view TLB hit rate, EMAT, totals |

---

## 🎯 Recommended Demo Order (for a 10-minute presentation)

1. **Show user portal** (1 min) — search trains, select one, open booking modal
2. **Open admin Live Feed side by side** (30 sec) — show it's empty, waiting
3. **Book a ticket** (2 min) — watch all 12 OS events appear step by step in the feed with step numbers (make sure "↓ Oldest First" is toggled)
4. **Point to stat bar** (1 min) — MUTEX flipped to LOCKED then OPEN, Buffer briefly 1→0, Reader was 1
5. **Switch scheduler to SJF** (1 min) — click SJF card, book again, show "[SJF] dispatching" in feed
6. **Dining Philosophers** (1 min) — click Start, show 5 crews cycling without deadlock
7. **OS Metrics tab** (1 min) — show aggregated TLB hit rate, confirmed bookings
8. **Simultaneous booking demo** (2 min) — two tabs booking same train, watch mutex LOCKED/blocked behavior

---

*Every concept you show is driven by **real data from real bookings** — not a simulation.*
