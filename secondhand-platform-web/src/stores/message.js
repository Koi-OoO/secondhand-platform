import { defineStore } from 'pinia'
import { totalUnread } from '@/utils/notification'
import { computed, ref } from 'vue'
import { ElNotification } from 'element-plus'
import { getConversations, getMessageUnreadCount, getOnlineStatus } from '@/api/message'
import { getNotificationUnreadCount } from '@/api/notification'
import { useUserStore } from '@/stores/user'
import { getOnlineUserIdFromReceivedMessage, normalizePresenceStatus } from '@/utils/messageState'
import { bindSocketIdentity } from '@/utils/requestIdentity'
import { getUserProfile } from '@/api/user'

function resolveWsUrl() {
  if (import.meta.env.VITE_WS_URL) {
    return import.meta.env.VITE_WS_URL
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws`
}

export const useMessageStore = defineStore('message', () => {
  const conversations = ref([])
  const unreadCount = ref(0)
  const notificationUnreadCount = ref(0)
  const totalUnreadCount = computed(() => totalUnread(unreadCount.value, notificationUnreadCount.value))
  const connected = ref(false)
  const connecting = ref(false)
  const latestChatMessage = ref(null)
  const latestNotification = ref(null)
  const onlineStatus = ref({})

  let socket = null
  let reconnectTimer = null
  let heartbeatTimer = null
  let reconnectAttempts = 0
  let manualClose = false

  async function bootstrap() {
    const userStore = useUserStore()
    if (!userStore.isLoggedIn) {
      reset()
      return
    }

    await Promise.allSettled([
      refreshConversations(),
      refreshUnreadCounts()
    ])
    await refreshOnlineStatus(conversations.value.map(item => item.peerId))
    connect()
  }

  async function refreshConversations() {
    const userStore = useUserStore()
    if (!userStore.isLoggedIn) {
      conversations.value = []
      return
    }

    try {
      conversations.value = await getConversations({ skipErrorMessage: true })
    } catch {
      // 暂时断网时保留当前会话列表，退出登录仍由 reset 清理。
    }
  }

  async function refreshUnreadCounts() {
    const userStore = useUserStore()
    if (!userStore.isLoggedIn) {
      unreadCount.value = 0
      notificationUnreadCount.value = 0
      return
    }

    const [messageResult, notificationResult] = await Promise.allSettled([
      getMessageUnreadCount({ skipErrorMessage: true }),
      getNotificationUnreadCount({ skipErrorMessage: true })
    ])
    if (messageResult.status === 'fulfilled') {
      unreadCount.value = Number(messageResult.value || 0)
    }
    if (notificationResult.status === 'fulfilled') {
      notificationUnreadCount.value = Number(notificationResult.value || 0)
    }
  }

  async function refreshOnlineStatus(userIds = []) {
    const userStore = useUserStore()
    if (!userStore.isLoggedIn) {
      onlineStatus.value = {}
      return {}
    }

    const ids = [...new Set(userIds.map(Number).filter(id => id > 0))]
    if (!ids.length) {
      return onlineStatus.value
    }

    try {
      const data = await getOnlineStatus(ids, { skipErrorMessage: true })
      const normalized = Object.fromEntries(
        Object.entries(data || {}).map(([id, status]) => [id, normalizePresenceStatus(status)])
      )
      onlineStatus.value = {
        ...onlineStatus.value,
        ...normalized
      }
      return onlineStatus.value
    } catch {
      return onlineStatus.value
    }
  }

  function getUserPresence(userId) {
    const id = Number(userId)
    if (!id) {
      return normalizePresenceStatus(null)
    }
    return normalizePresenceStatus(onlineStatus.value[String(id)] ?? onlineStatus.value[id])
  }

  function isUserOnline(userId) {
    return getUserPresence(userId).online
  }

  function markUserOnline(userId) {
    const id = Number(userId)
    if (!id) {
      return
    }
    onlineStatus.value = {
      ...onlineStatus.value,
      [id]: {
        online: true,
        lastActiveTime: new Date().toISOString()
      }
    }
  }

  function connect() {
    const userStore = useUserStore()
    if (!userStore.isLoggedIn || connecting.value) {
      return
    }
    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
      return
    }

    manualClose = false
    connecting.value = true
    socket = new WebSocket(bindSocketIdentity(resolveWsUrl()))

    socket.onopen = () => {
      connected.value = true
      connecting.value = false
      reconnectAttempts = 0
      startHeartbeat()
      // 重连期间可能错过推送，未读数和会话摘要以服务端重新查询为准。
      refreshConversations()
      refreshUnreadCounts()
    }

    socket.onmessage = event => {
      if (event.data === 'pong') {
        return
      }
      handleSocketPayload(event.data)
    }

    socket.onerror = () => {
      connecting.value = false
    }

    socket.onclose = async () => {
      connected.value = false
      connecting.value = false
      stopHeartbeat()
      if (!manualClose && useUserStore().isLoggedIn) {
        // 握手失败时浏览器不提供 HTTP 状态；用普通身份请求触发跨标签页409刷新或过期401跳转。
        try { await getUserProfile({ skipErrorMessage: true }) } catch { /* 请求拦截器处理身份变化 */ }
        if (manualClose || !useUserStore().isLoggedIn) return
        scheduleReconnect()
      }
    }
  }

  function handleSocketPayload(raw) {
    let payload
    try {
      payload = JSON.parse(raw)
    } catch {
      return
    }

    if (payload?.type === 'chat' && payload.data) {
      const userStore = useUserStore()
      latestChatMessage.value = {
        ...payload.data,
        __receivedAt: Date.now()
      }
      const onlineUserId = getOnlineUserIdFromReceivedMessage(payload.data, userStore.userInfo?.id)
      if (onlineUserId) {
        markUserOnline(onlineUserId)
      }
      refreshConversations()
      refreshUnreadCounts()
      return
    }

    if (payload?.type === 'notification' && payload.data) {
      latestNotification.value = {
        ...payload.data,
        __receivedAt: Date.now()
      }
      refreshUnreadCounts()
      ElNotification({
        title: payload.data.title || '新通知',
        message: payload.data.content || '',
        type: 'info',
        duration: 4200
      })
    }
  }

  function startHeartbeat() {
    stopHeartbeat()
    heartbeatTimer = window.setInterval(() => {
      if (socket?.readyState === WebSocket.OPEN) {
        socket.send('ping')
      }
    }, 25_000)
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      window.clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  function scheduleReconnect() {
    if (reconnectTimer) {
      return
    }
    const delay = Math.min(1000 * (2 ** reconnectAttempts), 15_000)
    reconnectAttempts += 1
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null
      connect()
    }, delay)
  }

  function disconnect() {
    manualClose = true
    if (reconnectTimer) {
      window.clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    stopHeartbeat()
    if (socket) {
      socket.close()
      socket = null
    }
    connected.value = false
    connecting.value = false
  }

  function reset() {
    disconnect()
    conversations.value = []
    unreadCount.value = 0
    notificationUnreadCount.value = 0
    latestChatMessage.value = null
    latestNotification.value = null
    onlineStatus.value = {}
  }

  return {
    conversations,
    unreadCount,
    notificationUnreadCount,
    totalUnreadCount,
    connected,
    connecting,
    latestChatMessage,
    latestNotification,
    onlineStatus,
    bootstrap,
    refreshConversations,
    refreshUnreadCounts,
    refreshOnlineStatus,
    getUserPresence,
    isUserOnline,
    markUserOnline,
    connect,
    disconnect,
    reset
  }
})
