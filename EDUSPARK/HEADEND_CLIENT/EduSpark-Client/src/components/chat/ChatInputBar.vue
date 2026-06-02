<script setup>
import { ref, computed, onMounted, onUnmounted, Transition } from 'vue'
import { formatFileSize } from '@/utils/format.js'

/**
 * 底部输入栏 —— welcome 落地页和正在对话页共用。
 *
 * 内部自管的纯 UI 状态：more menu 展开 / 各种 tooltip hover 状态 / 点外面关菜单。
 *
 * 通过 v-model 暴露 inputText，其他状态用 props 传入，用户操作用 emit 转发。
 */

const props = defineProps({
  modelValue: { type: String, default: '' },
  attachments: { type: Array, default: () => [] },
  isDraggingOver: { type: Boolean, default: false },
  isLoading: { type: Boolean, default: false },
  isSending: { type: Boolean, default: false },
  isRecording: { type: Boolean, default: false },
  voiceStatus: { type: String, default: '' },     // '' | 'processing' | 'error'
  searchMode: { type: String, default: 'off' },   // 'off' | 'auto'
  currentMode: { type: String, default: null },   // null | 'ppt' | 'lesson_plan' | 'interactive'
  placeholder: { type: String, default: '' },
  // PPT 落地页专用：在 textarea 旁边显示一个"已选模板"封面按钮
  showPptTemplateTrigger: { type: Boolean, default: false },
  selectedPptTemplate: { type: Object, default: null },
  pptTemplateSurfaceStyle: { type: Object, default: () => ({}) }
})

const emit = defineEmits([
  'update:modelValue',
  'send',
  'keydown',
  'drag-over',
  'drag-leave',
  'drop',
  'remove-attachment',
  'toggle-recording',
  'trigger-file-upload',
  'toggle-search-mode',
  'toggle-search-mode-from-tag',
  'exit-mode',
  'open-ppt-template-modal'
])

// === 内部 UI 状态 ===
const showMoreMenu = ref(false)
const dropdownPosition = ref('bottom')
const isTagHovered = ref(false)
const isModeTagHovered = ref(false)
const moreMenuContainerRef = ref(null)

// v-model 包装
const innerText = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const sendDisabled = computed(() =>
  !innerText.value?.trim() || props.isLoading || props.isSending
)

function toggleMoreMenu() {
  showMoreMenu.value = !showMoreMenu.value
}

function handleClickOutside(e) {
  if (moreMenuContainerRef.value && !moreMenuContainerRef.value.contains(e.target)) {
    showMoreMenu.value = false
  }
}

onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))

const MODE_LABELS = {
  ppt: 'PPT生成',
  lesson_plan: '教案生成',
  interactive: '互动内容'
}
function getModeLabel(mode) {
  return MODE_LABELS[mode] || ''
}

// 转发 textarea 事件
function onKeydown(e) {
  emit('keydown', e)
}
function onSend() {
  emit('send')
}
</script>

