<template>
  <div class="orders-page">
    <h2>我的订单</h2>

    <el-segmented
      v-model="tab"
      :options="tabOptions"
      class="orders-tabs"
      @change="handleTabChange"
    />

    <div v-if="list.length" class="batch-toolbar">
      <div class="batch-toolbar__meta">
        <span>已选 {{ selectedCount }} 条</span>
      </div>
      <div class="batch-toolbar__actions">
        <template v-if="tab === 'bought'">
          <el-button
            plain
            type="danger"
            :disabled="selectedHideOrders.length === 0"
            @click="handleBatchHide"
          >
            批量删除记录
          </el-button>
        </template>
        <template v-else>
          <el-button
            plain
            type="primary"
            :disabled="selectedPendingOrders.length === 0"
            :loading="batchDeliverLoading"
            @click="handleBatchDeliver"
          >
            批量交付
          </el-button>
          <el-button
            plain
            type="warning"
            :disabled="selectedPendingOrders.length === 0"
            @click="handleBatchReject"
          >
            批量拒绝交付
          </el-button>
          <el-button
            plain
            type="danger"
            :disabled="selectedHideOrders.length === 0"
            @click="handleBatchHide"
          >
            批量删除记录
          </el-button>
        </template>
      </div>
    </div>

    <div v-if="loading" class="loading-wrap">
      <el-skeleton :rows="4" animated />
    </div>

    <template v-else-if="list.length">
      <div class="orders-list">
        <article v-for="order in list" :key="order.id" class="order-card">
          <header class="order-card__header">
            <div class="order-card__header-left">
              <el-checkbox
                :model-value="selectedIds.has(order.id)"
                :disabled="!isSelectable(order)"
                @change="toggleSelect(order.id, $event)"
              />
              <span class="order-no">订单号：{{ order.orderNo }}</span>
            </div>
            <el-tag :type="statusColor(order.status)">{{ orderStatusText(order) }}</el-tag>
          </header>

          <div class="order-card__body">
            <div class="cover" @click="$router.push(`/product/${order.productId}`)">
              <img v-if="order.productImage" :src="order.productImage" alt="" />
              <span v-else>无图</span>
            </div>

            <div class="order-info">
              <h3 @click="$router.push(`/product/${order.productId}`)">{{ order.productTitle }}</h3>
              <div class="order-info__line">数量：{{ order.quantity || 1 }}</div>
              <div class="order-info__line">商品金额：{{ formatPrice(order.productAmount) }}</div>
              <div class="order-info__line order-info__total">订单金额：<strong>{{ formatPrice(order.totalAmount) }}</strong></div>
              <div class="order-info__line">
                {{ tab === 'bought' ? '卖家' : '买家' }}：{{ tab === 'bought' ? order.sellerNickname : order.buyerNickname }}
              </div>
              <div class="order-info__line">面交地点：{{ order.address }}</div>
              <div class="order-info__line">请线下验货并自行付款；平台不代收款、不提供交易担保，交付状态不代表已付款。</div>
              <div v-if="cancelNotice(order)" class="order-notice">{{ cancelNotice(order) }}</div>
            </div>
          </div>

          <footer class="order-card__footer">
            <template v-if="tab === 'bought'">
              <el-button
                v-if="order.status === 1"
                plain
                type="danger"
                :loading="actingId === order.id"
                @click="handleCancel(order)"
              >
                取消订单
              </el-button>
              <el-button
                v-if="order.status === 2"
                type="success"
                :loading="actingId === order.id"
                @click="handleConfirm(order)"
              >
                确认完成
              </el-button>
              <el-button
                v-if="order.status === 3 && !order.evaluated"
                plain
                type="warning"
                @click="openEval(order)"
              >
                评价
              </el-button>
              <el-tag v-if="order.status === 3 && order.evaluated" type="info">已评价</el-tag>
              <el-button
                v-if="isHideable(order)"
                plain
                type="danger"
                :loading="actingId === order.id"
                @click="handleHide(order)"
              >
                删除记录
              </el-button>
            </template>

            <template v-else>
              <el-button
                v-if="order.status === 1"
                type="primary"
                :loading="actingId === order.id"
                @click="handleDeliver(order)"
              >
                确认交付
              </el-button>
              <el-button
                v-if="order.status === 1"
                plain
                type="warning"
                :loading="actingId === order.id"
                @click="handleReject(order)"
              >
                拒绝交付
              </el-button>
              <el-button
                v-if="isHideable(order)"
                plain
                type="danger"
                :loading="actingId === order.id"
                @click="handleHide(order)"
              >
                删除记录
              </el-button>
            </template>
          </footer>
        </article>
      </div>
    </template>

    <div v-else class="empty-wrap">
      <div class="empty-wrap__icon">📦</div>
      <p>暂无订单</p>
    </div>

    <div v-if="total > size" class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next"
        background
        @current-change="fetchData"
      />
    </div>

    <el-dialog v-model="evalVisible" title="评价订单" width="420px" destroy-on-close>
      <div class="eval-form">
        <div class="eval-stars">
          <button
            v-for="n in 5"
            :key="n"
            type="button"
            class="star"
            :class="{ 'star--active': evalRating >= n }"
            @click="evalRating = n"
          >
            ★
          </button>
        </div>
        <el-input
          v-model="evalContent"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="补充评价内容"
        />
        <el-checkbox v-model="evalAnonymous" class="eval-anonymous">匿名评价</el-checkbox>
      </div>
      <template #footer>
        <el-button @click="evalVisible = false">取消</el-button>
        <el-button type="primary" :loading="evalLoading" :disabled="!evalRating" @click="submitEval">
          提交评价
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchHideBoughtOrders,
  batchHideSoldOrders,
  batchRejectOrders,
  batchDeliverOrders,
  cancelOrder,
  confirmOrder,
  getBoughtOrders,
  getSoldOrders,
  hideBoughtOrder,
  hideSoldOrder,
  rejectOrder,
  deliverOrder
} from '@/api/order'
import { evaluate } from '@/api/evaluation'
import { formatPrice } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const tab = ref('bought')
const list = ref([])
const loading = ref(true)
const page = ref(1)
const size = ref(12)
const total = ref(0)
const actingId = ref(null)
const selectedIds = reactive(new Set())

