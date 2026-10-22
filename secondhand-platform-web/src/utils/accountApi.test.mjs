import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'vite'

test('account writes carry a session token; ordinary writes bind the visible tab identity and never replay after a switch', async () => {
  const vite = await createServer({ configFile: false, resolve: { preserveSymlinks: true }, optimizeDeps: { noDiscovery: true }, server: { middlewareMode: true, hmr: false, watch: null } })
  const previousWindow = globalThis.window
  let reloads = 0
  globalThis.window = { location: { reload: () => { reloads += 1 } } }
  try {
    const { default: http } = await vite.ssrLoadModule('/src/api/index.js')
    const api = await vite.ssrLoadModule('/src/api/user.js')
    const { setRequestUserId } = await vite.ssrLoadModule('/src/utils/requestIdentity.js')
    setRequestUserId(7)
    const seen = []
    http.defaults.adapter = async config => {
      seen.push(config)
      return { data: { code: 200, data: {} }, config, status: 200, headers: {} }
    }
    await api.addSessionAccount('seller', 'test-only', 'session-token')
    await api.switchSessionAccount(9, 'next-token')
    await api.removeSessionAccount(9, 'next-token')
    await api.logoutSessionAccounts('next-token')
    assert.equal(seen[0].url, '/user/accounts/add')
    assert.equal(seen[0].headers['X-Account-Management-Token'], 'session-token')
    assert.equal(seen[0].headers['X-Active-User-Id'], '7')
    assert.deepEqual(JSON.parse(seen[0].data), { username: 'seller', password: 'test-only' })
    assert.deepEqual(JSON.parse(seen[1].data), { userId: 9 })
    assert.deepEqual(seen.slice(1).map(config => config.url), ['/user/accounts/switch', '/user/accounts/remove', '/user/accounts/logout-all'])
    let attempts = 0
    http.defaults.adapter = async config => {
      attempts += 1
      assert.equal(config.headers['X-Active-User-Id'], '7')
      const failure = new Error('账号已切换')
      failure.config = config
      failure.response = { status: 409, headers: { 'x-account-changed': 'true' }, data: {} }
      throw failure
    }
    await assert.rejects(http.post('/order/create', { productId: 12 }))
    assert.equal(attempts, 1)
    assert.equal(reloads, 1)
  } finally {
    globalThis.window = previousWindow
    await vite.close()
  }
})
