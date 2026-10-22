const SORT_VALUES = new Set(['relevance', 'latest', 'hottest', 'price_asc', 'price_desc'])

/** 解析筛选数值，空值与预算 0 分别处理，拒绝数组和非有限数。 */
function filterNumber(value) {
  if (typeof value !== 'number' && typeof value !== 'string') return null
  if (typeof value === 'string' && !value.trim()) return null
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? number : null
}

/** 只保留商品搜索实际支持的条件，不接受分页或外部跳转地址。 */
export function normalizeSearchFilters(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  const filters = {}
  if (typeof value.keyword === 'string' && value.keyword.trim()) filters.keyword = value.keyword.trim().slice(0, 200)
  const categoryId = filterNumber(value.categoryId)
  if (Number.isSafeInteger(categoryId) && categoryId > 0) filters.categoryId = categoryId
  for (const field of ['minPrice', 'maxPrice']) {
    const price = filterNumber(value[field])
    if (price !== null) filters[field] = price
  }
  const conditionLevel = filterNumber(value.conditionLevel)
  if (Number.isInteger(conditionLevel) && conditionLevel >= 1 && conditionLevel <= 4) filters.conditionLevel = conditionLevel
  if (SORT_VALUES.has(value.sort)) filters.sort = value.sort
  return filters
}

/** 从 URL 完整恢复列表条件，刷新、后退与 AI 继续浏览使用同一规则。 */
export function readProductListQuery(query = {}) {
  const filters = normalizeSearchFilters(query) || {}
  const page = filterNumber(query.page)
  return {
    keyword: filters.keyword ?? '',
    categoryId: filters.categoryId ?? 0,
    minPrice: filters.minPrice ?? null,
    maxPrice: filters.maxPrice ?? null,
    conditionLevel: filters.conditionLevel ?? 0,
    sort: filters.sort ?? 'latest',
    page: Number.isSafeInteger(page) && page > 0 ? page : 1
  }
}

/** 所有筛选、排序与翻页都序列化到本地商品列表 URL。 */
export function buildProductListQuery(state) {
  const filters = normalizeSearchFilters(state) || {}
  const query = {}
  for (const [field, value] of Object.entries(filters)) {
    if (field === 'sort' && value === 'latest') continue
    query[field] = String(value)
  }
  const page = filterNumber(state?.page)
  if (Number.isSafeInteger(page) && page > 1) query.page = String(page)
  return query
}

/** 请求参数只取已生效的 URL 条件，避免输入未确认的价格影响翻页。 */
export function buildProductPageParams(query, size = 16) {
  const state = readProductListQuery(query)
  return { ...normalizeSearchFilters(state), page: state.page, sort: state.sort, size }
}
