import axios from 'axios'
import {
  clearSession,
  getRefreshExpiresAt,
  getRefreshToken,
  getStoredUser,
  setSession,
} from '@/utils/session'

const authSessionHttp = axios.create({
  baseURL: '/api',
})

let refreshPromise = null

function applySession(payload) {
  setSession({
    token: payload.token,
    refreshToken: payload.refreshToken,
    expiresAt: payload.expiresAt,
    refreshExpiresAt: payload.refreshExpiresAt,
    user: payload.user || getStoredUser(),
  })
  return payload
}

export async function refreshSession() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    return null
  }

  const refreshExpiresAt = getRefreshExpiresAt()
  if (refreshExpiresAt && refreshExpiresAt <= Date.now()) {
    clearSession()
    throw new Error('登录已过期，请重新登录')
  }

  if (!refreshPromise) {
    refreshPromise = authSessionHttp
      .post('/auth/refresh', { refreshToken })
      .then((response) => applySession(response.data.data))
      .catch((error) => {
        const status = error.response?.status
        const message = error.response?.data?.message || error.message || '登录续期失败'
        if (status === 400 || status === 401) {
          clearSession()
        }
        throw new Error(message)
      })
      .finally(() => {
        refreshPromise = null
      })
  }

  return refreshPromise
}

export function persistAuthSession(payload) {
  return applySession(payload)
}
