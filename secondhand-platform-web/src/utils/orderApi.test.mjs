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
