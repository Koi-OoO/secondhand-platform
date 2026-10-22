// 只记录当前标签页已经展示的身份；不放进 localStorage，避免其他标签页悄悄覆盖它。
let currentUserId = null

export function setRequestUserId(value) {
  const id = Number(value)
  currentUserId = Number.isSafeInteger(id) && id > 0 ? String(id) : null
}

export function getRequestUserId() {
  return currentUserId
}

export function bindSocketIdentity(address) {
  const url = new URL(address, typeof window === 'undefined' ? undefined : window.location.href)
  if (currentUserId) url.searchParams.set('activeUserId', currentUserId)
  return url.toString()
}
