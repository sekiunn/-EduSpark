<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import DialogCard from '@/components/ui/DialogCard.vue'
import {
  createPptTemplate,
  deletePptTemplate,
  getPptTemplate,
  listPptScenes,
  listPptStyles,
  listPptTemplates,
  preParsePptTemplate,
  togglePptTemplate,
  updatePptTemplate,
  uploadPptTemplateAsset
} from '@/api/pptTemplate.js'

const router = useRouter()

const notice = reactive({
  type: 'success',
  text: ''
})

const loading = reactive({
  catalog: false,
  templates: false,
  summary: false
})

const summary = reactive({
  templateCount: 0,
  enabledTemplateCount: 0,
  disabledTemplateCount: 0
})

const scenes = ref([])
const styles = ref([])
const coverInputRef = ref(null)
const previewInputRef = ref(null)
const pptxInputRef = ref(null)
const uploadTarget = ref('cover')
const parsedTemplateStructure = ref(null)
let noticeTimer = null

const filters = reactive({
  keyword: '',
  sceneId: '',
  styleId: '',
  enabled: ''
})

const pagination = reactive({
  page: 1,
  size: 8
})

const templatePage = reactive({
  total: 0,
  page: 1,
  size: 8,
  pages: 0,
  list: []
})

const templateDialog = reactive({
  open: false,
  mode: 'create',
  loading: false,
  submitting: false,
  parsing: false,
  showAdvanced: false,
  form: createEmptyTemplateForm()
})

const isRefreshing = computed(() => Object.values(loading).some(Boolean))
const sortedScenes = computed(() => [...scenes.value].sort(sortBySortField))

const templateStyleOptions = computed(() => {
  if (!filters.sceneId) {
    return styles.value
  }

  return styles.value.filter((item) => String(item.sceneId) === String(filters.sceneId))
})

const dialogStyleOptions = computed(() => {
  if (!templateDialog.form.sceneId) {
    return styles.value
  }

  return styles.value.filter((item) => String(item.sceneId) === String(templateDialog.form.sceneId))
})

const totalParsedMarkerCount = computed(() => {
  const slides = parsedTemplateStructure.value?.slides
  if (!Array.isArray(slides)) {
    return 0
  }
  return slides.reduce((sum, s) => sum + (s.markers?.length || 0), 0)
})

// 用 JS 字符串常量包住"{{...}}"字面量，避免被 Vue 模板解析器当成插值。
const pptxPlaceholderHint = '上传含 {{标记名}} 占位符的 pptx，系统会自动解析标记结构'
const pptxMarkerExampleTitle = '{{课程标题}}'
const pptxMarkerExampleSubtitle = '{{授课教师}}'
const pptxMarkerExampleBullets = '{{学习要点}}'
const pptxMarkerExampleNotes = '{{讲解要点}}'

// 哪些条件让"保存模板"按钮不可点。返回非空字符串就 disabled 按钮并显示原因。
const saveDisabledReason = computed(() => {
  if (templateDialog.parsing) return 'PPT 解析中，请稍候...'
  if (!templateDialog.form.name?.trim()) return '请填写模板名称'
  if (!templateDialog.form.sceneId || !templateDialog.form.styleId) return '请选择所属场景和风格'
  if (templateDialog.mode === 'create'
      && !templateDialog.form.pendingFileKey
      && !templateDialog.form.templateFilePath) {
    return '请先上传 PPT 模板文件'
  }
  return ''
})

// 用 Set 记录加载失败的图片 URL，UI 上可以显示"图片加载失败 + 查看链接"
const brokenImageUrls = ref(new Set())
function markImageBroken(url) {
  if (!url) return
  brokenImageUrls.value.add(url)
  // 强制触发 reactivity（Set.add 不会自动触发）
  brokenImageUrls.value = new Set(brokenImageUrls.value)
}
function markImageLoaded(url) {
  if (!url || !brokenImageUrls.value.has(url)) return
  brokenImageUrls.value.delete(url)
  brokenImageUrls.value = new Set(brokenImageUrls.value)
}
function isImageBroken(url) {
  return brokenImageUrls.value.has(url)
}

const pptxStatusBadge = computed(() => {
  if (templateDialog.parsing) {
    return { text: '解析中', tone: 'info' }
  }
  if (templateDialog.form.pendingFileKey) {
    return { text: '待提交', tone: 'warning' }
  }
  if (templateDialog.form.templateFilePath) {
    return { text: '已上传', tone: 'success' }
  }
  if (templateDialog.mode === 'create') {
    return { text: '必填', tone: 'error' }
  }
  return null
})

const summaryCards = computed(() => [
  {
    label: '模板总数',
    value: summary.templateCount
  },
  {
    label: '已启用模板',
    value: summary.enabledTemplateCount
  },
  {
    label: '已停用模板',
    value: summary.disabledTemplateCount
  }
])

watch(
  () => filters.sceneId,
  (sceneId) => {
    if (!filters.styleId) {
      return
    }

    const valid = styles.value.some(
      (item) => String(item.id) === String(filters.styleId) && (!sceneId || String(item.sceneId) === String(sceneId))
    )

    if (!valid) {
      filters.styleId = ''
    }
  }
)

watch(
  () => templateDialog.form.sceneId,
  (sceneId) => {
    if (!templateDialog.form.styleId) {
      return
    }

    const valid = styles.value.some(
      (item) =>
        String(item.id) === String(templateDialog.form.styleId) &&
        (!sceneId || String(item.sceneId) === String(sceneId))
    )

    if (!valid) {
      templateDialog.form.styleId = ''
    }
  }
)

watch(
  () => pagination.size,
  () => {
    pagination.page = 1
    loadTemplates()
  }
)

