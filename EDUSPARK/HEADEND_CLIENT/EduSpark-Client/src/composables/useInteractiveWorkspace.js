import { computed, onUnmounted, ref } from 'vue'
import {
  connectInteractiveDocumentStream,
  exportInteractiveDocument,
  getInteractiveDocument,
  updateInteractiveDocumentContent
} from '@/api'

const STREAMABLE_STATUSES = new Set(['preparing', 'implementing', 'refining'])
const NON_RETRYABLE_HTTP_STATUSES = new Set([400, 401, 403, 404])
const MAX_RECONNECT_DELAY_MS = 8000
const RECOVERED_STATE_HOLD_MS = 2400

export function useInteractiveWorkspace() {
  const visible = ref(false)
  const currentDocumentId = ref(null)
  const document = ref(null)
  const draftContent = ref('')
  const lastSyncedContent = ref('')
  const streamingText = ref('')
  const loading = ref(false)
  const saving = ref(false)
  const exporting = ref(false)
  const error = ref('')
  const streamConnected = ref(false)
  const streamError = ref('')
  const streamState = ref('idle')
  const reconnectAttempt = ref(0)

  let streamHandle = null
  let reconnectTimer = null
  let recoveredStateTimer = null
  let streamRequestId = 0

  const status = computed(() => document.value?.status || '')
  const isStreamable = computed(() => STREAMABLE_STATUSES.has(status.value))
  const isDirty = computed(() => draftContent.value !== lastSyncedContent.value)
  const isEditable = computed(() => document.value?.status === 'completed')

  const clearReconnectTimer = () => {
    if (reconnectTimer) {
      window.clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  const clearRecoveredStateTimer = () => {
    if (recoveredStateTimer) {
      window.clearTimeout(recoveredStateTimer)
      recoveredStateTimer = null
    }
  }

  const resetConnectionState = ({ clearError = true } = {}) => {
    clearReconnectTimer()
    clearRecoveredStateTimer()
    streamConnected.value = false
    streamState.value = 'idle'
    reconnectAttempt.value = 0
    if (clearError) {
      streamError.value = ''
    }
  }

  const stopStream = ({ resetState = true } = {}) => {
    clearReconnectTimer()
    clearRecoveredStateTimer()
    streamRequestId += 1
    if (streamHandle) {
      streamHandle.close()
      streamHandle = null
    }
    streamConnected.value = false
    if (resetState) {
      streamState.value = 'idle'
      reconnectAttempt.value = 0
      streamError.value = ''
    }
  }

  const applyDocument = (payload, { syncEditor = false } = {}) => {
    document.value = payload || null
    error.value = payload?.errorMessage || ''

    const nextContent = payload?.htmlContent || ''
    if (payload?.status === 'completed') {
      if (syncEditor || !isDirty.value) {
        draftContent.value = nextContent
        lastSyncedContent.value = nextContent
      } else {
        lastSyncedContent.value = nextContent
      }
      streamingText.value = nextContent
    } else if (STREAMABLE_STATUSES.has(payload?.status)) {
      if (nextContent) {
        streamingText.value = nextContent
      }
    } else {
      streamingText.value = nextContent
    }

    if (!STREAMABLE_STATUSES.has(payload?.status)) {
      stopStream()
    }
  }

  const fetchDocument = async ({ syncEditor = false } = {}) => {
    if (!currentDocumentId.value) return null

    const res = await getInteractiveDocument(currentDocumentId.value)
    const payload = res?.data || null
    applyDocument(payload, { syncEditor })
    return payload
  }

  const shouldKeepStreaming = () => {
    return visible.value && !!currentDocumentId.value && STREAMABLE_STATUSES.has(document.value?.status)
  }

  const resolveStreamFailureMessage = (err) => {
    if (err?.status === 401 || err?.status === 403) {
      return '工作区连接已失效，请重新打开'
    }
    if (err?.status === 404) {
      return '互动页面不存在或已被删除'
    }
    return err?.message || '互动工作区连接失败'
  }

  const canReconnect = (err) => {
    if (!shouldKeepStreaming()) {
      return false
    }
    if (NON_RETRYABLE_HTTP_STATUSES.has(err?.status)) {
      return false
    }
    return true
  }

  const markStreamActive = ({ recovered = false } = {}) => {
    clearReconnectTimer()
    clearRecoveredStateTimer()
    streamConnected.value = true
    streamError.value = ''
    reconnectAttempt.value = 0

    if (recovered) {
      streamState.value = 'recovered'
      recoveredStateTimer = window.setTimeout(() => {
        if (streamConnected.value && shouldKeepStreaming() && streamState.value === 'recovered') {
          streamState.value = 'connected'
        }
      }, RECOVERED_STATE_HOLD_MS)
      return
    }

    streamState.value = 'connected'
  }

  const scheduleReconnect = (err = null) => {
    streamHandle = null
    streamConnected.value = false
    clearRecoveredStateTimer()

    if (!canReconnect(err)) {
      streamState.value = err ? 'failed' : 'idle'
      streamError.value = err ? resolveStreamFailureMessage(err) : ''
      return
    }

    clearReconnectTimer()
    streamError.value = ''
    streamState.value = 'reconnecting'
    reconnectAttempt.value += 1

    const retryDelay = Math.min(
      1000 * (2 ** Math.min(reconnectAttempt.value - 1, 3)),
      MAX_RECONNECT_DELAY_MS
    )
    const expectedRequestId = streamRequestId

    reconnectTimer = window.setTimeout(async () => {
      if (expectedRequestId !== streamRequestId) {
        return
      }

      try {
        const payload = await fetchDocument({ syncEditor: false })
        if (expectedRequestId !== streamRequestId) {
          return
        }
        if (!STREAMABLE_STATUSES.has(payload?.status)) {
          resetConnectionState()
          return
        }
        startStream({ isReconnect: true })
      } catch (reconnectError) {
        if (expectedRequestId !== streamRequestId) {
          return
        }
        scheduleReconnect(reconnectError)
      }
    }, retryDelay)
  }

  const startStream = ({ isReconnect = false } = {}) => {
    stopStream({ resetState: false })
    if (!currentDocumentId.value) return

    clearReconnectTimer()
    clearRecoveredStateTimer()
    streamConnected.value = false
    streamError.value = ''
    streamState.value = isReconnect ? 'reconnecting' : 'connecting'

    const requestId = ++streamRequestId
    let activated = false

    const activateStream = () => {
      if (requestId !== streamRequestId) {
        return
      }
      if (isReconnect && !activated) {
        markStreamActive({ recovered: true })
      } else {
        markStreamActive()
      }
      activated = true
    }

    streamHandle = connectInteractiveDocumentStream(currentDocumentId.value, {
      snapshot: (payload) => {
        if (requestId !== streamRequestId) return
        activateStream()
        applyDocument(payload, { syncEditor: false })
      },
      status: (payload) => {
        if (requestId !== streamRequestId) return
        activateStream()
        applyDocument(payload, { syncEditor: false })
      },
      content_delta: (payload) => {
        if (requestId !== streamRequestId) return
        activateStream()
        const delta = payload?.delta || ''
        if (!delta) return
        streamingText.value += delta
      },
      completed: (payload) => {
        if (requestId !== streamRequestId) return
        activateStream()
        applyDocument(payload, { syncEditor: true })
      },
      failed: (payload) => {
        if (requestId !== streamRequestId) return
        applyDocument(payload, { syncEditor: false })
      },
      error: (err) => {
        if (requestId !== streamRequestId) return
        scheduleReconnect(err)
      },
      close: () => {
        if (requestId !== streamRequestId) return
        scheduleReconnect()
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

    try {
      const payload = await fetchDocument({ syncEditor })
      streamingText.value = payload?.htmlContent || ''
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
    streamState.value = 'idle'
  }

  const saveDocument = async () => {
    if (!currentDocumentId.value) return null

    saving.value = true
    error.value = ''
    try {
      const res = await updateInteractiveDocumentContent(currentDocumentId.value, draftContent.value)
      applyDocument(res?.data || null, { syncEditor: true })
      return res?.data || null
    } finally {
      saving.value = false
    }
  }

  const exportDocument = async () => {
    if (!currentDocumentId.value) return null

    exporting.value = true
    error.value = ''
    try {
      const res = await exportInteractiveDocument(currentDocumentId.value)
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
    error,
    streamConnected,
    streamError,
    streamState,
    isDirty,
    isEditable,
    isStreamable,
    openWorkspace,
    closeWorkspace,
    resetWorkspace,
    saveDocument,
    exportDocument,
    stopStream,
    fetchDocument
  }
}
