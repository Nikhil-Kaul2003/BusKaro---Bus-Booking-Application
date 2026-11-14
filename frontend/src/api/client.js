import axios from 'axios';
// Use the same-origin /api path by default. Vite proxies it to Spring Boot in
// development, which avoids browser CORS and lets the target port be configured
// in one place. Set VITE_API_BASE_URL only when the frontend is hosted separately.
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
const API_PROXY_TARGET = import.meta.env.VITE_API_PROXY_TARGET || 'http://localhost:8082';
const backendLocation = import.meta.env.VITE_API_BASE_URL
    ? 'the configured API server'
    : `Spring Boot on port ${new URL(API_PROXY_TARGET).port || '8081'}`;
export const api = axios.create({
    baseURL: API_BASE_URL,
    timeout: 15000,
    headers: {
        'Content-Type': 'application/json',
    },
});
export const getApiErrorMessage = (error, fallback) => {
    if (axios.isAxiosError(error)) {
        if (error.code === 'ECONNABORTED') {
            return `The search service took too long to respond. Check that ${backendLocation} is running, then try again.`;
        }
        if (!error.response) {
            return `Cannot reach the backend. Check that ${backendLocation} is running, then try again.`;
        }
        if (error.response.status === 401) {
            const url = error.config?.url || '';
            return url.includes('/auth/login')
                ? 'Invalid email or password.'
                : 'Your session has expired. Please sign in again.';
        }
        const responseMessage = error.response.data?.message;
        if (responseMessage)
            return responseMessage;
    }
    return error instanceof Error && error.message ? error.message : fallback;
};
// Request interceptor to add JWT token
api.interceptors.request.use((config) => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
}, (error) => {
    return Promise.reject(error);
});
// Response interceptor for handling errors
api.interceptors.response.use((response) => response, (error) => {
    if (error.response?.status === 401) {
        // Don't clear auth on login attempts — 401 there means wrong password
        const url = error.config?.url || '';
        if (!url.includes('/auth/login')) {
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            window.dispatchEvent(new Event('unauthorized-logout'));
        }
    }
    return Promise.reject(error);
});
export default api;