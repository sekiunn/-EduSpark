<script setup>
import { computed, nextTick, ref, watch } from 'vue'

const props = defineProps({
  document: { type: Object, default: () => null },
  streamText: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  exporting: { type: Boolean, default: false },
  streamConnected: { type: Boolean, default: false },
  streamError: { type: String, default: '' },
  downloadUrl: { type: String, default: '' },
  slidesProgress: { type: Array, default: () => [] },
  slidesTotal: { type: Number, default: 0 },
  overallProgress: { type: Object, default: () => ({ done: 0, total: 0 }) },
  hasSlideProgress: { type: Boolean, default: false }
})

defineEmits(['close', 'export'])

const isCompleted = computed(() => props.document?.status === 'completed')
const isFailed = computed(() => props.document?.status === 'failed')
const title = computed(() => props.document?.title || 'PPT 初稿')

const progressDone = computed(() => props.overallProgress?.done || 0)
const progressTotal = computed(() => props.overallProgress?.total || props.slidesTotal || 0)
const progressPercent = computed(() => {
  if (!progressTotal.value) return 0
  return Math.min(100, Math.round((progressDone.value / progressTotal.value) * 100))
})

const summary = computed(() => {
  if (props.document?.summary) return props.document.summary
  if (props.hasSlideProgress && !isCompleted.value) {
    return `正在生成第 ${progressDone.value + 1}/${progressTotal.value || '?'} 页…`
  }
  if (isCompleted.value) return 'PPT 已生成完成，可查看页面结构并下载。'
  return '系统正在分阶段生成 PPT。'
})

const statusText = computed(() => props.streamError || props.document?.statusText || '准备中')
const statusTone = computed(() => {
  if (props.streamError || props.document?.errorMessage) return 'error'
  if (isCompleted.value) return 'success'
  return 'info'
})
const exportActionText = computed(() => (props.downloadUrl ? '下载 PPT' : '导出 PPT'))

const knowledgeSources = computed(() =>
  Array.isArray(props.document?.knowledgeSources) ? props.document.knowledgeSources.filter(Boolean) : []
)
const highlights = computed(() =>
  Array.isArray(props.document?.enrichmentHighlights) ? props.document.enrichmentHighlights.filter(Boolean) : []
)
const blueprintMeta = computed(() => {
  const bp = props.document?.enrichedBlueprint || props.document?.sourceBlueprint || {}
  return [
    bp.subject ? `学科：${bp.subject}` : '',
    bp.grade ? `年级：${bp.grade}` : '',
    bp.slideCount ? `页数：${bp.slideCount}` : '',
    bp.style ? `风格：${bp.style}` : ''
  ].filter(Boolean)
})

const fallbackSlides = computed(() =>
  Array.isArray(props.document?.plan?.slides) ? props.document.plan.slides.filter(Boolean) : []
)

const baseSlides = computed(() => {
  if (props.slidesProgress && props.slidesProgress.length > 0) {
    return props.slidesProgress
  }
  return fallbackSlides.value.map((slide) => ({
    slideNo: slide.slideNo,
    title: slide.title,
    bullets: slide.bullets || [],
    layout: slide.layout,
    slotLayout: slide.slotLayout,
    visualFocus: slide.visualFocus,
    speakerNotes: slide.speakerNotes,
    stage: 'completed',
    backgroundImageUrl: ''
  }))
})

// 把 baseSlides 补齐到 slidesTotal——没开始的页用 pending 占位，保证缩略图条总是显示完整页数
const allSlides = computed(() => {
  const base = baseSlides.value
  const total = Math.max(props.slidesTotal || 0, base.length)
  if (total === 0) return []
  const byNo = new Map(base.map((s) => [s.slideNo, s]))
  return Array.from({ length: total }, (_, i) => {
    const slideNo = i + 1
    return (
      byNo.get(slideNo) || {
        slideNo,
        stage: 'pending',
        bullets: [],
        title: ''
      }
    )
  })
})

const isPlanningPhase = computed(() => allSlides.value.length === 0)

