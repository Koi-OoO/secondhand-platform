const PRODUCT_FIELDS = ['title', 'price', 'stock', 'conditionLevel', 'tradeLocation']

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

  // Keep the API shape useful while never trusting a server-provided URL.
  if (Object.prototype.hasOwnProperty.call(item, 'detailPath')) {
    normalized.detailPath = `/product/${productId}`
  }

  return normalized
}

/**
 * Normalize the Java AI response before rendering it in the chat page.
 * Only product cards with a valid local id are returned.
 */
export function normalizeAiResponse(response) {
  const payload = response && typeof response === 'object' ? response : {}
  const answer = payload.answer == null ? '' : String(payload.answer)
  const rawItems = Array.isArray(payload.items) ? payload.items : []

  return {
    answer,
    items: rawItems.map(normalizeItem).filter(Boolean)
  }
}
