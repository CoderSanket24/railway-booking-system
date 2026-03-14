// API Configuration
// Change this IP to your computer's IP address
const API_BASE_URL = 'https://railway-booking-system-otpp.onrender.com';

export const API_ENDPOINTS = {
    LOGIN: `${API_BASE_URL}/api/auth/login`,
    REGISTER: `${API_BASE_URL}/api/auth/register`,
    BOOK: `${API_BASE_URL}/api/book`,
    STATS: `${API_BASE_URL}/api/stats`,
    ADMIN_MONITOR: `${API_BASE_URL}/api/admin/monitor`,
    ADMIN_SCHEDULER: `${API_BASE_URL}/api/admin/scheduler`,
    CHECK_AVAILABILITY: `${API_BASE_URL}/api/check`,
    PRODUCER_CONSUMER: `${API_BASE_URL}/api/producer-consumer`,
};

export default API_BASE_URL;