function createEmptyTemplateForm() {
  return {
    id: '',
    templateCode: '',
    name: '',
    sceneId: '',
    styleId: '',
    coverUrl: '',
    previewImages: [],
    description: '',
    engineTemplateKey: '',
    promptHint: '',
    blueprintConfigJson: '{}',
    renderConfigJson: '{}',
    templateFilePath: '',
    pendingFileKey: '',
    pendingFileName: '',
    enabled: 1,
    isDefault: 0,
    sort: 10,
    version: 1
  }
}

function showNotice(text, type = 'success') {
  notice.type = type
  notice.text = text

  if (noticeTimer) {
    clearTimeout(noticeTimer)
  }

  noticeTimer = setTimeout(() => {
    notice.text = ''
  }, 2800)
}

function sortBySortField(left, right) {
  return Number(left.sort || 0) - Number(right.sort || 0)
}

function parseLineList(text) {
  return text
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function normalizeJsonText(value, label) {
  const trimmed = value.trim()
  if (!trimmed) {
    return ''
  }

  try {
    return JSON.stringify(JSON.parse(trimmed), null, 2)
  } catch {
    throw new Error(`${label} 不是有效的 JSON`)
  }
}

function statusLabel(enabled) {
  return Number(enabled) === 1 ? '启用' : '停用'
}

function formatDate(value) {
  return value || '--'
}

function buildTemplateQuery() {
  return {
    ...(filters.keyword ? { keyword: filters.keyword.trim() } : {}),
    ...(filters.sceneId ? { sceneId: filters.sceneId } : {}),
    ...(filters.styleId ? { styleId: filters.styleId } : {}),
    ...(filters.enabled !== '' ? { enabled: filters.enabled === '1' } : {}),
    page: pagination.page,
    size: pagination.size
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.sceneId = ''
  filters.styleId = ''
  filters.enabled = ''
  pagination.page = 1
  loadTemplates()
}

function handleCreateTemplate() {
  if (!scenes.value.length || !styles.value.length) {
    showNotice('请先在分类管理中配置场景和风格', 'warning')
    router.push('/ppt/categories')
    return
  }

  openTemplateDialog()
}

async function openTemplateDialog(template = null) {
  templateDialog.mode = template ? 'edit' : 'create'
  templateDialog.loading = !!template
  templateDialog.open = true
  templateDialog.showAdvanced = false
  parsedTemplateStructure.value = null

  if (!template) {
    templateDialog.form = createEmptyTemplateForm()
    return
  }

  try {
    const response = await getPptTemplate(template.id)
    const detail = response.data || {}

    templateDialog.form = {
      id: detail.id,
      templateCode: detail.templateCode || '',
      name: detail.name || '',
      sceneId: String(detail.sceneId || ''),
      styleId: String(detail.styleId || ''),
      coverUrl: detail.coverUrl || '',
      previewImages: Array.isArray(detail.previewImages) ? [...detail.previewImages] : [],
      description: detail.description || '',
      engineTemplateKey: detail.engineTemplateKey || '',
      promptHint: detail.promptHint || '',
      blueprintConfigJson: detail.blueprintConfigJson || '{}',
      renderConfigJson: detail.renderConfigJson || '{}',
      templateFilePath: detail.templateFilePath || '',
      pendingFileKey: '',
      pendingFileName: '',
      enabled: Number(detail.enabled || 0),
      isDefault: Number(detail.isDefault || 0),
      sort: Number(detail.sort || 0),
      version: Number(detail.version || 1)
    }

    // Parse template structure for marker preview
    try {
      const config = JSON.parse(detail.renderConfigJson || '{}')
      parsedTemplateStructure.value = config.slides ? config : null
    } catch {
      parsedTemplateStructure.value = null
    }
  } catch (error) {
    showNotice(error.message || '模板详情加载失败', 'error')
    templateDialog.open = false
  } finally {
    templateDialog.loading = false
  }
}

async function submitTemplate() {
  if (!templateDialog.form.name.trim()) {
    showNotice('请输入模板名称', 'error')
    return
  }

  if (!templateDialog.form.sceneId || !templateDialog.form.styleId) {
    showNotice('请选择所属场景和风格', 'error')
    return
  }

  // 新增模式下必须先上传 pptx 才能保存（否则会得到一个"真空壳"模板，对教师端不可见）
  if (templateDialog.mode === 'create'
      && !templateDialog.form.pendingFileKey
      && !templateDialog.form.templateFilePath) {
    showNotice('请先上传 PPT 模板文件', 'error')
    return
  }

  templateDialog.submitting = true

  try {
    const payload = {
      templateCode: templateDialog.form.templateCode.trim() || null,
      name: templateDialog.form.name.trim(),
      sceneId: Number(templateDialog.form.sceneId),
      styleId: Number(templateDialog.form.styleId),
      coverUrl: templateDialog.form.coverUrl.trim(),
      previewImages: (templateDialog.form.previewImages || []).filter(url => url && url.trim()),
      description: templateDialog.form.description.trim(),
      // engineTemplateKey 为空时后端会用 templateCode 自动派生
      engineTemplateKey: templateDialog.form.engineTemplateKey.trim() || null,
      promptHint: templateDialog.form.promptHint.trim(),
      blueprintConfigJson: normalizeJsonText(templateDialog.form.blueprintConfigJson, '蓝图配置'),
      renderConfigJson: normalizeJsonText(templateDialog.form.renderConfigJson, '渲染配置'),
      enabled: Number(templateDialog.form.enabled || 0),
      isDefault: Number(templateDialog.form.isDefault || 0),
      sort: Number(templateDialog.form.sort || 0),
      version: Number(templateDialog.form.version || 1),
      pendingFileKey: templateDialog.form.pendingFileKey || null
    }

    if (templateDialog.mode === 'edit') {
      await updatePptTemplate(templateDialog.form.id, payload)
    } else {
      await createPptTemplate(payload)
    }

    templateDialog.open = false
    showNotice(templateDialog.mode === 'edit' ? '模板已更新' : '模板已创建')
    await Promise.all([loadTemplates(), loadTemplateSummary()])
  } catch (error) {
    showNotice(error.message || '模板保存失败', 'error')
  } finally {
    templateDialog.submitting = false
  }
}

async function removeTemplate(template) {
  if (!window.confirm(`确认删除模板“${template.name}”吗？`)) {
    return
  }

  try {
    await deletePptTemplate(template.id)
    showNotice('模板已删除')

    if (pagination.page > 1 && templatePage.list.length === 1) {
      pagination.page -= 1
    }

    await Promise.all([loadTemplates(), loadTemplateSummary()])
  } catch (error) {
    showNotice(error.message || '删除模板失败', 'error')
  }
}

async function handleToggleTemplate(template) {
  try {
    await togglePptTemplate(template.id)
    showNotice('模板状态已切换')
    await Promise.all([loadTemplates(), loadTemplateSummary()])
  } catch (error) {
    showNotice(error.message || '切换模板状态失败', 'error')
  }
}

function openUploadPicker(target) {
  uploadTarget.value = target

  if (target === 'cover') {
    coverInputRef.value?.click()
    return
  }

  previewInputRef.value?.click()
}

async function handleUpload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''

  if (!file) {
    return
  }

  try {
    const response = await uploadPptTemplateAsset(file)
    const fileUrl = response.data

    if (uploadTarget.value === 'cover') {
      templateDialog.form.coverUrl = fileUrl
    } else {
      if (!Array.isArray(templateDialog.form.previewImages)) {
        templateDialog.form.previewImages = []
      }
      templateDialog.form.previewImages.push(fileUrl)
    }

    showNotice('素材上传成功')
  } catch (error) {
    showNotice(error.message || '素材上传失败', 'error')
  }
}

function removeCoverImage() {
  templateDialog.form.coverUrl = ''
}

function removePreviewImage(index) {
  if (!Array.isArray(templateDialog.form.previewImages)) return
  templateDialog.form.previewImages.splice(index, 1)
}

async function handlePptxUpload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return

  templateDialog.parsing = true
  try {
    // 不论新增还是编辑模式，都走 pre-parse：临时存盘 + POI 解析标记 + LLM 建议元数据。
    // 最终在 submit 时随 createTemplate / updateTemplate 一并提交。
    const response = await preParsePptTemplate(file)
    const summary = response.data || {}

    templateDialog.form.pendingFileKey = summary.pendingFileKey || ''
    templateDialog.form.pendingFileName = summary.originalFileName || file.name
    templateDialog.form.renderConfigJson = summary.renderConfigJson || '{}'

    // 系统建议字段：仅在管理员还没手填的情况下自动回填，避免覆盖用户已有的输入。
    const filled = []
    if (summary.suggestedName && !templateDialog.form.name) {
      templateDialog.form.name = summary.suggestedName
      filled.push('名称')
    }
    if (summary.suggestedDescription && !templateDialog.form.description) {
      templateDialog.form.description = summary.suggestedDescription
      filled.push('描述')
    }
    if (summary.suggestedSceneId && !templateDialog.form.sceneId) {
      const sceneIdStr = String(summary.suggestedSceneId)
      if (scenes.value.some(s => String(s.id) === sceneIdStr)) {
        templateDialog.form.sceneId = sceneIdStr
        filled.push('场景')
      }
    }
    if (summary.suggestedStyleId && !templateDialog.form.styleId) {
      const styleIdStr = String(summary.suggestedStyleId)
      if (styles.value.some(st => String(st.id) === styleIdStr)) {
        templateDialog.form.styleId = styleIdStr
        filled.push('风格')
      }
    }

    try {
      const config = JSON.parse(summary.renderConfigJson || '{}')
      parsedTemplateStructure.value = config && Array.isArray(config.slides) ? config : null
    } catch {
      parsedTemplateStructure.value = null
    }

    const slideCount = summary.totalSlides || 0
    const markerCount = summary.markerCount || 0
    const fillSuffix = filled.length ? `，已自动填充：${filled.join('、')}` : ''
    showNotice(`已解析 ${slideCount} 页 / ${markerCount} 个标记${fillSuffix}`)
  } catch (error) {
    showNotice(error.message || 'PPTX 解析失败', 'error')
  } finally {
    templateDialog.parsing = false
  }
}

