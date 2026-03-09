package com.vit.railway_os.oscore;

/**
 * OS CONCEPT: 5-State Process Model
 * NEW → READY → RUNNING → WAITING → TERMINATED
 */
public enum ProcessState {
    NEW,        // Process is being created
    READY,      // Process is waiting in queue for CPU
    RUNNING,    // Process is currently executing
    WAITING,    // Process is waiting for I/O or resource
    TERMINATED  // Process has finished execution
}
