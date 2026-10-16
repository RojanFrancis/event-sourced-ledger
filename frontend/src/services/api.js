import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

const api = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' }
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 403 || error.response?.status === 401) {
      localStorage.clear()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export const register = (data) => api.post('/api/auth/register', data)
export const login = (data) => api.post('/api/auth/login', data)
export const getAccounts = () => api.get('/api/accounts')
export const getAccount = (id) => api.get(`/api/accounts/${id}`)
export const createAccount = (data) => api.post('/api/accounts', data)
export const getBalance = (id) => api.get(`/api/accounts/${id}/balance`)
export const getBalanceAsOf = (id, asOf) => api.get(`/api/accounts/${id}/balance?asOf=${asOf}`)
export const getEvents = (id) => api.get(`/api/accounts/${id}/events`)
export const deposit = (data) => api.post('/api/transactions/deposit', data)
export const withdraw = (data) => api.post('/api/transactions/withdraw', data)
export const transfer = (data) => api.post('/api/transactions/transfer', data)

// ── AI Finance Controller ─────────────────────────────────────────────────────
export const runReconciliation = (data) => api.post('/api/ai/reconcile', data).then(r => r.data)
export const askSettlementAgent = (accountId, question) => api.post('/api/ai/query', { accountId, question }).then(r => r.data)

export default api