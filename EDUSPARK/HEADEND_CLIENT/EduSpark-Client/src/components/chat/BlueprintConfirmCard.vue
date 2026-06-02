<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import ChatModeIcon from './ChatModeIcon.vue'

const props = defineProps({
  cardData: { type: Object, default: null },
  mode: { type: String, default: 'lesson_plan' },
  modeName: { type: String, default: '' },
  busy: { type: Boolean, default: false },
  processed: { type: Boolean, default: false },
  supplementOpen: { type: Boolean, default: false },
  supplementText: { type: String, default: '' },
  supplementLimit: { type: Number, default: 500 }
})

const emit = defineEmits([
  'supplement',
  'confirm',
  'close-supplement',
  'submit-supplement',
  'update-supplement-text'
])

const supplementInputRef = ref(null)

const modeNameMap = {
  lesson_plan: '教案',
  ppt: 'PPT',
  interactive: '互动内容'
}

const buttons = computed(() => Array.isArray(props.cardData?.buttons) ? props.cardData.buttons : [])
const resolvedMode = computed(() => props.cardData?.mode || props.mode || 'lesson_plan')
const resolvedModeName = computed(() => props.cardData?.modeName || props.modeName || modeNameMap[resolvedMode.value] || '蓝图')

const isLessonPlan = computed(() => resolvedMode.value === 'lesson_plan')
const isPpt = computed(() => resolvedMode.value === 'ppt')
const isInteractive = computed(() => resolvedMode.value === 'interactive')

const cardThemeClass = computed(() => `blueprint-confirm-card--${resolvedMode.value}`)
const extraSectionsExpanded = ref(false)

const joinText = (items) => Array.isArray(items) ? items.filter(Boolean).join('、') : ''

const joinedKnowledgePoints = computed(() => joinText(props.cardData?.knowledgePoints?.map(item => item?.name)))
const joinedTeachingGoals = computed(() => props.cardData?.teachingGoals ? joinText(Object.values(props.cardData.teachingGoals)) : '')
const joinedKeyPoints = computed(() => joinText(props.cardData?.keyPoints?.map(item => item?.name)))
const joinedDifficultPoints = computed(() => joinText(props.cardData?.difficultPoints?.map(item => item?.name)))
const joinedInteractionHints = computed(() => joinText(props.cardData?.interactionHints || []))
const joinedUserConstraints = computed(() => joinText(props.cardData?.userConstraints || []))

const baseRows = computed(() => ([
  { label: '学科', value: props.cardData?.subject },
  { label: '年级', value: props.cardData?.grade },
  { label: '课题', value: props.cardData?.title },
  { label: '时长', value: props.cardData?.duration ? `${props.cardData.duration}分钟` : '' }
]).filter(item => item.value))

// 全替换链路下，成品页数 = 模板页数、视觉风格 = 模板本身，用户填的"页数/风格"都不会生效，
// 显示出来反而误导。故 PPT 确认卡不再展示这两项——modeRows 为空时整个"PPT 偏好"区块会自动隐藏。
const pptRows = computed(() => [])

const interactiveRows = computed(() => ([
  { label: '承载形式', value: props.cardData?.deliveryFormat },
  { label: '互动构想', value: props.cardData?.interactionIdea },
  { label: '使用场景', value: props.cardData?.usageScene },
  { label: '视觉风格', value: props.cardData?.visualStyle },
  { label: '动画级别', value: props.cardData?.animationLevel },
  { label: '题目数量', value: props.cardData?.questionCount ? `${props.cardData.questionCount}题` : '' },
  { label: '题型', value: props.cardData?.questionType }
]).filter(item => item.value))

const modeSectionTitle = computed(() => {
  if (isPpt.value) return 'PPT 偏好'
  if (isInteractive.value) return '互动设定'
  return ''
})

const modeRows = computed(() => {
  if (isPpt.value) return pptRows.value
  if (isInteractive.value) return interactiveRows.value
  return []
})

const isWideText = (value, threshold = 28) => String(value || '').length > threshold

