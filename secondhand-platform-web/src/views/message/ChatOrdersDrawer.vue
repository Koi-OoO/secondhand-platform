<template>
  <el-drawer :model-value="modelValue" :title="`与 ${peerName || 'TA'} 的订单`" size="min(480px, 100vw)" @update:model-value="emit('update:modelValue', $event)">
    <div class="chat-orders">
      <div class="orders-intro"><p>只展示你与对方的交易，进行中的订单优先。</p><el-button :loading="loading" @click="load">刷新</el-button></div>
      <el-skeleton v-if="loading" :rows="5" animated />
      <div v-else-if="error" class="orders-empty" role="alert"><p>{{ error }}</p><el-button @click="load">重试</el-button></div>
      <div v-else-if="!orders.length" class="orders-empty"><h3>暂无交易订单</h3><p>你和对方可以继续聊天，购买商品后订单会显示在这里。</p></div>
      <div v-else class="peer-order-list">
        <button v-for="order in orders" :key="order.id" type="button" class="peer-order" @click="openOrder(order)">
          <span class="peer-order__top"><span>{{ Number(order.buyerId) === Number(currentUserId) ? '我买到的' : '我卖出的' }}</span><el-tag :type="[1,2,5,6].includes(order.status) ? 'warning' : 'info'">{{ ORDER_STATUS[order.status] || '未知状态' }}</el-tag></span>
          <span class="peer-order__product"><img v-if="order.productImage" :src="order.productImage" alt="商品封面" /><span v-else class="cover-empty">无图</span><span class="peer-order__copy"><strong>{{ order.productTitle || '商品信息未记录或已移除' }}</strong><span>数量 {{ order.quantity ?? '未记录' }} · 合计 {{ formatPrice(order.totalAmount) }}</span><small v-if="!order.snapshotTitle">历史订单：名称和图片为当前参考信息</small></span></span>
          <span class="peer-order__meta">订单号：{{ order.orderNo }}</span>
          <span class="peer-order__meta">面交地点：{{ order.address || '未记录' }}</span>
          <span v-if="[1,2,5,6].includes(order.status)" class="peer-order__meta">面交时间：{{ order.meetupTime ? formatDate(order.meetupTime) : '尚未确认，双方私信协商' }}</span>
          <span class="peer-order__bottom"><time>{{ formatDate(order.createTime) }}</time><strong>查看订单 →</strong></span>
        </button>
      </div>
      <el-pagination v-if="!error && total > size" v-model:current-page="page" :page-size="size" :total="total" layout="prev, pager, next" @current-change="load" />
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getOrdersWithUser } from '@/api/order'
import { formatDate, formatPrice } from '@/utils/format'
import { ORDER_STATUS } from '@/utils/constant'
import { useMessageStore } from '@/stores/message'

const props = defineProps({ modelValue: Boolean, peerId: Number, peerName: String, currentUserId: Number })
const emit = defineEmits(['update:modelValue'])
const router = useRouter(), messageStore = useMessageStore()
const orders = ref([]), loading = ref(false), error = ref(''), page = ref(1), total = ref(0)
const size = 10
let version = 0
async function load() {
  const requestVersion = ++version
  if (!props.modelValue || !props.peerId) return
  const peerId = props.peerId
  loading.value = true
  error.value = ''
  try {
    const result = await getOrdersWithUser(peerId, { page: page.value, size })
    if (requestVersion !== version || peerId !== props.peerId) return
    orders.value = result.records || []
    total.value = Number(result.total || 0)
  } catch {
    if (requestVersion === version) { orders.value = []; error.value = '订单加载失败，请重试。' }
  } finally { if (requestVersion === version) loading.value = false }
}
function openOrder(order) {
  emit('update:modelValue', false)
  router.push({ name: 'OrderDetail', params: { id: order.id } })
}
watch(() => [props.modelValue, props.peerId, props.currentUserId], () => {
  version++
  orders.value = []; total.value = 0; page.value = 1; error.value = ''; loading.value = false
  if (props.modelValue && props.peerId) load()
}, { immediate: true })
watch(() => messageStore.latestNotification, item => {
  if (props.modelValue && item?.type === 'order') { page.value = 1; load() }
})
</script>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;
.chat-orders { min-width: 0; }
.orders-intro { display: flex; gap: 12px; align-items: center; justify-content: space-between; margin-bottom: 24px; p { color: $color-text-secondary; margin: 0; line-height: 1.7; } }
.orders-empty { padding: 45px 10px; text-align: center; color: $color-text-secondary; line-height: 1.8; }
.peer-order-list { display: grid; gap: 16px; margin-bottom: 22px; }
.peer-order { width: 100%; display: flex; flex-direction: column; gap: 13px; padding: 16px; border: $border-ink; border-radius: $radius-md; background: $color-bg-card; color: $color-text; text-align: left; font: inherit; cursor: pointer; }
.peer-order:hover, .peer-order:focus-visible { background: $color-bg-hover; }
.peer-order__top, .peer-order__bottom { display: flex; justify-content: space-between; align-items: center; gap: 8px; font-size: 13px; }
.peer-order__product { display: flex; align-items: center; gap: 12px; min-width: 0; img, .cover-empty { width: 65px; height: 72px; flex-shrink: 0; border-radius: 8px; object-fit: cover; background: $color-paper; } }
.cover-empty { display: grid; place-items: center; }
.peer-order__copy { display: flex; flex-direction: column; gap: 7px; min-width: 0; overflow-wrap: anywhere; }
.peer-order__meta { color: $color-text-secondary; font-size: 13px; overflow-wrap: anywhere; }
.peer-order__copy small { color: $color-text-secondary; line-height: 1.6; }
.peer-order__bottom { border-top: 1px solid $color-border-light; padding-top: 12px; time { color: $color-text-secondary; } }
</style>
