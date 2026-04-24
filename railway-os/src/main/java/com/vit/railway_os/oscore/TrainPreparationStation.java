package com.vit.railway_os.oscore;

import java.util.concurrent.Semaphore;

/**
 * OS CONCEPT: Dining Philosophers Problem
 * Train crews (Philosophers) need exclusive access to two adjacent preparation tools 
 * (Chopsticks/Forks) to prepare a train.
 */
public class TrainPreparationStation {
    private final int NUM_CREWS = 5;
    private final Semaphore[] resources = new Semaphore[NUM_CREWS];
    private final TrainCrew[] crews = new TrainCrew[NUM_CREWS];
    private final OsStateTracker tracker;

    public TrainPreparationStation(OsStateTracker tracker) {
        this.tracker = tracker;
        for (int i = 0; i < NUM_CREWS; i++) {
            resources[i] = new Semaphore(1); // Binary semaphore for each resource
        }
    }

    public void startSimulation() {
        System.out.println("[DINING PHILOSOPHERS] Simulation started with 5 train crews.");
        
        for (int i = 0; i < NUM_CREWS; i++) {
            // To prevent deadlock, the last crew picks up the right resource first, then the left.
            // standard crew: picks left (i), then right ((i+1)%5).
            // last crew: picks right ((i+1)%5), then left (i).
            Semaphore firstResource;
            Semaphore secondResource;
            
            if (i == NUM_CREWS - 1) {
                firstResource = resources[(i + 1) % NUM_CREWS];
                secondResource = resources[i];
            } else {
                firstResource = resources[i];
                secondResource = resources[(i + 1) % NUM_CREWS];
            }
            
            crews[i] = new TrainCrew(i, firstResource, secondResource, tracker);
            crews[i].start();
        }
    }

    public void stopSimulation() {
        for (int i = 0; i < NUM_CREWS; i++) {
            if (crews[i] != null) {
                crews[i].interrupt();
            }
        }
        System.out.println("[DINING PHILOSOPHERS] Simulation stopped.");
    }
}
