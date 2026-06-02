import { ref, computed } from 'vue'
import { getPptTemplates } from '@/api'

/**
 * PPT 模板状态 + 操作 composable。
 *
 * 涵盖：模板列表加载、当前选中的模板 id、选择弹窗的开关、把后端模板原始数据
 * 规范化为前端用的 view-model（含渐变色 preset、封面 placeholder 信息等），
 * 以及给"PPT 模式首条用户消息"追加模板风格提示词的 helper。
 *
 * 跟 ChatHome 主流程的耦合点：
 *   - `currentMode` 切走 ppt 时，ChatHome 应主动调 {@link resetPptTemplateState} 清状态；
 *     composable 本身不监听 currentMode，避免双向耦合。
 *   - `decoratePptMessageForRequest(content, opts)` 需要外部传入 currentMode + 是否首条消息，
 *     由 ChatHome 自己根据 messages.length / currentMode 决定，避免 composable 反向依赖。
 */

const PPT_TEMPLATE_SURFACE_PRESETS = [
  {
    background: 'linear-gradient(135deg, #eff6ff 0%, #d9ebff 38%, #4f8cff 100%)',
    surfaceTextColor: '#0f172a'
  },
  {
    background: 'linear-gradient(135deg, #f8fafc 0%, #dfe7f0 38%, #b7cadf 100%)',
    surfaceTextColor: '#1f2937'
  },
  {
    background: 'linear-gradient(135deg, #020617 0%, #1f2937 54%, #f59e0b 100%)',
    surfaceTextColor: '#f8fafc'
  },
  {
    background: 'linear-gradient(135deg, #ecfccb 0%, #d9f99d 40%, #84cc16 100%)',
    surfaceTextColor: '#1f2937'
  },
  {
    background: 'linear-gradient(135deg, #eef2ff 0%, #ddd6fe 38%, #6366f1 100%)',
    surfaceTextColor: '#1e1b4b'
  },
  {
    background: 'linear-gradient(135deg, #fef3c7 0%, #fed7aa 40%, #f97316 100%)',
    surfaceTextColor: '#431407'
  }
]

/** 把后端模板原始数据规范化为前端用 view-model。 */
function normalizePptTemplate(template, index) {
  const preset = PPT_TEMPLATE_SURFACE_PRESETS[index % PPT_TEMPLATE_SURFACE_PRESETS.length]
  const scene = template?.sceneName || '默认场景'
  const style = template?.styleName || '默认风格'
  const description = template?.description?.trim() || `${scene} / ${style} 模板`
  const previewImages = Array.isArray(template?.previewImages) ? template.previewImages : []

  return {
    id: String(template?.id ?? template?.templateCode ?? `ppt-template-${index}`),
    templateCode: template?.templateCode || '',
    name: template?.name || '未命名模板',
    scene,
    style,
    badge: Number(template?.isDefault) === 1 ? '默认模板' : (style || 'PPT模板'),
    description,
    coverTitle: template?.name || 'PPT 模板',
    coverSubtitle: `${scene} / ${style}`,
    background: preset.background,
    surfaceTextColor: preset.surfaceTextColor,
    coverUrl: template?.coverUrl || '',
    previewImages,
    previewPages: previewImages.length
      ? previewImages.slice(0, 3).map((_, pageIndex) => `预览页 ${pageIndex + 1}`)
      : ['封面页', '目录页', '内容页']
  }
}

export function usePptTemplate() {
  const pptTemplateModalVisible = ref(false)
  const selectedPptTemplateId = ref('')
  const pptTemplates = ref([])
  const pptTemplatesLoading = ref(false)
  const pptTemplatesLoaded = ref(false)
  const pptTemplatesError = ref('')

  const selectedPptTemplate = computed(() =>
    pptTemplates.value.find(template => String(template.id) === String(selectedPptTemplateId.value)) || null
  )

  async function ensurePptTemplatesLoaded({ force = false } = {}) {
    if (pptTemplatesLoading.value) return
    if (!force && (pptTemplatesLoaded.value || pptTemplates.value.length > 0)) return

    pptTemplatesLoading.value = true
    pptTemplatesError.value = ''

    try {
      const response = await getPptTemplates()
      const list = Array.isArray(response?.data) ? response.data : []
      pptTemplates.value = list.map((template, index) => normalizePptTemplate(template, index))
      pptTemplatesLoaded.value = true

      // 当前选中的模板如果在新列表里没了（被管理员下架），清空选择
      if (
        selectedPptTemplateId.value &&
        !pptTemplates.value.some(template => String(template.id) === String(selectedPptTemplateId.value))
      ) {
        selectedPptTemplateId.value = ''
      }
    } catch (error) {
      console.error('加载 PPT 模板失败:', error)
      pptTemplatesError.value = error?.message || '加载模板失败，请稍后重试'
      if (!pptTemplatesLoaded.value) {
        pptTemplates.value = []
      }
    } finally {
      pptTemplatesLoading.value = false
    }
  }

  function resetPptTemplateState() {
    selectedPptTemplateId.value = ''
    pptTemplateModalVisible.value = false
  }

  async function openPptTemplateModal() {
    pptTemplateModalVisible.value = true
    await ensurePptTemplatesLoaded()
  }

  async function reloadPptTemplates() {
    await ensurePptTemplatesLoaded({ force: true })
  }

  function handlePptTemplateSelect(template) {
    selectedPptTemplateId.value = template?.id || ''
  }

  function getPptTemplateSurfaceStyle(template) {
    return {
      background: template?.background || 'linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%)',
      color: template?.surfaceTextColor || '#0f172a'
    }
  }

  function buildPptTemplatePromptHint(template) {
    if (!template) return ''
    const parts = [
      `请优先按“${template.name}”这套 PPT 模板风格来组织内容`,
      template.scene ? `适用场景是${template.scene}` : '',
      template.style ? `风格偏好为${template.style}` : '',
      template.description ? `模板特征：${template.description}` : ''
    ].filter(Boolean)
    return parts.join('，')
  }

  /**
   * 给 PPT 模式的首条用户消息追加模板风格提示词。
   * 调用方传 `isFirstMessage`（通常是 `messages.length === 0`）和当前是否在 ppt 模式，
   * 避免 composable 反向依赖外部的 currentMode / messages ref。
   */
  function decoratePptMessageForRequest(content, { isPptMode, isFirstMessage } = {}) {
    // 全替换链路下，模板视觉来自真实 pptx、文字由 AI 按文本块 1:1 改写，并不需要
    // “请优先按 X 模板风格组织内容”这类风格描述。更关键的是：之前把带引号的模板名
    //（如“手绘教师说课PPT”）追加进消息，会被后端 fastPath 的 extractTopic 当成课题抓走，
    // 把真实主题“C语言选择排序”挤掉，污染 PPT 主题与知识库检索 query。故不再追加任何提示。
    //（isPptMode / isFirstMessage / buildPptTemplatePromptHint 暂留，便于将来需要时回退。）
    return content?.trim() || ''
  }

  return {
    pptTemplateModalVisible,
    selectedPptTemplateId,
    selectedPptTemplate,
    pptTemplates,
    pptTemplatesLoading,
    pptTemplatesLoaded,
    pptTemplatesError,
    ensurePptTemplatesLoaded,
    openPptTemplateModal,
    reloadPptTemplates,
    handlePptTemplateSelect,
    resetPptTemplateState,
    getPptTemplateSurfaceStyle,
    buildPptTemplatePromptHint,
    decoratePptMessageForRequest
  }
}
