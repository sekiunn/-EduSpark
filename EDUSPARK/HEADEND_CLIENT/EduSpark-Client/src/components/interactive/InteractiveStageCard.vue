<script setup>
import { computed } from 'vue'
import htmlIcon from '@/assets/images/HTML_en.svg'

const props = defineProps({
  cardData: {
    type: Object,
    default: () => ({})
  },
  busy: {
    type: Boolean,
    default: false
  }
})

defineEmits(['open'])

const statusClassMap = {
  preparing: 'is-preparing',
  implementing: 'is-implementing',
  refining: 'is-refining',
  completed: 'is-completed',
  failed: 'is-failed'
}

const resolveStatusClass = (status) => statusClassMap[status] || 'is-preparing'

const statusLabelMap = {
  preparing: '准备任务中',
  implementing: '实现互动页面中',
  refining: '细化互动体验中',
  completed: '已完成 · 可继续预览和修改',
  failed: '生成失败 · 点击进入查看'
}

const resolvedStatus = computed(() => props.cardData?.status || 'preparing')
const resolvedTitle = computed(() => props.cardData?.title || '互动页面工作区')
const metaText = computed(() => (
  statusLabelMap[resolvedStatus.value] || props.cardData?.statusText || '准备任务中'
))

const buildPreviewLines = (text, fallback) => {
  const cleaned = String(text || '').replace(/\s+/g, ' ').trim()
  const lines = []
  const chunkSize = 14

  if (cleaned) {
    for (let index = 0; index < cleaned.length && lines.length < 3; index += chunkSize) {
      lines.push(cleaned.slice(index, index + chunkSize))
    }
  }

  while (lines.length < 3) {
    lines.push(fallback[lines.length] || fallback[fallback.length - 1] || '')
  }

  return lines.slice(0, 3)
}

const previewLines = computed(() => buildPreviewLines(
  props.cardData?.summary,
  ['HTML 页面预览', '可在工作区实时查看', '继续通过对话修改']
))
</script>

<template>
  <div
    class="interactive-stage-card"
    :class="[resolveStatusClass(cardData?.status), { 'is-disabled': busy }]"
    role="button"
    tabindex="0"
    :aria-disabled="busy ? 'true' : 'false'"
    @click="!busy && $emit('open')"
    @keydown.enter.prevent="!busy && $emit('open')"
    @keydown.space.prevent="!busy && $emit('open')"
  >
    <div class="entry-content">
      <img :src="htmlIcon" alt="" class="entry-icon" />

      <div class="entry-title">{{ resolvedTitle }}</div>

      <div class="entry-meta">
        <span class="entry-meta-dot"></span>
        <span class="entry-meta-text">{{ metaText }}</span>
      </div>
    </div>

    <div class="entry-preview" aria-hidden="true">
      <div class="entry-preview-sheet">
        <div class="entry-preview-title">HTML 页面</div>
        <div
          v-for="(line, index) in previewLines"
          :key="`${index}-${line}`"
          class="entry-preview-line"
        >
          {{ line }}
        </div>
        <div class="entry-preview-glow"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.interactive-stage-card {
  --entry-accent-rgb: 72, 118, 255;
  --entry-progress-rgb: 14, 165, 140;
  width: min(520px, calc(100% - 12px));
  display: grid;
  grid-template-columns: minmax(0, 1fr) 170px;
  gap: 20px;
  align-items: stretch;
  padding: 20px 0 20px 20px;
  border: 1px solid rgba(226, 232, 240, 0.98);
  border-radius: 22px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(252, 252, 253, 1)),
    #fff;
  box-shadow:
    0 10px 24px rgba(15, 23, 42, 0.05),
    0 2px 5px rgba(15, 23, 42, 0.03);
  appearance: none;
  cursor: pointer;
  text-align: left;
  user-select: none;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
  overflow: hidden;
}

