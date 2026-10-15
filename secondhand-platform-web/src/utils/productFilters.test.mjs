import test from 'node:test'
import assert from 'node:assert/strict'

import * as filters from './productFilters.js'

test('reads executed price and condition filters from the route including zero', () => {
  assert.equal(typeof filters?.readProductListQuery, 'function', '商品列表需要恢复 URL 中的所有条件')
  assert.deepEqual(filters.readProductListQuery({ keyword: '台灯', categoryId: '2', minPrice: '0', maxPrice: '0', conditionLevel: '2', sort: 'price_asc', page: '3' }), {
    keyword: '台灯', categoryId: 2, minPrice: 0, maxPrice: 0, conditionLevel: 2, sort: 'price_asc', page: 3
  })
})

test('pagination and sorting keep applied prices and conditions in the URL and API', () => {
  assert.equal(typeof filters?.buildProductListQuery, 'function', '翻页需要保留筛选条件')
  const state = { keyword: '台灯', categoryId: 2, minPrice: 0, maxPrice: 300, conditionLevel: 2, sort: 'price_asc', page: 3 }
  assert.deepEqual(filters.buildProductListQuery(state), { keyword: '台灯', categoryId: '2', minPrice: '0', maxPrice: '300', conditionLevel: '2', sort: 'price_asc', page: '3' })
  assert.deepEqual(filters.buildProductPageParams(state, 16), { ...state, size: 16 })
  assert.deepEqual(filters.readProductListQuery(filters.buildProductListQuery({ ...state, sort: 'hottest', page: 1 })), { ...state, sort: 'hottest', page: 1 })
})

test('rejects invalid numeric and repeated route values', () => {
  assert.equal(typeof filters?.readProductListQuery, 'function', '需要安全的查询解析函数')
  assert.deepEqual(filters.readProductListQuery({ keyword: ['台灯', '教材'], categoryId: '-1', minPrice: '-2', maxPrice: 'Infinity', conditionLevel: '9', sort: 'redirect', page: '0' }), {
    keyword: '', categoryId: 0, minPrice: null, maxPrice: null, conditionLevel: 0, sort: 'latest', page: 1
  })
})
