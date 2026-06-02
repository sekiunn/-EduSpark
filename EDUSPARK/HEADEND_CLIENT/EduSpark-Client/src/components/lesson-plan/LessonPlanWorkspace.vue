<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Extension } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { DOMParser as ProseMirrorDOMParser, Fragment, Slice } from '@tiptap/pm/model'
import { Decoration, DecorationSet } from '@tiptap/pm/view'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Highlight from '@tiptap/extension-highlight'
import Placeholder from '@tiptap/extension-placeholder'
import {
  ArrowUp,
  BookOpen,
  Bold,
  Download,
  Eraser,
  Heading2,
  Highlighter,
  Italic,
  List,
  LoaderCircle,
  Save,
  Sparkles,
  X
} from 'lucide-vue-next'
import { markdownToPlainText, renderMarkdown } from '@/utils/markdown.js'

const EMPTY_EDITOR_HTML = '<p></p>'
const persistentSelectionHighlightKey = new PluginKey('persistentSelectionHighlight')

const PersistentSelectionHighlight = Extension.create({
  name: 'persistentSelectionHighlight',
  addProseMirrorPlugins() {
    return [
      new Plugin({
        key: persistentSelectionHighlightKey,
        state: {
          init: () => null,
          apply(tr, value) {
            const meta = tr.getMeta(persistentSelectionHighlightKey)
            if (meta && Object.prototype.hasOwnProperty.call(meta, 'range')) {
              return meta.range
            }

            if (!value || !tr.docChanged) return value

            const from = tr.mapping.map(value.from)
            const to = tr.mapping.map(value.to)
            return from < to ? { from, to } : null
          }
        },
        props: {
          decorations(state) {
            const range = persistentSelectionHighlightKey.getState(state)
            if (!range?.from || !range?.to || range.from >= range.to) return null

            const maxPos = state.doc.content.size
            if (maxPos <= 0) return null

            const from = Math.max(1, Math.min(range.from, maxPos))
            const to = Math.max(from, Math.min(range.to, maxPos))
            if (from >= to) return null

            return DecorationSet.create(state.doc, [
              Decoration.inline(from, to, {
                class: 'persistent-selection-highlight'
              })
            ])
          }
        }
      })
    ]
  }
})

const props = defineProps({
  document: { type: Object, default: () => null },
  content: { type: String, default: '' },
  streamText: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  exporting: { type: Boolean, default: false },
  rewriting: { type: Boolean, default: false },
  rewriteSuggestion: { type: Object, default: () => null },
  dirty: { type: Boolean, default: false },
  streamConnected: { type: Boolean, default: false },
  streamError: { type: String, default: '' },
  downloadUrl: { type: String, default: '' }
})

const emit = defineEmits([
  'close',
  'save',
  'export',
  'update:content',
  'request-rewrite',
  'dismiss-rewrite'
])

const ui = {
  untitled: '\u6559\u6848\u521d\u7a3f',
  closeAria: '\u5173\u95ed\u5de5\u4f5c\u533a',
  readySummary: '\u5f53\u524d\u5de5\u4f5c\u533a\u53ea\u4fdd\u7559\u6700\u7ec8\u6559\u6848\u6b63\u6587\uff0c\u770b\u5230\u7684\u7248\u5f0f\u5c31\u662f\u53ef\u76f4\u63a5\u4fee\u6539\u7684\u5185\u5bb9\u3002',
  generatingSummary: '\u6b63\u5728\u751f\u6210\u6559\u6848\u6b63\u6587\uff0c\u5b8c\u6210\u540e\u4f1a\u76f4\u63a5\u5207\u6362\u4e3a\u53ef\u7f16\u8f91\u6587\u6863\u3002',
  save: '\u4fdd\u5b58',
  saving: '\u4fdd\u5b58\u4e2d...',
  export: '\u5bfc\u51fa\u4e0b\u8f7d',
  exporting: '\u5bfc\u51fa\u4e2d...',
  outlineTitle: '\u6b63\u6587\u76ee\u5f55',
  outlineEmpty: '\u6b63\u6587\u751f\u6210\u540e\uff0c\u8fd9\u91cc\u4f1a\u81ea\u52a8\u63d0\u53d6\u76ee\u5f55\u3002',
  emptyBody: '\u6559\u6848\u6b63\u6587\u4f1a\u5728\u8fd9\u91cc\u5c55\u5f00\u3002',
  emptyBodyError: '\u5f53\u524d\u6559\u6848\u6b63\u6587\u4e3a\u7a7a\uff0c\u8bf7\u91cd\u65b0\u751f\u6210\u3002',
  generatingNotice: '\u6b63\u5728\u751f\u6210\u6b63\u6587\uff0c\u5b8c\u6210\u540e\u4f1a\u76f4\u63a5\u5728\u5f53\u524d\u6587\u6863\u4e2d\u7ee7\u7eed\u7f16\u8f91\u3002',
  bold: '\u52a0\u7c97',
  italic: '\u659c\u4f53',
  highlight: '\u9ad8\u4eae',
  h2: 'H2',
  list: '\u5217\u8868',
  clearFormat: '\u6e05\u9664\u6837\u5f0f',
  rewriteAction: 'AI \u6539\u5199\u9009\u533a',
  rewritingAction: 'AI \u6539\u5199\u4e2d...',
  rewriteTitle: 'AI \u6539\u5199\u6307\u4ee4',
  rewriteHint: '\u8bf7\u5148\u5728\u6b63\u6587\u7f16\u8f91\u533a\u91cc\u9009\u4e2d\u9700\u8981 AI \u6539\u5199\u7684\u5185\u5bb9\u3002',
  rewritePlaceholder: '\u8bf4\u8bf4\u4f60\u60f3\u600e\u4e48\u6539\u5199',
  generateRewrite: '\u751f\u6210\u6539\u5199\u5efa\u8bae',
  generatingRewrite: '\u751f\u6210\u5efa\u8bae\u4e2d...',
  cancel: '\u53d6\u6d88',
  rewriteSuggestionTitle: 'AI \u6539\u5199\u5efa\u8bae',
  selectedLabel: '\u539f\u7247\u6bb5',
  suggestionLabel: '\u6539\u5199\u7ed3\u679c',
  instructionLabel: '\u6307\u4ee4',
  acceptRewrite: '\u63a5\u53d7\u66ff\u6362',
  dismissRewrite: '\u5148\u4e0d\u66ff\u6362',
  rewriteShortcut: 'Enter \u63d0\u4ea4\uff0cShift + Enter \u6362\u884c',
  rewriteSuggestionHint: '\u6839\u636e\u4f60\u7684\u6307\u4ee4\u751f\u6210\uff0c\u53ef\u4ee5\u76f4\u63a5\u66ff\u6362',
  rewriteClose: '\u5173\u95ed AI \u6539\u5199',
  rewriteSubmit: '\u63d0\u4ea4\u6539\u5199',
  unsaved: '\u6709\u672a\u4fdd\u5b58\u4fee\u6539',
  synced: '\u5185\u5bb9\u5df2\u540c\u6b65',
  generatingStage: '\u751f\u6210\u9636\u6bb5\u4e2d'
}

const previewRef = ref(null)
const documentSurfaceRef = ref(null)
const selectionToolbarRef = ref(null)
const rewriteComposerRef = ref(null)
const rewriteSuggestionRef = ref(null)
const rewriteInputRef = ref(null)
const markdownDraft = ref('')
const selection = ref({ from: 0, to: 0, text: '' })
const rewriteTarget = ref(null)
const rewriteInstruction = ref('')
const rewritePanelVisible = ref(false)
const knowledgeMenuRef = ref(null)
const knowledgeMenuOpen = ref(false)
const activeKnowledgeKey = ref('')
const currentHighlightColor = ref('')
const preferredHighlightColor = ref('#fef08a')
const suppressEditorSync = ref(false)
const selectionToolbarPosition = ref({ top: 18, left: 18, ready: false })
const selectionToolbarPlacement = ref('bottom')
const rewritePopoverPosition = ref({ top: 18, left: 18, ready: false })
const rewritePopoverPlacement = ref('bottom')

const REWRITE_POPOVER_MARGIN = 18
const HIGHLIGHT_COLOR_OPTIONS = [
  { label: '暖黄', value: '#fef08a' },
  { label: '杏橙', value: '#fdba74' },
  { label: '薄荷绿', value: '#86efac' },
  { label: '青柠绿', value: '#bef264' },
  { label: '湖蓝', value: '#7dd3fc' },
  { label: '雾蓝', value: '#93c5fd' },
  { label: '淡紫', value: '#c4b5fd' },
  { label: '粉莓', value: '#f9a8d4' }
]

