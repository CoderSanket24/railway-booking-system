// API Configuration — uses the same hostname as the browser so LAN/IP access works
// const API_BASE_URL = `http://${window.location.hostname}:8080`;
const API_BASE_URL = `https://railway-booking-system-otpp.onrender.com`;

export const API_ENDPOINTS = {
    // Auth
    LOGIN:    `${API_BASE_URL}/api/auth/login`,
    REGISTER: `${API_BASE_URL}/api/auth/register`,

    // Booking
    BOOK:               `${API_BASE_URL}/api/book`,
    TRAINS:             (userId?: number) => `${API_BASE_URL}/api/trains${userId ? `?userId=${userId}` : ''}`,
    USER_BOOKINGS:      (userId: number) => `${API_BASE_URL}/api/bookings/${userId}`,
    STATS:              `${API_BASE_URL}/api/stats`,
    CHECK_AVAILABILITY: `${API_BASE_URL}/api/check`,
    PRODUCER_CONSUMER:  `${API_BASE_URL}/api/producer-consumer`,

    // Admin – Units I-III
    ADMIN_MONITOR:              `${API_BASE_URL}/api/admin/monitor`,
    ADMIN_SCHEDULER:            `${API_BASE_URL}/api/admin/scheduler`,
    ADMIN_SCHEDULER_AUTO:       `${API_BASE_URL}/api/admin/scheduler/auto`,
    START_DINING_PHILOSOPHERS:  `${API_BASE_URL}/api/admin/start-dining-philosophers`, // kept for backward compat
    ADMIN_PHILOSOPHERS:         `${API_BASE_URL}/api/admin/philosophers`,
    LIVE_EVENTS:                `${API_BASE_URL}/api/admin/live-events`,
    LIVE_EVENTS_CLEAR:          `${API_BASE_URL}/api/admin/live-events/clear`,

    // Admin – Unit IV: Deadlocks
    BANKERS:                `${API_BASE_URL}/api/admin/bankers`,
    BANKERS_SAFETY:         `${API_BASE_URL}/api/admin/bankers/safety`,
    BANKERS_REQUEST:        `${API_BASE_URL}/api/admin/bankers/request`,
    DEADLOCK:               `${API_BASE_URL}/api/admin/deadlock`,
    DEADLOCK_SIMULATE:      `${API_BASE_URL}/api/admin/deadlock/simulate`,
    DEADLOCK_DETECT:        `${API_BASE_URL}/api/admin/deadlock/detect`,
    DEADLOCK_RECOVERY:      `${API_BASE_URL}/api/admin/deadlock/recovery`,
    DEADLOCK_RECOVER:       `${API_BASE_URL}/api/admin/deadlock/recover`,
    DEADLOCK_RECOVERY_RESET:`${API_BASE_URL}/api/admin/deadlock/recovery/reset`,

    // Admin – Unit V: Memory Management
    MEMORY:                 `${API_BASE_URL}/api/admin/memory`,
    MEMORY_ALLOC_FIXED:     `${API_BASE_URL}/api/admin/memory/allocate-fixed`,
    MEMORY_ALLOC_DYNAMIC:   `${API_BASE_URL}/api/admin/memory/allocate-dynamic`,
    MEMORY_RESET:           `${API_BASE_URL}/api/admin/memory/reset`,
    BUDDY:                  `${API_BASE_URL}/api/admin/buddy`,
    BUDDY_ALLOCATE:         `${API_BASE_URL}/api/admin/buddy/allocate`,
    BUDDY_DEALLOCATE:       `${API_BASE_URL}/api/admin/buddy/deallocate`,
    PAGE_REPLACEMENT:       `${API_BASE_URL}/api/admin/page-replacement`,
    TLB:                    `${API_BASE_URL}/api/admin/tlb`,
    TLB_DEMO:               `${API_BASE_URL}/api/admin/tlb/demo`,
    TLB_TRANSLATE:          `${API_BASE_URL}/api/admin/tlb/translate`,
    TLB_FLUSH:              `${API_BASE_URL}/api/admin/tlb/flush`,

    // Admin – Unit VI: I/O & File Management
    DISK_SCHEDULING:        `${API_BASE_URL}/api/admin/disk-scheduling`,
    IO_BUFFER:              `${API_BASE_URL}/api/admin/io-buffer`,
    IO_BUFFER_DEMO:         `${API_BASE_URL}/api/admin/io-buffer/demo`,
    FILE_SYSTEM:            `${API_BASE_URL}/api/admin/file-system`,
    FILE_SYSTEM_CREATE:     `${API_BASE_URL}/api/admin/file-system/create`,
    FILE_SYSTEM_READ:       `${API_BASE_URL}/api/admin/file-system/read`,
    FILE_SYSTEM_WRITE:      `${API_BASE_URL}/api/admin/file-system/write`,
    FILE_SYSTEM_SHARE:      `${API_BASE_URL}/api/admin/file-system/share`,
};

export default API_BASE_URL;
