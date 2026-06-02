<script setup>
import { computed, ref, watch } from 'vue'
import { getFilePreview } from '@/api'

const props = defineProps({
  userId: {
    type: [Number, String],
    default: null
  },
  selectedDocId: {
    type: [Number, String],
    default: null
  },
  selectedDoc: {
    type: Object,
    default: null
  }
})

const loading = ref(false)
const loadError = ref('')
const preview = ref(null)

const statusClass = computed(() => `detail-status--${props.selectedDoc?.status ?? 0}`)

const previewSummary = computed(() => {
  if (!preview.value?.contentLength) {
    return '暂无文本预览'
  }

  return `${preview.value.contentLength} 字${preview.value.truncated ? '，已截断展示' : ''}`
})

const loadPreview = async () => {
  if (!props.userId || !props.selectedDocId) {
    preview.value = null
    loadError.value = ''
    return
  }

  loading.value = true
  loadError.value = ''

  try {
    const res = await getFilePreview(props.selectedDocId, props.userId, 8)
    preview.value = res.data
  } catch (error) {
    console.error('加载知识文件预览失败:', error)
    loadError.value = error.message || '加载预览失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.userId, props.selectedDocId, props.selectedDoc?.status, props.selectedDoc?.chunkCount],
  loadPreview,
  { immediate: true }
)
</script>

<template>
  <aside class="detail-panel" aria-labelledby="knowledge-detail-title">
    <div v-if="!selectedDocId" class="detail-empty">
      <h3>文件详情</h3>
      <p>从左侧选择一个文件后，这里会展示文件属性、抽取文本预览，以及系统切分后的 chunk 摘要。</p>
    </div>

    <template v-else>
      <header class="detail-header">
        <div class="detail-title-group">
          <p class="detail-eyebrow">文件详情</p>
          <h3 id="knowledge-detail-title" class="detail-title">{{ selectedDoc?.name || '已选文件' }}</h3>
        </div>
        <span class="detail-status" :class="statusClass">
          {{ selectedDoc?.statusText || '处理中' }}
        </span>
      </header>

      <section class="detail-metas">
        <article class="detail-meta-card">
          <span class="meta-label">分类</span>
          <span class="meta-value">{{ selectedDoc?.category || '未分类' }}</span>
        </article>
        <article class="detail-meta-card">
          <span class="meta-label">格式</span>
          <span class="meta-value">{{ selectedDoc?.fileTypeLabel || '文件' }}</span>
        </article>
        <article class="detail-meta-card">
          <span class="meta-label">大小</span>
          <span class="meta-value">{{ selectedDoc?.size || '--' }}</span>
        </article>
        <article class="detail-meta-card">
          <span class="meta-label">分块数</span>
          <span class="meta-value">{{ selectedDoc?.chunkCount ?? 0 }}</span>
        </article>
      </section>

      <div v-if="selectedDoc?.errorMessage" class="detail-alert detail-alert--danger" role="alert">
        <strong>处理失败</strong>
        <p>{{ selectedDoc.errorMessage }}</p>
      </div>

      <div v-if="loading" class="detail-loading" role="status" aria-live="polite">
        <div class="loading-bar"></div>
        <p>正在读取文件内容和 chunk 预览...</p>
      </div>

      <div v-else-if="loadError" class="detail-alert detail-alert--danger" role="alert">
        <strong>预览加载失败</strong>
        <p>{{ loadError }}</p>
      </div>

      <template v-else>
        <section class="detail-section">
          <div class="section-head">
            <h4>文本预览</h4>
            <span class="section-note">{{ previewSummary }}</span>
          </div>
          <div class="preview-box">
            {{ preview?.contentPreview || '当前没有可展示的文本内容。如果文件仍在处理中，稍后刷新即可看到更新。' }}
          </div>
        </section>

        <section class="detail-section">
          <div class="section-head">
            <h4>Chunk 摘要</h4>
            <span class="section-note">{{ preview?.chunks?.length || 0 }} 个片段</span>
          </div>

          <div v-if="preview?.chunks?.length" class="chunk-list">
            <article v-for="chunk in preview.chunks" :key="chunk.chunkId" class="chunk-card">
              <div class="chunk-head">
                <span>Chunk {{ (chunk.chunkIndex ?? 0) + 1 }}</span>
                <span>{{ chunk.tokenCount || 0 }} tokens</span>
              </div>
              <p class="chunk-text">{{ chunk.text }}</p>
            </article>
          </div>
          <div v-else class="detail-empty-sub">
            暂无分块数据。文件尚未处理完成时，这里会为空。
          </div>
        </section>
      </template>
    </template>
  </aside>
</template>

<style scoped>
.detail-panel {
  width: 360px;
  flex-shrink: 0;
  min-height: 640px;
  position: sticky;
  top: 32px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  background: rgba(255, 255, 255, 0.84);
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  box-shadow: var(--kb-shadow-sm);
  backdrop-filter: blur(10px);
}

.detail-empty,
.detail-empty-sub {
  color: var(--kb-text-secondary);
}

.detail-empty h3 {
  font-size: 20px;
  color: var(--kb-text-primary);
  margin-bottom: 10px;
}

.detail-empty p,
.detail-empty-sub {
  line-height: 1.8;
  font-size: 14px;
}

.detail-header,
.section-head,
.chunk-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.detail-title-group {
  min-width: 0;
}

.detail-eyebrow {
  margin-bottom: 8px;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--kb-text-tertiary);
  font-weight: 700;
}

