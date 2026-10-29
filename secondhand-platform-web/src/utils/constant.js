// ============================================
// 枚举常量映射
// ============================================

/** 性别 */
export const GENDER = { 0: '未知', 1: '男', 2: '女' }

/** 用户状态 */
export const USER_STATUS = { 0: '禁用', 1: '正常' }

/** 商品成色 */
export const CONDITION = {
  1: '全新',
  2: '几乎全新',
  3: '轻微使用痕迹',
  4: '明显使用痕迹'
}

/** 商品状态 */
export const PRODUCT_STATUS = {
  1: '在售',
  2: '已下架',
  3: '已售出'
}

/** 商品状态颜色 */
export const PRODUCT_STATUS_COLOR = {
  1: 'success',
  2: 'info',
  3: 'warning'
}

/** 订单状态值（与后端 com.secondhand.constant.OrderStatus 一一对应） */
export const ORDER_WAIT_MEETUP = 1
export const ORDER_WAIT_CONFIRM = 2
export const ORDER_COMPLETED = 3
export const ORDER_CANCELLED = 4
export const ORDER_WAIT_ACCEPT = 5
export const ORDER_NEGOTIATING = 6

/** 订单状态文案 */
export const ORDER_STATUS = {
  [ORDER_WAIT_MEETUP]: '待面交',
  [ORDER_WAIT_CONFIRM]: '待买家确认',
  [ORDER_COMPLETED]: '已完成',
  [ORDER_CANCELLED]: '已取消',
  [ORDER_WAIT_ACCEPT]: '待卖家接单',
  [ORDER_NEGOTIATING]: '交易待协商'
}

/** 订单状态颜色 */
export const ORDER_STATUS_COLOR = {
  [ORDER_WAIT_ACCEPT]: 'warning',
  [ORDER_WAIT_MEETUP]: 'primary',
  [ORDER_WAIT_CONFIRM]: 'primary',
  [ORDER_COMPLETED]: 'success',
  [ORDER_CANCELLED]: 'info'
}
