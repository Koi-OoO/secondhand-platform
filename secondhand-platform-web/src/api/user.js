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

export function requestPasswordResetCode(username, email) {
  return http.post('/user/password/forgot/request', { username, email })
}

export function verifyPasswordResetCode(username, email, code) {
  return http.post('/user/password/forgot/verify', { username, email, code })
}

export function resetPassword(resetToken, newPassword) {
  return http.post('/user/password/reset', { resetToken, newPassword })
}

export function getSecurityInfo() {
  return http.get('/user/security')
}

export function requestEmailBindCode(email) {
  return http.post('/user/email/bind/request', { email })
}

export function verifyEmailBindCode(email, code) {
  return http.post('/user/email/bind/verify', { email, code })
}

export function requestEmailUnbindCode() {
  return http.post('/user/email/unbind/request')
}

export function unbindEmail(code) {
  return http.post('/user/email/unbind', { code })
}

export function changePassword(oldPassword, newPassword) {
  return http.post('/user/password/change', { oldPassword, newPassword })
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
