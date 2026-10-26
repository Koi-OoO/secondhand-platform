import http from './index'

export function createOrder(productId, quantity, address) {
  return http.post('/order/create', null, { params: { productId, quantity, address } })
}

export function getBoughtOrders(params = {}, options = {}) {
  return http.get('/order/bought', { ...options, params })
}

export function getSoldOrders(params = {}, options = {}) {
  return http.get('/order/sold', { ...options, params })
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

export function deliverOrder(id) {
  return http.put(`/order/${id}/deliver`)
}

export function batchDeliverOrders(items) {
  return http.put('/order/batch/deliver', { items })
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

export function getOrderDetail(id, options = {}) {
  return http.get(`/order/${id}`, options)
}

export function acceptOrder(id) {
  return http.put(`/order/${id}/accept`)
}

export function getOrdersWithUser(peerId, params = {}, options = {}) {
  return http.get(`/order/with-user/${peerId}`, { ...options, params })
}

export function remindBuyer(id) { return http.put(`/order/${id}/remind`) }
export function reportDeliveryIssue(id, issue) { return http.put(`/order/${id}/delivery-issue`, null, { params: { issue } }) }
