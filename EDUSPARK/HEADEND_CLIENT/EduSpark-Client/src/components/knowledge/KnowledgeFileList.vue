<script setup>
import { computed } from 'vue'

const props = defineProps({
  docs: {
    type: Array,
    default: () => []
  },
  viewMode: {
    type: String,
    default: 'grid'
  },
  selectedDocId: {
    type: [String, Number],
    default: null
  },
  variant: {
    type: String,
    default: 'page'
  }
})

const emit = defineEmits(['select-doc', 'quote-doc', 'retry-file', 'delete-file'])

const isPageVariant = computed(() => props.variant === 'page')

const categoryMetaMap = {
  课件: {
    tone: 'courseware',
    label: '课件'
  },
  教案: {
    tone: 'lesson-plan',
    label: '教案'
  },
  习题: {
    tone: 'exercise',
    label: '习题'
  },
  参考资料: {
    tone: 'reference',
    label: '参考资料'
  }
}

const isSelected = (doc) => String(props.selectedDocId) === String(doc.id)

const getCategoryMeta = (doc) => categoryMetaMap[doc.category] || {
  tone: 'default',
  label: doc.category || '文件'
}

const getStatusLabel = (doc) => {
  if (doc.statusText) {
    return doc.statusText
  }

  if (doc.status === 0) {
    return '处理中'
  }

  if (doc.status === 1) {
    return '已就绪'
  }

  if (doc.status === 2) {
    return '处理失败'
  }

  return '未知状态'
}

const getStatusTone = (doc) => {
  if (doc.status === 1) {
    return 'success'
  }

  if (doc.status === 2) {
    return 'danger'
  }

  return 'warning'
}

const selectDoc = (doc) => emit('select-doc', doc)
const quoteDoc = (doc) => emit('quote-doc', doc)
const retryFile = (doc) => emit('retry-file', doc)
const deleteFile = (doc) => emit('delete-file', doc)
const handleSelectableKeydown = (event, doc) => {
  if (event.target !== event.currentTarget) {
    return
  }

  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    selectDoc(doc)
  }
}
</script>

