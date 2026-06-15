import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginAPI, register as registerAPI, getUserProfile, updateProfile as updateProfileAPI, logout as logoutAPI } from '@/api/user'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  // === 状态 ===
  const userInfo = ref(null)
  const loading = ref(false)
  const loginError = ref('')

  // === 计算属性 ===
  const isLoggedIn = computed(() => !!userInfo.value)
  const username = computed(() => userInfo.value?.username || '')
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '用户')
  const avatar = computed(() => userInfo.value?.avatar || '')

  // === 方法 ===

  /** 从 Session 恢复登录状态 */
  async function restoreSession() {
    try {
      const data = await getUserProfile({ skipAuthRedirect: true })
      userInfo.value = data
    } catch { /* 未登录 */ }
  }

  /** 登录 */
  async function login(username, password) {
    loading.value = true
    loginError.value = ''
    try {
      await loginAPI(username, password)
      await fetchProfile()
      return true
    } catch (e) {
      loginError.value = e?.message || '登录失败，请稍后再试'
      return false
    } finally {
      loading.value = false
    }
  }

  /** 注册 */
  async function register(form) {
    loading.value = true
    try {
      await registerAPI(form)
      return true
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  /** 拉取用户资料 */
  async function fetchProfile() {
    try {
      const data = await getUserProfile()
      userInfo.value = data
    } catch {
      logout()
    }
  }

  /** 更新资料 */
  async function updateProfile(form) {
    loading.value = true
    try {
      await updateProfileAPI(form)
      await fetchProfile()
      return true
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  /** 退出登录 */
  async function logout() {
    try { await logoutAPI() } catch { /* ignore */ }
    userInfo.value = null
    router.push('/login')
  }

  return {
    userInfo,
    loading,
    loginError,
    isLoggedIn,
    username,
    nickname,
    avatar,
    restoreSession,
    login,
    register,
    fetchProfile,
    updateProfile,
    logout
  }
})
