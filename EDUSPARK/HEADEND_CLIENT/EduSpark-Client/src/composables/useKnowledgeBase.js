import { computed, ref } from 'vue'
import {
  createWorkspace as createWorkspaceApi,
  deleteFile as deleteFileApi,
  deleteWorkspace as deleteWorkspaceApi,
  FileStatus,
  getFileDetail,
  getFileList,
  getFileStatusText,
  getFileTypeInfo,
  listWorkspaces,
  reprocessFile,
  updateWorkspace as updateWorkspaceApi,
  uploadFile
} from '@/api'

const DEFAULT_CATEGORY = '课件'
const CATEGORY_OPTIONS = ['课件', '教案', '习题', '参考资料']

export function useKnowledgeBase(userId, { onQuoteDoc } = {}) {
  const knowledgeDocs = ref([])
  const knowledgeLoading = ref(false)
  const selectedDocId = ref(null)
  const searchKeyword = ref('')
  const currentCategory = ref('all')
  const viewMode = ref('grid')
  const referencedFileId = ref(null)

  const workspaces = ref([])
  const selectedWorkspaceId = ref(null)
  const workspacesLoading = ref(false)

  const showCategoryModal = ref(false)
  const pendingUploadFiles = ref([])
  const selectedCategory = ref(DEFAULT_CATEGORY)
  const categoryOptions = CATEGORY_OPTIONS
  const fileInputRef = ref(null)
  const folderInputRef = ref(null)

  const isUploading = ref(false)
  const uploadProgress = ref(0)
  const uploadQueue = ref([])

  const pollingTimers = new Map()
  const activeUploadCount = ref(0)

  const currentUserId = () => (typeof userId === 'object' ? userId.value : userId)

  const formatDate = (dateStr) => {
    if (!dateStr) {
      return '未知'
    }

    const date = new Date(dateStr)
    const now = new Date()
    const diff = now - date

    if (diff < 60 * 1000) {
      return '刚刚'
    }

    if (diff < 60 * 60 * 1000) {
      return `${Math.floor(diff / (60 * 1000))}分钟前`
    }

    if (diff < 24 * 60 * 60 * 1000) {
      return '今天'
    }

    if (diff < 48 * 60 * 60 * 1000) {
      return '昨天'
    }

    if (diff < 7 * 24 * 60 * 60 * 1000) {
      return `${Math.floor(diff / (24 * 60 * 60 * 1000))}天前`
    }

    return date.toLocaleDateString('zh-CN')
  }

  const formatFileSize = (bytes) => {
    if (!bytes) {
      return '0 B'
    }

    if (bytes < 1024) {
      return `${bytes} B`
    }

    if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(1)} KB`
    }

    if (bytes < 1024 * 1024 * 1024) {
      return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
    }

    return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`
  }

  const normalizeDoc = (file) => {
    const fileTypeInfo = getFileTypeInfo(file.fileType)

    return {
      id: file.fileId,
      name: file.fileName,
      fileType: file.fileType,
      fileTypeLabel: fileTypeInfo.label,
      fileIcon: fileTypeInfo.icon,
      category: file.category || DEFAULT_CATEGORY,
      tags: file.category ? [file.category] : [],
      date: file.createTime ? formatDate(file.createTime) : '未知',
      createTime: file.createTime || '',
      updateTime: file.updateTime || '',
      preview: file.description || '',
      size: file.fileSizeText || formatFileSize(file.fileSize),
      status: file.status,
      statusText: file.statusText || getFileStatusText(file.status),
      errorMessage: file.errorMessage || '',
      chunkCount: file.chunkCount || 0,
      workspaceId: file.workspaceId !== undefined && file.workspaceId !== null
        ? String(file.workspaceId)
        : null
    }
  }

  const ensureSelectedDoc = () => {
    if (!knowledgeDocs.value.length) {
      selectedDocId.value = null
      return
    }

    const exists = knowledgeDocs.value.some((doc) => String(doc.id) === String(selectedDocId.value))
    if (!exists) {
      selectedDocId.value = knowledgeDocs.value[0].id
    }
  }

  const pushQueueItem = (item) => {
    uploadQueue.value.unshift(item)
    if (uploadQueue.value.length > 8) {
      uploadQueue.value = uploadQueue.value.slice(0, 8)
    }
  }

  const updateQueueItem = (queueId, patch) => {
    const target = uploadQueue.value.find((item) => item.id === queueId)
    if (target) {
      Object.assign(target, patch)
    }
  }

  const updateDocStatus = (fileId, detail) => {
    const index = knowledgeDocs.value.findIndex((doc) => String(doc.id) === String(fileId))
    if (index === -1) {
      return
    }

    knowledgeDocs.value[index] = {
      ...knowledgeDocs.value[index],
      status: detail.status,
      statusText: detail.statusText || getFileStatusText(detail.status),
      errorMessage: detail.errorMessage || '',
      chunkCount: detail.chunkCount || 0,
      size: detail.fileSizeText || knowledgeDocs.value[index].size,
      preview: detail.description || knowledgeDocs.value[index].preview
    }
  }

  const stopFileStatusPolling = (fileId) => {
    const timer = pollingTimers.get(String(fileId))
    if (!timer) {
      return
    }

    clearInterval(timer)
    pollingTimers.delete(String(fileId))
  }

  const startFileStatusPolling = (fileId, queueId = null) => {
    const userIdValue = currentUserId()
    if (!userIdValue || !fileId) {
      return
    }

    stopFileStatusPolling(fileId)

    const timer = setInterval(async () => {
      try {
        const res = await getFileDetail(fileId, userIdValue)
        if (res.code !== 200 || !res.data) {
          return
        }

        updateDocStatus(fileId, res.data)

        if (queueId) {
          if (res.data.status === FileStatus.PROCESSING) {
            updateQueueItem(queueId, {
              stage: 'processing',
              progress: 100,
              statusText: '正在解析处理中',
              chunkCount: res.data.chunkCount || 0
            })
          }

          if (res.data.status === FileStatus.SUCCESS) {
            updateQueueItem(queueId, {
              stage: 'ready',
              progress: 100,
              statusText: '文件已就绪',
              chunkCount: res.data.chunkCount || 0
            })
          }

          if (res.data.status === FileStatus.FAILED) {
            updateQueueItem(queueId, {
              stage: 'failed',
              progress: 100,
              statusText: '处理失败',
              chunkCount: res.data.chunkCount || 0,
              errorMessage: res.data.errorMessage || '处理失败'
            })
          }
        }

        if (res.data.status === FileStatus.SUCCESS || res.data.status === FileStatus.FAILED) {
          stopFileStatusPolling(fileId)
        }
      } catch (error) {
        console.error('轮询知识库文件状态失败:', error)
      }
    }, 3000)

    pollingTimers.set(String(fileId), timer)
  }

  const filteredDocs = computed(() => {
    const keyword = searchKeyword.value.trim().toLowerCase()
    const wsId = selectedWorkspaceId.value
    const wsKey = wsId !== null && wsId !== undefined ? String(wsId) : null

    return knowledgeDocs.value.filter((doc) => {
      if (wsKey === 'ungrouped') {
        if (doc.workspaceId) return false
      } else if (wsKey !== null) {
        if (doc.workspaceId !== wsKey) return false
      }

      if (currentCategory.value !== 'all' && doc.category !== currentCategory.value) {
        return false
      }

      if (!keyword) {
        return true
      }

      return [
        doc.name,
        doc.category,
        doc.fileTypeLabel,
        doc.preview,
        ...(doc.tags || [])
      ].some((value) => String(value || '').toLowerCase().includes(keyword))
    })
  })

  const triggerFileUpload = () => {
    fileInputRef.value?.click()
  }

  const triggerFolderImport = () => {
    folderInputRef.value?.click()
  }

  const setCategory = (category) => {
    currentCategory.value = category
  }

  const setViewMode = (mode) => {
    viewMode.value = mode
  }

  const selectDoc = (doc) => {
    selectedDocId.value = typeof doc === 'object' ? doc?.id : doc
  }

  const quoteDoc = (doc) => {
    referencedFileId.value = doc.id
    onQuoteDoc?.(doc)
  }

  const loadKnowledgeFiles = async () => {
    const userIdValue = currentUserId()
    if (!userIdValue) {
      knowledgeDocs.value = []
      selectedDocId.value = null
      return
    }

    knowledgeLoading.value = true

    try {
      const result = await getFileList(userIdValue, { page: 1, size: 100 })
      const payload = result.data || {}
      const records = payload.list || payload.records || []

      knowledgeDocs.value = records.map(normalizeDoc)
      ensureSelectedDoc()

      knowledgeDocs.value
        .filter((doc) => doc.status === FileStatus.PROCESSING)
        .forEach((doc) => startFileStatusPolling(doc.id))
    } catch (error) {
      console.error('加载知识库文件失败:', error)
    } finally {
      knowledgeLoading.value = false
    }
  }

  const updateUploadFlags = () => {
    activeUploadCount.value = Math.max(activeUploadCount.value, 0)
    isUploading.value = activeUploadCount.value > 0
    if (!isUploading.value) {
      uploadProgress.value = 0
    }
  }

  const uploadSingleFile = async (file, category = DEFAULT_CATEGORY) => {
    const userIdValue = currentUserId()
    if (!userIdValue) {
      alert('请先登录')
      return
    }

    const queueId = `${Date.now()}-${Math.random().toString(36).slice(2)}`

    pushQueueItem({
      id: queueId,
      fileId: null,
      name: file.name,
      category,
      progress: 0,
      stage: 'uploading',
      statusText: '上传中',
      errorMessage: '',
      chunkCount: 0
    })

    activeUploadCount.value += 1
    isUploading.value = true
    uploadProgress.value = 0

    try {
      const wsId = selectedWorkspaceId.value
      const wsParam = wsId !== null && wsId !== undefined && wsId !== 'ungrouped' ? wsId : null
      const result = await uploadFile(file, userIdValue, category, '', (percent) => {
        uploadProgress.value = percent
        updateQueueItem(queueId, {
          progress: percent,
          stage: 'uploading',
          statusText: '上传中'
        })
      }, wsParam)

      const newDoc = normalizeDoc({ ...result, category })
      knowledgeDocs.value.unshift(newDoc)
      ensureSelectedDoc()

      updateQueueItem(queueId, {
        fileId: result.fileId,
        progress: 100,
        stage: result.status === FileStatus.SUCCESS ? 'ready' : result.status === FileStatus.FAILED ? 'failed' : 'processing',
        statusText: result.status === FileStatus.SUCCESS ? '文件已就绪' : result.status === FileStatus.FAILED ? '处理失败' : '正在解析处理中',
        errorMessage: result.errorMessage || ''
      })

      if (result.status === FileStatus.PROCESSING) {
        startFileStatusPolling(result.fileId, queueId)
      }
    } catch (error) {
      console.error('上传知识库文件失败:', error)
      updateQueueItem(queueId, {
        stage: 'failed',
        progress: 100,
        statusText: '上传失败',
        errorMessage: error.message || '上传失败'
      })
      alert(`文件“${file.name}”上传失败：${error.message}`)
    } finally {
      activeUploadCount.value -= 1
      updateUploadFlags()
    }
  }

  const handleDeleteFile = async (doc) => {
    const userIdValue = currentUserId()
    if (!userIdValue) {
      return
    }

    if (!confirm(`确定要删除文件“${doc.name}”吗？`)) {
      return
    }

    try {
      await deleteFileApi(doc.id, userIdValue)
      stopFileStatusPolling(doc.id)
      knowledgeDocs.value = knowledgeDocs.value.filter((item) => String(item.id) !== String(doc.id))
      uploadQueue.value = uploadQueue.value.filter((item) => String(item.fileId) !== String(doc.id))
      ensureSelectedDoc()
    } catch (error) {
      console.error('删除知识库文件失败:', error)
      alert(`删除失败：${error.message}`)
    }
  }

  const handleRetryFile = async (doc) => {
    const userIdValue = currentUserId()
    if (!userIdValue) {
      return
    }

    if (!confirm(`确定要重新处理文件“${doc.name}”吗？`)) {
      return
    }

    try {
      await reprocessFile(doc.id, userIdValue)

      updateDocStatus(doc.id, {
        status: FileStatus.PROCESSING,
        statusText: '处理中',
        errorMessage: '',
        chunkCount: 0
      })

      const queueId = `${Date.now()}-${Math.random().toString(36).slice(2)}`
      pushQueueItem({
        id: queueId,
        fileId: doc.id,
        name: doc.name,
        category: doc.category,
        progress: 100,
        stage: 'processing',
        statusText: '重新处理中',
        errorMessage: '',
        chunkCount: 0
      })

      startFileStatusPolling(doc.id, queueId)
    } catch (error) {
      console.error('重新处理知识库文件失败:', error)
      alert(`重新处理失败：${error.message}`)
    }
  }

  const cleanup = () => {
    pollingTimers.forEach((timer) => clearInterval(timer))
    pollingTimers.clear()
  }

  const normalizeWorkspace = (ws) => ({
    id: ws.id !== undefined && ws.id !== null ? String(ws.id) : null,
    name: ws.name || '',
    description: ws.description || '',
    coverColor: ws.coverColor || '',
    sort: ws.sort ?? 0,
    fileCount: ws.fileCount ?? 0,
    createTime: ws.createTime || '',
    updateTime: ws.updateTime || ''
  })

  const loadWorkspaces = async () => {
    const userIdValue = currentUserId()
    if (!userIdValue) {
      workspaces.value = []
      return
    }
    workspacesLoading.value = true
    try {
      const res = await listWorkspaces(userIdValue)
      const list = res?.data || []
      workspaces.value = list.map(normalizeWorkspace)
    } catch (error) {
      console.error('加载课程空间失败:', error)
    } finally {
      workspacesLoading.value = false
    }
  }

  const setSelectedWorkspace = (id) => {
    if (id === null || id === undefined || id === '' || id === 'all') {
      selectedWorkspaceId.value = null
    } else {
      selectedWorkspaceId.value = String(id)
    }
  }

  const createWorkspace = async ({ name, description = '', coverColor = '' } = {}) => {
    const userIdValue = currentUserId()
    if (!userIdValue) {
      alert('请先登录')
      return null
    }
    if (!name || !name.trim()) {
      alert('请输入课程名称')
      return null
    }
    try {
      const res = await createWorkspaceApi({
        userId: userIdValue,
        name: name.trim(),
        description,
        coverColor
      })
      const created = normalizeWorkspace(res?.data || {})
      workspaces.value = [...workspaces.value, created].sort((a, b) => (a.sort - b.sort) || (a.id > b.id ? 1 : -1))
      return created
    } catch (error) {
      console.error('创建课程空间失败:', error)
      alert(`创建课程空间失败：${error.message}`)
      return null
    }
  }

  const renameWorkspace = async (workspaceId, { name, description = '', coverColor = '' } = {}) => {
    const userIdValue = currentUserId()
    if (!userIdValue) return null
    if (!name || !name.trim()) return null
    try {
      const res = await updateWorkspaceApi(workspaceId, {
        userId: userIdValue,
        name: name.trim(),
        description,
        coverColor
      })
      const updated = normalizeWorkspace(res?.data || {})
      workspaces.value = workspaces.value.map((w) => (w.id === String(workspaceId) ? updated : w))
      return updated
    } catch (error) {
      console.error('更新课程空间失败:', error)
      alert(`更新课程空间失败：${error.message}`)
      return null
    }
  }

  const removeWorkspace = async (workspaceId) => {
    const userIdValue = currentUserId()
    if (!userIdValue) return
    if (!confirm('删除课程空间不会删除已上传的文件，文件将变为未归类。是否继续？')) {
      return
    }
    try {
      await deleteWorkspaceApi(workspaceId, userIdValue)
      workspaces.value = workspaces.value.filter((w) => w.id !== String(workspaceId))
      if (String(selectedWorkspaceId.value) === String(workspaceId)) {
        selectedWorkspaceId.value = null
      }
      knowledgeDocs.value = knowledgeDocs.value.map((doc) =>
        doc.workspaceId === String(workspaceId) ? { ...doc, workspaceId: null } : doc
      )
    } catch (error) {
      console.error('删除课程空间失败:', error)
      alert(`删除课程空间失败：${error.message}`)
    }
  }

  const workspaceCounts = computed(() => {
    const counts = { ungrouped: 0, total: knowledgeDocs.value.length }
    for (const doc of knowledgeDocs.value) {
      if (!doc.workspaceId) {
        counts.ungrouped += 1
      } else {
        counts[doc.workspaceId] = (counts[doc.workspaceId] || 0) + 1
      }
    }
    return counts
  })

  const workspaceList = computed(() =>
    workspaces.value.map((w) => ({
      ...w,
      fileCount: workspaceCounts.value[w.id] ?? w.fileCount ?? 0
    }))
  )

  return {
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
    workspaces: workspaceList,
    workspacesLoading,
    selectedWorkspaceId,
    workspaceCounts,
    loadWorkspaces,
    setSelectedWorkspace,
    createWorkspace,
    renameWorkspace,
    removeWorkspace,
    setCategory,
    setViewMode,
    selectDoc,
    quoteDoc,
    triggerFileUpload,
    triggerFolderImport,
    uploadSingleFile,
    loadKnowledgeFiles,
    startFileStatusPolling,
    handleDeleteFile,
    handleRetryFile,
    cleanup
  }
}
