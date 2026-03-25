const TOKEN_KEY = 'translate_novel_token'
const REFRESH_TOKEN_KEY = 'translate_novel_refresh_token'
const EXPIRES_AT_KEY = 'translate_novel_expires_at'
const REFRESH_EXPIRES_AT_KEY = 'translate_novel_refresh_expires_at'
const USER_KEY = 'translate_novel_user'
export const SESSION_EVENT = 'translate-novel-session-changed'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY) || ''
}

export function getSessionExpiresAt() {
  return Number(localStorage.getItem(EXPIRES_AT_KEY) || 0)
}

export function getRefreshExpiresAt() {
  return Number(localStorage.getItem(REFRESH_EXPIRES_AT_KEY) || 0)
}

export function getStoredUser() {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function readSession() {
  return {
    token: getToken(),
    refreshToken: getRefreshToken(),
    expiresAt: getSessionExpiresAt(),
    refreshExpiresAt: getRefreshExpiresAt(),
    user: getStoredUser(),
  }
}

export function hasUsableSession() {
  return hasValidAccessToken() || canRefreshSession()
}

export function hasValidAccessToken(bufferMs = 0) {
  const expiresAt = getSessionExpiresAt()
  return Boolean(getToken()) && expiresAt > Date.now() + bufferMs
}

export function canRefreshSession(bufferMs = 0) {
  const refreshExpiresAt = getRefreshExpiresAt()
  return Boolean(getRefreshToken()) && (!refreshExpiresAt || refreshExpiresAt > Date.now() + bufferMs)
}

export function setSession({ token, refreshToken, expiresAt, refreshExpiresAt, user }) {
  writeValue(TOKEN_KEY, token)
  writeValue(REFRESH_TOKEN_KEY, refreshToken)
  writeValue(EXPIRES_AT_KEY, expiresAt)
  writeValue(REFRESH_EXPIRES_AT_KEY, refreshExpiresAt)
  writeValue(USER_KEY, user ? JSON.stringify(user) : '')
  dispatchSessionChange()
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(EXPIRES_AT_KEY)
  localStorage.removeItem(REFRESH_EXPIRES_AT_KEY)
  localStorage.removeItem(USER_KEY)
  dispatchSessionChange()
}

function writeValue(key, value) {
  if (value === undefined || value === null || value === '') {
    localStorage.removeItem(key)
    return
  }
  localStorage.setItem(key, String(value))
}

function dispatchSessionChange() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(SESSION_EVENT))
  }
}
