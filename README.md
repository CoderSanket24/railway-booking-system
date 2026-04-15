# 🚄 RailBooker OS — Railway Booking System with Live OS Visualization

> A full-stack railway ticket booking platform where every real user booking triggers live, backend-driven Operating System concept visualizations in the admin dashboard. Not a simulation — every OS mechanism runs on actual booking data.

---

## 📌 Project Overview

**RailBooker OS** is a dual-interface web application built as an Operating Systems course project. It demonstrates 6 core OS units through a real, working railway reservation system:

| Interface | Purpose |
|---|---|
| 🧑‍💻 **User Side** | Real railway ticket booking (search, book, PNR, history) |
| 🖥️ **Admin Side** | Live visualization of OS concepts triggered by real bookings |

When a user books a ticket, the backend fires a chain of real OS events — mutex locks, scheduler dispatch, Banker's Algorithm safety check, TLB address translation, memory allocation, and indexed file write — all visible in the admin dashboard in real time.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend (React/Vite)                    │
│  ┌──────────────────────┐     ┌──────────────────────────────┐  │
│  │   User Dashboard     │     │     Admin Dashboard          │  │
│  │  - Search trains     │     │  - 📡 Live OS Event Feed     │  │
│  │  - Book tickets      │     │  - ⚙️ Schedulers & Sync      │  │
│  │  - View PNR/history  │     │  - 📊 OS Metrics             │  │
│  └────────┬─────────────┘     └──────────────┬───────────────┘  │
│           │  POST /api/book                  │  GET /api/admin/ │
└───────────┼──────────────────────────────────┼──────────────────┘
            │                                  │ polling (1.2s)
