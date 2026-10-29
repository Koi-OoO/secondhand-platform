import {
  ORDER_WAIT_MEETUP,
  ORDER_WAIT_CONFIRM,
  ORDER_COMPLETED,
  ORDER_CANCELLED,
  ORDER_WAIT_ACCEPT,
  ORDER_NEGOTIATING
} from './constant.js'

export function orderTimeline(order) {
  const events = [{ label: '下单', time: order.createTime || null }]
  if (order.acceptTime) events.push({ label: '卖家接单', time: order.acceptTime })
  if (order.deliverTime || [ORDER_WAIT_CONFIRM, ORDER_COMPLETED, ORDER_NEGOTIATING].includes(order.status)) events.push({ label: '卖家声明已交付', time: order.deliverTime || null })
  if (order.deliveryIssueTime) events.push({ label: '买家反馈交易问题', time: order.deliveryIssueTime })
  if (order.finishTime || order.status === ORDER_COMPLETED) events.push({ label: '买家确认完成', time: order.finishTime || null })
  if (order.status === ORDER_CANCELLED) events.push({ label: order.cancelType === 2 ? '卖家拒绝交付' : '买家取消订单', time: order.cancelTime || null })
  if (order.evaluation) events.push({ label: '买家评价', time: order.evaluation.createTime || null })
  return events
}

export function orderActions(order, userId) {
  if (Number(order.buyerId) === Number(userId)) {
    if ([ORDER_WAIT_MEETUP, ORDER_WAIT_ACCEPT].includes(order.status)) return ['cancel']
    if (order.status === ORDER_WAIT_CONFIRM) return ['confirm', 'issue']
    if (order.status === ORDER_NEGOTIATING) return ['confirm']
    if (order.status === ORDER_COMPLETED && !order.evaluated && !order.evaluation) return ['evaluate']
  }
  if (Number(order.sellerId) === Number(userId) && order.status === ORDER_WAIT_CONFIRM) return ['remind']
  if (Number(order.sellerId) === Number(userId) && order.status === ORDER_WAIT_ACCEPT) return ['accept', 'reject']
  if (Number(order.sellerId) === Number(userId) && order.status === ORDER_WAIT_MEETUP) return order.meetupTime ? ['deliver', 'reject'] : ['reject']
  return []
}

export function reminderCooling(order, now = Date.now()) {
  const time = order.lastRemindTime
  return !!time && now - new Date(typeof time === 'string' ? time.replace(' ', 'T') : time).getTime() < 86400000
}
