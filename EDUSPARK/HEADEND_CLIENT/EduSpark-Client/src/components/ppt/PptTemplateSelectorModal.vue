<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  templates: {
    type: Array,
    default: () => []
  },
  selectedTemplateId: {
    type: String,
    default: ''
  },
  loading: {
    type: Boolean,
    default: false
  },
  loadError: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue', 'select-template', 'reload'])

const sceneFilter = ref('全部场景')
const styleFilter = ref('全部风格')
const draftTemplateId = ref('')
const previewTemplateId = ref('')

const sceneOptions = computed(() => [
  '全部场景',
  ...new Set(props.templates.map(template => template.scene).filter(Boolean))
])

const styleOptions = computed(() => [
  '全部风格',
  ...new Set(props.templates.map(template => template.style).filter(Boolean))
])

const filteredTemplates = computed(() => props.templates.filter((template) => {
  const sceneMatched = sceneFilter.value === '全部场景' || template.scene === sceneFilter.value
  const styleMatched = styleFilter.value === '全部风格' || template.style === styleFilter.value
  return sceneMatched && styleMatched
}))

const previewTemplate = computed(() => {
  if (!filteredTemplates.value.length) return null
  return filteredTemplates.value.find(template => template.id === previewTemplateId.value) || filteredTemplates.value[0]
})

const syncDraftState = () => {
  const fallbackId = props.selectedTemplateId || filteredTemplates.value[0]?.id || props.templates[0]?.id || ''
  draftTemplateId.value = fallbackId
  previewTemplateId.value = fallbackId
}

watch(() => props.modelValue, (open) => {
  if (!open) return
  sceneFilter.value = '全部场景'
  styleFilter.value = '全部风格'
  syncDraftState()
})

watch(filteredTemplates, (templates) => {
  if (!props.modelValue) return
  if (!templates.length) {
    previewTemplateId.value = ''
    draftTemplateId.value = ''
    return
  }

  const currentPreviewExists = templates.some(template => template.id === previewTemplateId.value)
  if (!currentPreviewExists) {
    previewTemplateId.value = templates[0].id
  }

  const currentDraftExists = templates.some(template => template.id === draftTemplateId.value)
  if (!currentDraftExists) {
    draftTemplateId.value = templates[0].id
  }
})

const close = () => {
  emit('update:modelValue', false)
}

const chooseTemplate = (template) => {
  if (!template) return
  draftTemplateId.value = template.id
  previewTemplateId.value = template.id
}

const confirmSelection = () => {
  const template = props.templates.find(item => item.id === draftTemplateId.value) || previewTemplate.value
  if (template) {
    emit('select-template', template)
  }
  close()
}

