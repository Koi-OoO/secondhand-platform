import { computed, onMounted, onUnmounted, watch } from 'vue'
import { getProductAvailability } from '@/api/product'
import { createProductChannel, createRefreshQueue } from '@/utils/productChannel'

// 只补查公开库存/状态，不调用会增加浏览量的详情接口。
export function useProductAvailability(getProducts, apply) {
  const signature = computed(() => [...new Set(getProducts().map(p => Number(p?.id)).filter(id => id > 0))].sort((a,b) => a-b).slice(0,50).join(','))
  let alive = false, revision = 0, fallback = null
  const visible = () => document.visibilityState !== 'hidden'
  const queue = createRefreshQueue(async () => {
    const version = revision, key = signature.value
    if (!alive || !visible() || !key) return
    const rows = await getProductAvailability(key.split(',').map(Number))
    if (alive && visible() && version === revision && key === signature.value) apply(rows)
  })
  const invalidate = () => { revision++; if (alive && visible()) queue.request() }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const channel = createProductChannel({
    url: import.meta.env.VITE_PRODUCT_WS_URL || `${protocol}//${window.location.host}/ws/products`,
    onChange: invalidate, onReady: invalidate
  })
  function sync() {
    if (!alive) return
    revision++
    if (visible() && signature.value) { channel.start(signature.value.split(',').map(Number)); queue.request() }
    else channel.stop()
  }
  watch(signature, sync)
  onMounted(() => {
    alive = true; sync()
    // WebSocket 长期不可用时仍可恢复；后台页面不发查询。
    fallback = window.setInterval(invalidate, 30000)
    window.addEventListener('focus', invalidate)
    document.addEventListener('visibilitychange', sync)
  })
  onUnmounted(() => {
    alive = false; revision++; channel.stop(); queue.stop()
    if (fallback !== null) window.clearInterval(fallback)
    window.removeEventListener('focus', invalidate)
    document.removeEventListener('visibilitychange', sync)
  })
  return { refreshAvailability: invalidate }
}
