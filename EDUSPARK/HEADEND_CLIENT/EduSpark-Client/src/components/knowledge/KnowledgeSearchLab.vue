<script setup>
import { computed, ref } from 'vue'
import { searchKnowledgeTest } from '@/api'

const props = defineProps({
  userId: {
    type: [Number, String],
    default: null
  }
})

const query = ref('')
const topK = ref(5)
const maxTokens = ref(2000)
const vectorWeight = ref(0.6)
const bm25Weight = ref(0.4)
const loading = ref(false)
const errorMessage = ref('')
const result = ref(null)

const clamp = (value, min, max) => Math.min(max, Math.max(min, value))
const roundWeight = (value) => Math.round(value * 10) / 10

const weightTotal = computed(() => {
  return Number(vectorWeight.value || 0) + Number(bm25Weight.value || 0)
})

const parameterSummary = computed(() => [
  { label: 'TopK', value: topK.value },
  { label: 'Max Tokens', value: maxTokens.value },
  { label: '权重合计', value: weightTotal.value.toFixed(1) }
])

const normalizeParams = () => {
  const normalizedTopK = clamp(Number(topK.value) || 5, 1, 20)
  const normalizedMaxTokens = clamp(Number(maxTokens.value) || 2000, 100, 8000)
  const normalizedVector = roundWeight(clamp(Number(vectorWeight.value) || 0, 0, 1))
  const normalizedBm25 = roundWeight(clamp(Number(bm25Weight.value) || 0, 0, 1))

  topK.value = normalizedTopK
  maxTokens.value = normalizedMaxTokens
  vectorWeight.value = normalizedVector
  bm25Weight.value = normalizedBm25

  return {
    normalizedTopK,
    normalizedMaxTokens,
    normalizedVector,
    normalizedBm25
  }
}

