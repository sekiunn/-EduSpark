/**
 * 对话相关 API
 */
import { buildResponseError, createRequestError, del, get, post, postForm, put } from './request.js'
import { API_ENDPOINTS, BASE_URL } from './config.js'

/**
 * 发送对话消息
 * @param {string} message - 消息内容
 * @param {boolean} webSearchEnabled - 是否开启联网搜索
 * @param {number} sessionId - 会话ID（可选，不传则创建新会话）
 * @param {string} mode - 教学模式（ppt/lesson_plan/interactive）
 * @param {string} action - 动作（confirm/supplement）
 * @returns {Promise} 对话响应
 */
export function sendMessage(message, webSearchEnabled = false, sessionId = null, mode = null, action = null, templateId = null) {
  return post(API_ENDPOINTS.CHAT, {
    message,
    webSearchEnabled,
    sessionId,
    mode,
    action,
    templateId
  })
}

/**
 * 发送带附件的对话消息
 * @param {string} message - 消息内容
 * @param {boolean} webSearchEnabled - 是否开启联网搜索
 * @param {File[]} attachments - 附件文件列表
 * @param {number} sessionId - 会话ID（可选，用于关联到教学模式）
 * @param {string} mode - 教学模式（ppt/lesson_plan/interactive，可选）
 * @returns {Promise} 对话响应
 */
export function sendMessageWithFiles(
  message,
  webSearchEnabled = false,
  attachments = [],
  sessionId = null,
  mode = null,
  templateId = null,
  action = null
) {
  const formData = new FormData()
  formData.append('message', message)
  formData.append('webSearchEnabled', String(webSearchEnabled))
  if (sessionId != null) {
    formData.append('sessionId', String(sessionId))
  }
  if (mode != null) {
    formData.append('mode', mode)
  }
  if (templateId != null) {
    formData.append('templateId', templateId)
  }
  if (action != null) {
    formData.append('action', action)
  }
  attachments.forEach(file => {
    formData.append('attachments', file)
  })

  return postForm(API_ENDPOINTS.CHAT_WITH_FILES, formData)
}

/**
 * 流式发送对话消息（SSE）
 * @param {string} message - 消息内容
 * @param {boolean} webSearchEnabled - 是否开启联网搜索
 * @param {string} sessionId - 会话ID（可选）
 * @param {Object} callbacks - 回调函数
 * @param {Function} callbacks.onChunk - 每个文本块的回调
 * @param {Function} callbacks.onDone - 完成时的回调
 * @param {Function} callbacks.onError - 错误时的回调
 * @param {Function} callbacks.onSessionId - 新会话ID回调
 * @param {number|null} referencedFileId - 引用文档ID
 * @returns {void}
 */
export function sendMessageStream(message, webSearchEnabled = false, sessionId = null, callbacks = {}, referencedFileId = null) {
  const { onChunk, onDone, onError, onSessionId } = callbacks

  const token = localStorage.getItem('eduspark_token')
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { 'Authorization': `Bearer ${token}` } : {})
  }

  const body = JSON.stringify({
    message,
    webSearchEnabled,
    sessionId,
    referencedFileId
  })

  fetch(BASE_URL + API_ENDPOINTS.CHAT_STREAM, {
    method: 'POST',
    headers,
    body
  }).then(async response => {
    if (!response.ok) {
      throw await buildResponseError(response)
    }

    if (!response.body) {
      throw createRequestError('Empty stream body')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    // 用于跟踪当前事件名称
    let currentEventName = null

    function processLine(line) {
      const trimmed = line.trim()
      if (!trimmed) return

      if (trimmed.startsWith('event:')) {
        currentEventName = trimmed.slice(6).trim()
      } else if (trimmed.startsWith('data')) {
        // 处理 "data: xxx" 或 "data xxx" 格式
        let data = trimmed.startsWith('data:') ? trimmed.slice(5) : trimmed.slice(4)
        data = data.trim()
        
        // 尝试解析 JSON 编码的内容（处理多行文本）
        try {
          const parsed = JSON.parse(data)
          if (typeof parsed === 'string') {
            data = parsed
          }
        } catch (e) {
          // 如果不是 JSON，保持原样
        }
        
        if (currentEventName === 'chunk' && onChunk) {
          onChunk(data)
        } else if (currentEventName === 'done' && onDone) {
          onDone(data)
        } else if (currentEventName === 'error' && onError) {
          onError(new Error(data))
        } else if (currentEventName === 'sessionId' && onSessionId) {
          onSessionId(data)
        }
        currentEventName = null
      }
    }

    function read() {
      reader.read().then(({ done, value }) => {
        if (done) {
          if (buffer.length > 0) {
            processLine(buffer)
          }
          return
        }

        buffer += decoder.decode(value, { stream: true })
        // 处理完整的行
        const lines = buffer.split('\n')
        for (let i = 0; i < lines.length - 1; i++) {
          processLine(lines[i].trim())
        }
        buffer = lines[lines.length - 1]
        read()
      }).catch(err => {
        if (onError) onError(err)
      })
    }

    read()
  }).catch(err => {
    if (onError) onError(err)
  })
}

