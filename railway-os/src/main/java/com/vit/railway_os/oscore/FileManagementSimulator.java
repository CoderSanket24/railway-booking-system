package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * OS CONCEPT: File Management — Organization, Directories, Sharing (Unit VI)
 *
 * RAILWAY CONTEXT:
 *   Files = booking record files stored per train per date.
 *   e.g., "12951_2024-06-15.bkr" = all bookings for Rajdhani on June 15
 *
 * FILE ORGANIZATION TYPES:
 *
 *   1. SEQUENTIAL (Contiguous):
 *      Records stored one after another in a contiguous block.
 *      Access: O(N) linear scan. Fast sequential read; slow random access.
 *      Like a queue of booking records written in order.
 *
 *   2. INDEXED:
 *      An index block holds pointers to data blocks.
 *      Direct access to any record by looking up the index: O(1).
 *      Like a B-tree index in MySQL.
 *
 *   3. LINKED (Chained):
 *      Each block has a pointer to the next block.
 *      Access: O(N) — must traverse chain. Good for sequential access;
 *      each block can be anywhere on disk.
 *
 * FILE DIRECTORIES:
 *   Single-level directory (all files in one namespace).
 *   Operations: CREATE, DELETE, SEARCH, LIST.
 *
 * FILE SHARING:
 *   Multiple users can share a booking file.
 *   READ-ONLY access: any number of simultaneous readers (like Readers-Writers).
 *   READ-WRITE access: exclusive lock (writer has sole access).
 */
@Component
public class FileManagementSimulator {

    // ── File Directory ────────────────────────────────────────────────────────

    private final Map<String, FileEntry> directory = new LinkedHashMap<>();
    private final List<String> log = new ArrayList<>();

    public FileManagementSimulator() {
        // Pre-create some demo booking files
        createFile("12951_2024-06-15.bkr", "INDEXED", "admin");
        createFile("15001_2024-06-15.bkr", "SEQUENTIAL", "admin");
        createFile("16032_2024-06-16.bkr", "LINKED", "admin");
        log.add("[FILE-SYS] System initialised with 3 booking record files.");
    }

    // ── Directory Operations ──────────────────────────────────────────────────

    /** CREATE: register a new file in the directory */
    public synchronized boolean createFile(String name, String organization, String owner) {
        if (directory.containsKey(name)) {
            log.add("[DIR] ❌ CREATE FAILED: file \"" + name + "\" already exists.");
            return false;
        }
        FileEntry entry = new FileEntry(name, organization.toUpperCase(), owner);
        directory.put(name, entry);
        log.add("[DIR] ✅ CREATE: \"" + name + "\" | Org=" + organization + " | Owner=" + owner);
        return true;
    }

    /** DELETE: remove a file (only if no active writers) */
    public synchronized boolean deleteFile(String name) {
        FileEntry entry = directory.get(name);
        if (entry == null) {
            log.add("[DIR] ❌ DELETE FAILED: \"" + name + "\" not found."); return false;
        }
        if (entry.writers > 0) {
            log.add("[DIR] ❌ DELETE FAILED: \"" + name + "\" is currently open for writing."); return false;
        }
        directory.remove(name);
        log.add("[DIR] 🗑 DELETE: \"" + name + "\" removed.");
        return true;
    }

    /** SEARCH: find files matching a train number pattern */
    public synchronized List<String> searchFiles(String trainNumber) {
        List<String> found = directory.keySet().stream()
            .filter(name -> name.startsWith(trainNumber))
            .toList();
        log.add("[DIR] SEARCH \"" + trainNumber + "\" → " + found.size() + " file(s) found: " + found);
        return found;
    }

    // ── File Sharing (Access Control) ─────────────────────────────────────────

    /** Open file for reading (shared access) */
    public synchronized boolean openRead(String fileName, String user) {
        FileEntry f = directory.get(fileName);
        if (f == null) { log.add("[SHARE] ❌ File not found: " + fileName); return false; }
        if (f.writers > 0) {
            log.add("[SHARE] ⏳ \"" + user + "\" waiting — file locked by a writer."); return false;
        }
        f.readers++;
        f.sharedWith.add(user + "(R)");
        log.add("[SHARE] 📖 \"" + user + "\" opened \"" + fileName + "\" READ-ONLY | readers=" + f.readers);
        return true;
    }

    /** Open file for writing (exclusive access) */
    public synchronized boolean openWrite(String fileName, String user) {
        FileEntry f = directory.get(fileName);
        if (f == null) { log.add("[SHARE] ❌ File not found: " + fileName); return false; }
        if (f.readers > 0 || f.writers > 0) {
            log.add("[SHARE] ⏳ \"" + user + "\" waiting — file has " + f.readers + " readers / " + f.writers + " writers."); return false;
        }
        f.writers = 1;
        f.sharedWith.add(user + "(W)");
        f.owner = user;
        log.add("[SHARE] ✏ \"" + user + "\" opened \"" + fileName + "\" READ-WRITE (EXCLUSIVE)");
        return true;
    }

