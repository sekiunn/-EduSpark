<script setup>
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

const props = defineProps({
  open: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close'])

const route = useRoute()

const menuItems = [
  {
    key: 'dashboard',
    label: '首页',
    to: '/dashboard',
    icon: 'M3 13h8V3H3zm10 8h8V8h-8zm0-10v5h8V3zm-10 18h8v-6H3zm10 0h8v-6h-8z'
  },
  {
    key: 'ppt',
    label: 'PPT管理',
    to: '/ppt',
    icon: 'M4 5h16v14H4zm2 2v10h12V7zm14-4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2M8 20h8v2H8z'
  }
]

const activeKey = computed(() => route.meta.menuKey)
</script>

<template>
  <div v-if="props.open" class="mobile-sidebar-mask" @click="emit('close')"></div>

  <aside class="admin-sidebar" :class="{ 'is-open': props.open }">
    <div class="admin-sidebar-brand">
      <div class="admin-sidebar-brand-badge">E</div>
      <div>
        <div class="admin-sidebar-brand-title">EduSpark Admin</div>
        <div class="admin-sidebar-brand-subtitle">启思后台管理系统</div>
      </div>
    </div>

    <nav class="admin-nav">
      <div class="admin-nav-group-title">系统导航</div>

      <RouterLink
        v-for="item in menuItems"
        :key="item.key"
        :to="item.to"
        class="admin-nav-link"
        :class="{ 'is-active': activeKey === item.key }"
        @click="emit('close')"
      >
        <svg class="admin-nav-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path :d="item.icon"></path>
        </svg>
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>
  </aside>
</template>
