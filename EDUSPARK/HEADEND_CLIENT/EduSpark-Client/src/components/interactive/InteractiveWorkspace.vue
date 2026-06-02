<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  document: { type: Object, default: () => null },
  content: { type: String, default: '' },
  streamText: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  exporting: { type: Boolean, default: false },
  dirty: { type: Boolean, default: false },
  streamConnected: { type: Boolean, default: false },
  streamError: { type: String, default: '' },
  streamState: { type: String, default: 'idle' },
  downloadUrl: { type: String, default: '' }
})

const emit = defineEmits(['close', 'save', 'export', 'update:content'])

const PREVIEW_MIN_HEIGHT = 420
const PREVIEW_MAX_HEIGHT = 820
const PREVIEW_HEIGHT_MESSAGE = 'eduspark:interactive-preview-height'
const PREVIEW_SCRIPT_CLOSE_TAG = '</scr' + 'ipt>'

const EMPTY_PREVIEW_HTML = `
  <!DOCTYPE html>
  <html lang="zh-CN">
    <head>
      <meta charset="UTF-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1.0" />
      <style>
        body {
          margin: 0;
          display: grid;
          place-items: center;
          background: #f8fafc;
          color: #334155;
          font-family: "Microsoft YaHei", sans-serif;
          padding: 32px 16px;
        }
        .empty {
          width: min(420px, calc(100vw - 48px));
          padding: 28px;
          border-radius: 18px;
          border: 1px solid #e2e8f0;
          background: #ffffff;
          box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
        }
        h1 {
          margin: 0 0 8px;
          font-size: 20px;
          color: #0f172a;
        }
        p {
          margin: 0;
          line-height: 1.7;
        }
      </style>
    </head>
    <body>
      <div class="empty">
        <h1>工作区准备中</h1>
        <p>系统正在生成互动页面，完成后会直接在这里显示运行效果。</p>
      </div>
    </body>
  </html>
`

const PREVIEW_BRIDGE_MARKUP = `
  <script>
    (() => {
      const MESSAGE_TYPE = '${PREVIEW_HEIGHT_MESSAGE}'
      let scheduled = false

      const postHeight = () => {
        const body = document.body
        const root = document.documentElement
        const height = Math.max(
          body ? body.scrollHeight : 0,
          body ? body.offsetHeight : 0,
          root ? root.scrollHeight : 0,
          root ? root.offsetHeight : 0
        )

        window.parent.postMessage({ type: MESSAGE_TYPE, height }, '*')
      }

      const scheduleMeasure = () => {
        if (scheduled) return
        scheduled = true
        window.requestAnimationFrame(() => {
          scheduled = false
          postHeight()
        })
      }

      if (typeof ResizeObserver !== 'undefined') {
        const resizeObserver = new ResizeObserver(scheduleMeasure)
        if (document.documentElement) {
          resizeObserver.observe(document.documentElement)
        }
        if (document.body) {
          resizeObserver.observe(document.body)
        }
      }

      const mutationObserver = new MutationObserver(scheduleMeasure)
      if (document.documentElement) {
        mutationObserver.observe(document.documentElement, {
          subtree: true,
          childList: true,
          characterData: true,
          attributes: true
        })
      }

      window.addEventListener('load', scheduleMeasure)
      window.addEventListener('resize', scheduleMeasure)
      document.addEventListener('readystatechange', scheduleMeasure)
      document.fonts?.ready?.then(scheduleMeasure).catch(() => {})

      scheduleMeasure()
      window.setTimeout(scheduleMeasure, 120)
      window.setTimeout(scheduleMeasure, 360)
    })()
  ${PREVIEW_SCRIPT_CLOSE_TAG}
`

const activeView = ref('preview')
const previewFrameRef = ref(null)
const previewFrameHeight = ref(560)

const isCompleted = computed(() => props.document?.status === 'completed')
const title = computed(() => props.document?.title || '互动页面')
const statusText = computed(() => {
  if (props.streamError) return props.streamError
  if (props.saving) return '保存中...'
  if (isCompleted.value) return props.dirty ? '有未保存修改' : '已完成'
  if (props.streamState === 'connecting') return '正在连接生成通道'
  if (props.streamState === 'reconnecting') return '连接中断，正在重连'
  if (props.streamState === 'recovered') return '连接已恢复，继续生成中'
  return props.streamConnected ? (props.document?.statusText || '生成中') : (props.document?.statusText || '准备中')
})
const statusTone = computed(() => {
  if (props.streamError) return 'error'
  if (props.streamState === 'reconnecting' || props.dirty) return 'warning'
  if (props.streamState === 'recovered' || (isCompleted.value && !props.dirty)) return 'success'
  return ''
})
const exportActionText = computed(() => (props.downloadUrl ? '下载 HTML' : '导出 HTML'))