    /** Close file */
    public synchronized void closeFile(String fileName, String user, String mode) {
        FileEntry f = directory.get(fileName);
        if (f == null) return;
        if (mode.equalsIgnoreCase("READ") && f.readers > 0) {
            f.readers--;
            f.sharedWith.remove(user + "(R)");
            log.add("[SHARE] \"" + user + "\" closed \"" + fileName + "\" (READ). readers=" + f.readers);
        } else if (mode.equalsIgnoreCase("WRITE")) {
            f.writers = 0;
            f.sharedWith.remove(user + "(W)");
            log.add("[SHARE] \"" + user + "\" closed \"" + fileName + "\" (WRITE). File now free.");
        }
    }

    // ── File Organization: Record Access ──────────────────────────────────────

    /**
     * Simulate reading record `recordIndex` from a file.
     * Returns the access pattern and block reads required.
     */
    public synchronized AccessResult readRecord(String fileName, int recordIndex) {
        FileEntry f = directory.get(fileName);
        if (f == null) return new AccessResult(false, "FILE_NOT_FOUND", 0, List.of());

        f.accessCount++;
        List<String> steps = new ArrayList<>();
        int diskReads;

        switch (f.organization) {
            case "SEQUENTIAL" -> {
                // Must scan from beginning to reach record at recordIndex
                diskReads = recordIndex + 1;
                steps.add("SEQUENTIAL: scan blocks 0 → " + recordIndex + " (" + diskReads + " disk reads)");
                for (int i = 0; i <= recordIndex; i++) {
                    steps.add("  Read block-" + i + (i == recordIndex ? " ← TARGET" : ""));
                }
            }
            case "INDEXED" -> {
                // 1 read for index block + 1 read for data block = always 2 disk reads
                diskReads = 2;
                steps.add("INDEXED: 1. Read index block → pointer to data block-" + recordIndex);
                steps.add("         2. Read data block-" + recordIndex + " ← TARGET (only 2 disk reads!)");
            }
            case "LINKED" -> {
                // Must follow pointer chain from block 0 to block recordIndex
                diskReads = recordIndex + 1;
                steps.add("LINKED: Follow pointer chain:");
                for (int i = 0; i <= recordIndex; i++) {
                    steps.add("  Block-" + i + (i < recordIndex ? " → next:" + (i+1) : " → TARGET (no next)"));
                }
                steps.add("Total disk reads: " + diskReads);
            }
            default -> { diskReads = 1; steps.add("Unknown organization"); }
        }

        log.add("[FILE-IO] " + f.organization + " read record[" + recordIndex + "] from \""
                + fileName + "\" | " + diskReads + " disk reads");
        return new AccessResult(true, f.organization, diskReads, steps);
    }

    /** Write a new booking record to a file */
    public synchronized AccessResult writeRecord(String fileName, String record) {
        FileEntry f = directory.get(fileName);
        if (f == null) return new AccessResult(false, "FILE_NOT_FOUND", 0, List.of());

        f.records.add(record);
        f.sizeKB += 1; // each record = approx 1KB
        f.accessCount++;
        int diskWrites;
        List<String> steps = new ArrayList<>();

        switch (f.organization) {
            case "SEQUENTIAL" -> {
                diskWrites = 1;
                steps.add("SEQUENTIAL: Append record to end. 1 disk write.");
                steps.add("  Block-" + (f.records.size()-1) + " written: \"" + record + "\"");
            }
            case "INDEXED" -> {
                diskWrites = 2;
                steps.add("INDEXED: 1. Write data block-" + (f.records.size()-1) + ": \"" + record + "\"");
                steps.add("         2. Update index block with new pointer. (2 disk writes)");
            }
            case "LINKED" -> {
                diskWrites = 2;
                steps.add("LINKED: 1. Write new block-" + (f.records.size()-1) + ": \"" + record + "\"");
                steps.add("        2. Update previous block's pointer to this block. (2 disk writes)");
            }
            default -> { diskWrites = 1; steps.add("Unknown organization"); }
        }

        log.add("[FILE-IO] " + f.organization + " write to \"" + fileName + "\": \"" + record + "\" | " + diskWrites + " disk writes");
        return new AccessResult(true, f.organization, diskWrites, steps);
    }

    // ── State Snapshot ─────────────────────────────────────────────────────────

    public synchronized Map<String, Object> getState() {
        List<Map<String, Object>> files = new ArrayList<>();
        for (FileEntry f : directory.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name",         f.name);
            m.put("organization", f.organization);
            m.put("owner",        f.owner);
            m.put("sizeKB",       f.sizeKB);
            m.put("records",      f.records.size());
            m.put("readers",      f.readers);
            m.put("writers",      f.writers);
            m.put("sharedWith",   f.sharedWith);
            m.put("accessCount",  f.accessCount);
            files.add(m);
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("totalFiles", directory.size());
        state.put("files",      files);
        state.put("recentLog",  log.size() > 25 ? log.subList(log.size()-25, log.size()) : log);
        return state;
    }

    // ── Inner types ────────────────────────────────────────────────────────────

    private static class FileEntry {
        String name, organization, owner;
        int sizeKB = 0, readers = 0, writers = 0, accessCount = 0;
        List<String> records    = new ArrayList<>();
        List<String> sharedWith = new ArrayList<>();

        FileEntry(String name, String org, String owner) {
            this.name = name; this.organization = org; this.owner = owner;
        }
    }

    public record AccessResult(boolean success, String organization, int diskOps, List<String> steps) {}
}