function goPrevPage() {
  if (pagination.page <= 1) {
    return
  }

  pagination.page -= 1
  loadTemplates()
}

function goNextPage() {
  if (pagination.page >= (templatePage.pages || 1)) {
    return
  }

  pagination.page += 1
  loadTemplates()
}

async function loadCatalog() {
  loading.catalog = true

  try {
    const [sceneResponse, styleResponse] = await Promise.all([listPptScenes(), listPptStyles()])
    scenes.value = sceneResponse.data || []
    styles.value = styleResponse.data || []
  } catch (error) {
    showNotice(error.message || '分类数据加载失败', 'error')
  } finally {
    loading.catalog = false
  }
}

async function loadTemplateSummary() {
  loading.summary = true

  try {
    const [templateResponse, enabledResponse] = await Promise.all([
      listPptTemplates({ page: 1, size: 1 }),
      listPptTemplates({ enabled: true, page: 1, size: 1 })
    ])

    summary.templateCount = Number(templateResponse.data?.total || 0)
    summary.enabledTemplateCount = Number(enabledResponse.data?.total || 0)
    summary.disabledTemplateCount = Math.max(0, summary.templateCount - summary.enabledTemplateCount)
  } catch (error) {
    showNotice(error.message || '模板统计加载失败', 'error')
  } finally {
    loading.summary = false
  }
}

