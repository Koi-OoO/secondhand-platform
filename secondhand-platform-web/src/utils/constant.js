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

/** 订单状态 */
export const ORDER_STATUS = {
  0: '待付款',
  1: '待发货',
  2: '待收货',
  3: '已完成',
  4: '已取消'
}

/** 订单状态颜色 */
export const ORDER_STATUS_COLOR = {
  0: 'warning',
  1: 'primary',
  2: 'primary',
  3: 'success',
  4: 'info'
}
