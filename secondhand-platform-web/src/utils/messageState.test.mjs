import test from 'node:test'
import assert from 'node:assert/strict'
import {
  formatPresenceStatus,
  getOnlineUserIdFromReceivedMessage,
  getPeerIdFromMessage,
  mergeConversationItems,
  normalizeChatRecords,
  normalizePresenceStatus,
  pruneResolvedPendingConversations,
  upsertMessage
} from './messageState.js'

test('normalizes chat records from newest-first to oldest-first', () => {
  const records = [
    { id: 3, content: 'third' },
    { id: 1, content: 'first' },
    { id: 2, content: 'second' }
  ]

  assert.deepEqual(normalizeChatRecords(records).map(item => item.id), [1, 2, 3])
})

test('upserts a message without duplicating existing records', () => {
  const records = [{ id: 1, content: 'old' }]

  assert.deepEqual(upsertMessage(records, { id: 1, content: 'old again' }), records)
  assert.deepEqual(upsertMessage(records, { id: 2, content: 'new' }).map(item => item.id), [1, 2])
})

test('resolves the peer id for inbound and outbound messages', () => {
  assert.equal(getPeerIdFromMessage({ fromUserId: 8, toUserId: 3 }, 3), 8)
  assert.equal(getPeerIdFromMessage({ fromUserId: 3, toUserId: 8 }, 3), 8)
})

test('only inbound websocket messages imply the peer is online', () => {
  assert.equal(getOnlineUserIdFromReceivedMessage({ fromUserId: 8, toUserId: 3 }, 3), 8)
  assert.equal(getOnlineUserIdFromReceivedMessage({ fromUserId: 3, toUserId: 8 }, 3), null)
  assert.equal(getOnlineUserIdFromReceivedMessage({ fromUserId: 3, toUserId: 3 }, 3), null)
})

test('normalizes presence status from old booleans and new objects', () => {
  assert.deepEqual(normalizePresenceStatus(true), { online: true, lastActiveTime: null })
  assert.deepEqual(normalizePresenceStatus(false), { online: false, lastActiveTime: null })
  assert.deepEqual(
    normalizePresenceStatus({ online: true, lastActiveTime: '2026-07-04 20:00:00' }),
    { online: true, lastActiveTime: '2026-07-04 20:00:00' }
  )
})

test('formats presence status for message center display', () => {
  const now = new Date(2026, 6, 4, 20, 0, 0).getTime()

  assert.equal(formatPresenceStatus({ online: true }, now), '在线')
  assert.equal(formatPresenceStatus({ online: false, lastActiveTime: new Date(2026, 6, 4, 19, 59, 40) }, now), '刚刚在线')
  assert.equal(formatPresenceStatus({ online: false, lastActiveTime: new Date(2026, 6, 4, 19, 50, 0) }, now), '10分钟前在线')
  assert.equal(formatPresenceStatus({ online: false, lastActiveTime: new Date(2026, 6, 4, 19, 30, 0) }, now), '30分钟前在线')
  assert.equal(formatPresenceStatus({ online: false, lastActiveTime: new Date(2026, 6, 4, 19, 0, 0) }, now), '一个小时前在线')
  assert.equal(formatPresenceStatus({ online: false, lastActiveTime: new Date(2026, 6, 4, 18, 59, 0) }, now), '离线')
  assert.equal(formatPresenceStatus(null, now), '离线')
})

test('keeps pending direct conversations visible until server conversations include them', () => {
  const serverConversations = [{ peerId: 1, peerNickname: '第一个用户' }]
  const pendingConversations = [{ peerId: 2, peerNickname: '第二个用户', pending: true }]

  assert.deepEqual(
    mergeConversationItems(serverConversations, pendingConversations).map(item => item.peerId),
    [2, 1]
  )

  assert.deepEqual(
    mergeConversationItems([...serverConversations, { peerId: 2, peerNickname: '第二个用户' }], pendingConversations)
      .map(item => item.peerId),
    [1, 2]
  )
})

test('removes pending conversations once they are returned by the server', () => {
  const pendingConversations = [
    { peerId: 2, peerNickname: '第二个用户', pending: true },
    { peerId: 3, peerNickname: '第三个用户', pending: true }
  ]
  const serverConversations = [{ peerId: 2, peerNickname: '第二个用户' }]

  assert.deepEqual(
    pruneResolvedPendingConversations(pendingConversations, serverConversations).map(item => item.peerId),
    [3]
  )
})

test('keeps any number of pending direct conversations visible', () => {
  const serverConversations = [{ peerId: 1, peerNickname: '第一个用户' }]
  const pendingConversations = [
    { peerId: 2, peerNickname: '第二个用户', pending: true },
    { peerId: 3, peerNickname: '第三个用户', pending: true },
    { peerId: 4, peerNickname: '第四个用户', pending: true }
  ]

  assert.deepEqual(
    mergeConversationItems(serverConversations, pendingConversations).map(item => item.peerId),
    [2, 3, 4, 1]
  )

  assert.deepEqual(
    pruneResolvedPendingConversations(
      pendingConversations,
      [...serverConversations, { peerId: 3, peerNickname: '第三个用户' }]
    ).map(item => item.peerId),
    [2, 4]
  )
})
