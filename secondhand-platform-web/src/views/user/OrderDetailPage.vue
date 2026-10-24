<template>
  <main class="order-detail-page">
    <header class="detail-heading"><div><p class="eyebrow">交易记录</p><h1>订单详情</h1></div><el-button @click="backToOrders">我的订单</el-button></header>
    <div v-if="loading && !order" class="detail-panel"><el-skeleton :rows="6" animated /></div>
    <section v-else-if="error" class="detail-panel" role="alert"><h2>暂时无法查看订单</h2><p>{{ error }}</p><el-button @click="load">重试</el-button></section>
    <template v-else-if="order">
      <section class="detail-panel status-panel">
        <div><el-tag :type="order.status === 3 ? 'success' : 'info'">{{ statusText }}</el-tag><p>订单号：{{ order.orderNo }}</p></div>
        <div class="detail-actions">
          <el-button :loading="acting" @click="contact">联系{{ isBuyer ? '卖家' : '买家' }}</el-button>
          <el-button v-for="action in actions" :key="action" :type="action === 'reject' || action === 'cancel' ? 'danger' : 'primary'" :disabled="acting || (action === 'remind' && reminderCooling(order))" @click="perform(action)">{{ labels[action] }}</el-button>
          <el-button v-if="!isBuyer && order.status === 1" type="primary" @click="proposalVisible = true">{{ order.meetupTime ? '提议修改时间' : '提议面交时间' }}</el-button>
          <el-button :loading="loading" @click="load">刷新</el-button>
        </div>
        <p v-if="order.status === 5" class="progress-note">{{ isBuyer ? '等待卖家接单；接单后会收到通知。' : '买家已下单，请确认是否接单。接单后再协商面交时间。' }}</p>
        <p v-else-if="order.status === 1" class="progress-note">{{ order.acceptTime ? '卖家已接单。' : '' }}{{ order.meetupTime ? '面交时间已确认，实际交付后方可声明交付。' : '尚未确认面交时间。请先私信协商，由卖家发送预约卡片、买家确认后，才能声明交付。' }}</p>
        <p v-if="order.status === 2" class="progress-note">卖家声明已交付，尚待买家核实。未收到或有问题请反馈，不要提前确认完成。</p>
        <p v-if="order.status === 2 && order.lastRemindTime" class="progress-note">上次确认提醒：{{ formatDate(order.lastRemindTime) }}。每24小时可提醒一次。</p>
        <p v-if="order.status === 6" class="progress-note">买家已反馈问题，交易待协商，暂停确认提醒。请联系对方；解决问题并实际收到商品后，由买家确认完成。</p>
        <p v-if="order.status === 4" class="cancel-reason">{{ order.cancelReason || '未记录取消原因' }}</p>
      </section>
      <section class="detail-panel">
        <h2>商品与交易约定</h2>
        <p v-if="!order.snapshotTitle" class="legacy-note">历史订单未保存商品快照；以下名称和图片仅为当前商品参考信息，单价未记录。</p>
        <div class="detail-product">
          <button v-if="order.productImage" type="button" class="product-cover" aria-label="放大查看商品图片" @click="viewerVisible = true"><img :src="order.productImage" alt="商品封面" /></button>
          <div v-else class="product-cover empty-cover">无图片</div>
          <div><h3>{{ order.productTitle || '商品信息未记录或已移除' }}</h3><p>{{ order.snapshotTitle ? '下单时的商品信息' : '当前商品信息（仅供参考）' }}</p><el-button text @click="router.push({ name: 'ProductDetail', params: { id: order.productId } })">查看当前商品</el-button></div>
        </div>
        <dl class="detail-grid">
          <dt>下单单价</dt><dd>{{ order.unitPrice != null ? formatPrice(order.unitPrice) : '未记录' }}</dd>
          <dt>购买数量</dt><dd>{{ order.quantity ?? '未记录' }}</dd>
          <dt>商品小计</dt><dd>{{ formatPrice(order.productAmount) }}</dd>
          <dt>订单总金额</dt><dd class="order-total">{{ formatPrice(order.totalAmount) }}</dd>
          <dt>面交地点</dt><dd>{{ order.address || '未记录' }}</dd>
          <dt>面交时间</dt><dd>{{ order.meetupTime ? formatDate(order.meetupTime) : '尚未确认，请私信协商后由卖家发送预约卡片。' }}</dd>
          <dt>买家</dt><dd>{{ order.buyerNickname || `用户 #${order.buyerId}` }}</dd>
          <dt>卖家</dt><dd>{{ order.sellerNickname || `用户 #${order.sellerId}` }}</dd>
        </dl>
        <p class="transaction-note">请按订单地点面交，调整地点请在站内聊天中协商。线下验货并自行付款，平台不代收款、不提供交易担保，订单状态不代表付款状态。</p>
      </section>
      <section v-if="order.deliveryIssue" class="detail-panel"><h2>{{ order.status === 6 ? '待协商的问题' : '问题反馈历史' }}</h2><p class="evaluation-content">{{ order.deliveryIssue }}</p><p>{{ formatDate(order.deliveryIssueTime) }}</p><p v-if="order.status === 3">买家后续已确认收到商品，交易已完成。</p></section>
      <div class="detail-columns">
        <section class="detail-panel"><h2>交易记录</h2><ol class="order-timeline"><li v-for="event in timeline" :key="event.label"><strong>{{ event.label }}</strong><time>{{ event.time ? formatDate(event.time) : '未记录' }}</time></li></ol></section>
        <section class="detail-panel" id="evaluation"><h2>订单评价</h2>
          <template v-if="order.evaluation"><el-rate :model-value="order.evaluation.rating" disabled show-score /><p class="evaluation-content">{{ order.evaluation.content || '未填写文字评价' }}</p><p>{{ order.evaluation.anonymous ? '匿名评价' : '买家评价' }} · {{ formatDate(order.evaluation.createTime) }}</p></template>
          <p v-else>{{ order.status === 3 ? '买家尚未评价' : '订单完成后，买家可以评价本次交易。' }}</p>
          <el-button v-if="actions.includes('evaluate')" type="primary" @click="perform('evaluate')">去评价</el-button>
        </section>
      </div>
    </template>
    <el-dialog v-model="proposalVisible" title="提议面交时间" width="min(500px, 92vw)">
      <p>地点：{{ order?.address }}</p><p>只有买家确认后才生效。修改时间时，确认前保留原约定。</p>
      <el-date-picker v-model="proposedTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择未来的面交时间" style="max-width:100%" />
      <template #footer><el-button @click="proposalVisible = false">取消</el-button><el-button type="primary" :loading="proposing" @click="sendProposal">发送预约卡片</el-button></template>
    </el-dialog>
    <ImageViewer v-model="viewerVisible" :images="order?.productImage ? [order.productImage] : []" />
    <el-dialog v-model="evaluationVisible" title="评价本次交易" width="min(500px, 92vw)">
      <el-rate v-model="evaluation.rating" />
      <el-input v-model="evaluation.content" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="商品是否符合描述，面交体验如何？" />
      <el-checkbox v-model="evaluation.anonymous">匿名评价</el-checkbox>
      <template #footer><el-button @click="evaluationVisible = false">取消</el-button><el-button type="primary" :loading="acting" @click="submitEvaluation">提交评价</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { remindBuyer, reportDeliveryIssue, acceptOrder, getOrderDetail, cancelOrder, deliverOrder, rejectOrder, confirmOrder } from '@/api/order'
