<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import {
  sendMessage as sendMessageApi,
  sendMessageWithFiles,
  sendMessageStream,
  checkOllamaHealth,
  createRecorder,
  RecordingState,
  formatDuration,
  getSessionDetail,
  exitSessionMode,
  getGenerationStatus,
  getLessonPlanDocument,
  uploadFileAsync,
  getFileDetail
} from '@/api'
import { triggerAuthRequired } from '@/composables/authState.js'
import { useAuth } from '@/composables/useAuth.js'
import { useInteractiveWorkspace } from '@/composables/useInteractiveWorkspace.js'
import { useKnowledgeBase } from '@/composables/useKnowledgeBase.js'
import { useLessonPlanWorkspace } from '@/composables/useLessonPlanWorkspace.js'
import { usePptWorkspace } from '@/composables/usePptWorkspace.js'
import { usePptTemplate } from '@/composables/usePptTemplate.js'
import { useUserMenu } from '@/composables/useUserMenu.js'
import { useChatHistory } from '@/composables/useChatHistory.js'
import {
  useLessonPlanStageStream,
  isLessonPlanTerminalStatus
} from '@/composables/useLessonPlanStageStream.js'
import { useAttachments } from '@/composables/useAttachments.js'
import AuthModal from '@/components/AuthModal.vue'
import PersonalCenterModal from '@/components/PersonalCenterModal.vue'
import BlueprintConfirmCard from '@/components/chat/BlueprintConfirmCard.vue'
import ChatModeIcon from '@/components/chat/ChatModeIcon.vue'
import InteractiveStageCard from '@/components/interactive/InteractiveStageCard.vue'
import InteractiveWorkspace from '@/components/interactive/InteractiveWorkspace.vue'
import KnowledgeDrawer from '@/components/knowledge/KnowledgeDrawer.vue'
import LessonPlanStageCard from '@/components/lesson-plan/LessonPlanStageCard.vue'
import LessonPlanWorkspace from '@/components/lesson-plan/LessonPlanWorkspace.vue'
import PptStageCard from '@/components/ppt/PptStageCard.vue'
import PptTemplateSelectorModal from '@/components/ppt/PptTemplateSelectorModal.vue'
import PptWorkspace from '@/components/ppt/PptWorkspace.vue'
import ChatSidebar from '@/components/chat/ChatSidebar.vue'
import ChatInputBar from '@/components/chat/ChatInputBar.vue'
import ChatMessageList from '@/components/chat/ChatMessageList.vue'
import { renderBody, renderMarkdown } from '@/utils/markdown.js'
import {
  extractReferences,
  hasReferences,
  getRefCount,
  parseReferenceList
} from '@/utils/references.js'
import {
  formatDate,
  formatFileSize,
  getFileTypeName,
  getDocIcon,
  getTimeGroupLabel
} from '@/utils/format.js'
import {
  normalizeCoursewareDownloadPath,
  resolveDownloadUrl,
  extractDownloadFileName,
  saveBlobAsFile,
  downloadProtectedFile
} from '@/utils/download.js'

// expandedRefs 是组件内状态（per-instance），保留在这里；toggle/isExpanded 这两个
// 操作 ref 的小函数也跟着留在组件里——它们跟 expandedRefs 紧耦合。
const expandedRefs = ref({})

const toggleRef = (msgId) => {
  expandedRefs.value[msgId] = !expandedRefs.value[msgId]
}

const isRefExpanded = (msgId) => {
  return !!expandedRefs.value[msgId]
}

// ==================== 状态 ====================
const sidebarOpen = ref(true)
// historyPanelOpen / historyPanelSearch 由 useChatHistory 管理（在下面初始化）
// userSectionRef / userMenuStyle 已搬到 ChatSidebar 内部
const knowledgeDrawerOpen = ref(false)
const showDrawerBtn = ref(true)
const currentView = ref('chat')
const WORKSPACE_UI_RESTORE_DELAY = 380
const sidebarOpenBeforeWorkspace = ref(null)
const inputText = ref('')
const messages = ref([])
const isTransitioning = ref(false)
const copiedMsgId = ref(null)  // 复制的消息ID，用于显示勾号
const showMoreMenu = ref(false)
const searchMode = ref('off')
const moreMenuContainers = ref([])
const moreMenuContainerRef = ref(null)
const isTagHovered = ref(false)
const isModeTagHovered = ref(false)
const dropdownPosition = ref('bottom')
// userMenuOpen / authModalVisible / personalCenterVisible 由 useUserMenu 管理（在 useAuth 之后初始化）
// historyMenuOpen / editingTitle / editingTitleText / editTitleModalVisible /
// deleteConfirmModalVisible / sessionToDelete 由 useChatHistory 管理

// ==================== 当前会话 ====================
const currentSessionId = ref(null)  // 当前会话ID
const currentMode = ref(null)       // 当前教学模式（ppt/lesson_plan/interactive）
// PPT 模板的状态与方法见 @/composables/usePptTemplate.js
const {
  pptTemplateModalVisible,
  selectedPptTemplateId,
  selectedPptTemplate,
  pptTemplates,
  pptTemplatesLoading,
  pptTemplatesLoaded,
  pptTemplatesError,
  ensurePptTemplatesLoaded,
  openPptTemplateModal,
  reloadPptTemplates,
  handlePptTemplateSelect,
  resetPptTemplateState,
  getPptTemplateSurfaceStyle,
  decoratePptMessageForRequest: decoratePptMessageFn
} = usePptTemplate()
let generationPollingTimer = null  // 生成状态轮询定时器
// 教案 SSE 流的状态由 useLessonPlanStageStream 内部闭包管理
let drawerButtonRestoreTimer = null
let workspaceUiRestoreTimer = null

watch(currentMode, (val) => {
  if (!val) {
    isModeTagHovered.value = false
  }
  if (val !== 'ppt') {
    resetPptTemplateState()
  }
})

const isPptEmptyLanding = computed(() => currentMode.value === 'ppt' && messages.value.length === 0)
const currentInputPlaceholder = computed(() =>
  currentMode.value === 'ppt'
    ? '输入你想创作的 PPT 主题'
    : '输入您的教学想法，或上传资料...'
)

// ==================== 用户ID（从登录信息获取）====================
const currentUserId = computed(() => userInfo.value?.userId || null)

// ==================== 加载状态 ====================
const isLoading = ref(false)
const isSending = ref(false)  // 防止重复发送
const messageListRef = ref(null)  // ref 到 ChatMessageList 子组件实例
// conversationArea：通过 ChatMessageList expose 的 conversationArea 拿底层 DOM
const conversationArea = computed(() => messageListRef.value?.conversationArea || null)
const activeBlueprintComposerId = ref(null)
const blueprintSupplementText = ref('')
const BLUEPRINT_SUPPLEMENT_LIMIT = 500
const BLUEPRINT_CONFIRM_MESSAGE = '我确认以上蓝图信息无误，请开始生成。'

const messageHasFollowUps = (index) => index >= 0 && index < messages.value.length - 1

// ==================== 语音录音 ====================
const isRecording = ref(false)
const recordingDuration = ref(0)
const transcribedText = ref('')
const voiceStatus = ref('') // '' | 'processing' | 'error'
let recorder = null
let durationTimer = null

// ==================== 聊天历史 ====================
// historyList / 改名删除 modal state / loadHistoryList 等见 @/composables/useChatHistory.js
const {
  historyList,
  historyPanelOpen,
  historyPanelSearch,
  historyMenuOpen,
  editingTitle,
  editingTitleText,
  editTitleModalVisible,
  deleteConfirmModalVisible,
  sessionToDelete,
  filteredHistoryList,
  groupedHistoryList,
  loadHistoryList,
  toggleHistoryMenu,
  handleEditTitle,
  confirmEditTitle,
  handleDeleteSession,
  confirmDeleteSession
} = useChatHistory({
  // 删除的就是当前会话 → 主流程清空 + 新开
  onSessionDeleted: async (deletedId) => {
    if (currentSessionId.value === deletedId) {
      await startNewChat()
    }
  }
})

// ==================== 登录/注册弹窗 ====================
const { userInfo, isLoggedIn, fetchUserInfo, logout } = useAuth()
const {
  authModalVisible,
  personalCenterVisible,
  userMenuOpen,
  openAuthModal,
  openPersonalCenter,
  openSettings,
  toggleUserMenu,
  handleAuthRequired
} = useUserMenu(isLoggedIn)

const {
  knowledgeDocs,
  knowledgeLoading,
  isUploading,
  uploadProgress,
  uploadQueue,
  selectedDocId,
  searchKeyword,
  currentCategory,
  viewMode,
  referencedFileId,
  showCategoryModal,
  pendingUploadFiles,
  selectedCategory,
  categoryOptions,
  fileInputRef,
  folderInputRef,
  filteredDocs,
  workspaces,
  workspacesLoading,
  selectedWorkspaceId,
  workspaceCounts,
  loadWorkspaces,
  setSelectedWorkspace,
  createWorkspace: createKnowledgeWorkspace,
  renameWorkspace: renameKnowledgeWorkspace,
  removeWorkspace: removeKnowledgeWorkspace,
  selectDoc,
  quoteDoc,
  triggerFileUpload,
  triggerFolderImport,
  uploadSingleFile,
  loadKnowledgeFiles,
  handleDeleteFile,
  handleRetryFile,
  cleanup: cleanupKnowledgeBase
} = useKnowledgeBase(currentUserId, {
  onQuoteDoc: (doc) => {
    inputText.value = `\u8bf7\u53c2\u8003\u77e5\u8bc6\u5e93\u6587\u4ef6\u300a${doc.name}\u300b\u7684\u5185\u5bb9\u6765\u56de\u7b54\u6211\u7684\u95ee\u9898\u3002`
    switchToChat()
  }
})

const {
  visible: lessonPlanWorkspaceVisible,
  currentDocumentId: lessonPlanWorkspaceDocumentId,
  document: lessonPlanWorkspaceDocument,
  draftContent: lessonPlanWorkspaceContent,
  streamingText: lessonPlanWorkspaceStreamingText,
  loading: lessonPlanWorkspaceLoading,
  saving: lessonPlanWorkspaceSaving,
  exporting: lessonPlanWorkspaceExporting,
  rewriting: lessonPlanWorkspaceRewriting,
  rewriteSuggestion: lessonPlanWorkspaceRewriteSuggestion,
  error: lessonPlanWorkspaceError,
  streamConnected: lessonPlanWorkspaceStreamConnected,
  streamError: lessonPlanWorkspaceStreamError,
  isDirty: lessonPlanWorkspaceDirty,
  openWorkspace: openLessonPlanWorkspace,
  closeWorkspace: closeLessonPlanWorkspace,
  resetWorkspace: resetLessonPlanWorkspace,
  saveDocument: saveLessonPlanWorkspaceDocument,
  requestRewrite: requestLessonPlanWorkspaceRewrite,
  clearRewriteSuggestion: clearLessonPlanWorkspaceRewriteSuggestion,
  exportDocument: exportLessonPlanWorkspaceDocument
} = useLessonPlanWorkspace()

const {
  visible: interactiveWorkspaceVisible,
  currentDocumentId: interactiveWorkspaceDocumentId,
  document: interactiveWorkspaceDocument,
  draftContent: interactiveWorkspaceContent,
  streamingText: interactiveWorkspaceStreamingText,
  loading: interactiveWorkspaceLoading,
  saving: interactiveWorkspaceSaving,
  exporting: interactiveWorkspaceExporting,
  error: interactiveWorkspaceError,
  streamConnected: interactiveWorkspaceStreamConnected,
  streamError: interactiveWorkspaceStreamError,
  streamState: interactiveWorkspaceStreamState,
  isDirty: interactiveWorkspaceDirty,
  openWorkspace: openInteractiveWorkspace,
  closeWorkspace: closeInteractiveWorkspace,
  resetWorkspace: resetInteractiveWorkspace,
  saveDocument: saveInteractiveWorkspaceDocument,
  exportDocument: exportInteractiveWorkspaceDocument
} = useInteractiveWorkspace()

const {
  visible: pptWorkspaceVisible,
  currentDocumentId: pptWorkspaceDocumentId,
  document: pptWorkspaceDocument,
  streamingText: pptWorkspaceStreamingText,
  loading: pptWorkspaceLoading,
  exporting: pptWorkspaceExporting,
  error: pptWorkspaceError,
  streamConnected: pptWorkspaceStreamConnected,
  streamError: pptWorkspaceStreamError,
  slidesProgress: pptWorkspaceSlidesProgress,
  slidesTotal: pptWorkspaceSlidesTotal,
  overallProgress: pptWorkspaceOverallProgress,
  hasSlideProgress: pptWorkspaceHasSlideProgress,
  openWorkspace: openPptWorkspace,
  closeWorkspace: closePptWorkspace,
  resetWorkspace: resetPptWorkspace,
  exportDocument: exportPptWorkspaceDocument
} = usePptWorkspace()

const anyWorkspaceVisible = computed(() =>
  lessonPlanWorkspaceVisible.value || interactiveWorkspaceVisible.value || pptWorkspaceVisible.value
)

// openAuthModal / openPersonalCenter / openSettings / toggleUserMenu / handleAuthRequired
// 由 useUserMenu 提供（见上方解构）。
// userMenuStyle 已搬进 ChatSidebar 自己计算

/**
 * 登录/注册成功
 */
const onAuthSuccess = async () => {
  await fetchUserInfo()
  await loadHistoryList()
}

const handleLogout = () => {
  userMenuOpen.value = false
  stopGenerationPolling()
  stopLessonPlanStageStream()
  logout()
  messages.value = []
  closeBlueprintSupplement()
  resetLessonPlanWorkspace()
  resetInteractiveWorkspace()
  resetPptWorkspace()
  currentSessionId.value = null
  currentMode.value = null
  historyList.value = []
}

