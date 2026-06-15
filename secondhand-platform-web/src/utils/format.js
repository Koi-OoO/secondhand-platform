// ============================================
// 格式化工具
// ============================================

/**
 * 格式化价格
 * @param {number} val - 金额
 * @returns {string} ¥12.00
 */
export function formatPrice(val) {
  if (val == null || isNaN(val)) return '¥0.00'
  return '¥' + Number(val).toFixed(2)
}

/**
 * 格式化日期
 * @param {string|Date} val
 * @returns {string} 2026-06-01 12:30
 */
export function formatDate(val) {
  if (!val) return ''
  const d = new Date(val)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${day} ${h}:${min}`
}

/**
 * 格式化简短日期
 * @param {string|Date} val
 * @returns {string} 2026-06-01
 */
export function formatDateShort(val) {
  if (!val) return ''
  const d = new Date(val)
  return d.toISOString().slice(0, 10)
}

/**
 * 手机号脱敏
 * @param {string} phone
 * @returns {string} 138****8000
 */
export function maskPhone(phone) {
  if (!phone || phone.length < 7) return phone || ''
  return phone.slice(0, 3) + '****' + phone.slice(-4)
}

/**
 * 相对时间
 * @param {string|Date} val
 * @returns {string} 3分钟前 / 2小时前 / 3天前
 */
export function relativeTime(val) {
  if (!val) return ''
  const now = Date.now()
  const then = new Date(val).getTime()
  const diff = now - then
  const sec = Math.floor(diff / 1000)
  const min = Math.floor(sec / 60)
  const hour = Math.floor(min / 60)
  const day = Math.floor(hour / 24)

  if (sec < 60) return '刚刚'
  if (min < 60) return `${min}分钟前`
  if (hour < 24) return `${hour}小时前`
  if (day < 30) return `${day}天前`
  return formatDate(val)
}