const prioritizedSections = computed(() => ([
  { label: '补充说明', value: props.cardData?.notes || '', wide: true },
  { label: '知识点', value: joinedKnowledgePoints.value, wide: isWideText(joinedKnowledgePoints.value, 22) },
  { label: '教学目标', value: isLessonPlan.value ? joinedTeachingGoals.value : '', wide: isWideText(joinedTeachingGoals.value, 22) },
  { label: isPpt.value ? '重点内容' : '重点', value: joinedKeyPoints.value, wide: isWideText(joinedKeyPoints.value, 24) },
  { label: '难点', value: isLessonPlan.value ? joinedDifficultPoints.value : '', wide: isWideText(joinedDifficultPoints.value, 22) },
  { label: '互动提示', value: isInteractive.value ? joinedInteractionHints.value : '', wide: isWideText(joinedInteractionHints.value, 22) },
  { label: '约束条件', value: joinedUserConstraints.value, wide: isWideText(joinedUserConstraints.value, 22) }
]).filter(item => item.value))

const primarySections = computed(() => prioritizedSections.value.slice(0, 4))

const overflowSections = computed(() => prioritizedSections.value.slice(4))

const extraSections = computed(() => {
  const sections = []

  if (modeRows.value.length) {
    sections.push({
      label: modeSectionTitle.value,
      type: 'rows',
      rows: modeRows.value,
      wide: modeRows.value.length > 2
    })
  }

  overflowSections.value.forEach((section) => {
    sections.push({
      ...section,
      type: 'text'
    })
  })

  return sections
})

const extraSectionCount = computed(() => (
  extraSections.value.reduce((count, section) => count + (section.type === 'rows' ? section.rows.length : 1), 0)
))

const shouldCollapseExtras = computed(() => extraSections.value.length > 2 || extraSectionCount.value > 4)

const showExtraSections = computed(() => !shouldCollapseExtras.value || extraSectionsExpanded.value)

const supplementCount = computed(() => props.supplementText.length)
const showButton = (type) => buttons.value.length === 0 || buttons.value.some(button => button?.type === type)
const getButtonLabel = (type, fallback) => buttons.value.find(button => button?.type === type)?.label || fallback
const showActions = computed(() => !props.processed && (showButton('supplement') || showButton('confirm')))
const showSupplementComposer = computed(() => !props.processed && props.supplementOpen)
const showProcessedNote = computed(() => props.processed)
const processedNoteText = '该蓝图已处理，当前作为过程记录保留'

const updateSupplementText = (event) => {
  emit('update-supplement-text', event.target.value.slice(0, props.supplementLimit))
}

const handleSupplementKeydown = (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    emit('submit-supplement')
  }
}

const focusSupplementInput = async () => {
  await nextTick()
  if (!supplementInputRef.value) return
  supplementInputRef.value.focus()
  const cursor = props.supplementText.length
  supplementInputRef.value.setSelectionRange?.(cursor, cursor)
}

watch(() => props.supplementOpen, (open) => {
  if (open) focusSupplementInput()
})

watch(() => props.cardData, () => {
  extraSectionsExpanded.value = false
}, { deep: true })
</script>

