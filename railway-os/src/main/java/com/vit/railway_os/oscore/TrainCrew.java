package com.vit.railway_os.oscore;

import java.util.concurrent.Semaphore;

public class TrainCrew extends Thread {
    private final int crewId;
    private final Semaphore firstResource;
    private final Semaphore secondResource;
    private final OsStateTracker tracker;

    public TrainCrew(int crewId, Semaphore firstResource, Semaphore secondResource, OsStateTracker tracker) {
        this.crewId = crewId;
        this.firstResource = firstResource;
        this.secondResource = secondResource;
        this.tracker = tracker;
        updateState("THINKING (Resting)");
    }

    private void updateState(String state) {
        if (tracker != null) {
            tracker.updatePhilosopherState(crewId, state);
        }
        System.out.println("[CREW " + crewId + "] is " + state);
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // THINKING
                updateState("THINKING (Resting)");
                Thread.sleep((long) (Math.random() * 4000) + 1000); // 1-5 secs

                // HUNGRY
                updateState("HUNGRY (Waiting for resources)");
                
                // Acquire first resource
                firstResource.acquire();
                
                // Acquire second resource
                secondResource.acquire();

                // EATING (Preparing Train)
                updateState("EATING (Preparing Train)");
                Thread.sleep((long) (Math.random() * 3000) + 2000); // 2-5 secs

                // Release resources
                secondResource.release();
                firstResource.release();
            }
        } catch (InterruptedException e) {
            // Thread interrupted, exit gracefully
            updateState("FINISHED (Simulation ended)");
        }
    }
}
