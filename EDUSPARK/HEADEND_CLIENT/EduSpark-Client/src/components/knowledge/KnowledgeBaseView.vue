<script setup>
import { computed, ref } from 'vue'
import KnowledgeFileDetailPanel from './KnowledgeFileDetailPanel.vue'
import KnowledgeFileList from './KnowledgeFileList.vue'
import KnowledgeSearchLab from './KnowledgeSearchLab.vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  userId: {
    type: [String, Number],
    default: null
  },
  searchKeyword: {
    type: String,
    default: ''
  },
  currentCategory: {
    type: String,
    default: 'all'
  },
  viewMode: {
    type: String,
    default: 'grid'
  },
  knowledgeDocs: {
    type: Array,
    default: () => []
  },
  filteredDocs: {
    type: Array,
    default: () => []
  },
  selectedDocId: {
    type: [String, Number],
    default: null
  },
  knowledgeLoading: {
    type: Boolean,
    default: false
  },
  isUploading: {
    type: Boolean,
    default: false
  },
  uploadProgress: {
    type: Number,
    default: 0
  },
  uploadQueue: {
    type: Array,
    default: () => []
  },
  workspaces: {
    type: Array,
    default: () => []
  },
  workspacesLoading: {
    type: Boolean,
    default: false
  },
  selectedWorkspaceId: {
    type: [String, Number],
    default: null
  },
  workspaceCounts: {
    type: Object,
    default: () => ({ ungrouped: 0, total: 0 })
  }
})

const emit = defineEmits([
  'update:searchKeyword',
  'update:currentCategory',
  'update:viewMode',
  'select-doc',
  'quote-doc',
  'retry-file',
  'delete-file',
  'trigger-file-upload',
  'trigger-folder-import',
  'select-workspace',
  'create-workspace',
  'rename-workspace',
  'delete-workspace'
])

const showCreateModal = ref(false)
const newWorkspaceName = ref('')
const newWorkspaceDesc = ref('')
const newWorkspaceColor = ref('#1f1d1a')

const renamingWorkspaceId = ref(null)
const renameValue = ref('')

const COVER_PALETTE = ['#1f1d1a', '#5a4632', '#2e5a4b', '#3a4a7a', '#7a3a4a', '#7a6432', '#454f5b']

const openCreateModal = () => {
  newWorkspaceName.value = ''
  newWorkspaceDesc.value = ''
  newWorkspaceColor.value = COVER_PALETTE[0]
  showCreateModal.value = true
}

const closeCreateModal = () => {
  showCreateModal.value = false
}

const submitCreateWorkspace = () => {
  if (!newWorkspaceName.value.trim()) return
  emit('create-workspace', {
    name: newWorkspaceName.value.trim(),
    description: newWorkspaceDesc.value.trim(),
    coverColor: newWorkspaceColor.value
  })
  showCreateModal.value = false
}

const startRename = (ws) => {
  renamingWorkspaceId.value = ws.id
  renameValue.value = ws.name
}

const submitRename = (ws) => {
  const next = renameValue.value.trim()
  if (next && next !== ws.name) {
    emit('rename-workspace', {
      id: ws.id,
      name: next,
      description: ws.description || '',
      coverColor: ws.coverColor || ''
    })
  }
  renamingWorkspaceId.value = null
}

const cancelRename = () => {
  renamingWorkspaceId.value = null
}

const handleDeleteWorkspace = (ws) => {
  emit('delete-workspace', ws.id)
}

const selectAll = () => emit('select-workspace', null)
const selectUngrouped = () => emit('select-workspace', 'ungrouped')
const selectWorkspace = (ws) => emit('select-workspace', ws.id)

const isAllActive = computed(() => props.selectedWorkspaceId === null || props.selectedWorkspaceId === undefined)
const isUngroupedActive = computed(() => props.selectedWorkspaceId === 'ungrouped')
const activeWorkspace = computed(() => {
  if (isAllActive.value || isUngroupedActive.value) return null
  return props.workspaces.find((w) => String(w.id) === String(props.selectedWorkspaceId)) || null
})

const headerSubtitle = computed(() => {
  if (isAllActive.value) return '全部课程'
  if (isUngroupedActive.value) return '未归类'
  return activeWorkspace.value?.name || '当前课程'
})

const activeTab = ref('library')

