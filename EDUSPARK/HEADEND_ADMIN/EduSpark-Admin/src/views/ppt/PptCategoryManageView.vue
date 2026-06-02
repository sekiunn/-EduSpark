<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import DialogCard from '@/components/ui/DialogCard.vue'
import {
  createPptScene,
  createPptStyle,
  deletePptScene,
  deletePptStyle,
  listPptScenes,
  listPptStyles,
  updatePptScene,
  updatePptStyle
} from '@/api/pptTemplate.js'

const notice = reactive({
  type: 'success',
  text: ''
})

const loading = reactive({
  scenes: false,
  styles: false
})

const summary = reactive({
  sceneCount: 0,
  enabledSceneCount: 0,
  styleCount: 0,
  enabledStyleCount: 0
})

const scenes = ref([])
const styles = ref([])
const styleSceneFilter = ref('')
let noticeTimer = null

const sceneDialog = reactive({
  open: false,
  mode: 'create',
  submitting: false,
  form: createEmptySceneForm()
})

const styleDialog = reactive({
  open: false,
  mode: 'create',
  submitting: false,
  form: createEmptyStyleForm()
})

const isRefreshing = computed(() => Object.values(loading).some(Boolean))
const sortedScenes = computed(() => [...scenes.value].sort(sortBySortField))

const filteredStyles = computed(() => {
  if (!styleSceneFilter.value) {
    return styles.value
  }

  return styles.value.filter((item) => String(item.sceneId) === String(styleSceneFilter.value))
})

const sortedStyles = computed(() => [...filteredStyles.value].sort(sortBySortField))

const summaryCards = computed(() => [
  {
    label: '场景总数',
    value: summary.sceneCount,
    meta: `已启用 ${summary.enabledSceneCount} 个场景`
  },
  {
    label: '风格总数',
    value: summary.styleCount,
    meta: `已启用 ${summary.enabledStyleCount} 个风格`
  }
])

function createEmptySceneForm() {
  return {
    id: '',
    sceneCode: '',
    sceneName: '',
    sort: 10,
    enabled: 1
  }
}