// ==================== 消息操作 ====================
const copyMessage = async (msg) => {
  try {
    await navigator.clipboard.writeText(msg.content)
    copiedMsgId.value = msg.id
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

const resetCopyIcon = () => {
  copiedMsgId.value = null
}

const retryMessage = (msg) => {
  // 重试：将用户消息重新发送
  inputText.value = msg.content
  sendMessage()
}

const startTeachingMessage = async ({
  userMessage,
  displayContent = userMessage,
  action = null
}) => {
  const content = userMessage?.trim()
  if (!content || isLoading.value || isSending.value) return false

  if (!currentUserId.value) {
    openAuthModal()
    return false
  }

  messages.value.push({
    id: Date.now(),
    role: 'user',
    content: displayContent
  })
  scrollToBottom()

  const aiMsgId = Date.now() + 1
  const aiMsgIndex = messages.value.length
  messages.value.push({
    id: aiMsgId,
    role: 'ai',
    content: '',
    loading: true,
    cardType: null,
    cardData: null
  })
  scrollToBottom()

  isLoading.value = true
  isSending.value = true

  try {
    const result = await sendMessageApi(
      content,
      searchMode.value === 'auto',
      currentSessionId.value,
      currentMode.value,
      action,
      currentMode.value === 'ppt' ? selectedPptTemplateId.value || null : null
    )

    const data = result?.data || {}
    if (data.sessionId) {
      currentSessionId.value = data.sessionId
    }

    messages.value[aiMsgIndex] = {
      id: aiMsgId,
      role: 'ai',
      content: data.answer || '',
      layer: data.layer,
      layerDesc: data.layerDesc,
      knowledgeSources: data.knowledgeSources,
      costMs: data.costMs,
      cardType: data.cardType || null,
      cardData: data.cardData || null,
      loading: false
    }
    scrollToBottom()
    await loadHistoryList()
    if (data.cardType === 'lesson_plan_stage_entry') {
      await openLessonPlanWorkspaceByCard(data.cardData)
    } else if (data.cardType === 'ppt_stage_entry') {
      await openPptWorkspaceByCard(data.cardData)
    } else if (data.cardType === 'interactive_stage_entry') {
      await openInteractiveWorkspaceByCard(data.cardData)
    } else if (shouldUseLegacyGenerationPolling(data)) {
      startGenerationPolling()
    }
  } catch (error) {
    console.error('发送教学消息失败:', error)
    messages.value[aiMsgIndex] = {
      id: aiMsgId,
      role: 'ai',
      content: `抱歉，发生了错误：${error.message}。请稍后重试。`,
      loading: false
    }
    scrollToBottom()
  } finally {
    isLoading.value = false
    isSending.value = false
  }

  return true
}

const startStreamingMessage = ({
  userMessage,
  displayContent = userMessage,
  referencedFileId = null
}) => {
  const content = userMessage?.trim()
  if (!content || isLoading.value || isSending.value) return false

  if (!currentUserId.value) {
    openAuthModal()
    return false
  }

  messages.value.push({
    id: Date.now(),
    role: 'user',
    content: displayContent
  })
  scrollToBottom()

  const aiMsgId = Date.now() + 1
  const aiMsgIndex = messages.value.length
  messages.value.push({
    id: aiMsgId,
    role: 'ai',
    content: '',
    loading: true,
    cardType: null,
    cardData: null
  })
  scrollToBottom()

  isLoading.value = true
  isSending.value = true

  sendMessageStream(
    content,
    searchMode.value === 'auto',
    currentSessionId.value,
    {
      onChunk: (chunk) => {
        messages.value[aiMsgIndex].content += chunk
        messages.value[aiMsgIndex].loading = false
        scrollToBottom()
      },
      onSessionId: (sessionId) => {
        currentSessionId.value = sessionId
      },
      onDone: async (cost) => {
        isLoading.value = false
        isSending.value = false
        messages.value[aiMsgIndex].costMs = parseInt(cost)
        await loadHistoryList()
      },
      onError: (error) => {
        console.error('流式接收失败:', error)
        isLoading.value = false
        isSending.value = false
        messages.value[aiMsgIndex].content += `\n\n[错误: ${error.message}]`
        messages.value[aiMsgIndex].loading = false
      }
    },
    referencedFileId
  )

  return true
}

const likeMessage = (msg) => {
  msg.disliked = false
  msg.liked = !msg.liked
  if (msg.liked) {
    ElMessage.success('感谢您的点赞')
  }
}

const dislikeMessage = (msg) => {
  msg.liked = false
  msg.disliked = !msg.disliked
  if (msg.disliked) {
    ElMessage.info('已记录您的反馈')
  }
}

// ==================== 蓝图卡片操作 ====================
const isBlueprintSupplementOpen = (msg) => activeBlueprintComposerId.value === msg?.id

const closeBlueprintSupplement = () => {
  activeBlueprintComposerId.value = null
  blueprintSupplementText.value = ''
}

const ensureBlueprintActionReady = () => {
  if (!currentSessionId.value || !currentMode.value) {
    ElMessage.error('当前会话状态异常，请刷新后重试')
    return false
  }
  return true
}

const confirmBlueprint = () => {
  if (!ensureBlueprintActionReady()) return
  closeBlueprintSupplement()
  void startTeachingMessage({
    userMessage: BLUEPRINT_CONFIRM_MESSAGE,
    action: 'confirm'
  })
}

const supplementBlueprint = (msg) => {
  if (!ensureBlueprintActionReady()) return
  if (isLoading.value || isSending.value) return
  if (activeBlueprintComposerId.value !== msg.id) {
    blueprintSupplementText.value = ''
  }
  activeBlueprintComposerId.value = msg.id
  scrollToBottom()
}

const submitBlueprintSupplement = async () => {
  if (!ensureBlueprintActionReady()) return
  const content = blueprintSupplementText.value.trim()
  if (!content) return

  const sent = await startTeachingMessage({
    userMessage: content,
    action: 'supplement'
  })

  if (sent) {
    closeBlueprintSupplement()
  }
}

// ==================== 蓝图卡片辅助函数 ====================
const parseCardData = (cardData) => {
  if (!cardData) return null
  if (typeof cardData === 'object') return cardData
  if (typeof cardData !== 'string') return null

  try {
    return JSON.parse(cardData)
  } catch {
    return null
  }
}

const normalizeMessage = (message) => ({
  id: message.id,
  role: message.role === 'assistant' ? 'ai' : 'user',
  content: message.content,
  layer: message.layer,
  layerDesc: message.layerDesc,
  costMs: message.costMs,
  cardType: message.cardType || null,
  cardData: parseCardData(message.cardData),
  loading: false
})

const stopGenerationPolling = () => {
  if (generationPollingTimer) {
    clearInterval(generationPollingTimer)
    generationPollingTimer = null
  }
}

const getMessageMode = (msg) => msg?.cardData?.mode || currentMode.value

const getMessageModeName = (msg) => msg?.cardData?.modeName || getModeName(getMessageMode(msg))

// 下载相关纯函数已抽到 @/utils/download.js
// 仅保留 3 个 computed —— 用 resolveDownloadUrl 包装当前选中文档的 downloadUrl
const lessonPlanWorkspaceDownloadUrl = computed(() =>
  resolveDownloadUrl(lessonPlanWorkspaceDocument.value?.downloadUrl || '')
)

const pptWorkspaceDownloadUrl = computed(() =>
  resolveDownloadUrl(pptWorkspaceDocument.value?.downloadUrl || '')
)

const interactiveWorkspaceDownloadUrl = computed(() =>
  resolveDownloadUrl(interactiveWorkspaceDocument.value?.downloadUrl || '')
)

// isLessonPlanTerminalStatus / normalizeLessonPlanStatus 已从 @/composables/useLessonPlanStageStream.js 导入

const findLatestLessonPlanStageEntry = (messageList = messages.value) => {
  const reversed = [...messageList].reverse()
  return reversed.find(msg => msg.cardType === 'lesson_plan_stage_entry' && msg.cardData?.documentId) || null
}

const findLatestInteractiveStageEntry = (messageList = messages.value) => {
  const reversed = [...messageList].reverse()
  return reversed.find(msg => msg.cardType === 'interactive_stage_entry' && msg.cardData?.documentId) || null
}

const findLatestPptStageEntry = (messageList = messages.value) => {
  const reversed = [...messageList].reverse()
  return reversed.find(msg => msg.cardType === 'ppt_stage_entry' && msg.cardData?.documentId) || null
}

const syncLessonPlanStageCard = (documentPayload) => {
  if (!documentPayload?.documentId) return

  messages.value = messages.value.map(msg => {
    if (msg.cardType !== 'lesson_plan_stage_entry' || msg.cardData?.documentId !== documentPayload.documentId) {
      return msg
    }

    return {
      ...msg,
      cardData: {
        ...msg.cardData,
        status: documentPayload.status,
        statusText: documentPayload.statusText,
        summary: documentPayload.summary,
        preview: documentPayload.preview || msg.cardData?.preview,
        downloadUrl: documentPayload.downloadUrl || msg.cardData?.downloadUrl
      }
    }
  })
}

const refreshStaleLessonPlanCardStatus = async () => {
  const staleEntries = messages.value.filter(
    (msg) =>
      msg.cardType === 'lesson_plan_stage_entry' &&
      msg.cardData?.documentId &&
      !isLessonPlanTerminalStatus(msg.cardData?.status)
  )
  for (const entry of staleEntries) {
    try {
      const res = await getLessonPlanDocument(entry.cardData.documentId)
      if (res?.data) {
        syncLessonPlanStageCard(res.data)
      }
    } catch {
      // ignore — SSE stream will handle it if reachable
    }
  }
}

const syncInteractiveStageCard = (documentPayload) => {
  if (!documentPayload?.documentId) return

  messages.value = messages.value.map(msg => {
    if (msg.cardType !== 'interactive_stage_entry' || msg.cardData?.documentId !== documentPayload.documentId) {
      return msg
    }

    return {
      ...msg,
      cardData: {
        ...msg.cardData,
        status: documentPayload.status,
        statusText: documentPayload.statusText,
        summary: documentPayload.summary,
        downloadUrl: documentPayload.downloadUrl || msg.cardData?.downloadUrl
      }
    }
  })
}

const syncPptStageCard = (documentPayload) => {
  if (!documentPayload?.documentId) return

  messages.value = messages.value.map(msg => {
    if (msg.cardType !== 'ppt_stage_entry' || msg.cardData?.documentId !== documentPayload.documentId) {
      return msg
    }

    return {
      ...msg,
      cardData: {
        ...msg.cardData,
        status: documentPayload.status,
        statusText: documentPayload.statusText,
        summary: documentPayload.summary,
        downloadUrl: documentPayload.downloadUrl || msg.cardData?.downloadUrl,
        fileName: documentPayload.fileName || msg.cardData?.fileName
      }
    }
  })
}

// 教案 SSE 流逻辑见 @/composables/useLessonPlanStageStream.js
// 外部只需要 ensure(cardData) 启动 / stop() 停止两个动作。
const {
  ensure: ensureLessonPlanStageStream,
  stop: stopLessonPlanStageStream
} = useLessonPlanStageStream({
  currentMode,
  currentSessionId,
  isWorkspaceVisible: lessonPlanWorkspaceVisible,
  onSyncCard: (payload) => syncLessonPlanStageCard(payload),
  refreshMessages: (options) => refreshMessages(options),
  loadHistoryList: () => loadHistoryList()
})

const openLessonPlanWorkspaceByCard = async (cardData, options = {}) => {
  if (!cardData?.documentId) return

  const preserveDraft = options.preserveDraft !== false
  const sameDocument = lessonPlanWorkspaceDocumentId.value === cardData.documentId
  const syncEditor = !(preserveDraft && sameDocument && lessonPlanWorkspaceDirty.value)

  try {
    closePptWorkspace()
    closeInteractiveWorkspace()
    // 不再开"阶段卡流"——它和工作区流连同一个 /documents/{id}/stream，两条并发 SSE 会撞上
    // 浏览器同源连接数限制，导致工作区流被挂起到生成结束才连上、错过全部 content_delta（看不到流式）。
    // 工作区流本身就接收 status/completed，足以驱动正文流式 + 完成展示，所以这里只保留工作区流这一条。
    stopLessonPlanStageStream()
    const payload = await openLessonPlanWorkspace(cardData.documentId, { syncEditor })
    if (payload) {
      syncLessonPlanStageCard(payload)
    }
  } catch (error) {
    console.error('打开教案工作区失败:', error)
    ElMessage.error(`打开教案工作区失败：${error.message}`)
  }
}

const openInteractiveWorkspaceByCard = async (cardData, options = {}) => {
  if (!cardData?.documentId) return

  const preserveDraft = options.preserveDraft !== false
  const sameDocument = interactiveWorkspaceDocumentId.value === cardData.documentId
  const syncEditor = !(preserveDraft && sameDocument && interactiveWorkspaceDirty.value)

  try {
    stopLessonPlanStageStream()
    closeLessonPlanWorkspace()
    closePptWorkspace()
    const payload = await openInteractiveWorkspace(cardData.documentId, { syncEditor })
    if (payload) {
      syncInteractiveStageCard(payload)
    }
  } catch (error) {
    console.error('打开互动工作区失败:', error)
    ElMessage.error(`打开互动工作区失败：${error.message}`)
  }
}

const openPptWorkspaceByCard = async (cardData) => {
  if (!cardData?.documentId) return

  try {
    stopLessonPlanStageStream()
    closeLessonPlanWorkspace()
    closeInteractiveWorkspace()
    const payload = await openPptWorkspace(cardData.documentId)
    if (payload) {
      syncPptStageCard(payload)
    }
  } catch (error) {
    console.error('打开 PPT 工作区失败:', error)
    ElMessage.error(`打开 PPT 工作区失败：${error.message}`)
  }
}

const updateLessonPlanWorkspaceContent = (value) => {
  lessonPlanWorkspaceContent.value = value
}

const updateInteractiveWorkspaceContent = (value) => {
  interactiveWorkspaceContent.value = value
}

const handleLessonPlanWorkspaceSave = async () => {
  try {
    await saveLessonPlanWorkspaceDocument()
    ElMessage.success('教案内容已保存')
  } catch (error) {
    console.error('保存教案失败:', error)
    ElMessage.error(`保存失败：${error.message}`)
  }
}

const handleLessonPlanWorkspaceExport = async () => {
  try {
    if (lessonPlanWorkspaceDocument.value?.downloadUrl) {
      await downloadProtectedFile(
        lessonPlanWorkspaceDocument.value.downloadUrl,
        `${lessonPlanWorkspaceDocument.value?.title || '教案'}.docx`
      )
      return
    }

    const payload = await exportLessonPlanWorkspaceDocument()
    const resolvedDownloadUrl = resolveDownloadUrl(
      payload?.downloadUrl || lessonPlanWorkspaceDocument.value?.downloadUrl || ''
    )

    if (resolvedDownloadUrl) {
      await downloadProtectedFile(
        resolvedDownloadUrl,
        `${lessonPlanWorkspaceDocument.value?.title || '教案'}.docx`
      )
    } else {
      ElMessage.success('教案导出已完成')
    }
  } catch (error) {
    console.error('导出教案失败:', error)
    ElMessage.error(`导出失败：${error.message}`)
  }
}

const handleInteractiveWorkspaceSave = async () => {
  try {
    await saveInteractiveWorkspaceDocument()
    ElMessage.success('互动页面已保存')
  } catch (error) {
    console.error('保存互动页面失败:', error)
    ElMessage.error(`保存失败：${error.message}`)
  }
}

const handleInteractiveWorkspaceExport = async () => {
  try {
    if (interactiveWorkspaceDirty.value) {
      await saveInteractiveWorkspaceDocument()
    }

    if (interactiveWorkspaceDocument.value?.downloadUrl) {
      await downloadProtectedFile(
        interactiveWorkspaceDocument.value.downloadUrl,
        `${interactiveWorkspaceDocument.value?.title || '互动页面'}.html`
      )
      return
    }

    const payload = await exportInteractiveWorkspaceDocument()
    const resolvedDownloadUrl = resolveDownloadUrl(
      payload?.downloadUrl || interactiveWorkspaceDocument.value?.downloadUrl || ''
    )

    if (resolvedDownloadUrl) {
      await downloadProtectedFile(
        resolvedDownloadUrl,
        `${interactiveWorkspaceDocument.value?.title || '互动页面'}.html`
      )
    } else {
      ElMessage.success('互动页面导出已完成')
    }
  } catch (error) {
    console.error('导出互动页面失败:', error)
    ElMessage.error(`导出失败：${error.message}`)
  }
}

const handlePptWorkspaceExport = async () => {
  try {
    if (pptWorkspaceDocument.value?.downloadUrl) {
      await downloadProtectedFile(
        pptWorkspaceDocument.value.downloadUrl,
        `${pptWorkspaceDocument.value?.title || 'PPT'}.pptx`
      )
      return
    }

    const payload = await exportPptWorkspaceDocument()
    const resolvedDownloadUrl = resolveDownloadUrl(
      payload?.downloadUrl || pptWorkspaceDocument.value?.downloadUrl || ''
    )

    if (resolvedDownloadUrl) {
      await downloadProtectedFile(
        resolvedDownloadUrl,
        `${pptWorkspaceDocument.value?.title || 'PPT'}.pptx`
      )
    } else {
      ElMessage.success('PPT 导出已完成')
    }
  } catch (error) {
    console.error('导出 PPT 失败:', error)
    ElMessage.error(`导出失败：${error.message}`)
  }
}

const handleGenerationCardDownload = async (downloadUrl, fileName = '下载文件') => {
  try {
    await downloadProtectedFile(downloadUrl, fileName)
  } catch (error) {
    console.error('下载生成文件失败:', error)
    ElMessage.error(`下载失败：${error.message}`)
  }
}

const handleLessonPlanWorkspaceRewriteRequest = async ({ selectedText, instruction }) => {
  try {
    await requestLessonPlanWorkspaceRewrite(selectedText, instruction)
  } catch (error) {
    console.error('AI 改写教案失败:', error)
    ElMessage.error(`AI 改写失败：${error.message}`)
  }
}

const dismissLessonPlanWorkspaceRewrite = () => {
  clearLessonPlanWorkspaceRewriteSuggestion()
}

const shouldUseLegacyGenerationPolling = (data) => {
  if (!data) return false
  if (data.cardType === 'lesson_plan_stage_entry') return false
  if (data.cardType === 'ppt_stage_entry') return false
  if (data.cardType === 'interactive_stage_entry') return false
  if (data.mode === 'lesson_plan') return false
  if (data.mode === 'ppt') return false
  if (data.mode === 'interactive') return false
  return data.generationStatus === 'processing' || data.cardType === 'generation_pending'
}

watch(lessonPlanWorkspaceDocument, (documentPayload) => {
  if (!documentPayload?.documentId) return
  syncLessonPlanStageCard(documentPayload)
})

watch(pptWorkspaceDocument, (documentPayload) => {
  if (!documentPayload?.documentId) return
  syncPptStageCard(documentPayload)
})

watch(interactiveWorkspaceDocument, (documentPayload) => {
  if (!documentPayload?.documentId) return
  syncInteractiveStageCard(documentPayload)
})

watch(anyWorkspaceVisible, (visible) => {
  clearWorkspaceUiRestoreTimer()

  if (visible) {
    if (sidebarOpenBeforeWorkspace.value === null) {
      sidebarOpenBeforeWorkspace.value = sidebarOpen.value
    }

    if (knowledgeDrawerOpen.value) {
      knowledgeDrawerOpen.value = false
    }

    clearDrawerButtonRestoreTimer()
    showDrawerBtn.value = false
    sidebarOpen.value = false
    return
  }

  workspaceUiRestoreTimer = setTimeout(() => {
    if (sidebarOpenBeforeWorkspace.value !== null) {
      sidebarOpen.value = sidebarOpenBeforeWorkspace.value
      sidebarOpenBeforeWorkspace.value = null
    }

    if (!knowledgeDrawerOpen.value) {
      showDrawerBtn.value = true
    }
  }, WORKSPACE_UI_RESTORE_DELAY)
})

// ==================== 婊氬姩鍒板簳閮?====================
const scrollToBottom = () => {
  nextTick(() => {
    if (conversationArea.value) {
      conversationArea.value.scrollTop = conversationArea.value.scrollHeight
    }
  })
}

// ==================== 发送消息 ====================
const sendMessage = async () => {
  if (!inputText.value.trim() || isLoading.value || isSending.value) return

  if (!currentUserId.value) {
    openAuthModal()
    return
  }

  // PPT 模式必须先选模板：工作区已收口到「全替换」一条链路，生成必须以选定的 pptx
  // 模板为底版。没选模板就在这里友好拦住并打开选择弹窗，避免走到后端生成阶段才报错。
  if (currentMode.value === 'ppt' && !selectedPptTemplateId.value) {
    ElMessage.warning('请先选择一个 PPT 模板，再开始生成')
    openPptTemplateModal()
    return
  }

  closeBlueprintSupplement()
  const userMessage = inputText.value.trim()
  const requestMessage = decoratePptMessageForRequest(userMessage)
  const attachments = [...pendingAttachments.value]
  inputText.value = ''
  pendingAttachments.value = []

  const hasAttachments = attachments.length > 0
  if (!hasAttachments) {
    const currentReferencedFileId = referencedFileId.value
    referencedFileId.value = null
    if (currentMode.value) {
      await startTeachingMessage({
        userMessage: requestMessage,
        displayContent: userMessage
      })
    } else {
      startStreamingMessage({
        userMessage,
        referencedFileId: currentReferencedFileId
      })
    }
    return
  }

  const displayContent = hasAttachments
    ? userMessage + '\n\n[附件: ' + attachments.map(a => a.name).join(', ') + ']'
    : userMessage

  messages.value.push({
    id: Date.now(),
    role: 'user',
    content: displayContent,
    attachments: hasAttachments ? attachments.map(a => ({ name: a.name, type: a.type, size: a.size })) : undefined
  })
  scrollToBottom()

  const aiMsgId = Date.now()
  const aiMsgIndex = messages.value.length
  messages.value.push({
    id: aiMsgId,
    role: 'ai',
    content: '',
    loading: true,
    cardType: null,
    cardData: null
  })
  scrollToBottom()

  isLoading.value = true
  isSending.value = true

  if (hasAttachments) {
    try {
      const result = await sendMessageWithFiles(
        currentMode.value === 'ppt' ? requestMessage : userMessage,
        searchMode.value === 'auto',
        attachments.map(a => a.file),
        currentSessionId.value,
        currentMode.value,
        currentMode.value === 'ppt' ? selectedPptTemplateId.value || null : null
      )

      if (result.data.sessionId) {
        currentSessionId.value = result.data.sessionId
      }

      messages.value[aiMsgIndex] = {
        id: aiMsgId,
        role: 'ai',
        content: result.data.answer,
        layer: result.data.layer,
        layerDesc: result.data.layerDesc,
        knowledgeSources: result.data.knowledgeSources,
        costMs: result.data.costMs,
        cardType: result.data.cardType || null,
        cardData: result.data.cardData || null,
        loading: false
      }
      scrollToBottom()
      await loadHistoryList()
      if (result.data.cardType === 'lesson_plan_stage_entry') {
        await openLessonPlanWorkspaceByCard(result.data.cardData)
      } else if (result.data.cardType === 'ppt_stage_entry') {
        await openPptWorkspaceByCard(result.data.cardData)
      } else if (result.data.cardType === 'interactive_stage_entry') {
        await openInteractiveWorkspaceByCard(result.data.cardData)
      } else if (shouldUseLegacyGenerationPolling(result.data)) {
        startGenerationPolling()
      }
    } catch (error) {
      console.error('发送消息失败:', error)
      messages.value[aiMsgIndex] = {
        id: aiMsgId,
        role: 'ai',
        content: `抱歉，发生了错误：${error.message}。请稍后重试。`,
        loading: false
      }
      scrollToBottom()
    } finally {
      isLoading.value = false
      isSending.value = false
    }
    return
  }
}
/**
 * 轮询非教案模式的生成状态，直到完成或失败
 */
const startGenerationPolling = () => {
  stopGenerationPolling()
  if (!currentSessionId.value) return

  generationPollingTimer = setInterval(async () => {
    try {
      const res = await getGenerationStatus(currentSessionId.value)
      if (res.code !== 200) return

      const { generationStatus } = res.data
      if (generationStatus === 'completed' || generationStatus === 'failed') {
        stopGenerationPolling()
        await refreshMessages()
        await loadHistoryList()
      }
    } catch (e) {
      console.error('轮询生成状态失败:', e)
    }
  }, 3000)
}

// ==================== 键盘事件 ====================
const handleKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

// ==================== 切换侧边栏 ====================
const toggleSidebar = () => {
  sidebarOpen.value = !sidebarOpen.value
}

// ==================== 会话管理 ====================

/**
 * 加载会话列表
 */
// loadHistoryList / filteredHistoryList / groupedHistoryList 等历史会话逻辑已搬到 @/composables/useChatHistory.js

// PPT 模板相关的纯逻辑都搬到了 @/composables/usePptTemplate.js。
// 这里只保留 decoratePptMessageForRequest 的薄封装——它需要根据 ChatHome 的
// currentMode 和 messages.length 来判断"是否是 ppt 模式首条消息"。
const decoratePptMessageForRequest = (content) => {
  return decoratePptMessageFn(content, {
    isPptMode: currentMode.value === 'ppt',
    isFirstMessage: messages.value.length === 0
  })
}

/**
 * 新建会话
 */
const startNewChat = async () => {
  messages.value = []
  closeBlueprintSupplement()
  stopLessonPlanStageStream()
  resetLessonPlanWorkspace()
  resetInteractiveWorkspace()
  resetPptWorkspace()
  inputText.value = ''
  transcribedText.value = ''
  currentSessionId.value = null
  currentMode.value = null
  resetPptTemplateState()
  const url = new URL(window.location.href)
  url.searchParams.delete('session')
  window.history.replaceState({}, '', url)
  await loadHistoryList()
}

const enterMode = (mode) => {
  messages.value = []
  closeBlueprintSupplement()
  stopLessonPlanStageStream()
  resetLessonPlanWorkspace()
  resetInteractiveWorkspace()
  inputText.value = ''
  transcribedText.value = ''
  currentSessionId.value = null
  currentMode.value = mode
  resetPptTemplateState()
  const url = new URL(window.location.href)
  url.searchParams.delete('session')
  window.history.replaceState({}, '', url)
  if (mode === 'ppt') {
    void ensurePptTemplatesLoaded()
  }
}

const getModeLabel = (mode) => {
  const labels = {
    'ppt': 'PPT生成模式',
    'lesson_plan': '教案生成模式',
    'interactive': '互动内容模式'
  }
  return labels[mode] || mode
}

const getModeName = (mode) => {
  const names = {
    'ppt': 'PPT',
    'lesson_plan': '教案',
    'interactive': '互动'
  }
  return names[mode] || '通用'
}

/**
 * 退出教学模式
 */
const exitMode = async () => {
  stopGenerationPolling()
  stopLessonPlanStageStream()
  resetLessonPlanWorkspace()
  resetInteractiveWorkspace()
  resetPptWorkspace()
  currentMode.value = null
  isModeTagHovered.value = false
  resetPptTemplateState()
  const sessionId = currentSessionId.value
  if (sessionId) {
    try {
      await exitSessionMode(sessionId)
    } catch (e) {
      console.error('退出模式失败:', e)
    }
  }
}

/**
 * 切换到指定会话
 */
const switchToSession = async (session, options = {}) => {
  try {
    const res = await getSessionDetail(session.id)
    if (res.code === 200 && res.data) {
      const autoOpenLessonPlanWorkspace = options.autoOpenLessonPlanWorkspace !== false
      const autoOpenPptWorkspace = options.autoOpenPptWorkspace !== false
      const autoOpenInteractiveWorkspace = options.autoOpenInteractiveWorkspace !== false

      currentSessionId.value = session.id
      resetPptTemplateState()
      currentMode.value = res.data.mode || null
      messages.value = (res.data.messages || []).map(normalizeMessage)
      closeBlueprintSupplement()
      await refreshStaleLessonPlanCardStatus()
      const latestLessonPlanStageEntry = findLatestLessonPlanStageEntry(messages.value)
      const latestPptStageEntry = findLatestPptStageEntry(messages.value)
      const latestInteractiveStageEntry = findLatestInteractiveStageEntry(messages.value)
      const url = new URL(window.location.href)
      url.searchParams.set('session', session.id)
      window.history.replaceState({}, '', url)
      if (currentMode.value === 'lesson_plan' && latestLessonPlanStageEntry?.cardData?.documentId) {
        stopGenerationPolling()
        ensureLessonPlanStageStream(latestLessonPlanStageEntry.cardData)
        resetPptWorkspace()
        resetInteractiveWorkspace()
        if (autoOpenLessonPlanWorkspace) {
          await openLessonPlanWorkspaceByCard(latestLessonPlanStageEntry.cardData, { preserveDraft: false })
        }
      } else if (currentMode.value === 'ppt' && latestPptStageEntry?.cardData?.documentId) {
        stopLessonPlanStageStream()
        resetLessonPlanWorkspace()
        resetInteractiveWorkspace()
        if (autoOpenPptWorkspace) {
          await openPptWorkspaceByCard(latestPptStageEntry.cardData)
        }
      } else if (currentMode.value === 'interactive' && latestInteractiveStageEntry?.cardData?.documentId) {
        stopLessonPlanStageStream()
        resetLessonPlanWorkspace()
        resetPptWorkspace()
        resetInteractiveWorkspace()
        if (autoOpenInteractiveWorkspace) {
          await openInteractiveWorkspaceByCard(latestInteractiveStageEntry.cardData, { preserveDraft: false })
        }
      } else {
        stopLessonPlanStageStream()
        resetLessonPlanWorkspace()
        resetPptWorkspace()
        resetInteractiveWorkspace()
      }
      if (res.data.generationStatus === 'processing' && currentMode.value !== 'lesson_plan' && currentMode.value !== 'ppt' && currentMode.value !== 'interactive') {
        startGenerationPolling()
      } else {
        stopGenerationPolling()
      }
      nextTick(() => scrollToBottom())
    }
  } catch (error) {
    console.error('加载会话详情失败:', error)
  }
}

// 刷新当前会话消息
const refreshMessages = async (options = {}) => {
  if (currentSessionId.value) {
    const session = { id: currentSessionId.value }
    await switchToSession(session, options)
  }
}

/**
 * 删除会话
 */
// toggleHistoryMenu / handleEditTitle / confirmEditTitle / handleDeleteSession /
// confirmDeleteSession 已由 useChatHistory 提供（见上方解构）

const switchToChat = () => {
  if (currentView.value === 'chat') return
  isTransitioning.value = true
  setTimeout(() => {
    currentView.value = 'chat'
    isTransitioning.value = false
  }, 150)
}

// ==================== 知识库抽屉 ====================
const clearDrawerButtonRestoreTimer = () => {
  if (drawerButtonRestoreTimer) {
    clearTimeout(drawerButtonRestoreTimer)
    drawerButtonRestoreTimer = null
  }
}

const clearWorkspaceUiRestoreTimer = () => {
  if (workspaceUiRestoreTimer) {
    clearTimeout(workspaceUiRestoreTimer)
    workspaceUiRestoreTimer = null
  }
}

const restoreKnowledgeDrawerButton = () => {
  clearDrawerButtonRestoreTimer()
  drawerButtonRestoreTimer = setTimeout(() => {
    if (!anyWorkspaceVisible.value) {
      showDrawerBtn.value = true
    }
  }, 500)
}

const openKnowledgeDrawer = () => {
  if (knowledgeDrawerOpen.value || anyWorkspaceVisible.value) return
  clearDrawerButtonRestoreTimer()
  showDrawerBtn.value = false
  knowledgeDrawerOpen.value = true
  restoreKnowledgeDrawerButton()
  loadKnowledgeFiles()
  loadWorkspaces()
}

const closeKnowledgeDrawer = () => {
  if (!knowledgeDrawerOpen.value) return
  clearDrawerButtonRestoreTimer()
  showDrawerBtn.value = false
  knowledgeDrawerOpen.value = false
  restoreKnowledgeDrawerButton()
}

const toggleKnowledgeDrawer = () => {
  if (knowledgeDrawerOpen.value) {
    closeKnowledgeDrawer()
    return
  }

  openKnowledgeDrawer()
}

// ==================== 更多菜单展开方向 ====================
const toggleMoreMenu = (event) => {
  if (!showMoreMenu.value) {
    // 即将打开菜单，计算展开方向
    const button = event.currentTarget
    const rect = button.getBoundingClientRect()
    const windowHeight = window.innerHeight
    const dropdownHeight = 200 // 预估下拉菜单高度
    
    // 如果按钮下方空间不足，则向上展开
    if (rect.bottom + dropdownHeight > windowHeight) {
      dropdownPosition.value = 'top'
    } else {
      dropdownPosition.value = 'bottom'
    }
  }
  showMoreMenu.value = !showMoreMenu.value
}

// ==================== 拖拽上传 ====================
const chatAreaRef = ref(null)
// 附件 + 拖放视觉状态 + 操作方法都从 useAttachments 拿
const {
  pendingAttachments,
  isDraggingOver,
  handleDragOver,
  handleDragLeave,
  handleDrop,
  addAttachment,
  removeAttachment
} = useAttachments()

const handleFileUpload = async (event) => {
  const files = event.target.files || event.dataTransfer?.files
  if (!files || files.length === 0) return

  pendingUploadFiles.value = Array.from(files)
  showCategoryModal.value = true

  if (event.target && event.target.value) {
    event.target.value = ''
  }
}

const confirmUploadWithCategory = async () => {
  showCategoryModal.value = false

  for (const file of pendingUploadFiles.value) {
    await uploadSingleFile(file, selectedCategory.value)
  }

  pendingUploadFiles.value = []
  selectedCategory.value = '\u8bfe\u4ef6'
}

const handleFolderImport = async (event) => {
  const files = event.target.files
  if (!files || files.length === 0) return

  const validExtensions = ['.pdf', '.doc', '.docx', '.txt', '.md']
  const validFiles = Array.from(files).filter(file => {
    const ext = '.' + file.name.split('.').pop().toLowerCase()
    return validExtensions.includes(ext)
  })

  if (validFiles.length === 0) {
    alert('\u6240\u9009\u6587\u4ef6\u5939\u4e2d\u6ca1\u6709\u652f\u6301\u7684\u6587\u4ef6\u683c\u5f0f\uff08PDF\u3001Word\u3001TXT\u3001Markdown\uff09\u3002')
    return
  }

  pendingUploadFiles.value = validFiles
  showCategoryModal.value = true
  event.target.value = ''
}

// ==================== 语音录音 ====================
const toggleRecording = async () => {
  if (isRecording.value) {
    stopRecording()
  } else {
    await startRecording()
  }
}

const startRecording = async () => {
  if (!recorder) {
    recorder = createRecorder(
      (state) => {
        if (state === RecordingState.RECORDING) {
          isRecording.value = true
          recordingDuration.value = 0
          durationTimer = setInterval(() => {
            recordingDuration.value++
            if (recordingDuration.value >= 60) {
              stopRecording()
            }
          }, 1000)
        } else if (state === RecordingState.DONE) {
          isRecording.value = false
          voiceStatus.value = ''
          if (durationTimer) {
            clearInterval(durationTimer)
            durationTimer = null
          }
        } else if (state === RecordingState.ERROR) {
          isRecording.value = false
          voiceStatus.value = 'error'
          if (durationTimer) {
            clearInterval(durationTimer)
            durationTimer = null
          }
        }
      },
      (text) => {
        transcribedText.value = text
        if (text) {
          inputText.value = text
        }
      }
    )
  }

  try {
    await recorder.startRecording()
  } catch (error) {
    console.error('无法访问麦克风:', error)
    voiceStatus.value = 'error'
  }
}

const stopRecording = () => {
  // 立即恢复按钮样式并显示动画
  isRecording.value = false
  voiceStatus.value = 'processing'
  if (recorder) {
    recorder.stopRecording()
  }
  if (durationTimer) {
    clearInterval(durationTimer)
    durationTimer = null
  }
}

// ==================== 加载动画控制 ====================
const showLoading = () => {
  voiceStatus.value = 'processing'
}

const hideLoading = () => {
  voiceStatus.value = ''
}

const useTranscribedText = () => {
  if (transcribedText.value) {
    inputText.value = transcribedText.value
    transcribedText.value = ''
  }
}

// ==================== 联网搜索切换 ====================
const toggleSearchMode = (mode) => {
  searchMode.value = mode
  showMoreMenu.value = false
  isTagHovered.value = false
}

const toggleSearchModeFromTag = () => {
  searchMode.value = searchMode.value === 'auto' ? 'off' : 'auto'
  showMoreMenu.value = false
  isTagHovered.value = false
}

// ==================== 工具函数 ====================
// getFileTypeName / formatDate / formatFileSize / getDocIcon 已抽到 @/utils/format.js

const handleClickOutside = (e) => {
  // 使用单个 ref 而不是数组累积
  if (moreMenuContainerRef.value && !moreMenuContainerRef.value.contains(e.target)) {
    showMoreMenu.value = false
  }
}

// ==================== 生命周期 ====================
onMounted(async () => {
  scrollToBottom()
  document.addEventListener('click', handleClickOutside)

  window.addEventListener('auth-required', handleAuthRequired)

  if (window.innerWidth <= 768) {
    sidebarOpen.value = false
  }

  await loadHistoryList()

  const urlParams = new URLSearchParams(window.location.search)
  const sessionIdFromUrl = urlParams.get('session')
  if (sessionIdFromUrl) {
    const session = historyList.value.find(s => s.id === sessionIdFromUrl)
    if (session) {
      await switchToSession(session)
    }
  }

  try {
    const health = await checkOllamaHealth()
  } catch (e) {
    console.warn('Ollama服务未连接:', e)
  }
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  window.removeEventListener('auth-required', handleAuthRequired)
  clearDrawerButtonRestoreTimer()
  clearWorkspaceUiRestoreTimer()
  if (durationTimer) {
    clearInterval(durationTimer)
  }
  if (generationPollingTimer) {
    clearInterval(generationPollingTimer)
  }
  stopLessonPlanStageStream()
  resetPptWorkspace()
  resetInteractiveWorkspace()
  if (recorder) {
    recorder.reset()
  }
  cleanupKnowledgeBase()
})
</script>

<template>
  <div class="app-container">
    <!-- 隐藏的文件上传input -->
    <input
      ref="fileInputRef"
      type="file"
      multiple
      accept=".pdf,.doc,.docx,.txt,.md"
      @change="handleFileUpload"
      hidden
    />
    <input
      ref="folderInputRef"
      type="file"
      webkitdirectory
      directory
      @change="handleFolderImport"
      hidden
    />
    <!-- ★ 侧栏整体（aside + 用户菜单悬浮 + "全部历史"面板 + 折叠态浮动按钮）抽到 ChatSidebar -->
    <ChatSidebar
      :sidebar-open="sidebarOpen"
      :knowledge-drawer-open="knowledgeDrawerOpen"
      :any-workspace-visible="anyWorkspaceVisible"
      :current-mode="currentMode"
      :current-session-id="currentSessionId"
      :history-list="historyList"
      :history-menu-open="historyMenuOpen"
      :history-panel-open="historyPanelOpen"
      :history-panel-search="historyPanelSearch"
      :filtered-history-list="filteredHistoryList"
      :grouped-history-list="groupedHistoryList"
      :is-logged-in="isLoggedIn"
      :user-info="userInfo"
      :user-menu-open="userMenuOpen"
      @toggle-sidebar="toggleSidebar"
      @start-new-chat="startNewChat"
      @enter-mode="enterMode"
      @switch-to-session="switchToSession"
      @toggle-history-menu="toggleHistoryMenu"
      @edit-title="handleEditTitle"
      @delete-session="handleDeleteSession"
      @open-history-panel="historyPanelOpen = true"
      @close-history-panel="historyPanelOpen = false"
      @update:history-panel-search="(v) => (historyPanelSearch = v)"
      @toggle-user-menu="toggleUserMenu"
      @open-personal-center="openPersonalCenter"
      @open-settings="openSettings"
      @logout="handleLogout"
    />


    <main class="main-content" :class="{
      'drawer-open': knowledgeDrawerOpen,
      'view-transition': isTransitioning,
      'sidebar-collapsed': !sidebarOpen,
      'lesson-plan-workspace-open': anyWorkspaceVisible
    }">
      <div class="chat-workspace-shell" :class="{ 'workspace-open': anyWorkspaceVisible }">
      <div class="chat-container" v-show="currentView === 'chat'">
        <!-- 无消息时：welcome-view 和 input-area 作为整体垂直居中 -->
          <div v-if="messages.length === 0" class="welcome-wrapper">
          <div :key="`welcome-${currentMode || 'default'}`" class="welcome-view">
            <div class="welcome-logo">
              <h1 class="welcome-title">
                <span class="title-qi">启</span><span class="title-si">思</span>
                <span class="title-dot">·</span>
                <span class="title-edu">EduSpark</span>
              </h1>
              <p class="welcome-subtitle">
                {{ isPptEmptyLanding ? '选择模板后输入主题，让 PPT 生成流程更聚焦。' : '智能教学助手，让备课更轻松' }}
              </p>
            </div>
          </div>

          <!-- 输入栏：ChatInputBar 自带附件区/拖拽/工具栏/提示/语音卡片 -->
          <ChatInputBar
            :key="`welcome-input-${currentMode || 'default'}`"
            v-model="inputText"
            :attachments="pendingAttachments"
            :is-dragging-over="isDraggingOver"
            :is-loading="isLoading"
            :is-sending="isSending"
            :is-recording="isRecording"
            :voice-status="voiceStatus"
            :search-mode="searchMode"
            :current-mode="currentMode"
            :placeholder="currentInputPlaceholder"
            :show-ppt-template-trigger="isPptEmptyLanding"
            :selected-ppt-template="selectedPptTemplate"
            :ppt-template-surface-style="selectedPptTemplate ? getPptTemplateSurfaceStyle(selectedPptTemplate) : {}"
            @send="sendMessage"
            @keydown="handleKeydown"
            @drag-over="handleDragOver"
            @drag-leave="handleDragLeave"
            @drop="handleDrop"
            @remove-attachment="removeAttachment"
            @toggle-recording="toggleRecording"
            @trigger-file-upload="triggerFileUpload"
            @toggle-search-mode="toggleSearchMode"
            @toggle-search-mode-from-tag="toggleSearchModeFromTag"
            @exit-mode="exitMode"
            @open-ppt-template-modal="openPptTemplateModal"
          />

        </div>

        <!-- 有消息时：conversation-view + input-area 固定在底部 -->
        <div v-else class="conversation-view">
          <!-- 消息流：所有渲染逻辑搬到 ChatMessageList -->
          <ChatMessageList
            ref="messageListRef"
            :messages="messages"
            :is-loading="isLoading"
            :is-sending="isSending"
            :current-mode="currentMode"
            :copied-msg-id="copiedMsgId"
            :blueprint-supplement-text="blueprintSupplementText"
            :blueprint-supplement-limit="BLUEPRINT_SUPPLEMENT_LIMIT"
            :active-blueprint-composer-id="activeBlueprintComposerId"
            :lesson-plan-workspace-loading="lessonPlanWorkspaceLoading"
            :ppt-workspace-loading="pptWorkspaceLoading"
            :interactive-workspace-loading="interactiveWorkspaceLoading"
            @reset-copy-icon="resetCopyIcon"
            @copy-message="copyMessage"
            @retry-message="retryMessage"
            @like-message="likeMessage"
            @dislike-message="dislikeMessage"
            @supplement-blueprint="supplementBlueprint"
            @confirm-blueprint="confirmBlueprint"
            @close-blueprint-supplement="closeBlueprintSupplement"
            @submit-blueprint-supplement="submitBlueprintSupplement"
            @update:blueprint-supplement-text="(v) => (blueprintSupplementText = v)"
            @open-lesson-plan-workspace="openLessonPlanWorkspaceByCard"
            @open-ppt-workspace="openPptWorkspaceByCard"
            @open-interactive-workspace="openInteractiveWorkspaceByCard"
            @generation-card-download="(url, name) => handleGenerationCardDownload(url, name)"
          />


          <!-- 输入栏（对话页底部固定）：跟 welcome 共用 ChatInputBar，只是不带 PPT 模板触发 -->
          <ChatInputBar
            v-model="inputText"
            :attachments="pendingAttachments"
            :is-dragging-over="isDraggingOver"
            :is-loading="isLoading"
            :is-sending="isSending"
            :is-recording="isRecording"
            :voice-status="voiceStatus"
            :search-mode="searchMode"
            :current-mode="currentMode"
            :placeholder="currentInputPlaceholder"
            @send="sendMessage"
            @keydown="handleKeydown"
            @drag-over="handleDragOver"
            @drag-leave="handleDragLeave"
            @drop="handleDrop"
            @remove-attachment="removeAttachment"
            @toggle-recording="toggleRecording"
            @trigger-file-upload="triggerFileUpload"
            @toggle-search-mode="toggleSearchMode"
            @toggle-search-mode-from-tag="toggleSearchModeFromTag"
            @exit-mode="exitMode"
          />

        </div>
      </div>
      <Transition name="workspace-slide">
        <LessonPlanWorkspace
          v-if="lessonPlanWorkspaceVisible && currentView === 'chat'"
          :document="lessonPlanWorkspaceDocument"
          :content="lessonPlanWorkspaceContent"
          :stream-text="lessonPlanWorkspaceStreamingText"
          :loading="lessonPlanWorkspaceLoading"
          :saving="lessonPlanWorkspaceSaving"
          :exporting="lessonPlanWorkspaceExporting"
          :rewriting="lessonPlanWorkspaceRewriting"
          :rewrite-suggestion="lessonPlanWorkspaceRewriteSuggestion"
          :dirty="lessonPlanWorkspaceDirty"
          :stream-connected="lessonPlanWorkspaceStreamConnected"
          :stream-error="lessonPlanWorkspaceStreamError || lessonPlanWorkspaceError"
          :download-url="lessonPlanWorkspaceDownloadUrl"
          @close="closeLessonPlanWorkspace"
          @save="handleLessonPlanWorkspaceSave"
          @export="handleLessonPlanWorkspaceExport"
          @request-rewrite="handleLessonPlanWorkspaceRewriteRequest"
          @dismiss-rewrite="dismissLessonPlanWorkspaceRewrite"
          @update:content="updateLessonPlanWorkspaceContent"
        />
      </Transition>
      <Transition name="workspace-slide">
        <PptWorkspace
          v-if="pptWorkspaceVisible && currentView === 'chat'"
          :document="pptWorkspaceDocument"
          :stream-text="pptWorkspaceStreamingText"
          :loading="pptWorkspaceLoading"
          :exporting="pptWorkspaceExporting"
          :stream-connected="pptWorkspaceStreamConnected"
          :stream-error="pptWorkspaceStreamError || pptWorkspaceError"
          :download-url="pptWorkspaceDownloadUrl"
          :slides-progress="pptWorkspaceSlidesProgress"
          :slides-total="pptWorkspaceSlidesTotal"
          :overall-progress="pptWorkspaceOverallProgress"
          :has-slide-progress="pptWorkspaceHasSlideProgress"
          @close="closePptWorkspace"
          @export="handlePptWorkspaceExport"
        />
      </Transition>
      <Transition name="workspace-slide">
        <InteractiveWorkspace
          v-if="interactiveWorkspaceVisible && currentView === 'chat'"
          :document="interactiveWorkspaceDocument"
          :content="interactiveWorkspaceContent"
          :stream-text="interactiveWorkspaceStreamingText"
          :loading="interactiveWorkspaceLoading"
          :saving="interactiveWorkspaceSaving"
          :exporting="interactiveWorkspaceExporting"
          :dirty="interactiveWorkspaceDirty"
          :stream-connected="interactiveWorkspaceStreamConnected"
          :stream-error="interactiveWorkspaceStreamError || interactiveWorkspaceError"
          :stream-state="interactiveWorkspaceStreamState"
          :download-url="interactiveWorkspaceDownloadUrl"
          @close="closeInteractiveWorkspace"
          @save="handleInteractiveWorkspaceSave"
          @export="handleInteractiveWorkspaceExport"
          @update:content="updateInteractiveWorkspaceContent"
        />
      </Transition>
      </div>

    </main>

    <KnowledgeDrawer
      :open="knowledgeDrawerOpen"
      :user-id="currentUserId"
      v-model:search-keyword="searchKeyword"
      v-model:current-category="currentCategory"
      v-model:view-mode="viewMode"
      :knowledge-docs="knowledgeDocs"
      :knowledge-loading="knowledgeLoading"
      :is-uploading="isUploading"
      :upload-progress="uploadProgress"
      :upload-queue="uploadQueue"
      :filtered-docs="filteredDocs"
      :selected-doc-id="selectedDocId"
      :workspaces="workspaces"
      :workspaces-loading="workspacesLoading"
      :selected-workspace-id="selectedWorkspaceId"
      :workspace-counts="workspaceCounts"
      @select-doc="selectDoc"
      @quote-doc="quoteDoc"
      @retry-file="handleRetryFile"
      @delete-file="handleDeleteFile"
      @trigger-file-upload="triggerFileUpload"
      @trigger-folder-import="triggerFolderImport"
      @select-workspace="setSelectedWorkspace"
      @create-workspace="createKnowledgeWorkspace"
      @rename-workspace="(payload) => renameKnowledgeWorkspace(payload.id, payload)"
      @delete-workspace="removeKnowledgeWorkspace"
      @close="closeKnowledgeDrawer"
    />

    <button
      v-if="!anyWorkspaceVisible && !knowledgeDrawerOpen && showDrawerBtn && isLoggedIn"
      class="floating-drawer-btn"
      @click="toggleKnowledgeDrawer"
      title="知识库"
      aria-label="打开教育知识工作台"
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path>
        <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path>
      </svg>
    </button>
    <button
      v-if="!anyWorkspaceVisible && knowledgeDrawerOpen && showDrawerBtn && isLoggedIn"
      class="floating-drawer-btn close-btn"
      @click="toggleKnowledgeDrawer"
      title="关闭"
      aria-label="关闭教育知识工作台"
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
        <line x1="18" y1="6" x2="6" y2="18"></line>
        <line x1="6" y1="6" x2="18" y2="18"></line>
      </svg>
    </button>

    <!-- 登录注册弹窗 -->
    <AuthModal
      v-model="authModalVisible"
      @success="onAuthSuccess"
    />

    <!-- 个人中心弹窗 -->
    <PersonalCenterModal
      v-model="personalCenterVisible"
      @logout="handleLogout"
    />

    <PptTemplateSelectorModal
      v-model="pptTemplateModalVisible"
      :templates="pptTemplates"
      :loading="pptTemplatesLoading"
      :load-error="pptTemplatesError"
      :selected-template-id="selectedPptTemplateId"
      @reload="reloadPptTemplates"
      @select-template="handlePptTemplateSelect"
    />

    <!-- 编辑标题弹窗 -->
    <Teleport to="body">
      <Transition name="modal-fade">
        <div v-if="editTitleModalVisible" class="custom-modal-overlay" @click.self="editTitleModalVisible = false">
          <div class="custom-modal">
            <div class="custom-modal-header">
              <h3>编辑标题</h3>
            </div>
            <div class="custom-modal-body">
              <input
                type="text"
                v-model="editingTitleText"
                class="custom-input"
                placeholder="请输入标题"
                @keyup.enter="confirmEditTitle"
              />
            </div>
            <div class="custom-modal-footer">
              <button class="btn-cancel" @click="editTitleModalVisible = false">取消</button>
              <button class="btn-confirm" @click="confirmEditTitle">确认</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 删除确认弹窗 -->
    <Teleport to="body">
      <Transition name="modal-fade">
        <div v-if="deleteConfirmModalVisible" class="custom-modal-overlay" @click.self="deleteConfirmModalVisible = false">
          <div class="custom-modal">
            <div class="custom-modal-header">
              <h3>确认删除</h3>
            </div>
            <div class="custom-modal-body">
              <p class="confirm-text">确定要删除该会话吗？删除后无法恢复。</p>
            </div>
            <div class="custom-modal-footer">
              <button class="btn-cancel" @click="deleteConfirmModalVisible = false">取消</button>
              <button class="btn-confirm btn-danger" @click="confirmDeleteSession">删除</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>

  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="showCategoryModal" class="category-modal-overlay" @click.self="showCategoryModal = false">
        <div class="category-modal-content">
          <h3 class="category-modal-title">选择文件分类</h3>
          <p class="category-modal-subtitle">已选择 {{ pendingUploadFiles.length }} 个文件</p>
          <div class="category-options">
            <label
              v-for="cat in categoryOptions"
              :key="cat"
              class="category-option"
              :class="{ 'selected': selectedCategory === cat }"
            >
              <input type="radio" :value="cat" v-model="selectedCategory" hidden />
              {{ cat }}
            </label>
          </div>
          <div class="category-modal-actions">
            <button class="btn-cancel" @click="showCategoryModal = false">取消</button>
            <button class="btn-confirm" @click="confirmUploadWithCategory">确认上传</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style>
@import 'katex/dist/katex.min.css';
@import 'highlight.js/styles/github-dark.css';
</style>

<!-- 故意去掉 scoped：ChatSidebar / ChatInputBar / ChatMessageList 等抽出的子组件
     依赖父组件这块大量的 class（.sidebar / .input-card / .ai-bubble 等）。
     scoped 会让 Vue 给 class 加上 data-v-xxx 属性选择器，子组件 DOM 上没有
     这个属性，样式就不命中。教师端只有 ChatHome 一个视图，去掉 scoped 不会
     污染其他页面。后续如果要按组件迁 style，再逐个搬到子组件文件里。 -->
<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

.app-container {
  display: flex;
  height: 100vh;
  width: 100%;
  overflow: hidden;
  position: relative;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  background-color: var(--es-surface);
  color: var(--es-text-primary);
}

.sidebar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 240px;
  background-color: var(--es-surface-soft);
  z-index: 20;
  overflow: hidden;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border-right: 1px solid var(--es-border);
  display: flex;
  flex-direction: column;
}