const categories = [
  { label: '全部', value: 'all' },
  { label: '课件', value: '课件' },
  { label: '教案', value: '教案' },
  { label: '习题', value: '习题' },
  { label: '参考资料', value: '参考资料' }
]

const searchKeywordModel = computed({
  get: () => props.searchKeyword,
  set: (value) => emit('update:searchKeyword', value)
})

const selectedDoc = computed(() => {
  return props.knowledgeDocs.find((doc) => String(doc.id) === String(props.selectedDocId)) || null
})

const stats = computed(() => {
  const total = props.knowledgeDocs.length
  const processing = props.knowledgeDocs.filter((doc) => doc.status === 0).length
  const ready = props.knowledgeDocs.filter((doc) => doc.status === 1).length
  const failed = props.knowledgeDocs.filter((doc) => doc.status === 2).length

  return { total, processing, ready, failed }
})

const visibleQueue = computed(() => props.uploadQueue.slice(0, 5))
</script>

<template>
  <div class="knowledge-view" :class="{ 'knowledge-view--visible': visible }">
    <div class="kb-shell">
      <header class="kb-header">
        <div class="kb-title">
          <p class="kb-eyebrow">当前页面</p>
          <h2>我的知识库</h2>
        </div>

        <div class="kb-actions">
          <button type="button" class="header-btn header-btn--primary" @click="emit('trigger-file-upload')">
            上传文件
          </button>
          <button type="button" class="header-btn" @click="emit('trigger-folder-import')">
            导入文件夹
          </button>
        </div>
      </header>

      <section class="kb-nav-row">
        <div class="kb-tabs" role="tablist" aria-label="知识工作台功能区">
          <button
            type="button"
            class="tab-btn"
            :class="{ active: activeTab === 'library' }"
            role="tab"
            :aria-selected="activeTab === 'library'"
            @click="activeTab = 'library'"
          >
            文件工作台
          </button>
          <button
            type="button"
            class="tab-btn"
            :class="{ active: activeTab === 'search-lab' }"
            role="tab"
            :aria-selected="activeTab === 'search-lab'"
            @click="activeTab = 'search-lab'"
          >
            检索实验室
          </button>
        </div>
        <p class="kb-nav-note">文件工作台负责管理与预览，检索实验室用于验证召回效果和上下文拼接。</p>
      </section>

      <Transition name="kb-panel-swap" mode="out-in">
        <section :key="activeTab" class="kb-tab-panel">
          <template v-if="activeTab === 'library'">
            <section class="stats-row">
              <article class="stat-card">
                <span class="stat-label">总文件</span>
                <strong>{{ stats.total }}</strong>
                <span class="stat-note">当前已纳入工作台的文件总数</span>
              </article>
              <article class="stat-card">
                <span class="stat-label">处理中</span>
                <strong>{{ stats.processing }}</strong>
                <span class="stat-note">仍在解析或生成分块</span>
              </article>
              <article class="stat-card">
                <span class="stat-label">已就绪</span>
                <strong>{{ stats.ready }}</strong>
                <span class="stat-note">可以直接用于引用与检索</span>
              </article>
              <article class="stat-card">
                <span class="stat-label">处理失败</span>
                <strong>{{ stats.failed }}</strong>
                <span class="stat-note">需要重试或重新上传</span>
              </article>
            </section>

            <section v-if="isUploading || visibleQueue.length" class="queue-panel">
              <div class="queue-panel-head">
                <div>
                  <h3>上传与处理状态</h3>
                  <p>把“文件传到哪一步、什么时候能被检索”变成一个持续可见的过程。</p>
                </div>
                <span v-if="isUploading" class="queue-progress-label">当前上传进度 {{ uploadProgress }}%</span>
              </div>

              <div v-if="isUploading" class="progress-track" aria-hidden="true">
                <div class="progress-bar" :style="{ width: `${uploadProgress}%` }"></div>
              </div>

              <div class="queue-list">
                <article v-for="item in visibleQueue" :key="item.id" class="queue-item">
                  <div class="queue-item-main">
                    <div class="queue-item-title">
                      <strong>{{ item.name }}</strong>
                      <span>{{ item.category }}</span>
                    </div>
                    <p class="queue-item-status">{{ item.statusText }}</p>
                    <p v-if="item.errorMessage" class="queue-item-error">{{ item.errorMessage }}</p>
                  </div>
                  <div class="queue-item-side">
                    <span>{{ item.progress || 0 }}%</span>
                    <span v-if="item.chunkCount">分块 {{ item.chunkCount }}</span>
                  </div>
                </article>
              </div>
            </section>

            <div class="workspace-grid">
              <aside class="workspace-sidebar" aria-label="课程空间侧栏">
                <div class="ws-sidebar-head">
                  <div>
                    <p class="panel-eyebrow">课程空间</p>
                    <h3>我的课程</h3>
                  </div>
                  <button type="button" class="ws-create-btn" @click="openCreateModal" title="新建课程空间">
                    + 新建
                  </button>
                </div>

                <ul class="ws-list" role="list">
                  <li>
                    <button
                      type="button"
                      class="ws-item ws-item--virtual"
                      :class="{ active: isAllActive }"
                      @click="selectAll"
                    >
                      <span class="ws-item-dot ws-item-dot--all"></span>
                      <span class="ws-item-name">全部课程</span>
                      <span class="ws-item-count">{{ workspaceCounts.total ?? 0 }}</span>
                    </button>
                  </li>
                  <li>
                    <button
                      type="button"
                      class="ws-item ws-item--virtual"
                      :class="{ active: isUngroupedActive }"
                      @click="selectUngrouped"
                    >
                      <span class="ws-item-dot ws-item-dot--ungrouped"></span>
                      <span class="ws-item-name">未归类</span>
                      <span class="ws-item-count">{{ workspaceCounts.ungrouped ?? 0 }}</span>
                    </button>
                  </li>

                  <li v-if="workspaces.length" class="ws-divider" aria-hidden="true"></li>

                  <li v-for="ws in workspaces" :key="ws.id">
                    <div
                      class="ws-item"
                      :class="{ active: String(selectedWorkspaceId) === String(ws.id) }"
                    >
                      <button
                        type="button"
                        class="ws-item-main"
                        @click="selectWorkspace(ws)"
                        :title="ws.description || ws.name"
                      >
                        <span
                          class="ws-item-dot"
                          :style="{ background: ws.coverColor || '#1f1d1a' }"
                        ></span>
                        <span v-if="renamingWorkspaceId !== ws.id" class="ws-item-name">{{ ws.name }}</span>
                        <input
                          v-else
                          v-model="renameValue"
                          class="ws-rename-input"
                          @click.stop
                          @keyup.enter="submitRename(ws)"
                          @keyup.escape="cancelRename"
                          @blur="submitRename(ws)"
                          autofocus
                        />
                        <span v-if="renamingWorkspaceId !== ws.id" class="ws-item-count">{{ ws.fileCount ?? 0 }}</span>
                      </button>
                      <div v-if="renamingWorkspaceId !== ws.id" class="ws-item-actions">
                        <button type="button" class="ws-action-btn" @click.stop="startRename(ws)" title="重命名">✎</button>
                        <button type="button" class="ws-action-btn ws-action-btn--danger" @click.stop="handleDeleteWorkspace(ws)" title="删除">×</button>
                      </div>
                    </div>
                  </li>

                  <li v-if="!workspaces.length && !workspacesLoading" class="ws-empty">
                    还没有课程空间，点击「新建」开始
                  </li>
                  <li v-if="workspacesLoading" class="ws-empty">加载中...</li>
                </ul>
              </aside>

              <section class="library-panel">
                <div class="panel-header">
                  <div>
                    <p class="panel-eyebrow">{{ headerSubtitle }}</p>
                    <h3>知识文件</h3>
                  </div>
                  <span class="panel-meta">{{ filteredDocs.length }} 项</span>
                </div>

                <div class="toolbar">
                  <div class="search-box">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="11" cy="11" r="8"></circle>
                      <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                    </svg>
                    <input v-model="searchKeywordModel" type="text" placeholder="搜索文件名、分类、格式或描述..." />
                  </div>

                  <div class="view-toggle">
                    <button
                      type="button"
                      class="view-btn"
                      :class="{ active: viewMode === 'grid' }"
                      :aria-pressed="viewMode === 'grid'"
                      @click="emit('update:viewMode', 'grid')"
                    >
                      网格
                    </button>
                    <button
                      type="button"
                      class="view-btn"
                      :class="{ active: viewMode === 'list' }"
                      :aria-pressed="viewMode === 'list'"
                      @click="emit('update:viewMode', 'list')"
                    >
                      列表
                    </button>
                  </div>
                </div>

                <div class="category-row" aria-label="文件分类筛选">
                  <button
                    v-for="category in categories"
                    :key="category.value"
                    type="button"
                    class="category-chip"
                    :class="{ active: currentCategory === category.value }"
                    :aria-pressed="currentCategory === category.value"
                    @click="emit('update:currentCategory', category.value)"
                  >
                    {{ category.label }}
                  </button>
                </div>

                <div v-if="knowledgeLoading" class="library-loading">
                  正在加载知识文件...
                </div>

                <KnowledgeFileList
                  v-else
                  :docs="filteredDocs"
                  :view-mode="viewMode"
                  :selected-doc-id="selectedDocId"
                  variant="page"
                  @select-doc="emit('select-doc', $event)"
                  @quote-doc="emit('quote-doc', $event)"
                  @retry-file="emit('retry-file', $event)"
                  @delete-file="emit('delete-file', $event)"
                />
              </section>

              <KnowledgeFileDetailPanel
                :user-id="userId"
                :selected-doc-id="selectedDocId"
                :selected-doc="selectedDoc"
              />
            </div>
          </template>

          <KnowledgeSearchLab v-else :user-id="userId" />
        </section>
      </Transition>
    </div>

    <Transition name="kb-modal">
      <div v-if="showCreateModal" class="ws-modal-overlay" @click.self="closeCreateModal">
        <div class="ws-modal" role="dialog" aria-modal="true" aria-label="新建课程空间">
          <header class="ws-modal-head">
            <h3>新建课程空间</h3>
            <p>用一个空间装一门课的资料，方便后续按课程检索。</p>
          </header>

          <label class="ws-modal-field">
            <span>课程名称</span>
            <input
              v-model="newWorkspaceName"
              type="text"
              maxlength="80"
              placeholder="如：C 语言程序设计 / 数据结构"
              @keyup.enter="submitCreateWorkspace"
            />
          </label>

          <label class="ws-modal-field">
            <span>简介（可选）</span>
            <textarea
              v-model="newWorkspaceDesc"
              rows="2"
              maxlength="300"
              placeholder="本课程涉及的范围、教材版本等"
            ></textarea>
          </label>

          <div class="ws-modal-field">
            <span>封面色</span>
            <div class="ws-color-row">
              <button
                v-for="color in COVER_PALETTE"
                :key="color"
                type="button"
                class="ws-color-dot"
                :class="{ active: newWorkspaceColor === color }"
                :style="{ background: color }"
                @click="newWorkspaceColor = color"
                aria-label="选择颜色"
              ></button>
            </div>
          </div>

          <footer class="ws-modal-foot">
            <button type="button" class="header-btn" @click="closeCreateModal">取消</button>
            <button
              type="button"
              class="header-btn header-btn--primary"
              :disabled="!newWorkspaceName.trim()"
              @click="submitCreateWorkspace"
            >
              创建
            </button>
          </footer>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.knowledge-view {
  min-height: 100%;
  flex: 1;
  overflow-x: hidden;
  opacity: 0;
  transform: translateY(8px);
  transition: opacity var(--kb-motion-base) ease-out, transform var(--kb-motion-slow) var(--kb-ease);
  color: var(--kb-text-primary);
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.96), rgba(255, 255, 255, 0) 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(247, 246, 243, 0.94));
}

