import test from 'node:test'
import assert from 'node:assert/strict'
import { resolveCategory, categorySelectionError } from './categoryTree.js'

const categories = [
  { id: 1, name: '手机数码', children: [{ id: 8, name: '手机' }, { id: 9, name: '充电配件' }] },
  { id: 2, name: '图书教材', children: [{ id: 10, name: '专业教材' }] },
  { id: 3, name: '旧的叶子分类', children: null }
]

test('子分类网址能恢复所属大类、子分类按钮及完整路径', () => {
  const before = JSON.stringify(categories)
  const state = resolveCategory(categories, '9')
  assert.equal(state.root.id, 1)
  assert.equal(state.selected.id, 9)
  assert.equal(state.label, '手机数码 / 充电配件')
  assert.deepEqual(state.children.map(item => item.id), [8, 9])
  assert.equal(JSON.stringify(categories), before)
})

test('选择父类表示整个大类，全部和无效分类不留下其他大类的子分类', () => {
  const state = resolveCategory(categories, 1)
  assert.equal(state.root.id, 1)
  assert.equal(state.selected.id, 1)
  assert.equal(state.label, '手机数码')
  for (const invalid of [0, null, -1, 'invalid', 99, ['1'], true]) {
    const result = resolveCategory(categories, invalid)
    assert.equal(result.root, null)
    assert.equal(result.label, '')
    assert.deepEqual(result.children, [])
  }
})

test('新发布和更换分类必须选叶子，已有大类只允许原样保留', () => {
  assert.match(categorySelectionError(categories, null), /请选择商品分类/)
  assert.match(categorySelectionError(categories, 999), /分类不存在/)
  assert.match(categorySelectionError(categories, 1), /具体子分类/)
  assert.equal(categorySelectionError(categories, 8), '')
  assert.equal(categorySelectionError(categories, 3), '')
  assert.equal(categorySelectionError(categories, 1, 1), '')
  assert.match(categorySelectionError(categories, 2, 1), /具体子分类/)
  assert.equal(categorySelectionError(categories, 10, 1), '')
})
