/**
 * 视图层各种"格式化"的纯函数：文件大小、时间相对量、文件类型名、文档图标。
 */

const FILE_TYPE_NAMES = {
  pdf: 'PDF',
  docx: 'Word',
  doc: 'Word',
  txt: '文本',
  md: 'Markdown',
  pptx: 'PPT',
  ppt: 'PPT',
  png: '图片',
  jpg: '图片',
  jpeg: '图片'
}

/** 文件扩展名 → 用户友好名字。 */
export function getFileTypeName(type) {
  return FILE_TYPE_NAMES[type?.toLowerCase()] || '文件'
}

/** 字节数 → 可读字符串（B/KB/MB）。 */
export function formatFileSize(bytes) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

/** 知识库文档类别 → 图标名（lucide）。 */
export function getDocIcon(type) {
  if (type === '课件') return 'presentation'
  if (type === '教案') return 'file-text'
  if (type === '习题') return 'clipboard'
  return 'file'
}

/**
 * 相对时间标签：刚刚 / N 分钟前 / 今天 / 昨天 / N 天前 / 完整日期。
 * 用于消息流和会话列表。
 */
export function formatDate(dateStr) {
  if (!dateStr) return '未知'
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return '今天'
  if (diff < 172800000) return '昨天'
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`

  return date.toLocaleDateString('zh-CN')
}

/**
 * 历史会话侧栏按时间分组用的标签：今天 / 昨天 / 近7天 / 近30天 / 更早。
 * 跟 formatDate 不同——这个用于"分组桶"语义。
 */
export function getTimeGroupLabel(dateStr) {
  if (!dateStr) return '更早'
  const now = new Date()
  const d = new Date(dateStr)
  const diffMs = now.getTime() - d.getTime()
  const diffDays = diffMs / (1000 * 60 * 60 * 24)
  if (diffDays < 1 && now.getDate() === d.getDate()) return '今天'
  if (diffDays < 2 && now.getDate() - d.getDate() <= 1) return '昨天'
  if (diffDays < 7) return '近7天'
  if (diffDays < 30) return '近30天'
  return '更早'
}