<template>
  <div v-if="!docs.length" class="empty-state">
    <div class="empty-icon">
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
        <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path>
        <polyline points="13 2 13 9 20 9"></polyline>
      </svg>
    </div>
    <h4>当前没有匹配的知识文件</h4>
    <p>可以先上传文件，或者调整筛选条件后再查看。</p>
  </div>

  <div v-else-if="viewMode === 'grid'" class="doc-grid" :class="{ 'doc-grid--drawer': !isPageVariant }">
    <article
      v-for="doc in docs"
      :key="doc.id"
      class="doc-card"
      :class="[
        `doc-card--${getCategoryMeta(doc).tone}`,
        `doc-card--${getStatusTone(doc)}`,
        { selected: isSelected(doc), 'doc-card--drawer': !isPageVariant }
      ]"
      role="button"
      tabindex="0"
      :aria-pressed="isSelected(doc)"
      :aria-label="`查看文件 ${doc.name}`"
      @click="selectDoc(doc)"
      @keydown="handleSelectableKeydown($event, doc)"
    >
      <header class="doc-card-header">
        <div class="doc-icon" :class="`doc-icon--${getCategoryMeta(doc).tone}`">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path>
            <polyline points="13 2 13 9 20 9"></polyline>
          </svg>
        </div>
        <span class="status-badge" :class="`status-badge--${getStatusTone(doc)}`">
          {{ getStatusLabel(doc) }}
        </span>
      </header>

      <div class="doc-card-body">
        <h4 class="doc-title" :title="doc.name">{{ doc.name }}</h4>
        <div class="doc-meta">
          <span>{{ getCategoryMeta(doc).label }}</span>
          <span>{{ doc.fileTypeLabel || '文件' }}</span>
          <span>{{ doc.size || '--' }}</span>
        </div>
        <p v-if="isPageVariant" class="doc-preview">
          {{ doc.preview || '暂无文件描述，可在详情面板中查看提取后的内容预览。' }}
        </p>
        <div class="doc-tags">
          <span class="doc-tag">{{ getCategoryMeta(doc).label }}</span>
          <span v-if="doc.chunkCount" class="doc-tag doc-tag--soft">{{ doc.chunkCount }} 个分块</span>
        </div>
      </div>

      <footer class="doc-actions">
        <button type="button" class="action-btn action-btn--primary" @click.stop="quoteDoc(doc)">
          引用
        </button>
        <button
          v-if="doc.status === 2"
          type="button"
          class="action-btn action-btn--warning"
          @click.stop="retryFile(doc)"
        >
          重试
        </button>
        <button
          v-if="isPageVariant"
          type="button"
          class="action-btn action-btn--danger"
          @click.stop="deleteFile(doc)"
        >
          删除
        </button>
      </footer>
    </article>
  </div>

  <div v-else class="doc-list">
    <article
      v-for="doc in docs"
      :key="doc.id"
      class="doc-list-item"
      :class="[
        `doc-list-item--${getStatusTone(doc)}`,
        { selected: isSelected(doc), 'doc-list-item--drawer': !isPageVariant }
      ]"
      role="button"
      tabindex="0"
      :aria-pressed="isSelected(doc)"
      :aria-label="`查看文件 ${doc.name}`"
      @click="selectDoc(doc)"
      @keydown="handleSelectableKeydown($event, doc)"
    >
      <div class="doc-list-main">
        <div class="doc-icon" :class="`doc-icon--${getCategoryMeta(doc).tone}`">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path>
            <polyline points="13 2 13 9 20 9"></polyline>
          </svg>
        </div>

        <div class="doc-list-info">
          <h4 class="doc-title" :title="doc.name">{{ doc.name }}</h4>
          <div class="doc-meta">
            <span>{{ getCategoryMeta(doc).label }}</span>
            <span>{{ doc.fileTypeLabel || '文件' }}</span>
            <span>{{ doc.date || '--' }}</span>
            <span>{{ doc.size || '--' }}</span>
          </div>
        </div>
      </div>

      <div class="doc-list-side">
        <span class="status-badge" :class="`status-badge--${getStatusTone(doc)}`">
          {{ getStatusLabel(doc) }}
        </span>
        <div class="doc-actions">
          <button type="button" class="action-btn action-btn--primary" @click.stop="quoteDoc(doc)">
            引用
          </button>
          <button
            v-if="doc.status === 2"
            type="button"
            class="action-btn action-btn--warning"
            @click.stop="retryFile(doc)"
          >
            重试
          </button>
          <button
            v-if="isPageVariant"
            type="button"
            class="action-btn action-btn--danger"
            @click.stop="deleteFile(doc)"
          >
            删除
          </button>
        </div>
      </div>
    </article>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 28px;
  border: 1px dashed var(--kb-border-strong);
  border-radius: var(--kb-radius-lg);
  background: rgba(255, 255, 255, 0.76);
  color: var(--kb-text-secondary);
  text-align: center;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
}

.empty-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--kb-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  background: var(--kb-surface-muted);
  border: 1px solid var(--kb-border);
  color: var(--kb-text-secondary);
}

.empty-state h4 {
  margin-bottom: 10px;
  font-size: 18px;
  color: var(--kb-text-primary);
}

.empty-state p {
  max-width: 360px;
  line-height: 1.8;
  font-size: 14px;
}

.doc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.doc-grid--drawer {
  grid-template-columns: 1fr;
  gap: 12px;
}

.doc-card,
.doc-list-item {
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--kb-shadow-xs);
  transition:
    border-color var(--kb-motion-fast) ease,
    box-shadow var(--kb-motion-fast) ease,
    transform var(--kb-motion-fast) ease,
    background var(--kb-motion-fast) ease;
}

.doc-card {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  cursor: pointer;
  min-height: 252px;
  outline: none;
}

