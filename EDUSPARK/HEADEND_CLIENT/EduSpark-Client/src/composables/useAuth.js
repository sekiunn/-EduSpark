import { ref } from 'vue'
import { getUserInfo, login as loginApi, register as registerApi, sendSmsCode } from '@/api/user.js'
import {
  isLoggedIn,
  logout,
  onAuthRequired,
  saveAuth,
  token,
  triggerAuthRequired,
  USER_KEY,
  userInfo
} from './authState.js'

const isLoading = ref(false)

async function login(data) {
  isLoading.value = true
  try {
    const res = await loginApi(data)
    saveAuth(res.data.accessToken, res.data.user)
    return res
  } finally {
    isLoading.value = false
  }
}

async function register(data) {
  isLoading.value = true
  try {
    const res = await registerApi(data)
    saveAuth(res.data.accessToken, res.data.user)
    return res
  } finally {
    isLoading.value = false
  }
}

async function sendCode(phone, scene) {
  return sendSmsCode(phone, scene)
}

async function fetchUserInfo() {
  try {
    const res = await getUserInfo()
    if (res.data) {
      userInfo.value = res.data
      localStorage.setItem(USER_KEY, JSON.stringify(res.data))
    }
    return res
  } catch (error) {
    if (error.message && (error.message.includes('401') || error.message.includes('请先登录'))) {
      logout()
    }
    throw error
  }
}

function updateLocalUser(partial) {
  if (userInfo.value) {
    userInfo.value = { ...userInfo.value, ...partial }
    localStorage.setItem(USER_KEY, JSON.stringify(userInfo.value))
  }
}

export function useAuth() {
  return {
    token,
    userInfo,
    isLoggedIn,
    isLoading,
    login,
    register,
    sendCode,
    fetchUserInfo,
    logout,
    updateLocalUser,
    onAuthRequired,
    triggerAuthRequired
  }
}
