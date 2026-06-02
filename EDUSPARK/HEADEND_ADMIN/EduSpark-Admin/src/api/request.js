import { BASE_URL } from './config.js'
import { clearAdminAuth, getAccessToken } from '@/stores/adminAuth.js'

function createHttpError(message, extra = {}) {
  const error = new Error(message)
  Object.assign(error, extra)
  return error
}

function redirectToLogin(errorMessage = '') {
  if (typeof window === 'undefined') {
    return
  }

  const searchParams = new URLSearchParams(window.location.search)
  if (window.location.pathname === '/login' && (!errorMessage || searchParams.get('error') === errorMessage)) {
    return
  }

  const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`
  const nextSearchParams = new URLSearchParams()
  nextSearchParams.set('redirect', redirect)

  if (errorMessage) {
    nextSearchParams.set('error', errorMessage)
  }

  window.location.replace(`/login?${nextSearchParams.toString()}`)
}

async function parsePayload(response) {
  const contentType = response.headers.get('content-type') || ''

  if (contentType.includes('application/json')) {
    try {
      return await response.json()
    } catch {
      return null
    }
  }

  try {
    return await response.text()
  } catch {
    return null
  }
}

async function request(url, options = {}) {
  const fullUrl = url.startsWith('http') ? url : `${BASE_URL}${url}`
  const token = getAccessToken()
  const headers = {
    ...(options.skipContentType ? {} : { 'Content-Type': 'application/json' }),
    ...(options.headers || {})
  }

  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(fullUrl, {
    ...options,
    headers
  })

  const payload = await parsePayload(response)
  const code = payload?.code ?? response.status

  if (response.status === 401 || code === 401) {
    clearAdminAuth()
    redirectToLogin(payload?.message || '登录状态已失效，请重新登录')
    throw createHttpError(payload?.message || '登录状态已失效，请重新登录', {
      status: 401,
      code,
      payload
    })
  }

  if (response.status === 403 || code === 403) {
    clearAdminAuth()
    redirectToLogin(payload?.message || '当前账号无管理端访问权限')
    throw createHttpError(payload?.message || '当前账号无管理端访问权限', {
      status: 403,
      code,
      payload
    })
  }

  if (!response.ok || code !== 200) {
    throw createHttpError(payload?.message || `HTTP ${response.status}`, {
      status: response.status,
      code,
      payload
    })
  }

  return payload
}

export function get(url, params = {}) {
  const queryString = new URLSearchParams(params).toString()
  return request(queryString ? `${url}?${queryString}` : url, { method: 'GET' })
}

export function post(url, data = {}) {
  return request(url, {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function put(url, data = {}) {
  return request(url, {
    method: 'PUT',
    body: JSON.stringify(data)
  })
}

export function patch(url, data = null) {
  return request(url, {
    method: 'PATCH',
    ...(data == null ? {} : { body: JSON.stringify(data) })
  })
}

export function del(url) {
  return request(url, { method: 'DELETE' })
}

export function postForm(url, formData) {
  return request(url, {
    method: 'POST',
    skipContentType: true,
    body: formData
  })
}
