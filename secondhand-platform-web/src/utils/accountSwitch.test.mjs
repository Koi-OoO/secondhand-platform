import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createPinia, setActivePinia } from 'pinia'
import { fileURLToPath } from 'node:url'

// 加载真实用户 store，替换网络和路由边界；不启动后端，不使用真实账号密码。
async function withStore(run) {
  const previousWindow = globalThis.window
  const previousHarness = globalThis.__accountSwitchTest
  const records = new Map()
  const navigations = []
  const harness = {
    login: async () => {},
    profile: async () => ({ id: 1, username: 'buyer', nickname: '买家', avatar: '/uploads/buyer.png' }),
    logout: async () => {},
    accounts: async () => ({ currentUserId: 1, csrfToken: 'test-management-token', accounts: [{ userId: 1, username: 'buyer', nickname: '买家', current: true }] }),
    add: async () => {},
    switch: async () => {},
    remove: async () => {},
    logoutAll: async () => {},
  }
  globalThis.__accountSwitchTest = harness
  globalThis.window = {
    localStorage: { getItem: key => records.get(key) ?? null, setItem: (key, value) => records.set(key, value) },
    location: { assign: path => navigations.push(path) },
  }
  const vite = await createServer({
    configFile: false,
    resolve: { alias: { '@': fileURLToPath(new URL('../', import.meta.url)) }, preserveSymlinks: true },
    optimizeDeps: { noDiscovery: true },
    server: { middlewareMode: true, hmr: false, watch: null },
    plugins: [{
      name: 'account-switch-test-boundaries',
      enforce: 'pre',
      resolveId(id) {
        const normalized = id.replace(/\\/g, '/')
        if (id === '@/api/user' || /\/src\/api\/user(?:\.js)?$/.test(normalized)) return '\0account-api'
        if (id === '@/router' || /\/src\/router(?:\/index\.js)?$/.test(normalized)) return '\0account-router'
      },
      load(id) {
        if (id === '\0account-router') return 'export default { push: async () => {} }'
        if (id === '\0account-api') return `
          export const login = (...args) => globalThis.__accountSwitchTest.login(...args)
          export const getUserProfile = (...args) => globalThis.__accountSwitchTest.profile(...args)
          export const logout = (...args) => globalThis.__accountSwitchTest.logout(...args)
          export const getSessionAccounts = (...args) => globalThis.__accountSwitchTest.accounts(...args)
          export const addSessionAccount = (...args) => globalThis.__accountSwitchTest.add(...args)
          export const switchSessionAccount = (...args) => globalThis.__accountSwitchTest.switch(...args)
          export const removeSessionAccount = (...args) => globalThis.__accountSwitchTest.remove(...args)
          export const logoutSessionAccounts = (...args) => globalThis.__accountSwitchTest.logoutAll(...args)
          export const register = async () => {}
          export const updateProfile = async () => {}
        `
      },
    }],
  })
  try {
    setActivePinia(createPinia())
    const { useUserStore } = await vite.ssrLoadModule('/src/stores/user.js')
    await run({ store: useUserStore(), harness, records, navigations })
  } finally {
    await vite.close()
    globalThis.window = previousWindow
    globalThis.__accountSwitchTest = previousHarness
  }
}

test('successful login remembers only the verified profile, not submitted credentials', async () => {
  await withStore(async ({ store, records }) => {
    assert.equal(await store.login('buyer', 'test-password'), true)
    assert.equal(store.userInfo.username, 'buyer')
    const saved = [...records.values()].join('')
    assert.match(saved, /buyer/)
    assert.doesNotMatch(saved, /test-password|password/)
  })
})

test('failed credentials and unavailable profile never produce a remembered login', async () => {
  await withStore(async ({ store, harness, records }) => {
    harness.login = async () => { throw new Error('密码错误') }
    assert.equal(await store.login('buyer', 'wrong'), false)
    assert.equal(records.size, 0)
    harness.login = async () => {}
    harness.profile = async () => { throw new Error('资料暂时不可用') }
    assert.equal(await store.login('buyer', 'correct'), false)
    assert.equal(store.isLoggedIn, false)
    assert.equal(records.size, 0)
  })
})

test('opening the manager reads authorized accounts without logging out or navigating', async () => {
  await withStore(async ({ store, harness, navigations }) => {
    store.userInfo = { id: 1, username: 'buyer' }
    harness.logout = async () => { assert.fail('打开管理面板不能退出当前账号') }
    await store.loadSessionAccounts()
    assert.equal(store.sessionAccounts[0].userId, 1)
    assert.equal(store.userInfo.username, 'buyer')
    assert.deepEqual(navigations, [])
  })
})

