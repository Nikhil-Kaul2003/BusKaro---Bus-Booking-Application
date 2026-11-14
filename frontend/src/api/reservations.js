import api from './client';

export const reservationsApi = {
    create: async (data) => {
        const response = await api.post('/reservations', data);
        return response.data;
    },
    // Spring Boot quote endpoint calculates eligibility and final amount without creating a booking.
    quote: async (data) => {
        const response = await api.post('/reservations/quote', data);
        return response.data;
    },
    getCoupons: async () => {
        const response = await api.get('/coupons');
        return response.data;
    },
    getMyCoupons: async () => {
        const response = await api.get('/coupons/my');
        return response.data;
    },
    getMyReservations: async () => {
        const response = await api.get('/reservations', { timeout: 60000 });
        return response.data;
    },
    getById: async (id) => {
        const response = await api.get(`/reservations/${id}`);
        return response.data;
    },
    cancel: async (id) => {
        await api.delete(`/reservations/${id}`);
    },
    pay: async (id) => {
        await api.post(`/reservations/${id}/pay`);
    },
};

export default reservationsApi;
