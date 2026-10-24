export function totalUnread(messages, notifications) {
  return Math.max(0, Number(messages) || 0) + Math.max(0, Number(notifications) || 0)
}
