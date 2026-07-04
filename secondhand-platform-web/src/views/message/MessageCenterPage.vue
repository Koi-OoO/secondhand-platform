<template>
  <main class="messages-page">
    <section class="messages-shell">
      <aside class="conversation-pane">
        <div class="pane-header">
          <div>
            <p class="eyebrow">Messages</p>
            <h1>消息中心</h1>
          </div>
          <el-button circle :icon="Refresh" :loading="conversationsLoading" @click="refreshAll" />
        </div>

        <div class="connection-row">
          <span class="status-dot" :class="{ 'status-dot--on': messageStore.connected }"></span>
          <span>{{ messageStore.connected ? '实时推送已连接' : '实时推送未连接' }}</span>
        </div>

        <div v-if="conversationItems.length" class="conversation-list">
          <button
            v-for="item in conversationItems"
            :key="item.peerId"
            type="button"
            class="conversation-item"
            :class="{ 'conversation-item--active': Number(item.peerId) === selectedPeerId }"
            @click="selectPeer(item.peerId)"
          >
            <span class="avatar-with-status">
              <el-avatar :size="42" :src="item.peerAvatar">
                {{ displayPeerName(item).charAt(0) }}
              </el-avatar>
              <span class="peer-status-dot" :class="{ 'peer-status-dot--online': isPeerOnline(item.peerId) }"></span>
            </span>
            <div class="conversation-item__body">
              <div class="conversation-item__top">
                <strong>{{ displayPeerName(item) }}</strong>
                <span class="presence-label" :class="presenceClass(item.peerId)">
                  {{ presenceLabel(item.peerId) }}
                </span>
              </div>
              <p>{{ item.lastContent || '打开会话开始沟通' }}</p>
            </div>
            <span v-if="item.unreadCount > 0" class="unread-badge">{{ displayCount(item.unreadCount) }}</span>
          </button>
        </div>

        <div v-else class="pane-empty">
          <el-icon><ChatDotRound /></el-icon>
          <p>暂无会话</p>
          <span>从商品详情页联系卖家后，会话会显示在这里。</span>
        </div>
      </aside>

      <section class="chat-pane">
        <template v-if="selectedPeerId">
          <header class="chat-header">
            <div class="chat-peer">
              <span class="avatar-with-status avatar-with-status--large">
                <el-avatar :size="44" :src="selectedPeerAvatar">
                  {{ selectedPeerName.charAt(0) }}
                </el-avatar>
                <span class="peer-status-dot" :class="{ 'peer-status-dot--online': selectedPeerOnline }"></span>
              </span>
              <div>
                <h2>{{ selectedPeerName }}</h2>
                <p>
                  <span class="presence-label presence-label--header" :class="presenceClass(selectedPeerId)">
                    {{ selectedPeerPresenceLabel }}
                  </span>
                  <template v-if="selectedProductId"> · 关联商品 #{{ selectedProductId }}</template>
                </p>
              </div>
            </div>
            <el-tag :type="messageStore.connected ? 'success' : 'info'" effect="light" round>
              {{ messageStore.connected ? '在线推送' : '普通模式' }}
            </el-tag>
          </header>

          <div ref="chatBody" class="chat-body">
            <div v-if="chatLoading" class="chat-loading">
              <div v-for="i in 4" :key="i" class="message-skeleton" :class="{ 'message-skeleton--right': i % 2 === 0 }"></div>
            </div>

            <div v-else-if="!messages.length" class="chat-empty">
              <el-icon><ChatDotRound /></el-icon>
              <p>还没有聊天记录</p>
              <span>发送第一条消息，和对方确认物品细节、面交地点或库存。</span>
            </div>

            <div v-else class="message-list">
              <div
                v-for="message in messages"
                :key="message.id"
                class="message-row"
                :class="{ 'message-row--mine': isMine(message) }"
              >
                <div class="message-bubble">
                  <p>{{ message.content }}</p>
                  <time>{{ formatDate(message.createTime) }}</time>
                </div>
              </div>
            </div>
          </div>

          <form class="composer" @submit.prevent="sendCurrentMessage">
            <el-input
              v-model="draft"
              type="textarea"
              :rows="2"
              maxlength="1000"
              show-word-limit
              resize="none"
              placeholder="输入消息，按发送提交"
              @keydown.enter.exact.prevent="sendCurrentMessage"
            />
            <el-button type="primary" :loading="sending" :disabled="!draft.trim()" native-type="submit">
              <el-icon><Promotion /></el-icon>
              发送
            </el-button>
          </form>
        </template>

        <div v-else class="chat-placeholder">
          <el-icon><ChatDotRound /></el-icon>
          <h2>选择一个会话</h2>
          <p>从左侧选择会话，或到商品详情页点击“联系卖家”。</p>
        </div>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, Promotion, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { createConversation, getChat, sendMessage } from '@/api/message'