/* 折叠状态：向左滑出 */
.sidebar.collapsed {
  transform: translateX(-100%);
}

/* sidebar-inner 固定 240px 宽，防止宽度动画导致的挤压 */
.sidebar-inner {
  width: 240px;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 20px;
  overflow-y: hidden;
  overflow-x: hidden;
}

/* logo 区域 */
.logo-area {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  overflow: hidden;
  white-space: nowrap;
  min-height: 36px;
}

/* logo 区域 */
.logo-area {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
}

.sidebar-logo {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
}

.logo-area h1 {
  font-size: 24px;
  font-weight: 700;
  color: var(--es-text-primary);
  letter-spacing: -0.5px;
}

/* 折叠展开按钮 */
.sidebar-toggle {
  margin-left: auto;
  flex-shrink: 0;
  background: none;
  border: none;
  cursor: pointer;
  color: var(--es-text-secondary);
  padding: 4px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s, background 0.2s;
}

.sidebar-toggle:hover {
  color: var(--es-text-primary);
  background: var(--es-border);
}

/* 导航项目 */
.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--es-text-secondary);
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
}

/* 悬浮展开按钮：线条感 */
.sidebar-float-btn {
  position: fixed;
  top: 20px;
  left: 20px;
  z-index: 30;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid var(--es-border);
  border-radius: 10px;
  padding: 0 12px 0 6px;
  height: 40px;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--es-text-primary);
  backdrop-filter: blur(8px);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06), 0 0 0 1px rgba(0, 0, 0, 0.04);
  transition: background 0.2s, border-color 0.2s, box-shadow 0.2s, transform 0.2s;
}

