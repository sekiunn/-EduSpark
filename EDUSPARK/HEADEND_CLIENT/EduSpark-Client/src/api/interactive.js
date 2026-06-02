import { buildResponseError, get, post, put } from './request.js'
import { API_ENDPOINTS, BASE_URL } from './config.js'

export function getInteractiveDocument(documentId) {
  return get(`${API_ENDPOINTS.INTERACTIVE_DOCUMENTS}/${documentId}`)
}

export function updateInteractiveDocumentContent(documentId, content) {
  return put(`${API_ENDPOINTS.INTERACTIVE_DOCUMENTS}/${documentId}/content`, {
    content
  })
}

export function exportInteractiveDocument(documentId) {
  return post(`${API_ENDPOINTS.INTERACTIVE_DOCUMENTS}/${documentId}/export`)
}

export function connectInteractiveDocumentStream(documentId, callbacks = {}) {
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

  const createStreamError = (message, extra = {}) => {
    const error = new Error(message)
    Object.assign(error, extra)
    return error
  }

  fetch(`${BASE_URL}${API_ENDPOINTS.INTERACTIVE_DOCUMENTS}/${documentId}/stream`, {
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

      if (typeof callbacks.open === 'function') {
        callbacks.open()
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = 'message'
      let currentData = []

      const dispatchEvent = () => {
        if (!currentData.length) {
          currentEvent = 'message'
          return
        }

        const payload = parsePayload(currentData.join('\n'))
        const handler = callbacks[currentEvent] || callbacks.message
        if (typeof handler === 'function') {
          handler(payload)
        }
        currentEvent = 'message'
        currentData = []
      }

      const processLine = (rawLine) => {
        const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
        if (line === '') {
          dispatchEvent()
          return
        }
        if (line.startsWith(':')) {
          return
        }

        const separatorIndex = line.indexOf(':')
        const field = separatorIndex === -1 ? line : line.slice(0, separatorIndex)
        let value = separatorIndex === -1 ? '' : line.slice(separatorIndex + 1)
        if (value.startsWith(' ')) {
          value = value.slice(1)
        }

        if (field === 'event') {
          currentEvent = value || 'message'
          return
        }

        if (field === 'data') {
          currentData.push(value)
        }
      }

      while (true) {
        const { done, value } = await reader.read()
        if (value) {
          buffer += decoder.decode(value, { stream: !done })
        }

        let lineBreakIndex = buffer.indexOf('\n')
        while (lineBreakIndex !== -1) {
          processLine(buffer.slice(0, lineBreakIndex))
          buffer = buffer.slice(lineBreakIndex + 1)
          lineBreakIndex = buffer.indexOf('\n')
        }

        if (done) {
          if (buffer) {
            processLine(buffer)
            buffer = ''
          }
          dispatchEvent()
          if (typeof callbacks.close === 'function') {
            callbacks.close()
          }
          break
        }
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