import { getPublicUserProfile } from '@/api/user'
import { useMessageStore } from '@/stores/message'
import { useUserStore } from '@/stores/user'
import { formatDate } from '@/utils/format'
import {
  formatPresenceStatus,
  getPeerIdFromMessage,
  mergeConversationItems,
  normalizeChatRecords,
  pruneResolvedPendingConversations,
  upsertMessage,
  upsertPendingConversation
} from '@/utils/messageState'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const messageStore = useMessageStore()

const selectedPeerId = ref(null)
const selectedProductId = ref(null)
const pendingConversations = ref([])
const messages = ref([])
const draft = ref('')
const chatLoading = ref(false)
const conversationsLoading = ref(false)
const sending = ref(false)
const chatBody = ref(null)
let onlineTimer = null

const currentUserId = computed(() => Number(userStore.userInfo?.id || 0))
const conversationItems = computed(() => {
  return mergeConversationItems(messageStore.conversations, pendingConversations.value)
})

const selectedConversation = computed(() =>
  conversationItems.value.find(item => Number(item.peerId) === selectedPeerId.value)
)

const selectedPendingConversation = computed(() =>
  pendingConversations.value.find(item => Number(item.peerId) === selectedPeerId.value)
)

const selectedConversationItem = computed(() => {
  return selectedConversation.value || selectedPendingConversation.value || {}
})

const selectedPeerName = computed(() => displayPeerName(selectedConversationItem.value))
const selectedPeerAvatar = computed(() => selectedConversationItem.value?.peerAvatar || '')
const selectedPeerOnline = computed(() => isPeerOnline(selectedPeerId.value))
const selectedPeerPresenceLabel = computed(() => presenceLabel(selectedPeerId.value))

onMounted(async () => {
  await refreshAll()
  if (!route.query.peerId && messageStore.conversations.length) {
    selectPeer(messageStore.conversations[0].peerId, true)
  }
  onlineTimer = window.setInterval(refreshVisibleOnlineStatus, 30_000)
})

onUnmounted(() => {
  if (onlineTimer) {
    window.clearInterval(onlineTimer)
  }
})

watch(
  () => [route.query.peerId, route.query.productId],
  () => syncRoutePeer(),
  { immediate: true }
)

watch(
  () => messageStore.latestChatMessage,
  async message => {
    if (!message) {
      return
    }
    const peerId = getPeerIdFromMessage(message, currentUserId.value)
    if (peerId && peerId === selectedPeerId.value) {
      messages.value = upsertMessage(messages.value, message)
      await markActiveConversationRead()
      scrollToBottom()
    }
  }
)

async function refreshAll() {
  conversationsLoading.value = true
  try {
    await Promise.all([
      refreshConversationList(),
      messageStore.refreshUnreadCounts()
    ])
    await refreshVisibleOnlineStatus()
  } finally {
    conversationsLoading.value = false
  }
}

async function syncRoutePeer() {
  const peerId = Number(route.query.peerId || 0)
  selectedProductId.value = route.query.productId ? Number(route.query.productId) : null

  if (!peerId) {
    return
  }
  if (peerId === currentUserId.value) {
    ElMessage.warning('不能给自己发送私信')
    router.replace('/messages')
    return
  }

  selectedPeerId.value = peerId
  await ensureServerConversation(peerId)
  await refreshVisibleOnlineStatus()
  await loadChat(peerId)
}

async function refreshConversationList() {
  await messageStore.refreshConversations()
  pendingConversations.value = pruneResolvedPendingConversations(
    pendingConversations.value,
    messageStore.conversations
  )
}

async function ensurePendingConversation(peerId) {
  if (messageStore.conversations.some(item => Number(item.peerId) === peerId)) {
    pendingConversations.value = pruneResolvedPendingConversations(
      pendingConversations.value,
      messageStore.conversations
    )
    return
  }

  const existing = pendingConversations.value.find(item => Number(item.peerId) === peerId)
  if (existing) {
    pendingConversations.value = upsertPendingConversation(pendingConversations.value, {
      ...existing,
      productId: selectedProductId.value || existing.productId || null
    })
    return
  }

  let peer = null
  try {
    peer = await getPublicUserProfile(peerId)
  } catch {
    peer = null
  }
  pendingConversations.value = upsertPendingConversation(pendingConversations.value, {
    peerId,
    peerNickname: peer?.nickname || peer?.username || `用户 ${peerId}`,
    peerAvatar: peer?.avatar || '',
    productId: selectedProductId.value,
    lastContent: '从商品详情发起会话',
    lastTime: null,
    unreadCount: 0
  })
}