const getTemplateSurfaceStyle = (template) => ({
  background: template?.background || 'linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%)',
  color: template?.surfaceTextColor || '#0f172a'
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="modelValue" class="modal-overlay" @click.self="close">
        <div class="modal-container">
          <button class="modal-close" type="button" @click="close" aria-label="关闭模板弹窗">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>

          <div class="modal-header">
            <div class="modal-title">选择模板</div>
          </div>

          <div class="modal-filters">
            <div class="filter-row">
              <span class="filter-label">模板场景：</span>
              <div class="filter-pills">
                <button
                  v-for="scene in sceneOptions"
                  :key="scene"
                  type="button"
                  class="filter-pill"
                  :class="{ active: sceneFilter === scene }"
                  @click="sceneFilter = scene"
                >
                  {{ scene }}
                </button>
              </div>
            </div>

            <div class="filter-row">
              <span class="filter-label">模板风格：</span>
              <div class="filter-pills">
                <button
                  v-for="style in styleOptions"
                  :key="style"
                  type="button"
                  class="filter-pill"
                  :class="{ active: styleFilter === style }"
                  @click="styleFilter = style"
                >
                  {{ style }}
                </button>
              </div>
            </div>
          </div>

          <div class="modal-body">
            <!-- 左列：单列模板缩略图，垂直滚动 -->
            <div class="template-list" :class="{ 'is-empty': !filteredTemplates.length }">
              <template v-if="filteredTemplates.length">
                <button
                  v-for="template in filteredTemplates"
                  :key="template.id"
                  type="button"
                  class="template-list-item"
                  :class="{ active: draftTemplateId === template.id }"
                  @click="chooseTemplate(template)"
                  :title="template.name"
                >
                  <div class="template-thumb">
                    <img
                      v-if="template.coverUrl"
                      :src="template.coverUrl"
                      :alt="template.name"
                      loading="lazy"
                    />
                    <div v-else class="template-thumb-fallback" :style="getTemplateSurfaceStyle(template)">
                      <span>{{ template.name }}</span>
                    </div>
                  </div>
                </button>
              </template>
              <div v-else class="template-list-empty">
                {{ loading ? '模板加载中...' : '暂无可选模板' }}
              </div>
            </div>

            <!-- 中列：选中模板的大封面 + caption；状态消息 span 到右列 -->
            <div v-if="loading" class="template-empty template-empty--span">
              正在加载模板...
            </div>

            <div v-else-if="loadError && !filteredTemplates.length" class="template-empty template-empty--span template-empty--error">
              <span>{{ loadError }}</span>
              <button class="empty-retry-btn" type="button" @click="emit('reload')">
                重新加载
              </button>
            </div>

            <template v-else-if="previewTemplate">
              <div class="template-main-preview">
                <div class="preview-surface">
                  <img
                    v-if="previewTemplate.coverUrl"
                    :src="previewTemplate.coverUrl"
                    :alt="previewTemplate.name"
                    class="preview-cover-image"
                  />
                  <div v-else class="preview-cover-fallback" :style="getTemplateSurfaceStyle(previewTemplate)">
                    <div class="preview-meta">
                      <span class="preview-scene">{{ previewTemplate.scene }}</span>
                      <span class="preview-style">{{ previewTemplate.style }}</span>
                    </div>
                    <div class="preview-badge">{{ previewTemplate.badge }}</div>
                    <div class="preview-title">{{ previewTemplate.coverTitle }}</div>
                    <div class="preview-subtitle">{{ previewTemplate.coverSubtitle }}</div>
                  </div>
                </div>

                <div class="preview-caption">
                  <div class="preview-caption-title">
                    {{ previewTemplate.name }}
                    <span class="preview-caption-chip">{{ previewTemplate.scene }} · {{ previewTemplate.style }}</span>
                  </div>
                  <div class="preview-caption-text">{{ previewTemplate.description }}</div>
                </div>
              </div>

              <!-- 右列：3 张竖排小缩略图 -->
              <div class="preview-side-strip">
                <div
                  v-for="(imgUrl, idx) in (previewTemplate.previewImages || []).slice(0, 3)"
                  :key="`side-thumb-${idx}`"
                  class="preview-side-thumb"
                >
                  <img :src="imgUrl" :alt="`预览页 ${idx + 1}`" loading="lazy" />
                </div>
                <div
                  v-for="n in Math.max(0, 3 - ((previewTemplate.previewImages || []).slice(0, 3).length))"
                  :key="`side-placeholder-${n}`"
                  class="preview-side-thumb preview-side-thumb--placeholder"
                >
                  <span>暂无预览</span>
                </div>
              </div>
            </template>

            <div v-else class="template-empty template-empty--span">
              没有找到符合筛选条件的模板。
            </div>
          </div>

          <div class="modal-footer">
            <button class="footer-btn ghost" type="button" @click="close">取消</button>
            <button class="footer-btn primary" type="button" :disabled="!draftTemplateId" @click="confirmSelection">
              选择模板
            </button>
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
  z-index: 20000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.58);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.modal-container {
  position: relative;
  width: min(1060px, 100%);
  max-height: min(88vh, 820px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border-radius: 28px;
  background: var(--es-surface);
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.24);
}

.modal-close {
  position: absolute;
  top: 14px;
  right: 16px;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 999px;
  background: rgba(248, 250, 252, 0.96);
  color: var(--es-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.modal-close:hover {
  background: var(--es-link-soft);
  color: var(--es-link-hover);
}

.modal-header {
  padding: 16px 32px 12px;
}

.modal-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--es-text-primary);
}

.modal-filters {
  padding: 0 32px 14px;
  border-bottom: 1px solid var(--es-border);
}

.filter-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.filter-row + .filter-row {
  margin-top: 12px;
}

.filter-label {
  width: 76px;
  flex-shrink: 0;
  padding-top: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--es-text-secondary);
}

.filter-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.filter-pill {
  border: 1px solid var(--es-border);
  background: var(--es-surface-soft);
  color: var(--es-text-secondary);
  border-radius: 999px;
  padding: 8px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.filter-pill:hover,
.filter-pill.active {
  color: var(--es-link-hover);
  border-color: rgba(37, 99, 235, 0.24);
  background: var(--es-link-soft);
}

.modal-body {
  min-height: 360px;
  flex: 1;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 160px;
  gap: 20px;
  padding: 20px 32px 24px;
  align-items: stretch;
  overflow: hidden;
}

/* 左列：单列模板缩略图，垂直滚动 */
.template-list {
  overflow-y: auto;
  padding-right: 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-content: start;
  min-height: 100%;
}

.template-list.is-empty {
  overflow: hidden;
}

.template-list-empty {
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  border-radius: 16px;
  border: 1px dashed var(--es-border-strong);
  background: var(--es-surface-soft);
  color: var(--es-text-tertiary);
  font-size: 14px;
  text-align: center;
}

.template-list-item {
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  border: 2px solid transparent;
  background: transparent;
  border-radius: 14px;
  cursor: pointer;
  transition: transform 0.15s ease;
}

.template-list-item:hover {
  transform: translateY(-2px);
}

.template-list-item:focus-visible {
  outline: 2px solid var(--es-link-hover);
  outline-offset: 2px;
}

.template-list-item.active {
  border-color: var(--es-link-hover);
}

.template-list-item.active .template-thumb {
  box-shadow: 0 8px 24px rgba(37, 99, 235, 0.22);
}

.template-thumb {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 10;
  border-radius: 10px;
  overflow: hidden;
  background: var(--es-surface-soft);
  border: 1px solid var(--es-border);
  transition: box-shadow 0.18s ease;
}

.template-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.template-thumb-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px;
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  text-align: center;
  line-height: 1.3;
  word-break: break-word;
}


