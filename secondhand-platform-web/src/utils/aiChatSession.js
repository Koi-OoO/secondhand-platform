import { normalizeAiResponse } from './ai.js'

const STORAGE_PREFIX = 'xianwu:ai-chat:v1:'
const MAX_MESSAGES = 100
const MAX_CACHE_LENGTH = 512_000
const WELCOME_TEXT = '你好！我可以帮你查找在售闲置商品。告诉我预算、品类或成色，就从市场里开始找。'

/** 新会话保留引导消息，正在发送状态始终由当前页面请求决定。 */
function emptyState() {
  return {
    conversationId: null,
    messages: [{ id: 'welcome', role: 'assistant', content: WELCOME_TEXT }],
    draft: '',
    sending: false,
    errorMessage: '',
    failedMessageId: null,
    pendingMessageId: null
  }
}

/** 只用有效的登录用户 ID 生成独立存储键，游客不读写会话。 */
function userKey(userId) {
  const id = typeof userId === 'string' && userId.trim() ? Number(userId) : userId
  return Number.isSafeInteger(id) && id > 0 ? `${STORAGE_PREFIX}${id}` : null
}

/** 缓存中的消息也按接口规则清洗，限制历史长度及卡片数。 */
function normalizeMessages(messages) {
  if (!Array.isArray(messages)) return []
  return messages.slice(-MAX_MESSAGES).flatMap(message => {
    if (!message || !['assistant', 'user'].includes(message.role) || typeof message.content !== 'string' || typeof message.id !== 'string') return []
    const result = { id: message.id.slice(0, 100), role: message.role, content: message.content.slice(0, 12_000) }
    if (message.role === 'assistant') {
      const normalized = normalizeAiResponse(message)
      if (normalized.items.length) result.items = normalized.items.slice(0, 20)
      if (normalized.searchFilters != null) result.searchFilters = normalized.searchFilters
    }
    return [result]
  })
}

/** 版本和字段不合法时退回新会话，不让坏缓存阻断聊天。 */
function restoreSnapshot(snapshot) {
  const state = emptyState()
  if (!snapshot || snapshot.version !== 1 || !Array.isArray(snapshot.messages)) return state
  const messages = normalizeMessages(snapshot.messages)
  if (!messages.length) return state
  state.messages = messages
  if (typeof snapshot.conversationId === 'string') state.conversationId = snapshot.conversationId.slice(0, 200) || null
  if (typeof snapshot.draft === 'string') state.draft = snapshot.draft.slice(0, 1000)
  const failed = messages.find(message => message.role === 'user' && message.id === snapshot.failedMessageId)
  if (failed) {
    state.failedMessageId = failed.id
    state.errorMessage = typeof snapshot.errorMessage === 'string' ? snapshot.errorMessage.slice(0, 1000) : '上次查询没有完成，请重试。'
  }
  const interrupted = messages.find(message => message.role === 'user' && message.id === snapshot.pendingMessageId)
  if (interrupted) {
    state.failedMessageId = interrupted.id
    state.draft ||= interrupted.content.slice(0, 1000)
    state.errorMessage = '上次查询因页面刷新或离开而中断，可以重试，也可以修改问题后发送。'
  }
  return state
}

/** 管理当前浏览器会话，允许传入响应式状态；不向模型附加历史上下文。 */
export function createAiChatSession({ storage, userId, request, state = {}, formatError = error => error?.message || '服务暂时不可用，请稍后重试。' } = {}) {
  let currentKey = null
  let epoch = 0
  let sequence = 0
  let restoring = false

  /** 存储权限、隐私模式和配额错误都只影响保存，不影响当前会话。 */
  function getStorage() {
    return typeof storage === 'function' ? storage() : storage
  }

  function save() {
    if (!currentKey || restoring) return false
    try {
      const snapshot = {
        version: 1,
        conversationId: state.conversationId,
        messages: normalizeMessages(state.messages),
        draft: typeof state.draft === 'string' ? state.draft.slice(0, 1000) : '',
        errorMessage: state.errorMessage,
        failedMessageId: state.failedMessageId,
        pendingMessageId: state.pendingMessageId
      }
      // 先限制消息数量，再按容量从最早消息收缩，始终保留最近的问题。
      let serialized = JSON.stringify(snapshot)
      while (serialized.length > MAX_CACHE_LENGTH && snapshot.messages.length > 1) {
        snapshot.messages.shift()
        serialized = JSON.stringify(snapshot)
      }
      getStorage()?.setItem(currentKey, serialized)
      return true
    } catch { return false }
  }

  /** 先保存旧账号，再恢复新账号；代数变化使旧请求的成功和失败回调失效。 */
  function setUser(nextUserId) {
    save()
    epoch += 1
    currentKey = userKey(nextUserId)
    let restored = emptyState()
    if (currentKey) {
      try {
        const cached = getStorage()?.getItem(currentKey)
        if (cached && cached.length <= MAX_CACHE_LENGTH) restored = restoreSnapshot(JSON.parse(cached))
      } catch { /* 缓存损坏或浏览器禁止存储时使用空会话。 */ }
    }
    restoring = true
    Object.assign(state, restored)
    restoring = false
    save()
  }

  /** 开始新对话只重置当前账号，并忽略重置前的在途响应。 */
  function reset() {
    epoch += 1
    restoring = true
    Object.assign(state, emptyState())
    restoring = false
    save()
  }

  async function send() {
    const content = state.draft?.trim()
    if (!content || state.sending || !currentKey) return false
    const requestEpoch = epoch
    const requestKey = currentKey
    if (state.failedMessageId) state.messages = state.messages.filter(message => message.id !== state.failedMessageId)
    const userMessage = { id: `${Date.now()}-${++sequence}`, role: 'user', content }
    state.messages = [...state.messages, userMessage].slice(-MAX_MESSAGES)
    state.sending = true
    state.failedMessageId = null
    state.pendingMessageId = userMessage.id
    state.errorMessage = ''
    save()
    const isCurrent = () => requestEpoch === epoch && requestKey === currentKey

    try {
      const response = await request(state.conversationId, content)
      if (!isCurrent()) return false
      if (response?.conversationId) state.conversationId = String(response.conversationId).slice(0, 200)
      const normalized = normalizeAiResponse(response)
      state.messages = [...state.messages, {
        id: `${Date.now()}-${++sequence}`,
        role: 'assistant',
        content: normalized.answer || '暂时没有找到符合条件的商品。你可以换个关键词或预算再试试。',
        items: normalized.items,
        ...(normalized.searchFilters != null ? { searchFilters: normalized.searchFilters } : {})
      }].slice(-MAX_MESSAGES)
      state.draft = ''
      return true
    } catch (error) {
      if (!isCurrent()) return false
      state.failedMessageId = userMessage.id
      state.errorMessage = formatError(error)
      // 原问题保留在草稿中，便于重试或修改。
      return false
    } finally {
      if (isCurrent()) {
        state.sending = false
        state.pendingMessageId = null
        save()
      }
    }
  }

  /** 离开页面时保留未完成标记，后续恢复提供重试而不是无限加载。 */
  function dispose() {
    save()
    epoch += 1
  }

  setUser(userId)
  return { state, save, setUser, reset, send, dispose }
}