const runSearch = async () => {
  if (!props.userId) {
    errorMessage.value = '请先登录后再进行检索测试。'
    return
  }

  if (!query.value.trim()) {
    errorMessage.value = '请输入一个用于测试的查询问题。'
    return
  }

  const { normalizedTopK, normalizedMaxTokens, normalizedVector, normalizedBm25 } = normalizeParams()

  if (Number((normalizedVector + normalizedBm25).toFixed(1)) !== 1) {
    errorMessage.value = 'Vector 权重与 BM25 权重之和需要保持为 1.0。'
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const res = await searchKnowledgeTest(query.value.trim(), props.userId, {
      topK: normalizedTopK,
      maxTokens: normalizedMaxTokens,
      vectorWeight: normalizedVector,
      bm25Weight: normalizedBm25
    })
    result.value = res.data
  } catch (error) {
    console.error('知识检索实验室调用失败:', error)
    errorMessage.value = error.message || '检索测试失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="search-lab">
    <div class="lab-intro">
      <div>
        <p class="lab-eyebrow">检索实验室</p>
        <h3>把命中原因、参数和最终上下文放在同一张工作台上</h3>
        <p class="lab-desc">
          这里直接调用后端的混合检索测试接口，帮助你观察召回片段、分数分布和最终拼接的上下文是否稳定。
        </p>
      </div>
      <dl class="lab-guide">
        <div>
          <dt>建议方式</dt>
          <dd>先用默认参数验证，再逐步微调</dd>
        </div>
        <div>
          <dt>权重规则</dt>
          <dd>Vector + BM25 始终保持为 1.0</dd>
        </div>
      </dl>
    </div>

    <div class="lab-card">
      <div class="lab-form">
        <div class="lab-form-head">
          <div>
            <h4>检索参数</h4>
            <p>建议先固定问题和文件集合，再逐步调整 TopK、权重和上下文长度。</p>
          </div>
          <span class="parameter-badge">权重合计 {{ weightTotal.toFixed(1) }}</span>
        </div>

        <label class="field">
          <span>测试问题</span>
          <textarea
            v-model="query"
            rows="4"
            placeholder="例如：初二化学“中和反应”适合设计什么样的课堂互动环节？"
          ></textarea>
        </label>

        <div class="field-grid">
          <label class="field">
            <span>TopK</span>
            <input v-model.number="topK" type="number" min="1" max="20" />
          </label>
          <label class="field">
            <span>Max Tokens</span>
            <input v-model.number="maxTokens" type="number" min="100" max="8000" step="100" />
          </label>
          <label class="field">
            <span>Vector 权重</span>
            <input v-model.number="vectorWeight" type="number" min="0" max="1" step="0.1" />
          </label>
          <label class="field">
            <span>BM25 权重</span>
            <input v-model.number="bm25Weight" type="number" min="0" max="1" step="0.1" />
          </label>
        </div>

        <div class="lab-actions">
          <button type="button" class="run-btn" :disabled="loading" @click="runSearch">
            {{ loading ? '检索中...' : '开始测试' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="errorMessage" class="lab-error" role="alert">
      {{ errorMessage }}
    </div>

    <template v-if="result">
      <div class="lab-stats">
        <article class="stat-card">
          <span class="stat-label">命中片段</span>
          <strong>{{ result.total || 0 }}</strong>
        </article>
        <article class="stat-card">
          <span class="stat-label">纳入上下文</span>
          <strong>{{ result.usedChunkCount || 0 }}</strong>
        </article>
        <article class="stat-card">
          <span class="stat-label">上下文长度</span>
          <strong>{{ result.contextLength || 0 }}</strong>
        </article>
        <article class="stat-card">
          <span class="stat-label">耗时</span>
          <strong>{{ result.costMs || 0 }} ms</strong>
        </article>
      </div>

      <div class="lab-results">
        <div class="results-panel">
          <div class="panel-head">
            <h4>命中片段</h4>
            <span>{{ result.results?.length || 0 }} 条</span>
          </div>

          <div v-if="result.results?.length" class="result-list">
            <article v-for="item in result.results" :key="item.chunkId" class="result-card">
              <div class="result-meta">
                <strong>{{ item.fileName }}</strong>
                <span>综合分：{{ Number(item.score || 0).toFixed(4) }}</span>
              </div>
              <div class="result-scores">
                <span>Vector：{{ Number(item.vectorScore || 0).toFixed(4) }}</span>
                <span>BM25：{{ Number(item.bm25Score || 0).toFixed(4) }}</span>
              </div>
              <p class="result-text">{{ item.text }}</p>
            </article>
          </div>
          <div v-else class="panel-empty">没有检索到任何片段。</div>
        </div>

        <div class="context-panel">
          <div class="panel-head">
            <h4>最终上下文</h4>
            <span>{{ result.maxTokens || 0 }} tokens 上限</span>
          </div>
          <div class="context-box">
            {{ result.context || '当前没有生成上下文。' }}
          </div>
          <div class="parameter-summary">
            <article v-for="item in parameterSummary" :key="item.label" class="summary-card">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </article>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.search-lab {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.lab-intro {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 320px);
  gap: 20px;
  padding: 24px 26px;
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: var(--kb-shadow-sm);
  backdrop-filter: blur(10px);
}

.lab-eyebrow {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--kb-text-tertiary);
}

.lab-intro h3 {
  margin-bottom: 12px;
  font-size: 28px;
  letter-spacing: -0.03em;
  color: var(--kb-text-primary);
}

.lab-desc {
  font-size: 14px;
  line-height: 1.8;
  color: var(--kb-text-secondary);
}

.lab-guide {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.lab-guide div {
  padding: 16px 18px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-surface-soft);
  border: 1px solid rgba(23, 22, 20, 0.04);
}

.lab-guide dt {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--kb-text-tertiary);
}

.lab-guide dd {
  font-size: 13px;
  line-height: 1.7;
  color: var(--kb-text-secondary);
}

.lab-card,
.results-panel,
.context-panel,
.stat-card {
  border: 1px solid var(--kb-border);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--kb-shadow-sm);
  backdrop-filter: blur(10px);
}

.lab-card {
  padding: 22px;
  border-radius: var(--kb-radius-lg);
}

.lab-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.lab-form-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.lab-form-head h4 {
  margin-bottom: 6px;
  font-size: 18px;
  color: var(--kb-text-primary);
}

.lab-form-head p {
  font-size: 13px;
  line-height: 1.7;
  color: var(--kb-text-secondary);
}

.parameter-badge {
  flex-shrink: 0;
  min-height: 36px;
  padding: 8px 12px;
  border-radius: 999px;
  background: var(--kb-accent-soft);
  color: var(--kb-text-primary);
  font-size: 12px;
  font-weight: 700;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field span {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-text-primary);
}

.field textarea,
.field input {
  width: 100%;
  min-height: 48px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  padding: 12px 14px;
  font-size: 14px;
  color: var(--kb-text-primary);
  background: var(--kb-surface-soft);
  outline: none;
  transition: border-color var(--kb-motion-fast) ease, box-shadow var(--kb-motion-fast) ease, background var(--kb-motion-fast) ease;
}

.field textarea:focus,
.field input:focus {
  border-color: var(--kb-border-strong);
  box-shadow: 0 0 0 4px rgba(23, 22, 20, 0.06);
  background: var(--kb-surface);
}

.field textarea {
  min-height: 110px;
  resize: vertical;
  line-height: 1.8;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.lab-actions {
  display: flex;
  justify-content: flex-end;
}

.run-btn {
  min-height: 44px;
  border: 1px solid var(--kb-accent);
  border-radius: var(--kb-radius-sm);
  padding: 12px 18px;
  background: var(--kb-accent);
  color: var(--kb-accent-contrast);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition:
    transform var(--kb-motion-fast) ease,
    box-shadow var(--kb-motion-fast) ease,
    opacity var(--kb-motion-fast) ease;
}

.run-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: var(--kb-shadow-xs);
}

.run-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.lab-error {
  padding: 14px 16px;
  border-radius: var(--kb-radius-md);
  border: 1px solid rgba(142, 78, 70, 0.16);
  background: var(--kb-danger-bg);
  color: var(--kb-danger-text);
  font-size: 14px;
}

.lab-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  border-radius: var(--kb-radius-md);
  padding: 16px 18px;
}