<template>
  <div class="blueprint-confirm-card" :class="cardThemeClass">
    <div class="blueprint-confirm-card__header">
      <div class="blueprint-confirm-card__hero">
        <div class="blueprint-confirm-card__icon">
          <ChatModeIcon :mode="resolvedMode" />
        </div>
        <div class="blueprint-confirm-card__hero-copy">
          <div class="blueprint-confirm-card__header-meta">
            <span class="blueprint-confirm-card__badge">生成前确认</span>
            <span class="blueprint-confirm-card__mode-tag">{{ resolvedModeName }}蓝图</span>
          </div>
          <div class="blueprint-confirm-card__title">请确认本次蓝图信息</div>
          <p class="blueprint-confirm-card__subtitle">
            AI 已根据当前对话整理本次{{ resolvedModeName }}需求，确认后将进入工作区继续生成。
          </p>
        </div>
      </div>
    </div>

    <div class="blueprint-confirm-card__body">
      <div v-if="cardData" class="blueprint-confirm-card__content">
        <div v-if="baseRows.length" class="blueprint-confirm-card__overview">
          <div v-for="row in baseRows" :key="row.label" class="blueprint-confirm-card__overview-item">
            <span class="blueprint-confirm-card__overview-label">{{ row.label }}</span>
            <span class="blueprint-confirm-card__overview-value">{{ row.value }}</span>
          </div>
        </div>

        <div v-if="primarySections.length" class="blueprint-confirm-card__details">
          <div
            v-for="section in primarySections"
            :key="section.label"
            class="blueprint-confirm-card__panel blueprint-confirm-card__panel--detail"
            :class="{ 'blueprint-confirm-card__panel--wide': section.wide }"
          >
            <div class="blueprint-confirm-card__panel-title">{{ section.label }}</div>
            <template v-if="section.type === 'rows'">
              <div v-for="row in section.rows" :key="row.label" class="blueprint-confirm-card__row">
                <span class="blueprint-confirm-card__label">{{ row.label }}：</span>
                <span class="blueprint-confirm-card__value">{{ row.value }}</span>
              </div>
            </template>
            <div v-else class="blueprint-confirm-card__panel-content">{{ section.value }}</div>
          </div>
        </div>

        <div v-if="extraSections.length" class="blueprint-confirm-card__extra">
          <button
            v-if="shouldCollapseExtras"
            type="button"
            class="blueprint-confirm-card__extra-toggle"
            @click="extraSectionsExpanded = !extraSectionsExpanded"
          >
            <span>更多已提取信息（{{ extraSectionCount }}）</span>
            <svg
              class="blueprint-confirm-card__extra-toggle-icon"
              :class="{ 'is-open': extraSectionsExpanded }"
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <polyline points="6 9 12 15 18 9"></polyline>
            </svg>
          </button>

          <div v-if="showExtraSections" class="blueprint-confirm-card__details blueprint-confirm-card__details--secondary">
            <div
              v-for="section in extraSections"
              :key="section.label"
              class="blueprint-confirm-card__panel blueprint-confirm-card__panel--detail blueprint-confirm-card__panel--secondary"
              :class="{ 'blueprint-confirm-card__panel--wide': section.wide }"
            >
              <div class="blueprint-confirm-card__panel-title">{{ section.label }}</div>
              <template v-if="section.type === 'rows'">
                <div v-for="row in section.rows" :key="row.label" class="blueprint-confirm-card__row">
                  <span class="blueprint-confirm-card__label">{{ row.label }}：</span>
                  <span class="blueprint-confirm-card__value">{{ row.value }}</span>
                </div>
              </template>
              <div v-else class="blueprint-confirm-card__panel-content">{{ section.value }}</div>
            </div>
          </div>
        </div>

        <div class="blueprint-confirm-card__footer-note">
          确认后将进入对应工作区继续生成与编辑，也可以先补充更具体的要求。
        </div>
      </div>

      <div v-if="!cardData" class="blueprint-confirm-card__fallback">正在加载蓝图数据...</div>
    </div>

    <div v-if="showActions" class="blueprint-confirm-card__actions">
      <button
        v-if="showButton('supplement')"
        class="blueprint-confirm-card__button secondary"
        :disabled="busy"
        @click="emit('supplement')"
      >
        补充要求
      </button>
      <button
        v-if="showButton('confirm')"
        class="blueprint-confirm-card__button primary"
        :disabled="busy"
        @click="emit('confirm')"
      >
        {{ getButtonLabel('confirm', '确认并开始生成') }}
      </button>
    </div>

    <div v-else-if="showProcessedNote" class="blueprint-confirm-card__status-note">
      <span class="blueprint-confirm-card__status-dot" aria-hidden="true"></span>
      <span class="blueprint-confirm-card__status-text">{{ processedNoteText }}</span>
    </div>

    <div v-if="showSupplementComposer" class="blueprint-confirm-card__composer">
      <div class="blueprint-confirm-card__composer-title">补充要求</div>
      <textarea
        ref="supplementInputRef"
        class="blueprint-confirm-card__textarea"
        rows="4"
        :maxlength="supplementLimit"
        :value="supplementText"
        placeholder="如果 AI 理解有偏差，可直接补充这次生成需求，最多 500 字..."
        @input="updateSupplementText"
        @keydown="handleSupplementKeydown"
      ></textarea>
      <div class="blueprint-confirm-card__meta">
        <span class="blueprint-confirm-card__hint">这段内容会作为一条新的用户消息发给 AI</span>
        <span class="blueprint-confirm-card__counter">{{ supplementCount }}/{{ supplementLimit }}</span>
      </div>
      <div class="blueprint-confirm-card__composer-actions">
        <button class="blueprint-confirm-card__button secondary" :disabled="busy" @click="emit('close-supplement')">
          取消
        </button>
        <button
          class="blueprint-confirm-card__button primary"
          :disabled="busy || !supplementText.trim()"
          @click="emit('submit-supplement')"
        >
          确认补充
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.blueprint-confirm-card {
  --blueprint-accent-rgb: 37, 99, 235;
  --blueprint-accent: #2563eb;
  --blueprint-accent-strong: #1d4ed8;
  --blueprint-accent-soft: rgba(var(--blueprint-accent-rgb), 0.12);
  --blueprint-accent-surface: rgba(var(--blueprint-accent-rgb), 0.06);
  width: min(560px, calc(100% - 12px));
  max-width: min(100%, calc(100vw - 88px));
  position: relative;
  isolation: isolate;
  box-sizing: border-box;
  background:
    radial-gradient(circle at top right, var(--blueprint-accent-soft), transparent 36%),
    linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
  border: 1px solid rgba(203, 213, 225, 0.82);
  box-shadow:
    0 2px 6px rgba(15, 23, 42, 0.03),
    0 1px 0 rgba(255, 255, 255, 0.75) inset;
  border-radius: 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 20px 22px 22px;
  margin: 2px 6px 8px;
}

