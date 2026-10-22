import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createAiChatSession } from './aiChatSession.js'
import * as ai from './ai.js'
import { ElMessage } from 'element-plus'

test('a stale AI 401 does not redirect the newly selected account through the global interceptor', async () => {
  const vite = await createServer({ configFile: false, resolve: { preserveSymlinks: true }, optimizeDeps: { noDiscovery: true }, server: { middlewareMode: true, hmr: false, watch: null } })
  const previousWindow = globalThis.window
  let redirects = 0
  globalThis.window = { location: { pathname: '/ai', replace: () => { redirects += 1 } } }
  try {
    // 加载真实 API 与 Axios 拦截器，替换网络适配器来模拟延迟的旧请求。
    const { default: http } = await vite.ssrLoadModule('/src/api/index.js')
    const { chatWithAi } = await vite.ssrLoadModule('/src/api/ai.js')
    let rejectRequest
    let requestStarted
    const started = new Promise(resolve => { requestStarted = resolve })
    http.defaults.adapter = config => new Promise((resolve, reject) => {
      rejectRequest = () => {
        const error = new Error('旧账号已过期')
        error.config = config
        error.response = { status: 401, data: {} }
        reject(error)
      }
      requestStarted()
    })
    const session = createAiChatSession({ userId: 10, request: chatWithAi })
    session.state.draft = '账号甲的台灯'
    const pending = session.send()
    await started
    session.setUser(20)
    rejectRequest()
    await pending
    assert.equal(redirects, 0, '旧账号请求不能通过全局拦截器跳转新账号的页面')
    assert.equal(session.state.errorMessage, '')
    assert.equal(session.state.messages.length, 1)
  } finally {
    globalThis.window = previousWindow
    await vite.close()
  }
})

test('a stale AI server failure does not display a global error on the newly selected account', async () => {
  const vite = await createServer({ configFile: false, resolve: { preserveSymlinks: true }, optimizeDeps: { noDiscovery: true }, server: { middlewareMode: true, hmr: false, watch: null } })
  const originalError = ElMessage.error
  let notifications = 0
  ElMessage.error = () => { notifications += 1 }
  try {
    const { default: http } = await vite.ssrLoadModule('/src/api/index.js')
    const { chatWithAi } = await vite.ssrLoadModule('/src/api/ai.js')
    let rejectRequest
    let requestStarted
    const started = new Promise(resolve => { requestStarted = resolve })
    http.defaults.adapter = config => new Promise((resolve, reject) => {
      rejectRequest = () => {
        const error = new Error('旧请求错误')
        error.config = config
        error.response = { status: 500, data: {} }
        reject(error)
      }
      requestStarted()
    })
    const session = createAiChatSession({ userId: 10, request: chatWithAi })
    session.state.draft = '旧问题'
    const pending = session.send()
    await started
    session.setUser(20)
    rejectRequest()
    await pending
    assert.equal(notifications, 0, '旧账号请求不能通过共用拦截器通知新账号')
    assert.equal(session.state.errorMessage, '')
  } finally {
    ElMessage.error = originalError
    await vite.close()
  }
})

test('the active account still receives the server explanation when global notifications are suppressed', async () => {
  const vite = await createServer({ configFile: false, resolve: { preserveSymlinks: true }, optimizeDeps: { noDiscovery: true }, server: { middlewareMode: true, hmr: false, watch: null } })
  try {
    const { default: http } = await vite.ssrLoadModule('/src/api/index.js')
    const { chatWithAi } = await vite.ssrLoadModule('/src/api/ai.js')
    http.defaults.adapter = async config => {
      const error = new Error('Request failed with status code 500')
      error.config = config
      error.response = { status: 500, data: { message: '查询服务繁忙，请稍后重试。' } }
      throw error
    }
    const session = createAiChatSession({ userId: 10, request: chatWithAi, formatError: ai.getAiErrorMessage })
    session.state.draft = '台灯'
    await session.send()
    assert.equal(session.state.errorMessage, '查询服务繁忙，请稍后重试。')
    assert.equal(session.state.draft, '台灯')
    assert.ok(session.state.failedMessageId)
  } finally {
    await vite.close()
  }
})
