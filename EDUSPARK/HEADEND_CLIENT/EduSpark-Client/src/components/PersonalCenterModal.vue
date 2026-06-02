<script setup>
import { ref, reactive, watch } from 'vue'
import { useAuth } from '@/composables/useAuth.js'
import { updateUserInfo, changePassword, uploadAvatar } from '@/api/user.js'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const { userInfo, fetchUserInfo, updateLocalUser } = useAuth()

const currentView = ref('info')

const infoForm = reactive({
  username: '',
  email: '',
  bio: ''
})
const infoLoading = ref(false)
const infoSuccess = ref(false)

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const pwdLoading = ref(false)
const pwdSuccess = ref(false)
const pwdError = ref('')

const avatarLoading = ref(false)
const avatarError = ref('')

watch(() => props.modelValue, (val) => {
  if (val) {
    currentView.value = 'info'
    infoSuccess.value = false
    pwdSuccess.value = false
    pwdError.value = ''
    avatarError.value = ''
    if (userInfo.value) {
      infoForm.username = userInfo.value.username || ''
      infoForm.email = userInfo.value.email || ''
      infoForm.bio = userInfo.value.bio || ''
    }
  }
})

const close = () => {
  emit('update:modelValue', false)
}

const infoError = ref('')

const handleSaveInfo = async () => {
  infoError.value = ''
  infoSuccess.value = false
  if (infoForm.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(infoForm.email)) {
    infoError.value = '邮箱格式不正确'
    return
  }
  infoLoading.value = true
  try {
    await updateUserInfo({
      username: infoForm.username || undefined,
      email: infoForm.email || undefined,
      bio: infoForm.bio || undefined
    })
    updateLocalUser({
      username: infoForm.username,
      email: infoForm.email,
      bio: infoForm.bio
    })
    infoSuccess.value = true
    setTimeout(() => { infoSuccess.value = false }, 2500)
  } catch (e) {
    infoError.value = e.message || '保存失败'
  } finally {
    infoLoading.value = false
  }
}

const handleChangePassword = async () => {
  pwdError.value = ''
  pwdSuccess.value = false
  if (!pwdForm.oldPassword) {
    pwdError.value = '请输入原密码'
    return
  }
  if (!pwdForm.newPassword || pwdForm.newPassword.length < 8) {
    pwdError.value = '新密码长度8-20位'
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    pwdError.value = '两次密码不一致'
    return
  }
  pwdLoading.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    pwdSuccess.value = true
    setTimeout(() => { pwdSuccess.value = false }, 2500)
  } catch (e) {
    pwdError.value = e.message || '修改失败'
  } finally {
    pwdLoading.value = false
  }
}

const handleAvatarClick = () => {
  if (avatarLoading.value) return
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = handleAvatarChange
  input.click()
}

const handleAvatarChange = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    avatarError.value = '请选择图片文件'
    return
  }

  if (file.size > 5 * 1024 * 1024) {
    avatarError.value = '图片大小不能超过5MB'
    return
  }

  avatarLoading.value = true
  avatarError.value = ''

  try {
    const avatarUrl = await uploadAvatar(file)
    updateLocalUser({ avatar: avatarUrl })
  } catch (err) {
    avatarError.value = err.message || '上传失败'
  } finally {
    avatarLoading.value = false
  }
}

