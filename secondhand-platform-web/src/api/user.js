import http from './index'

/**
 * 用户登录
 * @param {string} username
 * @param {string} password
 * @returns {Promise<string>} 登录结果，登录态由浏览器 Session Cookie 维护
 */
export function login(username, password) {
  return http.post('/user/login', { username, password }, { skipErrorMessage: true })
}

/**
 * 用户注册
 * @param {object} userData - { username, password, ...optional }
 * @returns {Promise<string>} 成功消息
 */
export function register(userData) {
  return http.post('/user/register', userData)
}

/**
 * 获取当前用户完整信息（含密码字段，谨慎使用）
 */
export function getUserInfo() {
  return http.get('/user/profile')
}

/**
 * 获取用户个人资料（不含密码，推荐使用）
 */
export function getUserProfile(config = {}) {
  return http.get('/user/profile', config)
}

/**
 * 退出登录
 */
export function logout() {
  return http.post('/user/logout')
}

/**
 * 更新用户资料
 */
export function updateProfile(data) {
  return http.put('/user/update', data)
}

/**
 * 获取公开用户主页资料
 */
export function getPublicUserProfile(userId) {
  return http.get(`/user/public/${userId}`)
}
