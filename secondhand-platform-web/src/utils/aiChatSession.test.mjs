import test from 'node:test'
import assert from 'node:assert/strict'

import { createAiChatSession } from './aiChatSession.js'

function createSession(options) {
  assert.equal(typeof createAiChatSession, 'function', '需要可恢复且按账号隔离的会话实现')
  return createAiChatSession(options)
}

function memoryStorage() {
  const records = new Map()
  return {
    records,
    getItem: key => records.get(key) ?? null,
    setItem: (key, value) => records.set(key, value),
    removeItem: key => records.delete(key)
  }
}

test('restores messages, conversation id and draft after refresh', async () => {
  const storage = memoryStorage()
  const session = createSession({ storage, userId: 10, request: async () => ({ conversationId: 'conv-a', answer: '找到台灯', items: [{ productId: 1, coverUrl: '/uploads/lamp.jpg' }], searchFilters: { maxPrice: 0 } }) })
  session.state.draft = '找台灯'
  await session.send()
  session.state.draft = '还想找教材'
  session.save()
  const restored = createSession({ storage, userId: 10 })
  assert.equal(restored.state.conversationId, 'conv-a')
  assert.equal(restored.state.draft, '还想找教材')
  assert.equal(restored.state.messages.at(-1).items[0].coverUrl, '/uploads/lamp.jpg')
  assert.deepEqual(restored.state.messages.at(-1).searchFilters, { maxPrice: 0 })
  assert.equal(restored.state.sending, false)
})

test('switches accounts immediately and ignores an old account response', async () => {
  const storage = memoryStorage()
  let resolveRequest
  const session = createSession({ storage, userId: 10, request: () => new Promise(resolve => { resolveRequest = resolve }) })
  session.state.draft = '账号甲的台灯'
  const pending = session.send()
  session.setUser(20)
  assert.equal(session.state.messages.length, 1)
  assert.equal(session.state.draft, '')
  session.state.draft = '账号乙的教材'
  session.save()
  resolveRequest({ conversationId: 'old-conv', answer: '账号甲结果' })
  await pending
  assert.equal(session.state.conversationId, null)
  assert.equal(session.state.messages.length, 1)
  assert.equal(createSession({ storage, userId: 20 }).state.draft, '账号乙的教材')
  session.setUser(10)
  assert.equal(session.state.messages.at(-1).content, '账号甲的台灯')
  assert.equal(session.state.sending, false)
})

test('restores interrupted requests as a retry and does not duplicate the question', async () => {
  const storage = memoryStorage()
  const session = createSession({ storage, userId: 10, request: () => new Promise(() => {}) })
  session.state.draft = '300 元以内的台灯'
  session.send()
  const restored = createSession({ storage, userId: 10, request: async () => ({ answer: '找到商品' }) })
  assert.equal(restored.state.sending, false)
  assert.equal(restored.state.draft, '300 元以内的台灯')
  assert.match(restored.state.errorMessage, /刷新|中断/)
  assert.ok(restored.state.failedMessageId)
  await restored.send()
  assert.equal(restored.state.messages.filter(message => message.role === 'user').length, 1)
  assert.equal(restored.state.errorMessage, '')
})

test('recovers malformed caches and storage denial without preventing chat', async () => {
  const storage = memoryStorage()
  const seed = createSession({ storage, userId: 10 })
  seed.save()
  const key = [...storage.records.keys()][0]
  for (const value of ['{ broken', JSON.stringify({ version: 1, messages: 'bad' }), JSON.stringify({ version: 99, messages: [] })]) {
    storage.setItem(key, value)
    const restored = createSession({ storage, userId: 10 })
    assert.equal(restored.state.messages.length, 1)
    assert.equal(restored.state.draft, '')
  }
  const session = createSession({
    storage: () => { throw new Error('浏览器拒绝访问存储') },
    userId: 10,
    request: async () => ({ answer: '查询完成' })
  })
  session.state.draft = '台灯'
  assert.equal(await session.send(), true)
  assert.equal(session.state.messages.at(-1).content, '查询完成')
})

test('keeps retry state after errors and resets only the current account', async () => {
  const storage = memoryStorage()
  const session = createSession({ storage, userId: 10, request: async () => { throw new Error('查询失败') } })
  session.state.draft = '台灯'
  await session.send()
  const restored = createSession({ storage, userId: 10 })
  assert.equal(restored.state.errorMessage, '查询失败')
  assert.ok(restored.state.failedMessageId)
  session.setUser(20)
  session.state.draft = '教材'
  session.save()
  session.setUser(10)
  session.reset()
  assert.equal(session.state.messages.length, 1)
  assert.equal(createSession({ storage, userId: 10 }).state.failedMessageId, null)
  assert.equal(createSession({ storage, userId: 20 }).state.draft, '教材')
})

test('limits saved history and rejects unsafe cached product data', async () => {
  const storage = memoryStorage()
  const session = createSession({ storage, userId: 10, request: async () => ({ answer: '完成' }) })
  for (let index = 0; index < 70; index += 1) {
    session.state.draft = `台灯 ${index}`
    await session.send()
  }
  session.state.messages.at(-1).items = [{ productId: 10, coverUrl: 'javascript:alert(1)', detailPath: 'https://evil.example' }]
  session.save()
  const restored = createSession({ storage, userId: 10 })
  assert.ok(restored.state.messages.length <= 100)
  assert.equal(restored.state.messages.at(-1).items[0].coverUrl, undefined)
  assert.equal(restored.state.messages.at(-1).items[0].detailPath, '/product/10')
})

test('ignores late errors after a new conversation and keeps storage quota failures recoverable', async () => {
  let rejectRequest
  let formattedErrors = 0
  const session = createSession({
    userId: 10,
    storage: { getItem: () => null, setItem: () => { throw new Error('超出配额') } },
    request: () => new Promise((resolve, reject) => { rejectRequest = reject }),
    formatError: error => { formattedErrors += 1; return error.message }
  })
  session.state.draft = '旧问题'
  const pending = session.send()
  session.reset()
  session.state.draft = '新草稿'
  rejectRequest(new Error('旧请求失败'))
  await pending
  assert.equal(session.state.draft, '新草稿')
  assert.equal(session.state.messages.length, 1)
  assert.equal(session.state.errorMessage, '')
  assert.equal(session.state.sending, false)
  assert.equal(formattedErrors, 0)
  assert.equal(session.save(), false)
})
