import http from './index'

// ═══ 商品浏览 ═══

/** 分页查询商品列表 */
export function getProductPage(params = {}) {
  return http.get('/product/page', { params })
}

/** 获取商品详情 */
export function getProductDetail(id) {
  return http.get(`/product/detail/${id}`)
}

/** 热门搜索词 TOP10 */
export function getHotKeywords() {
  return http.get('/product/hot-keywords')
}

/** 卖家在售商品 */
export function getSellerProducts(sellerId, params = {}) {
  return http.get(`/product/seller/${sellerId}`, { params })
}

// ═══ 商品管理（需登录） ═══

/** 发布商品 */
export function publishProduct(data) {
  return http.post('/product/publish', data)
}

/** 更新商品 */
export function updateProduct(data) {
  return http.put('/product/update', data)
}

/** 下架商品 */
export function offShelfProduct(id) {
  return http.put(`/product/off-shelf/${id}`)
}

/** 上架商品 */
export function onShelfProduct(id) {
  return http.put(`/product/on-shelf/${id}`)
}

// ═══ 收藏 ═══

/** 收藏商品 */
export function addFavorite(id) {
  return http.post(`/product/${id}/favorite`)
}

/** 取消收藏 */
export function removeFavorite(id) {
  return http.delete(`/product/${id}/favorite`)
}

/** 我的收藏列表 */
export function getMyFavorites(params = {}) {
  return http.get('/product/my-favorites', { params })
}

/** 查询当前用户是否已收藏某商品 */
export function getFavoriteStatus(id) {
  return http.get(`/product/${id}/favorite/status`)
}

// ═══ 举报 ═══

/** 举报商品 */
export function reportProduct(id, reason, detail) {
  return http.post(`/product/${id}/report`, null, { params: { reason, detail } })
}