async function ensureServerConversation(peerId) {
  try {
    await createConversation(peerId, selectedProductId.value, { skipErrorMessage: true })
    await refreshConversationList()
  } catch {
    await ensurePendingConversation(peerId)
  }
}

function selectPeer(peerId, replace = false) {
  const query = { peerId: String(peerId) }
  const conversation = conversationItems.value.find(item => Number(item.peerId) === Number(peerId))
  if (conversation?.productId) {
    query.productId = String(conversation.productId)
  }
  const navigation = { path: '/messages', query }
  if (replace) {
    router.replace(navigation)
  } else {
    router.push(navigation)
  }
}

async function loadChat(peerId, options = {}) {
  if (!peerId) {
    return
  }
  if (!options.silent) {
    chatLoading.value = true
  }
  try {
    const data = await getChat(peerId, { page: 1, size: 80 })
    messages.value = normalizeChatRecords(data.records || [])
    await Promise.allSettled([
      refreshConversationList(),
      messageStore.refreshUnreadCounts()
    ])
    await refreshVisibleOnlineStatus()
    scrollToBottom()
  } finally {
    chatLoading.value = false
  }
}

async function markActiveConversationRead() {
  if (!selectedPeerId.value) {
    return
  }
  await loadChat(selectedPeerId.value, { silent: true })
}

async function sendCurrentMessage() {
  const content = draft.value.trim()
  if (!content || !selectedPeerId.value || sending.value) {
    return
  }

  sending.value = true
  try {
    const saved = await sendMessage(selectedPeerId.value, content, selectedProductId.value)
    messages.value = upsertMessage(messages.value, saved)
    draft.value = ''
    await refreshAll()
    scrollToBottom()
  } finally {
    sending.value = false
  }
}

function isMine(message) {
  return Number(message.fromUserId) === currentUserId.value
}

function displayPeerName(item) {
  return item?.peerNickname || item?.nickname || item?.username || '用户'
}

function displayCount(count) {
  return count > 99 ? '99+' : String(count)
}

function isPeerOnline(peerId) {
  return messageStore.isUserOnline(peerId)
}

function getPeerPresence(peerId) {
  return messageStore.getUserPresence(peerId)
}

function presenceLabel(peerId) {
  return formatPresenceStatus(getPeerPresence(peerId))
}

function presenceClass(peerId) {
  const presence = getPeerPresence(peerId)
  return {
    'presence-label--online': presence.online
  }
}

async function refreshVisibleOnlineStatus() {
  const ids = conversationItems.value.map(item => item.peerId)
  if (selectedPeerId.value) {
    ids.push(selectedPeerId.value)
  }
  await messageStore.refreshOnlineStatus(ids)
}

async function scrollToBottom() {
  await nextTick()
  if (chatBody.value) {
    chatBody.value.scrollTop = chatBody.value.scrollHeight
  }
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.messages-page {
  padding: 20px 0 $space-2xl;
}

.messages-shell {
  display: grid;
  min-height: calc(100vh - 160px);
  grid-template-columns: 330px minmax(0, 1fr);
  border: $border-ink;
  border-radius: $radius-lg;
  background: $color-bg-card;
  box-shadow: $shadow-hard;
  overflow: hidden;
}

.conversation-pane {
  min-width: 0;
  border-right: $border-ink;
  background: $color-paper;
}

.pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-md;
  padding: $space-lg;
  border-bottom: 1px solid $color-border-light;

  h1 {
    margin: 2px 0 0;
    font-size: 24px;
    color: $color-text;
  }
}

.eyebrow {
  margin: 0;
  color: $color-text-muted;
  font-family: $font-family-mono;
  font-size: 11px;
  font-weight: $font-weight-heavy;
  letter-spacing: 1px;
  text-transform: uppercase;
}

.connection-row {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 $space-lg $space-md;
  color: $color-text-muted;
  font-size: $font-size-xs;
  font-weight: $font-weight-semibold;
}

.status-dot {
  width: 8px;
  height: 8px;
  border: 1.5px solid $color-ink;
  border-radius: 50%;
  background: $color-text-muted;

  &--on {
    background: $color-teal;
    box-shadow: 0 0 0 3px rgba($color-teal, 0.14);
  }
}

.conversation-list {
  max-height: calc(100vh - 260px);
  overflow-y: auto;
  padding: 8px;
}

