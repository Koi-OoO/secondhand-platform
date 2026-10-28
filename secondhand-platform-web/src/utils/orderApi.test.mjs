import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'vite'

test('meetup delivery sends no logistics or payment data and preserves partial batch results', async () => {
  const vite = await createServer({ configFile: false, resolve: { preserveSymlinks: true }, optimizeDeps: { noDiscovery: true }, server: { middlewareMode: true, hmr: false, watch: null } })
  try {
    const { default: http } = await vite.ssrLoadModule('/src/api/index.js')
    const api = await vite.ssrLoadModule('/src/api/order.js')
    assert.equal(typeof api.deliverOrder, 'function')
    assert.equal(typeof api.batchDeliverOrders, 'function')
    const seen = []
    const result = { successIds: [12], successCount: 1, failCount: 1, failures: [{ orderId: 13, reason: '订单状态已变更' }] }
    http.defaults.adapter = async config => {
      seen.push(config)
      return { data: { code: 200, data: result }, config, status: 200, headers: {} }
    }
    await api.deliverOrder(12)
    assert.deepEqual(await api.batchDeliverOrders([{ orderId: 12 }, { orderId: 13 }]), result)
    assert.equal(seen[0].method, 'put')
    assert.equal(seen[0].url, '/order/12/deliver')
    assert.equal(seen[0].params, undefined)
    assert.equal(seen[0].data, undefined)
    assert.equal(seen[1].url, '/order/batch/deliver')
    assert.equal(seen[1].method, 'put')
    assert.deepEqual(JSON.parse(seen[1].data), { items: [{ orderId: 12 }, { orderId: 13 }] })
  } finally {
    await vite.close()
  }
})

test('chat orders request is scoped to peer and sends pagination without impersonating viewer', async () => {
  const vite = await createServer({ configFile: false, resolve: { preserveSymlinks: true }, optimizeDeps: { noDiscovery: true }, server: { middlewareMode: true, hmr: false, watch: null } })
  try {
    const { default: http } = await vite.ssrLoadModule('/src/api/index.js')
    const api = await vite.ssrLoadModule('/src/api/order.js')
    assert.equal(typeof api.getOrdersWithUser, 'function')
    let request
    http.defaults.adapter = async config => {
      request = config
      return { data: { code: 200, data: { records: [], total: 0 } }, config, status: 200, headers: {} }
    }
    const data = await api.getOrdersWithUser(30, { page: 2, size: 10 })
    assert.deepEqual(data, { records: [], total: 0 })
    assert.equal(request.method, 'get')
    assert.equal(request.url, '/order/with-user/30')
    assert.deepEqual(request.params, { page: 2, size: 10 })
  } finally { await vite.close() }
})


test('successful order writes notify the current page; reads and failed writes do not', async () => {
  const vite = await createServer({ configFile: false, resolve: { preserveSymlinks: true }, optimizeDeps: { noDiscovery: true }, server: { middlewareMode: true, hmr: false, watch: null } })
  const previousWindow = globalThis.window
  globalThis.window = new EventTarget()
  let changes = 0
  window.addEventListener('order-changed', () => changes++)
  try {
    const { default: http } = await vite.ssrLoadModule('/src/api/index.js')
    const api = await vite.ssrLoadModule('/src/api/order.js')
    http.defaults.adapter = async config => ({ data: { code: 200, data: { successIds: [12], failCount: 1 } }, config, status: 200, headers: {} })
    await api.deliverOrder(12)
    await api.confirmOrder(12)
    await api.acceptOrder(12)
    await api.cancelOrder(12)
    await api.rejectOrder(12, '测试')
    await api.createOrder(40, 1, '校门口')
    await api.batchDeliverOrders([{ orderId: 12 }])
    assert.equal(changes, 7)
    await api.getPendingCount()
    await api.getOrderDetail(12)
    assert.equal(changes, 7)
    http.defaults.adapter = async config => ({ data: { code: 400, message: '状态已变更' }, config, status: 200, headers: {} })
    await assert.rejects(http.put('/order/12/deliver', null, { skipErrorMessage: true }))
    assert.equal(changes, 7)
  } finally {
    globalThis.window = previousWindow
    await vite.close()
  }
})