// activeSlideNo：用户没点缩略图时跟随"最新正在制作的页"；点过缩略图后锁定
const userPinnedSlideNo = ref(null)
const trackingSlideNo = computed(() => {
  const slides = allSlides.value
  for (let i = slides.length - 1; i >= 0; i--) {
    const stage = slides[i].stage
    if (stage && stage !== 'completed' && stage !== 'pending') {
      return slides[i].slideNo
    }
  }
  const firstCompleted = slides.find((s) => s.stage === 'completed')
  return firstCompleted?.slideNo || slides[0]?.slideNo || 1
})
const activeSlideNo = computed(() => userPinnedSlideNo.value ?? trackingSlideNo.value)
const activeSlide = computed(
  () => allSlides.value.find((s) => s.slideNo === activeSlideNo.value) || allSlides.value[0] || null
)
const isAutoFollowing = computed(() => userPinnedSlideNo.value == null)

const handleThumbClick = (slideNo) => {
  userPinnedSlideNo.value = slideNo
}
const handleResumeAutoFollow = () => {
  userPinnedSlideNo.value = null
}

const stageOf = (slide) => slide?.stage || 'pending'
const hasBackground = (slide) => Boolean(slide?.backgroundImageUrl)

const thumbStripEl = ref(null)
watch(activeSlideNo, async () => {
  await nextTick()
  const el = thumbStripEl.value?.querySelector(`[data-slide-no="${activeSlideNo.value}"]`)
  if (el && typeof el.scrollIntoView === 'function') {
    el.scrollIntoView({ behavior: 'smooth', inline: 'nearest', block: 'nearest' })
  }
})

// 鼠标滚轮在缩略图条上横向滑动：把纵向滚轮增量转成横向 scrollLeft。
// 仅当还能横向滚动时才拦截默认纵向滚动，避免影响整页滚动。
const onThumbWheel = (e) => {
  const el = thumbStripEl.value
  if (!el || el.scrollWidth <= el.clientWidth) return
  e.preventDefault()
  el.scrollLeft += e.deltaY || e.deltaX
}
</script>

