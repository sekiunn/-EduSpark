import { reactive } from 'vue'

const TOKEN_KEY = 'eduspark_admin_token'
const USER_KEY = 'eduspark_admin_user'

function readStorage(key, fallback = '') {
  if (typeof window === 'undefined') {
    return fallback
  }

  const value = window.localStorage.getItem(key)
  return value ?? fallback
}

function readUser() {
  const raw = readStorage(USER_KEY, '')
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export const authState = reactive({
  token: readStorage(TOKEN_KEY, ''),
  user: readUser()
})

export function getAccessToken() {
  return authState.token
}

export function saveAdminAuth(token, user) {
  authState.token = token || ''
  authState.user = user || null

  if (typeof window === 'undefined') {
    return
  }

  if (token) {
    window.localStorage.setItem(TOKEN_KEY, token)
  } else {
    window.localStorage.removeItem(TOKEN_KEY)
  }

  if (user) {
    window.localStorage.setItem(USER_KEY, JSON.stringify(user))
  } else {
    window.localStorage.removeItem(USER_KEY)
  }
}

export function updateAdminUser(user) {
  saveAdminAuth(authState.token, user)
}

export function clearAdminAuth() {
  saveAdminAuth('', null)
}

export function isAdminRole(role) {
  return role === 'admin'
}

export function getRoleLabel(role) {
  if (role === 'admin') return '管理员'
  if (role === 'teacher') return '教师'
  if (role === 'student') return '学生'
  return '未标记角色'
}
