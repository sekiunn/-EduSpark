<script setup>
import { computed, onMounted } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import AdminHeader from '@/components/layout/AdminHeader.vue'
import { getCurrentUserInfo } from '@/api/auth.js'
import { authState, clearAdminAuth, isAdminRole, updateAdminUser } from '@/stores/adminAuth.js'

const route = useRoute()
const router = useRouter()

const breadcrumbItems = computed(() => {
  const currentTitle = route.meta.title || ''

  if (!currentTitle || route.name === 'dashboard') {
    return [{ label: '首页' }]
  }

  return [
    { label: '首页', to: '/dashboard' },
    { label: currentTitle }
  ]
})

const currentSubtitle = computed(() => route.meta.subtitle || '')

function handleLogout() {
  clearAdminAuth()
  router.replace('/login')
}

onMounted(async () => {
  if (!authState.token || authState.user) {
    return
  }

  try {
    const response = await getCurrentUserInfo()
    if (!response.data) {
      return
    }

    if (!isAdminRole(response.data.role)) {
      clearAdminAuth()
      router.replace({
        path: '/login',
        query: {
          error: '当前账号没有管理端访问权限'
        }
      })
      return
    }

    updateAdminUser(response.data)
  } catch {
    clearAdminAuth()
    router.replace('/login')
  }
})
</script>

<template>
  <div class="app-container">
    <AdminHeader :user="authState.user" @logout="handleLogout" />

    <div class="breadcrumb-container">
      <div class="breadcrumb-copy">
        <div class="breadcrumb-trail">
          <template v-for="(item, index) in breadcrumbItems" :key="`${item.label}-${index}`">
            <RouterLink v-if="item.to" :to="item.to" class="breadcrumb-link">
              {{ item.label }}
            </RouterLink>
            <span v-else class="breadcrumb-current">{{ item.label }}</span>
            <span v-if="index < breadcrumbItems.length - 1" class="breadcrumb-separator">/</span>
          </template>
        </div>

        <div v-if="currentSubtitle" class="breadcrumb-subtitle">{{ currentSubtitle }}</div>
      </div>
    </div>

    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>
