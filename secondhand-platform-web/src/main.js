import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
// variables.scss 已通过 vite additionalData 全局注入，无需单独 import
import './assets/styles/global.scss'
// 命令式 API（ElMessage/ElMessageBox/ElNotification/ElLoading）的样式：
// 这些组件通过 JS 调用而非模板标签使用，被显式 import 后绕过了 unplugin 的按需样式注入，
// 需手动引入，否则弹框/提示会因缺少 CSS 而错位（如 MessageBox 贴到最左侧）。
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'
import './assets/styles/element-overrides.scss'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.mount('#app')