.knowledge-view--visible {
  opacity: 1;
  transform: translateY(0);
}

.kb-shell {
  max-width: 1320px;
  margin: 0 auto;
  padding: 32px 36px 44px;
}

.kb-highlights,
.kb-actions,
.kb-tabs,
.stats-row,
.toolbar,
.view-toggle,
.category-row,
.queue-panel-head,
.queue-item,
.queue-item-title,
.queue-item-side,
.search-box,
.panel-header {
  display: flex;
  align-items: center;
}

.kb-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 32px;
  align-items: center;
  margin-bottom: 22px;
}

.kb-title {
  max-width: 820px;
}

.kb-eyebrow {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--kb-text-tertiary);
}

.kb-title h2 {
  margin: 0;
  font-size: 32px;
  line-height: 1.2;
  color: var(--kb-text-primary);
  letter-spacing: -0.03em;
}

.kb-actions {
  justify-content: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

.header-btn,
.tab-btn,
.view-btn,
.category-chip {
  border: 1px solid transparent;
  cursor: pointer;
  transition:
    background var(--kb-motion-fast) ease,
    color var(--kb-motion-fast) ease,
    border-color var(--kb-motion-fast) ease,
    box-shadow var(--kb-motion-fast) ease,
    transform var(--kb-motion-fast) ease;
}

.header-btn {
  min-height: 44px;
  padding: 12px 18px;
  border-radius: var(--kb-radius-sm);
  background: rgba(255, 255, 255, 0.82);
  border-color: var(--kb-border);
  color: var(--kb-text-primary);
  font-size: 14px;
  font-weight: 600;
  box-shadow: var(--kb-shadow-xs);
  backdrop-filter: blur(10px);
}

.header-btn--primary {
  background: var(--kb-link);
  border-color: var(--kb-link);
  color: var(--kb-link-contrast);
}

.header-btn--primary:hover {
  background: var(--kb-link-hover);
  border-color: var(--kb-link-hover);
}

.header-btn:hover,
.view-btn:hover,
.category-chip:hover,
.tab-btn:hover {
  transform: translateY(-1px);
}

.kb-nav-row {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: center;
  margin-bottom: 24px;
}

.kb-tabs {
  gap: 8px;
  padding: 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid var(--kb-border);
  box-shadow: var(--kb-shadow-xs);
  backdrop-filter: blur(10px);
}

.tab-btn {
  min-height: 44px;
  padding: 10px 16px;
  border-radius: 999px;
  background: transparent;
  color: var(--kb-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.tab-btn.active {
  background: var(--kb-link);
  color: var(--kb-link-contrast);
  box-shadow: var(--kb-shadow-xs);
}

.kb-nav-note {
  font-size: 13px;
  line-height: 1.7;
  color: var(--kb-text-tertiary);
  text-align: right;
  max-width: 440px;
}

.kb-tab-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card,
.queue-panel,
.library-panel,
.library-loading {
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: var(--kb-shadow-sm);
  backdrop-filter: blur(10px);
}

.stat-card {
  min-width: 0;
  padding: 18px 20px;
}

.stat-label {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--kb-text-tertiary);
}

.stat-card strong {
  display: block;
  margin-bottom: 8px;
  font-size: 30px;
  color: var(--kb-text-primary);
  letter-spacing: -0.04em;
}

.stat-note {
  display: block;
  font-size: 12px;
  line-height: 1.7;
  color: var(--kb-text-secondary);
}

.queue-panel {
  padding: 20px 22px;
}

.queue-panel-head {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.queue-panel-head h3 {
  font-size: 18px;
  color: var(--kb-text-primary);
  margin-bottom: 6px;
}

.queue-panel-head p {
  font-size: 13px;
  line-height: 1.7;
  color: var(--kb-text-secondary);
}

.queue-progress-label {
  flex-shrink: 0;
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--kb-link-soft);
  color: var(--kb-link-hover);
  font-size: 12px;
  font-weight: 700;
}

.progress-track {
  height: 8px;
  border-radius: 999px;
  background: rgba(23, 22, 20, 0.08);
  overflow: hidden;
  margin-bottom: 14px;
}

.progress-bar {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--kb-link), var(--kb-link-hover));
  transition: width var(--kb-motion-base) var(--kb-ease);
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.queue-item {
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-surface-soft);
  border: 1px solid rgba(23, 22, 20, 0.04);
  transition: transform var(--kb-motion-fast) ease, box-shadow var(--kb-motion-fast) ease;
}

