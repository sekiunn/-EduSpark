import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [
    vue(),
  ],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('/node_modules/vue/')) {
              return 'vue-vendor'
            }

            if (id.includes('/node_modules/marked/')) {
              return 'marked-vendor'
            }

            if (id.includes('/node_modules/katex/')) {
              return 'katex-vendor'
            }

            if (id.includes('/node_modules/highlight.js/')) {
              return 'highlight-vendor'
            }
          }

          if (
            id.includes('/src/views/ChatHome.vue') ||
            id.includes('/src/components/chat/') ||
            id.includes('/src/components/knowledge/')
          ) {
            return 'chat-home'
          }

          return undefined
        }
      }
    }
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
})
