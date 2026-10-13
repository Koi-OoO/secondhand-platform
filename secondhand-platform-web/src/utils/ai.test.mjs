import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeAiResponse } from './ai.js'

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
