import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getRequestUserId } from '../utils/requestIdentity'

// 创建 Axios 实例
const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
  withCredentials: true,  // 跨域请求携带 Cookie（Session 鉴权需要）
  headers: {}
})

// === 请求拦截器 ===
http.interceptors.request.use(
  config => {
    const userId = getRequestUserId()
    if (userId) config.headers['X-Active-User-Id'] = userId
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
    if (code === 200) {
      // 当前账号操作订单成功后，通知顶部提示和已打开的订单页面补查最新数据。
      const config = response.config
      if (/^\/order(?:\/|$)/.test(config?.url || '')
          && ['post', 'put', 'patch', 'delete'].includes(config?.method)
          && typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
        window.dispatchEvent(new Event('order-changed'))
      }
      return data
    }
    if (!response.config?.skipErrorMessage) {
      ElMessage.error(message || '操作失败')
    }
    return Promise.reject(new Error(message || '操作失败'))
  },
  error => {
    if (error.response) {
      const msg = error.response.data?.message
      if (error.response.status === 409 && error.response.headers?.['x-account-changed'] === 'true') {
        // 另一个标签页已切换账号：刷新展示实际身份，不重试刚才的业务操作。
        window.location.reload()
        return Promise.reject(error)
      }
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
      if (!error.config?.skipErrorMessage) {
        ElMessage.error(msg || '服务器异常')
      }
    } else if (!error.config?.skipErrorMessage) {
      ElMessage.error('网络连接失败，请检查网络')
    }
    return Promise.reject(error)
  }
)

export default http
