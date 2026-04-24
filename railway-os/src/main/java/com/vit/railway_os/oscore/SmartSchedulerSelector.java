package com.vit.railway_os.oscore;

import com.vit.railway_os.model.Train;
import com.vit.railway_os.repository.TrainRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * OS CONCEPT: Intelligent CPU Scheduling Selector (Unit III)
 *
 * In a real OS, the scheduler is chosen by the kernel based on the current
 * system state. This component mirrors that behaviour: it inspects each
 * booking's characteristics and the live queue depth, then selects the most
 * appropriate scheduling algorithm — and explains WHY.
 *
 * Decision Matrix:
 * ┌───────────────────────────────┬──────────┬──────────────────────────────────────────────┐
 * │ Condition                     │ Algo     │ Academic Justification                        │
 * ├───────────────────────────────┼──────────┼──────────────────────────────────────────────┤
 * │ isTatkal == true              │ PRIORITY │ High-priority process preempts normal queue   │
 * │ seatsNeeded == 1              │ SJF      │ Shortest job → minimises avg. waiting time   │
 * │ availableSeats < 15% total    │ SJF      │ Scarce resource → drain short tasks first    │
 * │ concurrentQueueDepth >= 3     │ RR       │ High load → time-sharing prevents starvation │
 * │ seatsNeeded >= 5 (group)      │ FCFS     │ Bulk jobs in arrival order, no discrimination│
 * │ Default                       │ FCFS     │ Simple, fair, predictable, no starvation     │
 * └───────────────────────────────┴──────────┴──────────────────────────────────────────────┘
 */
@Component
public class SmartSchedulerSelector {

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private FCFSScheduler         fcfsScheduler;
    @Autowired
    private SJFScheduler          sjfScheduler;
    @Autowired
    private RoundRobinScheduler   roundRobinScheduler;
    @Autowired
    private PriorityScheduler     priorityScheduler;

    /**
     * Result object returned by select() — contains both the algorithm name
     * and a human-readable reason that is pushed to the OS Event Log.
     */
    public static class Decision {
        public final String algorithm;
        public final String reason;

        public Decision(String algorithm, String reason) {
            this.algorithm = algorithm;
            this.reason    = reason;
        }
    }

    /**
     * Inspect booking characteristics + live system state → return the best scheduler.
     *
     * @param seatsNeeded  number of seats being requested
     * @param isTatkal     whether this is an urgent Tatkal booking
     * @param trainNumber  train number (to look up available seat count)
     */
    public Decision select(int seatsNeeded, boolean isTatkal, String trainNumber) {

        // ── Rule 1: TATKAL → PRIORITY ──────────────────────────────────────────
        // Tatkal bookings carry priority=1 (highest). Priority scheduling
        // ensures they jump ahead of all standard processes in the ready queue,
        // exactly as an OS handles high-priority interrupt-driven I/O requests.
        if (isTatkal) {
            return new Decision("PRIORITY",
                "TATKAL booking detected — high-priority process (priority=1) preempts standard queue [Priority Scheduling]");
        }

        // ── Rule 2: LIVE QUEUE DEPTH ≥ 3 → ROUND ROBIN ────────────────────────
        // When 3+ bookings are queued concurrently, FCFS causes a "convoy effect"
        // (one long job blocks all shorter ones). Round Robin time-slices the CPU
        // so every user sees responsiveness — essential for interactive systems.
        int totalQueueDepth = fcfsScheduler.getQueueSize()
                + sjfScheduler.getQueueSize()
                + roundRobinScheduler.getQueueSize()
                + priorityScheduler.getQueueSize();

        if (totalQueueDepth >= 3) {
            return new Decision("RR",
                "High concurrency detected (" + totalQueueDepth + " bookings queued) — Round Robin prevents convoy effect & ensures fair CPU time-sharing");
        }

        // ── Rule 3: SCARCE SEATS (< 15% remaining) → SJF ─────────────────────
        // When seats are nearly sold out, processing the shortest requests first
        // maximises the number of successful bookings before the last seat goes.
        // SJF is provably optimal for minimising average waiting time.
        Optional<Train> trainOpt = trainRepository.findByTrainNumber(trainNumber);
        if (trainOpt.isPresent()) {
            Train train = trainOpt.get();
            double fillRatio = 1.0 - ((double) train.getAvailableSeats() / train.getTotalSeats());
            if (fillRatio >= 0.85) {   // 85%+ full → scarce
                return new Decision("SJF",
                    "Train is " + String.format("%.0f", fillRatio * 100) + "% full ("
                    + train.getAvailableSeats() + " seats left) — SJF drains shortest jobs first to maximise throughput on scarce resource");
            }
        }

        // ── Rule 4: SINGLE SEAT → SJF ────────────────────────────────────────
        // A 1-seat booking has the shortest possible burst time (10ms).
        // SJF guarantees minimum average waiting time when burst lengths are known.
        if (seatsNeeded == 1) {
            return new Decision("SJF",
                "Single-seat booking (burst=10ms) — SJF selects shortest job, minimising average waiting time across all processes");
        }

        // ── Rule 5: GROUP / BULK BOOKING (≥ 5 seats) → FCFS ─────────────────
        // Large bookings should not be penalised or preempted mid-execution.
        // FCFS guarantees arrival-order processing with no starvation for long jobs.
        if (seatsNeeded >= 5) {
            return new Decision("FCFS",
                "Group/bulk booking (" + seatsNeeded + " seats, burst=" + seatsNeeded * 10 + "ms) — FCFS preserves arrival order, no discrimination against long jobs");
        }

        // ── Default: FCFS ─────────────────────────────────────────────────────
        return new Decision("FCFS",
            "Standard booking — FCFS applied: simple, predictable, guarantees no starvation");
    }
}