.sidebar-float-btn:hover {
  background: var(--es-surface);
  border-color: var(--es-border-strong);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1), 0 0 0 1px rgba(0, 0, 0, 0.06);
  transform: translateY(-1px);
}

/* 悬浮按钮组 - 半圆胶囊形状 */
.float-btn-group {
  position: fixed;
  top: 20px;
  left: 20px;
  z-index: 30;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid var(--es-border);
  border-radius: 20px;
  padding: 6px 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  backdrop-filter: blur(8px);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06), 0 0 0 1px rgba(0, 0, 0, 0.04);
}

.float-btn-wrapper {
  position: relative;
}

.float-btn-tooltip {
  position: absolute;
  top: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
  background: var(--es-text-primary);
  color: white;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s;
}

.float-btn-tooltip::after {
  content: '';
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  border: 5px solid transparent;
  border-bottom-color: var(--es-text-primary);
}

.float-btn-wrapper:hover .float-btn-tooltip {
  opacity: 1;
}

.sidebar-float-btn {
  background: transparent;
  border: none !important;
  border-radius: 8px;
  padding: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--es-text-primary);
  transition: background 0.2s, transform 0.2s;
  outline: none;
  box-shadow: none;
}

.sidebar-float-btn:hover {
  background: var(--es-surface-muted);
}

