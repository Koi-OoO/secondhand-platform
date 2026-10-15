import { normalizeSearchFilters, buildProductListQuery } from './productFilters.js'

const PRODUCT_FIELDS = ['title', 'price', 'stock', 'conditionLevel', 'tradeLocation']

/** 仅允许真实 HTTP(S) 图片及本站图片路径，拒绝脚本、内联图片和协议相对地址。 */
export function normalizeCoverUrl(value) {
  if (typeof value !== 'string') return null
  const url = value.trim()
  if (!url || url.length > 2048 || /[\\\u0000-\u001f\u007f]/.test(url)) return null
  if (/^https?:\/\//i.test(url)) {
    try {
      const parsed = new URL(url)
      return parsed.hostname && !parsed.username && !parsed.password ? url : null
    } catch { return null }
  }
  if (/^\/(?!\/|%2f|%5c)/i.test(url) && /\.(?:jpe?g|png|webp|gif|avif)(?:[?#].*)?$/i.test(url)) return url
  return null
}

function normalizeProductId(value) {
  const id = typeof value === 'string' && value.trim() ? Number(value) : value
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

function normalizeItem(item) {
  if (!item || typeof item !== 'object') {
    return null
  }

  const productId = normalizeProductId(item.productId)
  if (productId === null) {
    return null
  }

  const normalized = { productId }
  for (const field of PRODUCT_FIELDS) {
    if (item[field] !== undefined && item[field] !== null) {
      normalized[field] = item[field]
    }
  }

  const coverUrl = normalizeCoverUrl(item.coverUrl)
  if (coverUrl) normalized.coverUrl = coverUrl

  // 详情路径始终由有效商品 ID 构造，不信任服务端传来的外部链接。
  if (Object.prototype.hasOwnProperty.call(item, 'detailPath')) {
    normalized.detailPath = `/product/${productId}`
  }

  return normalized
}

/**
 * 展示前规范化 AI 响应，兼容未提供封面和搜索条件的旧接口。
 * 商品卡片只接受有效的本站商品 ID。
 */
export function normalizeAiResponse(response) {
  const payload = response && typeof response === 'object' ? response : {}
  const answer = payload.answer == null ? '' : String(payload.answer)
  const rawItems = Array.isArray(payload.items) ? payload.items : []

  const normalized = {
    answer,
    items: rawItems.map(normalizeItem).filter(Boolean)
  }
  if (Object.prototype.hasOwnProperty.call(payload, 'searchFilters')) {
    normalized.searchFilters = normalizeSearchFilters(payload.searchFilters)
  }
  return normalized
}

/** 更多入口仅来自实际工具筛选，空对象表示成功的无条件搜索。 */
export function buildProductSearchRoute(searchFilters) {
  const filters = normalizeSearchFilters(searchFilters)
  return filters === null ? null : { name: 'Products', query: buildProductListQuery(filters) }
}

/** 当前请求的错误在聊天内显示，保留服务端中文说明并覆盖常见网络英文提示。 */
export function getAiErrorMessage(error) {
  if (error?.response?.status === 401) return '登录状态已失效，请重新登录后再试。'
  if (error?.code === 'ECONNABORTED' || error?.code === 'ETIMEDOUT') return 'AI 响应时间较长，请稍后重试。'
  const serverMessage = error?.response?.data?.message
  if (typeof serverMessage === 'string' && serverMessage.trim()) return serverMessage.trim().slice(0, 1000)
  if (error?.code === 'ERR_NETWORK' || error?.message === 'Network Error') return '网络连接失败，请检查网络后重试。'
  if (typeof error?.message === 'string' && !/^Request failed with status code/.test(error.message)) return error.message || '服务暂时不可用，请稍后重试。'
  return '服务暂时不可用，请稍后重试。'
}
