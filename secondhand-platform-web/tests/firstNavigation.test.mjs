import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { existsSync, mkdirSync, mkdtempSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { spawn } from 'node:child_process'

const chrome = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))

test('冷启动后首次点击登录、注册和商品广场即可进入页面，不触发依赖刷新', {
  skip: !existsSync(chrome), timeout: 120000
}, async () => {
  mkdirSync('target/first-navigation', { recursive: true })
  const dir = mkdtempSync(resolve('target/first-navigation/run-'))
  let server, child, socket
  const events = []
  try {
    server = await createServer({
      cacheDir: resolve(dir, 'vite-cache'),
      server: { host: '127.0.0.1', port: 0, open: false, watch: { ignored: ['**/target/**'] } },
      plugins: [{ name: 'navigation-test-api', configureServer(vite) {
        vite.middlewares.use('/api', (req, res) => {
          res.setHeader('Content-Type', 'application/json')
          const data = req.url.includes('category') ? [] : { records: [], total: 0 }
          if (req.url.includes('user/profile')) {
            res.statusCode = 401
            res.end(JSON.stringify({ code: 401, message: '未登录' }))
          } else res.end(JSON.stringify({ code: 200, data }))
        })
      } }]
    })
    await server.listen()
    const base = `http://127.0.0.1:${server.httpServer.address().port}`
    child = spawn(chrome, ['--headless=new', '--no-sandbox', '--disable-gpu', '--no-first-run',
      '--remote-debugging-port=0', `--user-data-dir=${resolve(dir, 'profile')}`, 'about:blank'], { stdio: 'ignore' })
    const portFile = resolve(dir, 'profile/DevToolsActivePort')
    for (let i = 0; !existsSync(portFile) && i < 150; i++) await pause(100)
    const port = readFileSync(portFile, 'utf8').split('\n')[0]
    const page = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json()
    socket = new WebSocket(page.webSocketDebuggerUrl)
    await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject })
    let sequence = 0
    const pending = new Map()
    socket.onmessage = ({ data }) => {
      const message = JSON.parse(data)
      if (message.method) events.push(message)
      const request = pending.get(message.id)
      if (request) {
        pending.delete(message.id)
        message.error ? request.reject(new Error(JSON.stringify(message.error))) : request.resolve(message.result)
      }
    }
    const command = (method, params = {}) => new Promise((resolve, reject) => {
      const id = ++sequence
      pending.set(id, { resolve, reject })
      socket.send(JSON.stringify({ id, method, params }))
    })
    const evaluate = async expression => {
      const result = await command('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
      if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails))
      return result.result.value
    }
    const until = async (expression, label) => {
      for (let i = 0; i < 200; i++) {
        if (await evaluate(expression)) return
        await pause(100)
      }
      throw new Error(`等待失败: ${label}; 浏览器日志: ${JSON.stringify(events.filter(e => e.method === 'Runtime.exceptionThrown' || e.method === 'Runtime.consoleAPICalled').slice(-8))}`)
    }
    await command('Runtime.enable')
    await command('Network.enable')
    await command('Page.navigate', { url: base })
    await until(`!!document.querySelector('a[href="/login"]')`, '首页')
    await pause(1000)
    await evaluate('window.__firstNavigationDocument = 1')
    for (const [path, selector] of [
      ['/login', '.auth-page--login'],
      ['/register', '.auth-page--register'],
      ['/', '.app-header'],
      ['/products', '.product-list']
    ]) {
      await evaluate(`document.querySelector('a[href="${path}"]').click()`)
      await until(`location.pathname === '${path}' && !!document.querySelector('${selector}')`, path)
      await pause(1000)
      assert.equal(await evaluate('window.__firstNavigationDocument'), 1, `${path} 首次点击导致整页刷新`)
    }
    const reloads = events.filter(e => e.method === 'Network.webSocketFrameReceived' && e.params.response.payloadData.includes('full-reload'))
    assert.equal(reloads.length, 0, '首次导航不应触发 Vite full-reload')
  } finally {
    socket?.close()
    child?.kill()
    await server?.close()
  }
})
