import { triggerAuthRequired } from '@/composables/authState.js'

/**
 * 文件下载相关纯逻辑：
 *   - 把 /api/v1/courseware/download?path=... 这种相对路径补全成完整 URL
 *   - 用 fetch + Authorization 拉受保护的二进制流，触发浏览器另存
 */

/** 把 query 里的 path 重新编码，保证 URLSearchParams 处理后仍可访问。 */
export function normalizeCoursewareDownloadPath(path) {
  if (!path || !path.includes('/api/v1/courseware/download?')) return path

  const [pathname, query = ''] = path.split('?')
  if (!query) return path

  const params = new URLSearchParams(query)
  const rawPath = params.get('path')
  if (rawPath) {
    params.set('path', rawPath)
  }

  const format = params.get('format')
  if (format) {
    params.set('format', format)
  }

  return `${pathname}?${params.toString()}`
}

/**
 * 把后端返回的相对路径 / 绝对 URL 统一解析成"可直接访问"的 URL。
 * 兼容三种来源：
 *   1. 已经是 http(s)://...
 *   2. /api/... 开头（拼到 origin 上）
 *   3. /... 其他（拼到 VITE_API_BASE_URL 上）
 */
export function resolveDownloadUrl(path) {
  if (!path) return ''
  const normalizedPath = normalizeCoursewareDownloadPath(path)
  if (/^https?:\/\//.test(normalizedPath)) return normalizedPath

  const base = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
  const origin = base.replace(/\/api\/?$/, '')

  if (normalizedPath.startsWith('/api/')) {
    return `${origin}${normalizedPath}`
  }

  if (normalizedPath.startsWith('/')) {
    return `${base}${normalizedPath}`
  }

  return `${origin}/${normalizedPath}`
}

/** 从 Content-Disposition 响应头里抠出文件名，兼容 filename* UTF-8 编码。 */
export function extractDownloadFileName(contentDisposition = '') {
  if (!contentDisposition) return ''

  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1])
    } catch {
      return utf8Match[1]
    }
  }

  const plainMatch = contentDisposition.match(/filename="?([^\";]+)"?/i)
  return plainMatch?.[1] || ''
}

/** 把已经拿到的 Blob 触发浏览器"另存为"。 */
export function saveBlobAsFile(blob, fileName = 'download') {
  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
}

/**
 * 受保护文件下载——带 Bearer Token 拉二进制流然后触发浏览器另存。
 * 401 时调起登录弹窗（authState 的 triggerAuthRequired）。
 */
export async function downloadProtectedFile(path, fallbackFileName = 'download') {
  const resolvedUrl = resolveDownloadUrl(path)
  if (!resolvedUrl) {
    throw new Error('下载地址为空')
  }

  const token = localStorage.getItem('eduspark_token')
  const response = await fetch(resolvedUrl, {
    method: 'GET',
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })

  if (response.status === 401) {
    await triggerAuthRequired()
    throw new Error('请先登录')
  }

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  const blob = await response.blob()
  const fileName = extractDownloadFileName(response.headers.get('content-disposition') || '') || fallbackFileName
  saveBlobAsFile(blob, fileName)
}