┌───────────▼──────────────────────────────────▼──────────────────┐
│                     Backend (Spring Boot)                        │
│                                                                  │
│  BookingController ──► OsEventLog (ring buffer, thread-safe)     │
│       │                     ▲                                    │
│       ├── FCFSScheduler      │── pushScheduler()                 │
│       ├── CriticalSectionGuard ── pushMutex()                    │
│       ├── AvailabilityMonitor ─── pushReader()                   │
│       ├── BankersAlgorithm ────── pushBanker()                   │
│       ├── TLBSimulator ─────────── pushTLB()                     │
│       ├── TicketBuffer ─────────── produce() / consume()         │
│       └── FileManagementSimulator ─ pushFileIO()                 │
│                                                                  │
│  MySQL Database ◄── JPA/Hibernate (trains, bookings, users)      │
└──────────────────────────────────────────────────────────────────┘
```

---

## 🧠 OS Concepts Implemented

### Unit I — Process Management
| Class | Concept |
|---|---|
| `ProcessControlBlock.java` | PCB structure (PID, state, burst time, priority) |
| `BookingProcess.java` | Each booking runs as a real Java thread with PCB |
| `ProcessState.java` | `NEW → READY → RUNNING → WAITING → TERMINATED` lifecycle |

### Unit II — Process Synchronization
| Class | Concept |
|---|---|
| `CriticalSectionGuard.java` | **Mutex Lock** — `ReentrantLock` prevents double-booking under concurrency |
| `AvailabilityMonitor.java` | **Readers-Writers Problem** — multiple users can read seats; booking blocks all readers |
| `TicketBuffer.java` | **Producer-Consumer** — bookings produced into bounded buffer, consumed by schedulers |
| `TrainPreparationStation.java` | **Dining Philosophers** — 5 train crews compete for shared track resources |

### Unit III — CPU Scheduling
| Class | Algorithm | Data Structure |
|---|---|---|
| `FCFSScheduler.java` | First Come First Served | FIFO Queue |
| `SJFScheduler.java` | Shortest Job First | Min-Heap (fewest seats = shortest) |
| `RoundRobinScheduler.java` | Round Robin (quantum = 10ms) | Circular Queue |
| `PriorityScheduler.java` | Priority (Tatkal > Normal) | Max-Heap |

> The active scheduler can be switched live from the Admin Dashboard — all subsequent bookings use the new algorithm.

### Unit IV — Deadlock Handling
| Class | Concept |
|---|---|
| `BankersAlgorithm.java` | Safety check before every booking allocation |
| `ResourceAllocationGraph.java` | Cycle detection for deadlock identification |
| `DeadlockRecovery.java` | Recovery strategies (preemption, rollback) |

### Unit V — Memory Management
| Class | Concept |
|---|---|
| `TLBSimulator.java` | Translation Lookaside Buffer — virtual→physical address mapping |
| `MemoryPartitionManager.java` | Fixed & variable partitioning, First Fit / Best Fit / Worst Fit |
| `PageReplacementSimulator.java` | FIFO, LRU, Optimal page replacement |
| `BuddySystem.java` | Buddy memory allocation system |

### Unit VI — I/O & File Management
| Class | Concept |
|---|---|
| `FileManagementSimulator.java` | Sequential, Indexed, and Direct file organization |
| `DiskScheduler.java` | FCFS, SSTF, SCAN, C-SCAN, LOOK disk scheduling |
| `IOBufferManager.java` | I/O buffering strategies |

---

## 🛠️ Tech Stack

### Backend
| Technology | Version | Role |
|---|---|---|
| Java | 21 | Runtime |
| Spring Boot | 4.0.3 | REST API framework |
| Spring Security + JWT | — | Authentication & authorization |
| Spring Data JPA | — | ORM |
| Hibernate | 7.2.4 | Database layer |
| MySQL | 8.0+ | Persistent storage |
| Maven (Wrapper) | — | Build tool |

### Frontend
| Technology | Version | Role |
|---|---|---|
| React | 19 | UI framework |
| TypeScript | 5.9 | Type safety |
| Vite | 7.3 | Build tool & dev server |
| React Router | 7 | Client-side routing |
| Axios | 1.13 | HTTP client |
| Inter + Space Grotesk | — | Google Fonts |

---

## 📁 Project Structure

```
railway-booking-system/
├── frontend/                        # React/Vite frontend
│   └── src/
│       ├── components/
│       │   ├── UserLogin.tsx        # User login page
│       │   ├── Register.tsx         # User registration
│       │   ├── Dashboard.tsx        # User booking dashboard
│       │   ├── AdminLogin.tsx       # Admin login page
│       │   └── AdminDashboard.tsx   # Live OS monitoring dashboard
│       ├── config/
│       │   └── api.ts               # All API endpoint constants
│       ├── App.tsx                  # Router + protected routes
│       └── index.css                # Global design system
│
└── railway-os/                      # Spring Boot backend
    └── src/main/java/com/vit/railway_os/
        ├── controller/
        │   ├── BookingController.java  # /api/book, /api/trains
        │   ├── AdminController.java    # /api/admin/* endpoints
        │   └── AuthController.java     # /api/auth/login, /register
        ├── oscore/                     # All OS concept implementations
        │   ├── OsEventLog.java         # Thread-safe real-time event bus
        │   ├── OsStateTracker.java     # Live metric aggregator
        │   ├── CriticalSectionGuard.java
        │   ├── AvailabilityMonitor.java
        │   ├── TicketBuffer.java
        │   ├── FCFSScheduler.java
        │   ├── SJFScheduler.java
        │   ├── RoundRobinScheduler.java
        │   ├── PriorityScheduler.java
        │   ├── BankersAlgorithm.java
        │   ├── TLBSimulator.java
        │   ├── MemoryPartitionManager.java
        │   ├── FileManagementSimulator.java
        │   ├── DiskScheduler.java
        │   └── TrainPreparationStation.java
        ├── model/                      # JPA entities (User, Train, Booking)
        ├── repository/                 # Spring Data repositories
        └── security/                   # JWT filter, JwtUtil, SecurityConfig
