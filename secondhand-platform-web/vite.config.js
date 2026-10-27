import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import { existsSync, readdirSync } from 'node:fs'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import viteCompression from 'vite-plugin-compression'

const componentDirectory = resolve(__dirname, 'node_modules/element-plus/es/components')
const componentStyles = readdirSync(componentDirectory)
  .filter(name => existsSync(resolve(componentDirectory, name, 'style/css.mjs')))
  .map(name => `element-plus/es/components/${name}/style/css`)

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
  // 按需插件在转换 Vue 文件时才添加组件样式，启动扫描可能漏掉懒加载页面的依赖。
  // 提前预构建这些入口，避免第一次导航时重新优化依赖、整页刷新并打断路由跳转。
  optimizeDeps: {
    include: ['element-plus/es', ...componentStyles]
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
    // 固定本机 IPv4 地址，避免 localhost 只监听 ::1 时无法通过 127.0.0.1 访问。
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Java 的 AI 接口自身带 /api/ai 前缀；原有商品、用户接口仍去掉 /api。
        rewrite: (path) => /^\/api\/ai(?:\/|\?|$)/.test(path) ? path : path.replace(/^\/api/, '')
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,
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