.doc-card--drawer {
  min-height: 0;
}

.doc-card:hover,
.doc-card.selected,
.doc-card:focus-visible,
.doc-list-item:hover,
.doc-list-item.selected,
.doc-list-item:focus-visible {
  border-color: var(--kb-border-strong);
  box-shadow: var(--kb-shadow-sm);
  transform: translateY(-2px);
  background: rgba(255, 255, 255, 0.94);
}

.doc-card-header,
.doc-list-main,
.doc-list-side,
.doc-meta,
.doc-actions {
  display: flex;
  align-items: center;
}

.doc-card-header,
.doc-list-item {
  justify-content: space-between;
}

.doc-list-item {
  gap: 16px;
  padding: 16px 18px;
  cursor: pointer;
  outline: none;
}

.doc-list-item--drawer {
  padding: 12px 14px;
}

.doc-list-main {
  gap: 14px;
  min-width: 0;
  flex: 1;
}

.doc-list-side {
  gap: 12px;
  flex-shrink: 0;
}

.doc-icon {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: var(--kb-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--kb-surface-muted);
  border: 1px solid var(--kb-border);
  color: var(--kb-text-secondary);
}

.doc-card-body,
.doc-list-info {
  min-width: 0;
}

.doc-title {
  font-size: 15px;
  line-height: 1.55;
  color: var(--kb-text-primary);
  font-weight: 600;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.doc-meta {
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--kb-text-tertiary);
  margin-top: 10px;
}

.doc-preview {
  margin-top: 10px;
  font-size: 13px;
  color: var(--kb-text-secondary);
  line-height: 1.75;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 70px;
}

.doc-tags {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.doc-tag {
  padding: 5px 10px;
  border-radius: 999px;
  background: var(--kb-surface-muted);
  border: 1px solid var(--kb-border);
  color: var(--kb-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.doc-tag--soft {
  background: rgba(255, 255, 255, 0.7);
  color: var(--kb-text-tertiary);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  border: 1px solid transparent;
}

.status-badge--warning {
  background: var(--kb-warning-bg);
  color: var(--kb-warning-text);
  border-color: rgba(138, 101, 49, 0.12);
}

.status-badge--success {
  background: var(--kb-success-bg);
  color: var(--kb-success-text);
  border-color: rgba(68, 98, 76, 0.12);
}

.status-badge--danger {
  background: var(--kb-danger-bg);
  color: var(--kb-danger-text);
  border-color: rgba(142, 78, 70, 0.12);
}

.doc-actions {
  gap: 8px;
  flex-wrap: wrap;
  margin-top: auto;
}

.action-btn {
  min-height: 36px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background var(--kb-motion-fast) ease,
    color var(--kb-motion-fast) ease,
    border-color var(--kb-motion-fast) ease,
    transform var(--kb-motion-fast) ease;
}

.action-btn--primary {
  background: var(--kb-accent);
  border-color: var(--kb-accent);
  color: var(--kb-accent-contrast);
}

.action-btn--primary:hover {
  transform: translateY(-1px);
}

.action-btn--warning {
  background: rgba(255, 255, 255, 0.8);
  color: var(--kb-warning-text);
}

.action-btn--warning:hover {
  border-color: rgba(138, 101, 49, 0.22);
  background: var(--kb-warning-bg);
}

.action-btn--danger {
  background: rgba(255, 255, 255, 0.8);
  color: var(--kb-danger-text);
}

.action-btn--danger:hover {
  border-color: rgba(142, 78, 70, 0.22);
  background: var(--kb-danger-bg);
}

.doc-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

@media (max-width: 960px) {
  .doc-grid {
    grid-template-columns: 1fr;
  }

  .doc-list-item {
    flex-direction: column;
    align-items: stretch;
  }

  .doc-list-side {
    justify-content: space-between;
  }
}

@media (prefers-reduced-motion: reduce) {
  .doc-card,
  .doc-list-item,
  .action-btn {
    transition: none;
  }
}
</style>
