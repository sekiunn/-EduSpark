<script setup>
import { ref, reactive, watch } from 'vue'
import { useAuth } from '@/composables/useAuth.js'
import { forgotPassword } from '@/api/user.js'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  initialTab: {
    type: String,
    default: 'login'
  },
  initialLoginMode: {
    type: String,
    default: 'password'
  }
})

const emit = defineEmits(['update:modelValue', 'success'])

const { login, register, sendCode, isLoading } = useAuth()

const activeTab = ref(props.initialTab)
const loginMode = ref(props.initialLoginMode)

const countdown = ref(0)
let countdownTimer = null

const errorMsg = ref('')
let errorMsgTimer = null

const showError = (msg) => {
  errorMsg.value = msg
  if (errorMsgTimer) clearTimeout(errorMsgTimer)
  errorMsgTimer = setTimeout(() => {
    errorMsg.value = ''
  }, 3000)
}

watch(() => props.modelValue, (val) => {
  if (!val) {
    errorMsg.value = ''
    if (errorMsgTimer) clearTimeout(errorMsgTimer)
  }
})

// 登录表单
const loginForm = reactive({
  phone: '',
  password: '',
  smsCode: ''
})

// 注册表单
const registerForm = reactive({
  phone: '',
  smsCode: '',
  password: '',
  confirmPassword: ''
})

// 忘记密码表单
const forgotForm = reactive({
  phone: '',
  smsCode: '',
  newPassword: '',
  confirmPassword: ''
})
const showForgot = ref(false)

const close = () => {
  emit('update:modelValue', false)
  errorMsg.value = ''
  showForgot.value = false
  activeTab.value = 'login'
  loginMode.value = 'password'
}

const switchTab = (tab) => {
  activeTab.value = tab
  errorMsg.value = ''
  showForgot.value = false
}

const switchLoginMode = (mode) => {
  loginMode.value = mode
  errorMsg.value = ''
}

// 发送验证码
const handleSendCode = async () => {
  const phone = activeTab.value === 'login'
    ? loginForm.phone
    : activeTab.value === 'register'
      ? registerForm.phone
      : forgotForm.phone

  if (!phone || !/^1[3-9]\d{9}$/.test(phone)) {
    showError('请输入正确的手机号')
    return
  }

  const scene = activeTab.value === 'login'
    ? 'login'
    : activeTab.value === 'register'
      ? 'register'
      : 'forgotPassword'

  try {
    await sendCode(phone, scene)
    countdown.value = 60
    countdownTimer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
    }, 1000)
  } catch (e) {
    showError(e.message || '发送验证码失败')
  }
}

// 登录
const handleLogin = async () => {
  errorMsg.value = ''
  if (!loginForm.phone || !/^1[3-9]\d{9}$/.test(loginForm.phone)) {
    showError('请输入正确的手机号')
    return
  }
  if (loginMode.value === 'password') {
    if (!loginForm.password) {
      showError('请输入密码')
      return
    }
  } else {
    if (!loginForm.smsCode || loginForm.smsCode.length !== 6) {
      showError('请输入6位验证码')
      return
    }
  }

  try {
    await login({
      loginType: loginMode.value,
      phone: loginForm.phone,
      password: loginMode.value === 'password' ? loginForm.password : undefined,
      smsCode: loginMode.value === 'sms' ? loginForm.smsCode : undefined
    })
    emit('success')
    close()
  } catch (e) {
    showError(e.message || '登录失败')
  }
}

// 注册
const handleRegister = async () => {
  errorMsg.value = ''
  if (!registerForm.phone || !/^1[3-9]\d{9}$/.test(registerForm.phone)) {
    showError('请输入正确的手机号')
    return
  }
  if (!registerForm.smsCode || registerForm.smsCode.length !== 6) {
    showError('请输入6位验证码')
    return
  }
  if (!registerForm.password || registerForm.password.length < 8) {
    showError('密码长度8-20位')
    return
  }
  if (registerForm.password !== registerForm.confirmPassword) {
    showError('两次密码不一致')
    return
  }

  try {
    await register({
      phone: registerForm.phone,
      smsCode: registerForm.smsCode,
      password: registerForm.password,
      confirmPassword: registerForm.confirmPassword
    })
    emit('success')
    close()
  } catch (e) {
    showError(e.message || '注册失败')
  }
}

