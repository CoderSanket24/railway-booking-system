# 👥 RailBooker OS — Team Member Task Distribution

This document outlines a balanced, 5-member workload distribution for the **RailBooker OS** project. Each member handles a distinct layer of the Operating System architecture, ensuring an even split of logical complexity, algorithm implementation, and system integration.

---

## 👨‍💻 Member 1: Process Management & CPU Scheduling *(The Dispatcher)*
**Core Responsibility:** Handling how booking processes are created, queued, and dispatched to the CPU.

*   **Unit I (Process Management):** 
    *   Implement the `ProcessControlBlock` (PCB) to track PID, state, burst time, and priority.
    *   Manage the `ProcessState` lifecycle transitions (`NEW → READY → RUNNING → WAITING → TERMINATED`).
*   **Unit III (CPU Scheduling):**
    *   Implement the 4 core scheduling algorithms: `FCFSScheduler` (FIFO), `SJFScheduler` (Min-Heap), `RoundRobinScheduler` (Circular Queue), and `PriorityScheduler` (Max-Heap).
    *   Design the **Smart Auto-Scheduler** logic that dynamically picks the best algorithm based on the booking context (e.g., routing Tatkal bookings to Priority, single seats to SJF).
*   **Assigned Backend Files:**
    *   `oscore/ProcessControlBlock.java`
    *   `oscore/ProcessState.java`
    *   `oscore/BookingProcess.java`
    *   `oscore/FCFSScheduler.java`
    *   `oscore/SJFScheduler.java`
    *   `oscore/RoundRobinScheduler.java`
    *   `oscore/PriorityScheduler.java`
    *   `oscore/SmartSchedulerSelector.java`

---

## 👩‍💻 Member 2: Process Synchronization *(The Concurrency Guard)*
**Core Responsibility:** Solving classic synchronization problems to ensure data integrity when multiple users book tickets simultaneously.

*   **Unit II (Process Synchronization):**
    *   **Mutex Locks:** Create the `CriticalSectionGuard` (using `ReentrantLock`) to prevent double-booking of the exact same seat.
    *   **Readers-Writers Problem:** Implement the `AvailabilityMonitor` to allow multiple users to read seat availability concurrently, while ensuring a booking write blocks all readers.
    *   **Producer-Consumer:** Build the `TicketBuffer` where user booking requests are produced into a bounded buffer and consumed by the active CPU scheduler.
    *   **Dining Philosophers:** Implement the `TrainPlatformLock` to manage train crews safely competing for shared track resources.
*   **Assigned Backend Files:**
    *   `oscore/CriticalSectionGuard.java`
    *   `oscore/AvailabilityMonitor.java`
    *   `oscore/TicketBuffer.java`
    *   `oscore/TicketProducer.java`
    *   `oscore/TicketConsumer.java`
    *   `oscore/TrainPlatformLock.java`
    *   `oscore/TrainCrew.java`
    *   `oscore/TrainPreparationStation.java`

---

## 👨‍💻 Member 3: Deadlocks & Real-Time Telemetry *(The System Monitor)*
**Core Responsibility:** Ensuring the system doesn't freeze under heavy load and architecting the real-time event pipeline.

*   **Unit IV (Deadlock Handling):**
    *   Implement **Banker’s Algorithm** as a predictive safety check before allowing massive group bookings.
    *   Build the `ResourceAllocationGraph` to perform cycle detection and identify deadlocks.
    *   Implement `DeadlockRecovery` strategies (Preemption and Process Termination).
*   **Real-time Event Logging Architecture:**
    *   Build the `OsEventLog` (a thread-safe Ring Buffer) that captures every OS event (Mutex acquires, TLB hits, Scheduler dispatches) from the backend and streams it live to the Admin Dashboard.
*   **Assigned Backend Files:**
    *   `oscore/BankersAlgorithm.java`
    *   `oscore/ResourceAllocationGraph.java`
    *   `oscore/DeadlockRecovery.java`
    *   `oscore/OsEventLog.java`
    *   `oscore/OsStateTracker.java`

---

## 👩‍💻 Member 4: Memory Management *(The Allocator)*
**Core Responsibility:** Simulating how RAM, cache, and virtual memory are managed for user sessions and booking operations.

*   **Unit V (Memory Management):**
    *   **TLB Simulation:** Build the Translation Lookaside Buffer (`TLBSimulator`) to map virtual logical addresses to physical frames.
    *   **Memory Partitioning:** Implement Fixed & Dynamic partitioning algorithms (First Fit, Best Fit, Worst Fit) via `MemoryPartitionManager`.
    *   **Page Replacement:** Simulate page fault handling using FIFO, LRU, and Optimal page replacement algorithms (`PageReplacementSimulator`).
    *   **Buddy System:** Implement the `BuddySystem` for dividing contiguous memory into power-of-two blocks for incoming user sessions.
*   **Assigned Backend Files:**
    *   `oscore/TLBSimulator.java`
    *   `oscore/MemoryPartitionManager.java`
    *   `oscore/PageReplacementSimulator.java`
    *   `oscore/BuddySystem.java`

---

## 👨‍💻 Member 5: I/O, File Systems & Full-Stack Integration *(The Architect)*
**Core Responsibility:** Managing how booking records are saved to disk and ensuring the Admin UI correctly visualizes the team's combined backend logic.

*   **Unit VI (I/O & File Management):**
    *   **File Organization:** Simulate Sequential, Indexed, and Direct file structures for saving PNR records via `FileManagementSimulator`.
    *   **Disk Scheduling:** Implement FCFS, SSTF, SCAN, C-SCAN, and LOOK disk head movement algorithms (`DiskScheduler`).
    *   **I/O Buffering:** Implement `IOBufferManager` to handle data transfer between primary memory and secondary storage.
*   **Full-Stack Wiring & UI:**
    *   Connect the React Admin Dashboard to the Spring Boot REST endpoints.
    *   Build and wire the UI components (Stat Pills, Live OS Feed Table, Sync Cards) to visually demonstrate the OS concepts working together in real-time.
*   **Assigned Backend Files:**
    *   `oscore/FileManagementSimulator.java`
    *   `oscore/DiskScheduler.java`
    *   `oscore/IOBufferManager.java`
    *   `controller/AdminController.java`
    *   `controller/BookingController.java`
    *   `controller/AuthController.java`