<template>
  <aside class="ppt-workspace">
    <header class="workspace-header">
      <div class="workspace-main">
        <h2 class="workspace-title">{{ title }}</h2>
        <span class="workspace-status" :class="statusTone">{{ statusText }}</span>
      </div>

      <div class="workspace-actions">
        <button
          class="action-btn primary"
          type="button"
          :disabled="!isCompleted || exporting"
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

    <p class="workspace-summary">{{ summary }}</p>

    <div v-if="progressTotal > 0 && !isCompleted && !isFailed" class="progress-strip">
      <div class="progress-text">
        已完成 <strong>{{ progressDone }}</strong> / {{ progressTotal }} 页
      </div>
      <div class="progress-bar">
        <div class="progress-bar-fill" :style="{ width: progressPercent + '%' }"></div>
      </div>
    </div>

    <div class="workspace-layout">
      <aside class="side-panel">
        <section class="side-section">
          <div class="side-title">蓝图摘要</div>
          <div v-if="blueprintMeta.length" class="meta-list">
            <div v-for="item in blueprintMeta" :key="item" class="meta-item">{{ item }}</div>
          </div>
          <div v-else class="side-empty">等待蓝图补全</div>
        </section>

        <section class="side-section">
          <div class="side-title">模板</div>
          <div class="meta-card">
            <div class="meta-card-label">当前模板</div>
            <div class="meta-card-value">{{ document?.templateName || '未命名模板' }}</div>
          </div>
        </section>

        <section class="side-section">
          <div class="side-title">知识依据</div>
          <div v-if="knowledgeSources.length" class="source-list">
            <div
              v-for="item in knowledgeSources"
              :key="`${item.fileId || item.fileName}-${item.excerpt}`"
              class="source-item"
            >
              <div class="source-name">{{ item.fileName }}</div>
              <div v-if="item.excerpt" class="source-excerpt">{{ item.excerpt }}</div>
            </div>
          </div>
          <div v-else class="side-empty">当前没有额外知识库命中</div>
        </section>

        <section class="side-section">
          <div class="side-title">增强结果</div>
          <div v-if="highlights.length" class="highlight-list">
            <div v-for="item in highlights" :key="item" class="highlight-item">{{ item }}</div>
          </div>
          <div v-else class="side-empty">等待增强结果</div>
        </section>
      </aside>

      <section class="main-panel">
        <div v-if="document?.errorMessage || streamError" class="workspace-note error">
          {{ document?.errorMessage || streamError }}
        </div>

        <div
          v-if="!isPlanningPhase"
          ref="thumbStripEl"
          class="thumb-strip"
          role="tablist"
          aria-label="幻灯片缩略图"
          @wheel="onThumbWheel"
        >
          <button
            v-for="slide in allSlides"
            :key="slide.slideNo"
            type="button"
            role="tab"
            class="thumb"
            :class="[
              `thumb-stage-${stageOf(slide)}`,
              { active: slide.slideNo === activeSlideNo, 'has-bg': hasBackground(slide) }
            ]"
            :data-slide-no="slide.slideNo"
            :aria-selected="slide.slideNo === activeSlideNo"
            :style="hasBackground(slide) ? { backgroundImage: `url(${slide.backgroundImageUrl})` } : {}"
            @click="handleThumbClick(slide.slideNo)"
          >
            <!-- 有真实预览图的缩略图：不加灰罩、不叠文字，直接干净展示效果图 -->
            <span v-if="!hasBackground(slide)" class="thumb-inner">
              <span class="thumb-no">{{ slide.slideNo }}</span>
              <span class="thumb-title">
                {{ slide.title || (stageOf(slide) === 'pending' ? '待生成' : '生成中…') }}
              </span>
            </span>
            <span
              v-if="stageOf(slide) !== 'completed' && stageOf(slide) !== 'pending'"
              class="thumb-shimmer"
            ></span>
          </button>
        </div>

        <div v-if="!isAutoFollowing && !isCompleted && !isFailed" class="follow-bar">
          已锁定第 {{ activeSlideNo }} 页查看
          <button type="button" class="follow-btn" @click="handleResumeAutoFollow">
            返回跟随生成
          </button>
        </div>

        <div class="stage-area">
          <div v-if="isPlanningPhase" class="planning-frame">
            <div class="planning-title">AI 正在思考中...</div>
            <pre class="planning-text">{{ streamText || '正在生成页级蓝图...' }}</pre>
          </div>

          <div v-else-if="activeSlide" class="active-slide-wrap">
            <article
              class="active-slide-frame"
              :class="[`stage-${stageOf(activeSlide)}`, { 'has-bg': hasBackground(activeSlide) }]"
              :style="hasBackground(activeSlide) ? { backgroundImage: `url(${activeSlide.backgroundImageUrl})` } : {}"
            >
              <div v-if="hasBackground(activeSlide)" class="active-slide-veil"></div>
              <div v-else class="frame-placeholder">
                <span>第 {{ activeSlide.slideNo }} 页 · 暂无预览图</span>
              </div>
            </article>

            <section class="active-slide-content">
              <template v-if="stageOf(activeSlide) === 'pending'">
                <div class="pending-hint">该页尚未开始生成，请耐心等待…</div>
                <div class="sk-line sk-title shimmer"></div>
                <div class="sk-line sk-bullet shimmer"></div>
                <div class="sk-line sk-bullet shimmer short"></div>
              </template>

              <template v-else-if="stageOf(activeSlide) === 'skeleton'">
                <div class="sk-line sk-title shimmer"></div>
                <div class="sk-line sk-bullet shimmer"></div>
                <div class="sk-line sk-bullet shimmer short"></div>
              </template>

              <template v-else-if="stageOf(activeSlide) === 'background_ready'">
                <div class="sk-line sk-title shimmer"></div>
                <div class="sk-line sk-bullet shimmer"></div>
              </template>

              <template v-else-if="stageOf(activeSlide) === 'content_filling'">
                <h3 v-if="activeSlide.title" class="slide-title fade-in-up">{{ activeSlide.title }}</h3>
                <div v-else class="sk-line sk-title shimmer"></div>

                <ul v-if="activeSlide.bullets && activeSlide.bullets.length" class="slide-bullets">
                  <li
                    v-for="(bullet, idx) in activeSlide.bullets"
                    :key="`${activeSlide.slideNo}-b-${idx}`"
                    class="bullet-enter"
                  >
                    {{ bullet }}
                  </li>
                </ul>

                <div v-if="activeSlide.visualFocus" class="slide-note fade-in-up">
                  <span class="slide-note-label">视觉提示</span>
                  <span>{{ activeSlide.visualFocus }}</span>
                </div>
              </template>

              <template v-else>
                <h3 class="slide-title">{{ activeSlide.title || '（无标题）' }}</h3>
                <ul v-if="activeSlide.bullets && activeSlide.bullets.length" class="slide-bullets">
                  <li v-for="bullet in activeSlide.bullets" :key="bullet">{{ bullet }}</li>
                </ul>
                <div v-if="activeSlide.visualFocus" class="slide-note">
                  <span class="slide-note-label">视觉提示</span>
                  <span>{{ activeSlide.visualFocus }}</span>
                </div>
                <div v-if="activeSlide.speakerNotes" class="slide-note">
                  <span class="slide-note-label">讲解提示</span>
                  <span>{{ activeSlide.speakerNotes }}</span>
                </div>
              </template>
            </section>

            <div class="active-slide-foot">
              <div class="active-slide-layout">
                {{ activeSlide.slotLayout || activeSlide.layout || 'content' }}
              </div>
              <div class="active-slide-no">第 {{ activeSlide.slideNo }} / {{ progressTotal || allSlides.length }} 页</div>
            </div>
          </div>
        </div>

        <div v-if="loading" class="loading-mask">正在加载工作区...</div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.ppt-workspace {
  flex: 1 1 auto;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 24px 22px;
  background: var(--es-surface);
  overflow: auto;
}