.queue-item:hover {
  transform: translateY(-1px);
  box-shadow: var(--kb-shadow-xs);
}

.queue-item-main {
  min-width: 0;
}

.queue-item-title {
  gap: 8px;
  margin-bottom: 6px;
}

.queue-item-title strong {
  max-width: 520px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  color: var(--kb-text-primary);
}

.queue-item-title span,
.queue-item-side span,
.queue-item-status {
  font-size: 12px;
  color: var(--kb-text-secondary);
}

.queue-item-side {
  gap: 8px;
  flex-shrink: 0;
  flex-direction: column;
  align-items: flex-end;
}

.queue-item-error {
  margin-top: 4px;
  font-size: 12px;
  color: var(--kb-danger-text);
}

.workspace-grid {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr) 360px;
  gap: 20px;
  align-items: start;
}

.workspace-sidebar {
  position: sticky;
  top: 12px;
  align-self: start;
  padding: 18px 16px 16px;
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: var(--kb-shadow-sm);
  backdrop-filter: blur(10px);
}

.ws-sidebar-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.ws-sidebar-head h3 {
  margin: 0;
  font-size: 16px;
  letter-spacing: -0.02em;
  color: var(--kb-text-primary);
}

.ws-create-btn {
  flex-shrink: 0;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid var(--kb-link);
  background: var(--kb-link);
  color: var(--kb-link-contrast);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: background var(--kb-motion-fast) ease, transform var(--kb-motion-fast) ease, box-shadow var(--kb-motion-fast) ease;
}

