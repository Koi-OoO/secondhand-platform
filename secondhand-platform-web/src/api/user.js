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
export function logout(config = {}) {
  return http.post('/user/logout', undefined, config)
}

// 管理令牌只来自后端的当前 Session，跟随每次操作更新，不存进浏览器持久存储。
function accountConfig(token) {
  return {
    skipAuthRedirect: true,
    skipErrorMessage: true,
    headers: token ? { 'X-Account-Management-Token': token } : {},
  }
}

export function getSessionAccounts() {
  return http.get('/user/accounts', accountConfig())
}

export function addSessionAccount(username, password, token) {
  return http.post('/user/accounts/add', { username, password }, accountConfig(token))
}

export function switchSessionAccount(userId, token) {
  return http.post('/user/accounts/switch', { userId }, accountConfig(token))
}

export function removeSessionAccount(userId, token) {
  return http.post('/user/accounts/remove', { userId }, accountConfig(token))
}

export function logoutSessionAccounts(token) {
  return http.post('/user/accounts/logout-all', undefined, accountConfig(token))
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