.sidebar-float-btn:active {
  background: var(--es-border);
  transform: scale(0.95);
}

.expand-sidebar-btn {
  position: static;
}

.new-chat-float-btn {
  position: static;
}

.new-chat-float-btn svg {
  color: var(--es-link);
}

.new-chat-float-btn:hover svg {
  color: var(--es-link-hover);
}

/* Logo 小尺寸 */
.float-logo {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
}

/* 分隔线 */
.float-divider {
  width: 1px;
  height: 20px;
  background: var(--es-border);
  flex-shrink: 0;
}

/* 汉堡图标 */
.float-hamburger {
  color: var(--es-text-secondary);
  flex-shrink: 0;
}

.sidebar-float-btn svg {
  color: var(--es-text-secondary);
}

.new-chat-btn {
  background-color: var(--es-text-primary);
  color: white;
  border: none;
  border-radius: 8px;
  padding: 10px;
  width: 100%;
  font-weight: 500;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: background 0.2s;
  margin-bottom: 6px;
}

.new-chat-btn:hover {
  background-color: var(--es-text-primary);
}

.shortcut-hint {
  font-size: 12px;
  color: var(--es-text-tertiary);
  text-align: center;
  margin-bottom: 24px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--es-text-primary);
  margin: 20px 0 12px 0;
  padding: 0 8px;
}

