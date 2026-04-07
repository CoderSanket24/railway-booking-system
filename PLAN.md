# Implementation Plan: Real OS Events Visualization

## Vision
- **User side**: Premium real-time railway booking app (IRCTC-feel, seat map, live counts)
- **Admin side**: OS concepts dashboard showing events fired by REAL user bookings — not manual simulations

## What "Real" Means
Every booking triggers actual OS mechanisms:
- CPU scheduler picks the booking as a process → shown in scheduler queue
- Mutex lock prevents double-booking → shown live
- Memory allocated for the booking session → shown in memory map
- File written to booking record → disk I/O shown
- TLB checked for seat table address → shown in TLB stats
- Banker's algorithm runs to avoid deadlock among concurrent bookings → shown live

## Approach
1. **Backend** — `BookingController` already calls `TrainPreparationStation`, mutex, scheduler etc.
   - Add a `/api/admin/live-events` endpoint that returns the last N OS events from a shared event log
   - Each booking push → event log entry: `{type, detail, pid, timestamp}`

2. **User Dashboard** — Enhance with:
   - Live seat availability bar per train
   - Seat class selector (AC/SL/GN)
   - OS indicator strip: shows active scheduler, mutex state, memory usage

3. **Admin Dashboard** — Replace manual simulator tabs with:
   - **Live OS Event Feed**: real-time stream of events from bookings
   - **CPU Scheduler**: shows booking PIDs entering queues in real-time
   - **Memory Map**: shows allocations that happen per booking
   - **Mutex/Lock Monitor**: shows lock state during concurrent bookings
   - **File I/O log**: booking records being written
   - **OS Concepts** tab still exists but shows metrics from real usage (page faults from actual seat lookups, TLB hits, etc.)

## Files to Create/Modify

### Backend
- `OsEventLog.java` (new) — shared event bus, stores last 100 OS events
- `BookingController.java` — push events to OsEventLog on each booking
- `AdminController.java` — expose `/api/admin/live-events` GET endpoint

### Frontend
- `Dashboard.tsx` — enhance UX: seat class, live indicators, OS strip
- `AdminDashboard.tsx` — rewrite live panel to consume real events
- `simulators/OsSimulatorHub.tsx` — rename to "OS Metrics" tab showing real stats
