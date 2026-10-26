export function normalizeChatRecords(records = []) {
  return [...records].sort((a, b) => Number(a.id || 0) - Number(b.id || 0))
}

// 补查响应不能覆盖请求等待期间刚收到的消息；已有消息仍以服务端状态为准。
export function mergeChatRefresh(reply = [], current = [], baseline = []) {
  const before = new Set(baseline.map(message => String(message.id)))
  const merged = new Map(reply.map(message => [String(message.id), message]))
  for (const message of current) {
    const key = String(message.id)
    if (!before.has(key) && !merged.has(key)) merged.set(key, message)
  }
  return normalizeChatRecords([...merged.values()])
}

export function upsertMessage(records = [], message) {
  if (!message || message.id == null) {
    return records
  }
  if (records.some(item => item.id === message.id)) {
    return records
  }
  return normalizeChatRecords([...records, message])
}

export function getPeerIdFromMessage(message, currentUserId) {
  if (!message || currentUserId == null) {
    return null
  }
  const me = Number(currentUserId)
  const from = Number(message.fromUserId)
  const to = Number(message.toUserId)
  if (from === me) return to
  if (to === me) return from
  return null
}

export function getOnlineUserIdFromReceivedMessage(message, currentUserId) {
  if (!message || currentUserId == null) {
    return null
  }
  const me = Number(currentUserId)
  const from = Number(message.fromUserId)
  const to = Number(message.toUserId)
  if (!from || !to || from === me) {
    return null
  }
  return to === me ? from : null
}

export function normalizePresenceStatus(status) {
  if (typeof status === 'boolean') {
    return {
      online: status,
      lastActiveTime: null
    }
  }
  return {
    online: Boolean(status?.online),
    lastActiveTime: status?.lastActiveTime || null
  }
}

function parsePresenceTime(value) {
  if (!value) {
    return null
  }
  const date = value instanceof Date
    ? value
    : new Date(String(value).trim().replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatPresenceStatus(status, now = Date.now()) {
  const presence = normalizePresenceStatus(status)
  if (presence.online) {
    return '在线'
  }

  const activeAt = parsePresenceTime(presence.lastActiveTime)
  if (!activeAt) {
    return '离线'
  }

  const diff = Math.max(0, Number(now) - activeAt.getTime())
  const minutes = Math.floor(diff / 60_000)
  if (minutes < 1) {
    return '刚刚在线'
  }
  if (minutes < 60) {
    return `${minutes}分钟前在线`
  }
  if (minutes === 60) {
    return '一个小时前在线'
  }
  return '离线'
}

function toPeerId(value) {
  const id = Number(value)
  return id > 0 ? id : null
}

export function mergeConversationItems(conversations = [], pendingConversations = []) {
  const serverIds = new Set(conversations.map(item => toPeerId(item?.peerId)).filter(Boolean))
  const visiblePending = pendingConversations
    .filter(item => {
      const peerId = toPeerId(item?.peerId)
      return peerId && !serverIds.has(peerId)
    })
    .map(item => ({
      ...item,
      peerId: toPeerId(item.peerId),
      pending: true
    }))
  return [...visiblePending, ...conversations]
}

export function pruneResolvedPendingConversations(pendingConversations = [], conversations = []) {
  const serverIds = new Set(conversations.map(item => toPeerId(item?.peerId)).filter(Boolean))
  return pendingConversations.filter(item => {
    const peerId = toPeerId(item?.peerId)
    return peerId && !serverIds.has(peerId)
  })
}

export function upsertPendingConversation(pendingConversations = [], conversation) {
  const peerId = toPeerId(conversation?.peerId)
  if (!peerId) {
    return pendingConversations
  }
  const pending = {
    ...conversation,
    peerId,
    pending: true
  }
  return [
    pending,
    ...pendingConversations.filter(item => toPeerId(item?.peerId) !== peerId)
  ]
}