const evalVisible = ref(false)
const evalOrderId = ref(null)
const evalRating = ref(5)
const evalContent = ref('')
const evalAnonymous = ref(false)
const evalLoading = ref(false)

const batchDeliverLoading = ref(false)

const tabOptions = [
  { label: '我买到的', value: 'bought' },
  { label: '我卖出的', value: 'sold' }
]

const statusMap = {
  1: '待交付',
  2: '待确认',
  3: '已完成',
  4: '已取消'
}

const selectedOrders = computed(() => list.value.filter(order => selectedIds.has(order.id)))
const selectedHideOrders = computed(() => selectedOrders.value.filter(order => isHideable(order)))
const selectedPendingOrders = computed(() => selectedOrders.value.filter(order => order.status === 1))
const selectedCount = computed(() => selectedIds.size)

onMounted(() => {
  syncTabFromQuery()
  fetchData()
})

watch(
  () => route.query.tab,
  () => {
    const previousTab = tab.value
    syncTabFromQuery()
    if (previousTab !== tab.value) {
      selectedIds.clear()
      page.value = 1
      fetchData()
    }
  }
)

function syncTabFromQuery() {
  tab.value = route.query.tab === 'sold' ? 'sold' : 'bought'
}

function handleTabChange(value) {
  tab.value = value
  selectedIds.clear()
  page.value = 1
  router.replace({ query: value === 'sold' ? { tab: 'sold' } : {} })
  fetchData()
}

function statusColor(status) {
  return { 1: 'primary', 2: 'primary', 3: 'success', 4: 'info' }[status] || 'info'
}

function orderStatusText(order) {
  if (order.status === 4 && order.cancelType === 2) {
    return '卖家拒绝交付'
  }
  return statusMap[order.status] || '未知状态'
}