.workspace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.workspace-main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.workspace-title {
  margin: 0;
  font-size: 28px;
  line-height: 1.15;
  font-weight: 800;
  color: var(--es-text-primary);
}

.workspace-status {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.workspace-status.info {
  background: var(--es-link-soft);
  color: var(--es-link-hover);
}

.workspace-status.success {
  background: var(--es-success-bg);
  color: var(--es-success-text);
}

.workspace-status.error {
  background: var(--es-danger-bg);
  color: var(--es-danger-text);
}

.workspace-actions {
  display: flex;
  align-items: center;
  gap: 8px;
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
  opacity: 0.5;
  cursor: not-allowed;
}

.workspace-summary,
.workspace-note,
.side-empty,
.source-excerpt,
.meta-item,
.highlight-item {
  font-size: 13px;
  line-height: 1.7;
  color: var(--es-text-secondary);
}

.workspace-summary {
  margin: 0;
}

.progress-strip {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 14px;
  background: var(--es-surface-soft);
  border: 1px solid var(--es-border);
}

.progress-text {
  font-size: 13px;
  color: var(--es-text-secondary);
  flex-shrink: 0;
}

.progress-text strong {
  color: var(--es-text-primary);
  font-weight: 800;
}

.progress-bar {
  flex: 1 1 auto;
  height: 6px;
  border-radius: 999px;
  background: var(--es-border);
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--es-link-hover), var(--es-text-primary));
  transition: width 320ms ease-out;
}

.workspace-layout {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 22px;
}

.side-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
}

.side-section,
.main-panel {
  border: 1px solid var(--es-border);
  border-radius: 18px;
  background: var(--es-surface);
}

.side-section {
  padding: 14px;
}

.side-title,
.outline-title {
  font-size: 13px;
  font-weight: 800;
  color: var(--es-text-primary);
  margin-bottom: 10px;
}