<template>
  <div class="input-area">
    <!-- 附件悬浮卡片 -->
    <div v-if="attachments.length > 0" class="attachment-card-list">
      <div
        v-for="att in attachments"
        :key="att.id"
        class="attachment-card"
        :class="{ 'att-loading': att.loading }"
      >
        <div class="att-card-icon">
          <div v-if="att.loading" class="att-spinner"></div>
          <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path>
            <polyline points="13 2 13 9 20 9"></polyline>
          </svg>
        </div>
        <div class="att-card-info">
          <span class="att-card-name">{{ att.name }}</span>
          <span class="att-card-size">{{ att.loading ? '解析中...' : formatFileSize(att.size) }}</span>
        </div>
        <button v-if="!att.loading" class="att-card-remove" @click.stop="emit('remove-attachment', att.id)">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
    </div>

    <!-- 输入卡片 -->
    <div
      class="input-card"
      :class="{ 'drag-over': isDraggingOver, 'ppt-mode-card': showPptTemplateTrigger }"
      @dragover="emit('drag-over', $event)"
      @dragleave="emit('drag-leave', $event)"
      @drop="emit('drop', $event)"
    >
      <!-- 拖拽毛玻璃遮罩 -->
      <Transition name="fade">
        <div v-if="isDraggingOver" class="drag-overlay">
          <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
            <polyline points="17 8 12 3 7 8"></polyline>
            <line x1="12" y1="3" x2="12" y2="15"></line>
          </svg>
          <span class="drag-title">释放以上传文件</span>
          <span class="drag-hint">支持 PDF、Word、PPT、图片、视频</span>
        </div>
      </Transition>

      <!-- PPT 落地页：textarea 左侧浮一个"已选模板"封面触发按钮 -->
      <div v-if="showPptTemplateTrigger" class="ppt-input-shell active">
        <button
          class="ppt-template-trigger"
          type="button"
          @click="emit('open-ppt-template-modal')"
        >
          <div
            v-if="selectedPptTemplate"
            class="ppt-template-trigger__surface"
            :style="!selectedPptTemplate.coverUrl ? pptTemplateSurfaceStyle : null"
          >
            <img
              v-if="selectedPptTemplate.coverUrl"
              :src="selectedPptTemplate.coverUrl"
              :alt="selectedPptTemplate.name"
              class="ppt-template-trigger__cover"
              loading="lazy"
            />
            <template v-else>
              <span class="ppt-template-trigger__badge">{{ selectedPptTemplate.badge }}</span>
              <strong class="ppt-template-trigger__name">{{ selectedPptTemplate.name }}</strong>
              <span class="ppt-template-trigger__meta">{{ selectedPptTemplate.style }}</span>
            </template>
          </div>
          <div v-else class="ppt-template-trigger__empty">
            <span class="ppt-template-trigger__empty-label">未选择</span>
            <span class="ppt-template-trigger__empty-hint">点击选择模板</span>
          </div>
        </button>

        <div class="ppt-input-main">
          <textarea
            rows="2"
            v-model="innerText"
            @keydown="onKeydown"
            :placeholder="placeholder"
          ></textarea>
        </div>
      </div>

      <!-- 普通：直接 textarea -->
      <textarea
        v-else
        rows="2"
        v-model="innerText"
        @keydown="onKeydown"
        :placeholder="placeholder"
      ></textarea>

      <!-- 工具栏：更多菜单 + 录音 + 模式标签 + 联网标签 + 发送 -->
      <div class="input-actions">
        <div class="left-actions">
          <div class="more-menu-container" ref="moreMenuContainerRef">
            <button class="icon-btn more-btn" @click="toggleMoreMenu">
              <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle>
                <line x1="12" y1="8" x2="12" y2="16"></line>
                <line x1="8" y1="12" x2="16" y2="12"></line>
              </svg>
            </button>
            <transition name="dropdown-fade">
              <div v-if="showMoreMenu" class="more-dropdown" :class="{ 'dropdown-top': dropdownPosition === 'top' }">
                <div class="dropdown-item" @click="emit('trigger-file-upload'); showMoreMenu = false">
                  <svg class="dropdown-icon-svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"></path>
                  </svg>
                  <span>文件上传</span>
                </div>
                <div class="dropdown-item has-submenu">
                  <svg class="dropdown-icon-svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"></circle>
                    <line x1="2" y1="12" x2="22" y2="12"></line>
                    <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path>
                  </svg>
                  <span>联网搜索</span>
                  <svg class="arrow-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="9 18 15 12 9 6"></polyline>
                  </svg>
                  <div class="submenu">
                    <div
                      class="submenu-item"
                      :class="{ active: searchMode === 'off' }"
                      @click="emit('toggle-search-mode', 'off')"
                    >
                      <span>关闭</span>
                      <span class="submenu-hint">只从本地知识库中获取信息</span>
                    </div>
                    <div
                      class="submenu-item"
                      :class="{ active: searchMode === 'auto' }"
                      @click="emit('toggle-search-mode', 'auto')"
                    >
                      <span>自动</span>
                      <span class="submenu-hint">自动判断联网获取信息</span>
                    </div>
                  </div>
                </div>
              </div>
            </transition>
          </div>
          <button class="icon-btn" @click="emit('toggle-recording')" :class="{ recording: isRecording }">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"></path>
              <path d="M19 10v2a7 7 0 0 1-14 0v-2"></path>
              <line x1="12" y1="19" x2="12" y2="23"></line>
              <line x1="8" y1="23" x2="16" y2="23"></line>
            </svg>
          </button>
          <Transition name="tag">
            <div
              v-if="currentMode"
              class="mode-tag-inline"
              @click="emit('exit-mode')"
              @mouseenter="isModeTagHovered = true"
              @mouseleave="isModeTagHovered = false"
            >
              <transition name="icon-fade" mode="out-in">
                <svg v-if="!isModeTagHovered" key="mode" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path v-if="currentMode === 'ppt'" d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path>
                  <polyline v-if="currentMode === 'ppt'" points="13 2 13 9 20 9"></polyline>
                  <rect v-if="currentMode === 'lesson_plan'" x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
                  <line v-if="currentMode === 'lesson_plan'" x1="9" y1="9" x2="15" y2="9"></line>
                  <line v-if="currentMode === 'lesson_plan'" x1="9" y1="13" x2="15" y2="13"></line>
                  <line v-if="currentMode === 'lesson_plan'" x1="9" y1="17" x2="11" y2="17"></line>
                  <path v-if="currentMode === 'interactive'" d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                  <circle v-if="currentMode === 'interactive'" cx="9" cy="7" r="4"></circle>
                  <path v-if="currentMode === 'interactive'" d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                  <path v-if="currentMode === 'interactive'" d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                </svg>
                <svg v-else key="close" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"></line>
                  <line x1="6" y1="6" x2="18" y2="18"></line>
                </svg>
              </transition>
              <span>{{ getModeLabel(currentMode) }}</span>
              <transition name="tooltip-fade">
                <div v-if="isModeTagHovered" class="tooltip">退出模式</div>
              </transition>
            </div>
          </Transition>
          <Transition name="tag">
            <div
              v-if="searchMode === 'auto'"
              class="search-mode-tag"
              @click="emit('toggle-search-mode-from-tag')"
              @mouseenter="isTagHovered = true"
              @mouseleave="isTagHovered = false"
            >
              <transition name="icon-fade" mode="out-in">
                <svg v-if="!isTagHovered" key="globe" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"></circle>
                  <line x1="2" y1="12" x2="22" y2="12"></line>
                  <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path>
                </svg>
                <svg v-else key="close" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"></line>
                  <line x1="6" y1="6" x2="18" y2="18"></line>
                </svg>
              </transition>
              <span>联网已开启</span>
              <transition name="tooltip-fade">
                <div v-if="isTagHovered" class="tooltip">关闭联网</div>
              </transition>
            </div>
          </Transition>
        </div>
        <button class="send-btn" @click="onSend" :disabled="sendDisabled">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2">
            <line x1="12" y1="19" x2="12" y2="5"></line>
            <polyline points="5 12 12 5 19 12"></polyline>
          </svg>
        </button>
      </div>
    </div>

    <div class="input-hint">
      <span>按 Enter 发送，Shift + Enter 换行</span>
    </div>

    <!-- 语音识别状态卡片 -->
    <Transition name="fade">
      <div v-if="voiceStatus === 'processing'" class="voice-wave-card">
        <div class="wave-dot-loader">
          <div></div><div></div><div></div><div></div><div></div>
        </div>
        <span class="wave-label">正在处理</span>
      </div>
      <div v-else-if="voiceStatus === 'error'" class="voice-error-card">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"></circle>
          <line x1="12" y1="8" x2="12" y2="12"></line>
          <line x1="12" y1="16" x2="12.01" y2="16"></line>
        </svg>
        <span>处理失败</span>
      </div>
    </Transition>
  </div>
</template>
