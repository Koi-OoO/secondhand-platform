import http from './index'

/** 提交评价 */
export function evaluate(orderId, rating, content, anonymous = false) {
  return http.post('/evaluation', null, { params: { orderId, rating, content, anonymous } })
}

/** 查看用户收到的评价 */
export function getUserEvaluations(userId, params = {}) {
  return http.get(`/evaluation/user/${userId}`, { params })
}
