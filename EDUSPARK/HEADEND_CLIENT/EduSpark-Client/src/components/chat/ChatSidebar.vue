<script setup>
import { computed, ref, Transition } from 'vue'

/**
 * 教师端左侧栏：Logo / 折叠 / 新建会话 / 三个模式入口 / 历史会话列表 /
 * 底部用户区 / 用户菜单悬浮 / "全部历史"全屏面板。
 *
 * 所有状态都从父组件以 props 传入，用户操作通过 emit 发出去；
 * 子组件不直接持有 composable，保证一次 composable 调用、状态唯一来源。
 *
 * 例外：userMenuStyle 基于 user-section 的 getBoundingClientRect，DOM 在
 * 组件内部，所以让 ChatSidebar 自己计算这个浮层位置 style。
 */

defineProps({
  sidebarOpen: { type: Boolean, default: true },
  knowledgeDrawerOpen: { type: Boolean, default: false },
  anyWorkspaceVisible: { type: Boolean, default: false },

  currentMode: { type: String, default: null },
  currentSessionId: { type: String, default: null },

  // 历史会话
  historyList: { type: Array, default: () => [] },
  historyMenuOpen: { type: [String, Number, null], default: null },
  historyPanelOpen: { type: Boolean, default: false },
  historyPanelSearch: { type: String, default: '' },
  filteredHistoryList: { type: Array, default: () => [] },
  groupedHistoryList: { type: Array, default: () => [] },

  // 用户
  isLoggedIn: { type: Boolean, default: false },
  userInfo: { type: Object, default: () => ({}) },
  userMenuOpen: { type: Boolean, default: false }
})

const emit = defineEmits([
  'toggle-sidebar',
  'start-new-chat',
  'enter-mode',
  'switch-to-session',
  'toggle-history-menu',
  'edit-title',
  'delete-session',
  'open-history-panel',
  'close-history-panel',
  'update:history-panel-search',
  'toggle-user-menu',
  'open-personal-center',
  'open-settings',
  'logout'
])

// 用户菜单悬浮位置——绑在 user-section DOM ref 上算 fixed 坐标
const userSectionRef = ref(null)
const userMenuStyle = computed(() => {
  const el = userSectionRef.value
  if (!el) return {}
  const rect = el.getBoundingClientRect()
  return {
    position: 'fixed',
    left: `${rect.left + 12}px`,
    bottom: `${window.innerHeight - rect.top + 8}px`,
    width: `${rect.width - 24}px`,
    zIndex: 100
  }
})
</script>