function processBuffer(buffer, onChunk, onLeftover) {
  // 逐行处理 SSE 数据
  const lines = buffer.split('\n')
  for (let i = 0; i < lines.length - 1; i++) {
    const line = lines[i].trim()
    if (line.startsWith('data: ')) {
      // data: 后面是实际的数据内容（可能是字符串或JSON）
      const rawData = line.slice(6)
      // 尝试解析为JSON，如果是字符串就直接使用
      let data = rawData
      try {
        const parsed = JSON.parse(rawData)
        // 如果解析成功且有data字段，使用data字段
        if (parsed && parsed.data !== undefined) {
          data = parsed.data
        } else if (parsed && typeof parsed === 'string') {
          data = parsed
        }
      } catch (e) {
        // 不是JSON，直接使用原始字符串
      }
      if (onChunk && typeof data === 'string') {
        onChunk(data)
      }
    }
  }
  // 保留最后一行（可能不完整）
  if (onLeftover) {
    onLeftover(lines[lines.length - 1])
  }
}

/**
 * 测试意图识别
 * @param {string} query - 查询内容
 * @returns {Promise} 意图识别结果
 */
export function testIntent(query) {
  return get(API_ENDPOINTS.CHAT_INTENT, { query })
}

/**
 * 仅检索（不生成回答）
 * @param {string} message - 检索内容
 * @param {number} userId - 用户ID
 * @returns {Promise} 检索结果
 */
export function searchOnly(message, userId) {
  return get(API_ENDPOINTS.CHAT_SEARCH, { message, userId })
}

/**
 * 测试联网搜索
 * @param {string} query - 搜索内容
 * @returns {Promise} 搜索结果
 */
export function testWebSearch(query) {
  return get(API_ENDPOINTS.CHAT_WEB_SEARCH, { query })
}

/**
 * 发送语音消息并获取转文字结果
 * @param {File} audioFile - 音频文件
 * @param {string} sessionId - 会话ID
 * @returns {Promise} 转文字结果
 */
export function voiceToText(audioFile) {
  const formData = new FormData()
  formData.append('file', audioFile, 'recording.webm')

  return fetch(BASE_URL + API_ENDPOINTS.VOICE_TRANSCRIBE, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('eduspark_token') || ''}`
    },
    body: formData
  }).then(res => res.json())
}

// 对话消息数据结构
export const MessageRole = {
  USER: 'user',
  AI: 'ai',
  SYSTEM: 'system'
}

// 创建用户消息
export function createUserMessage(content) {
  return {
    id: Date.now(),
    role: MessageRole.USER,
    content
  }
}

// 创建AI消息
export function createAiMessage(content, extra = {}) {
  return {
    id: Date.now(),
    role: MessageRole.AI,
    content,
    ...extra
  }
}

/**
 * 获取会话列表
 * @returns {Promise} 会话列表
 */
export function getSessionList() {
  return get(API_ENDPOINTS.CHAT_SESSIONS)
}

/**
 * 轮询生成状态
 * @param {number} sessionId - 会话ID
 * @returns {Promise}
 */
export function getGenerationStatus(sessionId) {
  return get(`${API_ENDPOINTS.CHAT_SESSIONS}/${sessionId}/generation-status`)
}

/**
 * 获取会话详情（含消息）
 * @param {number} sessionId - 会话ID
 * @returns {Promise} 会话详情
 */
export function getSessionDetail(sessionId) {
  return get(`${API_ENDPOINTS.CHAT_SESSIONS}/${sessionId}`)
}

/**
 * 删除会话
 * @param {number} sessionId - 会话ID
 * @returns {Promise}
 */
export function deleteSession(sessionId) {
  return del(`${API_ENDPOINTS.CHAT_SESSIONS}/${sessionId}`)
}

export function updateSessionTitle(sessionId, title) {
  return put(`${API_ENDPOINTS.CHAT_SESSIONS}/${sessionId}/title`, { title })
}

export function exitSessionMode(sessionId) {
  return post(`${API_ENDPOINTS.CHAT_SESSIONS}/${sessionId}/exit-mode`)
}
