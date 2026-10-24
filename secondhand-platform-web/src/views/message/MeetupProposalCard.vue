<template>
  <article class="meetup-card">
    <header><strong>面交时间确认</strong><el-tag>{{ statusText }}</el-tag></header>
    <template v-if="proposal">
      <h3>{{ proposal.productTitle || '历史订单商品（查看订单了解详情）' }}</h3>
      <p>订单号：{{ proposal.orderNo }}</p><p>地点：{{ proposal.address }}</p>
      <p class="proposed-time">提议时间：{{ formatDate(proposal.proposedTime) }}</p>
      <p v-if="proposal.currentMeetupTime && proposal.currentMeetupTime !== proposal.proposedTime" class="agreement">当前约定：{{ formatDate(proposal.currentMeetupTime) }}。新时间确认前，原约定仍有效。</p>
      <p v-if="proposal.effectiveStatus === 'pending' && !canRespond" class="agreement">{{ statusText === '已失效' ? '请重新协商，由卖家再次提议。' : '等待订单买家确认。' }}</p>
      <div class="card-actions"><template v-if="canRespond"><el-button type="primary" :loading="acting" @click="respond(true)">确认时间</el-button><el-button :disabled="acting" @click="respond(false)">时间不合适</el-button></template><el-button text @click="router.push({ name: 'OrderDetail', params: { id: proposal.orderId } })">查看订单</el-button><el-button text :loading="loading" @click="load">刷新</el-button></div>
    </template>
    <p v-if="error" role="alert">{{ error }} <el-button text @click="load">重试</el-button></p>
  </article>
</template>
<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMeetupProposal, respondToMeetup } from '@/api/meetupProposal'
import { canRespondToProposal, proposalTime } from '@/utils/meetupProposal'
import { formatDate } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import { useMessageStore } from '@/stores/message'
const props = defineProps({ proposalId: Number, initial: Object })
const router = useRouter(), userStore = useUserStore(), messageStore = useMessageStore()
const proposal = ref(null), acting = ref(false), loading = ref(false), error = ref(''), now = ref(Date.now())
const timer = setInterval(() => { now.value = Date.now() }, 1000)
onUnmounted(() => clearInterval(timer))
const canRespond = computed(() => canRespondToProposal(proposal.value, userStore.userInfo?.id, now.value))
const statusText = computed(() => {
  const p = proposal.value
  if (!p) return loading.value ? '加载中' : '预约卡片'
  if (p.effectiveStatus === 'pending' && (!p.proposedTime || proposalTime(p.proposedTime) <= now.value)) return '已失效'
  return { pending: '待买家确认', confirmed: '已确认', declined: '未同意', expired: '已失效' }[p.effectiveStatus] || '已失效'
})
let version = 0
async function load() {
  const current = ++version
  loading.value = true; error.value = ''
  try { const data = await getMeetupProposal(props.proposalId); if (current === version) proposal.value = data }
  catch { if (current === version) { error.value = '卡片加载失败，请刷新后操作'; proposal.value = null } }
  finally { if (current === version) loading.value = false }
}
watch(() => [props.proposalId, props.initial], () => {
  version++; proposal.value = props.initial || null
  if (!props.initial?.effectiveStatus) load()
}, { immediate: true })
watch(() => messageStore.latestNotification, n => { if (n?.type === 'order' && n.relatedId === proposal.value?.orderId) load() })
watch(() => messageStore.latestChatMessage, m => { if (m?.meetupProposal?.orderId === proposal.value?.orderId) load() })
async function respond(accept) {
  if (acting.value || !canRespond.value) return
  const confirmed = await ElMessageBox.confirm(accept ? '确认按卡片上的时间和订单地点面交？' : '选择时间不合适后，请继续私信协商，由卖家重新提议。', accept ? '确认面交时间' : '时间不合适').then(() => true).catch(() => false)
  if (!confirmed) return
  acting.value = true
  try { proposal.value = await respondToMeetup(props.proposalId, accept); ElMessage.success(accept ? '时间已确认' : '已告知卖家时间不合适') }
  catch { await load() }
  finally { acting.value = false }
}
</script>
<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;
.meetup-card { width: min(360px, 100%); min-width: 0; border: $border-ink; border-radius: 12px; padding: 18px; background: $color-bg-card; color: $color-text; text-align: left; overflow-wrap: anywhere; header { display: flex; gap: 12px; align-items: center; justify-content: space-between; } h3 { font-size: 16px; margin: 16px 0; } p { margin: 10px 0; line-height: 1.7; font-size: 13px; } }
.proposed-time { font-weight: bold; }
.agreement { color: $color-text-secondary; }
.card-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; .el-button { margin-left: 0; } }
</style>
