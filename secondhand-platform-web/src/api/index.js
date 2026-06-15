import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建 Axios 实例
const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {}
})

// === 请求拦截器 ===
http.interceptors.request.use(
  config => {
    if (!(config.data instanceof FormData)) {
      config.headers['Content-Type'] = 'application/json'
    }
    return config
  },
  error => Promise.reject(error)
)

// === 响应拦截器 ===
http.interceptors.response.use(
  response => {
    const { code, message, data } = response.data
    if (code === 200) return data
    if (!response.config?.skipErrorMessage) {
      ElMessage.error(message || '操作失败')
    }
    return Promise.reject(new Error(message || '操作失败'))
  },
  error => {
    if (error.response) {
      const msg = error.response.data?.message
      if (error.response.status === 401) {
        if (error.config?.skipAuthRedirect) {
          return Promise.reject(error)
        }
        // 避免在已经是登录页时重复跳转造成无限闪屏
        if (!window.location.pathname.startsWith('/login')) {
          window.location.replace('/login')
        }
        return Promise.reject(error)
      }
      ElMessage.error(msg || '服务器异常')
    } else {
      ElMessage.error('网络连接失败，请检查网络')
    }
    return Promise.reject(error)
  }
)

export default http
