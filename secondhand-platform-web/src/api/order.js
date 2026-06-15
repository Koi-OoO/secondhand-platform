import http from './index'

export function createOrder(productId, quantity, address) {
  return http.post('/order/create', null, { params: { productId, quantity, address } })
}

export function getBoughtOrders(params = {}) {
  return http.get('/order/bought', { params })
}

export function getSoldOrders(params = {}) {
  return http.get('/order/sold', { params })
}

export function hideBoughtOrder(id) {
  return http.delete(`/order/${id}/buyer`)
}

export function hideSoldOrder(id) {
  return http.delete(`/order/${id}/seller`)
}

export function batchHideBoughtOrders(orderIds) {
  return http.delete('/order/batch/buyer', { data: { orderIds } })
}

export function batchHideSoldOrders(orderIds) {
  return http.delete('/order/batch/seller', { data: { orderIds } })
}

export function cancelOrder(id) {
  return http.put(`/order/${id}/cancel`)
}

export function shipOrder(id, expressNo) {
  return http.put(`/order/${id}/ship`, null, { params: { expressNo } })
}

export function batchShipOrders(items) {
  return http.put('/order/batch/ship', { items })
}

export function confirmOrder(id) {
  return http.put(`/order/${id}/confirm`)
}

export function rejectOrder(id, reason) {
  return http.put(`/order/${id}/reject`, null, { params: { reason } })
}

export function batchRejectOrders(orderIds, reason) {
  return http.put('/order/batch/reject', { orderIds, reason })
}

export function getPendingCount() {
  return http.get('/order/pending-count')
}
