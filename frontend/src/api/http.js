import axios from 'axios'
import { clearSession, canRefreshSession, getToken, hasValidAccessToken } from '@/utils/session'
import { refreshSession } from './authSession'

const http = axios.create({
  baseURL: '/api',
})

const REFRESH_BUFFER_MS = 60 * 1000

http.interceptors.request.use(async (config) => {
  if (!config.skipAuthRefresh && !hasValidAccessToken(REFRESH_BUFFER_MS) && canRefreshSession()) {
    await refreshSession()
  }

  const token = getToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const originalRequest = error.config || {}
    if (error.response?.status === 401 && !originalRequest._retry && !originalRequest.skipAuthRefresh && canRefreshSession()) {
      originalRequest._retry = true

      try {
        await refreshSession()
        originalRequest.headers = originalRequest.headers || {}
        originalRequest.headers.Authorization = `Bearer ${getToken()}`
        return http(originalRequest)
      } catch (refreshError) {
        clearSession()
        return Promise.reject(refreshError)
      }
    }

    if (error.response?.status === 401) {
      clearSession()
    }

    const message = error.response?.data?.message || error.message || '请求失败'
    return Promise.reject(new Error(message))
  },
)

export default http