.ws-create-btn:hover {
  background: var(--kb-link-hover);
  border-color: var(--kb-link-hover);
  transform: translateY(-1px);
  box-shadow: var(--kb-shadow-xs);
}

.ws-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ws-divider {
  height: 1px;
  background: var(--kb-border);
  margin: 8px 4px;
}

.ws-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 38px;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: var(--kb-radius-sm);
  background: transparent;
  color: var(--kb-text-secondary);
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: background var(--kb-motion-fast) ease, border-color var(--kb-motion-fast) ease, color var(--kb-motion-fast) ease;
}

button.ws-item {
  font-family: inherit;
}

.ws-item:hover {
  background: var(--kb-surface-soft);
  color: var(--kb-text-primary);
}

.ws-item.active {
  background: var(--kb-link-soft);
  border-color: var(--kb-link);
  color: var(--kb-link-hover);
}

.ws-item-main {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  border: none;
  background: transparent;
  padding: 0;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.ws-item-actions {
  display: none;
  gap: 4px;
  flex-shrink: 0;
}

.ws-item:hover .ws-item-actions,
.ws-item.active .ws-item-actions {
  display: flex;
}

.ws-action-btn {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--kb-border);
  border-radius: 6px;
  background: var(--kb-surface);
  color: var(--kb-text-secondary);
  font-size: 12px;
  cursor: pointer;
}

