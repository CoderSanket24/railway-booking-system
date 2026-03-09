package com.vit.railway_os.oscore;

public class BookingProcess extends Thread implements Comparable<BookingProcess> {
    private int userId;
    private int seatsNeeded;
    private boolean isTatkal;
    private CriticalSectionGuard guard;

    public BookingProcess(int userId, int seatsNeeded, boolean isTatkal, CriticalSectionGuard guard) {
        this.userId = userId;
        this.seatsNeeded = seatsNeeded;
        this.isTatkal = isTatkal;
        this.guard = guard;
    }

    @Override
    public void run() {
        System.out.println("[PROCESS STATE: RUNNING] Process " + userId + " started execution.");
        guard.bookSeat(userId, seatsNeeded);
        System.out.println("[PROCESS STATE: TERMINATED] Process " + userId + " finished.");
    }

    // For Member 5's Priority Queue: Tatkal (VIP) gets higher priority
    @Override
    public int compareTo(BookingProcess other) {
        return Boolean.compare(other.isTatkal, this.isTatkal);
    }
}