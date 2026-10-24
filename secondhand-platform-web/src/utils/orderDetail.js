export function orderTimeline(order) {
  const events = [{ label: '下单', time: order.createTime || null }]
  if (order.acceptTime) events.push({ label: '卖家接单', time: order.acceptTime })
  if (order.deliverTime || [2, 3, 6].includes(order.status)) events.push({ label: '卖家声明已交付', time: order.deliverTime || null })
  if (order.deliveryIssueTime) events.push({ label: '买家反馈交易问题', time: order.deliveryIssueTime })
  if (order.finishTime || order.status === 3) events.push({ label: '买家确认完成', time: order.finishTime || null })
  if (order.status === 4) events.push({ label: order.cancelType === 2 ? '卖家拒绝交付' : '买家取消订单', time: order.cancelTime || null })
  if (order.evaluation) events.push({ label: '买家评价', time: order.evaluation.createTime || null })
  return events
}

export function orderActions(order, userId) {
  if (Number(order.buyerId) === Number(userId)) {
    if ([1, 5].includes(order.status)) return ['cancel']
    if (order.status === 2) return ['confirm', 'issue']
    if (order.status === 6) return ['confirm']
    if (order.status === 3 && !order.evaluated && !order.evaluation) return ['evaluate']
  }
  if (Number(order.sellerId) === Number(userId) && order.status === 2) return ['remind']
  if (Number(order.sellerId) === Number(userId) && order.status === 5) return ['accept', 'reject']
  if (Number(order.sellerId) === Number(userId) && order.status === 1) return order.meetupTime ? ['deliver', 'reject'] : ['reject']
  return []
}

export function reminderCooling(order, now = Date.now()) {
  const time = order.lastRemindTime
  return !!time && now - new Date(typeof time === 'string' ? time.replace(' ', 'T') : time).getTime() < 86400000
}
