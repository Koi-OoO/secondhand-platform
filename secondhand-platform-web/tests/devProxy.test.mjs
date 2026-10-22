import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer as createHttpServer } from 'node:http'
import { once } from 'node:events'
import { createServer as createViteServer } from 'vite'

test('开发代理将 AI 请求转发到 Java 的 /api/ai/chat，并保持普通商品接口路径', async () => {
  const backend = createHttpServer(async (request, response) => {
    let body = ''
    for await (const chunk of request) body += chunk
    response.setHeader('Content-Type', 'application/json')
    response.end(JSON.stringify({ path: request.url, method: request.method, body }))
  })
  backend.listen(0, '127.0.0.1')
  await once(backend, 'listening')
  const target = `http://127.0.0.1:${backend.address().port}`
  let frontend

  try {
    // 加载项目实际的 Vite 配置，只将代理目标改成测试服务器。
    frontend = await createViteServer({
      server: {
        host: '127.0.0.1',
        port: 0,
        hmr: false,
        watch: null,
        proxy: { '/api': { target } }
      }
    })
    await frontend.listen()
    const baseUrl = `http://127.0.0.1:${frontend.httpServer.address().port}`
    const payload = { message: '帮我找台灯', conversationId: 'test-conversation' }
    const response = await fetch(`${baseUrl}/api/ai/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    const received = await response.json()
    assert.equal(received.path, '/api/ai/chat')
    assert.equal(received.method, 'POST')
    assert.deepEqual(JSON.parse(received.body), payload)

    const productResponse = await fetch(`${baseUrl}/api/product/page?page=1`)
    assert.equal((await productResponse.json()).path, '/product/page?page=1')
  } finally {
    if (frontend) await frontend.close()
    backend.closeAllConnections()
    await new Promise(resolve => backend.close(resolve))
  }
})