.section-title svg {
  color: var(--es-text-secondary);
}

.feature-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.feature-card {
  flex: 1 0 calc(50% - 8px);
  background: var(--es-surface);
  border: 1px solid var(--es-border);
  border-radius: 8px;
  padding: 8px 6px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--es-text-primary);
  cursor: pointer;
  transition: background 0.2s;
}

.feature-card:hover {
  background-color: var(--es-surface-muted);
}

.feature-card svg {
  color: var(--es-link);
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.history-item {
  position: relative;
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
  font-size: 14px;
  color: var(--es-text-primary);
}

.history-item:hover {
  background-color: var(--es-surface-muted);
}

.history-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.history-more {
  display: flex;
  padding: 4px;
  border-radius: 4px;
  color: var(--es-text-tertiary);
  cursor: pointer;
  transition: all 0.15s;
  opacity: 0;
  flex-shrink: 0;
}

.history-item:hover .history-more {
  opacity: 1;
}

.history-more:hover {
  background-color: var(--es-border-strong);
  color: var(--es-text-primary);
}

.history-menu {
  position: absolute;
  right: 8px;
  top: 100%;
  margin-top: 4px;
  background: white;
  border: 1px solid var(--es-border);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  z-index: 100;
  min-width: 120px;
  overflow: hidden;
}

.history-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  font-size: 13px;
  color: var(--es-text-primary);
  cursor: pointer;
  transition: background 0.15s;
}

.history-menu-item:hover {
  background: var(--es-surface-muted);
}

.history-menu-item svg {
  color: var(--es-text-secondary);
  flex-shrink: 0;
}

.history-menu-danger {
  color: var(--es-danger-text);
}

.history-menu-danger svg {
  color: var(--es-danger-text);
}

.history-menu-danger:hover {
  background: var(--es-danger-bg);
}

.history-empty {
  padding: 12px 8px;
  font-size: 13px;
  color: var(--es-text-tertiary);
  text-align: center;
}

/* 查看全部 - 列表内最后一条 */
.view-all-history-item {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--es-text-secondary);
  font-size: 13px;
  transition: background 0.2s, color 0.2s;
  flex-shrink: 0;
}

.view-all-history-item:hover {
  background: var(--es-border);
  color: var(--es-text-primary);
}

/* 全部历史面板 */
.history-panel-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 50;
}

.history-panel {
  position: absolute;
  left: 50%;
  top: 8vh;
  transform: translateX(-50%);
  width: 560px;
  max-height: 76vh;
  background: var(--es-surface);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.history-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 0;
}

.history-panel-header h2 {
  font-size: 18px;
  font-weight: 600;
  color: var(--es-text-primary);
  margin: 0;
}

.history-panel-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--es-text-secondary);
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.history-panel-close:hover {
  background: var(--es-border);
  color: var(--es-text-primary);
}

.history-panel-search {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 16px 24px;
  padding: 8px 12px;
  background: var(--es-surface-soft);
  border: 1px solid var(--es-border);
  border-radius: 10px;
  transition: border-color 0.2s;
}

.history-panel-search:focus-within {
  border-color: var(--es-border-strong);
}

.history-panel-search svg {
  color: var(--es-text-tertiary);
  flex-shrink: 0;
}

.history-panel-search input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  font-size: 14px;
  color: var(--es-text-primary);
}

.history-panel-search input::placeholder {
  color: var(--es-text-tertiary);
}

.search-clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  background: var(--es-border);
  color: var(--es-text-secondary);
  border-radius: 50%;
  cursor: pointer;
  padding: 0;
  transition: background 0.2s;
}

.search-clear:hover {
  background: var(--es-border-strong);
}

.history-panel-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 24px 20px;
}

.history-group-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--es-text-tertiary);
  padding: 12px 0 6px;
  position: sticky;
  top: 0;
  background: var(--es-surface);
  z-index: 1;
}

.history-panel-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s;
}

.history-panel-item:hover {
  background: var(--es-surface-soft);
}

.history-panel-item.active {
  background: var(--es-surface-soft);
}

.history-panel-item-main {
  flex: 1;
  min-width: 0;
}

.history-panel-item-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--es-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}

.history-panel-item-preview {
  font-size: 13px;
  color: var(--es-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-panel-item-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
}

.history-panel-item-date {
  font-size: 12px;
  color: var(--es-text-tertiary);
  white-space: nowrap;
}

.history-panel-item-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}

.history-panel-item:hover .history-panel-item-actions {
  opacity: 1;
}

.history-panel-item-actions button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: var(--es-text-secondary);
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  padding: 0;
}

.history-panel-item-actions button:hover {
  background: var(--es-border);
  color: var(--es-text-primary);
}

.history-panel-item-actions button:last-child:hover {
  background: var(--es-danger-bg);
  color: var(--es-danger-text);
}

.history-panel-empty {
  text-align: center;
  padding: 48px 0;
  font-size: 14px;
  color: var(--es-text-tertiary);
}