```

---

## ⚙️ Prerequisites

- **Java 21** or later
- **Node.js 18+** and npm
- **MySQL 8.0+** running locally
- *(Optional)* Maven — project includes `mvnw` wrapper

---

## 🚀 Setup & Running

### 1. Database Setup

Create the MySQL database (tables are auto-created by Hibernate):

```sql
CREATE DATABASE railway_db;
```

### 2. Configure Database Credentials

Edit `railway-os/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/railway_db?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD
```

### 3. Start the Backend

```powershell
cd railway-os
.\mvnw.cmd clean package -DskipTests
java -jar target\railway-os-0.0.1-SNAPSHOT.jar
```

Backend starts on **http://localhost:8080**

> On first startup, 4 trains are auto-seeded into the database:
> Mumbai Rajdhani, Shatabdi Express, Duronto Express, Garib Rath Express

### 4. Start the Frontend

```powershell
cd frontend
npm install
npm run dev
```

Frontend starts on **http://localhost:5173**

---

## 🔑 Credentials

| Role | URL | Username | Password |
|---|---|---|---|
| User | http://localhost:5173/login | *(register first)* | — |
| Admin | http://localhost:5173/admin-login | `admin` | `123456` |

---

## 📱 Application Pages

### User Side
| Page | Route | Description |
|---|---|---|
| Login | `/login` | User authentication |
| Register | `/register` | New account creation |
| Dashboard | `/dashboard` | Search & book trains, booking history |

### Admin Side
| Page | Route | Description |
|---|---|---|
| Admin Login | `/admin-login` | Admin authentication |
| Admin Dashboard | `/admin` | Live OS monitoring |

---

## 🖥️ Admin Dashboard — Feature Guide

### 📡 Live OS Feed
- Real-time stream of every OS event fired by actual user bookings
- **Color-coded by OS unit**: App (green), CPU (blue), Sync (orange), Memory (purple), I/O (cyan), Deadlock (orange-red)
- **Filter** by event type (BOOKING, SCHEDULER, MUTEX, MEMORY, FILE_IO, TLB, BANKER, READER)
- **↓ Oldest First / ↑ Newest First** toggle — chronological mode shows numbered steps (01, 02, 03…)
- **Pause / Resume** live feed; **Clear** all events

**Booking pipeline events (in order):**
```
01  🎫  App      New booking request: [Name] → [Train] (N seats)
02  📦  Sync     Producer placed booking into buffer (size=1)
03  ⚙️  CPU      [FCFS] dispatching booking process P[id] (burst=10ms)
04  🧠  Memory   Allocating session memory for P[id] (~64KB)
05  ✅  Deadlock Banker's check: requesting N seats — safety algo PASSED
06  🔒  Sync     P[id] acquiring mutex lock on seat table (critical section)
07  ✅  Sync     Consumer dequeued booking from buffer (size=0)
08  🔓  Sync     P[id] releasing mutex lock — seat table updated
09  💿  I/O      Writing booking record PNR... to disk (INDEXED file org)
10  ⚡  Memory   TLB hit: booking page → physical frame 0x[addr]
11  🧠  Memory   Deallocating session memory (booking committed)
12  🎫  App      ✅ Booking CONFIRMED: PNR... | [Name] | [Train]
```

### ⚙️ Schedulers & Sync
- **CPU Scheduling panel** — switch between FCFS / SJF / Round Robin / Priority live; queue depth visualized as colored dots
- **Mutex state** — shows `🔴 LOCKED — P[pid] in critical section` or `🟢 UNLOCKED`
- **Readers-Writers** — live count of concurrent readers accessing the seat table
- **Producer-Consumer buffer** — current item count in the bounded TicketBuffer
- **Dining Philosophers** — 5 train crews (philosophers) competing for track (fork) resources

### 📊 OS Metrics
Aggregate statistics derived exclusively from real booking activity:
- TLB Hit Rate & EMAT calculation
- Total Mutex acquisitions
- Banker's Algorithm checks
- Memory allocations
- File I/O writes
- Confirmed bookings vs total booking attempts

---

## 🔌 API Reference

### Auth Endpoints
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/login` | Login (returns JWT) |
| POST | `/api/auth/register` | Register new user |

### User Endpoints (JWT required)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/trains` | List all trains (triggers Readers-Writers lock) |
| POST | `/api/book` | Book a ticket (triggers full OS pipeline) |
| GET | `/api/bookings/{userId}` | Fetch booking history |

### Admin Endpoints (JWT required)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/admin/live-events?limit=100` | Real-time OS event feed |
| POST | `/api/admin/live-events/clear` | Clear event log |
| GET | `/api/admin/monitor` | Live metrics (mutex, readers, buffer) |
| GET | `/api/admin/scheduler` | Current active scheduler |
| POST | `/api/admin/scheduler` | Change scheduler `{"scheduler":"SJF"}` |
| GET | `/api/admin/bankers` | Banker's Algorithm state |
| POST | `/api/admin/bankers/safety` | Run safety algorithm check |
| POST | `/api/admin/start-dining-philosophers` | Start Dining Philosophers demo |

