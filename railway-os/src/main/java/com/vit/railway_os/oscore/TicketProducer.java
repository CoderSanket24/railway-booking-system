package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;

/**
 * MEMBER 5: Producer Thread
 * Generates tickets and adds them to bounded buffer
 */
@Component
public class TicketProducer extends Thread {
    
    private TicketBuffer buffer;
    private int ticketCounter = 1;
    private String trainNumber;
    private int totalTickets;
    
    public TicketProducer() {
        // Default constructor for Spring
    }
    
    public void initialize(TicketBuffer buffer, String trainNumber, int totalTickets) {
        this.buffer = buffer;
        this.trainNumber = trainNumber;
        this.totalTickets = totalTickets;
    }
    
    @Override
    public void run() {
        System.out.println("[PRODUCER STARTED] Generating " + totalTickets + " tickets for train " + trainNumber);
        
        for (int i = 0; i < totalTickets; i++) {
            try {
                TicketBuffer.Ticket ticket = new TicketBuffer.Ticket(ticketCounter++, trainNumber, i + 1);
                buffer.produce(ticket);
                
                // Simulate ticket generation time
                Thread.sleep(500);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("[PRODUCER] Interrupted");
                break;
            }
        }
        
        System.out.println("[PRODUCER FINISHED] All tickets generated");
    }
}
