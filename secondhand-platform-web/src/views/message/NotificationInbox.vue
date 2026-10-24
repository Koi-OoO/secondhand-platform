<template>
  <section class="notification-inbox" aria-label="系统通知">
    <header class="inbox-header">
      <div><h1>系统通知</h1><p>订单进展和评价提醒，离线消息也会保留。</p></div>
      <div class="inbox-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button :disabled="!messageStore.notificationUnreadCount" :loading="markingAll" @click="readAll">全部已读</el-button>
      </div>
    </header>
    <div v-if="error" class="inbox-empty" role="alert">{{ error }} <el-button @click="load">重试</el-button></div>
    <div v-else v-loading="loading" class="notification-list">
      <p v-if="!items.length && !loading" class="inbox-empty">暂无系统通知，交易进展会显示在这里。</p>
      <button v-for="item in items" :key="item.id" class="notification-item" :class="{ unread: !item.isRead }" @click="open(item)">
        <span class="notification-dot" :class="{ visible: !item.isRead }"></span>
        <span class="notification-copy"><strong>{{ item.title }}</strong><span>{{ item.content }}</span><time>{{ formatDate(item.createTime) }}</time></span>
        <span class="notification-action">{{ item.type === 'order' && item.relatedId ? '查看订单' : '查看通知' }}</span>
      </button>
    </div>
    <el-pagination v-if="total > size" v-model:current-page="page" :page-size="size" :total="total" layout="prev, pager, next" @current-change="load" />
    <el-dialog v-model="detailVisible" title="通知详情" width="min(560px, 92vw)">
      <h3>{{ selected?.title }}</h3><p class="detail-content">{{ selected?.content }}</p>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getNotifications, markNotificationRead, markAllNotificationsRead } from '@/api/notification'
import { useMessageStore } from '@/stores/message'
import { formatDate } from '@/utils/format'

const router = useRouter()
const messageStore = useMessageStore()
const items = ref([]), page = ref(1), total = ref(0), loading = ref(false), error = ref('')
const size = 10
const markingAll = ref(false), detailVisible = ref(false), selected = ref(null)
let loadVersion = 0
async function load() {
  const version = ++loadVersion
  loading.value = true
  error.value = ''
  try {
    const data = await getNotifications({ page: page.value, size })
    if (version !== loadVersion) return
    items.value = data.records || []
    total.value = Number(data.total || 0)
  } catch {
    if (version === loadVersion) error.value = '通知加载失败，请重试。'
  } finally {
    if (version === loadVersion) loading.value = false
  }
}
async function open(item) {
  selected.value = item
  if (!item.isRead) {
    try {
      await markNotificationRead(item.id)
      item.isRead = true
      await messageStore.refreshUnreadCounts()
    } catch { /* 保持未读，可再次点击重试；请求拦截器显示错误 */ }
  }
  if (item.type === 'order' && item.relatedId) {
    await router.push({ name: 'OrderDetail', params: { id: item.relatedId } })
  } else {
    detailVisible.value = true
  }
}
async function readAll() {
  markingAll.value = true
  try {
    await markAllNotificationsRead()
    await Promise.all([load(), messageStore.refreshUnreadCounts()])
  } finally { markingAll.value = false }
}
onMounted(load)
watch(() => messageStore.latestNotification, () => { page.value = 1; load() })
watch(() => messageStore.connected, connected => { if (connected) { load(); messageStore.refreshUnreadCounts() } })
</script>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;
.notification-inbox { background: $color-paper; border: $border-ink; border-radius: $radius-lg; box-shadow: $shadow-hard; padding: 24px; min-height: 420px; }
.inbox-header { display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap; border-bottom: 1px solid $color-border-light; padding-bottom: 20px; }
h1 { margin: 0; font-size: 24px; }
.inbox-header p, time { color: $color-text-secondary; }
.inbox-actions { display: flex; align-items: center; }
.notification-list { min-height: 220px; }
.notification-item { width: 100%; display: flex; align-items: center; gap: 14px; text-align: left; border: 0; border-bottom: 1px solid $color-border-light; background: transparent; color: inherit; font: inherit; padding: 20px 8px; cursor: pointer; }
.notification-item:hover, .notification-item:focus-visible { background: rgba(0,0,0,.035); }
.notification-copy { display: flex; flex-direction: column; gap: 7px; min-width: 0; flex: 1; overflow-wrap: anywhere; }
.notification-dot { width: 8px; height: 8px; flex-shrink: 0; border-radius: 50%; background: transparent; }
.notification-dot.visible { background: #c45530; }
.notification-action { font-size: 13px; color: $color-text-secondary; flex-shrink: 0; }
.inbox-empty { padding: 50px 0; text-align: center; color: $color-text-secondary; }
.detail-content { white-space: pre-wrap; overflow-wrap: anywhere; }
@media(max-width: 600px) { .notification-inbox { padding: 16px; } .notification-action { display: none; } }
</style>