function cancelNotice(order) {
  if (order.status !== 4 || order.cancelType !== 2) {
    return ''
  }
  return order.cancelReason ? `拒绝原因：${order.cancelReason}` : '卖家拒绝交付'
}

function isHideable(order) {
  return order.status === 3 || order.status === 4
}

function isSelectable(order) {
  return isHideable(order) || (tab.value === 'sold' && order.status === 1)
}

function toggleSelect(orderId, checked) {
  if (checked) {
    selectedIds.add(orderId)
  } else {
    selectedIds.delete(orderId)
  }
}

async function fetchData() {
  loading.value = true
  try {
    const fetcher = tab.value === 'bought' ? getBoughtOrders : getSoldOrders
    const data = await fetcher({ page: page.value, size: size.value })
    list.value = data.records || []
    total.value = data.total || 0
    pruneSelection()
  } catch {
    list.value = []
    total.value = 0
    selectedIds.clear()
  } finally {
    loading.value = false
  }
}

function pruneSelection() {
  const currentIds = new Set(list.value.map(order => order.id))
  Array.from(selectedIds).forEach(id => {
    if (!currentIds.has(id)) {
      selectedIds.delete(id)
    }
  })
}

async function handleCancel(order) {
  const confirmed = await ElMessageBox.confirm('确认取消这笔订单吗？', '取消订单', { type: 'warning' })
    .then(() => true)
    .catch(() => false)
  if (!confirmed) {
    return
  }

  actingId.value = order.id
  try {
    await cancelOrder(order.id)
    order.status = 4
    order.cancelType = 1
    order.cancelReason = '买家取消订单'
    ElMessage.success('订单已取消')
  } finally {
    actingId.value = null
  }
}

async function handleConfirm(order) {
  const confirmed = await ElMessageBox.confirm('请在面交验货、收到商品并自行完成线下付款后确认完成。平台不代收款、不提供交易担保。', '确认完成', { type: 'success' })
    .then(() => true)
    .catch(() => false)
  if (!confirmed) {
    return
  }

  actingId.value = order.id
  try {
    await confirmOrder(order.id)
    order.status = 3
    ElMessage.success('已确认完成')
  } finally {
    actingId.value = null
  }
}

async function handleDeliver(order) {
  const confirmed = await ElMessageBox.confirm('请在实际面交并交付商品后确认。交付状态不代表已付款，双方应线下验货并自行付款，平台不代收款、不提供交易担保。', '确认交付', {
    confirmButtonText: '已面交，确认交付',
    type: 'warning'
  }).then(() => true).catch(() => false)
  if (!confirmed) return

  actingId.value = order.id
  try {
    await deliverOrder(order.id)
    order.status = 2
    ElMessage.success('已交付，等待买家确认')
  } finally {
    actingId.value = null
  }
}

async function handleReject(order) {
  const result = await ElMessageBox.prompt('请输入拒绝原因', '拒绝交付', {
    confirmButtonText: '确认拒绝',
    cancelButtonText: '取消',
    inputPlaceholder: '例如：库存盘点异常'
  }).catch(() => null)

  if (!result) {
    return
  }

  actingId.value = order.id
  try {
    await rejectOrder(order.id, result.value?.trim() || '')
    order.status = 4
    order.cancelType = 2
    order.cancelReason = result.value?.trim() || '卖家拒绝交付'
    ElMessage.success('已拒绝交付')
  } finally {
    actingId.value = null
  }
}

async function handleHide(order) {
  const api = tab.value === 'bought' ? hideBoughtOrder : hideSoldOrder
  const title = tab.value === 'bought' ? '删除购买记录' : '删除卖出记录'
  const message = tab.value === 'bought'
    ? '确认从买家视角删除这条订单记录吗？卖家仍可查看。'
    : '确认从卖家视角删除这条订单记录吗？买家仍可查看。'
  const confirmed = await ElMessageBox.confirm(message, title, { type: 'warning' })
    .then(() => true)
    .catch(() => false)
  if (!confirmed) {
    return
  }

  actingId.value = order.id
  try {
    await api(order.id)
    ElMessage.success('订单记录已删除')
    await removeOrdersFromCurrentPage([order.id])
  } finally {
    actingId.value = null
  }
}

