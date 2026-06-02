import { computed, onUnmounted, ref } from 'vue'
import {
  connectPptDocumentStream,
  exportPptDocument,
  getPptDocument
} from '@/api'

const STREAMABLE_STATUSES = new Set([
  'preparing',
  'retrieving',
  'enriching',
  'planning',
  'structuring',
  'rendering'
])

const SLIDE_STAGES = {
  SKELETON: 'skeleton',
  BACKGROUND_READY: 'background_ready',
  CONTENT_FILLING: 'content_filling',
  COMPLETED: 'completed'
}

export function usePptWorkspace() {
  const visible = ref(false)
  const currentDocumentId = ref(null)
  const document = ref(null)
  const streamingText = ref('')
  const loading = ref(false)
  const exporting = ref(false)
  const error = ref('')
  const streamConnected = ref(false)
  const streamError = ref('')

  // 逐页揭幕状态：按 slideNo 升序排列
  const slidesProgress = ref([])
  const slidesTotal = ref(0)

  let streamHandle = null

  const status = computed(() => document.value?.status || '')
  const isStreamable = computed(() => STREAMABLE_STATUSES.has(status.value))

  const overallProgress = computed(() => {
    const done = slidesProgress.value.filter((s) => s.stage === SLIDE_STAGES.COMPLETED).length
    const total = slidesTotal.value || slidesProgress.value.length
    return { done, total }
  })

  const hasSlideProgress = computed(() => slidesProgress.value.length > 0)

  const stopStream = () => {
    if (streamHandle) {
      streamHandle.close()
      streamHandle = null
    }
    streamConnected.value = false
  }

  const upsertSlideProgress = (payload, mergeFn) => {
    if (!payload || payload.slideNo == null) return
    const slideNo = payload.slideNo
    if (payload.total) {
      slidesTotal.value = payload.total
    }
    const list = slidesProgress.value.slice()
    const idx = list.findIndex((s) => s.slideNo === slideNo)
    const base = idx >= 0 ? { ...list[idx] } : { slideNo, bullets: [] }
    const next = mergeFn(base, payload)
    if (idx >= 0) {
      list[idx] = next
    } else {
      list.push(next)
      list.sort((a, b) => (a.slideNo || 0) - (b.slideNo || 0))
    }
    slidesProgress.value = list
  }

  const applySlideSkeleton = (payload) => {
    upsertSlideProgress(payload, (base, p) => ({
      ...base,
      stage: SLIDE_STAGES.SKELETON,
      layout: p.layout ?? base.layout ?? '',
      slotLayout: p.slotLayout ?? base.slotLayout ?? ''
    }))
  }

  const applySlideBackground = (payload) => {
    upsertSlideProgress(payload, (base, p) => ({
      ...base,
      stage: SLIDE_STAGES.BACKGROUND_READY,
      backgroundImageUrl: p.backgroundImageUrl ?? base.backgroundImageUrl ?? ''
    }))
  }

  const applySlideContentDelta = (payload) => {
    if (!payload || payload.slideNo == null) return
    upsertSlideProgress({ slideNo: payload.slideNo }, (base) => {
      const next = { ...base, stage: SLIDE_STAGES.CONTENT_FILLING }
      const field = payload.field
      const value = payload.value ?? ''
      if (field === 'title') {
        next.title = value
      } else if (field === 'bullet') {
        next.bullets = payload.append === false ? [value] : [...(base.bullets || []), value]
      } else if (field === 'visualFocus') {
        next.visualFocus = value
      } else if (field === 'speakerNotes') {
        next.speakerNotes = value
      }
      return next
    })
  }

  const applySlideCompleted = (payload) => {
    upsertSlideProgress(payload, (base, p) => {
      const slidePlan = p.slidePlan || {}
      return {
        ...base,
        stage: SLIDE_STAGES.COMPLETED,
        title: p.title ?? slidePlan.title ?? base.title,
        bullets: p.bullets ?? slidePlan.bullets ?? base.bullets ?? [],
        layout: p.layout ?? slidePlan.layout ?? base.layout,
        slotLayout: p.slotLayout ?? slidePlan.slotLayout ?? base.slotLayout,
        visualFocus: p.visualFocus ?? slidePlan.visualFocus ?? base.visualFocus,
        speakerNotes: p.speakerNotes ?? slidePlan.speakerNotes ?? base.speakerNotes,
        backgroundImageUrl: p.backgroundImageUrl ?? base.backgroundImageUrl,
        slidePlan
      }
    })
  }

  const replaySlidesProgress = (list) => {
    if (!Array.isArray(list) || list.length === 0) return
    const sorted = [...list]
      .filter((item) => item && item.slideNo != null)
      .sort((a, b) => (a.slideNo || 0) - (b.slideNo || 0))
    if (sorted.length === 0) return
    slidesProgress.value = sorted.map((item) => ({
      slideNo: item.slideNo,
      stage: item.stage || SLIDE_STAGES.COMPLETED,
      title: item.title || item.slidePlan?.title || '',
      bullets: item.bullets || item.slidePlan?.bullets || [],
      layout: item.layout || item.slidePlan?.layout || '',
      slotLayout: item.slotLayout || item.slidePlan?.slotLayout || '',
      visualFocus: item.visualFocus || item.slidePlan?.visualFocus || '',
      speakerNotes: item.speakerNotes || item.slidePlan?.speakerNotes || '',
      backgroundImageUrl: item.backgroundImageUrl || '',
      slidePlan: item.slidePlan || null
    }))
    const lastTotal = sorted[sorted.length - 1]?.total
    if (lastTotal) {
      slidesTotal.value = lastTotal
    }
  }

  const applyDocument = (payload) => {
    document.value = payload || null
    error.value = payload?.errorMessage || ''

    const nextPlanning = payload?.planningMarkdown || ''
    if (payload?.status === 'completed') {
      streamingText.value = nextPlanning
    } else if (STREAMABLE_STATUSES.has(payload?.status)) {
      if (nextPlanning) {
        streamingText.value = nextPlanning
      }
    } else {
      streamingText.value = nextPlanning
    }

    // 重连时把后端持久化的 slidesProgress 直接合并展示，不重放动画
    if (Array.isArray(payload?.slidesProgress) && payload.slidesProgress.length > 0) {
      replaySlidesProgress(payload.slidesProgress)
    }

    if (!STREAMABLE_STATUSES.has(payload?.status)) {
      stopStream()
    }
  }

  const fetchDocument = async () => {
    if (!currentDocumentId.value) return null

    const res = await getPptDocument(currentDocumentId.value)
    const payload = res?.data || null
    applyDocument(payload)
    return payload
  }

  const startStream = () => {
    stopStream()
    if (!currentDocumentId.value) return

    streamError.value = ''
    streamHandle = connectPptDocumentStream(currentDocumentId.value, {
      snapshot: (payload) => {
        streamConnected.value = true
        applyDocument(payload)
      },
      status: (payload) => {
        streamConnected.value = true
        applyDocument(payload)
      },
      content_delta: (payload) => {
        streamConnected.value = true
        const delta = payload?.delta || ''
        if (!delta) return
        streamingText.value += delta
      },
      'slide.skeleton': (payload) => {
        streamConnected.value = true
        applySlideSkeleton(payload)
      },
      'slide.background': (payload) => {
        streamConnected.value = true
        applySlideBackground(payload)
      },
      'slide.content_delta': (payload) => {
        streamConnected.value = true
        applySlideContentDelta(payload)
      },
      'slide.completed': (payload) => {
        streamConnected.value = true
        applySlideCompleted(payload)
      },
      completed: (payload) => {
        streamConnected.value = true
        applyDocument(payload)
        stopStream()
      },
      failed: (payload) => {
        applyDocument(payload)
        stopStream()
      },
      error: (err) => {
        streamConnected.value = false
        streamError.value = err.message || 'PPT 工作区流连接失败'
      },
      close: () => {
        streamConnected.value = false
      }
    })
  }

  const openWorkspace = async (documentId) => {
    if (!documentId) return null

    visible.value = true
    currentDocumentId.value = documentId
    loading.value = true
    error.value = ''
    streamError.value = ''
    slidesProgress.value = []
    slidesTotal.value = 0

    try {
      const payload = await fetchDocument()
      streamingText.value = payload?.planningMarkdown || ''
      if (STREAMABLE_STATUSES.has(payload?.status)) {
        startStream()
      } else {
        stopStream()
      }
      return payload
    } finally {
      loading.value = false
    }
  }

  const closeWorkspace = () => {
    visible.value = false
    stopStream()
  }

  const resetWorkspace = () => {
    closeWorkspace()
    currentDocumentId.value = null
    document.value = null
    streamingText.value = ''
    error.value = ''
    streamError.value = ''
    slidesProgress.value = []
    slidesTotal.value = 0
  }

  const exportDocument = async () => {
    if (!currentDocumentId.value) return null

    exporting.value = true
    error.value = ''
    try {
      const res = await exportPptDocument(currentDocumentId.value)
      applyDocument(res?.data || null)
      return res?.data || null
    } finally {
      exporting.value = false
    }
  }

  onUnmounted(() => {
    stopStream()
  })

  return {
    visible,
    currentDocumentId,
    document,
    streamingText,
    loading,
    exporting,
    error,
    streamConnected,
    streamError,
    isStreamable,
    slidesProgress,
    slidesTotal,
    overallProgress,
    hasSlideProgress,
    openWorkspace,
    closeWorkspace,
    resetWorkspace,
    exportDocument,
    stopStream,
    fetchDocument
  }
}