function createEmptyStyleForm() {
  return {
    id: '',
    sceneId: '',
    styleCode: '',
    styleName: '',
    sort: 10,
    enabled: 1
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

function statusLabel(enabled) {
  return Number(enabled) === 1 ? '启用' : '停用'
}

function formatDate(value) {
  return value || '--'
}

function updateCounts() {
  summary.sceneCount = scenes.value.length
  summary.enabledSceneCount = scenes.value.filter((item) => Number(item.enabled) === 1).length
  summary.styleCount = styles.value.length
  summary.enabledStyleCount = styles.value.filter((item) => Number(item.enabled) === 1).length
}

function openSceneDialog(scene = null) {
  sceneDialog.mode = scene ? 'edit' : 'create'
  sceneDialog.form = scene
    ? {
        id: scene.id,
        sceneCode: scene.sceneCode,
        sceneName: scene.sceneName,
        sort: Number(scene.sort || 0),
        enabled: Number(scene.enabled || 0)
      }
    : createEmptySceneForm()
  sceneDialog.open = true
}

function openStyleDialog(style = null) {
  styleDialog.mode = style ? 'edit' : 'create'
  styleDialog.form = style
    ? {
        id: style.id,
        sceneId: String(style.sceneId || ''),
        styleCode: style.styleCode,
        styleName: style.styleName,
        sort: Number(style.sort || 0),
        enabled: Number(style.enabled || 0)
      }
    : {
        ...createEmptyStyleForm(),
        sceneId: styleSceneFilter.value || (sortedScenes.value[0] ? String(sortedScenes.value[0].id) : '')
      }
  styleDialog.open = true
}

async function submitScene() {
  if (!sceneDialog.form.sceneCode.trim()) {
    showNotice('请输入场景编码', 'error')
    return
  }

  if (!sceneDialog.form.sceneName.trim()) {
    showNotice('请输入场景名称', 'error')
    return
  }

  sceneDialog.submitting = true

  try {
    const payload = {
      sceneCode: sceneDialog.form.sceneCode.trim(),
      sceneName: sceneDialog.form.sceneName.trim(),
      sort: Number(sceneDialog.form.sort || 0),
      enabled: Number(sceneDialog.form.enabled || 0)
    }

    if (sceneDialog.mode === 'edit') {
      await updatePptScene(sceneDialog.form.id, payload)
    } else {
      await createPptScene(payload)
    }

    sceneDialog.open = false
    showNotice(sceneDialog.mode === 'edit' ? '场景已更新' : '场景已创建')
    await Promise.all([loadScenes(), loadStyles()])
  } catch (error) {
    showNotice(error.message || '场景保存失败', 'error')
  } finally {
    sceneDialog.submitting = false
  }
}

async function submitStyle() {
  if (!styleDialog.form.sceneId) {
    showNotice('请选择所属场景', 'error')
    return
  }

  if (!styleDialog.form.styleCode.trim()) {
    showNotice('请输入风格编码', 'error')
    return
  }

  if (!styleDialog.form.styleName.trim()) {
    showNotice('请输入风格名称', 'error')
    return
  }

  styleDialog.submitting = true

  try {
    const payload = {
      sceneId: Number(styleDialog.form.sceneId),
      styleCode: styleDialog.form.styleCode.trim(),
      styleName: styleDialog.form.styleName.trim(),
      sort: Number(styleDialog.form.sort || 0),
      enabled: Number(styleDialog.form.enabled || 0)
    }

    if (styleDialog.mode === 'edit') {
      await updatePptStyle(styleDialog.form.id, payload)
    } else {
      await createPptStyle(payload)
    }

    styleDialog.open = false
    showNotice(styleDialog.mode === 'edit' ? '风格已更新' : '风格已创建')
    await loadStyles()
  } catch (error) {
    showNotice(error.message || '风格保存失败', 'error')
  } finally {
    styleDialog.submitting = false
  }
}

async function removeScene(scene) {
  if (!window.confirm(`确认删除场景“${scene.sceneName}”吗？`)) {
    return
  }

  try {
    await deletePptScene(scene.id)
    showNotice('场景已删除')
    await Promise.all([loadScenes(), loadStyles()])
  } catch (error) {
    showNotice(error.message || '删除场景失败', 'error')
  }
}

async function removeStyle(style) {
  if (!window.confirm(`确认删除风格“${style.styleName}”吗？`)) {
    return
  }

  try {
    await deletePptStyle(style.id)
    showNotice('风格已删除')
    await loadStyles()
  } catch (error) {
    showNotice(error.message || '删除风格失败', 'error')
  }
}

async function loadScenes() {
  loading.scenes = true

  try {
    const response = await listPptScenes()
    scenes.value = response.data || []

    if (styleSceneFilter.value && !scenes.value.some((item) => String(item.id) === String(styleSceneFilter.value))) {
      styleSceneFilter.value = ''
    }

    updateCounts()
  } catch (error) {
    showNotice(error.message || '场景列表加载失败', 'error')
  } finally {
    loading.scenes = false
  }
}

async function loadStyles() {
  loading.styles = true

  try {
    const response = await listPptStyles()
    styles.value = response.data || []
    updateCounts()
  } catch (error) {
    showNotice(error.message || '风格列表加载失败', 'error')
  } finally {
    loading.styles = false
  }
}

async function refreshAll() {
  await Promise.all([loadScenes(), loadStyles()])
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
  <div class="page-shell ppt-category-page">
    <section class="ppt-page-hero">
      <div class="ppt-page-copy">
        <span class="ppt-page-kicker">PPT Categories</span>
        <h2>分类管理</h2>
        <p>把模板依赖的场景与风格拆成独立资源管理，确保分类体系、启用状态和排序规则清晰一致。</p>
      </div>

      <div class="ppt-hero-metrics">
        <article v-for="card in summaryCards" :key="card.label" class="ppt-hero-metric">
          <div class="ppt-hero-metric-label">{{ card.label }}</div>
          <div class="ppt-hero-metric-value">{{ card.value }}</div>
          <div class="ppt-hero-metric-meta">{{ card.meta }}</div>
        </article>
      </div>
    </section>

    <transition name="toast-fade">
      <div v-if="notice.text" class="floating-toast" :class="notice.type" role="status">{{ notice.text }}</div>
    </transition>

    <section class="ppt-panel-grid">
      <article class="ppt-section-card">
        <div class="ppt-section-head">
          <div class="ppt-section-copy">
            <h3>场景列表</h3>
            <p>面向前台模板选择器和模板归类的一级分类。</p>
          </div>

          <div class="toolbar-inline">
            <button class="button button-secondary" type="button" :disabled="isRefreshing" @click="refreshAll">
              {{ isRefreshing ? '刷新中...' : '刷新数据' }}
            </button>
            <button class="button button-primary" type="button" @click="openSceneDialog()">新增场景</button>
          </div>
        </div>

        <div class="data-table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>场景信息</th>
                <th>排序</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody v-if="sortedScenes.length">
              <tr v-for="scene in sortedScenes" :key="scene.id">
                <td>
                  <div class="table-meta-stack">
                    <strong>{{ scene.sceneName }}</strong>
                    <span>编码：{{ scene.sceneCode }}</span>
                  </div>
                </td>
                <td>{{ scene.sort }}</td>
                <td>
                  <span class="status-tag" :class="Number(scene.enabled) === 1 ? 'success' : 'warning'">
                    {{ statusLabel(scene.enabled) }}
                  </span>
                </td>
                <td>{{ formatDate(scene.updateTime) }}</td>
                <td>
                  <div class="table-actions">
                    <button class="button button-secondary button-sm" type="button" @click="openSceneDialog(scene)">编辑</button>
                    <button class="button button-danger button-sm" type="button" @click="removeScene(scene)">删除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>

          <div v-if="!sortedScenes.length" class="table-empty">
            {{ loading.scenes ? '场景加载中...' : '暂无场景数据' }}
          </div>
        </div>
      </article>

      <article class="ppt-section-card">
        <div class="ppt-section-head">
          <div class="ppt-section-copy">
            <h3>风格列表</h3>
            <p>风格归属于某个场景，可按场景过滤查看和维护。</p>
          </div>

          <div class="toolbar-inline">
            <select v-model="styleSceneFilter" class="select" style="min-width: 220px;">
              <option value="">全部场景</option>
              <option v-for="scene in sortedScenes" :key="scene.id" :value="String(scene.id)">{{ scene.sceneName }}</option>
            </select>
            <button class="button button-primary" type="button" @click="openStyleDialog()">新增风格</button>
          </div>
        </div>

        <div class="data-table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>风格信息</th>
                <th>所属场景</th>
                <th>排序</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody v-if="sortedStyles.length">
              <tr v-for="style in sortedStyles" :key="style.id">
                <td>
                  <div class="table-meta-stack">
                    <strong>{{ style.styleName }}</strong>
                    <span>编码：{{ style.styleCode }}</span>
                  </div>
                </td>
                <td>{{ style.sceneName || '--' }}</td>
                <td>{{ style.sort }}</td>
                <td>
                  <span class="status-tag" :class="Number(style.enabled) === 1 ? 'success' : 'warning'">
                    {{ statusLabel(style.enabled) }}
                  </span>
                </td>
                <td>{{ formatDate(style.updateTime) }}</td>
                <td>
                  <div class="table-actions">
                    <button class="button button-secondary button-sm" type="button" @click="openStyleDialog(style)">编辑</button>
                    <button class="button button-danger button-sm" type="button" @click="removeStyle(style)">删除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>

          <div v-if="!sortedStyles.length" class="table-empty">
            {{ loading.styles ? '风格加载中...' : '暂无风格数据' }}
          </div>
        </div>
      </article>
    </section>

    <DialogCard
      v-model="sceneDialog.open"
      :title="sceneDialog.mode === 'edit' ? '编辑场景' : '新增场景'"
      description="字段将映射到后端的 PptTemplateSceneRequest。"
      width="560px"
    >
      <form class="form-grid" @submit.prevent="submitScene">
        <label class="field">
          <span class="field-label">场景编码</span>
          <input v-model.trim="sceneDialog.form.sceneCode" class="input" type="text" placeholder="例如 classroom" />
        </label>

        <label class="field">
          <span class="field-label">场景名称</span>
          <input v-model.trim="sceneDialog.form.sceneName" class="input" type="text" placeholder="例如 课堂授课" />
        </label>

        <label class="field">
          <span class="field-label">排序</span>
          <input v-model.number="sceneDialog.form.sort" class="input" type="number" min="0" />
        </label>

        <label class="field">
          <span class="field-label">状态</span>
          <select v-model.number="sceneDialog.form.enabled" class="select">
            <option :value="1">启用</option>
            <option :value="0">停用</option>
          </select>
        </label>
      </form>

      <template #footer>
        <button class="button button-secondary" type="button" @click="sceneDialog.open = false">取消</button>
        <button class="button button-primary" type="button" :disabled="sceneDialog.submitting" @click="submitScene">
          {{ sceneDialog.submitting ? '提交中...' : '保存场景' }}
        </button>
      </template>
    </DialogCard>

    <DialogCard
      v-model="styleDialog.open"
      :title="styleDialog.mode === 'edit' ? '编辑风格' : '新增风格'"
      description="字段将映射到后端的 PptTemplateStyleRequest。"
      width="560px"
    >
      <form class="form-grid" @submit.prevent="submitStyle">
        <label class="field span-2">
          <span class="field-label">所属场景</span>
          <select v-model="styleDialog.form.sceneId" class="select">
            <option value="">请选择所属场景</option>
            <option v-for="scene in sortedScenes" :key="scene.id" :value="String(scene.id)">{{ scene.sceneName }}</option>
          </select>
        </label>

        <label class="field">
          <span class="field-label">风格编码</span>
          <input v-model.trim="styleDialog.form.styleCode" class="input" type="text" placeholder="例如 tech_blue" />
        </label>

        <label class="field">
          <span class="field-label">风格名称</span>
          <input v-model.trim="styleDialog.form.styleName" class="input" type="text" placeholder="例如 科技蓝" />
        </label>

        <label class="field">
          <span class="field-label">排序</span>
          <input v-model.number="styleDialog.form.sort" class="input" type="number" min="0" />
        </label>

        <label class="field">
          <span class="field-label">状态</span>
          <select v-model.number="styleDialog.form.enabled" class="select">
            <option :value="1">启用</option>
            <option :value="0">停用</option>
          </select>
        </label>
      </form>

      <template #footer>
        <button class="button button-secondary" type="button" @click="styleDialog.open = false">取消</button>
        <button class="button button-primary" type="button" :disabled="styleDialog.submitting" @click="submitStyle">
          {{ styleDialog.submitting ? '提交中...' : '保存风格' }}
        </button>
      </template>
    </DialogCard>
  </div>
</template>

<style scoped>
.ppt-category-page {
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
  max-width: 760px;
  margin: 12px 0 0;
  color: var(--admin-text-secondary);
  font-size: 14px;
}

.ppt-hero-metrics {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 280px;
  flex-shrink: 0;
}

.ppt-hero-metric {
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

.ppt-hero-metric-meta {
  margin-top: 8px;
  color: var(--admin-text-muted);
  font-size: 12px;
}

.ppt-panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
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

@media (max-width: 1200px) {
  .ppt-panel-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .ppt-page-hero,
  .ppt-section-head {
    flex-direction: column;
  }

  .ppt-hero-metrics {
    width: 100%;
  }
}

@media (max-width: 640px) {
  .ppt-page-hero {
    padding: 22px 20px;
  }

  .ppt-page-copy h2 {
    font-size: 24px;
  }
}
</style>