test('switch waits for authorized server switching and ignores a duplicate click', async () => {
  await withStore(async ({ store, harness, navigations }) => {
    store.userInfo = { id: 1, username: 'buyer' }
    await store.loadSessionAccounts()
    let finishSwitch
    let calls = 0
    harness.switch = (userId, token) => {
      assert.equal(userId, 2)
      assert.equal(token, 'test-management-token')
      calls += 1
      return new Promise(resolve => { finishSwitch = resolve })
    }
    const pending = store.switchAccount(2)
    assert.equal(store.switching, true)
    assert.equal(navigations.length, 0)
    assert.equal(await store.switchAccount(2), false)
    assert.equal(calls, 1)
    finishSwitch({ currentUserId: 2, csrfToken: 'next-token', accounts: [{ userId: 2, username: 'seller', current: true }] })
    assert.equal(await pending, true)
    assert.equal(store.userInfo, null)
    assert.deepEqual(navigations, ['/'])
  })
})

test('a rejected or expired switch does not replace the current identity', async () => {
  await withStore(async ({ store, harness, navigations }) => {
    store.userInfo = { id: 1, username: 'buyer' }
    await store.loadSessionAccounts()
    harness.switch = async () => { throw new Error('账号授权已失效') }
    await assert.rejects(store.switchAccount(2), /授权已失效/)
    assert.equal(store.userInfo.username, 'buyer')
    assert.equal(store.switching, false)
    assert.deepEqual(navigations, [])
  })
})

test('adding an account sends credentials once but keeps the current account active', async () => {
  await withStore(async ({ store, harness, records, navigations }) => {
    store.userInfo = { id: 1, username: 'buyer' }
    await store.loadSessionAccounts()
    harness.add = async (username, password, token) => {
      assert.deepEqual([username, password, token], ['seller', 'test-seller', 'test-management-token'])
      return { currentUserId: 1, csrfToken: 'new-token', accounts: [{ userId: 1, username: 'buyer', current: true }, { userId: 2, username: 'seller', nickname: '卖家', current: false }] }
    }
    assert.equal(await store.addAccount('seller', 'test-seller'), true)
    assert.equal(store.sessionAccounts.length, 2)
    assert.equal(store.userInfo.username, 'buyer')
    assert.deepEqual(navigations, [])
    assert.doesNotMatch([...records.values()].join(''), /test-seller|test-management-token|new-token/)
  })
})

test('removing inactive authorization keeps current identity; removing current reloads the server-selected account', async () => {
  await withStore(async ({ store, harness, navigations }) => {
    store.userInfo = { id: 1, username: 'buyer' }
    await store.loadSessionAccounts()
    harness.remove = async () => ({ currentUserId: 1, csrfToken: 'next-token', accounts: [{ userId: 1, username: 'buyer', current: true }] })
    await store.removeAccount(2)
    assert.equal(store.userInfo.username, 'buyer')
    assert.deepEqual(navigations, [])
    harness.remove = async () => ({ currentUserId: null, csrfToken: null, accounts: [] })
    await store.removeAccount(1)
    assert.equal(store.userInfo, null)
    assert.deepEqual(navigations, ['/login'])
  })
})

test('logout-all is server-confirmed and leaves no in-memory authorization', async () => {
  await withStore(async ({ store, harness, records, navigations }) => {
    store.userInfo = { id: 1, username: 'buyer' }
    await store.loadSessionAccounts()
    let called = false
    harness.logoutAll = async token => { assert.equal(token, 'test-management-token'); called = true }
    await store.logoutAllAccounts()
    assert.equal(called, true)
    assert.deepEqual(store.sessionAccounts, [])
    assert.equal(store.userInfo, null)
    assert.deepEqual(navigations, ['/login'])
    assert.doesNotMatch([...records.values()].join(''), /test-management-token/)
  })
})

test('does not switch accounts while a login is still retrieving the current profile', async () => {
  await withStore(async ({ store, harness, navigations }) => {
    let finishProfile
    let started
    const profileStarted = new Promise(resolve => { started = resolve })
    harness.profile = () => { started(); return new Promise(resolve => { finishProfile = resolve }) }
    harness.logout = async () => { assert.fail('登录请求尚未完成，不能退出其 Session') }
    const pending = store.login('buyer', 'test-password')
    await profileStarted
    assert.equal(await store.switchAccount(), false)
    assert.deepEqual(navigations, [])
    finishProfile({ id: 1, username: 'buyer' })
    assert.equal(await pending, true)
  })
})

test('failed logout-all keeps the current identity and grants available for retry', async () => {
  await withStore(async ({ store, harness, navigations }) => {
    store.userInfo = { id: 1, username: 'buyer' }
    await store.loadSessionAccounts()
    harness.logoutAll = async () => { throw new Error('网络暂时不可用') }
    await assert.rejects(store.logoutAllAccounts(), /网络暂时不可用/)
    assert.equal(store.userInfo.username, 'buyer')
    assert.equal(store.sessionAccounts.length, 1)
    assert.equal(store.switching, false)
    assert.deepEqual(navigations, [])
  })
})