async function loadTemplates() {
  loading.templates = true

  try {
    const response = await listPptTemplates(buildTemplateQuery())
    const page = response.data || {}

    templatePage.total = Number(page.total || 0)
    templatePage.page = Number(page.page || pagination.page)
    templatePage.size = Number(page.size || pagination.size)
    templatePage.pages = Number(page.pages || 0)
    templatePage.list = page.list || []
  } catch (error) {
    showNotice(error.message || '模板列表加载失败', 'error')
  } finally {
    loading.templates = false
  }
}

async function refreshAll() {
  await Promise.all([loadCatalog(), loadTemplates(), loadTemplateSummary()])
}

onMounted(() => {
  refreshAll()
})

onBeforeUnmount(() => {
  if (noticeTimer) {
    clearTimeout(noticeTimer)
  }
})
</script>

<template>
  <div class="page-shell ppt-template-page">
    <section class="ppt-page-hero">
      <div class="ppt-page-copy">
        <span class="ppt-page-kicker">PPT Templates</span>
        <h2>模板管理</h2>
        <p>集中维护模板元数据、引擎模板键、预览素材与前台可见状态，保持模板资产清晰可控。</p>
      </div>

      <div class="ppt-hero-metrics">
        <article v-for="card in summaryCards" :key="card.label" class="ppt-hero-metric">
          <div class="ppt-hero-metric-label">{{ card.label }}</div>
          <div class="ppt-hero-metric-value">{{ card.value }}</div>
        </article>
      </div>
    </section>

    <transition name="toast-fade">
      <div v-if="notice.text" class="floating-toast" :class="notice.type" role="status">{{ notice.text }}</div>
    </transition>

    <section class="ppt-section-card">
      <div class="ppt-section-head">
        <div class="ppt-section-copy">
          <h3>筛选条件</h3>
          <p>按模板名称、场景、风格和状态快速定位需要维护的模板。</p>
        </div>

        <button class="button button-secondary" type="button" :disabled="isRefreshing" @click="refreshAll">
          {{ isRefreshing ? '刷新中...' : '刷新数据' }}
        </button>
      </div>

      <div class="ppt-filter-grid">
        <input v-model.trim="filters.keyword" class="input" type="text" placeholder="搜索模板名称、编码或引擎键" />

        <select v-model="filters.sceneId" class="select">
          <option value="">全部场景</option>
          <option v-for="scene in sortedScenes" :key="scene.id" :value="String(scene.id)">{{ scene.sceneName }}</option>
        </select>

        <select v-model="filters.styleId" class="select">
          <option value="">全部风格</option>
          <option v-for="style in templateStyleOptions" :key="style.id" :value="String(style.id)">{{ style.styleName }}</option>
        </select>

        <select v-model="filters.enabled" class="select">
          <option value="">全部状态</option>
          <option value="1">仅启用</option>
          <option value="0">仅停用</option>
        </select>
      </div>

      <div class="toolbar-inline" style="margin-top: 16px;">
        <button class="button button-primary" type="button" @click="pagination.page = 1; loadTemplates()">查询</button>
        <button class="button button-ghost" type="button" @click="resetFilters">重置</button>
      </div>

      <div v-if="!scenes.length || !styles.length" class="ppt-inline-banner">
        模板依赖场景和风格分类。当前分类未配置完整，请先前往
        <button class="banner-link" type="button" @click="router.push('/ppt/categories')">分类管理</button>
        完成基础配置。
      </div>
    </section>

    <section class="ppt-section-card">
      <div class="ppt-section-head">
        <div class="ppt-section-copy">
          <h3>模板列表</h3>
          <p>当前筛选命中 {{ templatePage.total }} 个模板，支持直接编辑、启停和删除。</p>
        </div>

        <button class="button button-primary" type="button" @click="handleCreateTemplate">新增模板</button>
      </div>

      <div class="data-table-wrap">
        <table class="data-table" style="min-width: 1100px;">
          <thead>
            <tr>
              <th>模板信息</th>
              <th>分类归属</th>
              <th>引擎配置</th>
              <th>状态</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody v-if="templatePage.list.length">
            <tr v-for="template in templatePage.list" :key="template.id">
              <td>
                <div class="template-cell">
                  <div class="template-cover">
                    <img v-if="template.coverUrl" :src="template.coverUrl" :alt="template.name" />
                    <div v-else class="template-cover-placeholder">暂无封面</div>
                  </div>

                  <div class="template-main">
                    <div class="template-name">{{ template.name }}</div>
                    <div class="template-meta">编码：{{ template.templateCode || '--' }}</div>
                    <div class="template-meta">预览图：{{ template.previewImages?.length || 0 }} 张</div>
                    <div class="template-desc">{{ template.description || '未填写模板描述' }}</div>
                  </div>
                </div>
              </td>
              <td>
                <div class="table-meta-stack">
                  <strong>{{ template.sceneName || '--' }}</strong>
                  <span>{{ template.styleName || '--' }}</span>
                  <span>排序 {{ template.sort }} / 版本 {{ template.version }}</span>
                </div>
              </td>
              <td>
                <div class="table-meta-stack">
                  <strong>{{ template.engineTemplateKey }}</strong>
                  <span v-if="template.promptHint">{{ template.promptHint }}</span>
                  <span v-else>未填写 Prompt 提示</span>
                </div>
              </td>
              <td>
                <div class="status-stack">
                  <span class="status-tag" :class="Number(template.enabled) === 1 ? 'success' : 'warning'">
                    {{ statusLabel(template.enabled) }}
                  </span>
                  <span v-if="Number(template.isDefault) === 1" class="status-tag info">默认模板</span>
                </div>
              </td>
              <td>{{ formatDate(template.updateTime) }}</td>
              <td>
                <div class="table-actions">
                  <button class="button button-secondary button-sm" type="button" @click="openTemplateDialog(template)">编辑</button>
                  <button class="button button-ghost button-sm" type="button" @click="handleToggleTemplate(template)">
                    {{ Number(template.enabled) === 1 ? '停用' : '启用' }}
                  </button>
                  <button class="button button-danger button-sm" type="button" @click="removeTemplate(template)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="!templatePage.list.length" class="table-empty">
          {{ loading.templates ? '模板加载中...' : '暂无模板数据' }}
        </div>
      </div>

      <div class="pagination-bar">
        <div class="pagination-meta">
          共 {{ templatePage.total }} 条，当前第 {{ pagination.page }} / {{ templatePage.pages || 1 }} 页
        </div>

        <div class="pagination-actions">
          <select v-model.number="pagination.size" class="select" style="width: 110px;">
            <option :value="8">8 / 页</option>
            <option :value="12">12 / 页</option>
            <option :value="20">20 / 页</option>
          </select>

          <button class="button button-secondary button-sm" type="button" :disabled="pagination.page <= 1" @click="goPrevPage">
            上一页
          </button>
          <button
            class="button button-secondary button-sm"
            type="button"
            :disabled="pagination.page >= (templatePage.pages || 1)"
            @click="goNextPage"
          >
            下一页
          </button>
        </div>
      </div>
    </section>

    <DialogCard
      v-model="templateDialog.open"
      :title="templateDialog.mode === 'edit' ? '编辑模板' : '新增模板'"
      description="字段将映射到后端的 PptTemplateUpsertRequest，JSON 字段会在提交前校验格式。"
      width="980px"
    >
      <div v-if="templateDialog.loading" class="table-empty">模板详情加载中...</div>

      <form v-else class="form-grid" @submit.prevent="submitTemplate">
        <!-- ① PPT 模板文件：第二版下这是模板的"主角"，先上传 → 解析 → 再填其他元数据 -->
        <div class="field span-2 pptx-primary">
          <div class="pptx-primary-head">
            <div class="pptx-primary-title">
              <span class="field-label-text">PPT 模板文件</span>
              <span v-if="pptxStatusBadge" class="pptx-status-badge" :class="pptxStatusBadge.tone">
                {{ pptxStatusBadge.text }}
              </span>
            </div>
            <button
              type="button"
              class="button button-primary"
              :disabled="templateDialog.parsing"
              @click="pptxInputRef?.click()"
            >
              {{ templateDialog.parsing
                ? '解析中（含 AI 分析）...'
                : (templateDialog.form.pendingFileKey || templateDialog.form.templateFilePath
                  ? '替换 PPT 模板'
                  : '上传 PPT 模板') }}
            </button>
          </div>
          <div class="pptx-primary-meta">
            <template v-if="templateDialog.form.pendingFileKey">
              <span class="pptx-filename">待提交：{{ templateDialog.form.pendingFileName || '已上传 pptx' }}</span>
              <span class="field-hint">保存模板后，临时文件会归档到正式位置</span>
            </template>
            <template v-else-if="templateDialog.form.templateFilePath">
              <span class="pptx-filename">已上传：{{ templateDialog.form.templateFilePath }}</span>
              <span class="field-hint">点击"替换 PPT 模板"可上传新版本</span>
            </template>
            <template v-else>
              <span class="pptx-filename pptx-empty">未上传任何 pptx 文件</span>
              <span class="field-hint">{{ pptxPlaceholderHint }}</span>
            </template>
          </div>
        </div>

        <!-- 解析摘要 -->
        <div v-if="parsedTemplateStructure?.slides?.length" class="field span-2 marker-preview">
          <span class="field-label">已解析标记 ({{ totalParsedMarkerCount }} 个)</span>

          <!-- 0 标记时：使用"上传即模板"模式（AI 改写全部文本，保留图片/版式/配色） -->
          <div v-if="totalParsedMarkerCount === 0" class="marker-zero-info">
            <div class="marker-zero-title">✓ 上传即模板模式</div>
            <div class="marker-zero-body">
              <p>这份 pptx 没有手工占位符，将使用<strong>"上传即模板"自动模式</strong>：
                老师选用后，AI 会按教学主题改写 pptx 里的所有文本内容，原文件的图片、版式、
                配色、装饰元素全部保留。这通常就是你想要的。</p>
              <p>如果你想<strong>精确控制</strong>哪些位置被改、哪些保留，可以在 PowerPoint 的目标文本框里手写
                双花括号占位符（如 <code>{{ pptxMarkerExampleTitle }}</code>），重新上传后系统会优先按占位符替换。</p>
            </div>
          </div>

          <!-- 有标记时正常展示 -->
          <div v-else class="marker-list">
            <div v-for="slide in parsedTemplateStructure.slides" :key="slide.slideIndex" class="marker-slide">
              <div class="marker-slide-header">
                第 {{ slide.slideIndex + 1 }} 页
                <span class="marker-role">{{ slide.slideRole || '未分类' }}</span>
              </div>
              <div v-if="slide.markers?.length" class="marker-tags">
                <span v-for="m in slide.markers" :key="m.name" class="marker-tag" v-text="'{{' + m.name + '}}'">
                </span>
              </div>
              <div v-else class="marker-empty">无标记</div>
            </div>
          </div>
          <div v-if="totalParsedMarkerCount > 0 && parsedTemplateStructure.repeatableSlideIndex >= 0" class="field-hint">
            可复制页：第 {{ parsedTemplateStructure.repeatableSlideIndex + 1 }} 页（生成时若内容超出页数，将复制此页扩展）
          </div>
        </div>

        <!-- ② 元数据基本信息 -->
        <label class="field span-2">
          <span class="field-label">模板名称</span>
          <input v-model.trim="templateDialog.form.name" class="input" type="text" placeholder="请输入模板名称" />
        </label>

        <label class="field">
          <span class="field-label">所属场景</span>
          <select v-model="templateDialog.form.sceneId" class="select">
            <option value="">请选择场景</option>
            <option v-for="scene in sortedScenes" :key="scene.id" :value="String(scene.id)">{{ scene.sceneName }}</option>
          </select>
        </label>

        <label class="field">
          <span class="field-label">所属风格</span>
          <select v-model="templateDialog.form.styleId" class="select">
            <option value="">请选择风格</option>
            <option v-for="style in dialogStyleOptions" :key="style.id" :value="String(style.id)">{{ style.styleName }}</option>
          </select>
        </label>

        <label class="field span-2">
          <span class="field-label">模板描述</span>
          <textarea v-model="templateDialog.form.description" class="textarea" placeholder="描述模板适用场景与展示风格"></textarea>
        </label>

        <div class="field span-2">
          <span class="field-label">封面</span>
          <div class="image-card-row">
            <div
              v-if="templateDialog.form.coverUrl"
              class="image-card"
              :title="templateDialog.form.coverUrl"
            >
              <img
                v-show="!isImageBroken(templateDialog.form.coverUrl)"
                :src="templateDialog.form.coverUrl"
                alt="封面预览"
                @error="markImageBroken(templateDialog.form.coverUrl)"
                @load="markImageLoaded(templateDialog.form.coverUrl)"
              />
              <div
                v-if="isImageBroken(templateDialog.form.coverUrl)"
                class="image-card-error"
              >
                <div class="image-card-error-title">⚠ 图片加载失败</div>
                <a :href="templateDialog.form.coverUrl" target="_blank" class="image-card-error-link">
                  打开 URL 检查
                </a>
              </div>
              <div class="image-card-actions">
                <button
                  type="button"
                  class="image-card-btn"
                  title="替换"
                  @click="openUploadPicker('cover')"
                >替换</button>
                <button
                  type="button"
                  class="image-card-btn danger"
                  title="删除"
                  @click="removeCoverImage"
                >×</button>
              </div>
            </div>
            <button
              v-else
              type="button"
              class="image-card image-card-empty"
              @click="openUploadPicker('cover')"
            >
              <span class="image-card-plus">＋</span>
              <span class="image-card-text">点击上传封面</span>
            </button>
          </div>
          <span class="field-hint">单张图，建议宽高比 16:9 或 4:3</span>
        </div>

        <div class="field span-2">
          <span class="field-label">预览图列表</span>
          <div class="image-grid">
            <div
              v-for="(imgUrl, idx) in templateDialog.form.previewImages"
              :key="idx + '-' + imgUrl"
              class="image-card"
              :title="imgUrl"
            >
              <img
                v-show="!isImageBroken(imgUrl)"
                :src="imgUrl"
                :alt="`预览图 ${idx + 1}`"
                @error="markImageBroken(imgUrl)"
                @load="markImageLoaded(imgUrl)"
              />
              <div v-if="isImageBroken(imgUrl)" class="image-card-error">
                <div class="image-card-error-title">⚠ 加载失败</div>
                <a :href="imgUrl" target="_blank" class="image-card-error-link">打开 URL</a>
              </div>
              <div class="image-card-actions">
                <button
                  type="button"
                  class="image-card-btn danger"
                  title="删除"
                  @click="removePreviewImage(idx)"
                >×</button>
              </div>
            </div>
            <button
              type="button"
              class="image-card image-card-empty"
              @click="openUploadPicker('preview')"
            >
              <span class="image-card-plus">＋</span>
              <span class="image-card-text">添加预览图</span>
            </button>
          </div>
          <span class="field-hint">可上传多张，按上传顺序展示</span>
        </div>

        <label class="field">
          <span class="field-label">状态</span>
          <select v-model.number="templateDialog.form.enabled" class="select">
            <option :value="1">启用</option>
            <option :value="0">停用</option>
          </select>
        </label>

        <label class="field">
          <span class="field-label">默认模板</span>
          <select v-model.number="templateDialog.form.isDefault" class="select">
            <option :value="0">否</option>
            <option :value="1">是</option>
          </select>
        </label>

        <label class="field">
          <span class="field-label">排序</span>
          <input v-model.number="templateDialog.form.sort" class="input" type="number" min="0" />
        </label>

        <!-- ③ 高级设置（默认折叠）：第二版下这些字段大多用不到 -->
        <div class="field span-2 advanced-toggle">
          <button
            type="button"
            class="button button-ghost button-sm"
            @click="templateDialog.showAdvanced = !templateDialog.showAdvanced"
          >
            {{ templateDialog.showAdvanced ? '▾ 收起高级设置' : '▸ 展开高级设置（模板编码、引擎键、Prompt 等）' }}
          </button>
        </div>

        <template v-if="templateDialog.showAdvanced">
          <label class="field">
            <span class="field-label">模板编码</span>
            <input v-model.trim="templateDialog.form.templateCode" class="input" type="text" placeholder="留空时按名称自动生成" />
          </label>

          <label class="field">
            <span class="field-label">引擎模板 Key</span>
            <input
              v-model.trim="templateDialog.form.engineTemplateKey"
              class="input"
              type="text"
              placeholder="留空时与模板编码一致"
            />
          </label>

          <label class="field">
            <span class="field-label">版本</span>
            <input v-model.number="templateDialog.form.version" class="input" type="number" min="1" />
          </label>

          <label class="field span-2">
            <span class="field-label">Prompt 提示</span>
            <textarea v-model="templateDialog.form.promptHint" class="textarea" placeholder="用于补充模板风格约束"></textarea>
          </label>

          <label class="field span-2">
            <span class="field-label">蓝图配置 JSON</span>
            <textarea v-model="templateDialog.form.blueprintConfigJson" class="textarea" placeholder="{}"></textarea>
          </label>

          <label class="field span-2">
            <span class="field-label">渲染配置 JSON（自动生成，只读）</span>
            <textarea
              v-model="templateDialog.form.renderConfigJson"
              class="textarea readonly"
              readonly
              placeholder="上传 pptx 后由系统自动填充"
            ></textarea>
            <span class="field-hint">该字段由 pptx 解析或配色种子自动维护，请勿手动修改</span>
          </label>
        </template>
      </form>

      <template #footer>
        <span v-if="saveDisabledReason" class="footer-hint">{{ saveDisabledReason }}</span>
        <button class="button button-secondary" type="button" @click="templateDialog.open = false">取消</button>
        <button
          class="button button-primary"
          type="button"
          :disabled="templateDialog.submitting || !!saveDisabledReason"
          @click="submitTemplate"
        >
          {{ templateDialog.submitting ? '提交中...' : '保存模板' }}
        </button>
      </template>
    </DialogCard>

    <input ref="coverInputRef" type="file" accept="image/*" hidden @change="handleUpload" />
    <input ref="previewInputRef" type="file" accept="image/*" hidden @change="handleUpload" />
    <input ref="pptxInputRef" type="file" accept=".pptx" hidden @change="handlePptxUpload" />
  </div>