.conversation-item {
  position: relative;
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 11px 10px;
  border: 2px solid transparent;
  border-radius: $radius-md;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background $transition-fast, border-color $transition-fast, box-shadow $transition-fast;

  &:hover {
    background: $color-bg-hover;
  }

  &--active {
    border-color: $color-ink;
    background: $color-yellow;
    box-shadow: 2px 2px 0 $color-ink;
  }
}

.conversation-item__body {
  min-width: 0;
  flex: 1;

  p {
    margin: 4px 0 0;
    color: $color-text-secondary;
    font-size: $font-size-xs;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.conversation-item__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;

  strong {
    min-width: 0;
    color: $color-text;
    font-size: $font-size-sm;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .presence-label {
    flex-shrink: 0;
  }
}

.presence-label {
  color: $color-text-muted;
  font-size: 11px;
  font-weight: $font-weight-bold;
  white-space: nowrap;

  &--online {
    color: $color-teal;
  }

  &--header {
    font-size: $font-size-xs;
  }
}

.avatar-with-status {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;

  &--large {
    .peer-status-dot {
      right: 0;
      bottom: 1px;
    }
  }
}

.peer-status-dot {
  position: absolute;
  right: -1px;
  bottom: 0;
  width: 11px;
  height: 11px;
  border: 2px solid $color-paper;
  border-radius: 50%;
  background: $color-text-muted;
  box-shadow: 0 0 0 1.5px $color-ink;

  &--online {
    background: $color-teal;
  }
}

.unread-badge {
  display: inline-flex;
  min-width: 20px;
  height: 20px;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  border: 1.5px solid $color-ink;
  border-radius: 999px;
  background: $color-red;
  color: #fff;
  font-size: 11px;
  font-weight: $font-weight-heavy;
}

.pane-empty,
.chat-empty,
.chat-placeholder {
  display: flex;
  min-height: 260px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: $space-xl;
  color: $color-text-muted;
  text-align: center;

  .el-icon {
    font-size: 34px;
    color: $color-primary;
  }

  p,
  h2 {
    margin: 0;
    color: $color-text;
    font-weight: $font-weight-bold;
  }

  span {
    max-width: 320px;
    line-height: 1.7;
    font-size: $font-size-sm;
  }
}

.chat-pane {
  display: grid;
  min-width: 0;
  grid-template-rows: auto minmax(0, 1fr) auto;
  background: $color-paper-2;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-md;
  padding: $space-lg;
  border-bottom: 1px solid $color-border-light;
  background: $color-bg-card;
}

.chat-peer {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;

  h2 {
    margin: 0;
    color: $color-text;
    font-size: 20px;
  }

  p {
    margin: 3px 0 0;
    color: $color-text-muted;
    font-size: $font-size-xs;
  }
}

.chat-body {
  min-height: 0;
  overflow-y: auto;
  padding: $space-lg;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-row {
  display: flex;
  justify-content: flex-start;

  &--mine {
    justify-content: flex-end;

    .message-bubble {
      border-color: $color-ink;
      background: $color-primary;
      color: #fff;
      box-shadow: 3px 3px 0 $color-ink;

      time {
        color: rgba(255, 255, 255, 0.76);
      }
    }
  }
}

.message-bubble {
  max-width: min(560px, 72%);
  padding: 10px 12px;
  border: 1px solid $color-border;
  border-radius: $radius-md;
  background: #fff;
  color: $color-text;
  box-shadow: $shadow-card;

  p {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
    line-height: 1.65;
  }

  time {
    display: block;
    margin-top: 6px;
    color: $color-text-muted;
    font-size: 11px;
  }
}

.composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  padding: $space-lg;
  border-top: 1px solid $color-border-light;
  background: $color-bg-card;
}

.chat-loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-skeleton {
  width: 42%;
  height: 54px;
  border-radius: $radius-md;
  background: linear-gradient(90deg, #eee7de, #f7f1e8, #eee7de);
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite linear;

  &--right {
    align-self: flex-end;
  }
}

@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}

@media (max-width: 860px) {
  .messages-shell {
    grid-template-columns: 1fr;
  }

  .conversation-pane {
    border-right: 0;
    border-bottom: $border-ink;
  }

  .conversation-list {
    display: flex;
    max-height: none;
    overflow-x: auto;
  }

  .conversation-item {
    min-width: 260px;
  }

  .chat-pane {
    min-height: 560px;
  }
}

@media (max-width: 640px) {
  .messages-page {
    padding-top: 12px;
  }

  .messages-shell {
    border-right: 0;
    border-left: 0;
    border-radius: 0;
  }

  .chat-header,
  .composer {
    grid-template-columns: 1fr;
  }

  .composer {
    display: flex;
    flex-direction: column;
  }

  .message-bubble {
    max-width: 86%;
  }
}
</style>