.blueprint-confirm-card::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: transparent;
  box-shadow:
    0 16px 28px rgba(15, 23, 42, 0.08),
    0 6px 12px rgba(15, 23, 42, 0.05);
  transform: translateY(3px) scale(0.992);
  transform-origin: center top;
  z-index: -1;
  opacity: 0.92;
  pointer-events: none;
}

.blueprint-confirm-card--ppt {
  --blueprint-accent-rgb: 79, 70, 229;
  --blueprint-accent: #4f46e5;
  --blueprint-accent-strong: #4338ca;
}

.blueprint-confirm-card--interactive {
  --blueprint-accent-rgb: 14, 116, 144;
  --blueprint-accent: #0e7490;
  --blueprint-accent-strong: #155e75;
}

.blueprint-confirm-card__header {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-bottom: 2px;
}

.blueprint-confirm-card__header-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 2px;
}

.blueprint-confirm-card__badge,
.blueprint-confirm-card__mode-tag {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.blueprint-confirm-card__badge {
  color: #0f172a;
  background: rgba(15, 23, 42, 0.06);
}

.blueprint-confirm-card__mode-tag {
  color: var(--blueprint-accent-strong);
  background: var(--blueprint-accent-surface);
}

.blueprint-confirm-card__hero {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.blueprint-confirm-card__hero-copy {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
}

.blueprint-confirm-card__icon {
  width: 50px;
  height: 50px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--blueprint-accent-strong);
  background:
    linear-gradient(180deg, rgba(var(--blueprint-accent-rgb), 0.12), rgba(var(--blueprint-accent-rgb), 0.04)),
    #fff;
  border: 1px solid rgba(var(--blueprint-accent-rgb), 0.14);
  box-shadow: 0 6px 16px rgba(var(--blueprint-accent-rgb), 0.10);
  flex-shrink: 0;
}

.blueprint-confirm-card__title {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.3;
  color: #0f172a;
}

.blueprint-confirm-card__subtitle {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  color: #526277;
}

.blueprint-confirm-card__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.blueprint-confirm-card__content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.blueprint-confirm-card__overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.blueprint-confirm-card__overview-item,
.blueprint-confirm-card__panel {
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow:
    0 2px 6px rgba(15, 23, 42, 0.03),
    inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.blueprint-confirm-card__overview-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 92px;
  padding: 16px;
}

.blueprint-confirm-card__overview-label,
.blueprint-confirm-card__panel-title,
.blueprint-confirm-card__label,
.blueprint-confirm-card__composer-title {
  font-size: 13px;
  font-weight: 700;
  color: #475569;
}

.blueprint-confirm-card__overview-value {
  font-size: 15px;
  line-height: 1.5;
  color: #0f172a;
  font-weight: 600;
}

.blueprint-confirm-card__details {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.blueprint-confirm-card__details--secondary {
  margin-top: 10px;
}

.blueprint-confirm-card__panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 15px;
}

.blueprint-confirm-card__panel--detail {
  min-height: 100%;
}

.blueprint-confirm-card__panel--secondary {
  background: rgba(249, 251, 255, 0.92);
}

.blueprint-confirm-card__panel--wide {
  grid-column: 1 / -1;
}

.blueprint-confirm-card__row {
  display: flex;
  gap: 6px;
  align-items: flex-start;
}

.blueprint-confirm-card__value,
.blueprint-confirm-card__panel-content,
.blueprint-confirm-card__fallback,
.blueprint-confirm-card__hint,
.blueprint-confirm-card__counter {
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
}

.blueprint-confirm-card__footer-note {
  padding: 14px 15px;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
  border: 1px solid rgba(var(--blueprint-accent-rgb), 0.10);
  border-radius: 16px;
  background: rgba(var(--blueprint-accent-rgb), 0.05);
}

.blueprint-confirm-card__extra {
  display: flex;
  flex-direction: column;
}

.blueprint-confirm-card__extra-toggle {
  width: 100%;
  min-height: 46px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 16px;
  border: 1px solid rgba(var(--blueprint-accent-rgb), 0.16);
  border-radius: 14px;
  background: rgba(var(--blueprint-accent-rgb), 0.04);
  color: #334155;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease;
}

.blueprint-confirm-card__extra-toggle:hover {
  border-color: rgba(var(--blueprint-accent-rgb), 0.28);
  background: rgba(var(--blueprint-accent-rgb), 0.07);
}

.blueprint-confirm-card__extra-toggle-icon {
  flex-shrink: 0;
  transition: transform 0.18s ease;
}

.blueprint-confirm-card__extra-toggle-icon.is-open {
  transform: rotate(180deg);
}

.blueprint-confirm-card__actions,
.blueprint-confirm-card__composer-actions {
  display: flex;
  gap: 10px;
}

.blueprint-confirm-card__status-note {
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border: 1px solid rgba(var(--blueprint-accent-rgb), 0.12);
  border-radius: 14px;
  background: rgba(var(--blueprint-accent-rgb), 0.045);
}

.blueprint-confirm-card__status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: rgba(var(--blueprint-accent-rgb), 0.78);
  box-shadow: 0 0 0 4px rgba(var(--blueprint-accent-rgb), 0.08);
  flex-shrink: 0;
}