.meta-list,
.highlight-list,
.source-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meta-item,
.highlight-item,
.source-item,
.meta-card {
  padding: 10px 12px;
  border-radius: 14px;
  background: var(--es-surface-soft);
}

.meta-card-label,
.source-name,
.slide-note-label,
.active-slide-no,
.active-slide-layout {
  font-size: 12px;
  font-weight: 700;
  color: var(--es-text-tertiary);
}

.meta-card-value {
  margin-top: 4px;
  font-size: 15px;
  font-weight: 700;
  color: var(--es-text-primary);
}

.main-panel {
  position: relative;
  min-width: 0;
  min-height: 0;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--es-surface-soft);
}

.workspace-note.error {
  color: var(--es-danger-text);
  margin: 0;
}

.thumb-strip {
  display: flex;
  gap: 10px;
  padding: 4px 2px 10px;
  overflow-x: auto;
  overflow-y: hidden;
  flex-shrink: 0;
  scrollbar-width: thin;
}

.thumb-strip::-webkit-scrollbar {
  height: 6px;
}
.thumb-strip::-webkit-scrollbar-thumb {
  background: var(--es-border-strong);
  border-radius: 999px;
}

.thumb {
  position: relative;
  flex: 0 0 auto;
  width: 144px;
  aspect-ratio: 16 / 9;
  padding: 0;
  border: 2px solid var(--es-border);
  border-radius: 12px;
  background: var(--es-surface);
  background-size: cover;
  background-position: center;
  cursor: pointer;
  overflow: hidden;
  transition: border-color 200ms ease, box-shadow 200ms ease, transform 200ms ease;
}

.thumb:hover {
  border-color: var(--es-border-strong);
  transform: translateY(-1px);
}

.thumb.active {
  border-color: var(--es-text-primary);
  box-shadow: 0 0 0 3px var(--es-link-soft), 0 8px 18px rgba(15, 23, 42, 0.12);
}

.thumb.thumb-stage-pending {
  background: linear-gradient(135deg, #f1f5f9, #e2e8f0);
}

.thumb.thumb-stage-skeleton,
.thumb.thumb-stage-background_ready,
.thumb.thumb-stage-content_filling {
  background-color: #f8fafc;
}

.thumb.has-bg .thumb-inner {
  color: #fff;
  text-shadow: 0 1px 2px rgba(15, 23, 42, 0.45);
}

.thumb-veil {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.18), rgba(15, 23, 42, 0.5));
  pointer-events: none;
}

.thumb-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 100%;
  padding: 6px 8px 7px;
  text-align: left;
  color: var(--es-text-primary);
}

.thumb-no {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.4px;
  color: var(--es-text-tertiary);
}

.thumb.has-bg .thumb-no {
  color: rgba(255, 255, 255, 0.78);
}

.thumb-title {
  font-size: 12px;
  font-weight: 700;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.thumb-shimmer {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg,
      rgba(255, 255, 255, 0) 0%,
      rgba(255, 255, 255, 0.55) 50%,
      rgba(255, 255, 255, 0) 100%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite linear;
  pointer-events: none;
  z-index: 2;
  mix-blend-mode: overlay;
}

.follow-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 12px;
  background: var(--es-link-soft);
  color: var(--es-link-hover);
  font-size: 12px;
  font-weight: 700;
}

.follow-btn {
  margin-left: auto;
  padding: 4px 10px;
  border: 1px solid var(--es-link-hover);
  border-radius: 999px;
  background: transparent;
  color: var(--es-link-hover);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.follow-btn:hover {
  background: var(--es-link-hover);
  color: #fff;
}

.stage-area {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4px;
  overflow: hidden;
}

.planning-frame {
  width: 100%;
  max-width: 720px;
  padding: 22px 26px;
  border-radius: 18px;
  background: var(--es-surface);
  border: 1px solid var(--es-border);
}

.planning-title {
  font-size: 15px;
  font-weight: 800;
  color: var(--es-text-primary);
  margin-bottom: 10px;
}

.planning-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.8;
  color: var(--es-text-secondary);
  font-family: "SFMono-Regular", Consolas, monospace;
  max-height: 100%;
  overflow: auto;
}