const isCompleted = computed(() => props.document?.status === 'completed')
const sourceMarkdown = computed(() => (
  isCompleted.value ? markdownDraft.value : (props.streamText || props.document?.content || '')
))
const hasDocumentBody = computed(() => Boolean(markdownToPlainText(sourceMarkdown.value)))
const hasActiveSelection = computed(() => (
  isCompleted.value &&
  selection.value.from < selection.value.to &&
  Boolean(selection.value.text)
))
const canSave = computed(() => isCompleted.value && !props.loading && !props.saving && props.dirty)
const canExport = computed(() =>
  isCompleted.value &&
  !props.loading &&
  !props.exporting &&
  Boolean(props.document?.content || props.content)
)
const canRequestRewrite = computed(() =>
  isCompleted.value &&
  Boolean(selection.value.text) &&
  !props.loading &&
  !props.saving
)
const canSubmitRewrite = computed(() =>
  Boolean(rewriteTarget.value?.text) &&
  Boolean(rewriteInstruction.value.trim()) &&
  !props.rewriting
)
const showSelectionToolbar = computed(() =>
  hasActiveSelection.value &&
  !showRewriteComposer.value &&
  !showRewriteSuggestion.value
)
const showRewriteComposer = computed(() =>
  Boolean(rewriteTarget.value?.text) &&
  (rewritePanelVisible.value || (props.rewriting && !props.rewriteSuggestion))
)
const showRewriteSuggestion = computed(() =>
  Boolean(props.rewriteSuggestion) &&
  !rewritePanelVisible.value &&
  Boolean(rewriteTarget.value?.text)
)
const suggestionText = computed(() => props.rewriteSuggestion?.suggestion || '')
const activeHighlightColor = computed(() => (
  currentHighlightColor.value || preferredHighlightColor.value || '#fef08a'
))
const selectionToolbarStyle = computed(() => ({
  top: `${selectionToolbarPosition.value.top}px`,
  left: `${selectionToolbarPosition.value.left}px`,
  opacity: selectionToolbarPosition.value.ready ? 1 : 0,
  pointerEvents: selectionToolbarPosition.value.ready ? 'auto' : 'none'
}))
const rewritePopoverStyle = computed(() => ({
  top: `${rewritePopoverPosition.value.top}px`,
  left: `${rewritePopoverPosition.value.left}px`,
  opacity: rewritePopoverPosition.value.ready ? 1 : 0,
  pointerEvents: rewritePopoverPosition.value.ready ? 'auto' : 'none'
}))
const workspaceTitle = computed(() => props.document?.title || ui.untitled)
const workspaceSummary = computed(() => props.document?.summary || (
  isCompleted.value ? ui.readySummary : ui.generatingSummary
))
const exportActionText = computed(() => (props.downloadUrl ? '\u4e0b\u8f7d\u6587\u4ef6' : ui.export))
const footerText = computed(() => {
  if (!isCompleted.value) return ui.generatingStage
  return props.dirty ? ui.unsaved : ui.synced
})
const banner = computed(() => {
  if (props.document?.errorMessage) {
    return { tone: 'error', text: props.document.errorMessage }
  }
  if (props.streamError) {
    return { tone: 'error', text: props.streamError }
  }
  if (isCompleted.value && !hasDocumentBody.value) {
    return { tone: 'error', text: ui.emptyBodyError }
  }
  if (!isCompleted.value) {
    return { tone: 'info', text: ui.generatingNotice }
  }
  return null
})
const knowledgeSources = computed(() => {
  const items = props.document?.knowledgeSources
  return Array.isArray(items) ? items.filter(Boolean) : []
})
const knowledgeAttachmentNames = computed(() => {
  const names = props.document?.enrichedBlueprint?.attachmentNames
  return Array.isArray(names)
    ? names
        .map(item => (typeof item === 'string' ? item.trim() : ''))
        .filter(Boolean)
    : []
})
const knowledgeHighlights = computed(() => {
  const items = props.document?.enrichmentHighlights
  return Array.isArray(items)
    ? items
        .map(item => (typeof item === 'string' ? item.trim() : ''))
        .filter(Boolean)
    : []
})
const knowledgeDocuments = computed(() => {
  const grouped = new Map()

  const ensureDocument = ({ fileId = null, fileName = '', excerpt = '', score = null }) => {
    const safeName = (fileName || '').trim()
    if (!safeName) return

    const key = fileId ? `file-${fileId}` : `name-${safeName}`
    if (!grouped.has(key)) {
      grouped.set(key, {
        key,
        fileId,
        fileName: safeName,
        bestScore: typeof score === 'number' ? score : null,
        excerpts: []
      })
    }

    const target = grouped.get(key)
    if (typeof score === 'number' && !Number.isNaN(score)) {
      target.bestScore = target.bestScore == null ? score : Math.max(target.bestScore, score)
    }

    const safeExcerpt = (excerpt || '').trim()
    if (safeExcerpt && !target.excerpts.includes(safeExcerpt)) {
      target.excerpts.push(safeExcerpt)
    }
  }

  knowledgeSources.value.forEach((item) => {
    ensureDocument({
      fileId: item.fileId ?? null,
      fileName: item.fileName || '',
      excerpt: item.excerpt || '',
      score: typeof item.score === 'number' ? item.score : null
    })
  })

  knowledgeAttachmentNames.value.forEach((fileName) => {
    ensureDocument({ fileName })
  })

  return [...grouped.values()].sort((left, right) => {
    const scoreGap = (right.bestScore ?? -1) - (left.bestScore ?? -1)
    if (Math.abs(scoreGap) > Number.EPSILON) return scoreGap
    return left.fileName.localeCompare(right.fileName, 'zh-CN')
  })
})
const hasKnowledgeEvidence = computed(() =>
  knowledgeDocuments.value.length > 0 || knowledgeHighlights.value.length > 0
)
const activeKnowledgeDocument = computed(() => {
  if (!knowledgeDocuments.value.length) return null
  return knowledgeDocuments.value.find(item => item.key === activeKnowledgeKey.value) || knowledgeDocuments.value[0]
})
const activeKnowledgeBasis = computed(() => {
  const doc = activeKnowledgeDocument.value
  const basis = []

  if (doc?.bestScore != null) {
    basis.push(`检索命中得分：${doc.bestScore.toFixed(3)}`)
  }
  if (doc && knowledgeAttachmentNames.value.includes(doc.fileName)) {
    basis.push('该文档已纳入本次教案的参考资料上下文')
  }
  if (doc?.excerpts?.length) {
    basis.push(`共采用 ${doc.excerpts.length} 段片段参与蓝图补强与最终写作`)
  }
  if (knowledgeHighlights.value.length) {
    basis.push(...knowledgeHighlights.value.slice(0, 4))
  }

  return basis.length ? [...new Set(basis)] : ['该资料已参与本次教案生成过程']
})
const buildOutline = (markdown) => {
  if (!markdown) return []

  let index = 0
  return markdown
    .split('\n')
    .map((line) => {
      const match = line.match(/^(#{1,3})\s+(.+?)\s*$/)
      if (!match) return null
      const domIndex = index
      index += 1
      return {
        id: `outline-${index}`,
        domIndex,
        level: match[1].length,
        text: match[2].replace(/[*_`~=]/g, '').trim()
      }
    })
    .filter(Boolean)
}

const outlineItems = computed(() => buildOutline(sourceMarkdown.value))

const decoratedMarkdown = computed(() => {
  if (!sourceMarkdown.value) return ''

  let outlineIndex = 0
  return sourceMarkdown.value
    .split('\n')
    .map((line) => {
      const match = line.match(/^(#{1,3})\s+(.+?)\s*$/)
      if (!match) return line
      outlineIndex += 1
      return `${match[1]} <span data-outline-id="outline-${outlineIndex}">${match[2]}</span>`
    })
    .join('\n')
})

const previewHtml = computed(() => renderMarkdown(decoratedMarkdown.value))

const normalizeHighlightColor = (value) => (
  typeof value === 'string' ? value.trim().toLowerCase() : ''
)

const applyMarks = (text, marks = []) => {
  if (!text) return ''
  const names = new Set(marks.map(mark => mark.type.name))
  if (names.has('code')) return `\`${text}\``

  let result = text
  const highlightMark = marks.find(mark => mark.type.name === 'highlight')
  if (highlightMark) {
    const color = normalizeHighlightColor(highlightMark.attrs?.color)
    result = color ? `=={${color}}${result}==` : `==${result}==`
  }
  if (names.has('bold')) result = `**${result}**`
  if (names.has('italic')) result = `*${result}*`
  if (names.has('strike')) result = `~~${result}~~`
  return result
}

const indentLines = (text, prefix = '  ') => (
  text
    .split('\n')
    .map(line => (line ? `${prefix}${line}` : line))
    .join('\n')
)

const serializeInlineNode = (node) => {
  const type = node.type?.name
  if (type === 'text') return applyMarks(node.text || '', node.marks || [])
  if (type === 'hardBreak') return '  \n'
  if (node.content?.content?.length) {
    return node.content.content.map(child => serializeInlineNode(child)).join('')
  }
  return ''
}

const serializeInlineContent = (node) => (
  (node.content?.content || []).map(child => serializeInlineNode(child)).join('').trim()
)

const prefixListLines = (text, marker) => {
  const lines = text.split('\n')
  return lines
    .map((line, index) => {
      if (index === 0) return `${marker}${line}`
      return line ? `  ${line}` : line
    })
    .join('\n')
}

const serializeBlock = (node) => {
  const type = node.type?.name

  switch (type) {
    case 'paragraph':
      return serializeInlineContent(node)
    case 'heading':
      return `${'#'.repeat(node.attrs?.level || 1)} ${serializeInlineContent(node)}`.trim()
    case 'bulletList':
    case 'orderedList':
      return serializeList(node)
    case 'blockquote': {
      const body = serializeBlocks(node.content?.content || [])
      return body
        .split('\n')
        .map(line => (line ? `> ${line}` : '>'))
        .join('\n')
    }
    case 'codeBlock': {
      const language = node.attrs?.language || ''
      return `\`\`\`${language}\n${node.textContent || ''}\n\`\`\``
    }
    case 'horizontalRule':
      return '---'
    default:
      if (node.content?.content?.length) {
        return serializeBlocks(node.content.content)
      }
      return ''
  }
}

const serializeListItem = (node, marker) => {
  const blocks = []

  ;(node.content?.content || []).forEach((child) => {
    const type = child.type?.name
    if (type === 'paragraph') {
      blocks.push(serializeInlineContent(child))
      return
    }
    if (type === 'bulletList' || type === 'orderedList') {
      const nested = serializeList(child)
      if (nested) blocks.push(indentLines(nested))
      return
    }

    const serialized = serializeBlock(child)
    if (serialized) blocks.push(indentLines(serialized))
  })

  if (!blocks.length) return marker.trimEnd()
  return prefixListLines(blocks.join('\n'), marker)
}

const serializeList = (node) => {
  const ordered = node.type?.name === 'orderedList'
  const start = Number(node.attrs?.start || 1)
  return (node.content?.content || [])
    .map((item, index) => serializeListItem(item, ordered ? `${start + index}. ` : '- '))
    .join('\n')
}

function serializeBlocks(nodes) {
  return nodes
    .map(node => serializeBlock(node))
    .filter(Boolean)
    .join('\n\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function serializeSelectionContent(editorInstance = null) {
  const instance = editorInstance || editor.value
  if (!instance) return ''

  const { selection: currentSelection } = instance.state
  if (!currentSelection || currentSelection.empty) return ''

  const sliceNodes = currentSelection.content().content?.content || []
  if (!sliceNodes.length) return ''

  const hasBlockNode = sliceNodes.some(node => node.isBlock)
  if (!hasBlockNode) {
    return sliceNodes.map(node => serializeInlineNode(node)).join('').trim()
  }

  return serializeBlocks(sliceNodes)
}

function collectTextNodes(nodeOrFragment, bucket = []) {
  if (!nodeOrFragment) return bucket

  if (nodeOrFragment.isText && nodeOrFragment.text) {
    bucket.push(nodeOrFragment)
    return bucket
  }

  if (typeof nodeOrFragment.forEach === 'function') {
    nodeOrFragment.forEach((child) => {
      collectTextNodes(child, bucket)
    })
  } else if (nodeOrFragment.content) {
    collectTextNodes(nodeOrFragment.content, bucket)
  }

  return bucket
}

function resolveCommonMarks(textNodes = []) {
  if (!textNodes.length) return []

  return textNodes[0].marks.filter(mark => (
    textNodes.every(node => node.marks.some(candidate => candidate.eq(mark)))
  ))
}

function mergeInheritedMarks(existingMarks = [], inheritedMarks = []) {
  return inheritedMarks.reduce((marks, mark) => mark.addToSet(marks), existingMarks)
}

function mapFragmentNodes(fragment, transform) {
  const nextChildren = []

  fragment.forEach((child) => {
    nextChildren.push(transform(child))
  })

  return Fragment.fromArray(nextChildren)
}

function applyInheritedMarksToNode(node, inheritedMarks = []) {
  if (!inheritedMarks.length) return node

  if (node.isText) {
    return node.mark(mergeInheritedMarks(node.marks, inheritedMarks))
  }

  if (!node.content?.size) return node
  return node.copy(applyInheritedMarksToFragment(node.content, inheritedMarks))
}

function applyInheritedMarksToFragment(fragment, inheritedMarks = []) {
  if (!inheritedMarks.length) return fragment
  return mapFragmentNodes(fragment, child => applyInheritedMarksToNode(child, inheritedMarks))
}

function parseMarkdownToSlice(markdown, editorInstance = null) {
  const instance = editorInstance || editor.value
  if (!instance) return null

  const html = renderMarkdown(markdown || '')
  const host = globalThis.document?.createElement('div')
  if (!host) return null

  host.innerHTML = html || `<p>${markdown || ''}</p>`
  return ProseMirrorDOMParser.fromSchema(instance.schema).parseSlice(host, {
    preserveWhitespace: true
  })
}

function getRewriteSelectionProfile(editorInstance = null) {
  const instance = editorInstance || editor.value
  if (!instance) return null

  const { selection: currentSelection } = instance.state
  if (!currentSelection || currentSelection.empty) return null

  const slice = currentSelection.content()
  const textNodes = collectTextNodes(slice.content)

  return {
    sameParentTextblock:
      currentSelection.$from.sameParent(currentSelection.$to) &&
      currentSelection.$from.parent.isTextblock,
    commonMarks: resolveCommonMarks(textNodes),
    topLevelTypes: (slice.content?.content || []).map(node => node.type?.name || ''),
    openStart: slice.openStart,
    openEnd: slice.openEnd
  }
}

function syncHighlightState(editorInstance = null) {
  const instance = editorInstance || editor.value
  if (!instance || !isCompleted.value) {
    currentHighlightColor.value = ''
    return
  }

  const color = normalizeHighlightColor(instance.getAttributes('highlight')?.color)
  currentHighlightColor.value = color
  if (color) {
    preferredHighlightColor.value = color
  }
}

function syncSelectionState(editorInstance = null) {
  const instance = editorInstance || editor.value
  if (!instance || !isCompleted.value) {
    selection.value = { from: 0, to: 0, text: '' }
    currentHighlightColor.value = ''
    return
  }

  const { from, to, empty } = instance.state.selection
  const serializedSelection = empty ? '' : serializeSelectionContent(instance)
  selection.value = {
    from,
    to,
    text: serializedSelection || (empty ? '' : instance.state.doc.textBetween(from, to, '\n').trim())
  }
  syncHighlightState(instance)
}

function syncRewriteInputHeight() {
  const element = rewriteInputRef.value
  if (!element) return

  element.style.height = '0px'
  element.style.height = `${Math.min(Math.max(element.scrollHeight, 72), 168)}px`
}

function syncPersistentSelectionHighlight(editorInstance = null) {
  const instance = editorInstance || editor.value
  if (!instance) return

  const target = rewriteTarget.value
  const range = target?.from < target?.to
    ? { from: target.from, to: target.to }
    : null

  instance.view.dispatch(
    instance.state.tr
      .setMeta('addToHistory', false)
      .setMeta(persistentSelectionHighlightKey, { range })
  )
}

function getRangeAnchor(range, editorInstance = null) {
  const instance = editorInstance || editor.value
  const surface = documentSurfaceRef.value

  if (!instance || !surface || !range?.from || !range?.to || range.from >= range.to) return null

  try {
    const start = instance.view.coordsAtPos(range.from)
    const end = instance.view.coordsAtPos(range.to)
    const surfaceRect = surface.getBoundingClientRect()
    const leftEdge = Math.min(start.left, start.right, end.left, end.right)
    const rightEdge = Math.max(start.left, start.right, end.left, end.right)

    return {
      top: Math.min(start.top, end.top) - surfaceRect.top,
      bottom: Math.max(start.bottom, end.bottom) - surfaceRect.top,
      centerX: ((leftEdge + rightEdge) / 2) - surfaceRect.left
    }
  } catch {
    return null
  }
}

function getSelectionAnchor(editorInstance = null) {
  const instance = editorInstance || editor.value
  const { from, to, empty } = instance?.state?.selection || {}
  if (!instance || empty || from >= to) return null
  return getRangeAnchor({ from, to }, instance)
}

function getRewriteAnchor(editorInstance = null) {
  const target = rewriteTarget.value
  if (!target?.text) return null
  return getRangeAnchor({ from: target.from, to: target.to }, editorInstance)
}

function resetSelectionToolbarUi() {
  selectionToolbarPlacement.value = 'bottom'
  selectionToolbarPosition.value = { top: 18, left: 18, ready: false }
}

async function updateSelectionToolbarPosition(editorInstance = null) {
  const surface = documentSurfaceRef.value
  if (!surface) return

  await nextTick()

  const popover = selectionToolbarRef.value
  if (!popover) return

  const anchor = getSelectionAnchor(editorInstance)
  if (!anchor) {
    resetSelectionToolbarUi()
    return
  }

  const popoverWidth = popover.offsetWidth || 360
  const popoverHeight = popover.offsetHeight || 0
  const surfaceWidth = surface.clientWidth || 0
  const surfaceHeight = surface.scrollHeight || surface.clientHeight || 0

  let left = anchor.centerX - popoverWidth / 2
  const maxLeft = Math.max(REWRITE_POPOVER_MARGIN, surfaceWidth - popoverWidth - REWRITE_POPOVER_MARGIN)
  left = Math.min(Math.max(left, REWRITE_POPOVER_MARGIN), maxLeft)

  let top = anchor.bottom + 16
  let placement = 'bottom'
  if (
    top + popoverHeight > surfaceHeight - REWRITE_POPOVER_MARGIN &&
    anchor.top - popoverHeight - 16 >= REWRITE_POPOVER_MARGIN
  ) {
    top = anchor.top - popoverHeight - 16
    placement = 'top'
  }

  selectionToolbarPlacement.value = placement
  selectionToolbarPosition.value = {
    top,
    left,
    ready: true
  }
}

async function updateRewritePopoverPosition(editorInstance = null) {
  const surface = documentSurfaceRef.value
  if (!surface) return

  await nextTick()

  const popover = showRewriteSuggestion.value
    ? rewriteSuggestionRef.value
    : rewriteComposerRef.value
  if (!popover) return

  const anchor = getRewriteAnchor(editorInstance)
  if (!anchor) {
    rewritePopoverPlacement.value = 'bottom'
    rewritePopoverPosition.value = {
      top: REWRITE_POPOVER_MARGIN,
      left: REWRITE_POPOVER_MARGIN,
      ready: true
    }
    return
  }

  const popoverWidth = popover.offsetWidth || 420
  const popoverHeight = popover.offsetHeight || 0
  const surfaceWidth = surface.clientWidth || 0
  const surfaceHeight = surface.scrollHeight || surface.clientHeight || 0

  let left = anchor.centerX - popoverWidth / 2
  const maxLeft = Math.max(REWRITE_POPOVER_MARGIN, surfaceWidth - popoverWidth - REWRITE_POPOVER_MARGIN)
  left = Math.min(Math.max(left, REWRITE_POPOVER_MARGIN), maxLeft)

  let top = anchor.bottom + 18
  let placement = 'bottom'
  if (
    top + popoverHeight > surfaceHeight - REWRITE_POPOVER_MARGIN &&
    anchor.top - popoverHeight - 18 >= REWRITE_POPOVER_MARGIN
  ) {
    top = anchor.top - popoverHeight - 18
    placement = 'top'
  }

  rewritePopoverPlacement.value = placement
  rewritePopoverPosition.value = {
    top,
    left,
    ready: true
  }
}

function resetRewriteUi({ keepSuggestion = false } = {}) {
  rewriteTarget.value = null
  rewriteInstruction.value = ''
  rewritePanelVisible.value = false
  resetSelectionToolbarUi()
  rewritePopoverPlacement.value = 'bottom'
  rewritePopoverPosition.value = { top: 18, left: 18, ready: false }
  if (!keepSuggestion && props.rewriteSuggestion) {
    emit('dismiss-rewrite')
  }
}

function toggleKnowledgeMenu() {
  if (!hasKnowledgeEvidence.value) return
  knowledgeMenuOpen.value = !knowledgeMenuOpen.value
}

function selectKnowledgeDocument(item) {
  activeKnowledgeKey.value = item.key
}

function closeKnowledgeMenu() {
  knowledgeMenuOpen.value = false
}

function handleGlobalPointerDown(event) {
  const target = event.target
  const targetElement = target instanceof Element ? target : null

  if (knowledgeMenuOpen.value) {
    if (!knowledgeMenuRef.value?.contains(target)) {
      closeKnowledgeMenu()
    } else {
      return
    }
  }

  if (
    rewritePanelVisible.value &&
    !props.rewriting &&
    rewriteComposerRef.value &&
    !rewriteComposerRef.value.contains(target) &&
    !targetElement?.closest('[data-rewrite-trigger="true"]')
  ) {
    resetRewriteUi({ keepSuggestion: true })
  }
}

const emitContent = () => {
  emit('update:content', markdownDraft.value)
}

const handleEditorStateChange = (editorInstance) => {
  if (suppressEditorSync.value) return
  markdownDraft.value = serializeBlocks(editorInstance.state.doc.content.content || [])
  emitContent()
  syncSelectionState(editorInstance)
}

const editor = useEditor({
  extensions: [
    StarterKit.configure({
      heading: { levels: [1, 2, 3] }
    }),
    Highlight.configure({
      multicolor: true
    }),
    Placeholder.configure({
      placeholder: ui.emptyBody
    }),
    PersistentSelectionHighlight
  ],
  content: EMPTY_EDITOR_HTML,
  editable: false,
  editorProps: {
    attributes: {
      class: 'document-editor-prose document-prose markdown-prose'
    }
  },
  onCreate: ({ editor: instance }) => {
    instance.setEditable(isCompleted.value)
    syncSelectionState(instance)
    syncPersistentSelectionHighlight(instance)
  },
  onUpdate: ({ editor: instance }) => {
    handleEditorStateChange(instance)
  },
  onSelectionUpdate: ({ editor: instance }) => {
    syncSelectionState(instance)
    if (!rewriteTarget.value?.text) {
      void updateSelectionToolbarPosition(instance)
    }
  }
})

const syncEditorFromMarkdown = async (value, { force = false } = {}) => {
  const nextMarkdown = value || ''
  if (!force && nextMarkdown === markdownDraft.value) return

  markdownDraft.value = nextMarkdown
  const instance = editor.value
  if (!instance) return
  if (!force && instance.isFocused) return

  suppressEditorSync.value = true
  instance.commands.setContent(nextMarkdown ? renderMarkdown(nextMarkdown) : EMPTY_EDITOR_HTML, false)
  await nextTick()
  suppressEditorSync.value = false
  syncSelectionState(instance)
}

const runEditorCommand = (callback) => {
  if (!isCompleted.value || !editor.value) return
  callback(editor.value.chain().focus())
  syncSelectionState(editor.value)
  if (!rewriteTarget.value?.text) {
    void updateSelectionToolbarPosition(editor.value)
  }
}

const toggleBold = () => runEditorCommand(chain => chain.toggleBold().run())
const toggleItalic = () => runEditorCommand(chain => chain.toggleItalic().run())
const applyHighlightColor = (color = '#fef08a') => {
  const normalizedColor = normalizeHighlightColor(color) || '#fef08a'
  preferredHighlightColor.value = normalizedColor
  runEditorCommand(chain => chain.setHighlight({ color: normalizedColor }).run())
}
const applyPreferredHighlight = () => applyHighlightColor(preferredHighlightColor.value)
const toggleHeading = () => runEditorCommand(chain => chain.toggleHeading({ level: 2 }).run())
const toggleBulletList = () => runEditorCommand(chain => chain.toggleBulletList().run())
const clearFormatting = () => runEditorCommand(chain => chain.unsetAllMarks().clearNodes().run())

const openRewritePanel = async () => {
  syncSelectionState()
  if (!canRequestRewrite.value) return

  rewriteTarget.value = {
    ...selection.value,
    profile: getRewriteSelectionProfile()
  }
  rewriteInstruction.value = ''
  rewritePanelVisible.value = true
  rewritePopoverPosition.value = { top: 18, left: 18, ready: false }
  if (props.rewriteSuggestion) {
    emit('dismiss-rewrite')
  }

  await nextTick()
  syncRewriteInputHeight()
  await updateRewritePopoverPosition()
  rewriteInputRef.value?.focus()
}

const submitRewriteRequest = () => {
  if (!canSubmitRewrite.value) return

  emit('request-rewrite', {
    selectedText: rewriteTarget.value.text,
    instruction: rewriteInstruction.value.trim()
  })
}

const handleRewriteInstructionKeydown = (event) => {
  if (event.key === 'Escape') {
    event.preventDefault()
    resetRewriteUi({ keepSuggestion: true })
    return
  }

  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    submitRewriteRequest()
  }
}

const dismissRewriteSuggestion = () => {
  resetRewriteUi()
}

function buildRewriteReplacementSlice(suggestionMarkdown, editorInstance = null) {
  const instance = editorInstance || editor.value
  if (!instance || !rewriteTarget.value) return null

  const parsedSlice = parseMarkdownToSlice(suggestionMarkdown, instance)
  if (!parsedSlice) return null

  const profile = rewriteTarget.value.profile
  if (!profile?.sameParentTextblock || !profile.commonMarks?.length) {
    return parsedSlice
  }

  return new Slice(
    applyInheritedMarksToFragment(parsedSlice.content, profile.commonMarks),
    parsedSlice.openStart,
    parsedSlice.openEnd
  )
}

const acceptRewriteSuggestion = () => {
  if (!rewriteTarget.value || !suggestionText.value || !editor.value) return

  const replacementSlice = buildRewriteReplacementSlice(suggestionText.value, editor.value)
  if (!replacementSlice) return

  editor.value.view.focus()
  editor.value.view.dispatch(
    editor.value.state.tr
      .replaceRange(rewriteTarget.value.from, rewriteTarget.value.to, replacementSlice)
      .scrollIntoView()
  )

  resetRewriteUi()
  syncSelectionState(editor.value)
}

function handleWindowResize() {
  if (showSelectionToolbar.value) {
    void updateSelectionToolbarPosition()
  }
  if (!showRewriteComposer.value && !showRewriteSuggestion.value) return
  void updateRewritePopoverPosition()
}

const scrollToOutline = async (item) => {
  await nextTick()

  if (isCompleted.value) {
    const headings = previewRef.value?.querySelectorAll('.ProseMirror h1, .ProseMirror h2, .ProseMirror h3')
    headings?.[item.domIndex]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }

  const target = previewRef.value?.querySelector(`[data-outline-id="${item.id}"]`)
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

watch(
  () => props.document?.documentId,
  () => {
    syncEditorFromMarkdown(props.content, { force: true })
    resetRewriteUi()
    closeKnowledgeMenu()
  }
)

watch(
  () => props.content,
  (value) => {
    syncEditorFromMarkdown(value)
  }
)

watch(
  () => props.dirty,
  (value) => {
    if (!value && isCompleted.value) {
      syncEditorFromMarkdown(props.content, { force: true })
    }
  }
)

watch(
  isCompleted,
  async (value) => {
    if (editor.value) {
      editor.value.setEditable(value)
    }

    if (value) {
      await syncEditorFromMarkdown(props.content, { force: true })
    } else {
      selection.value = { from: 0, to: 0, text: '' }
      resetSelectionToolbarUi()
      resetRewriteUi()
    }
  },
  { immediate: true }
)

watch(
  () => props.rewriteSuggestion,
  async (value) => {
    if (value) {
      rewritePanelVisible.value = false
      await updateRewritePopoverPosition()
    }
  }
)

watch(
  showSelectionToolbar,
  async (value) => {
    if (!value) {
      resetSelectionToolbarUi()
      return
    }
    await updateSelectionToolbarPosition()
  }
)

watch(
  rewriteInstruction,
  async () => {
    await nextTick()
    syncRewriteInputHeight()
    if (showRewriteComposer.value) {
      await updateRewritePopoverPosition()
    }
  }
)

watch(
  showRewriteComposer,
  async (value) => {
    if (!value) return
    await nextTick()
    syncRewriteInputHeight()
    await updateRewritePopoverPosition()
    if (!props.rewriting) {
      rewriteInputRef.value?.focus()
    }
  }
)

watch(
  showRewriteSuggestion,
  async (value) => {
    if (!value) return
    await updateRewritePopoverPosition()
  }
)

watch(
  knowledgeDocuments,
  (documents) => {
    if (!documents.length) {
      activeKnowledgeKey.value = ''
      closeKnowledgeMenu()
      return
    }

    if (!documents.some(item => item.key === activeKnowledgeKey.value)) {
      activeKnowledgeKey.value = documents[0].key
    }
  },
  { immediate: true }
)

watch(
  editor,
  (instance) => {
    if (!instance) return
    instance.setEditable(isCompleted.value)
    syncEditorFromMarkdown(props.content, { force: true })
    syncPersistentSelectionHighlight(instance)
  },
  { immediate: true }
)

watch(
  [
    editor,
    () => rewriteTarget.value?.from ?? 0,
    () => rewriteTarget.value?.to ?? 0,
    () => rewriteTarget.value?.text ?? ''
  ],
  ([instance]) => {
    if (!instance) return
    syncPersistentSelectionHighlight(instance)
  },
  { immediate: true }
)

onMounted(() => {
  globalThis.document?.addEventListener('pointerdown', handleGlobalPointerDown)
  globalThis.window?.addEventListener('resize', handleWindowResize)
})

onBeforeUnmount(() => {
  globalThis.document?.removeEventListener('pointerdown', handleGlobalPointerDown)
  globalThis.window?.removeEventListener('resize', handleWindowResize)
})
</script>

<template>
  <aside class="lesson-workspace">
    <header class="workspace-header">
      <div class="workspace-title-block">
        <h2 class="workspace-title">{{ workspaceTitle }}</h2>
        <p class="workspace-summary">{{ workspaceSummary }}</p>
        <p v-if="banner" class="workspace-note" :class="banner.tone">
          {{ banner.text }}
        </p>
      </div>

      <div class="workspace-toolbar">
        <div v-if="hasKnowledgeEvidence" ref="knowledgeMenuRef" class="knowledge-menu">
          <button
            class="icon-btn"
            :class="{ active: knowledgeMenuOpen }"
            type="button"
            :data-tooltip="'知识依据'"
            title="知识依据"
            aria-label="知识依据"
            @click.stop="toggleKnowledgeMenu"
          >
            <BookOpen />
          </button>

          <div v-if="knowledgeMenuOpen" class="knowledge-dropdown" @click.stop>
            <div class="knowledge-dropdown-head">
              <div class="knowledge-dropdown-title">涉及资料</div>
              <div class="knowledge-dropdown-subtitle">
                {{ knowledgeDocuments.length }} 篇资料
              </div>
            </div>

            <div v-if="knowledgeDocuments.length" class="knowledge-doc-list">
              <button
                v-for="item in knowledgeDocuments"
                :key="item.key"
                type="button"
                class="knowledge-doc-chip"
                :class="{ active: activeKnowledgeDocument?.key === item.key }"
                @click="selectKnowledgeDocument(item)"
              >
                {{ item.fileName }}
              </button>
            </div>

            <template v-if="activeKnowledgeDocument">
              <section class="knowledge-section">
                <div class="knowledge-section-title">命中文档</div>
                <div class="knowledge-card">
                  <div class="knowledge-doc-name">{{ activeKnowledgeDocument.fileName }}</div>
                  <div v-if="activeKnowledgeDocument.bestScore != null" class="knowledge-doc-meta">
                    命中得分 {{ activeKnowledgeDocument.bestScore.toFixed(3) }}
                  </div>
                </div>
              </section>

              <section class="knowledge-section">
                <div class="knowledge-section-title">采用片段</div>
                <div v-if="activeKnowledgeDocument.excerpts.length" class="knowledge-snippet-list">
                  <div
                    v-for="excerpt in activeKnowledgeDocument.excerpts"
                    :key="excerpt"
                    class="knowledge-snippet"
                  >
                    {{ excerpt }}
                  </div>
                </div>
                <div v-else class="knowledge-empty-block">
                  当前没有可展示的片段，已记录该资料参与生成。
                </div>
              </section>

              <section class="knowledge-section">
                <div class="knowledge-section-title">生成依据</div>
                <div class="knowledge-basis-list">
                  <div
                    v-for="basis in activeKnowledgeBasis"
                    :key="basis"
                    class="knowledge-basis-item"
                  >
                    {{ basis }}
                  </div>
                </div>
              </section>
            </template>
          </div>
        </div>

        <span v-if="hasKnowledgeEvidence" class="toolbar-divider"></span>

        <button
          class="icon-btn"
          type="button"
          :disabled="!canSave"
          :data-tooltip="saving ? ui.saving : ui.save"
          :title="saving ? ui.saving : ui.save"
          :aria-label="ui.save"
          @click="$emit('save')"
        >
          <Save />
        </button>
        <button
          class="icon-btn"
          type="button"
          :disabled="!canExport"
          :data-tooltip="exporting ? ui.exporting : exportActionText"
          :title="exporting ? ui.exporting : exportActionText"
          :aria-label="exportActionText"
          @click="$emit('export')"
        >
          <Download />
        </button>
        <button
          class="icon-btn"
          type="button"
          :data-tooltip="ui.closeAria"
          :title="ui.closeAria"
          :aria-label="ui.closeAria"
          @click="$emit('close')"
        >
          <X />
        </button>
      </div>
    </header>

    <div class="workspace-layout">
      <aside class="outline-panel">
        <div class="outline-title">{{ ui.outlineTitle }}</div>
        <div v-if="outlineItems.length" class="outline-list">
          <button
            v-for="item in outlineItems"
            :key="item.id"
            type="button"
            class="outline-item"
            :class="[`level-${item.level}`]"
            @click="scrollToOutline(item)"
          >
            {{ item.text }}
          </button>
        </div>
        <div v-else class="outline-empty">
          {{ ui.outlineEmpty }}
        </div>
      </aside>

      <section class="document-panel">
        <div ref="previewRef" class="document-viewport">
          <div
            ref="documentSurfaceRef"
            class="document-surface"
            :class="{ editable: isCompleted }"
          >
            <EditorContent
              v-if="isCompleted && editor"
              :editor="editor"
              class="document-editor-host"
            />
            <div
              v-else-if="previewHtml"
              class="document-render document-prose markdown-prose"
              v-html="previewHtml"
            ></div>
            <div v-else class="document-empty">
              {{ ui.emptyBody }}
            </div>

            <div
              v-if="showSelectionToolbar"
              ref="selectionToolbarRef"
              class="selection-toolbar"
              :class="[`placement-${selectionToolbarPlacement}`, { ready: selectionToolbarPosition.ready }]"
              :style="selectionToolbarStyle"
              @pointerdown.stop
            >
              <div class="selection-toolbar-arrow"></div>

              <button
                class="selection-toolbar-btn selection-toolbar-btn-ai"
                type="button"
                :disabled="!canRequestRewrite"
                :title="rewriting ? ui.rewritingAction : ui.rewriteAction"
                :aria-label="rewriting ? ui.rewritingAction : ui.rewriteAction"
                data-rewrite-trigger="true"
                @mousedown.prevent
                @click="openRewritePanel"
              >
                <Sparkles />
                <span>AI 改写</span>
              </button>

              <span class="selection-toolbar-divider"></span>

              <div class="selection-highlight-menu" @pointerdown.stop>
                <button
                  class="selection-toolbar-btn selection-toolbar-btn-highlight"
                  :class="{ active: editor?.isActive('highlight') }"
                  type="button"
                  :title="ui.highlight"
                  :aria-label="ui.highlight"
                  :style="{ '--highlight-active-color': activeHighlightColor }"
                  @mousedown.prevent
                  @click="applyPreferredHighlight"
                >
                  <Highlighter />
                </button>

                <div class="selection-highlight-palette">
                  <button
                    v-for="color in HIGHLIGHT_COLOR_OPTIONS"
                    :key="color.value"
                    class="selection-highlight-swatch"
                    :class="{ active: activeHighlightColor === normalizeHighlightColor(color.value) }"
                    type="button"
                    :title="color.label"
                    :aria-label="color.label"
                    :style="{ '--swatch-color': color.value }"
                    @mousedown.prevent
                    @click="applyHighlightColor(color.value)"
                  ></button>
                </div>
              </div>
              <button
                class="selection-toolbar-btn"
                :class="{ active: editor?.isActive('bold') }"
                type="button"
                :title="ui.bold"
                :aria-label="ui.bold"
                @mousedown.prevent
                @click="toggleBold"
              >
                <Bold />
              </button>
              <button
                class="selection-toolbar-btn"
                :class="{ active: editor?.isActive('italic') }"
                type="button"
                :title="ui.italic"
                :aria-label="ui.italic"
                @mousedown.prevent
                @click="toggleItalic"
              >
                <Italic />
              </button>
              <button
                class="selection-toolbar-btn"
                :class="{ active: editor?.isActive('heading', { level: 2 }) }"
                type="button"
                :title="ui.h2"
                :aria-label="ui.h2"
                @mousedown.prevent
                @click="toggleHeading"
              >
                <Heading2 />
              </button>
              <button
                class="selection-toolbar-btn"
                :class="{ active: editor?.isActive('bulletList') }"
                type="button"
                :title="ui.list"
                :aria-label="ui.list"
                @mousedown.prevent
                @click="toggleBulletList"
              >
                <List />
              </button>
              <button
                class="selection-toolbar-btn selection-toolbar-btn-clear"
                type="button"
                :title="ui.clearFormat"
                :aria-label="ui.clearFormat"
                @mousedown.prevent
                @click="clearFormatting"
              >
                <Eraser />
              </button>
            </div>

            <div
              v-if="showRewriteComposer"
              ref="rewriteComposerRef"
              class="rewrite-floater rewrite-composer"
              :class="[`placement-${rewritePopoverPlacement}`, { ready: rewritePopoverPosition.ready, busy: rewriting }]"
              :style="rewritePopoverStyle"
              @pointerdown.stop
            >
              <div class="rewrite-floater-arrow"></div>
              <div class="rewrite-input-shell composer-shell">
                <textarea
                  ref="rewriteInputRef"
                  v-model="rewriteInstruction"
                  class="rewrite-textarea"
                  rows="1"
                  :disabled="rewriting"
                  :placeholder="ui.rewritePlaceholder"
                  @input="syncRewriteInputHeight"
                  @keydown="handleRewriteInstructionKeydown"
                ></textarea>

                <button
                  class="rewrite-inline-close"
                  type="button"
                  :title="ui.rewriteClose"
                  :aria-label="ui.rewriteClose"
                  :disabled="rewriting"
                  @click="resetRewriteUi({ keepSuggestion: true })"
                >
                  <X />
                </button>

                <button
                  class="rewrite-submit-btn"
                  type="button"
                  :disabled="!canSubmitRewrite"
                  :title="rewriting ? ui.generatingRewrite : ui.rewriteSubmit"
                  :aria-label="rewriting ? ui.generatingRewrite : ui.rewriteSubmit"
                  @click="submitRewriteRequest"
                >
                  <LoaderCircle v-if="rewriting" class="spin-icon" />
                  <ArrowUp v-else />
                </button>
              </div>

              <div class="rewrite-floater-meta compact">
                <span>{{ rewriting ? ui.generatingRewrite : ui.rewriteShortcut }}</span>
                <button
                  class="rewrite-text-btn"
                  type="button"
                  :disabled="rewriting"
                  @click="resetRewriteUi({ keepSuggestion: true })"
                >
                  {{ ui.cancel }}
                </button>
              </div>
            </div>

            <div
              v-if="showRewriteSuggestion"
              ref="rewriteSuggestionRef"
              class="rewrite-floater rewrite-suggestion"
              :class="[`placement-${rewritePopoverPlacement}`, { ready: rewritePopoverPosition.ready }]"
              :style="rewritePopoverStyle"
              @pointerdown.stop
            >
              <div class="rewrite-floater-arrow"></div>
              <div class="rewrite-floater-head">
                <span class="rewrite-floater-pill success">
                  <Sparkles />
                  {{ ui.rewriteSuggestionTitle }}
                </span>
                <button
                  class="rewrite-close-btn"
                  type="button"
                  :title="ui.dismissRewrite"
                  :aria-label="ui.dismissRewrite"
                  @click="dismissRewriteSuggestion"
                >
                  <X />
                </button>
              </div>

              <div class="rewrite-suggestion-grid">
                <div class="rewrite-result-card">
                  <div class="rewrite-result-label">{{ ui.selectedLabel }}</div>
                  <div class="rewrite-result-text muted">{{ rewriteSuggestion.selectedText }}</div>
                </div>
                <div class="rewrite-result-card emphasis">
                  <div class="rewrite-result-label">{{ ui.suggestionLabel }}</div>
                  <div class="rewrite-result-text">{{ rewriteSuggestion.suggestion }}</div>
                </div>
              </div>

              <div class="rewrite-floater-meta">
                <span class="rewrite-instruction-preview">
                  {{ ui.instructionLabel }}：{{ rewriteSuggestion.instruction }}
                </span>
                <span>{{ ui.rewriteSuggestionHint }}</span>
              </div>

              <div class="rewrite-result-actions">
                <button class="rewrite-secondary-btn" type="button" @click="dismissRewriteSuggestion">
                  {{ ui.dismissRewrite }}
                </button>
                <button class="rewrite-primary-btn" type="button" @click="acceptRewriteSuggestion">
                  {{ ui.acceptRewrite }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <footer class="workspace-footer">
          <span>{{ footerText }}</span>
          <span v-if="streamConnected && !isCompleted">SSE 已连接</span>
        </footer>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.lesson-workspace {
  flex: 1 1 auto;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 18px 28px 24px;
  background: #ffffff;
  overflow: hidden;
}

.workspace-header,
.workspace-toolbar,
.assistant-actions,
.workspace-footer {
  display: flex;
  align-items: center;
}

.workspace-header {
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  padding-bottom: 18px;
  border-bottom: 1px solid #f3f4f6;
}

.workspace-title-block {
  min-width: 0;
}

.workspace-title {
  margin: 0;
  font-size: 34px;
  line-height: 1.1;
  font-weight: 800;
  color: #111827;
}

.workspace-summary {
  margin: 10px 0 0;
  max-width: 760px;
  font-size: 14px;
  line-height: 1.8;
  color: #4b5563;
}

.outline-empty,
.document-empty,
.assistant-instruction,
.assistant-label,
.workspace-note,
.workspace-footer {
  font-size: 13px;
  line-height: 1.7;
  color: #6b7280;
}

.workspace-note {
  margin: 8px 0 0;
  max-width: 720px;
}

.workspace-note.info {
  color: #1d4ed8;
}

.workspace-note.error {
  color: #dc2626;
}

.workspace-layout {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: 192px minmax(0, 1fr);
  gap: 40px;
  padding-top: 20px;
}

.outline-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: transparent;
  overflow: auto;
  padding-top: 6px;
}

.outline-title {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 14px;
}

.outline-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.outline-item {
  width: 100%;
  padding: 8px 0 8px 14px;
  border-radius: 0;
  border: none;
  border-left: 2px solid transparent;
  background: transparent;
  font-size: 13px;
  line-height: 1.55;
  text-align: left;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease;
}

.outline-item:hover {
  background: transparent;
  border-color: #d1d5db;
  color: #111827;
  transform: none;
}

.outline-item.level-2 {
  padding-left: 22px;
}

.outline-item.level-3 {
  padding-left: 30px;
}

.document-panel {
  position: relative;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: transparent;
  overflow: hidden;
}

.workspace-toolbar {
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.knowledge-menu {
  position: relative;
}

.knowledge-dropdown {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: min(440px, calc(100vw - 56px));
  max-height: min(72vh, 640px);
  overflow: auto;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.12);
  z-index: 30;
}

.knowledge-dropdown-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.knowledge-dropdown-title,
.knowledge-section-title {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #6b7280;
}

.knowledge-dropdown-subtitle,
.knowledge-doc-meta,
.knowledge-empty-block {
  font-size: 12px;
  line-height: 1.6;
  color: #9ca3af;
}

.knowledge-doc-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.knowledge-doc-chip {
  min-height: 32px;
  max-width: 100%;
  padding: 0 12px;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #ffffff;
  color: #374151;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
  cursor: pointer;
}

.knowledge-doc-chip.active {
  border-color: #111827;
  color: #111827;
  box-shadow: inset 0 0 0 1px #111827;
}

.knowledge-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.knowledge-section + .knowledge-section {
  margin-top: 14px;
}

.knowledge-card,
.knowledge-snippet,
.knowledge-basis-item,
.knowledge-empty-block {
  padding: 12px 14px;
  border: 1px solid #eef2f7;
  border-radius: 14px;
  background: #fafafa;
}

.knowledge-doc-name {
  font-size: 14px;
  font-weight: 700;
  line-height: 1.5;
  color: #111827;
  word-break: break-word;
}

.knowledge-snippet-list,
.knowledge-basis-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.knowledge-snippet,
.knowledge-basis-item {
  font-size: 13px;
  line-height: 1.75;
  color: #374151;
  white-space: pre-wrap;
  word-break: break-word;
}

.toolbar-divider {
  width: 1px;
  height: 18px;
  margin: 0 4px;
  background: #e5e7eb;
}

.icon-btn {
  position: relative;
  width: 38px;
  height: 38px;
  border: none;
  background: transparent;
  color: #374151;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.icon-btn svg {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  stroke-width: 2.1;
  stroke-linecap: round;
  stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
  shape-rendering: geometricPrecision;
}

.icon-btn::after {
  content: attr(data-tooltip);
  position: absolute;
  top: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%) translateY(-4px);
  padding: 6px 8px;
  border-radius: 8px;
  background: rgba(17, 24, 39, 0.94);
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition: opacity 0.18s ease, transform 0.18s ease, visibility 0.18s ease;
  z-index: 20;
}

.icon-btn::before {
  content: '';
  position: absolute;
  top: calc(100% + 3px);
  left: 50%;
  width: 8px;
  height: 8px;
  background: rgba(17, 24, 39, 0.94);
  transform: translateX(-50%) rotate(45deg) translateY(-4px);
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition: opacity 0.18s ease, transform 0.18s ease, visibility 0.18s ease;
  z-index: 19;
}

.icon-btn:hover {
  background: #f3f4f6;
  color: #111827;
}

.icon-btn:hover::after,
.icon-btn:hover::before,
.icon-btn:focus-visible::after,
.icon-btn:focus-visible::before {
  opacity: 1;
  visibility: visible;
}

.icon-btn:hover::after,
.icon-btn:focus-visible::after {
  transform: translateX(-50%) translateY(0);
}

.icon-btn:hover::before,
.icon-btn:focus-visible::before {
  transform: translateX(-50%) rotate(45deg) translateY(0);
}

.icon-btn.active {
  background: #ffffff;
  color: #111827;
  box-shadow: inset 0 0 0 1.5px #111827;
}

.icon-btn.active:hover {
  background: #f9fafb;
  color: #111827;
}

.icon-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.selection-toolbar {
  position: absolute;
  z-index: 15;
  display: flex;
  align-items: center;
  gap: 4px;
  max-width: calc(100% - 24px);
  padding: 8px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(18px) saturate(180%);
  box-shadow:
    0 18px 40px rgba(15, 23, 42, 0.12),
    0 8px 18px rgba(15, 23, 42, 0.08);
  white-space: nowrap;
  overflow: visible;
  transform: translateY(8px) scale(0.98);
  transform-origin: center top;
  transition: opacity 0.18s ease, transform 0.22s ease;
}

.selection-toolbar.ready {
  transform: translateY(0) scale(1);
}

.selection-toolbar.placement-top {
  transform-origin: center bottom;
}

.selection-toolbar-arrow {
  position: absolute;
  left: 50%;
  width: 14px;
  height: 14px;
  background: rgba(255, 255, 255, 0.96);
  border-left: 1px solid rgba(148, 163, 184, 0.18);
  border-top: 1px solid rgba(148, 163, 184, 0.18);
  transform: translateX(-50%) rotate(45deg);
}

.selection-toolbar.placement-bottom .selection-toolbar-arrow {
  top: -8px;
}

.selection-toolbar.placement-top .selection-toolbar-arrow {
  bottom: -8px;
  transform: translateX(-50%) rotate(225deg);
}

.selection-toolbar-divider {
  width: 1px;
  height: 22px;
  flex: 0 0 auto;
  margin: 0 2px;
  background: rgba(226, 232, 240, 0.96);
}

.selection-toolbar-btn {
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: #475569;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
}

.selection-toolbar-btn:hover:not(:disabled) {
  background: #f8fafc;
  color: #0f172a;
}

.selection-toolbar-btn.active {
  background: #eff6ff;
  color: #1d4ed8;
  box-shadow: inset 0 0 0 1px rgba(147, 197, 253, 0.95);
}

.selection-highlight-menu {
  position: relative;
  display: inline-flex;
  flex: 0 0 auto;
}

.selection-highlight-menu::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  width: 168px;
  height: 16px;
  transform: translateX(-50%);
}

.selection-highlight-palette {
  position: absolute;
  top: calc(100% + 10px);
  left: 50%;
  z-index: 18;
  display: grid;
  grid-template-columns: repeat(4, 28px);
  gap: 8px;
  width: max-content;
  padding: 10px;
  border: 1px solid rgba(226, 232, 240, 0.96);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow:
    0 20px 42px rgba(15, 23, 42, 0.14),
    0 8px 18px rgba(15, 23, 42, 0.08);
  transform: translateX(-50%) translateY(-6px);
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition: opacity 0.18s ease, transform 0.18s ease, visibility 0.18s ease;
}

.selection-highlight-palette::before {
  content: '';
  position: absolute;
  top: -7px;
  left: 50%;
  width: 14px;
  height: 14px;
  border-left: 1px solid rgba(226, 232, 240, 0.96);
  border-top: 1px solid rgba(226, 232, 240, 0.96);
  background: rgba(255, 255, 255, 0.98);
  transform: translateX(-50%) rotate(45deg);
}

.selection-highlight-menu:hover .selection-highlight-palette,
.selection-highlight-menu:focus-within .selection-highlight-palette {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
  transform: translateX(-50%) translateY(0);
}

.selection-highlight-swatch {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 10px;
  background: var(--swatch-color);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.62),
    0 0 0 1px rgba(148, 163, 184, 0.36);
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

.selection-highlight-swatch:hover,
.selection-highlight-swatch:focus-visible {
  transform: translateY(-1px) scale(1.04);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.72),
    0 0 0 2px rgba(37, 99, 235, 0.18);
}

.selection-highlight-swatch.active {
  box-shadow:
    inset 0 0 0 2px rgba(255, 255, 255, 0.92),
    0 0 0 2px rgba(15, 23, 42, 0.84);
}

.selection-toolbar-btn-highlight.active {
  background: var(--highlight-active-color, #fef3c7);
  color: #92400e;
  box-shadow: inset 0 0 0 1px rgba(245, 158, 11, 0.38);
}

.selection-toolbar-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.selection-toolbar-btn svg {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

.selection-toolbar-btn-ai {
  width: auto;
  min-width: 0;
  padding: 0 12px;
  gap: 8px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(239, 246, 255, 0.96), rgba(240, 249, 255, 0.98));
  color: #2563eb;
  font-size: 14px;
  font-weight: 700;
  box-shadow: inset 0 0 0 1px rgba(191, 219, 254, 0.92);
}

.selection-toolbar-btn-ai:hover:not(:disabled) {
  background: linear-gradient(135deg, rgba(219, 234, 254, 0.98), rgba(224, 242, 254, 1));
  color: #1d4ed8;
}

.selection-toolbar-btn-clear:hover:not(:disabled) {
  background: #fff1f2;
  color: #be123c;
}

.rewrite-floater {
  position: absolute;
  z-index: 14;
  width: min(460px, calc(100% - 36px));
  padding: 14px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 24px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.92));
  backdrop-filter: blur(16px) saturate(180%);
  box-shadow:
    0 24px 60px rgba(15, 23, 42, 0.14),
    0 10px 24px rgba(15, 23, 42, 0.08);
  display: flex;
  flex-direction: column;
  gap: 12px;
  transform: translateY(8px) scale(0.98);
  transform-origin: center top;
  transition: opacity 0.18s ease, transform 0.22s ease;
}

.rewrite-composer {
  width: min(560px, calc(100% - 36px));
  padding: 12px;
  gap: 10px;
}

.rewrite-floater.ready {
  transform: translateY(0) scale(1);
}

.rewrite-floater.placement-top {
  transform-origin: center bottom;
}

.rewrite-floater-arrow {
  position: absolute;
  left: 50%;
  width: 18px;
  height: 18px;
  background: rgba(255, 255, 255, 0.94);
  border-left: 1px solid rgba(148, 163, 184, 0.18);
  border-top: 1px solid rgba(148, 163, 184, 0.18);
  transform: translateX(-50%) rotate(45deg);
}

.rewrite-floater.placement-bottom .rewrite-floater-arrow {
  top: -10px;
}

.rewrite-floater.placement-top .rewrite-floater-arrow {
  bottom: -10px;
  transform: translateX(-50%) rotate(225deg);
}

.rewrite-floater-head {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.rewrite-floater-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.rewrite-floater-pill svg {
  width: 14px;
  height: 14px;
}

.rewrite-floater-pill.success {
  background: rgba(14, 165, 145, 0.12);
  color: #0f766e;
}

.rewrite-close-btn {
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.74);
  color: #64748b;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease;
}

.rewrite-close-btn:hover:not(:disabled) {
  background: #f8fafc;
  color: #0f172a;
}

.rewrite-close-btn:disabled {
  opacity: 0.48;
  cursor: not-allowed;
}

.rewrite-close-btn svg {
  width: 18px;
  height: 18px;
}

.rewrite-result-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 18px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.76);
}

.rewrite-result-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #64748b;
  text-transform: uppercase;
}

.rewrite-result-text {
  white-space: pre-wrap;
  word-break: break-word;
  color: #0f172a;
  line-height: 1.75;
}

.rewrite-result-text {
  max-height: 180px;
  overflow: auto;
}

.rewrite-result-text.muted {
  color: #475569;
}

.rewrite-result-card.emphasis {
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.96), rgba(248, 250, 252, 0.9));
  border-color: rgba(147, 197, 253, 0.65);
}

