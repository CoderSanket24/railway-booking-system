package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;

/**
 * MEMBER 5: Consumer Thread
 * Books tickets from bounded buffer
 */
@Component
public class TicketConsumer extends Thread {
    
    private TicketBuffer buffer;
    private int consumerId;
    private int ticketsToBook;
    
    public TicketConsumer() {
        // Default constructor for Spring
    }
    
    public void initialize(TicketBuffer buffer, int consumerId, int ticketsToBook) {
        this.buffer = buffer;
        this.consumerId = consumerId;
        this.ticketsToBook = ticketsToBook;
    }
    
    @Override
    public void run() {
        System.out.println("[CONSUMER " + consumerId + " STARTED] Booking " + ticketsToBook + " tickets");
        
        for (int i = 0; i < ticketsToBook; i++) {
            try {
                TicketBuffer.Ticket ticket = buffer.consume();
                System.out.println("[CONSUMER " + consumerId + "] Successfully booked: " + ticket);
                
                // Simulate booking processing time
                Thread.sleep(800);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("[CONSUMER " + consumerId + "] Interrupted");
                break;
            }
        }
        
        System.out.println("[CONSUMER " + consumerId + " FINISHED] All tickets booked");
    }
}