.stat-label {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--kb-text-tertiary);
}

.stat-card strong {
  font-size: 24px;
  color: var(--kb-text-primary);
  letter-spacing: -0.03em;
}

.lab-results {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 18px;
}

.results-panel,
.context-panel {
  padding: 20px;
  border-radius: var(--kb-radius-lg);
}

.panel-head,
.result-meta,
.result-scores {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.panel-head {
  align-items: center;
  margin-bottom: 16px;
}

.panel-head h4 {
  font-size: 16px;
  color: var(--kb-text-primary);
}

.panel-head span,
.result-meta span,
.result-scores span {
  font-size: 12px;
  color: var(--kb-text-tertiary);
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 720px;
  overflow-y: auto;
}

.result-card {
  padding: 14px 16px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-surface-soft);
  transition: transform var(--kb-motion-fast) ease, box-shadow var(--kb-motion-fast) ease;
}

.result-card:hover {
  transform: translateY(-1px);
  box-shadow: var(--kb-shadow-xs);
}

.result-meta {
  margin-bottom: 8px;
}

.result-meta strong {
  font-size: 14px;
  color: var(--kb-text-primary);
}

.result-scores {
  margin-bottom: 10px;
}

.result-text,
.context-box {
  font-size: 13px;
  line-height: 1.8;
  color: var(--kb-text-secondary);
  white-space: pre-wrap;
}

.context-box {
  min-height: 320px;
  max-height: 720px;
  overflow-y: auto;
  padding: 16px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-surface-soft);
  border: 1px solid rgba(23, 22, 20, 0.04);
}

.parameter-summary {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.summary-card {
  padding: 12px 14px;
  border-radius: var(--kb-radius-md);
  border: 1px solid rgba(23, 22, 20, 0.04);
  background: rgba(255, 255, 255, 0.72);
}

.summary-card span {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--kb-text-tertiary);
}

.summary-card strong {
  font-size: 15px;
  color: var(--kb-text-primary);
}

.panel-empty {
  font-size: 14px;
  line-height: 1.7;
  color: var(--kb-text-secondary);
}

@media (max-width: 1080px) {
  .lab-intro,
  .field-grid,
  .lab-stats,
  .lab-results {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .lab-intro,
  .lab-form-head {
    display: flex;
    flex-direction: column;
  }

  .parameter-summary {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .field textarea,
  .field input,
  .run-btn,
  .result-card {
    transition: none;
  }
}
</style>
