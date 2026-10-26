import { onMounted, onUnmounted, watch } from 'vue'
import { useMessageStore } from '@/stores/message'
import { createRefreshQueue } from '@/utils/productChannel'

// 订单页面复用现有私有通知连接，保留分页和输入内容，仅静默重取数据。
export function useOrderRefresh(refresh, matches = () => true, blocked = () => false, fallbackMs = 30000) {
  const messages = useMessageStore()
  let alive = true, deferred = false, fallback = null
  const queue = createRefreshQueue(async () => {
    if (blocked()) { deferred = true; return }
    if (alive && document.visibilityState !== 'hidden') await refresh()
  })
  watch(() => messages.latestNotification, n => { if (n?.type === 'order' && matches(n)) queue.request() })
  watch(() => messages.connected, connected => { if (connected) queue.request() })
  watch(blocked, busy => { if (!busy && deferred) { deferred = false; queue.request() } })
  const resume = () => { if (document.visibilityState !== 'hidden') queue.request() }
  onMounted(() => {
    window.addEventListener('focus', resume); document.addEventListener('visibilitychange', resume)
    if (fallbackMs) fallback = window.setInterval(resume, fallbackMs)
  })
  onUnmounted(() => {
    alive = false; queue.stop()
    if (fallback !== null) window.clearInterval(fallback)
    window.removeEventListener('focus', resume); document.removeEventListener('visibilitychange', resume)
  })
}
