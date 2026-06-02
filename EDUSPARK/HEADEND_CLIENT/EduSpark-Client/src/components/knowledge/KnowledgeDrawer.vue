<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import KnowledgeBaseView from './KnowledgeBaseView.vue'

const props = defineProps({
  open: {
    type: Boolean,
    default: false
  },
  userId: {
    type: [String, Number],
    default: null
  },
  searchKeyword: {
    type: String,
    default: ''
  },
  currentCategory: {
    type: String,
    default: 'all'
  },
  viewMode: {
    type: String,
    default: 'grid'
  },
  knowledgeDocs: {
    type: Array,
    default: () => []
  },
  filteredDocs: {
    type: Array,
    default: () => []
  },
  selectedDocId: {
    type: [String, Number],
    default: null
  },
  knowledgeLoading: {
    type: Boolean,
    default: false
  },
  isUploading: {
    type: Boolean,
    default: false
  },
  uploadProgress: {
    type: Number,
    default: 0
  },
  uploadQueue: {
    type: Array,
    default: () => []
  },
  workspaces: {
    type: Array,
    default: () => []
  },
  workspacesLoading: {
    type: Boolean,
    default: false
  },
  selectedWorkspaceId: {
    type: [String, Number],
    default: null
  },
  workspaceCounts: {
    type: Object,
    default: () => ({ ungrouped: 0, total: 0 })
  }
})

const emit = defineEmits([
  'close',
  'update:searchKeyword',
  'update:currentCategory',
  'update:viewMode',
  'select-doc',
  'quote-doc',
  'retry-file',
  'delete-file',
  'trigger-file-upload',
  'trigger-folder-import',
  'select-workspace',
  'create-workspace',
  'rename-workspace',
  'delete-workspace'
])

const panelRef = ref(null)
const previousBodyOverflow = ref('')

const handleKeydown = (event) => {
  if (event.key === 'Escape') {
    emit('close')
  }
}

watch(
  () => props.open,
  async (isOpen) => {
    if (typeof document !== 'undefined') {
      if (isOpen) {
        previousBodyOverflow.value = document.body.style.overflow
        document.body.style.overflow = 'hidden'
      } else {
        document.body.style.overflow = previousBodyOverflow.value
      }
    }

    if (isOpen) {
      await nextTick()
      panelRef.value?.focus()
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  if (typeof document !== 'undefined') {
    document.body.style.overflow = previousBodyOverflow.value
  }
})
</script>

<template>
  <aside
    class="knowledge-drawer"
    :class="{ open }"
    role="dialog"
    aria-modal="true"
    aria-label="教育知识工作台"
    :aria-hidden="!open"
    @keydown="handleKeydown"
    @click.self="emit('close')"
  >
    <div ref="panelRef" class="knowledge-drawer-panel" tabindex="-1">
      <KnowledgeBaseView
        :visible="open"
        :user-id="userId"
        :search-keyword="searchKeyword"
        :current-category="currentCategory"
        :view-mode="viewMode"
        :knowledge-docs="knowledgeDocs"
        :filtered-docs="filteredDocs"
        :selected-doc-id="selectedDocId"
        :knowledge-loading="knowledgeLoading"
        :is-uploading="isUploading"
        :upload-progress="uploadProgress"
        :upload-queue="uploadQueue"
        :workspaces="workspaces"
        :workspaces-loading="workspacesLoading"
        :selected-workspace-id="selectedWorkspaceId"
        :workspace-counts="workspaceCounts"
        @update:search-keyword="emit('update:searchKeyword', $event)"
        @update:current-category="emit('update:currentCategory', $event)"
        @update:view-mode="emit('update:viewMode', $event)"
        @select-doc="emit('select-doc', $event)"
        @quote-doc="emit('quote-doc', $event)"
        @retry-file="emit('retry-file', $event)"
        @delete-file="emit('delete-file', $event)"
        @trigger-file-upload="emit('trigger-file-upload')"
        @trigger-folder-import="emit('trigger-folder-import')"
        @select-workspace="emit('select-workspace', $event)"
        @create-workspace="emit('create-workspace', $event)"
        @rename-workspace="emit('rename-workspace', $event)"
        @delete-workspace="emit('delete-workspace', $event)"
      />
    </div>
  </aside>
</template>

<style scoped>
.knowledge-drawer {
  position: fixed;
  inset: 0;
  z-index: 25;
  background: rgba(247, 246, 243, 0.88);
  backdrop-filter: blur(18px);
  opacity: 0;
  pointer-events: none;
  overflow: hidden;
  transition: opacity var(--kb-motion-base) ease-out, background-color var(--kb-motion-base) ease-out;
}

.knowledge-drawer.open {
  opacity: 1;
  pointer-events: auto;
}

.knowledge-drawer-panel {
  width: 100%;
  height: 100%;
  overflow-y: auto;
  outline: none;
  background: var(--kb-bg);
  opacity: 0;
  transform: translateX(100%);
  transition: transform var(--kb-motion-slow) var(--kb-ease), opacity var(--kb-motion-base) ease-out;
  scrollbar-gutter: stable both-edges;
  will-change: transform, opacity;
}

.knowledge-drawer.open .knowledge-drawer-panel {
  opacity: 1;
  transform: translateX(0);
}

@media (max-width: 768px) {
  .knowledge-drawer-panel {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .knowledge-drawer,
  .knowledge-drawer-panel {
    transition: none;
  }
}
</style>