// 忘记密码
const handleForgot = async () => {
  errorMsg.value = ''
  if (!forgotForm.phone || !/^1[3-9]\d{9}$/.test(forgotForm.phone)) {
    showError('请输入正确的手机号')
    return
  }
  if (!forgotForm.smsCode || forgotForm.smsCode.length !== 6) {
    showError('请输入6位验证码')
    return
  }
  if (!forgotForm.newPassword || forgotForm.newPassword.length < 8) {
    showError('密码长度8-20位')
    return
  }
  if (forgotForm.newPassword !== forgotForm.confirmPassword) {
    showError('两次密码不一致')
    return
  }

  try {
    await forgotPassword({
      phone: forgotForm.phone,
      smsCode: forgotForm.smsCode,
      newPassword: forgotForm.newPassword
    })
    alert('密码重置成功，请使用新密码登录')
    showForgot.value = false
    activeTab.value = 'login'
  } catch (e) {
    showError(e.message || '密码重置失败')
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="modelValue" class="modal-overlay" @click.self="close">
        <div class="auth-card">
          <!-- 关闭按钮 -->
          <button class="card-close" @click="close">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>

          <!-- 大标题 -->
          <div class="card-head">
            <h1 class="card-title">
              {{ activeTab === 'login' && !showForgot ? '登录' : activeTab === 'register' ? '注册' : '找回密码' }}
            </h1>
            <p class="card-subtitle">
              {{ activeTab === 'login' && !showForgot ? '欢迎回到 EduSpark' : activeTab === 'register' ? '创建您的账户' : '重置您的密码' }}
            </p>
          </div>

          <!-- 错误提示 -->
          <div v-if="errorMsg" class="error-box">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="12" y1="8" x2="12" y2="12"></line>
              <line x1="12" y1="16" x2="12.01" y2="16"></line>
            </svg>
            {{ errorMsg }}
          </div>

          <!-- 登录表单 -->
          <div v-if="activeTab === 'login' && !showForgot" class="card-form">
            <!-- 登录方式切换 -->
            <div class="login-mode-toggle">
              <button
                class="mode-btn"
                :class="{ active: loginMode === 'password' }"
                @click="switchLoginMode('password')"
              >密码登录</button>
              <button
                class="mode-btn"
                :class="{ active: loginMode === 'sms' }"
                @click="switchLoginMode('sms')"
              >短信登录</button>
            </div>

            <div class="field-row">
              <input
                v-model="loginForm.phone"
                type="tel"
                maxlength="11"
                placeholder="手机号"
                class="field-input"
              />
            </div>

            <div v-if="loginMode === 'password'" class="field-row">
              <input
                v-model="loginForm.password"
                type="password"
                placeholder="密码"
                class="field-input"
                @keyup.enter="handleLogin"
              />
            </div>

            <div v-else class="field-row sms-row">
              <input
                v-model="loginForm.smsCode"
                type="text"
                maxlength="6"
                placeholder="验证码"
                class="field-input sms-input"
                @keyup.enter="handleLogin"
              />
              <button class="sms-btn" :disabled="countdown > 0" @click="handleSendCode">
                {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
              </button>
            </div>

            <div class="form-links">
              <button class="link-btn" @click="showForgot = true">忘记密码？</button>
            </div>

            <button class="submit-btn" :disabled="isLoading" @click="handleLogin">
              {{ isLoading ? '登录中...' : '登 录' }}
            </button>

            <div class="form-links center">
              <span class="link-hint">没有账号？</span>
              <button class="link-btn" @click="switchTab('register')">立即注册</button>
            </div>
          </div>

          <!-- 注册表单 -->
          <div v-else-if="activeTab === 'register'" class="card-form">
            <div class="field-row">
              <input
                v-model="registerForm.phone"
                type="tel"
                maxlength="11"
                placeholder="手机号"
                class="field-input"
              />
            </div>

            <div class="field-row sms-row">
              <input
                v-model="registerForm.smsCode"
                type="text"
                maxlength="6"
                placeholder="验证码"
                class="field-input sms-input"
              />
              <button class="sms-btn" :disabled="countdown > 0" @click="handleSendCode">
                {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
              </button>
            </div>

            <div class="field-row">
              <input
                v-model="registerForm.password"
                type="password"
                placeholder="密码（8-20位）"
                class="field-input"
              />
            </div>

            <div class="field-row">
              <input
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="确认密码"
                class="field-input"
                @keyup.enter="handleRegister"
              />
            </div>

            <button class="submit-btn" :disabled="isLoading" @click="handleRegister">
              {{ isLoading ? '注册中...' : '注 册' }}
            </button>

            <div class="form-links center">
              <span class="link-hint">已有账号？</span>
              <button class="link-btn" @click="switchTab('login')">立即登录</button>
            </div>
          </div>

          <!-- 忘记密码表单 -->
          <div v-else-if="showForgot" class="card-form">
            <div class="field-row">
              <input
                v-model="forgotForm.phone"
                type="tel"
                maxlength="11"
                placeholder="手机号"
                class="field-input"
              />
            </div>

            <div class="field-row sms-row">
              <input
                v-model="forgotForm.smsCode"
                type="text"
                maxlength="6"
                placeholder="验证码"
                class="field-input sms-input"
              />
              <button class="sms-btn" :disabled="countdown > 0" @click="handleSendCode">
                {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
              </button>
            </div>

            <div class="field-row">
              <input
                v-model="forgotForm.newPassword"
                type="password"
                placeholder="新密码（8-20位）"
                class="field-input"
              />
            </div>

            <div class="field-row">
              <input
                v-model="forgotForm.confirmPassword"
                type="password"
                placeholder="确认新密码"
                class="field-input"
                @keyup.enter="handleForgot"
              />
            </div>

            <button class="submit-btn" :disabled="isLoading" @click="handleForgot">
              {{ isLoading ? '重置中...' : '重置密码' }}
            </button>

            <div class="form-links center">
              <button class="link-btn" @click="showForgot = false">返回登录</button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.32);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  backdrop-filter: blur(12px) saturate(180%);
}

.auth-card {
  --auth-accent-rgb: 37, 99, 235;
  --auth-accent: rgb(var(--auth-accent-rgb));
  width: 420px;
  max-width: 92vw;
  background:
    radial-gradient(circle at top right, rgba(var(--auth-accent-rgb), 0.08), transparent 40%),
    linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 24px;
  box-shadow:
    0 24px 48px rgba(15, 23, 42, 0.10),
    0 8px 16px rgba(15, 23, 42, 0.06);
  padding: 36px 32px 32px;
  position: relative;
  transition: box-shadow 0.22s ease;
}

.auth-card:hover {
  box-shadow:
    0 28px 56px rgba(15, 23, 42, 0.12),
    0 10px 20px rgba(15, 23, 42, 0.07);
}

.card-close {
  position: absolute;
  top: 16px;
  right: 16px;
  background: rgba(15, 23, 42, 0.04);
  border: none;
  border-radius: 12px;
  cursor: pointer;
  color: #64748b;
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.18s ease, color 0.18s ease;
}

.card-close:hover {
  background: rgba(15, 23, 42, 0.08);
  color: #0f172a;
}

.card-head {
  margin-bottom: 28px;
}

.card-title {
  font-size: 32px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.5px;
  line-height: 1.15;
  margin-bottom: 0;
}

.card-subtitle {
  font-size: 14px;
  color: #64748b;
  margin-top: 10px;
  line-height: 1.6;
}

.error-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #dc2626;
  padding: 10px 14px;
  font-size: 13px;
  font-weight: 600;
  border-radius: 14px;
  margin-bottom: 16px;
}

.card-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.login-mode-toggle {
  display: flex;
  background: rgba(241, 245, 249, 0.92);
  border-radius: 14px;
  padding: 3px;
  margin-bottom: 2px;
}

.mode-btn {
  flex: 1;
  padding: 9px;
  background: transparent;
  border: none;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  color: #64748b;
  transition: background 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
}

.mode-btn.active {
  background: #fff;
  color: #0f172a;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
}

.field-row {
  position: relative;
}

.field-input {
  width: 100%;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 14px;
  padding: 13px 16px;
  font-size: 15px;
  font-family: inherit;
  background: rgba(255, 255, 255, 0.8);
  color: #0f172a;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
  box-sizing: border-box;
  outline: none;
}

.field-input::placeholder {
  color: #94a3b8;
  font-weight: 500;
}

.field-input:focus {
  border-color: rgba(var(--auth-accent-rgb), 0.5);
  box-shadow: 0 0 0 4px rgba(var(--auth-accent-rgb), 0.08);
  background: #fff;
}

.sms-row {
  display: flex;
  gap: 8px;
}

.sms-input {
  flex: 1;
}

.sms-btn {
  background: rgba(var(--auth-accent-rgb), 0.06);
  border: 1px solid rgba(var(--auth-accent-rgb), 0.18);
  border-radius: 14px;
  padding: 0 18px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  color: var(--auth-accent);
  white-space: nowrap;
  transition: background 0.18s ease, border-color 0.18s ease;
  flex-shrink: 0;
}

.sms-btn:hover:not(:disabled) {
  background: rgba(var(--auth-accent-rgb), 0.10);
  border-color: rgba(var(--auth-accent-rgb), 0.32);
}

.sms-btn:disabled {
  color: #94a3b8;
  cursor: not-allowed;
}

.form-links {
  display: flex;
  justify-content: flex-end;
}

.form-links.center {
  justify-content: center;
  gap: 0;
}

.link-hint {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.link-btn {
  background: none;
  border: none;
  font-size: 13px;
  font-weight: 700;
  color: var(--auth-accent);
  cursor: pointer;
  text-decoration: none;
  padding: 0 0 0 4px;
  transition: color 0.18s ease;
}

.link-btn:hover {
  color: #1d4ed8;
}

.submit-btn {
  width: 100%;
  background: linear-gradient(180deg, var(--auth-accent) 0%, #1d4ed8 100%);
  color: #fff;
  border: none;
  border-radius: 14px;
  padding: 14px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 1px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  margin-top: 4px;
  box-shadow: 0 8px 20px rgba(var(--auth-accent-rgb), 0.2);
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(var(--auth-accent-rgb), 0.24);
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 4px 12px rgba(var(--auth-accent-rgb), 0.18);
}

.submit-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* 动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.22s ease;
}

.modal-enter-active .auth-card,
.modal-leave-active .auth-card {
  transition: transform 0.28s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.22s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .auth-card,
.modal-leave-to .auth-card {
  transform: scale(0.96) translateY(16px);
  opacity: 0;
}
</style>