.detail-title {
  font-size: 20px;
  line-height: 1.6;
  color: var(--kb-text-primary);
  word-break: break-word;
  letter-spacing: -0.02em;
}

.detail-status {
  flex-shrink: 0;
  align-self: flex-start;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid transparent;
}

.detail-status--0 {
  background: var(--kb-warning-bg);
  color: var(--kb-warning-text);
  border-color: rgba(138, 101, 49, 0.12);
}

.detail-status--1 {
  background: var(--kb-success-bg);
  color: var(--kb-success-text);
  border-color: rgba(68, 98, 76, 0.12);
}

.detail-status--2 {
  background: var(--kb-danger-bg);
  color: var(--kb-danger-text);
  border-color: rgba(142, 78, 70, 0.12);
}

.detail-metas {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-meta-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-surface-soft);
  border: 1px solid rgba(23, 22, 20, 0.04);
}

.meta-label {
  font-size: 12px;
  color: var(--kb-text-tertiary);
}

.meta-value {
  font-size: 14px;
  font-weight: 700;
  color: var(--kb-text-primary);
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-head {
  align-items: center;
}

.section-head h4 {
  font-size: 15px;
  color: var(--kb-text-primary);
}

.section-note {
  font-size: 12px;
  color: var(--kb-text-tertiary);
}

.preview-box,
.chunk-card {
  border-radius: var(--kb-radius-md);
  background: var(--kb-surface-soft);
}

.preview-box {
  padding: 15px 16px;
  border: 1px solid var(--kb-border);
  color: var(--kb-text-secondary);
  font-size: 13px;
  line-height: 1.9;
  white-space: pre-wrap;
  max-height: 220px;
  overflow-y: auto;
}

.chunk-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 320px;
  overflow-y: auto;
}

.chunk-card {
  padding: 14px 16px;
  border: 1px solid var(--kb-border);
}

.chunk-head {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--kb-text-tertiary);
}

.chunk-text {
  color: var(--kb-text-secondary);
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.detail-alert {
  padding: 14px 16px;
  border-radius: var(--kb-radius-md);
  font-size: 13px;
  line-height: 1.8;
}

.detail-alert strong {
  display: block;
  margin-bottom: 4px;
}

.detail-alert--danger {
  border: 1px solid rgba(142, 78, 70, 0.16);
  background: var(--kb-danger-bg);
  color: var(--kb-danger-text);
}

.detail-loading {
  border-radius: var(--kb-radius-md);
  background: var(--kb-surface-soft);
  padding: 16px;
  border: 1px solid rgba(23, 22, 20, 0.04);
}

.loading-bar {
  width: 100%;
  height: 8px;
  border-radius: 999px;
  margin-bottom: 10px;
  background: linear-gradient(90deg, rgba(23, 22, 20, 0.06) 0%, rgba(23, 22, 20, 0.18) 50%, rgba(23, 22, 20, 0.06) 100%);
  background-size: 200% 100%;
  animation: loading-slide 1.2s linear infinite;
}

.detail-loading p {
  font-size: 13px;
  color: var(--kb-text-secondary);
}

@keyframes loading-slide {
  from {
    background-position: 200% 0;
  }

  to {
    background-position: -200% 0;
  }
}

@media (max-width: 1120px) {
  .detail-panel {
    width: 100%;
    min-height: 0;
    position: static;
  }
}

@media (prefers-reduced-motion: reduce) {
  .loading-bar {
    animation: none;
  }
}
</style>