</template>

<style scoped>
.ppt-template-page {
  gap: 16px;
}

.ppt-page-hero,
.ppt-section-card,
.ppt-hero-metric {
  border: 1px solid var(--admin-border);
  background: var(--admin-white);
  box-shadow: var(--admin-card-shadow);
}

.ppt-page-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 30px;
  border-radius: 22px;
  background:
    radial-gradient(circle at top right, rgba(var(--admin-primary-rgb), 0.12), transparent 30%),
    linear-gradient(180deg, rgba(var(--admin-primary-rgb), 0.05), rgba(255, 255, 255, 0.96)),
    var(--admin-white);
}

.ppt-page-copy {
  flex: 1;
  min-width: 0;
}

.ppt-page-kicker {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  color: var(--admin-primary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  background: rgba(var(--admin-primary-rgb), 0.1);
}

.ppt-page-copy h2 {
  margin: 14px 0 0;
  font-size: 30px;
  line-height: 1.2;
  color: var(--admin-text);
}

.ppt-page-copy p {
  max-width: 520px;
  margin: 12px 0 0;
  color: var(--admin-text-secondary);
  font-size: 14px;
}

.ppt-hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, 140px);
  justify-content: flex-start;
  gap: 12px;
  width: fit-content;
  flex-shrink: 0;
}

