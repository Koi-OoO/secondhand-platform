import test from 'node:test'
import assert from 'node:assert/strict'
import { loadRecentAccounts, rememberRecentAccount, removeRecentAccount, RECENT_ACCOUNTS_KEY } from './recentAccounts.js'

function memoryStorage() {
  const records = new Map()
  return { records, getItem: key => records.get(key) ?? null, setItem: (key, value) => records.set(key, value) }
}

test('only persists display fields, never passwords, sessions or private profile data', () => {
  const storage = memoryStorage()
  rememberRecentAccount({ username: 'buyer', nickname: '买家', avatar: '/uploads/a.png', password: 'secret', token: 'secret-token', email: 'private@example.com', id: 7 }, storage)
  const saved = JSON.parse(storage.getItem(RECENT_ACCOUNTS_KEY))
  assert.deepEqual(saved.accounts, [{ username: 'buyer', nickname: '买家', avatar: '/uploads/a.png' }])
  assert.doesNotMatch(storage.getItem(RECENT_ACCOUNTS_KEY), /secret|private@example|password|token/)
})

test('keeps the last five accounts, deduplicates and updates an existing account', () => {
  const storage = memoryStorage()
  for (let index = 0; index < 7; index += 1) rememberRecentAccount({ username: `user${index}` }, storage)
  assert.deepEqual(loadRecentAccounts(storage).map(item => item.username), ['user6', 'user5', 'user4', 'user3', 'user2'])
  rememberRecentAccount({ username: 'user4', nickname: '新昵称' }, storage)
  const accounts = loadRecentAccounts(storage)
  assert.equal(accounts.length, 5)
  assert.equal(accounts[0].username, 'user4')
  assert.equal(accounts[0].nickname, '新昵称')
})

test('removes only one remembered account and leaves other browser data untouched', () => {
  const storage = memoryStorage()
  storage.setItem('ai-chat:buyer', '聊天记录')
  rememberRecentAccount({ username: 'buyer' }, storage)
  rememberRecentAccount({ username: 'seller' }, storage)
  assert.deepEqual(removeRecentAccount('buyer', storage).map(item => item.username), ['seller'])
  assert.deepEqual(loadRecentAccounts(storage).map(item => item.username), ['seller'])
  assert.equal(storage.getItem('ai-chat:buyer'), '聊天记录')
})

test('recovers malformed or unsupported storage and tolerates browser storage denial', () => {
  const storage = memoryStorage()
  for (const value of ['broken', 'null', '{"version":99,"accounts":[]}', '{"version":1,"accounts":"bad"}']) {
    storage.setItem(RECENT_ACCOUNTS_KEY, value)
    assert.deepEqual(loadRecentAccounts(storage), [])
  }
  const denied = () => { throw new Error('浏览器禁止存储') }
  assert.deepEqual(loadRecentAccounts(denied), [])
  assert.equal(rememberRecentAccount({ username: 'buyer' }, denied)[0].username, 'buyer')
  assert.deepEqual(removeRecentAccount('buyer', denied), [])
})

test('rejects invalid entries and unsafe avatars loaded from local storage', () => {
  const storage = memoryStorage()
  storage.setItem(RECENT_ACCOUNTS_KEY, JSON.stringify({ version: 1, accounts: [
    null, { username: '' }, { username: 123 },
    { username: 'buyer', avatar: 'javascript:alert(1)', password: 'secret' },
    { username: 'buyer', nickname: '重复记录' },
    { username: 'seller', avatar: '//other.example/a.png' },
  ] }))
  assert.deepEqual(loadRecentAccounts(storage), [
    { username: 'buyer', nickname: 'buyer', avatar: '' },
    { username: 'seller', nickname: 'seller', avatar: '' },
  ])
})

test('accepts only bounded valid display fields and regular image URLs', () => {
  const storage = memoryStorage()
  assert.deepEqual(rememberRecentAccount({ username: '  buyer  ', avatar: 'https://example.com/a.png' }, storage), [
    { username: 'buyer', nickname: 'buyer', avatar: 'https://example.com/a.png' },
  ])
  rememberRecentAccount({ username: 'x'.repeat(201) }, storage)
  assert.equal(loadRecentAccounts(storage).length, 1)
  rememberRecentAccount({ username: 'seller', avatar: '/\\other.example/a.png' }, storage)
  assert.equal(loadRecentAccounts(storage)[0].avatar, '')
})
