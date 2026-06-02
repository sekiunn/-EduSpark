import { ref } from 'vue'

/**
 * 用户右下角菜单 + 登录/个人中心弹窗的开关状态。
 *
 * 不放进来的：handleLogout（涉及全局状态重置，留在 ChatHome）、
 * userMenuStyle computed（依赖模板里的 DOM ref，跟视图绑定紧）。
 *
 * @param {Ref<boolean>} isLoggedIn  当前是否已登录，由 useAuth() 提供。
 *                                   未登录时点用户头像会直接弹登录而不是展开菜单。
 */
export function useUserMenu(isLoggedIn) {
  const authModalVisible = ref(false)
  const personalCenterVisible = ref(false)
  const userMenuOpen = ref(false)

  function openAuthModal() {
    authModalVisible.value = true
  }

  function openPersonalCenter() {
    userMenuOpen.value = false
    personalCenterVisible.value = true
  }

  function openSettings() {
    userMenuOpen.value = false
    // 占位，真实"设置"功能后续接入
    alert('设置功能开发中...')
  }

  function toggleUserMenu() {
    if (!isLoggedIn?.value) {
      openAuthModal()
      return
    }
    userMenuOpen.value = !userMenuOpen.value
  }

  /** 监听到 401 触发 auth-required 事件时调起登录。 */
  function handleAuthRequired() {
    openAuthModal()
  }

  return {
    authModalVisible,
    personalCenterVisible,
    userMenuOpen,
    openAuthModal,
    openPersonalCenter,
    openSettings,
    toggleUserMenu,
    handleAuthRequired
  }
}
