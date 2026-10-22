import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { login as loginAPI, register as registerAPI, getUserProfile, updateProfile as updateProfileAPI,
  getSessionAccounts, addSessionAccount, switchSessionAccount, removeSessionAccount, logoutSessionAccounts } from '@/api/user'
import { rememberRecentAccount, removeRecentAccount } from '@/utils/recentAccounts'
import { setRequestUserId } from '@/utils/requestIdentity'

export const useUserStore = defineStore('user', () => {
  // === 状态 ===
  const userInfo = ref(null)
  const loading = ref(false)
  const loginError = ref('')
  const switching = ref(false)
  const sessionAccounts = ref([])
  // 令牌只在这次页面运行的内存中使用，不能当作最近账号资料保存。
  let accountToken = null

  watch(() => userInfo.value?.id, setRequestUserId, { immediate: true, flush: 'sync' })

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
      rememberRecentAccount(data)
    } catch { /* 未登录 */ }
  }

  /** 登录 */
  async function login(username, password) {
    if (loading.value || switching.value) return false
    loading.value = true
    loginError.value = ''
    try {
      await loginAPI(username, password)
      // 登录和获取当前身份都成功后才展示新用户、记住账号；不能把资料获取失败当作登录成功。
      const profile = await getUserProfile({ skipAuthRedirect: true, skipErrorMessage: true })
      if (!profile?.id || !profile?.username) throw new Error('无法获取账号资料，请稍后重试')
      userInfo.value = profile
      rememberRecentAccount(profile)
      return true
    } catch (e) {
      userInfo.value = null
      loginError.value = e?.response?.data?.message || e?.message || '登录失败，请稍后再试'
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
      userInfo.value = null
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

  function applyAccountState(state) {
    sessionAccounts.value = Array.isArray(state?.accounts) ? state.accounts : []
    accountToken = state?.csrfToken || null
  }

  /** 面板打开时读取后端已授权账号，最近用户名记录不能用来免密登录。 */
  async function loadSessionAccounts() {
    const state = await getSessionAccounts()
    applyAccountState(state)
    return state
  }

  async function managementToken() {
    if (!accountToken) await loadSessionAccounts()
    if (!accountToken) throw new Error('登录会话已失效，请重新登录')
    return accountToken
  }

  /** 添加时验证密码，但保留当前登录身份。 */
  async function addAccount(username, password) {
    if (switching.value || loading.value) return false
    switching.value = true
    try {
      const state = await addSessionAccount(username, password, await managementToken())
      applyAccountState(state)
      const added = sessionAccounts.value.find(account => account.username === username)
      if (added) rememberRecentAccount(added)
      return true
    } finally { switching.value = false }
  }

  /** 后端确认切换后重新打开首页，清掉旧账号的消息连接和页面状态。 */
  async function switchAccount(userId) {
    if (switching.value || loading.value) return false
    switching.value = true
    try {
      const state = await switchSessionAccount(userId, await managementToken())
      applyAccountState(state)
      const selected = sessionAccounts.value.find(account => account.userId === state.currentUserId)
      if (selected) rememberRecentAccount(selected)
      userInfo.value = null
      loginError.value = ''
      window.location.assign('/')
      return true
    } finally { switching.value = false }
  }

  /** 移除的是本次会话的登录授权，不会删除数据库账号。 */
  async function removeAccount(userId) {
    if (switching.value || loading.value) return false
    switching.value = true
    try {
      const removed = sessionAccounts.value.find(account => account.userId === userId)
      const previousId = userInfo.value?.id
      const state = await removeSessionAccount(userId, await managementToken())
      applyAccountState(state)
      if (removed) removeRecentAccount(removed.username)
      if (state.currentUserId !== previousId) {
        userInfo.value = null
        window.location.assign(state.currentUserId ? '/' : '/login')
      }
      return true
    } finally { switching.value = false }
  }

  /** 必须等后端撤销全部授权成功，再清理当前页面。网络失败时保留当前身份。 */
  async function logoutAllAccounts() {
    if (switching.value || loading.value) return false
    switching.value = true
    try {
      await logoutSessionAccounts(await managementToken())
      applyAccountState(null)
      userInfo.value = null
      window.location.assign('/login')
      return true
    } finally { switching.value = false }
  }

  const logout = logoutAllAccounts

  return {
    userInfo,
    loading,
    loginError,
    switching,
    sessionAccounts,
    isLoggedIn,
    username,
    nickname,
    avatar,
    restoreSession,
    login,
    register,
    fetchProfile,
    updateProfile,
    logout,
    switchAccount,
    loadSessionAccounts,
    addAccount,
    removeAccount,
    logoutAllAccounts
  }
})