.blueprint-confirm-card__status-text {
  font-size: 13px;
  line-height: 1.6;
  color: #526277;
}

.blueprint-confirm-card__button {
  flex: 1;
  min-height: 46px;
  border-radius: 14px;
  border: 1px solid #d4dbe5;
  background: #fff;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease;
}

.blueprint-confirm-card__button.primary {
  border-color: var(--blueprint-accent);
  background: linear-gradient(180deg, var(--blueprint-accent) 0%, var(--blueprint-accent-strong) 100%);
  color: #fff;
  box-shadow: 0 10px 18px rgba(var(--blueprint-accent-rgb), 0.18);
}

.blueprint-confirm-card__button.secondary {
  background: rgba(255, 255, 255, 0.92);
}

.blueprint-confirm-card__button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 10px 18px rgba(15, 23, 42, 0.08);
}

.blueprint-confirm-card__button.secondary:hover:not(:disabled) {
  border-color: rgba(var(--blueprint-accent-rgb), 0.34);
  color: var(--blueprint-accent-strong);
}

.blueprint-confirm-card__button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}

.blueprint-confirm-card__composer {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 15px 15px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(var(--blueprint-accent-rgb), 0.05), rgba(255, 255, 255, 0.95));
}

.blueprint-confirm-card__textarea {
  width: 100%;
  min-height: 112px;
  resize: vertical;
  border: 1px solid #cbd5e1;
  border-radius: 14px;
  padding: 12px;
  font-size: 14px;
  line-height: 1.7;
  outline: none;
  background: #fff;
}

.blueprint-confirm-card__textarea:focus {
  border-color: rgba(var(--blueprint-accent-rgb), 0.55);
  box-shadow: 0 0 0 4px rgba(var(--blueprint-accent-rgb), 0.10);
}

.blueprint-confirm-card__meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

@media (max-width: 720px) {
  .blueprint-confirm-card {
    max-width: 100%;
  }

  .blueprint-confirm-card__overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .blueprint-confirm-card {
    width: 100%;
    max-width: 100%;
    padding: 18px;
  }

  .blueprint-confirm-card__hero {
    flex-direction: column;
  }

  .blueprint-confirm-card__overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .blueprint-confirm-card__details {
    grid-template-columns: 1fr;
  }

  .blueprint-confirm-card__actions,
  .blueprint-confirm-card__composer-actions,
  .blueprint-confirm-card__meta {
    flex-direction: column;
  }
}

@media (max-width: 480px) {
  .blueprint-confirm-card__overview {
    grid-template-columns: 1fr;
  }
}
</style>
