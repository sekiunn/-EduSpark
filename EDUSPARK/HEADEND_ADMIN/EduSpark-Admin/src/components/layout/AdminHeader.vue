<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getRoleLabel } from '@/stores/adminAuth.js'

const props = defineProps({
  user: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['logout'])

const route = useRoute()
const router = useRouter()
const userMenuOpen = ref(false)
const mobileMenuOpen = ref(false)
const navMenuOpenKey = ref('')
const mobileExpandedKeys = ref([])
const userMenuRef = ref(null)
const navMenuRef = ref(null)

const menuItems = [
  {
    key: 'dashboard',
    label: '首页',
    to: '/dashboard'
  },
  {
    key: 'ppt',
    label: 'PPT 管理',
    children: [
      {
        key: 'ppt-templates',
        label: '模板管理',
        desc: '维护模板信息、预览素材和引擎键',
        to: '/ppt/templates'
      },
      {
        key: 'ppt-categories',
        label: '分类管理',
        desc: '维护场景、风格和模板归类关系',
        to: '/ppt/categories'
      }
    ]
  }
]

const activeKey = computed(() => route.meta.menuKey || 'dashboard')
const userInitial = computed(() => (props.user?.username || 'A').slice(0, 1).toUpperCase())
const displayUserName = computed(() => props.user?.username || '管理员')
const displayUserRole = computed(() => getRoleLabel(props.user?.role))
const displayUserPhone = computed(() => props.user?.phone || '未绑定手机号')

function isItemActive(item) {
  if (item.children?.length) {
    return activeKey.value === item.key
  }

  return activeKey.value === item.key || route.path === item.to
}

function goTo(path) {
  navMenuOpenKey.value = ''
  mobileMenuOpen.value = false
  mobileExpandedKeys.value = []
  router.push(path)
}

function toggleUserMenu() {
  userMenuOpen.value = !userMenuOpen.value
}

function toggleMobileMenu() {
  mobileMenuOpen.value = !mobileMenuOpen.value
}

function toggleNavMenu(key) {
  navMenuOpenKey.value = navMenuOpenKey.value === key ? '' : key
}

function toggleMobileSubmenu(key) {
  if (mobileExpandedKeys.value.includes(key)) {
    mobileExpandedKeys.value = mobileExpandedKeys.value.filter((item) => item !== key)
    return
  }

  mobileExpandedKeys.value = [...mobileExpandedKeys.value, key]
}

function isMobileExpanded(key) {
  return mobileExpandedKeys.value.includes(key)
}

function handleDocumentClick(event) {
  if (userMenuRef.value && !userMenuRef.value.contains(event.target)) {
    userMenuOpen.value = false
  }

  if (navMenuRef.value && !navMenuRef.value.contains(event.target)) {
    navMenuOpenKey.value = ''
  }
}

watch(
  () => route.fullPath,
  () => {
    userMenuOpen.value = false
    mobileMenuOpen.value = false
    navMenuOpenKey.value = ''
    mobileExpandedKeys.value = []
  }
)

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})
</script>

<template>
  <header class="header-shell">
    <div class="top-navbar">
      <div class="navbar-left">
        <button class="nav-mobile-toggle" type="button" aria-label="打开导航菜单" @click="toggleMobileMenu">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="3" y1="6" x2="21" y2="6"></line>
            <line x1="3" y1="12" x2="21" y2="12"></line>
            <line x1="3" y1="18" x2="21" y2="18"></line>
          </svg>
        </button>

        <button class="logo-section" type="button" @click="goTo('/dashboard')">
          <span class="logo-mark">AI</span>
          <span class="logo-copy">
            <strong class="logo-title">EduSpark 管理端</strong>
            <span class="logo-subtitle">启思 · 多模态 AI 教学智能体</span>
          </span>
        </button>
      </div>

      <nav ref="navMenuRef" class="navbar-center">
        <template v-for="item in menuItems" :key="item.key">
          <div v-if="item.children?.length" class="top-menu-group">
            <button
              class="top-menu-item top-menu-item--submenu"
              :class="{ 'is-active': isItemActive(item), 'is-open': navMenuOpenKey === item.key }"
              type="button"
              @click.stop="toggleNavMenu(item.key)"
            >
              <span>{{ item.label }}</span>
              <svg class="top-menu-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
            </button>

            <Transition name="nav-dropdown">
              <div v-if="navMenuOpenKey === item.key" class="top-menu-dropdown">
                <button
                  v-for="child in item.children"
                  :key="child.key"
                  class="top-menu-dropdown-item"
                  :class="{ 'is-active': route.path === child.to }"
                  type="button"
                  @click="goTo(child.to)"
                >
                  <span class="top-menu-dropdown-title">{{ child.label }}</span>
                  <span class="top-menu-dropdown-desc">{{ child.desc }}</span>
                </button>
              </div>
            </Transition>
          </div>

          <button
            v-else
            class="top-menu-item"
            :class="{ 'is-active': isItemActive(item) }"
            type="button"
            @click="goTo(item.to)"
          >
            {{ item.label }}
          </button>
        </template>
      </nav>

      <div class="navbar-right">
        <div ref="userMenuRef" class="user-menu">
          <button class="user-info" :class="{ 'is-open': userMenuOpen }" type="button" @click.stop="toggleUserMenu">
            <span class="user-avatar">{{ userInitial }}</span>
            <span class="user-meta">
              <strong>{{ displayUserName }}</strong>
              <span>{{ displayUserRole }}</span>
            </span>
            <svg class="user-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"></polyline>
            </svg>
          </button>

          <Transition name="user-dropdown">
            <div v-if="userMenuOpen" class="user-dropdown">
              <div class="user-dropdown-section">
                <div class="user-dropdown-name">{{ displayUserName }}</div>
                <div class="user-dropdown-text">{{ displayUserRole }}</div>
                <div class="user-dropdown-text">{{ displayUserPhone }}</div>
              </div>

              <button class="user-dropdown-action" type="button" @click="emit('logout')">退出登录</button>
            </div>
          </Transition>
        </div>
      </div>
    </div>

    <Transition name="mobile-nav">
      <div v-if="mobileMenuOpen" class="mobile-nav-panel">
        <div v-for="item in menuItems" :key="item.key" class="mobile-nav-group">
          <button
            class="mobile-nav-item"
            :class="{ 'is-active': isItemActive(item) }"
            type="button"
            @click="item.children?.length ? toggleMobileSubmenu(item.key) : goTo(item.to)"
          >
            <span>{{ item.label }}</span>
            <svg
              v-if="item.children?.length"
              class="mobile-nav-chevron"
              :class="{ 'is-open': isMobileExpanded(item.key) }"
              width="14"
              height="14"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <polyline points="6 9 12 15 18 9"></polyline>
            </svg>
          </button>

          <div v-if="item.children?.length && isMobileExpanded(item.key)" class="mobile-nav-submenu">
            <button
              v-for="child in item.children"
              :key="child.key"
              class="mobile-nav-subitem"
              :class="{ 'is-active': route.path === child.to }"
              type="button"
              @click="goTo(child.to)"
            >
              <span>{{ child.label }}</span>
              <small>{{ child.desc }}</small>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </header>
</template>