</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="modelValue" class="modal-overlay" @click.self="close">
        <div class="modal-container">
          <!-- 关闭按钮 -->
          <button class="modal-close" @click="close">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>

          <div class="modal-layout">
            <!-- 左侧菜单 -->
            <div class="sidebar-menu">
              <div class="user-card">
                <div class="user-avatar-wrapper" @click="handleAvatarClick">
                  <img
                    v-if="userInfo?.avatar"
                    :src="userInfo.avatar"
                    class="user-avatar-img"
                    alt="avatar"
                  />
                  <div v-else class="user-avatar">
                    {{ (userInfo?.username || userInfo?.phone || 'U').charAt(0).toUpperCase() }}
                  </div>
                  <div class="avatar-overlay">
                    <svg v-if="!avatarLoading" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"></path>
                      <circle cx="12" cy="13" r="4"></circle>
                    </svg>
                    <div v-else class="avatar-loading-spinner"></div>
                  </div>
                </div>
                <div v-if="avatarError" class="avatar-error">{{ avatarError }}</div>
                <div class="user-name">{{ userInfo?.username || '未设置昵称' }}</div>
                <div class="user-phone">{{ userInfo?.phone || '' }}</div>
              </div>

              <nav class="menu-nav">
                <button
                  class="menu-item"
                  :class="{ active: currentView === 'info' }"
                  @click="currentView = 'info'"
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                    <circle cx="12" cy="7" r="4"></circle>
                  </svg>
                  个人信息
                </button>
                <button
                  class="menu-item"
                  :class="{ active: currentView === 'password' }"
                  @click="currentView = 'password'"
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                    <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                  </svg>
                  修改密码
                </button>
              </nav>
            </div>

            <!-- 右侧内容 -->
            <div class="content-area">
              <!-- 个人信息 -->
              <div v-if="currentView === 'info'" class="content-inner">
                <h3 class="section-title">个人信息</h3>

                <div v-if="infoSuccess" class="success-tip">保存成功</div>
                <div v-if="infoError" class="error-tip">{{ infoError }}</div>

                <div class="form-item">
                  <label class="form-label">昵称</label>
                  <input
                    v-model="infoForm.username"
                    type="text"
                    maxlength="50"
                    placeholder="请输入昵称"
                    class="form-input"
                  />
                </div>

                <div class="form-item">
                  <label class="form-label">手机号</label>
                  <input
                    :value="userInfo?.phone || ''"
                    type="text"
                    disabled
                    placeholder="未绑定"
                    class="form-input disabled"
                  />
                </div>

                <div class="form-item">
                  <label class="form-label">邮箱</label>
                  <input
                    v-model="infoForm.email"
                    type="email"
                    maxlength="100"
                    placeholder="请输入邮箱（用于找回密码）"
                    class="form-input"
                  />
                </div>

                <div class="form-item">
                  <label class="form-label">个人简介</label>
                  <textarea
                    v-model="infoForm.bio"
                    maxlength="500"
                    rows="3"
                    placeholder="简单介绍一下自己..."
                    class="form-input"
                  ></textarea>
                </div>

                <button class="primary-btn" :disabled="infoLoading" @click="handleSaveInfo">
                  {{ infoLoading ? '保存中...' : '保存修改' }}
                </button>
              </div>

              <!-- 修改密码 -->
              <div v-else-if="currentView === 'password'" class="content-inner">
                <h3 class="section-title">修改密码</h3>

                <div v-if="pwdSuccess" class="success-tip">密码修改成功</div>
                <div v-if="pwdError" class="error-tip">{{ pwdError }}</div>

                <div class="form-item">
                  <label class="form-label">原密码</label>
                  <input
                    v-model="pwdForm.oldPassword"
                    type="password"
                    placeholder="请输入原密码"
                    class="form-input"
                  />
                </div>

                <div class="form-item">
                  <label class="form-label">新密码</label>
                  <input
                    v-model="pwdForm.newPassword"
                    type="password"
                    placeholder="新密码（8-20位）"
                    class="form-input"
                  />
                </div>

                <div class="form-item">
                  <label class="form-label">确认新密码</label>
                  <input
                    v-model="pwdForm.confirmPassword"
                    type="password"
                    placeholder="再次输入新密码"
                    class="form-input"
                    @keyup.enter="handleChangePassword"
                  />
                </div>

                <button class="primary-btn" :disabled="pwdLoading" @click="handleChangePassword">
                  {{ pwdLoading ? '修改中...' : '确认修改' }}
                </button>
              </div>
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
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  backdrop-filter: blur(4px);
}