<template>
  <!-- 侧边栏收起时的悬浮展开/新建按钮 -->
  <Transition name="fade">
    <div
      v-if="!sidebarOpen && !knowledgeDrawerOpen && !anyWorkspaceVisible"
      class="float-btn-group"
    >
      <div class="float-btn-wrapper">
        <button class="sidebar-float-btn expand-sidebar-btn" @click="emit('toggle-sidebar')">
          <img src="@/assets/images/logo.svg" alt="Logo" class="float-logo" />
        </button>
        <div class="float-btn-tooltip">展开侧边栏</div>
      </div>
      <div class="float-btn-wrapper">
        <button class="sidebar-float-btn new-chat-float-btn" @click="emit('start-new-chat')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
        </button>
        <div class="float-btn-tooltip">新建对话</div>
      </div>
    </div>
  </Transition>

  <aside class="sidebar" :class="{ 'collapsed': !sidebarOpen }">
    <div class="sidebar-inner">
      <!-- Logo 区域 -->
      <div class="logo-area">
        <img src="@/assets/images/logo.svg" alt="Logo" class="sidebar-logo" />
        <Transition name="fade">
          <h1 v-if="sidebarOpen">启思</h1>
        </Transition>
        <button class="sidebar-toggle" @click="emit('toggle-sidebar')" :title="sidebarOpen ? '收起侧边栏' : '展开侧边栏'">
          <svg v-if="sidebarOpen" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <line x1="3" y1="6" x2="13" y2="6"></line>
            <line x1="3" y1="12" x2="13" y2="12"></line>
            <line x1="3" y1="18" x2="13" y2="18"></line>
            <polyline points="18 9 14 12 18 15"></polyline>
          </svg>
          <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <line x1="11" y1="6" x2="21" y2="6"></line>
            <line x1="11" y1="12" x2="21" y2="12"></line>
            <line x1="11" y1="18" x2="21" y2="18"></line>
            <polyline points="6 9 10 12 6 15"></polyline>
          </svg>
        </button>
      </div>

      <!-- 新建会话 -->
      <button class="new-chat-btn" @click="emit('start-new-chat')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19"></line>
          <line x1="5" y1="12" x2="19" y2="12"></line>
        </svg>
        新建会话
      </button>

      <!-- 三个功能入口 -->
      <div class="section-title">功能入口</div>
      <div class="nav-items">
        <div class="nav-item" :class="{ active: currentMode === 'ppt' }" @click="emit('enter-mode', 'ppt')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path>
            <polyline points="13 2 13 9 20 9"></polyline>
          </svg>
          PPT生成
        </div>
        <div class="nav-item" :class="{ active: currentMode === 'interactive' }" @click="emit('enter-mode', 'interactive')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
            <circle cx="9" cy="7" r="4"></circle>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
          </svg>
          互动内容生成
        </div>
        <div class="nav-item" :class="{ active: currentMode === 'lesson_plan' }" @click="emit('enter-mode', 'lesson_plan')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
            <line x1="9" y1="9" x2="15" y2="9"></line>
            <line x1="9" y1="13" x2="15" y2="13"></line>
            <line x1="9" y1="17" x2="11" y2="17"></line>
          </svg>
          教案生成
        </div>
      </div>

      <!-- 历史会话 -->
      <div class="section-title">历史会话</div>
      <div class="history-list">
        <div
          class="history-item"
          v-for="item in historyList"
          :key="item.id"
          @click="emit('switch-to-session', item)"
        >
          <span class="history-title">{{ item.title }}</span>
          <div class="history-more" @click.stop="emit('toggle-history-menu', item.id)">
            <svg width="16" height="16" viewBox="0 0 1024 1024" fill="currentColor">
              <path d="M266.24 524.288a61.44 61.44 0 1 1-122.88 0 61.44 61.44 0 0 1 122.88 0zM573.44 524.288a61.44 61.44 0 1 1-122.88 0 61.44 61.44 0 0 1 122.88 0zM819.2 585.728a61.44 61.44 0 1 0 0-122.88 61.44 61.44 0 0 0 0 122.88z"></path>
            </svg>
          </div>
          <Transition name="menu-slide">
            <div v-if="historyMenuOpen === item.id" class="history-menu">
              <div class="history-menu-item" @click.stop="emit('edit-title', item)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                </svg>
                <span>编辑标题</span>
              </div>
              <div class="history-menu-item history-menu-danger" @click.stop="emit('delete-session', item)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="3 6 5 6 21 6"></polyline>
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                </svg>
                <span>删除</span>
              </div>
            </div>
          </Transition>
        </div>
        <div v-if="historyList.length === 0" class="history-empty">
          {{ isLoggedIn ? '暂无历史会话' : '请登录查看历史会话' }}
        </div>
        <div v-if="historyList.length > 0" class="view-all-history-item" @click="emit('open-history-panel')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <polyline points="12 6 12 12 16 14"></polyline>
          </svg>
          <span>查看全部</span>
        </div>
      </div>
    </div>

    <!-- 底部用户区 -->
    <div v-if="sidebarOpen" class="user-section" ref="userSectionRef">
      <div class="user-info" @click="emit('toggle-user-menu')">
        <div class="avatar">
          <img v-if="isLoggedIn && userInfo?.avatar" :src="userInfo.avatar" class="avatar-img" alt="avatar" />
          <span v-else>{{ isLoggedIn ? (userInfo?.username || userInfo?.phone || 'U').charAt(0).toUpperCase() : '登录' }}</span>
        </div>
        <Transition name="fade">
          <span class="user-name">{{ isLoggedIn ? (userInfo?.username || userInfo?.phone || '未设置') : '点击登录' }}</span>
        </Transition>
        <svg v-if="isLoggedIn" class="user-arrow" :class="{ rotated: userMenuOpen }" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="6 9 12 15 18 9"></polyline>
        </svg>
      </div>
    </div>
  </aside>

  <!-- 用户菜单下拉（放在 aside 外面避免 overflow:hidden 裁切） -->
  <Transition name="menu-slide">
    <div v-if="userMenuOpen && isLoggedIn && sidebarOpen" class="user-menu-fixed" :style="userMenuStyle">
      <div class="menu-item" @click="emit('open-personal-center')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
          <circle cx="12" cy="7" r="4"></circle>
        </svg>
        <span>个人中心</span>
      </div>
      <div class="menu-item" @click="emit('open-settings')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="3"></circle>
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
        </svg>
        <span>设置</span>
      </div>
      <div class="menu-divider"></div>
      <div class="menu-item menu-item-danger" @click="emit('logout')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
          <polyline points="16 17 21 12 16 7"></polyline>
          <line x1="21" y1="12" x2="9" y2="12"></line>
        </svg>
        <span>退出登录</span>
      </div>
    </div>
  </Transition>

  <!-- "全部历史"全屏面板 -->
  <Transition name="fade">
    <div v-if="historyPanelOpen" class="history-panel-overlay" @click.self="emit('close-history-panel')">
      <div class="history-panel">
        <div class="history-panel-header">
          <h2>历史会话</h2>
          <button class="history-panel-close" @click="emit('close-history-panel')">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
        <div class="history-panel-search">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"></circle>
            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
          </svg>
          <input
            :value="historyPanelSearch"
            placeholder="搜索历史会话"
            @input="emit('update:history-panel-search', $event.target.value)"
          />
          <button v-if="historyPanelSearch" class="search-clear" @click="emit('update:history-panel-search', '')">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
        <div class="history-panel-body">
          <div v-for="group in groupedHistoryList" :key="group.label" class="history-group">
            <div class="history-group-label">{{ group.label }}</div>
            <div
              v-for="item in group.items"
              :key="item.id"
              class="history-panel-item"
              :class="{ active: currentSessionId === item.id }"
              @click="emit('switch-to-session', item); emit('close-history-panel')"
            >
              <div class="history-panel-item-main">
                <div class="history-panel-item-title">{{ item.title }}</div>
                <div v-if="item.lastMessage" class="history-panel-item-preview">{{ item.lastMessage }}</div>
              </div>
              <div class="history-panel-item-meta">
                <span class="history-panel-item-date">{{ item.date }}</span>
                <div class="history-panel-item-actions">
                  <button @click.stop="emit('edit-title', item)" title="编辑标题">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                    </svg>
                  </button>
                  <button @click.stop="emit('delete-session', item)" title="删除会话">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="3 6 5 6 21 6"></polyline>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          </div>
          <div v-if="filteredHistoryList.length === 0 && historyList.length > 0" class="history-panel-empty">
            无匹配会话
          </div>
          <div v-if="historyList.length === 0" class="history-panel-empty">
            暂无历史会话
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>
