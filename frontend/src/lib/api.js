import api from './axios'

export function getErrorMessage(err, fallback = 'An unexpected error occurred') {
  return err.response?.data?.message ?? fallback
}

// ─── Auth ────────────────────────────────────────────────────────────────────
export const authApi = {
  login: (data) => api.post('/auth/login', data),
  register: (data) => api.post('/auth/register', data),
  refresh: (refreshToken) => api.post('/auth/refresh', { refreshToken }),
  me: () => api.get('/auth/me'),
}

// ─── Accounts ────────────────────────────────────────────────────────────────
export const accountApi = {
  list: () => api.get('/accounts'),
  getById: (id) => api.get(`/accounts/${id}`),
  getByNumber: (number) => api.get(`/accounts/number/${number}`),
  create: (data) => api.post('/accounts', data),
  close: (id) => api.delete(`/accounts/${id}/close`),
}

// ─── Transactions ─────────────────────────────────────────────────────────────
export const transactionApi = {
  list: (params) => api.get('/transactions', { params }),
  getByAccount: (accountNumber, params) =>
    api.get(`/transactions/account/${accountNumber}`, { params }),
  getByReference: (ref) => api.get(`/transactions/reference/${ref}`),
  deposit: (data) => api.post('/transactions/deposit', data),
  withdraw: (data) => api.post('/transactions/withdraw', data),
  transfer: (data) => api.post('/transactions/transfer', data),
}

// ─── Notifications ────────────────────────────────────────────────────────────
export const notificationApi = {
  list: (params) => api.get('/notifications', { params }),
  unread: () => api.get('/notifications/unread'),
  unreadCount: () => api.get('/notifications/unread/count'),
  markRead: (id) => api.patch(`/notifications/${id}/read`),
  markAllRead: () => api.patch('/notifications/read-all'),
}