.user-section {
  position: relative;
  width: 100%;
  padding: 16px 20px;
  border-top: 1px solid var(--es-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  background: var(--es-surface-soft);
  box-sizing: border-box;
  z-index: 30;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  border-radius: 8px;
  padding: 8px;
  transition: background 0.2s;
}

.user-info:hover {
  background: var(--es-border);
}

.user-arrow {
  margin-left: auto;
  color: var(--es-text-tertiary);
  transition: transform 0.2s;
}

.user-arrow.rotated {
  transform: rotate(180deg);
}

.user-menu-fixed {
  background: white;
  border: 1px solid var(--es-border);
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.15s;
  font-size: 14px;
  color: var(--es-text-primary);
}

.menu-item:hover {
  background: var(--es-surface-muted);
}

.menu-item svg {
  color: var(--es-text-secondary);
  flex-shrink: 0;
}

.menu-item-danger {
  color: var(--es-danger-text);
}

.menu-item-danger svg {
  color: var(--es-danger-text);
}

.menu-item-danger:hover {
  background: var(--es-danger-bg);
}

.menu-divider {
  height: 1px;
  background: var(--es-border);
  margin: 4px 0;
}

.menu-slide-enter-active {
  animation: menuSlideUp 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.menu-slide-leave-active {
  animation: menuSlideDown 0.15s cubic-bezier(0.4, 0, 1, 1);
}

@keyframes menuSlideUp {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes menuSlideDown {
  from {
    opacity: 1;
    transform: translateY(0);
  }
  to {
    opacity: 0;
    transform: translateY(8px);
  }
}

.avatar {
  width: 36px;
  height: 36px;
  background: var(--es-text-tertiary);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: var(--es-text-primary);
  overflow: hidden;
  flex-shrink: 0;
}

.avatar .avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
}

.main-content {
  position: fixed;
  left: 240px;
  top: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  background: white;
  transition: left 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.main-content.sidebar-collapsed {
  left: 0;
}

.chat-workspace-shell {
  width: 100%;
  height: 100%;
  display: flex;
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.chat-workspace-shell.workspace-open {
  background: var(--es-surface);
}

.chat-container {
  max-width: 900px;
  margin: 0 auto;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.main-content.lesson-plan-workspace-open .chat-container {
  max-width: none;
  margin: 0;
}

.chat-workspace-shell.workspace-open .chat-container {
  flex: 0 0 clamp(320px, 30vw, 500px);
  width: clamp(320px, 30vw, 500px);
  max-width: clamp(320px, 30vw, 500px);
  min-width: 320px;
  border-right: 1px solid rgba(226, 232, 240, 0.9);
  background: var(--es-surface);
}

.chat-workspace-shell.workspace-open :deep(.lesson-workspace),
.chat-workspace-shell.workspace-open :deep(.ppt-workspace),
.chat-workspace-shell.workspace-open :deep(.interactive-workspace) {
  flex: 1 1 auto;
}

.workspace-slide-enter-active,
.workspace-slide-leave-active {
  transition: transform 0.38s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.28s ease;
}

.workspace-slide-enter-from,
.workspace-slide-leave-to {
  opacity: 0;
  transform: translateX(100%);
}

.welcome-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.welcome-view {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.welcome-logo {
  text-align: center;
  margin-bottom: 40px;
  animation: fadeIn 0.4s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.welcome-title {
  font-size: 36px;
  font-weight: 600;
  margin-bottom: 16px;
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 4px;
  color: var(--es-text-primary);
}

.title-qi,
.title-si {
  font-size: 36px;
  font-weight: 600;
  color: var(--es-text-primary);
}

.title-dot {
  color: var(--es-text-primary);
  font-size: 28px;
  margin: 0 8px;
}

.title-edu {
  font-size: 28px;
  font-weight: 500;
  color: var(--es-text-primary);
  letter-spacing: 1px;
}

.welcome-subtitle {
  font-size: 18px;
  color: var(--es-text-tertiary);
  font-weight: 400;
}

.welcome-wrapper .input-area {
  width: 100%;
  max-width: 600px;
  margin: 0 auto;
  padding-left: clamp(8px, 1.2vw, 12px);
  padding-right: clamp(8px, 1.2vw, 12px);
  animation: slideUp 0.5s cubic-bezier(0.16, 1, 0.3, 1) 0.15s forwards;
  opacity: 0;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.conversation-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.conversation-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 26px;
  overflow-y: auto;
  padding: 26px 0 30px;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.conversation-area::-webkit-scrollbar {
  display: none;
}

/* 语音识别波纹加载卡片 */
.voice-wave-card {
  display: flex;
  align-items: center;
  margin: 12px auto 0;
  padding: 14px 20px;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid var(--es-border);
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  width: fit-content;
  backdrop-filter: blur(8px);
}

.wave-text {
  font-weight: 600;
  font-size: 15px;
  color: var(--es-text-primary);
  letter-spacing: 0.5px;
}

.w {
  display: inline-block;
}

@keyframes wave-letter {
  0%, 100% { font-size: 15px; }
  50% { font-size: 22px; }
}

.w1 { animation: wave-letter 1s infinite; animation-delay: 0s; }
.w2 { animation: wave-letter 1s infinite; animation-delay: -0.9s; }
.w3 { animation: wave-letter 1s infinite; animation-delay: -0.8s; }
.w4 { animation: wave-letter 1s infinite; animation-delay: -0.7s; }
.w5 { animation: wave-letter 1s infinite; animation-delay: -0.6s; }
.w6 { animation: wave-letter 1s infinite; animation-delay: -0.5s; }
.w7 { animation: wave-letter 1s infinite; animation-delay: -0.4s; }

/* ===== 语音识别加载动画 ===== */
.wave-dot-loader {
  display: flex;
  align-items: center;
  gap: 4px;
}

.wave-dot-loader > div {
  width: 8px;
  height: 8px;
  border-radius: 100%;
  background: var(--es-link);
  transform: scale(1);
}

.wave-dot-loader > div:nth-child(1) { animation: dotWave 1s infinite linear; animation-delay: 0s; }
.wave-dot-loader > div:nth-child(2) { animation: dotWave 1s infinite linear; animation-delay: 0.15s; }
.wave-dot-loader > div:nth-child(3) { animation: dotWave 1s infinite linear; animation-delay: 0.3s; }
.wave-dot-loader > div:nth-child(4) { animation: dotWave 1s infinite linear; animation-delay: 0.45s; }
.wave-dot-loader > div:nth-child(5) { animation: dotWave 1s infinite linear; animation-delay: 0.6s; }

@keyframes dotWave {
  0%, 100% { transform: scale(1); opacity: 0.4; }
  50% { transform: scale(1.5); opacity: 1; }
}

.wave-label {
  font-size: 14px;
  color: var(--es-text-secondary);
  margin-left: 8px;
  font-weight: 500;
}

/* 语音识别失败卡片 */
.voice-error-card {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px auto 0;
  padding: 12px 20px;
  background: rgba(254, 242, 242, 0.95);
  border: 1px solid var(--es-danger-bg);
  border-radius: 12px;
  color: var(--es-danger-text);
  font-size: 14px;
  font-weight: 500;
  width: fit-content;
  backdrop-filter: blur(8px);
}

.message {
  display: flex;
  align-items: flex-end;
  width: 100%;
  max-width: min(100%, 900px);
  margin: 0 auto;
  padding: 0 clamp(8px, 1.2vw, 12px);
}

.message.user {
  justify-content: flex-end;
}

.message.ai {
  justify-content: flex-start;
}

.bubble {
  padding: 14px 18px;
  border-radius: 18px;
  font-size: 14.5px;
  line-height: 1.7;
  word-break: break-word;
}

.message.user .bubble {
  max-width: 100%;
  background: var(--es-surface-muted);
  color: var(--es-text-primary);
  border-radius: 16px;
  border: 1px solid var(--es-border);
}

.user-message-content {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  width: auto;
  max-width: min(88%, 720px);
  margin-right: 0;
}

/* ===== 消息快捷按钮 ===== */
.message-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.2s;
  width: fit-content;
}

.message:hover .message-actions {
  opacity: 1;
}

.message-actions.ai-actions {
  align-items: center;
  justify-content: flex-start;
  margin-top: 2px;
  margin-left: 0;
}

.message-actions.user-actions {
  align-items: center;
  justify-content: center;
  margin-top: 4px;
  margin-right: 0;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--es-text-tertiary);
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
}

.action-btn:hover {
  background: var(--es-surface-muted);
  color: var(--es-text-secondary);
}

.action-btn.active {
  color: var(--es-text-secondary);
}

.action-btn.active:hover {
  background: var(--es-border);
}

.action-btn svg {
  width: 16px;
  height: 16px;
}

/* ===== AI 消息 ===== */
.ai-message {
  width: 100%;
}

.ai-message-column {
  min-width: 0;
  width: min(100%, 720px);
  max-width: 720px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-left: 0;
  padding-right: 0;
}

.ai-bubble {
  width: 100%;
  background: transparent;
  border-radius: 0;
  padding: 0;
  box-shadow: none;
}

/* ==================== 蓝图确认卡片 - 商品卡片风格 ==================== */

/* 独立卡片消息：外层不再保留常规气泡 */
.ai-bubble.standalone-card-message {
  background: transparent;
  border: none;
  box-shadow: none;
  border-radius: 0;
  padding: 0;
  max-width: none;
  width: auto;
  overflow: visible;
  transition: none;
}

.ai-bubble.standalone-card-message:hover {
  box-shadow: none;
  transform: none;
}

/* 卡片消息栈：上方普通文本，下方结构化卡片 */
.card-message-stack {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 16px;
}

.card-message-text {
  width: 100%;
  padding: 0;
  background: transparent;
  border-radius: 0;
  box-shadow: none;
}

@media (max-width: 480px) {
  .bp-card,
  .stage-entry-card {
    width: min(100%, calc(100vw - 48px));
    max-width: none;
    padding: 18px 16px;
  }

  .bp-card-footer {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .bp-btn {
    width: 100%;
  }
}

.ai-bubble-loading .typing-dots {
  display: flex;
  gap: 4px;
  padding: 8px 0;
}

/* Markdown 内容样式 */
.message-content,
.message-body {
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Segoe UI", sans-serif;
  font-size: 15px;
  line-height: 1.68;
  color: var(--es-text-primary);
  font-weight: 400;
  letter-spacing: 0.01em;
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.message-content > :first-child,
.message-body > :first-child {
  margin-top: 0;
}

.message-content > :last-child,
.message-body > :last-child {
  margin-bottom: 0;
}

.message-content p,
.message-body p {
  margin: 0 0 0.74em 0;
}

.message-content h1,
.message-content h2,
.message-content h3,
.message-body h1,
.message-body h2,
.message-body h3 {
  margin: 1em 0 0.34em 0;
  font-weight: 600;
  color: var(--es-text-primary);
  line-height: 1.38;
}

.message-content h1,
.message-body h1 {
  font-size: 19px;
}

.message-content h2,
.message-body h2 {
  font-size: 17px;
}

.message-content h3,
.message-body h3 {
  font-size: 15px;
}

.message-content ul,
.message-content ol,
.message-body ul,
.message-body ol {
  margin: 0.72em 0;
  padding-left: 1.45em;
}

.message-content li,
.message-body li {
  margin: 0.22em 0;
  line-height: 1.65;
}

.message-content code,
.message-body code {
  background: var(--es-surface-muted);
  color: var(--es-danger-text);
  padding: 2px 6px;
  border-radius: 5px;
  font-size: 0.9em;
  font-family: "Fira Code", "Consolas", monospace;
}

.message-content pre,
.message-body pre {
  background: var(--es-text-primary);
  color: var(--es-border);
  padding: 12px 14px;
  border-radius: 10px;
  overflow-x: auto;
  margin: 0.9em 0;
}

.message-content pre code,
.message-body pre code {
  background: transparent;
  color: inherit;
  padding: 0;
}

.message-content blockquote,
.message-body blockquote {
  border-left: 3px solid var(--es-border-strong);
  padding: 8px 12px;
  margin: 0.9em 0;
  color: var(--es-text-secondary);
  background: var(--es-surface-soft);
  border-radius: 0 10px 10px 0;
}

.message-content a,
.message-body a {
  color: var(--es-success-text);
  text-decoration: none;
}

.message-content a:hover,
.message-body a:hover {
  text-decoration: underline;
}

.message-content strong,
.message-body strong {
  font-weight: 600;
  color: var(--es-text-primary);
}

.message-content em,
.message-body em {
  font-style: italic;
  color: var(--es-text-secondary);
}

.message-content table,
.message-body table {
  border-collapse: collapse;
  width: 100%;
  margin: 0.95em 0;
  font-size: 14px;
}

.message-content th,
.message-content td,
.message-body th,
.message-body td {
  border: 1px solid var(--es-border);
  padding: 8px 12px;
  text-align: left;
}

.message-content th,
.message-body th {
  background: var(--es-surface-soft);
  font-weight: 600;
}

/* ===== 引用区域（气泡内部）===== */
.references {
  margin-top: 16px;
  border-top: 1px solid var(--es-border);
  padding-top: 12px;
}

.references-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--es-text-secondary);
  font-size: 13px;
  cursor: pointer;
  user-select: none;
  transition: color 0.2s;
}

.references-header:hover {
  color: var(--es-text-secondary);
}

.references-icon {
  width: 16px;
  height: 16px;
  transition: transform 0.2s;
  flex-shrink: 0;
}

.references.expanded .references-icon {
  transform: rotate(180deg);
}

.references-list {
  display: none;
  margin-top: 12px;
  gap: 8px;
  flex-wrap: wrap;
}

.references.expanded .references-list {
  display: flex;
}

.reference-tag {
  background: white;
  border: 1px solid var(--es-border);
  border-radius: 6px;
  padding: 6px 12px 6px 30px;
  font-size: 12px;
  color: var(--es-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.reference-tag::before {
  content: attr(data-num);
  position: absolute;
  left: 6px;
  top: 50%;
  transform: translateY(-50%);
  width: 18px;
  height: 18px;
  background: var(--es-surface-muted);
  color: var(--es-text-secondary);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
}

.reference-tag:hover {
  border-color: var(--es-border-strong);
  color: var(--es-text-primary);
  background: var(--es-surface-soft);
}

/* 底部统计信息 */
.references-stats {
  display: flex;
  gap: 10px;
  margin-top: 12px;
  font-size: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.stat-item {
  background: white;
  padding: 4px 10px;
  border-radius: 4px;
  border: 1px solid var(--es-border);
  color: var(--es-text-tertiary);
}

.stat-action {
  color: var(--es-text-secondary);
  cursor: pointer;
  transition: color 0.15s;
  user-select: none;
}

.stat-action:hover {
  color: var(--es-text-primary);
  text-decoration: underline;
}

.reference-detail strong {
  color: var(--es-text-primary);
}

/* KaTeX 公式样式 */
.katex {
  font-size: 1.1em;
}

.katex-display {
  margin: 16px 0;
  overflow-x: auto;
  padding: 8px 0;
}

/* 消息头像 */
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
  flex-shrink: 0;
}

.user-avatar {
  background: var(--es-text-primary);
  color: white;
  margin-left: 10px;
  align-self: center;
  overflow: hidden;
}

.user-avatar .avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* AI 气泡加载中 */
.ai-bubble-loading {
  display: flex;
  align-items: center;
  padding: 6px 0;
  min-width: 48px;
  min-height: 36px;
}

/* 打字动画 */
.typing-dots {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}

.typing-dots span {
  width: 8px;
  height: 8px;
  background: var(--es-text-tertiary);
  border-radius: 50%;
  animation: typingBounce 1.4s infinite ease-in-out both;
}

.typing-dots span:nth-child(1) { animation-delay: -0.32s; }
.typing-dots span:nth-child(2) { animation-delay: -0.16s; }
.typing-dots span:nth-child(3) { animation-delay: 0s; }

@keyframes typingBounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.input-area {
  width: 100%;
  max-width: 900px;
  margin: 0 auto;
  flex-shrink: 0;
  padding: 22px clamp(8px, 1.2vw, 12px) 20px;
  background: white;
}

.input-card {
  background: white;
  border: 1px solid var(--es-border);
  border-radius: 20px;
  padding: 12px 14px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06), 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}

.input-card:focus-within {
  border-color: var(--es-link);
  box-shadow: 0 4px 32px rgba(59, 130, 246, 0.15), 0 1px 3px rgba(59, 130, 246, 0.08);
}

.input-card textarea {
  width: 100%;
  border: none;
  resize: none;
  font-family: inherit;
  font-size: 15px;
  padding: 6px 0;
  outline: none;
  background: transparent;
  color: var(--es-text-primary);
}

.ppt-input-shell {
  display: block;
}

.ppt-input-shell.active {
  display: grid;
  grid-template-columns: 108px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.ppt-input-main {
  min-width: 0;
}

.ppt-template-trigger {
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  min-width: 0;
  border-radius: 16px;
  transform: rotate(-3.5deg);
  transform-origin: center;
  transition: transform 0.22s ease, filter 0.22s ease;
  filter: drop-shadow(0 6px 14px rgba(15, 23, 42, 0.12));
}

.ppt-template-trigger:hover {
  transform: rotate(0deg) scale(1.04);
  filter: drop-shadow(0 8px 18px rgba(15, 23, 42, 0.18));
}

.ppt-template-trigger:focus-visible {
  outline: 2px solid var(--es-link-hover);
  outline-offset: 2px;
}

.ppt-template-trigger__surface,
.ppt-template-trigger__empty {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 14px;
  padding: 10px 9px;
  overflow: hidden;
}

/* 有 cover 图时让图片充满按钮，盖掉文字样式 */
.ppt-template-trigger__surface:has(.ppt-template-trigger__cover) {
  padding: 0;
}

.ppt-template-trigger__cover {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  border-radius: 14px;
}

.ppt-template-trigger__surface:not(:has(.ppt-template-trigger__cover))::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.48), transparent 30%),
    radial-gradient(circle at bottom right, rgba(255, 255, 255, 0.18), transparent 34%);
  pointer-events: none;
}

.ppt-template-trigger__surface {
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.38);
}

.ppt-template-trigger__badge {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.68);
  color: rgba(15, 23, 42, 0.78);
  font-size: 10px;
  font-weight: 700;
}

.ppt-template-trigger__name {
  position: relative;
  z-index: 1;
  display: block;
  margin-top: 10px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.3;
}

.ppt-template-trigger__meta {
  position: relative;
  z-index: 1;
  display: block;
  margin-top: 4px;
  font-size: 10px;
  opacity: 0.8;
}

.ppt-template-trigger__empty {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  border: 1px solid var(--es-link-soft-strong);
  background: linear-gradient(135deg, var(--es-surface) 0%, var(--es-surface-soft) 100%);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.72);
}

.ppt-template-trigger__empty-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--es-text-primary);
}

.ppt-template-trigger__empty-hint {
  font-size: 10px;
  color: var(--es-text-tertiary);
}

.input-card textarea::placeholder {
  color: var(--es-text-tertiary);
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
}

.left-actions {
  display: flex;
  gap: 12px;
}

.icon-btn {
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--es-text-secondary);
  transition: color 0.2s;
  padding: 4px;
}

.icon-btn:hover {
  color: var(--es-link);
}

.icon-btn.recording {
  color: var(--es-danger-text);
  animation: pulse-recording 1s infinite;
}

@keyframes pulse-recording {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.15); }
}

.send-btn {
  background: linear-gradient(135deg, var(--es-link) 0%, var(--es-link-hover) 100%);
  border: none;
  border-radius: 32px;
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

.send-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.4);
}

.send-btn:active {
  transform: scale(0.95);
}

.send-btn:hover {
  background-color: var(--es-link);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-hint {
  text-align: center;
  margin-top: 12px;
  font-size: 12px;
  color: var(--es-text-tertiary);
}

.floating-drawer-btn {
  position: fixed;
  z-index: 40;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(215, 209, 198, 0.9);
  border-radius: 14px;
  width: 44px;
  height: 44px;
  color: var(--es-text-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  backdrop-filter: blur(12px);
  box-shadow: 0 10px 28px rgba(17, 16, 12, 0.08);
  top: 20px;
  right: 24px;
  transition: background 0.2s, border-color 0.2s, box-shadow 0.2s, transform 0.2s, opacity 0.2s;
}

.floating-drawer-btn:hover {
  background: rgba(255, 255, 255, 0.96);
  border-color: rgba(198, 191, 179, 0.95);
  box-shadow: 0 14px 34px rgba(17, 16, 12, 0.1);
  transform: translateY(-1px);
}

/* 关闭按钮：保持在右上角，与抽屉滑入方向一致 */
.floating-drawer-btn.close-btn {
  top: 20px;
  right: 24px;
  left: auto;
}

@media (max-width: 768px) {
  .chat-workspace-shell {
    flex-direction: column;
  }

  .sidebar {
    position: fixed;
    top: 0;
    left: 0;
    height: 100%;
  }
  .float-btn-group {
    top: 16px;
    left: 16px;
    border-radius: 18px;
    padding: 4px 6px;
  }
  .sidebar-float-btn {
    width: 26px;
    height: 26px;
  }
  .chat-container {
    padding: 80px 20px 24px;
  }
  .floating-drawer-btn {
    top: 16px;
    right: 16px;
  }

  .ppt-input-shell.active {
    grid-template-columns: 1fr;
  }

  .ppt-template-trigger__surface,
  .ppt-template-trigger__empty {
    min-height: 72px;
  }

}

::-webkit-scrollbar {
  width: 5px;
}

::-webkit-scrollbar-track {
  background: var(--es-surface-muted);
}

::-webkit-scrollbar-thumb {
  background: var(--es-text-tertiary);
  border-radius: 5px;
}

.nav-items {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 14px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  color: var(--es-text-secondary);
  font-size: 14px;
  font-weight: 500;
  position: relative;
}

.nav-item:hover:not(.active) {
  background: linear-gradient(135deg, var(--es-surface-soft) 0%, var(--es-surface-soft) 100%);
  color: var(--es-text-primary);
  transform: translateX(3px);
}

.nav-item.active {
  background: linear-gradient(135deg, var(--es-link-soft) 0%, var(--es-link-soft) 100%);
  color: var(--es-link-hover);
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.15);
}

.nav-item svg {
  color: var(--es-text-tertiary);
  transition: color 0.2s;
}

.nav-item.active svg {
  color: var(--es-link);
}

.view-transition {
  opacity: 0.95;
}

.more-menu-container {
  position: relative;
  display: inline-block;
}

.more-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  margin-top: 8px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  min-width: 160px;
  z-index: 100;
  padding: 8px 0;
}