const sourceContent = computed({
  get: () => props.content || '',
  set: (value) => emit('update:content', value)
})

const previewHtml = computed(() => {
  if (isCompleted.value) {
    return sourceContent.value || props.document?.htmlContent || ''
  }
  return props.streamText || props.document?.htmlContent || ''
})

const clampPreviewHeight = (height) => {
  const safeHeight = Number(height) || 0
  return Math.max(PREVIEW_MIN_HEIGHT, Math.min(safeHeight, PREVIEW_MAX_HEIGHT))
}

const injectPreviewBridge = (html) => {
  const source = html || EMPTY_PREVIEW_HTML

  if (/<\/body>/i.test(source)) {
    return source.replace(/<\/body>/i, `${PREVIEW_BRIDGE_MARKUP}</body>`)
  }

  if (/<\/html>/i.test(source)) {
    return source.replace(/<\/html>/i, `${PREVIEW_BRIDGE_MARKUP}</html>`)
  }

  return `${source}${PREVIEW_BRIDGE_MARKUP}`
}

const previewSrcDoc = computed(() => injectPreviewBridge(previewHtml.value))
const canSave = computed(() => isCompleted.value && props.dirty && !props.loading && !props.saving)
const canExport = computed(() => isCompleted.value && !props.loading && !props.exporting)

watch(isCompleted, (completed) => {
  if (!completed && activeView.value !== 'preview') {
    activeView.value = 'preview'
  }
}, { immediate: true })

watch(previewSrcDoc, () => {
  previewFrameHeight.value = PREVIEW_MIN_HEIGHT
})

const handlePreviewMessage = (event) => {
  if (event.source !== previewFrameRef.value?.contentWindow) {
    return
  }

  if (event.data?.type !== PREVIEW_HEIGHT_MESSAGE) {
    return
  }

  const nextHeight = Number(event.data?.height) || 0
  if (nextHeight > 0) {
    previewFrameHeight.value = clampPreviewHeight(nextHeight + 8)
  }
}

const handlePreviewLoad = () => {
  previewFrameHeight.value = PREVIEW_MIN_HEIGHT
}

onMounted(() => {
  window.addEventListener('message', handlePreviewMessage)
})

onBeforeUnmount(() => {
  window.removeEventListener('message', handlePreviewMessage)
})
</script>

<template>
  <aside class="interactive-workspace">
    <header class="workspace-header">
      <div class="workspace-main">
        <h2 class="workspace-title">{{ title }}</h2>
        <span class="workspace-status" :class="statusTone">{{ statusText }}</span>
      </div>

      <div class="workspace-actions">
        <div class="view-switch" role="tablist" aria-label="工作区视图切换">
          <button
            class="view-switch-btn"
            :class="{ active: activeView === 'preview' }"
            type="button"
            @click="activeView = 'preview'"
          >
            预览
          </button>
          <button
            class="view-switch-btn"
            :class="{ active: activeView === 'source' }"
            type="button"
            :disabled="!isCompleted"
            @click="activeView = 'source'"
          >
            源码
          </button>
        </div>
        <button
          class="action-btn icon-only"
          type="button"
          :disabled="!canSave"
          @click="$emit('save')"
          :aria-label="saving ? '保存中' : '保存'"
          :title="saving ? '保存中…' : '保存'"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
            <polyline points="17 21 17 13 7 13 7 21"/>
            <polyline points="7 3 7 8 15 8"/>
          </svg>
        </button>
        <button
          class="action-btn primary"
          type="button"
          :disabled="!canExport"
          @click="$emit('export')"
        >
          {{ exporting ? '处理中...' : exportActionText }}
        </button>
        <button
          class="action-btn icon-only"
          type="button"
          @click="$emit('close')"
          aria-label="关闭工作区"
          title="关闭工作区"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M6 6 L18 18 M6 18 L18 6" />
          </svg>
        </button>
      </div>
    </header>

    <section v-if="activeView === 'preview'" class="preview-panel">
      <div class="preview-shell">
        <iframe
          ref="previewFrameRef"
          class="preview-frame"
          :srcdoc="previewSrcDoc"
          :style="{ height: `${previewFrameHeight}px` }"
          sandbox="allow-scripts"
          referrerpolicy="no-referrer"
          title="互动内容预览"
          @load="handlePreviewLoad"
        ></iframe>
      </div>
      <div v-if="loading" class="preview-mask">正在加载工作区...</div>
    </section>

    <section v-else class="source-panel">
      <textarea
        v-model="sourceContent"
        class="source-editor"
        :disabled="!isCompleted"
        spellcheck="false"
      ></textarea>
    </section>
  </aside>
