export function totalUnread(messages, notifications) {
  return Math.max(0, Number(messages) || 0) + Math.max(0, Number(notifications) || 0)
}

export function notificationActor(item) {
  if (item?.type !== 'favorite') return null
  const id = String(item.actorUserId || '')
  if (!/^[1-9]\d*$/.test(id)) return null
  return {
    name: String(item.actorNickname || '').trim() || '校园用户',
    avatar: typeof item.actorAvatar === 'string' ? item.actorAvatar : '',
    route: { name: 'SellerProfile', params: { id } }
  }
}
