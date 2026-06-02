<script setup>
import { computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { BASE_URL } from '@/api/config.js'
import { listPptScenes, listPptStyles, listPptTemplates } from '@/api/pptTemplate.js'
import { authState, getRoleLabel } from '@/stores/adminAuth.js'

const router = useRouter()

const state = reactive({
  loading: false,
  error: '',
  sceneCount: 0,
  enabledSceneCount: 0,
  styleCount: 0,
  enabledStyleCount: 0,
  templateCount: 0,
  enabledTemplateCount: 0
})

const statCards = computed(() => [
  {
    label: '场景总数',
    value: state.sceneCount,
    meta: `已启用 ${state.enabledSceneCount} 个`
  },
  {
    label: '风格总数',
    value: state.styleCount,
    meta: `已启用 ${state.enabledStyleCount} 个`
  },
  {
    label: '模板总数',
    value: state.templateCount,
    meta: '模板管理页当前维护的模板池'
  },
  {
    label: '启用模板',
    value: state.enabledTemplateCount,
    meta: '前台可直接选择的模板数量'
  }
])

const quickActions = [
  {
    title: '进入模板管理',
    desc: '集中维护 PPT 模板、预览图、引擎键和启停状态。',
    actionLabel: '打开模板页',
    action: () => router.push('/ppt/templates')
  },
  {
    title: '进入分类管理',
    desc: '维护场景和风格分类，保障模板归属结构清晰。',
    actionLabel: '打开分类页',
    action: () => router.push('/ppt/categories')
  },
  {
    title: '刷新数据概览',
    desc: '重新拉取模板相关统计，确认接口与鉴权状态正常。',
    actionLabel: '立即刷新',
    action: () => loadOverview()
  }
]

const operationItems = computed(() => [
  { label: '当前角色', value: getRoleLabel(authState.user?.role) },
  { label: '登录账号', value: authState.user?.username || '系统管理员' },
  { label: '手机号', value: authState.user?.phone || '未绑定' },
  { label: '最近登录', value: authState.user?.lastLoginTime || '暂无记录' }
])

const welcomeName = computed(() => authState.user?.username || '系统管理员')

async function loadOverview() {
  state.loading = true
  state.error = ''

  try {
    const [sceneRes, styleRes, templateRes, enabledTemplateRes] = await Promise.all([
      listPptScenes(),
      listPptStyles(),
      listPptTemplates({ page: 1, size: 1 }),
      listPptTemplates({ enabled: true, page: 1, size: 1 })
    ])

    const scenes = sceneRes.data || []
    const styles = styleRes.data || []
    const templatePage = templateRes.data || {}
    const enabledTemplatePage = enabledTemplateRes.data || {}

    state.sceneCount = scenes.length
    state.enabledSceneCount = scenes.filter((item) => Number(item.enabled) === 1).length
    state.styleCount = styles.length
    state.enabledStyleCount = styles.filter((item) => Number(item.enabled) === 1).length
    state.templateCount = Number(templatePage.total || 0)
    state.enabledTemplateCount = Number(enabledTemplatePage.total || 0)
  } catch (error) {
    state.error = error.message || '首页概览数据加载失败'
  } finally {
    state.loading = false
  }
}

onMounted(() => {
  loadOverview()
})
</script>

<template>
  <div class="page-shell dashboard-page">
    <section class="dashboard-hero">
      <div class="dashboard-hero-copy">
        <span class="dashboard-kicker">Dashboard</span>
        <h2>{{ welcomeName }}，欢迎回来</h2>
        <p>这里聚合了 PPT 资产后台最核心的统计和入口，适合快速进入模板管理与分类维护。</p>
      </div>
    </section>

    <div v-if="state.error" class="alert-banner error">{{ state.error }}</div>

    <section class="dashboard-stat-grid">
      <article v-for="card in statCards" :key="card.label" class="dashboard-stat-card">
        <div class="dashboard-stat-label">{{ card.label }}</div>
        <div class="dashboard-stat-value">{{ card.value }}</div>
        <div class="dashboard-stat-meta">{{ card.meta }}</div>
      </article>
    </section>

    <section class="dashboard-panel-grid">
      <article class="dashboard-panel">
        <div class="dashboard-panel-head">
          <h3>快捷入口</h3>
          <p>从这里直接进入模板与分类两类核心管理任务。</p>
        </div>

        <div class="dashboard-action-list">
          <div v-for="item in quickActions" :key="item.title" class="dashboard-action-item">
            <div>
              <div class="dashboard-action-title">{{ item.title }}</div>
              <div class="dashboard-action-desc">{{ item.desc }}</div>
            </div>
            <button class="button button-primary button-sm" type="button" @click="item.action()">
              {{ item.actionLabel }}
            </button>
          </div>
        </div>
      </article>

      <article class="dashboard-panel">
        <div class="dashboard-panel-head">
          <h3>运行信息</h3>
          <p>当前账号与接入环境的关键信息。</p>
        </div>

        <div class="dashboard-info-list">
          <div v-for="item in operationItems" :key="item.label" class="dashboard-info-row">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>

          <div class="dashboard-info-row">
            <span>接口基地址</span>
            <code>{{ BASE_URL }}</code>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.dashboard-page {
  gap: 16px;
}

.dashboard-hero,
.dashboard-stat-card,
.dashboard-panel {
  border: 1px solid var(--admin-border);
  background: var(--admin-white);
  box-shadow: var(--admin-card-shadow);
}

.dashboard-hero {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 28px 30px;
  border-radius: 22px;
  background:
    radial-gradient(circle at top right, rgba(var(--admin-primary-rgb), 0.12), transparent 28%),
    linear-gradient(180deg, rgba(var(--admin-primary-rgb), 0.05), rgba(255, 255, 255, 0.96)),
    var(--admin-white);
}

.dashboard-kicker {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  color: var(--admin-primary);
  font-size: 12px;
  font-weight: 600;
  background: rgba(var(--admin-primary-rgb), 0.1);
}

.dashboard-hero-copy h2 {
  margin: 14px 0 0;
  color: var(--admin-text);
  font-size: 30px;
  line-height: 1.2;
}

.dashboard-hero-copy p {
  max-width: 720px;
  margin: 12px 0 0;
  color: var(--admin-text-secondary);
  font-size: 14px;
}

.dashboard-info-row code {
  padding: 8px 10px;
  border-radius: 10px;
  color: var(--admin-primary);
  background: rgba(var(--admin-primary-rgb), 0.08);
  word-break: break-all;
}

.dashboard-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.dashboard-stat-card {
  padding: 20px 22px;
  border-radius: 18px;
}

.dashboard-stat-label {
  color: var(--admin-text-secondary);
  font-size: 13px;
  font-weight: 500;
}

.dashboard-stat-value {
  margin-top: 18px;
  color: var(--admin-text);
  font-size: 34px;
  font-weight: 600;
  line-height: 1;
}

.dashboard-stat-meta {
  margin-top: 10px;
  color: var(--admin-text-muted);
  font-size: 12px;
}

.dashboard-panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.dashboard-panel {
  padding: 22px;
  border-radius: 18px;
}

.dashboard-panel-head h3 {
  margin: 0;
  color: var(--admin-text);
  font-size: 18px;
  font-weight: 600;
}

.dashboard-panel-head p {
  margin: 8px 0 0;
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.dashboard-action-list,
.dashboard-info-list {
  margin-top: 20px;
}

.dashboard-action-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dashboard-action-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid var(--admin-border);
  border-radius: 14px;
  background: var(--admin-bg);
}

.dashboard-action-title {
  color: var(--admin-text);
  font-size: 14px;
  font-weight: 600;
}

.dashboard-action-desc {
  margin-top: 4px;
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.dashboard-info-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.dashboard-info-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  color: var(--admin-text-secondary);
}

.dashboard-info-row strong {
  color: var(--admin-text);
  font-size: 14px;
  font-weight: 600;
  text-align: right;
}

@media (max-width: 1200px) {
  .dashboard-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .dashboard-panel-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .dashboard-hero {
    padding: 22px 20px;
  }

  .dashboard-hero-copy h2 {
    font-size: 24px;
  }

  .dashboard-stat-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-action-item,
  .dashboard-info-row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
