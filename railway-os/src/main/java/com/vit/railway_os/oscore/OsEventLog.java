package com.vit.railway_os.oscore;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Shared real-time OS event log.
 * Every OS mechanism (scheduler, mutex, memory, file I/O, TLB) pushes here.
 * Frontend polls /api/admin/live-events to display them.
 */
@Component
public class OsEventLog {

    public static final String TYPE_SCHEDULER  = "SCHEDULER";
    public static final String TYPE_MUTEX      = "MUTEX";
    public static final String TYPE_MEMORY     = "MEMORY";
    public static final String TYPE_FILE_IO    = "FILE_IO";
    public static final String TYPE_TLB        = "TLB";
    public static final String TYPE_BANKER     = "BANKER";
    public static final String TYPE_BOOKING    = "BOOKING";
    public static final String TYPE_READER     = "READER";

    public static class OsEvent {
        public long   timestamp;
        public String type;
        public String unit;     // "Unit-I", "Unit-II" … "Unit-VI"
        public String detail;
        public int    pid;
        public String icon;
        public boolean success;

        public OsEvent(String type, String unit, String detail, int pid, String icon, boolean success) {
            this.timestamp = Instant.now().toEpochMilli();
            this.type    = type;
            this.unit    = unit;
            this.detail  = detail;
            this.pid     = pid;
            this.icon    = icon;
            this.success = success;
        }
    }

    // Keep last 120 events (ring buffer via deque)
    private final ConcurrentLinkedDeque<OsEvent> events = new ConcurrentLinkedDeque<>();
    private static final int MAX_EVENTS = 120;

    public void push(String type, String unit, String detail, int pid, String icon, boolean success) {
        events.addFirst(new OsEvent(type, unit, detail, pid, icon, success));
        while (events.size() > MAX_EVENTS) events.pollLast();
    }

    /** Convenience overloads */
    public void pushScheduler(String detail, int pid) {
        push(TYPE_SCHEDULER, "Unit-III", detail, pid, "⚙️", true);
    }
    public void pushMutex(String detail, int pid, boolean locked) {
        push(TYPE_MUTEX, "Unit-II", detail, pid, locked ? "🔒" : "🔓", !locked);
    }
    public void pushMemory(String detail, int pid) {
        push(TYPE_MEMORY, "Unit-V", detail, pid, "🧠", true);
    }
    public void pushFileIO(String detail, int pid, boolean ok) {
        push(TYPE_FILE_IO, "Unit-VI", detail, pid, "💿", ok);
    }
    public void pushTLB(String detail, int pid, boolean hit) {
        push(TYPE_TLB, "Unit-V", detail, pid, hit ? "⚡" : "❌", hit);
    }
    public void pushBanker(String detail, int pid, boolean safe) {
        push(TYPE_BANKER, "Unit-IV", detail, pid, safe ? "✅" : "⚠️", safe);
    }
    public void pushBooking(String detail, int pid, boolean ok) {
        push(TYPE_BOOKING, "App", detail, pid, ok ? "🎫" : "🚫", ok);
    }
    public void pushReader(String detail, int pid) {
        push(TYPE_READER, "Unit-II", detail, pid, "👓", true);
    }

    public List<OsEvent> getRecent(int limit) {
        List<OsEvent> list = new ArrayList<>(events);
        return list.subList(0, Math.min(limit, list.size()));
    }

    public List<OsEvent> getAll() {
        return new ArrayList<>(events);
    }

    public void clear() { events.clear(); }
}
