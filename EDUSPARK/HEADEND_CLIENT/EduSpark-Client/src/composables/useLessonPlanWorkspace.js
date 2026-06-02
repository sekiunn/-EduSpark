import { computed, onUnmounted, ref } from 'vue'
import {
  connectLessonPlanDocumentStream,
  exportLessonPlanDocument,
  getLessonPlanDocument,
  rewriteLessonPlanDocumentSelection,
  updateLessonPlanDocumentContent
} from '@/api'

const STREAMABLE_STATUSES = new Set(['preparing', 'retrieving', 'enriching', 'drafting'])

export function useLessonPlanWorkspace() {
  const visible = ref(false)
  const currentDocumentId = ref(null)
  const document = ref(null)
  const draftContent = ref('')
  const lastSyncedContent = ref('')
  const streamingText = ref('')
  const loading = ref(false)
  const saving = ref(false)
  const exporting = ref(false)
  const rewriting = ref(false)
  const rewriteSuggestion = ref(null)
  const error = ref('')
  const streamConnected = ref(false)
  const streamError = ref('')

  let streamHandle = null
  // 流式节流：deepseek 逐字流约 100+ 次/秒，若每个 delta 都更新 streamingText 会触发等量的
  // markdown 重渲染、打满主线程，导致"看不到流式、只在最后一次性出现"。这里把 delta 攒到
  // ~90ms 一批再更新，渲染降到约 11 次/秒，既流畅又省 CPU。
  let pendingDelta = ''
  let flushTimer = null

  const clearStreamThrottle = () => {
    if (flushTimer) {
      clearTimeout(flushTimer)
      flushTimer = null
    }
    pendingDelta = ''
  }

  const status = computed(() => document.value?.status || '')
  const isStreamable = computed(() => STREAMABLE_STATUSES.has(status.value))
  const isDirty = computed(() => draftContent.value !== lastSyncedContent.value)
  const isEditable = computed(() => document.value?.status === 'completed')

  const stopStream = () => {
    clearStreamThrottle()
    if (streamHandle) {
      streamHandle.close()
      streamHandle = null
    }
    streamConnected.value = false
  }

  const clearRewriteSuggestion = () => {
    rewriteSuggestion.value = null
  }

  const applyDocument = (payload, { syncEditor = false } = {}) => {
    document.value = payload || null
    error.value = payload?.errorMessage || ''

    const nextContent = payload?.content || ''
    if (payload?.status === 'completed') {
      if (syncEditor || !isDirty.value) {
        draftContent.value = nextContent
        lastSyncedContent.value = nextContent
      } else {
        lastSyncedContent.value = nextContent
      }
    }

    if (payload?.status !== 'completed' && nextContent) {
      streamingText.value = nextContent
    }

    if (!STREAMABLE_STATUSES.has(payload?.status)) {
      stopStream()
    }
  }

  const fetchDocument = async ({ syncEditor = false } = {}) => {
    if (!currentDocumentId.value) return null

    const res = await getLessonPlanDocument(currentDocumentId.value)
    const payload = res?.data || null
    applyDocument(payload, { syncEditor })
    return payload
  }

  const startStream = () => {
    stopStream()
    if (!currentDocumentId.value) return

    streamError.value = ''
    streamHandle = connectLessonPlanDocumentStream(currentDocumentId.value, {
      snapshot: (payload) => {
        streamConnected.value = true
        applyDocument(payload, { syncEditor: false })
      },
      status: (payload) => {
        streamConnected.value = true
        applyDocument(payload, { syncEditor: false })
      },
      content_delta: (payload) => {
        streamConnected.value = true
        const delta = payload?.delta || ''
        if (!delta) return
        // 节流：攒到 ~90ms 一批再更新，避免逐字触发 markdown 重渲染、打满主线程。
        pendingDelta += delta
        if (!flushTimer) {
          flushTimer = setTimeout(() => {
            flushTimer = null
            if (pendingDelta) {
              streamingText.value += pendingDelta
              pendingDelta = ''
            }
          }, 90)
        }
      },
      completed: (payload) => {
        streamConnected.value = true
        // 终态以服务端完整内容为准，丢弃未刷新的零头 delta，避免重复拼接。
        clearStreamThrottle()
        applyDocument(payload, { syncEditor: true })
        streamingText.value = payload?.content || ''
        stopStream()
        // 兜底：completed 事件 payload 很大（含 knowledgeSources 等），万一不完整会导致编辑器正文为空。
        // 这里再用 REST 重新拉一次权威完整文档，强制同步进编辑器，确保完成后正文不丢。
        fetchDocument({ syncEditor: true }).catch(() => {})
      },
      failed: (payload) => {
        applyDocument(payload, { syncEditor: false })
        stopStream()
      },
      error: (err) => {
        streamConnected.value = false
        streamError.value = err.message || '教案工作区流连接失败'
      },
      close: () => {
        streamConnected.value = false
      }
    })
  }

  const openWorkspace = async (documentId, { syncEditor = true } = {}) => {
    if (!documentId) return null

    visible.value = true
    currentDocumentId.value = documentId
    loading.value = true
    error.value = ''
    streamError.value = ''
    clearRewriteSuggestion()

    try {
      const payload = await fetchDocument({ syncEditor })
      streamingText.value = payload?.content || ''
      if (STREAMABLE_STATUSES.has(payload?.status)) {
        startStream()
      } else {
        stopStream()
      }
      return payload
    } finally {
      loading.value = false
    }
  }

  const closeWorkspace = () => {
    visible.value = false
    clearRewriteSuggestion()
    stopStream()
  }

  const resetWorkspace = () => {
    closeWorkspace()
    currentDocumentId.value = null
    document.value = null
    draftContent.value = ''
    lastSyncedContent.value = ''
    streamingText.value = ''
    error.value = ''
    streamError.value = ''
    rewriting.value = false
  }

  const saveDocument = async () => {
    if (!currentDocumentId.value) return null

    saving.value = true
    error.value = ''
    try {
      const res = await updateLessonPlanDocumentContent(currentDocumentId.value, draftContent.value)
      applyDocument(res?.data || null, { syncEditor: true })
      return res?.data || null
    } finally {
      saving.value = false
    }
  }

  const requestRewrite = async (selectedText, instruction) => {
    if (!currentDocumentId.value) return null

    rewriting.value = true
    rewriteSuggestion.value = null
    try {
      const res = await rewriteLessonPlanDocumentSelection(
        currentDocumentId.value,
        selectedText,
        instruction
      )
      rewriteSuggestion.value = res?.data || null
      return rewriteSuggestion.value
    } finally {
      rewriting.value = false
    }
  }

  const exportDocument = async () => {
    if (!currentDocumentId.value) return null

    exporting.value = true
    error.value = ''
    try {
      const res = await exportLessonPlanDocument(currentDocumentId.value)
      applyDocument(res?.data || null, { syncEditor: false })
      return res?.data || null
    } finally {
      exporting.value = false
    }
  }

  onUnmounted(() => {
    stopStream()
  })

  return {
    visible,
    currentDocumentId,
    document,
    draftContent,
    streamingText,
    loading,
    saving,
    exporting,
    rewriting,
    rewriteSuggestion,
    error,
    streamConnected,
    streamError,
    isDirty,
    isEditable,
    isStreamable,
    openWorkspace,
    closeWorkspace,
    resetWorkspace,
    saveDocument,
    requestRewrite,
    clearRewriteSuggestion,
    exportDocument,
    stopStream,
    fetchDocument
  }
}
