/** 分类 ID 只接受正整数，避免 URL 数组或异常值影响选中状态。 */
function categoryId(value) {
  if (typeof value !== 'number' && typeof value !== 'string') return 0
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : 0
}

/** 从两级分类树还原当前大类、子类和显示路径，不修改接口原始数据。 */
export function resolveCategory(categories, value) {
  const id = categoryId(value)
  if (id && Array.isArray(categories)) {
    for (const root of categories) {
      const children = Array.isArray(root.children) ? root.children : []
      if (root.id === id) return { root, selected: root, children, label: root.name }
      const selected = children.find(child => child.id === id)
      if (selected) return { root, selected, children, label: `${root.name} / ${selected.name}` }
    }
  }
  return { root: null, selected: null, children: [], label: '' }
}

/** 新选分类必须是叶子；编辑时可以保留数据库中原有的大类归属。 */
export function categorySelectionError(categories, value, originalValue = null) {
  const id = categoryId(value)
  if (!id) return '请选择商品分类'
  const { selected } = resolveCategory(categories, id)
  if (!selected) return '所选分类不存在，请重新选择'
  if (id === categoryId(originalValue)) return ''
  if (Array.isArray(selected.children) && selected.children.length) return '请选择具体子分类'
  return ''
}
