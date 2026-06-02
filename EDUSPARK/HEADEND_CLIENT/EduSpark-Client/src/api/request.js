import { BASE_URL } from './config.js'
import { triggerAuthRequired } from '../composables/authState.js'

export function createRequestError(message, extra = {}) {
  const error = new Error(message)
  Object.assign(error, extra)
  return error
}

export async function buildResponseError(response, fallbackMessage = `HTTP ${response.status}`) {
  const contentType = response.headers.get('content-type') || ''
  let payload = null
  let message = fallbackMessage

  if (contentType.includes('application/json')) {
    try {
      payload = await response.json()
    } catch {
      payload = null
    }
    if (payload?.message) {
      message = payload.message
    }
  } else {
    try {
      const text = await response.text()
      if (text) {
        message = text
      }
    } catch {
      // Ignore secondary parse failures and keep fallback message.
    }
  }

  const code = payload?.code ?? response.status
  if (response.status === 401 || code === 401) {
    await triggerAuthRequired()
  }

  return createRequestError(message, {
    status: response.status,
    code,
    payload
  })
}

async function request(url, options = {}) {
  const fullUrl = url.startsWith('http') ? url : BASE_URL + url

  const defaultOptions = {
    headers: {
      'Content-Type': 'application/json'
    }
  }

  const mergedOptions = {
    ...defaultOptions,
    ...options,
    headers: {
      ...defaultOptions.headers,
      ...options.headers
    }
  }

  if (options.skipContentType) {
    delete mergedOptions.headers['Content-Type']
    delete mergedOptions.skipContentType
  }

  const token = localStorage.getItem('eduspark_token')
  if (token) {
    mergedOptions.headers.Authorization = `Bearer ${token}`
  }

  try {
    const response = await fetch(fullUrl, mergedOptions)
    const contentType = response.headers.get('content-type') || ''

    if (contentType.includes('application/json')) {
      const data = await response.json()
      const code = data?.code ?? response.status

      if (response.status === 401 || code === 401) {
        await triggerAuthRequired()
        throw createRequestError(data?.message || '请先登录', {
          status: response.status || 401,
          code,
          payload: data
        })
      }

      if (!response.ok || code !== 200) {
        throw createRequestError(data?.message || `HTTP ${response.status}`, {
          status: response.status,
          code,
          payload: data
        })
      }

      return data
    }

    if (response.status === 401) {
      await triggerAuthRequired()
      throw createRequestError('请先登录', { status: 401, code: 401 })
    }

    if (!response.ok) {
      throw createRequestError(`HTTP ${response.status}`, {
        status: response.status,
        code: response.status
      })
    }

    return await response.text()
  } catch (error) {
    console.error('请求失败:', error)
    throw error
  }
}

export function get(url, params = {}) {
  const queryString = new URLSearchParams(params).toString()
  const fullUrl = queryString ? `${url}?${queryString}` : url
  return request(fullUrl, { method: 'GET' })
}

export function post(url, data = {}) {
  return request(url, {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function postForm(url, formData) {
  return request(url, {
    method: 'POST',
    skipContentType: true,
    body: formData
  })
}

export function put(url, data = {}) {
  return request(url, {
    method: 'PUT',
    body: JSON.stringify(data)
  })
}

export function del(url) {
  return request(url, { method: 'DELETE' })
}

export default {
  get,
  post,
  postForm,
  put,
  del
}