.ppt-hero-metric {
  box-sizing: border-box;
  width: 140px;
  min-height: 108px;
  padding: 16px 18px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
}

.ppt-hero-metric-label {
  color: var(--admin-text-secondary);
  font-size: 12px;
  font-weight: 500;
}

.ppt-hero-metric-value {
  margin-top: 14px;
  color: var(--admin-text);
  font-size: 28px;
  font-weight: 600;
  line-height: 1;
}

.ppt-section-card {
  padding: 24px;
  border-radius: 20px;
}

.ppt-section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.ppt-section-copy h3 {
  margin: 0;
  color: var(--admin-text);
  font-size: 18px;
  font-weight: 600;
}

.ppt-section-copy p {
  margin: 8px 0 0;
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.ppt-filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.ppt-inline-banner {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px solid rgba(var(--admin-primary-rgb), 0.12);
  border-radius: 14px;
  color: var(--admin-text-secondary);
  font-size: 13px;
  background: rgba(var(--admin-primary-rgb), 0.05);
}

.banner-link {
  padding: 0;
  border: none;
  color: var(--admin-primary);
  font-weight: 600;
  background: transparent;
}

.template-cell {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.template-cover {
  width: 88px;
  height: 60px;
  overflow: hidden;
  flex-shrink: 0;
  border: 1px solid var(--admin-border);
  border-radius: 12px;
  background: var(--es-surface-soft);
}

.template-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.template-cover-placeholder {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  color: var(--admin-text-muted);
  font-size: 12px;
}

.template-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.template-name {
  color: var(--admin-text);
  font-size: 14px;
  font-weight: 600;
}

.template-meta {
  color: var(--admin-text-muted);
  font-size: 12px;
}

.template-desc {
  display: -webkit-box;
  overflow: hidden;
  color: var(--admin-text-secondary);
  font-size: 13px;
  line-height: 1.5;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.table-meta-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.table-meta-stack strong {
  color: var(--admin-text);
  font-size: 14px;
  font-weight: 600;
}

.table-meta-stack span {
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.status-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 1480px) {
  .ppt-page-hero {
    flex-direction: column;
  }

  .ppt-hero-metrics {
    width: fit-content;
    max-width: 100%;
  }
}

@media (max-width: 1200px) {
  .ppt-filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ppt-hero-metrics {
    grid-template-columns: repeat(2, 140px);
  }
}

@media (max-width: 960px) {
  .ppt-page-hero,
  .ppt-section-head {
    flex-direction: column;
  }

  .ppt-hero-metrics {
    grid-template-columns: repeat(2, 140px);
  }
}

@media (max-width: 640px) {
  .ppt-page-hero {
    padding: 22px 20px;
  }

  .ppt-page-copy h2 {
    font-size: 24px;
  }

  .ppt-filter-grid {
    grid-template-columns: 1fr;
  }

  .ppt-hero-metrics {
    grid-template-columns: 140px;
    width: 140px;
  }

  .template-cell {
    flex-direction: column;
  }

  .template-cover {
    width: 100%;
    height: 140px;
  }
}

.pptx-upload-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pptx-filename {
  flex: 1;
  font-size: 13px;
  color: var(--color-text, #334155);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pptx-filename.pptx-empty {
  color: var(--color-text-muted, #94a3b8);
}

.pptx-primary {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border: 1px dashed var(--admin-border, #e2e8f0);
  border-radius: 12px;
  background: rgba(var(--admin-primary-rgb, 59 130 246), 0.04);
}

.pptx-primary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pptx-primary-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.field-label-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--admin-text, #0f172a);
}

.pptx-status-badge {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 999px;
}

.pptx-status-badge.success {
  background: var(--color-success-soft, #dcfce7);
  color: var(--color-success, #16a34a);
}

.pptx-status-badge.warning {
  background: #fef3c7;
  color: #b45309;
}

.pptx-status-badge.info {
  background: var(--color-primary-soft, #dbeafe);
  color: var(--color-primary, #3b82f6);
}

.pptx-status-badge.error {
  background: #fee2e2;
  color: #b91c1c;
}

.pptx-primary-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.advanced-toggle {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed var(--admin-border, #e2e8f0);
}

.textarea.readonly {
  background: var(--color-surface-alt, #f8fafc);
  color: var(--color-text-muted, #64748b);
  cursor: default;
}

/* === 图片上传卡片（封面 + 预览图） === */
.image-card-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, 130px);
  gap: 12px;
}

.image-card {
  position: relative;
  width: 130px;
  height: 90px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--color-surface-alt, #f8fafc);
  border: 1px solid var(--admin-border, #e2e8f0);
  box-sizing: border-box;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.image-card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.image-card:hover {
  border-color: var(--admin-primary, #3b82f6);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.image-card-actions {
  position: absolute;
  top: 4px;
  right: 4px;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.image-card:hover .image-card-actions {
  opacity: 1;
}

.image-card-btn {
  padding: 2px 8px;
  border: 0;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  background: rgba(0, 0, 0, 0.65);
  color: #fff;
  cursor: pointer;
  line-height: 1.4;
}

.image-card-btn:hover {
  background: rgba(0, 0, 0, 0.85);
}

.image-card-btn.danger {
  background: rgba(220, 38, 38, 0.85);
  padding: 2px 7px;
}

.image-card-btn.danger:hover {
  background: rgba(185, 28, 28, 1);
}

.image-card-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 2px dashed var(--admin-border, #cbd5e1);
  background: transparent;
  color: var(--color-text-muted, #94a3b8);
  cursor: pointer;
  padding: 0;
}

.image-card-error {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 6px;
  background: #fef2f2;
  text-align: center;
}

.image-card-error-title {
  font-size: 12px;
  color: #b91c1c;
  font-weight: 500;
}

.image-card-error-link {
  font-size: 11px;
  color: #1d4ed8;
  text-decoration: underline;
  word-break: break-all;
}

.footer-hint {
  flex: 1;
  font-size: 12px;
  color: var(--color-text-muted, #94a3b8);
  align-self: center;
}

.image-card-empty:hover {
  border-color: var(--admin-primary, #3b82f6);
  color: var(--admin-primary, #3b82f6);
  background: rgba(var(--admin-primary-rgb, 59 130 246), 0.04);
}

.image-card-plus {
  font-size: 22px;
  line-height: 1;
  font-weight: 300;
}

.image-card-text {
  font-size: 12px;
}

/* === 0 标记中性提示卡（FullReplacement 模式说明）=== */
.marker-zero-info {
  margin-top: 8px;
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid #bae6fd;
  background: #f0f9ff;
}

.marker-zero-title {
  color: #0c4a6e;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.marker-zero-body {
  color: #0c4a6e;
  font-size: 13px;
  line-height: 1.6;
}

.marker-zero-body p {
  margin: 0 0 6px;
}

.marker-zero-body p:last-child {
  margin-bottom: 0;
}

.marker-zero-body code {
  background: rgba(12, 74, 110, 0.08);
  padding: 1px 6px;
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #0c4a6e;
}

.button-sm {
  padding: 4px 12px;
  font-size: 12px;
}

.marker-preview {
  margin-top: 4px;
}

.marker-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
  padding: 12px;
  background: var(--color-surface-alt, #f8fafc);
  border-radius: 8px;
  border: 1px solid var(--color-border, #e2e8f0);
}

.marker-slide-header {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text, #334155);
  display: flex;
  align-items: center;
  gap: 6px;
}

.marker-role {
  font-size: 11px;
  font-weight: 400;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--color-primary-soft, #dbeafe);
  color: var(--color-primary, #3b82f6);
}

.marker-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.marker-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--color-success-soft, #dcfce7);
  color: var(--color-success, #16a34a);
  font-family: monospace;
}

.marker-empty {
  font-size: 12px;
  color: var(--color-text-muted, #94a3b8);
}

.field-hint {
  display: block;
  font-size: 11px;
  color: var(--color-text-muted, #94a3b8);
  margin-top: 4px;
}
</style>