.more-dropdown.dropdown-top {
  top: auto;
  bottom: 100%;
  margin-top: 0;
  margin-bottom: 8px;
}

.dropdown-fade-enter-active {
  animation: dropdownFadeIn 0.2s ease-out;
}

.dropdown-fade-leave-active {
  animation: dropdownFadeOut 0.15s ease-in;
}

@keyframes dropdownFadeIn {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes dropdownFadeOut {
  from {
    opacity: 1;
    transform: translateY(0);
  }
  to {
    opacity: 0;
    transform: translateY(-8px);
  }
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.2s;
  font-size: 14px;
  color: var(--es-text-primary);
  position: relative;
}

.dropdown-item:hover {
  background-color: var(--es-surface-muted);
}

.dropdown-icon-svg {
  color: var(--es-text-secondary);
  flex-shrink: 0;
}

.dropdown-item:hover .dropdown-icon-svg {
  color: var(--es-link);
}

.arrow-icon {
  margin-left: auto;
  color: var(--es-text-tertiary);
  flex-shrink: 0;
}

.dropdown-item.has-submenu {
  position: relative;
}

.submenu {
  position: absolute;
  left: 100%;
  top: -8px;
  margin-left: 8px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  min-width: 220px;
  padding: 8px 0;
  opacity: 0;
  visibility: hidden;
  transform: translateX(-4px);
  transition: all 0.2s ease;
}

.dropdown-item.has-submenu:hover .submenu {
  opacity: 1;
  visibility: visible;
  transform: translateX(0);
}

.submenu-item {
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.2s;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.submenu-item:hover {
  background-color: var(--es-surface-muted);
}

.submenu-item.active {
  background-color: var(--es-link-soft);
}

.submenu-item.active span:first-child {
  color: var(--es-link);
  font-weight: 600;
}

.submenu-item span:first-child {
  font-size: 14px;
  color: var(--es-text-primary);
}

.submenu-hint {
  font-size: 12px;
  color: var(--es-text-tertiary);
}

.search-mode-tag {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background-color: var(--es-link-soft);
  border: 1px solid var(--es-link-soft-strong);
  border-radius: 20px;
  font-size: 13px;
  color: var(--es-link-hover);
  cursor: pointer;
}

.mode-tag-inline {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background-color: var(--es-success-bg);
  border: 1px solid var(--es-success-bg);
  border-radius: 20px;
  font-size: 13px;
  color: var(--es-success-text);
  cursor: pointer;
}

.mode-tag-inline svg {
  color: var(--es-success-text);
}

/* 出现动画 */
.tag-enter-active {
  animation: tagScaleIn 0.25s ease-out;
}
.tag-leave-active {
  animation: tagScaleOut 0.2s ease-in forwards;
}

@keyframes tagScaleIn {
  from { opacity: 0; transform: scale(0.7); }
  to   { opacity: 1; transform: scale(1); }
}

@keyframes tagScaleOut {
  from { opacity: 1; transform: scale(1); }
  to   { opacity: 0; transform: scale(0.7); }
}

.search-mode-tag svg {
  color: var(--es-link);
}

.icon-fade-enter-active,
.icon-fade-leave-active {
  transition: opacity 0.15s ease-out;
}

.icon-fade-enter-from,
.icon-fade-leave-to {
  opacity: 0;
}

.tooltip {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  margin-bottom: 8px;
  padding: 6px 12px;
  background-color: var(--es-text-primary);
  color: white;
  font-size: 12px;
  border-radius: 6px;
  white-space: nowrap;
  z-index: 1000;
}

.tooltip::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border: 6px solid transparent;
  border-top-color: var(--es-text-primary);
}

.tooltip-fade-enter-active,
.tooltip-fade-leave-active {
  transition: opacity 0.15s ease-out;
}

.tooltip-fade-enter-from,
.tooltip-fade-leave-to {
  opacity: 0;
}

.search-mode-tag.search-mode-off {
  background-color: var(--es-surface-soft);
  border-color: var(--es-border);
  color: var(--es-text-secondary);
}

.search-mode-tag.search-mode-off svg {
  color: var(--es-text-tertiary);
}

/* ===== 附件悬浮卡片 ===== */
.attachment-card-list {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
  margin-bottom: 10px;
  /* 自定义滚动条样式 */
  scrollbar-width: thin;
  scrollbar-color: var(--es-border-strong) transparent;
}

.attachment-card-list::-webkit-scrollbar {
  height: 4px;
}

.attachment-card-list::-webkit-scrollbar-track {
  background: transparent;
}

.attachment-card-list::-webkit-scrollbar-thumb {
  background: var(--es-border-strong);
  border-radius: 2px;
}

.attachment-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--es-surface);
  border: 1px solid var(--es-border);
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  flex-shrink: 0;
  width: 260px;
  transition: opacity 0.2s;
}

.attachment-card.att-loading {
  opacity: 0.7;
}

.att-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid var(--es-border);
  border-top-color: var(--es-text-secondary);
  border-radius: 50%;
  animation: attSpin 0.6s linear infinite;
}

@keyframes attSpin {
  to { transform: rotate(360deg); }
}

.att-card-icon {
  width: 36px;
  height: 36px;
  background: var(--es-surface-muted);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.att-card-icon svg {
  color: var(--es-text-secondary);
}

.att-card-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.att-card-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--es-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.att-card-size {
  font-size: 11px;
  color: var(--es-text-tertiary);
}

.att-card-remove {
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
  color: var(--es-border-strong);
  transition: color 0.15s;
  border-radius: 4px;
  flex-shrink: 0;
}

.att-card-remove:hover {
  color: var(--es-danger-text);
  background: var(--es-danger-bg);
}

/* ===== 拖拽毛玻璃遮罩 ===== */
.input-card.drag-over {
  border-color: var(--es-link);
  border-style: dashed;
}

.drag-overlay {
  position: absolute;
  inset: -1px;
  background: rgba(239, 246, 255, 0.85);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 2px dashed var(--es-link);
  border-radius: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--es-link);
  pointer-events: none;
  z-index: 10;
}

.drag-overlay svg {
  color: var(--es-link);
  opacity: 0.8;
}

.drag-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--es-link-hover);
}

.drag-hint {
  font-size: 12px;
  color: var(--es-text-secondary);
}

/* fade transition for drag overlay */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.category-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
}

.category-modal-content {
  background: white;
  border-radius: 20px;
  padding: 28px;
  width: 420px;
  max-width: 90vw;
  box-shadow: 0 25px 60px rgba(0, 0, 0, 0.25), 0 10px 20px rgba(0, 0, 0, 0.1);
}

.category-modal-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--es-text-primary);
  margin-bottom: 4px;
}

.category-modal-subtitle {
  font-size: 13px;
  color: var(--es-text-secondary);
  margin-bottom: 20px;
}

.category-options {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 24px;
}

.category-option {
  padding: 12px 16px;
  border-radius: 12px;
  border: 2px solid var(--es-border);
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
  text-align: center;
}

.category-option:hover {
  border-color: var(--es-link-soft-strong);
  background: var(--es-link-soft);
  color: var(--es-link-hover);
}

.category-option.selected {
  background: linear-gradient(135deg, var(--es-link) 0%, var(--es-link-hover) 100%);
  color: white;
  border-color: transparent;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.35);
}

.category-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.btn-cancel {
  padding: 10px 20px;
  border: none;
  background: var(--es-surface-soft);
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  color: var(--es-text-secondary);
  transition: all 0.2s;
}

.btn-cancel:hover {
  background: var(--es-border);
  color: var(--es-text-primary);
}

.btn-confirm {
  padding: 10px 24px;
  border: none;
  background: linear-gradient(135deg, var(--es-link) 0%, var(--es-link-hover) 100%);
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  color: white;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
  transition: all 0.2s;
}

.btn-confirm:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.4);
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.2s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

/* 自定义弹窗样式 */
.custom-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.custom-modal {
  background: white;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  min-width: 360px;
  max-width: 90vw;
  overflow: hidden;
}

.custom-modal-header {
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--es-surface-muted);
}

.custom-modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--es-text-primary);
}

.custom-modal-body {
  padding: 20px 24px;
}

.custom-input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--es-border);
  border-radius: 8px;
  font-size: 14px;
  color: var(--es-text-primary);
  transition: all 0.2s;
  box-sizing: border-box;
}

.custom-input:focus {
  outline: none;
  border-color: var(--es-link);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.confirm-text {
  margin: 0;
  font-size: 14px;
  color: var(--es-text-secondary);
  line-height: 1.6;
}

.custom-modal-footer {
  padding: 16px 24px 20px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.btn-danger {
  background: linear-gradient(135deg, var(--es-danger-text) 0%, var(--es-danger-text) 100%) !important;
}

.btn-danger:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(239, 68, 68, 0.4) !important;
}

/* 卡片数据区域样式 */
.card-data-section {
  padding: 8px 0;
}

/* ====== Blueprint 卡片：参考商品卡片样式 ====== */
.bp-card {
  --font-color: var(--es-text-primary);
  --font-color-sub: var(--es-text-secondary);
  --bg-color: var(--es-surface);
  --main-color: var(--es-text-primary);
  --main-focus: var(--es-link);
  width: 360px;
  max-width: min(100%, calc(100vw - 88px));
  background: var(--bg-color);
  border: 2px solid var(--main-color);
  box-shadow: 6px 6px 0 var(--main-color);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  padding: 22px 20px 18px;
  gap: 14px;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
}

/* 卡片头部：图标居中 */
.bp-card-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.bp-card-icon {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.5s;
  color: var(--main-color);
  background: var(--es-surface-soft);
  border: 2px solid var(--main-color);
}

.bp-card-icon svg {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  stroke: currentColor;
}

.bp-card-icon:hover {
  transform: translateY(-3px);
}

.bp-card-title {
  font-size: 18px;
  font-weight: 600;
  text-align: center;
  color: var(--font-color);
  letter-spacing: -0.01em;
}

/* 卡片描述区 */
.bp-card-subtitle {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.bp-info-row {
  display: flex;
  gap: 8px;
  font-size: 14px;
  line-height: 1.6;
}

.bp-info-label {
  color: var(--font-color-sub);
  min-width: 50px;
  flex-shrink: 0;
}

.bp-info-value {
  color: var(--font-color);
  font-weight: 500;
  flex: 1;
  min-width: 0;
  word-break: break-word;
}

.bp-info-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 14px;
  line-height: 1.6;
}

.bp-section-label {
  color: var(--font-color-sub);
  flex-shrink: 0;
}

.bp-section-value {
  color: var(--font-color);
  word-break: break-word;
}

/* 分隔线：圆角 */
.bp-card-divider {
  width: 100%;
  border: 1px solid var(--main-color);
  border-radius: 50px;
  margin: 0;
}

/* 卡片底部：左右分布 */
.bp-card-footer {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  align-items: stretch;
}

.stage-entry-card {
  width: 360px;
  max-width: min(100%, calc(100vw - 88px));
}

.stage-entry-footer {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
}

.bp-card-note {
  font-size: 12px;
  line-height: 1.5;
  color: var(--font-color-sub);
  max-width: none;
}

.bp-status-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 24px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  border: 2px solid var(--main-color);
  background: var(--es-surface);
}

.bp-status-chip.processing {
  color: var(--es-link-hover);
  border-color: var(--es-link-hover);
  background: var(--es-link-soft);
}

.bp-status-chip.completed {
  color: var(--es-success-text);
  border-color: var(--es-success-text);
  background: var(--es-success-bg);
}

.bp-status-chip.failed {
  color: var(--es-danger-text);
  border-color: var(--es-danger-text);
  background: var(--es-danger-bg);
}

.bp-btn {
  min-height: 44px;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.2;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--bg-color);
  border: 2px solid var(--main-color);
  color: var(--font-color);
  white-space: nowrap;
}

.bp-btn svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  fill: var(--main-color);
  transition: all 0.3s;
}

.bp-btn:hover {
  border: 2px solid var(--main-focus);
}

.bp-btn:hover svg {
  fill: var(--main-focus);
}

.bp-btn:active {
  transform: translateY(3px);
}

.bp-btn:disabled {
  opacity: 0.58;
  cursor: not-allowed;
  pointer-events: none;
  transform: none;
  box-shadow: none;
}

.bp-btn.primary {
  color: var(--es-text-inverse);
  background: var(--main-color);
}

.bp-btn.primary svg {
  fill: var(--es-surface);
}

.bp-btn.primary:hover {
  background: var(--main-focus);
}

.bp-btn.primary:hover svg {
  fill: var(--es-surface);
}

.bp-btn.static {
  cursor: default;
  pointer-events: none;
}

.bp-btn.static:active {
  transform: none;
}
.card-message {
  max-width: 100%;
}

.generation-card {
  width: min(420px, 100%);
  background: var(--es-surface);
  border: 2px solid var(--es-text-primary);
  border-radius: 10px;
  padding: 20px;
  box-shadow: 4px 4px var(--es-text-primary);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.generation-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.generation-card-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--es-text-primary);
  line-height: 1.5;
}

.generation-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.generation-badge.processing {
  background: var(--es-link-soft);
  color: var(--es-link-hover);
}

.generation-badge.completed {
  background: var(--es-success-bg);
  color: var(--es-success-text);
}

.generation-badge.failed {
  background: var(--es-danger-bg);
  color: var(--es-danger-text);
}

.generation-card-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.generation-summary {
  margin: 0;
  color: var(--es-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.generation-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--es-surface-soft);
  border: 1px solid var(--es-link-soft-strong);
  color: var(--es-text-primary);
  font-size: 13px;
}

.generation-preview {
  white-space: pre-wrap;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid var(--es-link-soft-strong);
  background: var(--es-surface-soft);
  color: var(--es-text-primary);
  font-size: 13px;
  line-height: 1.7;
}

.generation-outline {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.generation-outline-item {
  font-size: 13px;
  color: var(--es-text-primary);
  line-height: 1.5;
}

.generation-card-actions {
  display: flex;
  justify-content: flex-end;
}

.generation-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  padding: 0 16px;
  border-radius: 6px;
  background: var(--es-text-primary);
  color: var(--es-surface);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  border: 2px solid var(--es-text-primary);
  cursor: pointer;
  appearance: none;
  transition: background 0.2s ease, transform 0.2s ease;
}

.generation-link:hover {
  background: var(--es-link-hover);
  border-color: var(--es-link-hover);
  transform: translateY(-1px);
}
</style>