.modal-container {
  background: var(--es-surface);
  border-radius: 20px;
  padding: 0;
  width: 680px;
  max-width: 92vw;
  max-height: 85vh;
  overflow: hidden;
  position: relative;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.18);
  display: flex;
  flex-direction: column;
}

.modal-close {
  position: absolute;
  top: 16px;
  right: 16px;
  background: none;
  border: none;
  cursor: pointer;
  color: var(--es-text-tertiary);
  padding: 4px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s, background 0.2s;
  z-index: 10;
}

.modal-close:hover {
  color: var(--es-text-primary);
  background: var(--es-surface-muted);
}

.modal-layout {
  display: flex;
  height: 100%;
  max-height: 85vh;
}

/* 左侧菜单 */
.sidebar-menu {
  width: 200px;
  background: var(--es-surface-soft);
  border-right: 1px solid var(--es-border);
  padding: 28px 16px 20px;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.user-card {
  text-align: center;
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--es-border);
}

.user-avatar-wrapper {
  position: relative;
  width: 56px;
  height: 56px;
  margin: 0 auto 10px;
  cursor: pointer;
  border-radius: 50%;
  overflow: hidden;
}

.user-avatar-wrapper:hover .avatar-overlay {
  opacity: 1;
}

.user-avatar {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, var(--es-link), #6366F1);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  color: white;
}

.user-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
  border-radius: 50%;
}

.avatar-overlay svg {
  color: white;
}

.avatar-loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.avatar-error {
  font-size: 11px;
  color: var(--es-danger-text);
  margin-bottom: 4px;
}

.user-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--es-text-primary);
  margin-bottom: 2px;
}

.user-phone {
  font-size: 12px;
  color: var(--es-text-tertiary);
}

.menu-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  border: none;
  background: none;
  font-size: 14px;
  color: var(--es-text-secondary);
  cursor: pointer;
  text-align: left;
  transition: all 0.2s;
  width: 100%;
}

.menu-item:hover {
  background: var(--es-border);
  color: var(--es-text-primary);
}

.menu-item.active {
  background: var(--es-link-soft);
  color: var(--es-link-hover);
  font-weight: 500;
}

.menu-item svg {
  flex-shrink: 0;
}

/* 右侧内容 */
.content-area {
  flex: 1;
  overflow-y: auto;
  padding: 28px 32px;
}

.content-inner {
  max-width: 360px;
}

.section-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--es-text-primary);
  margin-bottom: 20px;
}

.success-tip {
  background: var(--es-success-bg);
  border: 1px solid var(--es-success-bg);
  color: var(--es-success-text);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  margin-bottom: 16px;
}

.error-tip {
  background: var(--es-danger-bg);
  border: 1px solid var(--es-danger-bg);
  color: var(--es-danger-text);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  margin-bottom: 16px;
}

.form-item {
  margin-bottom: 16px;
}

.form-label {
  display: block;
  font-size: 13px;
  color: var(--es-text-secondary);
  margin-bottom: 6px;
  font-weight: 500;
}

.form-input {
  width: 100%;
  border: 1px solid var(--es-border);
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-sizing: border-box;
  color: var(--es-text-primary);
  resize: none;
  font-family: inherit;
}

.form-input::placeholder {
  color: var(--es-text-tertiary);
}

.form-input:focus {
  border-color: var(--es-link);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.form-input.disabled {
  background: var(--es-surface-soft);
  color: var(--es-text-tertiary);
  cursor: not-allowed;
}

.primary-btn {
  width: 100%;
  background: var(--es-text-primary);
  color: var(--es-surface);
  border: none;
  border-radius: 10px;
  padding: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
  margin-top: 8px;
}

.primary-btn:hover:not(:disabled) {
  background: var(--es-link);
}

.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.25s ease;
}

.modal-enter-active .modal-container,
.modal-leave-active .modal-container {
  transition: transform 0.25s ease, opacity 0.25s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .modal-container,
.modal-leave-to .modal-container {
  transform: scale(0.92) translateY(10px);
  opacity: 0;
}
</style>