/* 中列：大封面 + 下方 caption */
.template-main-preview {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.preview-surface {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 14px;
  overflow: hidden;
  background: var(--es-surface-soft);
  border: 1px solid var(--es-border);
}

.preview-cover-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
  background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
}

.preview-cover-fallback {
  position: absolute;
  inset: 0;
  padding: 28px;
  display: flex;
  flex-direction: column;
  color: #ffffff;
}

.preview-cover-fallback::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.52), transparent 32%),
    radial-gradient(circle at bottom right, rgba(255, 255, 255, 0.24), transparent 34%);
  pointer-events: none;
}

.preview-meta {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 10px;
  font-size: 13px;
  font-weight: 600;
  opacity: 0.85;
}

.preview-badge {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  margin-top: 10px;
  min-height: 26px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.78);
  color: rgba(15, 23, 42, 0.82);
  align-self: flex-start;
}

.preview-title {
  position: relative;
  z-index: 1;
  margin-top: auto;
  font-size: clamp(24px, 2.4vw, 34px);
  line-height: 1.2;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.preview-subtitle {
  position: relative;
  z-index: 1;
  margin-top: 10px;
  font-size: 14px;
  opacity: 0.85;
}

.preview-caption {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preview-caption-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 16px;
  font-weight: 700;
  color: var(--es-text-primary);
}

.preview-caption-chip {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--es-link-soft);
  color: var(--es-link-hover);
  font-size: 11.5px;
  font-weight: 500;
}

.preview-caption-text {
  font-size: 13px;
  line-height: 1.6;
  color: var(--es-text-tertiary);
}

/* 右列：竖排 3 张小缩略图 */
.preview-side-strip {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-content: start;
}

.preview-side-thumb {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 10;
  border-radius: 10px;
  overflow: hidden;
  background: var(--es-surface-soft);
  border: 1px solid var(--es-border);
  flex-shrink: 0;
}

.preview-side-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.preview-side-thumb--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--es-text-tertiary);
  font-size: 12px;
  border-style: dashed;
}

.template-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 100%;
  border-radius: 16px;
  border: 1px dashed var(--es-border-strong);
  color: var(--es-text-tertiary);
  background: var(--es-surface-soft);
  padding: 24px;
  text-align: center;
}

/* 三栏布局下，loading / empty / error 跨"中列 + 右列" */
.template-empty--span {
  grid-column: 2 / -1;
}

.template-empty--error {
  color: var(--es-danger-text);
  border-color: rgba(239, 68, 68, 0.24);
  background: var(--es-danger-bg);
}

.empty-retry-btn {
  min-width: 108px;
  height: 38px;
  border: none;
  border-radius: 999px;
  background: var(--es-link-hover);
  color: var(--es-surface);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 0 32px 28px;
}

.footer-btn {
  min-width: 120px;
  height: 46px;
  border-radius: 14px;
  border: none;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.footer-btn.ghost {
  background: var(--es-surface-muted);
  color: var(--es-text-secondary);
}

.footer-btn.ghost:hover {
  background: var(--es-border);
}

.footer-btn.primary {
  background: linear-gradient(135deg, #2563eb 0%, #3b82f6 100%);
  color: white;
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.24);
}

.footer-btn.primary:hover:not(:disabled) {
  transform: translateY(-1px);
}

.footer-btn.primary:disabled {
  cursor: not-allowed;
  opacity: 0.55;
  box-shadow: none;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-active .modal-container,
.modal-leave-active .modal-container {
  transition: transform 0.24s ease, opacity 0.24s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .modal-container,
.modal-leave-to .modal-container {
  transform: translateY(12px) scale(0.98);
  opacity: 0;
}

@media (max-width: 980px) {
  .modal-body {
    grid-template-columns: 1fr;
  }

  .template-list {
    flex-direction: row;
    overflow-x: auto;
    overflow-y: hidden;
    max-height: 200px;
    padding-right: 0;
    padding-bottom: 8px;
  }

  .template-list-item {
    flex-shrink: 0;
    width: 150px;
  }

  .preview-side-strip {
    flex-direction: row;
  }

  .preview-side-thumb {
    flex: 1;
  }

  .template-empty--span {
    grid-column: 1 / -1;
  }
}

@media (max-width: 720px) {
  .modal-overlay {
    padding: 14px;
  }

  .modal-container {
    border-radius: 22px;
    max-height: min(94vh, 820px);
  }

  .modal-header,
  .modal-filters,
  .modal-body,
  .modal-footer {
    padding-left: 18px;
    padding-right: 18px;
  }

  .filter-row {
    flex-direction: column;
    gap: 8px;
  }

  .filter-label {
    width: auto;
    padding-top: 0;
  }

  .preview-thumb-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .modal-footer {
    justify-content: stretch;
  }

  .footer-btn {
    flex: 1;
  }
}
</style>