.ws-action-btn:hover {
  background: var(--kb-surface-muted);
  color: var(--kb-text-primary);
}

.ws-action-btn--danger:hover {
  background: var(--kb-danger-bg);
  color: var(--kb-danger-text);
}

.ws-item-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--kb-text-secondary);
  flex-shrink: 0;
}

.ws-item-dot--all {
  background: linear-gradient(135deg, #1f1d1a, #5a4632);
}

.ws-item-dot--ungrouped {
  background: transparent;
  border: 2px dashed var(--kb-text-tertiary);
}

.ws-item-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ws-item-count {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--kb-surface-muted);
  border: 1px solid var(--kb-border);
  color: var(--kb-text-secondary);
  font-size: 11px;
  font-weight: 700;
}

.ws-item.active .ws-item-count {
  background: rgba(255, 255, 255, 0.7);
}

.ws-rename-input {
  flex: 1;
  min-width: 0;
  padding: 4px 6px;
  border-radius: 6px;
  border: 1px solid var(--kb-link);
  background: var(--kb-surface);
  color: var(--kb-text-primary);
  font: inherit;
  outline: none;
  box-shadow: 0 0 0 3px var(--kb-link-soft);
}

.ws-empty {
  padding: 14px 8px;
  font-size: 12px;
  color: var(--kb-text-tertiary);
  text-align: center;
}

.ws-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(23, 22, 20, 0.42);
  backdrop-filter: blur(6px);
}

.ws-modal {
  width: min(440px, 100%);
  background: var(--kb-surface);
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  box-shadow: var(--kb-shadow-md);
  padding: 24px 24px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ws-modal-head h3 {
  margin: 0 0 6px;
  font-size: 18px;
  color: var(--kb-text-primary);
}

.ws-modal-head p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--kb-text-secondary);
}

.ws-modal-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--kb-text-secondary);
}

.ws-modal-field input,
.ws-modal-field textarea {
  padding: 10px 12px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-surface-soft);
  color: var(--kb-text-primary);
  font-size: 14px;
  font-weight: 500;
  outline: none;
  transition: border-color var(--kb-motion-fast) ease, box-shadow var(--kb-motion-fast) ease;
}

.ws-modal-field input:focus,
.ws-modal-field textarea:focus {
  border-color: var(--kb-link);
  box-shadow: 0 0 0 4px var(--kb-link-soft);
}

.ws-modal-field textarea {
  resize: vertical;
}

.ws-color-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ws-color-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  transition: transform var(--kb-motion-fast) ease, border-color var(--kb-motion-fast) ease;
}

