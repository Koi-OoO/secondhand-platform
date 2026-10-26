import http from './index'

export function getNotifications(params = {}) {
  return http.get('/notification/page', { params })
}

export function getNotificationUnreadCount(options = {}) {
  return http.get('/notification/unread-count', options)
}

export function markNotificationRead(id) {
  return http.put(`/notification/${id}/read`)
}

export function markAllNotificationsRead() {
  return http.put('/notification/read-all')
}