.rewrite-input-shell {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 10px 10px 10px 14px;
  border-radius: 22px;
  border: 1px solid rgba(203, 213, 225, 0.8);
  background: rgba(255, 255, 255, 0.92);
}

.rewrite-input-shell.composer-shell {
  gap: 8px;
  min-height: 72px;
  padding: 8px 8px 8px 14px;
  border-radius: 26px;
  border-color: rgba(191, 219, 254, 0.88);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.96));
  box-shadow:
    inset 0 0 0 1px rgba(219, 234, 254, 0.82),
    0 16px 34px rgba(15, 23, 42, 0.08);
}

.rewrite-textarea {
  flex: 1 1 auto;
  min-height: 72px;
  max-height: 168px;
  resize: none;
  padding: 0;
  border: none;
  background: transparent;
  color: #0f172a;
  outline: none;
  font-size: 15px;
  line-height: 1.8;
  box-sizing: border-box;
}

.rewrite-textarea::placeholder {
  color: #94a3b8;
}

.rewrite-inline-close {
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  border: none;
  border-radius: 14px;
  background: rgba(241, 245, 249, 0.92);
  color: #64748b;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease, opacity 0.18s ease;
}

.rewrite-inline-close:hover:not(:disabled) {
  background: rgba(226, 232, 240, 0.96);
  color: #0f172a;
}

