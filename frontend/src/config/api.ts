// API Configuration — uses the same hostname as the browser so LAN/IP access works
const API_BASE_URL = `http://${window.location.hostname}:8080`;

export const API_ENDPOINTS = {
    LOGIN: `${API_BASE_URL}/api/auth/login`,
    REGISTER: `${API_BASE_URL}/api/auth/register`,
    BOOK: `${API_BASE_URL}/api/book`,
    TRAINS: (userId?: number) => `${API_BASE_URL}/api/trains${userId ? `?userId=${userId}` : ''}`,
    USER_BOOKINGS: (userId: number) => `${API_BASE_URL}/api/bookings/${userId}`,
    STATS: `${API_BASE_URL}/api/stats`,
    ADMIN_MONITOR: `${API_BASE_URL}/api/admin/monitor`,
    ADMIN_SCHEDULER: `${API_BASE_URL}/api/admin/scheduler`,
    CHECK_AVAILABILITY: `${API_BASE_URL}/api/check`,
    PRODUCER_CONSUMER: `${API_BASE_URL}/api/producer-consumer`,
    START_DINING_PHILOSOPHERS: `${API_BASE_URL}/api/admin/start-dining-philosophers`,
};

export default API_BASE_URL;