.interactive-stage-card:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(203, 213, 225, 1);
  box-shadow:
    0 14px 28px rgba(15, 23, 42, 0.07),
    0 3px 8px rgba(15, 23, 42, 0.04);
}

.interactive-stage-card.is-disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.interactive-stage-card:focus-visible {
  outline: 3px solid rgba(var(--entry-accent-rgb), 0.18);
  outline-offset: 3px;
}

.entry-content {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.entry-icon {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
}

.entry-title {
  margin-top: 18px;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
  color: #0f172a;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.entry-meta {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.entry-meta-dot {
  position: relative;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #94a3b8;
  flex-shrink: 0;
}

.entry-meta-dot::before,
.entry-meta-dot::after {
  content: '';
  position: absolute;
  inset: -4px;
  border-radius: 999px;
  border: 1px solid transparent;
  opacity: 0;
  transform: scale(0.82);
  pointer-events: none;
}

.entry-meta-text {
  font-size: 13px;
  line-height: 1.5;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.interactive-stage-card.is-preparing .entry-meta-dot,
.interactive-stage-card.is-implementing .entry-meta-dot,
.interactive-stage-card.is-refining .entry-meta-dot {
  background: rgb(var(--entry-progress-rgb));
  box-shadow: 0 0 0 2px rgba(var(--entry-progress-rgb), 0.10);
}

.interactive-stage-card.is-preparing .entry-meta-dot::before,
.interactive-stage-card.is-implementing .entry-meta-dot::before,
.interactive-stage-card.is-refining .entry-meta-dot::before {
  border-color: rgba(var(--entry-progress-rgb), 0.18);
  animation: entryRipplePulse 2.2s cubic-bezier(0.22, 1, 0.36, 1) infinite;
}

.interactive-stage-card.is-preparing .entry-meta-dot::after,
.interactive-stage-card.is-implementing .entry-meta-dot::after,
.interactive-stage-card.is-refining .entry-meta-dot::after {
  border-color: rgba(var(--entry-progress-rgb), 0.1);
  animation: entryRipplePulse 2.2s cubic-bezier(0.22, 1, 0.36, 1) infinite 1.1s;
}

.interactive-stage-card.is-completed .entry-meta-dot {
  background: #22c55e;
}

.interactive-stage-card.is-failed .entry-meta-dot {
  background: #ef4444;
}

@keyframes entryRipplePulse {
  0% {
    opacity: 0.42;
    transform: scale(0.82);
  }
  72% {
    opacity: 0;
    transform: scale(1.72);
  }
  100% {
    opacity: 0;
    transform: scale(1.72);
  }
}

.entry-preview {
  display: flex;
  align-items: stretch;
  justify-content: flex-end;
  min-width: 0;
}

.entry-preview-sheet {
  position: relative;
  width: 170px;
  min-height: 112px;
  margin-right: -16px;
  padding: 16px 16px 0;
  border: 1px solid #e7edf4;
  border-radius: 14px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(249, 251, 255, 0.98)),
    #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.entry-preview-title {
  font-size: 11px;
  font-weight: 700;
  color: #6b7280;
}

.entry-preview-line {
  margin-top: 8px;
  font-size: 11px;
  line-height: 1.45;
  color: #475569;
}

.entry-preview-glow {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: -24px;
  height: 72px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(var(--entry-accent-rgb), 0.10), rgba(var(--entry-accent-rgb), 0.02));
}

@media (max-width: 640px) {
  .interactive-stage-card {
    width: 100%;
    grid-template-columns: minmax(0, 1fr) 136px;
    gap: 16px;
    padding: 18px 0 18px 18px;
  }

  .entry-title {
    font-size: 16px;
  }

  .entry-preview-sheet {
    width: 136px;
    min-height: 104px;
  }
}

@media (max-width: 480px) {
  .interactive-stage-card {
    display: flex;
    padding: 18px;
  }

  .entry-preview {
    display: none;
  }
}
</style>