</template>

<style scoped>
.interactive-workspace {
  flex: 1 1 auto;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 20px 20px;
  background: var(--es-surface);
  overflow: auto;
}

.workspace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 48px;
}

.workspace-main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.workspace-title {
  margin: 0;
  min-width: 0;
  font-size: 24px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--es-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-status {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--es-surface-muted);
  color: var(--es-text-secondary);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.workspace-status.error {
  background: var(--es-danger-bg);
  color: var(--es-danger-text);
}

.workspace-status.warning {
  background: var(--es-warning-bg);
  color: var(--es-warning-text);
}

.workspace-status.success {
  background: var(--es-success-bg);
  color: var(--es-success-text);
}

.workspace-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.view-switch {
  display: inline-flex;
  align-items: center;
  padding: 3px;
  border: 1px solid var(--es-border-strong);
  border-radius: 12px;
  background: var(--es-surface-soft);
}

.view-switch-btn {
  min-width: 72px;
  height: 30px;
  padding: 0 12px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--es-text-secondary);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.view-switch-btn.active {
  background: var(--es-surface);
  color: var(--es-text-primary);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}

.view-switch-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.action-btn {
  height: 38px;
  min-width: 104px;
  padding: 0 18px;
  border: 1px solid var(--es-border-strong);
  border-radius: 12px;
  background: var(--es-surface);
  color: var(--es-text-primary);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
  transition: background 160ms ease, border-color 160ms ease, color 160ms ease;
}

.action-btn:hover:not(:disabled) {
  border-color: var(--es-text-primary);
}

.action-btn.primary {
  border-color: var(--es-text-primary);
  background: var(--es-text-primary);
  color: var(--es-surface);
}

.action-btn.primary:hover:not(:disabled) {
  background: var(--es-link-hover);
  border-color: var(--es-link-hover);
}

.action-btn.icon-only {
  min-width: auto;
  width: 38px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--es-text-secondary);
}

.action-btn.icon-only:hover:not(:disabled) {
  color: var(--es-text-primary);
}

.action-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.preview-panel {
  position: relative;
  flex: 0 0 auto;
  padding: 14px;
  border: 1px solid var(--es-border);
  border-radius: 18px;
  overflow: hidden;
  background: var(--es-surface-soft);
}

.preview-shell {
  width: min(100%, 1120px);
  margin: 0 auto;
}

.preview-frame {
  display: block;
  width: 100%;
  min-height: 420px;
  border: none;
  border-radius: 16px;
  background: var(--es-surface);
}

.preview-mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(248, 250, 252, 0.72);
  color: var(--es-text-secondary);
  font-size: 14px;
  font-weight: 700;
}

.source-panel {
  flex: 1 1 auto;
  min-height: 0;
  border: 1px solid var(--es-border);
  border-radius: 18px;
  overflow: hidden;
  background: #0f172a;
}

.source-editor {
  width: 100%;
  height: 100%;
  padding: 16px;
  border: none;
  outline: none;
  resize: none;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 13px;
  line-height: 1.7;
  font-family: "Consolas", "SFMono-Regular", monospace;
}

@media (max-width: 1200px) {
  .workspace-header {
    flex-direction: column;
    align-items: stretch;
  }

  .workspace-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .interactive-workspace {
    padding: 14px 16px 16px;
  }

  .workspace-main {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .workspace-title {
    font-size: 20px;
    white-space: normal;
  }

  .preview-panel {
    padding: 10px;
  }
}

@media (max-width: 640px) {
  .workspace-actions {
    width: 100%;
  }

  .action-btn:not(.icon-only) {
    flex: 1 1 auto;
  }
}
</style>
