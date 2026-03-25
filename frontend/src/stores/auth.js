import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getProfile, login, register } from '@/api/auth'
import { persistAuthSession, refreshSession } from '@/api/authSession'
import {
  SESSION_EVENT,
  clearSession,
  getRefreshExpiresAt,
  getRefreshToken,
  getSessionExpiresAt,
  readSession,
  setSession,
} from '@/utils/session'

let listenersBound = false

export const useAuthStore = defineStore('auth', () => {
  const session = readSession()
  const token = ref(session.token)
  const refreshToken = ref(session.refreshToken)
  const expiresAt = ref(session.expiresAt)
  const refreshExpiresAt = ref(session.refreshExpiresAt)
  const user = ref(session.user)

  const isAuthenticated = computed(() => {
    const now = Date.now()
    return Boolean(token.value && expiresAt.value > now)
      || Boolean(refreshToken.value && (!refreshExpiresAt.value || refreshExpiresAt.value > now))
  })

  function syncFromStorage() {
    const nextSession = readSession()
    token.value = nextSession.token
    refreshToken.value = nextSession.refreshToken
    expiresAt.value = nextSession.expiresAt
    refreshExpiresAt.value = nextSession.refreshExpiresAt
    user.value = nextSession.user
  }

  function persist(payload) {
    persistAuthSession(payload)
    syncFromStorage()
  }

  async function loginWithCredentials(payload) {
    const { data } = await login(payload)
    persist(data)
    return data.user
  }

  async function registerWithCredentials(payload) {
    const { data } = await register(payload)
    persist(data)
    return data.user
  }

  async function refreshSessionData() {
    const data = await refreshSession()
    syncFromStorage()
    return data?.user || user.value
  }

  async function refreshProfile() {
    const { data } = await getProfile()
    user.value = data
    setSession({
      token: token.value,
      refreshToken: getRefreshToken(),
      expiresAt: getSessionExpiresAt(),
      refreshExpiresAt: getRefreshExpiresAt(),
      user: data,
    })
    return data
  }

  function logout() {
    clearSession()
    syncFromStorage()
  }

  if (typeof window !== 'undefined' && !listenersBound) {
    window.addEventListener(SESSION_EVENT, syncFromStorage)
    window.addEventListener('storage', syncFromStorage)
    listenersBound = true
  }

  return {
    token,
    refreshToken,
    expiresAt,
    refreshExpiresAt,
    user,
    isAuthenticated,
    loginWithCredentials,
    registerWithCredentials,
    refreshSessionData,
    refreshProfile,
    logout,
  }
})
