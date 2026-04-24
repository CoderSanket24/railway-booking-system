package com.vit.railway_os.oscore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;

/**
 * MEMBER 5: Producer-Consumer Problem
 * Bounded Buffer for ticket generation and booking.
 * Now a Spring @Component so it can be shared across all booking requests.
 */
@Component
public class TicketBuffer {
    
    private static final int BUFFER_SIZE = 10;
    private final Queue<Ticket> buffer = new LinkedList<>();

    // Semaphores for synchronization
    private final Semaphore empty = new Semaphore(BUFFER_SIZE); // Count empty slots
    private final Semaphore full  = new Semaphore(0);           // Count filled slots
    private final Semaphore mutex = new Semaphore(1);           // Mutual exclusion

    @Autowired
    private OsStateTracker tracker;

    // No-arg constructor required by Spring
    public TicketBuffer() {}
    
    /**
     * Producer: Generate tickets and add to buffer
     */
    public void produce(Ticket ticket) throws InterruptedException {
        empty.acquire();  // Wait if buffer is full
        mutex.acquire();  // Enter critical section
        
        buffer.add(ticket);
        System.out.println("[PRODUCER] Generated ticket: " + ticket + " | Buffer size: " + buffer.size());
        
        if (tracker != null) tracker.updateBufferCount(buffer.size());
        
        mutex.release();  // Exit critical section
        full.release();   // Signal that buffer has item
    }
    
    /**
     * Consumer: Book tickets from buffer
     */
    public Ticket consume() throws InterruptedException {
        full.acquire();   // Wait if buffer is empty
        mutex.acquire();  // Enter critical section
        
        Ticket ticket = buffer.poll();
        System.out.println("[CONSUMER] Booked ticket: " + ticket + " | Buffer size: " + buffer.size());
        
        if (tracker != null) tracker.updateBufferCount(buffer.size());
        
        mutex.release();  // Exit critical section
        empty.release();  // Signal that buffer has space
        
        return ticket;
    }
    
    public int getBufferSize() {
        return buffer.size();
    }
    
    /**
     * Inner class representing a ticket
     */
    public static class Ticket {
        private int ticketId;
        private String trainNumber;
        private int seatNumber;
        
        public Ticket(int ticketId, String trainNumber, int seatNumber) {
            this.ticketId = ticketId;
            this.trainNumber = trainNumber;
            this.seatNumber = seatNumber;
        }
        
        @Override
        public String toString() {
            return String.format("Ticket[ID=%d, Train=%s, Seat=%d]", ticketId, trainNumber, seatNumber);
        }
        
        public int getTicketId() { return ticketId; }
        public String getTrainNumber() { return trainNumber; }
        public int getSeatNumber() { return seatNumber; }
    }
}
