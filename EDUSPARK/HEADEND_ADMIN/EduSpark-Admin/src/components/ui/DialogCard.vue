<script setup>
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: ''
  },
  description: {
    type: String,
    default: ''
  },
  width: {
    type: String,
    default: '720px'
  }
})

const emit = defineEmits(['update:modelValue'])

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog-fade">
      <div v-if="props.modelValue" class="dialog-mask" @click.self="close">
        <div class="dialog-card" :style="{ width: `min(${props.width}, calc(100vw - 32px))` }">
          <div class="dialog-head">
            <div>
              <div class="dialog-title">{{ props.title }}</div>
              <div v-if="props.description" class="dialog-description">{{ props.description }}</div>
            </div>

            <button class="dialog-close" type="button" aria-label="关闭弹窗" @click="close">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </button>
          </div>

          <div class="dialog-body">
            <slot />
          </div>

          <div v-if="$slots.footer" class="dialog-footer">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 180;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(17, 24, 39, 0.24);
  backdrop-filter: blur(4px);
}

.dialog-card {
  max-height: calc(100vh - 32px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--es-border);
  border-radius: 18px;
  background: var(--es-surface);
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.16);
}

.dialog-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px 0;
}

.dialog-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--es-text-primary);
}

.dialog-description {
  margin-top: 6px;
  font-size: 13px;
  color: var(--es-text-secondary);
}

.dialog-close {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: 1px solid var(--es-border);
  border-radius: 10px;
  color: var(--es-text-secondary);
  background: var(--es-surface);
  transition: all 0.3s ease;
}

.dialog-close:hover {
  color: var(--es-link);
  background: var(--es-surface-soft);
}

.dialog-body {
  min-height: 0;
  overflow: auto;
  padding: 22px 24px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 18px 24px 24px;
  border-top: 1px solid var(--es-border);
  background: var(--es-surface);
}

.dialog-fade-enter-active,
.dialog-fade-leave-active {
  transition: opacity 0.24s ease;
}

.dialog-fade-enter-active .dialog-card,
.dialog-fade-leave-active .dialog-card {
  transition: transform 0.24s ease, opacity 0.24s ease;
}

.dialog-fade-enter-from,
.dialog-fade-leave-to {
  opacity: 0;
}

.dialog-fade-enter-from .dialog-card,
.dialog-fade-leave-to .dialog-card {
  opacity: 0;
  transform: translateY(10px) scale(0.98);
}
</style>