.rewrite-inline-close:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.rewrite-inline-close svg {
  width: 18px;
  height: 18px;
}

.rewrite-submit-btn {
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  border: none;
  border-radius: 16px;
  background: linear-gradient(135deg, #2563eb, #0ea5e9);
  color: #ffffff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.24);
  transition: transform 0.18s ease, box-shadow 0.18s ease, opacity 0.18s ease;
}

.rewrite-submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 16px 28px rgba(37, 99, 235, 0.28);
}

.rewrite-submit-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.rewrite-submit-btn svg {
  width: 18px;
  height: 18px;
}

.rewrite-floater-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

.rewrite-floater-meta.compact {
  padding: 0 6px 0 4px;
}

.rewrite-text-btn {
  border: none;
  background: transparent;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  padding: 0;
}

.rewrite-text-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.rewrite-suggestion-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.rewrite-result-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.rewrite-primary-btn,
.rewrite-secondary-btn {
  min-height: 40px;
  padding: 0 16px;
  border-radius: 14px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.rewrite-primary-btn {
  border: none;
  background: linear-gradient(135deg, #2563eb, #0ea5e9);
  color: #ffffff;
}

.rewrite-primary-btn:hover {
  transform: translateY(-1px);
}

.rewrite-secondary-btn {
  border: 1px solid rgba(203, 213, 225, 0.9);
  background: rgba(255, 255, 255, 0.9);
  color: #334155;
}

.rewrite-secondary-btn:hover {
  border-color: #94a3b8;
}

.rewrite-instruction-preview {
  color: #334155;
}

.spin-icon {
  animation: spin 1s linear infinite;
}

.rewrite-composer.busy .rewrite-input-shell {
  border-color: rgba(147, 197, 253, 0.92);
  box-shadow: inset 0 0 0 1px rgba(191, 219, 254, 0.8);
}

.document-editor-prose:focus {
  border-color: #d1d5db;
  box-shadow: none;
}

.document-viewport {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
}

.document-surface {
  position: relative;
  width: min(100%, 920px);
  min-height: 100%;
  margin: 0 auto;
  padding: 18px 24px 80px;
  border: 1px solid #eef2f7;
  border-radius: 22px;
  background: #ffffff;
  box-sizing: border-box;
}

.document-surface.editable {
  cursor: text;
}

.document-editor-host,
.document-render,
.document-empty {
  min-height: clamp(480px, 68vh, 1080px);
}

:deep(.document-editor-prose),
:deep(.document-editor-prose .ProseMirror),
:deep(.document-editor-prose.ProseMirror) {
  min-height: clamp(480px, 68vh, 1080px);
  outline: none;
}

:deep(.persistent-selection-highlight) {
  background: linear-gradient(180deg, rgba(191, 219, 254, 0.65), rgba(147, 197, 253, 0.45));
  border-radius: 0.35em;
  box-shadow:
    0 0 0 1px rgba(96, 165, 250, 0.14),
    inset 0 -0.1em 0 rgba(96, 165, 250, 0.12);
}

:deep(.document-editor-prose p.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  float: left;
  height: 0;
  color: #9ca3af;
  pointer-events: none;
}

.document-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
}

:deep(.document-prose h1) {
  margin: 0 0 20px;
  font-size: 40px;
  line-height: 1.18;
  color: #111827;
}

:deep(.document-prose h2) {
  margin: 30px 0 16px;
  font-size: 30px;
  line-height: 1.26;
  color: #111827;
}

:deep(.document-prose h3) {
  margin: 22px 0 12px;
  font-size: 22px;
  line-height: 1.34;
  color: #111827;
}

:deep(.document-prose p) {
  margin: 0 0 1em;
  font-size: 17px;
  line-height: 2;
  color: #374151;
}

:deep(.document-prose ul),
:deep(.document-prose ol) {
  margin: 0 0 1.2em;
  padding-left: 1.6em;
}

:deep(.document-prose li) {
  margin-bottom: 0.45em;
  font-size: 17px;
  line-height: 1.95;
  color: #374151;
}

:deep(.document-prose blockquote) {
  margin: 1.2em 0;
  padding: 0.85em 1em;
  border-left: 4px solid #93c5fd;
  background: #eff6ff;
  color: #1d4ed8;
}

:deep(.document-prose mark) {
  padding: 0.08em 0.18em;
  border-radius: 0.28em;
  background: linear-gradient(180deg, rgba(254, 240, 138, 0.94), rgba(253, 224, 71, 0.82));
  color: inherit;
}

:deep(.document-prose code) {
  padding: 0.12em 0.38em;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.06);
  font-size: 0.92em;
}

:deep(.document-prose pre) {
  margin: 1.2em 0;
  padding: 14px 16px;
  border-radius: 16px;
  background: #111827;
  color: #f9fafb;
  overflow: auto;
}

:deep(.document-prose pre code) {
  padding: 0;
  background: transparent;
}

.workspace-footer {
  justify-content: space-between;
  gap: 12px;
  padding-top: 14px;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1280px) {
  .workspace-layout {
    grid-template-columns: 176px minmax(0, 1fr);
    gap: 26px;
  }
}

@media (max-width: 1100px) {
  .workspace-layout,
  .rewrite-suggestion-grid {
    grid-template-columns: 1fr;
  }

  .outline-panel {
    max-height: 200px;
    padding-top: 0;
  }
}

@media (max-width: 960px) {
  .lesson-workspace {
    padding: 16px 18px 18px;
  }

  .workspace-header,
  .workspace-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .document-surface {
    padding: 16px 14px 56px;
  }

  .selection-toolbar {
    max-width: calc(100% - 12px);
  }

  .rewrite-floater {
    width: calc(100% - 20px);
    padding: 12px;
  }

  .rewrite-floater-meta,
  .rewrite-result-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .workspace-footer {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
