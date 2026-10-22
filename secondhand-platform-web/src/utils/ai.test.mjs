import test from 'node:test'
import assert from 'node:assert/strict'
import * as ai from './ai.js'
const { normalizeAiResponse } = ai

test('normalizes an AI answer and safe product cards', () => {
  assert.deepEqual(
    normalizeAiResponse({
      conversationId: 'conversation-001',
      answer: '找到 1 件',
      items: [
        {
          productId: 10,
          title: '宿舍护眼台灯',
          price: 89,
          stock: 2,
          conditionLevel: 2,
          tradeLocation: '主校区食堂门口',
          detailPath: 'https://evil.example/product/10'
        },
        { title: '缺少 ID 的商品' }
      ]
    }),
    {
      answer: '找到 1 件',
      items: [
        {
          productId: 10,
          title: '宿舍护眼台灯',
          price: 89,
          stock: 2,
          conditionLevel: 2,
          tradeLocation: '主校区食堂门口',
          detailPath: '/product/10'
        }
      ]
    }
  )
})

test('uses safe defaults for malformed AI responses', () => {
  assert.deepEqual(normalizeAiResponse({}), { answer: '', items: [] })
  assert.deepEqual(normalizeAiResponse(null), { answer: '', items: [] })
})

test('keeps real image covers and rejects executable or remote-path covers', () => {
  const response = normalizeAiResponse({ items: [
    { productId: 1, coverUrl: 'https://oss.example/goods/lamp.webp' },
    { productId: 2, coverUrl: '/uploads/lamp.jpg' },
    { productId: 3, coverUrl: 'javascript:alert(1)' },
    { productId: 4, coverUrl: '//evil.example/lamp.png' },
    { productId: 5, coverUrl: 'data:image/svg+xml,<svg />' }
  ] })
  assert.equal(response.items[0].coverUrl, 'https://oss.example/goods/lamp.webp')
  assert.equal(response.items[1].coverUrl, '/uploads/lamp.jpg')
  for (const item of response.items.slice(2)) assert.equal(item.coverUrl, undefined)
})

test('keeps the executed search filters including a zero budget', () => {
  const response = normalizeAiResponse({
    answer: '根据预算找到了商品',
    searchFilters: { keyword: '台灯', categoryId: 2, minPrice: 0, maxPrice: 0, conditionLevel: 2, sort: 'price_asc', page: 5, detailPath: 'https://evil.example' }
  })
  assert.deepEqual(response.searchFilters, { keyword: '台灯', categoryId: 2, minPrice: 0, maxPrice: 0, conditionLevel: 2, sort: 'price_asc' })
})

test('builds more results as a local route without deriving keywords from an answer', () => {
  assert.equal(typeof ai.buildProductSearchRoute, 'function', '需要安全的商品列表路由构造函数')
  assert.deepEqual(ai.buildProductSearchRoute({ keyword: '台灯', maxPrice: 0, conditionLevel: 2, url: 'https://evil.example' }), {
    name: 'Products', query: { keyword: '台灯', maxPrice: '0', conditionLevel: '2' }
  })
  assert.deepEqual(ai.buildProductSearchRoute({}), { name: 'Products', query: {} })
  assert.equal(ai.buildProductSearchRoute(null), null)
  assert.equal(normalizeAiResponse({ answer: '300 元以内的台灯' }).searchFilters, undefined)
})

test('shows server error details and Chinese messages for timeout and network failures', () => {
  assert.equal(typeof ai.getAiErrorMessage, 'function', '需要保留有效请求的中文错误说明')
  assert.equal(ai.getAiErrorMessage({ response: { status: 500, data: { message: '查询服务繁忙，请稍后重试。' } }, message: 'Request failed with status code 500' }), '查询服务繁忙，请稍后重试。')
  assert.equal(ai.getAiErrorMessage({ code: 'ERR_NETWORK', message: 'Network Error' }), '网络连接失败，请检查网络后重试。')
  assert.equal(ai.getAiErrorMessage({ code: 'ETIMEDOUT' }), 'AI 响应时间较长，请稍后重试。')
  assert.equal(ai.getAiErrorMessage({ response: { status: 401 } }), '登录状态已失效，请重新登录后再试。')
})