import { proposeMeetup } from '@/api/meetupProposal'
import { evaluate } from '@/api/evaluation'
import { createConversation } from '@/api/message'
import { useUserStore } from '@/stores/user'
import { useMessageStore } from '@/stores/message'
import { formatDate, formatPrice } from '@/utils/format'
import { ORDER_STATUS } from '@/utils/constant'
import { orderActions, orderTimeline, reminderCooling } from '@/utils/orderDetail'
import ImageViewer from '@/components/common/ImageViewer.vue'

const route = useRoute(), router = useRouter(), userStore = useUserStore(), messageStore = useMessageStore()
const order = ref(null), loading = ref(false), error = ref(''), acting = ref(false), viewerVisible = ref(false), evaluationVisible = ref(false)
const proposalVisible = ref(false), proposedTime = ref(''), proposing = ref(false)
const evaluation = reactive({ rating: 5, content: '', anonymous: false })
const isBuyer = computed(() => Number(order.value?.buyerId) === Number(userStore.userInfo?.id))
const actions = computed(() => order.value ? orderActions(order.value, userStore.userInfo?.id) : [])
const timeline = computed(() => order.value ? orderTimeline(order.value) : [])
const statusText = computed(() => order.value?.cancelType === 2 && order.value?.status === 4 ? '卖家拒绝交付' : ORDER_STATUS[order.value?.status])
const labels = { remind: '提醒买家确认', issue: '未收到／交易有问题', accept: '接单', cancel: '取消订单', deliver: '已交付，请买家确认', reject: '拒绝/取消交易', confirm: '确认收到，完成交易', evaluate: '评价订单' }
let loadVersion = 0
async function load() {
  const version = ++loadVersion
  loading.value = true
  error.value = ''
  try {
    const data = await getOrderDetail(route.params.id)
    if (version === loadVersion) order.value = data
  } catch (ex) {
    if (version === loadVersion) error.value = ex.message || '订单加载失败'
  } finally { if (version === loadVersion) loading.value = false }
}
watch(() => route.params.id, () => { order.value = null; evaluationVisible.value = false; proposalVisible.value = false; proposedTime.value = ''; load() }, { immediate: true })
watch(() => messageStore.latestNotification, n => { if (n?.type === 'order' && String(n.relatedId) === String(route.params.id) && !acting.value) load() })
function backToOrders() { router.push({ name: 'MyOrders', query: { tab: isBuyer.value ? 'bought' : 'sold' } }) }
async function contact() {
  if (acting.value) return
  acting.value = true
  try {
    const peerId = isBuyer.value ? order.value.sellerId : order.value.buyerId
    await createConversation(peerId, order.value.productId)
    await router.push({ name: 'Messages', query: { peerId, productId: order.value.productId } })
  } finally { acting.value = false }
}
async function perform(action) {
  if (acting.value || !actions.value.includes(action)) return
  const id = order.value.id
  if (action === 'evaluate') { evaluationVisible.value = true; return }
  let reason = ''
  if (action === 'issue' || action === 'reject') {
    const answer = await ElMessageBox.prompt(action === 'issue' ? '请说明未收到商品或遇到的问题，卖家会收到通知。' : '请输入取消原因，买家将收到通知。', labels[action], { inputValidator: v => !!v?.trim() || '请填写拒绝原因', inputPattern: /^[\s\S]{1,255}$/, inputErrorMessage: '原因不超过255字' }).catch(() => null)
    if (!answer) return
    reason = answer.value.trim()
  } else {
    const texts = { remind: '提醒买家核实收货？每24小时可提醒一次；提醒不会自动完成交易。', accept: '确认接受这笔订单？接单后请通过站内聊天协商面交时间。', cancel: '确定取消这笔订单吗？', deliver: '仅在实际交付商品后操作。这只是卖家的交付声明，须买家核实确认后交易才完成，不代表已付款。', confirm: '请在验货、收到商品并自行完成线下付款后确认。平台不代收款、不提供交易担保。' }
    const confirmed = await ElMessageBox.confirm(texts[action], labels[action], { type: 'warning' }).then(() => true).catch(() => false)
    if (!confirmed) return
  }
  if (order.value?.id !== id || !actions.value.includes(action)) return
  acting.value = true
  try {
    await ({ issue: reportDeliveryIssue, remind: remindBuyer, accept: acceptOrder, cancel: cancelOrder, deliver: deliverOrder, confirm: confirmOrder, reject: rejectOrder })[action](id, reason)
    ElMessage.success('操作成功')
    await load()
  } catch { await load() } finally { acting.value = false }
}
async function sendProposal() {
  if (proposing.value || !order.value || isBuyer.value || order.value.status !== 1) return
  if (!proposedTime.value || new Date(proposedTime.value.replace(' ', 'T')).getTime() <= Date.now()) { ElMessage.warning('请选择未来时间'); return }
  const targetOrder = { id: order.value.id, buyerId: order.value.buyerId, productId: order.value.productId }
  proposing.value = true
  try {
    await proposeMeetup(targetOrder.id, proposedTime.value)
    proposalVisible.value = false
    ElMessage.success('预约卡片已发送，等待买家确认')
    await router.push({ name: 'Messages', query: { peerId: targetOrder.buyerId, productId: targetOrder.productId } })
  } finally { proposing.value = false }
}
async function submitEvaluation() {
  if (acting.value || !actions.value.includes('evaluate')) return
  acting.value = true
  try {
    await evaluate(order.value.id, evaluation.rating, evaluation.content, evaluation.anonymous)
    evaluationVisible.value = false
    ElMessage.success('评价成功')
    await load()
  } finally { acting.value = false }
}
</script>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;
.order-detail-page { max-width: 1000px; margin: 0 auto; padding: 28px 16px 50px; }
.detail-heading, .status-panel { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
h1 { margin: 0; font-size: 30px; } h2 { margin: 0 0 20px; font-size: 20px; } h3 { margin: 0; overflow-wrap: anywhere; }
.eyebrow { color: $color-text-secondary; margin-bottom: 5px; }
.detail-heading { margin-bottom: 24px; }
.detail-panel { border: $border-ink; border-radius: $radius-lg; padding: 24px; background: $color-bg-card; margin-bottom: 22px; }
.detail-actions { display: flex; flex-wrap: wrap; gap: 8px; .el-button { margin-left: 0; } }
.detail-product { display: flex; gap: 20px; align-items: center; margin: 22px 0; }
.product-cover { width: 110px; height: 110px; flex-shrink: 0; border-radius: 12px; padding: 0; border: 1px solid $color-border-light; overflow: hidden; background: $color-paper; cursor: zoom-in; img { width: 100%; height: 100%; object-fit: cover; } }
.empty-cover { display: grid; place-items: center; color: $color-text-secondary; cursor: default; }
.detail-grid { display: grid; grid-template-columns: 110px minmax(0,1fr); gap: 15px; dt { color: $color-text-secondary; } dd { margin: 0; overflow-wrap: anywhere; } }
.order-total { font-size: 22px; font-weight: bold; color: $color-primary; }
.legacy-note, .transaction-note { padding: 14px; background: $color-paper; border-radius: 8px; color: $color-text-secondary; line-height: 1.8; }
.progress-note { flex-basis: 100%; color: $color-text-secondary; margin: 0; line-height: 1.8; }
.cancel-reason { flex-basis: 100%; margin: 0; overflow-wrap: anywhere; }
.detail-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 22px; }
.order-timeline { list-style: none; padding: 0 0 0 12px; margin: 0; li { border-left: 2px solid $color-border-light; padding: 0 0 24px 20px; } time { display: block; margin-top: 8px; color: $color-text-secondary; } }
.evaluation-content { white-space: pre-wrap; overflow-wrap: anywhere; }
@media(max-width: 600px) { .detail-columns { grid-template-columns: 1fr; gap: 0; } .detail-panel { padding: 18px; } .detail-product { gap: 12px; } .product-cover { width: 80px; height: 90px; } .detail-grid { grid-template-columns: 88px minmax(0,1fr); } }
</style>