.ws-color-dot:hover {
  transform: scale(1.05);
}

.ws-color-dot.active {
  border-color: var(--kb-link);
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.9), 0 0 0 4px var(--kb-link);
}

.ws-modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 6px;
}

.ws-modal-foot .header-btn[disabled] {
  opacity: 0.5;
  cursor: not-allowed;
}

.kb-modal-enter-active,
.kb-modal-leave-active {
  transition: opacity var(--kb-motion-base) ease;
}

.kb-modal-enter-from,
.kb-modal-leave-to {
  opacity: 0;
}

.library-panel {
  padding: 20px;
}

.panel-header {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.panel-eyebrow {
  margin-bottom: 8px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--kb-text-tertiary);
}

.panel-header h3 {
  font-size: 20px;
  letter-spacing: -0.02em;
  color: var(--kb-text-primary);
}

.panel-meta {
  flex-shrink: 0;
  padding: 7px 10px;
  border-radius: 999px;
  background: var(--kb-surface-muted);
  border: 1px solid var(--kb-border);
  color: var(--kb-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.toolbar {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.search-box {
  flex: 1;
  gap: 10px;
  min-height: 48px;
  padding: 12px 14px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-surface-soft);
  transition: border-color var(--kb-motion-fast) ease, box-shadow var(--kb-motion-fast) ease, background var(--kb-motion-fast) ease;
}

.search-box:focus-within {
  border-color: var(--kb-link);
  box-shadow: 0 0 0 4px var(--kb-link-soft);
  background: var(--kb-surface);
}

.search-box input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: var(--kb-text-primary);
}

.search-box input::placeholder {
  color: var(--kb-text-tertiary);
}

.view-toggle {
  gap: 8px;
  flex-shrink: 0;
}

.view-btn {
  min-height: 44px;
  padding: 10px 14px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-surface-soft);
  border-color: var(--kb-border);
  color: var(--kb-text-secondary);
  font-size: 13px;
  font-weight: 700;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.75);
}

.view-btn.active {
  background: var(--kb-surface);
  color: var(--kb-text-primary);
  border-color: var(--kb-border-strong);
  box-shadow: var(--kb-shadow-xs);
}

.category-row {
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}

.category-chip {
  min-height: 40px;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.76);
  border-color: var(--kb-border);
  color: var(--kb-text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.category-chip.active {
  background: var(--kb-link-soft);
  color: var(--kb-link-hover);
  border-color: var(--kb-link);
}

.library-loading {
  padding: 36px 24px;
  font-size: 14px;
  color: var(--kb-text-secondary);
}

.kb-panel-swap-enter-active,
.kb-panel-swap-leave-active {
  transition: opacity var(--kb-motion-base) ease, transform var(--kb-motion-base) var(--kb-ease);
}

.kb-panel-swap-enter-from,
.kb-panel-swap-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

@media (max-width: 1320px) {
  .workspace-grid {
    grid-template-columns: 220px minmax(0, 1fr);
  }
  .workspace-grid > :nth-child(3) {
    grid-column: 1 / -1;
  }
}

@media (max-width: 1120px) {
  .kb-header,
  .kb-nav-row,
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .workspace-sidebar {
    position: static;
  }

  .kb-header,
  .kb-nav-row {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }

  .kb-nav-note {
    max-width: none;
    text-align: left;
  }

  .stats-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .kb-shell {
    padding: 24px 18px 32px;
  }

  .kb-title h2 {
    font-size: 28px;
  }

  .kb-header,
  .toolbar,
  .queue-panel-head,
  .queue-item,
  .panel-header {
    flex-direction: column;
    align-items: stretch;
  }

  .stats-row {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .kb-tabs {
    width: 100%;
    justify-content: space-between;
  }

  .tab-btn {
    flex: 1;
  }

  .header-btn {
    width: 100%;
  }

  .queue-item {
    gap: 10px;
  }

  .queue-item-side {
    align-items: flex-start;
  }

  .toolbar {
    gap: 12px;
  }

  .view-toggle {
    width: 100%;
  }

  .view-btn {
    flex: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .knowledge-view,
  .header-btn,
  .tab-btn,
  .view-btn,
  .category-chip,
  .queue-item,
  .search-box,
  .kb-panel-swap-enter-active,
  .kb-panel-swap-leave-active {
    transition: none;
  }
}
</style>
