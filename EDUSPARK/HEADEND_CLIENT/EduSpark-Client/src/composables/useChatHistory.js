import { ref, computed } from 'vue'
import { getSessionList, updateSessionTitle, deleteSession } from '@/api'
import { getTimeGroupLabel } from '@/utils/format.js'

/**
 * 左侧历史会话列表 + "全部历史"面板 + 改名/删除二级弹窗。
 *
 * 不放进来的：switchToChat（涉及主 currentView / isTransitioning），由 ChatHome 管。
 *
 * @param {Object}   options
 * @param {Function} [options.onSessionDeleted]  删除会话成功后回调，参数是被删的 sessionId。
 *                                               ChatHome 在这里判断"如果删的就是当前会话则 startNewChat"。
 */
export function useChatHistory({ onSessionDeleted } = {}) {
  // 列表本身
  const historyList = ref([])

  // 历史抽屉面板（"全部历史"）
  const historyPanelOpen = ref(false)
  const historyPanelSearch = ref('')

  // 每行右上角的点点菜单（同时只展开一个）
  const historyMenuOpen = ref(null)

  // 改名 modal
  const editingTitle = ref(null)
  const editingTitleText = ref('')
  const editTitleModalVisible = ref(false)

  // 删除确认 modal
  const deleteConfirmModalVisible = ref(false)
  const sessionToDelete = ref(null)

  async function loadHistoryList() {
    try {
      const res = await getSessionList()
      if (res.code === 200) {
        historyList.value = (res.data || []).map(s => ({
          id: s.sessionId,
          sessionId: s.sessionId,
          title: s.title || '新会话',
          date: s.updateTime ? new Date(s.updateTime).toLocaleDateString() : '',
          lastMessage: s.lastMessage || '',
          messageCount: s.messageCount || 0,
          updateTime: s.updateTime || ''
        }))
      }
    } catch (error) {
      console.error('加载会话列表失败:', error)
    }
  }

  const filteredHistoryList = computed(() => {
    const keyword = historyPanelSearch.value.trim().toLowerCase()
    if (!keyword) return historyList.value
    return historyList.value.filter(item =>
      (item.title || '').toLowerCase().includes(keyword) ||
      (item.lastMessage || '').toLowerCase().includes(keyword)
    )
  })

  const groupedHistoryList = computed(() => {
    const groups = []
    const groupMap = new Map()
    const order = ['今天', '昨天', '近7天', '近30天', '更早']
    for (const item of filteredHistoryList.value) {
      const label = getTimeGroupLabel(item.updateTime)
      if (!groupMap.has(label)) {
        const group = { label, items: [] }
        groupMap.set(label, group)
        groups.push(group)
      }
      groupMap.get(label).items.push(item)
    }
    groups.sort((a, b) => order.indexOf(a.label) - order.indexOf(b.label))
    return groups
  })

  function toggleHistoryMenu(id) {
    historyMenuOpen.value = historyMenuOpen.value === id ? null : id
  }

  function handleEditTitle(session) {
    historyMenuOpen.value = null
    editingTitle.value = session
    editingTitleText.value = session.title
    editTitleModalVisible.value = true
  }

  async function confirmEditTitle() {
    if (editingTitleText.value && editingTitleText.value.trim()) {
      await updateSessionTitle(editingTitle.value.sessionId, editingTitleText.value.trim())
      editingTitle.value.title = editingTitleText.value.trim()
      await loadHistoryList()
    }
    editTitleModalVisible.value = false
    editingTitle.value = null
    editingTitleText.value = ''
  }

  function handleDeleteSession(session) {
    historyMenuOpen.value = null
    sessionToDelete.value = session
    deleteConfirmModalVisible.value = true
  }

  async function confirmDeleteSession() {
    if (!sessionToDelete.value) return
    const deletedId = sessionToDelete.value.sessionId
    try {
      await deleteSession(deletedId)
      await loadHistoryList()
      if (typeof onSessionDeleted === 'function') {
        await onSessionDeleted(deletedId)
      }
    } catch (error) {
      console.error('删除会话失败:', error)
    }
    deleteConfirmModalVisible.value = false
    sessionToDelete.value = null
  }

  return {
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
  }
}
