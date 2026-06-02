<script setup>
import { ref } from 'vue'
import { renderBody, renderMarkdown } from '@/utils/markdown.js'
import {
  hasReferences,
  getRefCount,
  parseReferenceList
} from '@/utils/references.js'
import BlueprintConfirmCard from '@/components/chat/BlueprintConfirmCard.vue'
import ChatModeIcon from '@/components/chat/ChatModeIcon.vue'
import LessonPlanStageCard from '@/components/lesson-plan/LessonPlanStageCard.vue'
import PptStageCard from '@/components/ppt/PptStageCard.vue'
import InteractiveStageCard from '@/components/interactive/InteractiveStageCard.vue'

/**
 * 消息流：v-for 渲染所有 messages，按 role + cardType 分支出不同 UI。
 *
 * 自管的"展开引用文献"状态在组件内部维护（per-instance UI 状态，不需要冒泡）。
 * 其他状态都通过 props 传入；用户动作通过 emit 转发到父组件主流程处理。
 */

const props = defineProps({
  messages: { type: Array, default: () => [] },
  isLoading: { type: Boolean, default: false },
  isSending: { type: Boolean, default: false },
  currentMode: { type: String, default: null },

  // 复制按钮 UX 标记：被点过的 msg.id 短暂显示"对勾"
  copiedMsgId: { type: [String, Number, null], default: null },

  // 蓝图确认卡片
  blueprintSupplementText: { type: String, default: '' },
  blueprintSupplementLimit: { type: Number, default: 500 },
  activeBlueprintComposerId: { type: [String, Number, null], default: null },

  // 三个工作区的 loading（用于卡片上的"打开工作区"按钮 disabled）
  lessonPlanWorkspaceLoading: { type: Boolean, default: false },
  pptWorkspaceLoading: { type: Boolean, default: false },
  interactiveWorkspaceLoading: { type: Boolean, default: false }
})

const emit = defineEmits([
  'reset-copy-icon',
  'copy-message',
  'retry-message',
  'like-message',
  'dislike-message',
  'supplement-blueprint',
  'close-blueprint-supplement',
  'submit-blueprint-supplement',
  'update:blueprint-supplement-text',
  'confirm-blueprint',
  'open-lesson-plan-workspace',
  'open-ppt-workspace',
  'open-interactive-workspace',
  'generation-card-download'
])

const conversationArea = ref(null)

// 把 DOM ref 暴露给父组件，让父组件能调 scrollToBottom 这种命令式操作
defineExpose({ conversationArea })

// === 内部状态：哪些消息的"引用文献"区是展开的 ===
const expandedRefs = ref({})
function toggleRef(msgId) {
  expandedRefs.value[msgId] = !expandedRefs.value[msgId]
}
function isRefExpanded(msgId) {
  return !!expandedRefs.value[msgId]
}

// === 卡片元数据辅助 ===
const MODE_NAMES = {
  ppt: 'PPT',
  lesson_plan: '教案',
  interactive: '互动内容'
}
function getModeName(mode) {
  return MODE_NAMES[mode] || '内容'
}
function getMessageMode(msg) {
  return msg?.cardData?.mode || props.currentMode
}
function getMessageModeName(msg) {
  return msg?.cardData?.modeName || getModeName(getMessageMode(msg))
}

function isBlueprintSupplementOpen(msg) {
  return props.activeBlueprintComposerId === msg?.id
}

function messageHasFollowUps(index) {
  return index >= 0 && index < props.messages.length - 1
}
</script>

