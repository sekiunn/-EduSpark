import { computed, ref } from 'vue'

export const TOKEN_KEY = 'eduspark_token'
export const USER_KEY = 'eduspark_user'
export const AUTH_REQUIRED_EVENT = 'auth-required'

function parseStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

export const token = ref(localStorage.getItem(TOKEN_KEY) || null)
export const userInfo = ref(parseStoredUser())
export const isLoggedIn = computed(() => !!token.value)

const authModalCallbacks = []

export function saveAuth(newToken, newUser) {
  token.value = newToken
  userInfo.value = newUser

  if (newToken) {
    localStorage.setItem(TOKEN_KEY, newToken)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }

  if (newUser) {
    localStorage.setItem(USER_KEY, JSON.stringify(newUser))
  } else {
    localStorage.removeItem(USER_KEY)
  }
}

export function logout() {
  saveAuth(null, null)
}

export function onAuthRequired(callback) {
  authModalCallbacks.push(callback)

  return () => {
    const index = authModalCallbacks.indexOf(callback)
    if (index !== -1) {
      authModalCallbacks.splice(index, 1)
    }
  }
}

export function triggerAuthRequired() {
  saveAuth(null, null)
  authModalCallbacks.slice().forEach(callback => callback())
  window.dispatchEvent(new CustomEvent(AUTH_REQUIRED_EVENT))
}
