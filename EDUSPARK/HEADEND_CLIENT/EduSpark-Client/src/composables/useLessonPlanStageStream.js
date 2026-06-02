import { connectLessonPlanDocumentStream } from '@/api'

/**
 * 教案"阶段卡片" SSE 重连管理。
 *
 * 教案模式的流式生成期间，后端通过 SSE 不断推送 status / completed / failed 等事件，
 * 前端要把这些 payload 同步到聊天消息里那个卡片上，断线时按 1.5s 节奏重连，
 * 终态（completed/failed）到达后停流并刷一遍消息和历史列表。
 *
 * 这块逻辑在 ChatHome 里以 5+ 个 let 变量 + 5 个函数纠缠在一起，抽出来后从外部看
 * 只需要 ensure(cardData) 启动 / stop() 停止两个动作。
 */

const LESSON_PLAN_TERMINAL_STATUSES = new Set(['completed', 'failed'])

export function normalizeLessonPlanStatus(status) {
  return (status || '').toLowerCase()
}

export function isLessonPlanTerminalStatus(status) {
  return LESSON_PLAN_TERMINAL_STATUSES.has(normalizeLessonPlanStatus(status))
}

/**
 * @param {Object} options
 * @param {Ref<string|null>} options.currentMode         当前教学模式 ref。非 lesson_plan 时拒绝连接
 * @param {Ref<string|null>} options.currentSessionId    当前会话 ref。无值时拒绝重连
 * @param {Ref<boolean>}     options.isWorkspaceVisible  教案工作区是否打开。终态后刷消息时透传
 * @param {Function}         options.onSyncCard          (payload) => void 把 SSE payload 同步到消息卡片
 * @param {Function}         options.refreshMessages     ({autoOpenLessonPlanWorkspace}) => Promise<void>
 * @param {Function}         options.loadHistoryList     () => Promise<void>
 * @returns {{ ensure: Function, stop: Function }}
 */
export function useLessonPlanStageStream({
  currentMode,
  currentSessionId,
  isWorkspaceVisible,
  onSyncCard,
  refreshMessages,
  loadHistoryList
}) {
  // 闭包内部的可变状态——不需要响应式，外部也不直接读
  let streamHandle = null
  let streamDocumentId = null
  let streamRetryTimer = null
  let streamStopped = true
  let refreshKey = ''

  function clearRetry() {
    if (streamRetryTimer) {
      clearTimeout(streamRetryTimer)
      streamRetryTimer = null
    }
  }

  function stop() {
    streamStopped = true
    clearRetry()
    if (streamHandle) {
      const handle = streamHandle
      streamHandle = null
      handle.close()
    }
    streamDocumentId = null
  }

  function scheduleReconnect(documentId) {
    clearRetry()
    if (streamStopped || !documentId) return
    streamRetryTimer = setTimeout(() => {
      if (streamStopped) return
      if (currentMode.value !== 'lesson_plan') return
      if (!currentSessionId.value) return
      if (streamDocumentId !== documentId) return
      connect(documentId)
    }, 1500)
  }

  async function refreshConversationAfterTerminal(payload) {
    if (!payload?.documentId || !currentSessionId.value) return
    const key = `${currentSessionId.value}:${payload.documentId}:${payload.status}`
    if (refreshKey === key) return
    refreshKey = key
    await refreshMessages({
      autoOpenLessonPlanWorkspace: !!isWorkspaceVisible?.value
    })
    if (typeof loadHistoryList === 'function') {
      await loadHistoryList()
    }
  }

  async function finalize(payload) {
    if (!payload?.documentId) return
    if (streamStopped || streamDocumentId !== payload.documentId) return
    onSyncCard(payload)
    stop()
    await refreshConversationAfterTerminal(payload)
  }

  function connect(documentId) {
    clearRetry()
    if (!documentId) return

    streamHandle = connectLessonPlanDocumentStream(documentId, {
      snapshot: (payload) => {
        if (streamStopped || streamDocumentId !== documentId) return
        if (payload?.documentId !== documentId) return
        onSyncCard(payload)
        if (isLessonPlanTerminalStatus(payload?.status)) {
          void finalize(payload)
        }
      },
      status: (payload) => {
        if (streamStopped || streamDocumentId !== documentId) return
        if (payload?.documentId !== documentId) return
        onSyncCard(payload)
        if (isLessonPlanTerminalStatus(payload?.status)) {
          void finalize(payload)
        }
      },
      completed: (payload) => {
        if (streamStopped || streamDocumentId !== documentId) return
        if (payload?.documentId !== documentId) return
        void finalize(payload)
      },
      failed: (payload) => {
        if (streamStopped || streamDocumentId !== documentId) return
        if (payload?.documentId !== documentId) return
        void finalize(payload)
      },
      error: (error) => {
        if (streamStopped || streamDocumentId !== documentId) return
        console.error('教案卡片流连接失败:', error)
        scheduleReconnect(documentId)
      },
      close: () => {
        if (streamStopped || streamDocumentId !== documentId) return
        scheduleReconnect(documentId)
      }
    })
  }

  /**
   * 智能"按需启动 / 停止"：调用方拿到一个 cardData 后丢给这个函数，
   * 它自己判断要不要建流、是否已经在跟同一个 documentId、终态时是否要停。
   */
  function ensure(cardData) {
    const documentId = cardData?.documentId
    if (currentMode.value !== 'lesson_plan' || !documentId) {
      stop()
      return
    }
    if (isLessonPlanTerminalStatus(cardData?.status)) {
      stop()
      return
    }
    if (streamHandle && streamDocumentId === documentId) {
      return
    }
    stop()
    streamStopped = false
    streamDocumentId = documentId
    connect(documentId)
  }

  return { ensure, stop }
}
