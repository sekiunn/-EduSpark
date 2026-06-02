import { del, get, post, put } from './request.js'
import { API_ENDPOINTS, BASE_URL } from './config.js'

export const FileStatus = {
  PROCESSING: 0,
  SUCCESS: 1,
  FAILED: 2
}

export const FileTypeMap = {
  pdf: { icon: 'pdf', label: 'PDF' },
  docx: { icon: 'word', label: 'Word' },
  doc: { icon: 'word', label: 'Word' },
  txt: { icon: 'text', label: '文本' },
  md: { icon: 'text', label: 'Markdown' },
  pptx: { icon: 'ppt', label: 'PPT' },
  ppt: { icon: 'ppt', label: 'PPT' }
}

export function uploadFile(file, userId, category = '', description = '', onProgress = null, workspaceId = null) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('userId', userId)

  if (workspaceId !== null && workspaceId !== undefined && workspaceId !== '') {
    formData.append('workspaceId', workspaceId)
  }

  if (category) {
    formData.append('category', category)
  }

  if (description) {
    formData.append('description', description)
  }

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()

    xhr.upload.addEventListener('progress', (event) => {
      if (!event.lengthComputable || !onProgress) {
        return
      }

      const percent = Math.round((event.loaded / event.total) * 100)
      onProgress(percent)
    })

    xhr.addEventListener('load', async () => {
      let payload = null

      try {
        payload = JSON.parse(xhr.responseText || '{}')
      } catch {
        payload = null
      }

      if (xhr.status === 401 || payload?.code === 401) {
        const { triggerAuthRequired } = await import('../composables/authState.js')
        await triggerAuthRequired()
        reject(new Error(payload?.message || '请先登录'))
        return
      }

      if (xhr.status >= 200 && xhr.status < 300 && payload?.code === 200) {
        resolve(payload.data)
        return
      }

      reject(new Error(payload?.message || `上传失败（HTTP ${xhr.status}）`))
    })

    xhr.addEventListener('error', () => {
      reject(new Error('网络错误，上传失败'))
    })

    xhr.addEventListener('abort', () => {
      reject(new Error('上传已取消'))
    })

    const token = localStorage.getItem('eduspark_token')
    xhr.open('POST', BASE_URL + API_ENDPOINTS.KNOWLEDGE_UPLOAD)

    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    }

    xhr.send(formData)
  })
}

export function uploadFileAsync(file, userId, category = '', description = '') {
  return uploadFile(file, userId, category, description)
}

export function getFileList(userId, params = {}) {
  const queryParams = {
    userId,
    page: params.page || 1,
    size: params.size || 10
  }

  if (params.workspaceId !== undefined && params.workspaceId !== null && params.workspaceId !== '') {
    queryParams.workspaceId = params.workspaceId
  }

  if (params.status !== undefined && params.status !== null) {
    queryParams.status = params.status
  }

  if (params.fileType) {
    queryParams.fileType = params.fileType
  }

  if (params.keyword) {
    queryParams.keyword = params.keyword
  }

  return get(API_ENDPOINTS.KNOWLEDGE_FILES, queryParams)
}

export function getFileDetail(fileId, userId) {
  return get(`${API_ENDPOINTS.KNOWLEDGE_FILES}/${fileId}`, { userId })
}

export function getFilePreview(fileId, userId, chunkLimit = 6) {
  return get(`${API_ENDPOINTS.KNOWLEDGE_FILES}/${fileId}/preview`, { userId, chunkLimit })
}

export function getFileChunks(fileId, userId, limit = 20) {
  return get(`${API_ENDPOINTS.KNOWLEDGE_FILES}/${fileId}/chunks`, { userId, limit })
}

export function deleteFile(fileId, userId) {
  return del(`${API_ENDPOINTS.KNOWLEDGE_DELETE}/${fileId}?userId=${userId}`)
}

export function reprocessFile(fileId, userId) {
  return post(`${API_ENDPOINTS.KNOWLEDGE_FILES}/${fileId}/reprocess`, { userId })
}

export function searchKnowledge(query, userId, topK = 5, options = {}) {
  return post(API_ENDPOINTS.KNOWLEDGE_SEARCH, {
    query,
    userId,
    topK,
    vectorWeight: options.vectorWeight,
    bm25Weight: options.bm25Weight
  })
}

export function searchKnowledgeTest(query, userId, options = {}) {
  return post(API_ENDPOINTS.KNOWLEDGE_SEARCH_TEST, {
    query,
    userId,
    topK: options.topK || 5,
    maxTokens: options.maxTokens || 2000,
    vectorWeight: options.vectorWeight ?? 0.6,
    bm25Weight: options.bm25Weight ?? 0.4
  })
}

export function getFileUrl(fileId) {
  return get(API_ENDPOINTS.KNOWLEDGE_URL, { fileId })
}

export function listWorkspaces(userId) {
  return get(API_ENDPOINTS.KNOWLEDGE_WORKSPACES, { userId })
}

export function createWorkspace(payload) {
  return post(API_ENDPOINTS.KNOWLEDGE_WORKSPACES, payload)
}

export function updateWorkspace(workspaceId, payload) {
  return put(`${API_ENDPOINTS.KNOWLEDGE_WORKSPACES}/${workspaceId}`, payload)
}

export function deleteWorkspace(workspaceId, userId) {
  return del(`${API_ENDPOINTS.KNOWLEDGE_WORKSPACES}/${workspaceId}?userId=${userId}`)
}

export function checkOllamaHealth() {
  return get(API_ENDPOINTS.KNOWLEDGE_HEALTH)
}

export function checkStorageHealth() {
  return get(API_ENDPOINTS.UPLOAD_HEALTH)
}

export function getFileStatusText(status) {
  switch (status) {
    case FileStatus.PROCESSING:
      return '处理中'
    case FileStatus.SUCCESS:
      return '已就绪'
    case FileStatus.FAILED:
      return '处理失败'
    default:
      return '未知状态'
  }
}

export function getFileTypeInfo(type) {
  return FileTypeMap[type?.toLowerCase()] || { icon: 'file', label: '文件' }
}
