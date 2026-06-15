import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
// variables.scss 已通过 vite additionalData 全局注入，无需单独 import
import './assets/styles/global.scss'
import './assets/styles/element-overrides.scss'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.mount('#app')
