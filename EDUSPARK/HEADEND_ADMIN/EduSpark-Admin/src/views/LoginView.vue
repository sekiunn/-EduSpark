<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { loginByPassword } from '@/api/auth.js'
import { clearAdminAuth, isAdminRole, saveAdminAuth } from '@/stores/adminAuth.js'

const router = useRouter()
const route = useRoute()

const form = reactive({
  phone: '',
  password: ''
})

const loading = ref(false)
const initialError = Array.isArray(route.query.error) ? route.query.error[0] : route.query.error
const errorMessage = ref(initialError || '')

function getRedirectTarget() {
  const redirect = Array.isArray(route.query.redirect) ? route.query.redirect[0] : route.query.redirect
  return redirect || '/dashboard'
}

async function handleLogin() {
  errorMessage.value = ''

  if (!form.phone.trim()) {
    errorMessage.value = '请输入手机号'
    return
  }

  if (!form.password.trim()) {
    errorMessage.value = '请输入密码'
    return
  }

  loading.value = true

  try {
    const response = await loginByPassword(form)

    if (!isAdminRole(response.data?.user?.role)) {
      clearAdminAuth()
      errorMessage.value = '当前账号无管理端访问权限'
      return
    }

    saveAdminAuth(response.data?.accessToken, response.data?.user || null)
    router.replace(getRedirectTarget())
  } catch (error) {
    errorMessage.value = error.message || '登录失败，请检查账号信息'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-hero">
      <div class="login-hero-badge">启思 - 多模态AI教学智能体</div>
      <h1 class="login-hero-title">EduSpark 管理端</h1>
      <p class="login-hero-text">
        聚焦教学场景配置与资源管理，提供简洁直接的后台入口，便于后续继续扩展知识库、用户和规则管理能力。
      </p>
    </section>

    <section class="login-panel-wrap">
      <div class="login-panel">
        <div class="login-panel-head">
          <div class="login-panel-title">系统登录</div>
          <div class="login-panel-subtitle">请输入已开通账号的手机号和密码</div>
        </div>

        <div v-if="errorMessage" class="login-error">
          {{ errorMessage }}
        </div>

        <form class="login-form" @submit.prevent="handleLogin">
          <label class="login-field">
            <span>手机号</span>
            <input
              v-model.trim="form.phone"
              class="login-input"
              type="text"
              placeholder="请输入手机号"
              autocomplete="username"
            />
          </label>

          <label class="login-field">
            <span>密码</span>
            <input
              v-model="form.password"
              class="login-input"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
            />
          </label>

          <button class="login-submit" type="submit" :disabled="loading">
            {{ loading ? '登录中...' : '登录管理端' }}
          </button>
        </form>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.2fr 0.9fr;
  background:
    radial-gradient(circle at 14% 16%, rgba(76, 144, 255, 0.24), transparent 24%),
    linear-gradient(135deg, #1d3f93 0%, #2a5cc3 46%, #eff4fc 46%, #f6f9fd 100%);
}

.login-hero {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  padding: 72px 72px 56px;
  color: var(--es-text-inverse);
}

.login-hero::before,
.login-hero::after {
  content: '';
  position: absolute;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
}

.login-hero::before {
  top: -120px;
  right: -40px;
  width: 320px;
  height: 320px;
}

.login-hero::after {
  bottom: -80px;
  left: 12%;
  width: 260px;
  height: 260px;
}

.login-hero-badge {
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  padding: 0 16px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.92);
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(10px);
}

.login-hero-title {
  margin-top: 26px;
  font-size: clamp(38px, 5vw, 60px);
  line-height: 1.08;
  font-weight: 800;
}

.login-hero-text {
  margin-top: 18px;
  max-width: 520px;
  font-size: 18px;
  line-height: 1.9;
  color: rgba(255, 255, 255, 0.82);
}

.login-panel-wrap {
  display: grid;
  place-items: center;
  padding: 32px;
}

.login-panel {
  width: min(460px, 100%);
  padding: 36px 34px;
  border: 1px solid rgba(255, 255, 255, 0.88);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 26px 56px rgba(27, 51, 115, 0.16);
}

.login-panel-head {
  margin-bottom: 22px;
}

.login-panel-title {
  font-size: 30px;
  font-weight: 800;
  color: var(--es-text-primary);
}

.login-panel-subtitle {
  margin-top: 8px;
  font-size: 14px;
  color: var(--es-text-secondary);
}

.login-error {
  margin-bottom: 16px;
  padding: 11px 14px;
  border-radius: 12px;
  color: var(--es-danger-text);
  background: rgba(245, 63, 63, 0.1);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.login-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: var(--es-text-primary);
  font-size: 14px;
  font-weight: 700;
}

.login-input {
  height: 48px;
  padding: 0 14px;
  border: 1px solid var(--es-border);
  border-radius: 14px;
  background: var(--es-surface-soft);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.login-input:focus {
  outline: none;
  border-color: rgba(47, 124, 246, 0.44);
  box-shadow: 0 0 0 3px rgba(47, 124, 246, 0.12);
}

.login-submit {
  margin-top: 4px;
  height: 50px;
  border: none;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 800;
  color: var(--es-text-inverse);
  background: linear-gradient(135deg, #2f7cf6 0%, #58a4ff 100%);
  box-shadow: 0 16px 28px rgba(47, 124, 246, 0.22);
}

@media (max-width: 1120px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-hero {
    padding: 52px 24px 34px;
  }
}

@media (max-width: 640px) {
  .login-panel-wrap {
    padding: 18px;
  }

  .login-panel {
    padding: 28px 20px;
    border-radius: 18px;
  }

  .login-hero-title {
    font-size: 34px;
  }
}
</style>