async function handleBatchHide() {
  if (selectedHideOrders.value.length === 0) {
    ElMessage.warning('请先选择可删除记录的订单')
    return
  }
  const api = tab.value === 'bought' ? batchHideBoughtOrders : batchHideSoldOrders
  const title = tab.value === 'bought' ? '批量删除购买记录' : '批量删除卖出记录'
  const confirmed = await ElMessageBox.confirm(`确认批量删除 ${selectedHideOrders.value.length} 条订单记录吗？`, title, {
    type: 'warning'
  }).then(() => true).catch(() => false)
  if (!confirmed) {
    return
  }

  const ids = selectedHideOrders.value.map(order => order.id)
  const data = await api(ids)
  await applyBatchResult(data, {
    successMessage: '批量删除完成',
    removeOnSuccess: true
  })
}

async function handleBatchReject() {
  if (selectedPendingOrders.value.length === 0) {
    ElMessage.warning('请先选择待交付订单')
    return
  }
  const result = await ElMessageBox.prompt('请输入统一拒绝原因', '批量拒绝交付', {
    confirmButtonText: '确认拒绝',
    cancelButtonText: '取消',
    inputPlaceholder: '例如：库存盘点异常'
  }).catch(() => null)
  if (!result) {
    return
  }

  const ids = selectedPendingOrders.value.map(order => order.id)
  const data = await batchRejectOrders(ids, result.value?.trim() || '')
  await applyBatchResult(data, {
    successMessage: '批量拒绝处理完成',
    onSuccessIds: successIds => {
      list.value.forEach(order => {
        if (successIds.includes(order.id)) {
          order.status = 4
          order.cancelType = 2
          order.cancelReason = result.value?.trim() || '卖家拒绝交付'
        }
      })
    }
  })
}

async function handleBatchDeliver() {
  if (selectedPendingOrders.value.length === 0 || batchDeliverLoading.value) return
  const orders = [...selectedPendingOrders.value]
  const confirmed = await ElMessageBox.confirm(`请确认所选 ${orders.length} 笔订单均已实际面交并交付商品。交付状态不代表已付款，双方应线下验货并自行付款，平台不代收款、不提供交易担保。`, '批量确认交付', {
    confirmButtonText: '均已面交，确认交付',
    type: 'warning'
  }).then(() => true).catch(() => false)
  if (!confirmed) return

  batchDeliverLoading.value = true
  try {
    const data = await batchDeliverOrders(orders.map(order => ({ orderId: order.id })))
    await applyBatchResult(data, {
      successMessage: '批量交付完成',
      onSuccessIds: successIds => {
        list.value.forEach(order => {
          if (successIds.includes(order.id)) order.status = 2
        })
      }
    })
  } finally {
    batchDeliverLoading.value = false
  }
}

async function applyBatchResult(data, options = {}) {
  const successIds = data?.successIds || []
  const failures = data?.failures || []

  if (successIds.length > 0) {
    if (options.removeOnSuccess) {
      await removeOrdersFromCurrentPage(successIds)
    }
    if (options.onSuccessIds) {
      options.onSuccessIds(successIds)
    }
  }

  successIds.forEach(id => selectedIds.delete(id))
  failures.forEach(item => {
    if (item?.orderId != null) {
      selectedIds.delete(item.orderId)
    }
  })

  const summary = `成功 ${data?.successCount || 0} 条，失败 ${data?.failCount || 0} 条`
  if (failures.length > 0) {
    const firstFailure = failures[0]
    ElMessage.warning(`${options.successMessage || '批量操作完成'}：${summary}。首条失败原因：${firstFailure.reason}`)
  } else {
    ElMessage.success(`${options.successMessage || '批量操作完成'}：${summary}`)
  }
}

async function removeOrdersFromCurrentPage(orderIds) {
  const removeSet = new Set(orderIds)
  list.value = list.value.filter(item => !removeSet.has(item.id))
  total.value = Math.max(0, total.value - orderIds.length)
  orderIds.forEach(id => selectedIds.delete(id))

  if (list.value.length === 0 && page.value > 1 && total.value > 0) {
    page.value -= 1
    await fetchData()
  }
}

