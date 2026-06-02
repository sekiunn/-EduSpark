import { ref } from 'vue'

/**
 * 聊天输入区"附件 + 拖放上传"管理。
 *
 * 状态：
 *   - pendingAttachments：待发送的附件列表（用户拖入 / 点击添加 / 录音转录得到的）
 *   - isDraggingOver：拖拽悬浮在聊天区时为 true，用于高亮提示
 *
 * 暴露的方法：handleDragOver / handleDragLeave / handleDrop / addAttachment / removeAttachment
 */
export function useAttachments() {
  const pendingAttachments = ref([])
  const isDraggingOver = ref(false)

  function handleDragOver(e) {
    e.preventDefault()
    if (e.dataTransfer?.types?.includes('Files')) {
      isDraggingOver.value = true
    }
  }

  function handleDragLeave(e) {
    // 只有真正离开聊天区才隐藏高亮（避免子元素触发）
    if (!e.currentTarget.contains(e.relatedTarget)) {
      isDraggingOver.value = false
    }
  }

  function handleDrop(e) {
    e.preventDefault()
    isDraggingOver.value = false
    const files = e.dataTransfer?.files
    if (!files || files.length === 0) return
    for (const file of files) {
      addAttachment(file)
    }
  }

  /** 通过扩展名归类附件类型（pdf / word / ppt / image / video / audio / file）。 */
  function inferAttachmentType(fileName) {
    const ext = fileName.split('.').pop()?.toLowerCase() || ''
    const typeMap = {
      pdf: 'pdf', docx: 'word', doc: 'word',
      pptx: 'ppt', ppt: 'ppt',
      png: 'image', jpg: 'image', jpeg: 'image', webp: 'image', gif: 'image',
      mp4: 'video', mov: 'video', avi: 'video',
      mp3: 'audio', wav: 'audio', m4a: 'audio'
    }
    return typeMap[ext] || 'file'
  }

  async function addAttachment(file) {
    // 避免同名同大小重复添加
    if (pendingAttachments.value.some(a => a.name === file.name && a.size === file.size)) return
    const att = {
      id: Date.now() + Math.random(),
      name: file.name,
      type: inferAttachmentType(file.name),
      size: file.size,
      file,
      loading: true
    }
    pendingAttachments.value.push(att)
    // 模拟前端解析预览：600~1000ms 后取消 loading 态
    await new Promise(r => setTimeout(r, 600 + Math.random() * 400))
    const item = pendingAttachments.value.find(a => a.id === att.id)
    if (item) item.loading = false
  }

  function removeAttachment(id) {
    pendingAttachments.value = pendingAttachments.value.filter(a => a.id !== id)
  }

  return {
    pendingAttachments,
    isDraggingOver,
    handleDragOver,
    handleDragLeave,
    handleDrop,
    addAttachment,
    removeAttachment
  }
}
