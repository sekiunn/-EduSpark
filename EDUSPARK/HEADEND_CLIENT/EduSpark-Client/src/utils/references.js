/**
 * AI 回复里【参考文献】块的解析工具。
 * 纯函数，不依赖任何 Vue 响应式状态。
 */

/** 提取【参考文献】之后的整段原文（不含标题本身）。 */
export function extractReferences(text) {
  if (!text) return ''
  const refMatch = text.match(/【参考文献】([\s\S]*?)$/)
  if (!refMatch) return ''
  return refMatch[1].trim()
}

/** 文本里是否包含【参考文献】块。 */
export function hasReferences(text) {
  return /【参考文献】/.test(text)
}

/** 统计【参考文献】里 [n] 形式的引用数。 */
export function getRefCount(text) {
  const refSection = extractReferences(text)
  const matches = refSection.match(/\[\d+\]/g)
  return matches ? matches.length : 0
}

/**
 * 把【参考文献】块解析成 [{num, text}, ...]
 * num 取自 [n]，text 是去掉 [n] 之后的引用条目正文。
 */
export function parseReferenceList(text) {
  const refText = extractReferences(text)
  if (!refText) return []
  const lines = refText.split('\n').filter(l => l.trim())
  return lines.map((line, idx) => {
    const numMatch = line.match(/^\s*\[(\d+)\]\s*(.+)/)
    return {
      num: numMatch ? numMatch[1] : String(idx + 1),
      text: numMatch ? numMatch[2].trim() : line.trim()
    }
  }).filter(r => r.text)
}