<template>
  <div class="conversation-area" ref="conversationArea">
    <div
      class="message"
      :class="msg.role"
      v-for="(msg, index) in messages"
      :key="msg.id"
      @mouseleave="emit('reset-copy-icon')"
    >
      <!-- AI 消息 -->
      <div v-if="msg.role === 'ai'" class="ai-message">
        <div class="ai-message-column">
          <div
            class="ai-bubble"
            :class="{
              'card-message': !!msg.cardType,
              'standalone-card-message': msg.cardType === 'generation_pending'
                || msg.cardType === 'lesson_plan_stage_entry'
                || msg.cardType === 'ppt_stage_entry'
                || msg.cardType === 'interactive_stage_entry'
            }"
          >
            <div v-if="msg.loading" class="typing-dots">
              <span></span><span></span><span></span>
            </div>
            <template v-else>
              <!-- 蓝图确认卡 -->
              <template v-if="msg.cardType === 'blueprint_confirm'">
                <div class="card-message-stack">
                  <BlueprintConfirmCard
                    :card-data="msg.cardData"
                    :mode="getMessageMode(msg)"
                    :mode-name="getMessageModeName(msg)"
                    :busy="isLoading || isSending"
                    :processed="messageHasFollowUps(index)"
                    :supplement-open="isBlueprintSupplementOpen(msg)"
                    :supplement-text="blueprintSupplementText"
                    :supplement-limit="blueprintSupplementLimit"
                    @supplement="emit('supplement-blueprint', msg)"
                    @confirm="emit('confirm-blueprint')"
                    @close-supplement="emit('close-blueprint-supplement')"
                    @submit-supplement="emit('submit-blueprint-supplement')"
                    @update-supplement-text="emit('update:blueprint-supplement-text', $event)"
                  />
                  <div
                    v-if="msg.content"
                    class="card-message-text message-body"
                    v-html="renderBody(msg.content)"
                  ></div>
                </div>
              </template>

              <!-- 教案 / PPT / 互动 stage 入口卡 -->
              <template v-else-if="msg.cardType === 'lesson_plan_stage_entry'">
                <div class="card-message-stack">
                  <LessonPlanStageCard
                    :card-data="msg.cardData"
                    :busy="lessonPlanWorkspaceLoading"
                    @open="emit('open-lesson-plan-workspace', msg.cardData)"
                  />
                  <div
                    v-if="msg.content"
                    class="card-message-text message-body"
                    v-html="renderBody(msg.content)"
                  ></div>
                </div>
              </template>

              <template v-else-if="msg.cardType === 'ppt_stage_entry'">
                <div class="card-message-stack">
                  <PptStageCard
                    :card-data="msg.cardData"
                    :busy="pptWorkspaceLoading"
                    @open="emit('open-ppt-workspace', msg.cardData)"
                  />
                  <div
                    v-if="msg.content"
                    class="card-message-text message-body"
                    v-html="renderBody(msg.content)"
                  ></div>
                </div>
              </template>

              <template v-else-if="msg.cardType === 'interactive_stage_entry'">
                <div class="card-message-stack">
                  <InteractiveStageCard
                    :card-data="msg.cardData"
                    :busy="interactiveWorkspaceLoading"
                    @open="emit('open-interactive-workspace', msg.cardData)"
                  />
                  <div
                    v-if="msg.content"
                    class="card-message-text message-body"
                    v-html="renderBody(msg.content)"
                  ></div>
                </div>
              </template>

              <!-- 进行中的 generation_pending（不可点的占位卡） -->
              <div v-else-if="msg.cardType === 'generation_pending'" class="bp-card stage-entry-card">
                <div class="bp-card-header">
                  <div class="bp-card-icon">
                    <ChatModeIcon :mode="getMessageMode(msg)" />
                  </div>
                  <div class="bp-card-title">{{ getMessageModeName(msg) }}生成任务</div>
                </div>
                <div class="bp-card-subtitle">
                  <div class="bp-info-row">
                    <span class="bp-info-label">状态：</span>
                    <span class="bp-info-value">
                      <span class="bp-status-chip processing">
                        {{ msg.cardData?.statusText || '正在生成' }}
                      </span>
                    </span>
                  </div>
                  <div class="bp-info-row" v-if="msg.cardData?.title">
                    <span class="bp-info-label">课题：</span>
                    <span class="bp-info-value">{{ msg.cardData.title }}</span>
                  </div>
                  <div class="bp-info-section">
                    <span class="bp-section-label">说明：</span>
                    <span class="bp-section-value">{{ msg.cardData?.summary || msg.content }}</span>
                  </div>
                </div>
                <hr class="bp-card-divider">
                <div class="bp-card-footer stage-entry-footer">
                  <span class="bp-card-note">生成完成后会自动追加结果卡片</span>
                  <span class="bp-btn primary static">生成中</span>
                </div>
              </div>

              <!-- 生成完成卡 -->
              <div v-else-if="msg.cardType === 'generation_complete'" class="generation-card">
                <div class="generation-card-header">
                  <div class="generation-card-title">
                    {{ getMessageModeName(msg) }}{{ msg.cardData?.title ? ' - ' + msg.cardData.title : '' }}
                  </div>
                  <span class="generation-badge" :class="msg.cardData?.status || (msg.cardType === 'generation_pending' ? 'processing' : 'completed')">
                    {{ msg.cardData?.statusText || '已生成' }}
                  </span>
                </div>
                <div class="generation-card-body">
                  <p class="generation-summary">{{ msg.cardData?.summary || msg.content }}</p>
                  <div v-if="msg.cardData?.fileName" class="generation-meta">
                    <span>文件</span>
                    <span>{{ msg.cardData.fileName }}</span>
                  </div>
                  <div v-if="msg.cardData?.preview" class="generation-preview">{{ msg.cardData.preview }}</div>
                  <div v-if="msg.cardData?.outline?.length" class="generation-outline">
                    <div v-for="(item, idx) in msg.cardData.outline" :key="idx" class="generation-outline-item">
                      {{ idx + 1 }}. {{ item }}
                    </div>
                  </div>
                </div>
                <div v-if="msg.cardData?.downloadUrl" class="generation-card-actions">
                  <button
                    class="generation-link"
                    type="button"
                    @click="emit('generation-card-download', msg.cardData.downloadUrl, msg.cardData.fileName || '下载文件')"
                  >
                    下载文件
                  </button>
                </div>
              </div>

              <!-- 普通文本 AI 回复 -->
              <div v-else class="message-body" v-html="renderBody(msg.content)"></div>

              <!-- 引用文献块（仅对纯文本回复展示） -->
              <div
                v-if="!msg.cardType && hasReferences(msg.content)"
                class="references"
                :class="{ expanded: isRefExpanded(msg.id) }"
              >
                <div class="references-header" @click="toggleRef(msg.id)">
                  <svg class="references-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="6 9 12 15 18 9"></polyline>
                  </svg>
                  <span>引用</span>
                </div>
                <div class="references-list" v-show="isRefExpanded(msg.id)">
                  <div
                    v-for="(ref, idx) in parseReferenceList(msg.content)"
                    :key="idx"
                    class="reference-tag"
                    :data-num="ref.num"
                  >{{ ref.text }}</div>
                </div>
                <div class="references-stats">
                  <span class="stat-item">{{ getRefCount(msg.content) }}条引用</span>
                  <template v-if="msg.costMs">
                    <span class="stat-item">{{ (msg.costMs / 1000).toFixed(2) }}s</span>
                  </template>
                  <span class="stat-action" @click="toggleRef(msg.id)">
                    {{ isRefExpanded(msg.id) ? '收起' : '查看详情' }}
                  </span>
                </div>
              </div>
            </template>
          </div>

          <!-- AI 消息底部工具栏 -->
          <div v-if="!msg.loading" class="message-actions ai-actions">
            <button class="action-btn" :class="{ active: msg.liked }" @click="emit('like-message', msg)" title="点赞">
              <svg viewBox="0 0 24 24" :fill="msg.liked ? 'var(--es-text-secondary)' : 'none'" stroke="var(--es-text-secondary)" stroke-width="2"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"></path></svg>
            </button>
            <button class="action-btn" :class="{ active: msg.disliked }" @click="emit('dislike-message', msg)" title="点踩">
              <svg viewBox="0 0 24 24" :fill="msg.disliked ? 'var(--es-text-secondary)' : 'none'" stroke="var(--es-text-secondary)" stroke-width="2"><path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3zM17 2h2.67A2.31 2.31 0 0 1 22 4v7a2.31 2.31 0 0 1-2.33 2H17"></path></svg>
            </button>
            <button class="action-btn" :class="{ active: copiedMsgId === msg.id }" @click="emit('copy-message', msg)" title="复制">
              <svg v-if="copiedMsgId === msg.id" viewBox="0 0 24 24" fill="none" stroke="var(--es-text-secondary)" stroke-width="2"><polyline points="20 6 9 17 4 12"></polyline></svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="var(--es-text-secondary)" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
            </button>
          </div>
        </div>
      </div>

      <!-- 用户消息 -->
      <template v-else>
        <div class="user-message-content">
          <div class="bubble">
            <div class="message-content" v-html="renderMarkdown(msg.content)"></div>
          </div>
          <div class="message-actions user-actions">
            <button class="action-btn" @click="emit('retry-message', msg)" title="重试">
              <svg viewBox="0 0 24 24" fill="none" stroke="var(--es-text-secondary)" stroke-width="2"><polyline points="23 4 23 10 17 10"></polyline><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path></svg>
            </button>
            <button class="action-btn" :class="{ active: copiedMsgId === msg.id }" @click="emit('copy-message', msg)" title="复制">
              <svg v-if="copiedMsgId === msg.id" viewBox="0 0 24 24" fill="none" stroke="var(--es-text-secondary)" stroke-width="2"><polyline points="20 6 9 17 4 12"></polyline></svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="var(--es-text-secondary)" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
            </button>
          </div>
        </div>
      </template>
    </div>

    <!-- AI 正在输入的占位（尚未推送到 messages 时显示） -->
    <div v-if="isLoading && !messages.some(m => m.role === 'ai')" class="message ai">
      <div class="ai-message">
        <div class="ai-message-column">
          <div class="ai-bubble ai-bubble-loading">
            <div class="typing-dots">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
