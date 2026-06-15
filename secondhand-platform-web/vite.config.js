import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import viteCompression from 'vite-plugin-compression'

export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入：组件、指令、ElMessage/ElMessageBox 等 API 及其样式自动导入
    AutoImport({ resolvers: [ElementPlusResolver()] }),
    Components({ resolvers: [ElementPlusResolver()] }),
    // 生产构建额外产出 .gz，配合服务端 gzip 显著减小传输体积
    viteCompression({ threshold: 10240, verbose: false })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        // 全局注入设计变量，免去每个文件重复 @use；
        // 跳过 variables.scss 自身，避免循环 @use 报错
        additionalData: (content, filename) => {
          const normalized = filename.replace(/\\/g, '/')
          if (normalized.endsWith('assets/styles/variables.scss')) return content
          // 已显式 @use variables 的文件跳过，避免重复 @use 报错；
          // 后续逐步移除各文件的 @use 时，注入会自动补上，平滑过渡
          if (/@use\s+['"][^'"]*variables/.test(content)) return content
          return `@use '@/assets/styles/variables.scss' as *;\n${content}`
        }
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    cssCodeSplit: true,
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        // 第三方库分包，提升缓存命中率、避免主包过大
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus', '@element-plus/icons-vue']
        }
      }
    }
  }
})