function openEval(order) {
  evalOrderId.value = order.id
  evalRating.value = 5
  evalContent.value = ''
  evalAnonymous.value = false
  evalVisible.value = true
}

async function submitEval() {
  evalLoading.value = true
  try {
    await evaluate(evalOrderId.value, evalRating.value, evalContent.value, evalAnonymous.value)
    const order = list.value.find(item => item.id === evalOrderId.value)
    if (order) order.evaluated = true
    evalVisible.value = false
    ElMessage.success('评价成功')
  } finally {
    evalLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.orders-page > h2 {
  margin: 0 0 $space-lg;
  font-size: $font-size-2xl;
  font-weight: $font-weight-bold;
  letter-spacing: $letter-spacing-tight;
  color: $color-text;
}

.orders-tabs {
  margin-bottom: $space-lg;
}

.batch-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: $space-md;
  padding: 12px 16px;
  background: $color-bg-warm;
  border: 1px solid $color-border-light;
  border-radius: $radius-md;
}

.batch-toolbar__meta {
  color: $color-text-secondary;
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
}

.batch-toolbar__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.orders-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.order-card {
  background: $color-bg-card;
  border: 1px solid $color-border-light;
  border-radius: $radius-lg;
  box-shadow: $shadow-card;
  padding: $space-lg;
  transition: border-color $transition-fast, box-shadow $transition-fast;

  &:hover {
    border-color: rgba($color-primary, 0.16);
    box-shadow: $shadow-md;
  }

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
    padding-bottom: 14px;
    border-bottom: 1px solid $color-border-light;
  }

  &__footer {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    padding-top: 14px;
    border-top: 1px solid $color-border-light;

    &:empty {
      display: none;
    }
  }

  &__body {
    display: flex;
    gap: 14px;
    padding: 16px 0;
  }
}

.order-card__header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.order-no {
  color: $color-text-muted;
  font-size: $font-size-sm;
}

.cover {
  width: 92px;
  height: 92px;
  flex-shrink: 0;
  border-radius: $radius-md;
  overflow: hidden;
  background: #f3eee8;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: $color-text-muted;
  font-size: $font-size-xs;
  transition: opacity $transition-fast;

  &:hover {
    opacity: 0.85;
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.order-info {
  flex: 1;
  min-width: 0;

  h3 {
    margin: 0 0 8px;
    font-size: $font-size-md;
    font-weight: $font-weight-semibold;
    color: $color-text;
    cursor: pointer;
    transition: color $transition-fast;

    &:hover {
      color: $color-primary;
    }
  }

  &__line {
    font-size: $font-size-sm;
    color: $color-text-secondary;
    margin-bottom: 4px;
  }

  &__total strong {
    color: $color-price;
    font-weight: $font-weight-bold;
    font-size: $font-size-md;
  }
}

.order-notice {
  margin-top: 8px;
  display: inline-flex;
  padding: 6px 10px;
  border-radius: $radius-sm;
  background: rgba($color-error, 0.08);
  color: $color-error;
  font-size: $font-size-xs;
  font-weight: $font-weight-medium;
}

.loading-wrap,
.pagination-wrap {
  margin-top: $space-lg;
}

.empty-wrap {
  text-align: center;
  color: $color-text-muted;
  padding: 64px 20px;
  background: $color-bg-warm;
  border: 1px dashed $color-border;
  border-radius: $radius-lg;

  &__icon {
    font-size: 48px;
    margin-bottom: 8px;
  }

  p {
    font-size: $font-size-md;
  }
}

.pagination-wrap {
  display: flex;
  justify-content: center;
}

.eval-stars {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.star {
  border: none;
  background: transparent;
  font-size: 30px;
  line-height: 1;
  color: $color-border;
  cursor: pointer;
  transition: color $transition-fast, transform $transition-fast;

  &:hover {
    transform: scale(1.12);
  }

  &--active {
    color: $color-warning;
  }
}

.eval-anonymous {
  margin-top: 12px;
}
</style>