.active-slide-frame {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 16px;
  background: var(--es-surface);
  background-size: cover;
  background-position: center;
  overflow: hidden;
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.1);
  transition: box-shadow 240ms ease;
  flex-shrink: 0;
}

.active-slide-frame.stage-completed {
  animation: slide-completed-pop 360ms ease-out;
}

@keyframes slide-completed-pop {
  0% {
    transform: scale(0.985);
    box-shadow: 0 8px 18px rgba(15, 23, 42, 0.06);
  }
  60% {
    transform: scale(1.005);
    box-shadow: 0 24px 48px rgba(15, 23, 42, 0.14);
  }
  100% {
    transform: scale(1);
    box-shadow: 0 18px 38px rgba(15, 23, 42, 0.12);
  }
}

.active-slide-veil {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.0), rgba(15, 23, 42, 0.12));
  pointer-events: none;
}

.frame-placeholder {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--es-text-tertiary);
  font-size: 14px;
  font-weight: 700;
  background: repeating-linear-gradient(
    45deg,
    var(--es-surface) 0,
    var(--es-surface) 12px,
    var(--es-surface-soft) 12px,
    var(--es-surface-soft) 24px
  );
}

.active-slide-wrap {
  width: 100%;
  max-width: min(100%, 960px);
  max-height: 100%;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
  min-height: 0;
}

.active-slide-content {
  flex: 1 1 auto;
  min-height: 0;
  padding: 18px 22px;
  border-radius: 16px;
  background: var(--es-surface);
  border: 1px solid var(--es-border);
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
}

.active-slide-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--es-text-tertiary);
  flex-shrink: 0;
}

.slide-title {
  margin: 0;
  font-size: 22px;
  line-height: 1.3;
  font-weight: 800;
  color: var(--es-text-primary);
}

.slide-bullets {
  margin: 0;
  padding-left: 22px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: var(--es-text-primary);
  font-size: 15px;
  line-height: 1.7;
}

.slide-note {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--es-surface-soft);
  color: var(--es-text-secondary);
  font-size: 13px;
  line-height: 1.65;
}

.pending-hint {
  font-size: 13px;
  color: var(--es-text-tertiary);
  margin-bottom: 4px;
}

.sk-line {
  height: 16px;
  border-radius: 8px;
  margin-bottom: 12px;
  background: linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%);
  background-size: 200% 100%;
}

.sk-line.sk-title {
  height: 26px;
  width: 55%;
}

.sk-line.sk-bullet {
  width: 88%;
}

.sk-line.sk-bullet.short {
  width: 62%;
}

.sk-line.shimmer {
  animation: shimmer 1.4s infinite linear;
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

.fade-in-up {
  animation: fadeInUp 320ms ease-out both;
}

.bullet-enter {
  animation: fadeInUp 280ms ease-out both;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.loading-mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(248, 250, 252, 0.74);
  color: var(--es-text-secondary);
  font-size: 14px;
  font-weight: 700;
  border-radius: 18px;
  z-index: 4;
}

@media (max-width: 1180px) {
  .workspace-layout {
    grid-template-columns: 1fr;
  }

  .side-panel {
    flex-direction: row;
    overflow-x: auto;
  }

  .side-section {
    min-width: 240px;
    flex: 0 0 auto;
  }
}

@media (max-width: 768px) {
  .ppt-workspace {
    padding: 14px 16px 16px;
  }

  .workspace-header {
    flex-direction: column;
    align-items: stretch;
  }

  .workspace-main,
  .workspace-actions {
    justify-content: space-between;
  }

  .workspace-title {
    font-size: 22px;
  }

  .thumb {
    width: 120px;
  }

  .active-slide-content {
    padding: 14px 16px;
  }

  .slide-title {
    font-size: 18px;
  }

  .slide-bullets {
    font-size: 14px;
  }
}
</style>
