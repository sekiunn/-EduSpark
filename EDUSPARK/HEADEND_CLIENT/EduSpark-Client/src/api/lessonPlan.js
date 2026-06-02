import { buildResponseError, createRequestError, get, post, put } from './request.js'
import { API_ENDPOINTS, BASE_URL } from './config.js'

export function getLessonPlanDocument(documentId) {
  return get(`${API_ENDPOINTS.LESSON_PLAN_DOCUMENTS}/${documentId}`)
}

export function updateLessonPlanDocumentContent(documentId, content) {
  return put(`${API_ENDPOINTS.LESSON_PLAN_DOCUMENTS}/${documentId}/content`, {
    content
  })
}

export function rewriteLessonPlanDocumentSelection(documentId, selectedText, instruction) {
  return post(`${API_ENDPOINTS.LESSON_PLAN_DOCUMENTS}/${documentId}/rewrite`, {
    selectedText,
    instruction
  })
}

export function exportLessonPlanDocument(documentId) {
  return post(`${API_ENDPOINTS.LESSON_PLAN_DOCUMENTS}/${documentId}/export`)
}

export function connectLessonPlanDocumentStream(documentId, callbacks = {}) {
  const controller = new AbortController()
  const token = localStorage.getItem('eduspark_token')
  const headers = {
    Accept: 'text/event-stream',
    ...(token ? { Authorization: `Bearer ${token}` } : {})
  }

  const parsePayload = (value) => {
    if (!value) return null
    try {
      return JSON.parse(value)
    } catch {
      return value
    }
  }

  const createStreamError = (message, extra = {}) => createRequestError(message, extra)

  fetch(`${BASE_URL}${API_ENDPOINTS.LESSON_PLAN_DOCUMENTS}/${documentId}/stream`, {
    method: 'GET',
    headers,
    signal: controller.signal
  })
    .then(async (response) => {
      if (!response.ok) {
        throw await buildResponseError(response)
      }

      if (!response.body) {
        throw createStreamError('Empty stream body', { status: response.status })
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = null

      const processLine = (line) => {
        const trimmed = line.trim()
        if (!trimmed) return

        if (trimmed.startsWith('event:')) {
          currentEvent = trimmed.slice(6).trim()
          return
        }

        if (!trimmed.startsWith('data:')) return

        const payload = parsePayload(trimmed.slice(5).trim())
        const handler = callbacks[currentEvent]
        if (typeof handler === 'function') {
          handler(payload)
        }
        currentEvent = null
      }

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          if (buffer) processLine(buffer)
          if (typeof callbacks.close === 'function') {
            callbacks.close()
          }
          break
        }

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        for (let i = 0; i < lines.length - 1; i++) {
          processLine(lines[i])
        }
        buffer = lines[lines.length - 1]
      }
    })
    .catch((error) => {
      if (controller.signal.aborted) {
        return
      }
      if (typeof callbacks.error === 'function') {
        callbacks.error(error)
      }
    })

  return {
    close() {
      controller.abort()
    }
  }
}