---

## 🔄 How a Real Booking Triggers OS Events

```
User clicks "Confirm Booking"
         │
         ▼
POST /api/book (JWT authenticated)
         │
         ├─► OsEventLog.pushBooking("New request...")
         │
         ├─► ticketBuffer.produce(ticket)          ← Producer-Consumer
         │   OsEventLog.push("📦 Buffer size=1")
         │
         ├─► OsEventLog.pushScheduler("[FCFS] dispatching P[pid]")
         │
         ├─► OsEventLog.pushMemory("Allocating ~64KB")
         │
         ├─► BankersAlgorithm.isSafeState()        ← Deadlock avoidance
         │   OsEventLog.pushBanker("Safety check passed")
         │
         ├─► CriticalSectionGuard.bookSeat()        ← Mutex LOCK
         │   OsStateTracker.setMutexLocked(true)
         │   OsEventLog.pushMutex("🔒 Acquiring lock")
         │
         ├─► ticketBuffer.consume()                 ← Consumer
         │   OsEventLog.push("✅ Buffer size=0")
         │
         ├─► [MySQL] UPDATE train SET seats = seats - N
         ├─► [MySQL] INSERT INTO bookings (pnr, ...)
         │
         ├─► mutex.unlock() → OsStateTracker.setMutexLocked(false)
         │
         ├─► OsEventLog.pushFileIO("Writing PNR to disk")
         ├─► TLBSimulator → OsEventLog.pushTLB("TLB hit → 0x[frame]")
         ├─► OsEventLog.pushMemory("Deallocating session memory")
         └─► OsEventLog.pushBooking("✅ CONFIRMED: PNR...")

Admin dashboard polls GET /api/admin/live-events every 1.2s
→ All events appear in real-time feed
```

---

## 🚂 Available Trains

| Train | Route | Type | Price | Seats |
|---|---|---|---|---|
| Mumbai Rajdhani (EXP-12951) | Mumbai → Delhi | Rajdhani | ₹1,500 | 100 |
| Shatabdi Express (EXP-12952) | Mumbai → Pune | Shatabdi | ₹450 | 85 |
| Duronto Express (EXP-12953) | Mumbai → Bangalore | Duronto | ₹1,200 | 120 |
| Garib Rath Express (EXP-12954) | Delhi → Kolkata | Express | ₹650 | 42 |

---

## 👥 Team Contributions

| Member | OS Unit | Implementation |
|---|---|---|
| Member 1 | Unit I — Process Management | `ProcessControlBlock`, `BookingProcess`, `ProcessState` |
| Member 2 | Unit II — Synchronization | `CriticalSectionGuard`, `AvailabilityMonitor`, `TicketBuffer`, `TrainPreparationStation` |
| Member 3 | Unit III — CPU Scheduling | `FCFSScheduler`, `SJFScheduler`, `RoundRobinScheduler`, `PriorityScheduler` |
| Member 4 | Unit IV — Deadlocks | `BankersAlgorithm`, `ResourceAllocationGraph`, `DeadlockRecovery` |
| Member 5 | Unit V — Memory | `TLBSimulator`, `MemoryPartitionManager`, `PageReplacementSimulator`, `BuddySystem` |
| Member 6 | Unit VI — I/O & Files | `FileManagementSimulator`, `DiskScheduler`, `IOBufferManager` |

---

## 🐛 Troubleshooting

**Backend won't start — port 8080 already in use:**
```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object -ExpandProperty OwningProcess | ForEach-Object { Stop-Process -Id $_ -Force }
```

**`Cannot connect to MySQL` error:**
- Ensure MySQL 8.0 is running: `net start MySQL80`
- Verify credentials in `application.properties`
- Database `railway_db` must exist

**Frontend shows "Server connection error":**
- Confirm backend is running on port 8080
- Check CORS is enabled (`@CrossOrigin` on controllers)

**Admin dashboard shows 0 events:**
- Events are generated only by real bookings
- Go to user dashboard → book a ticket → events appear in Live Feed

---

## 📄 License

This project is for academic purposes — Operating Systems (SY SEM-II), VIT.

---

*Built with ☕ Java + ⚛️ React — where every ticket booking runs through a real OS pipeline.*
