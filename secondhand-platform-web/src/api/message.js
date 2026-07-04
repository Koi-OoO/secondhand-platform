import http from './index'

export function sendMessage(toUserId, content, productId) {
  return http.post('/message/send', null, {
    params: {
      toUserId,
      content,
      productId
    }
  })
}

export function createConversation(peerId, productId, options = {}) {
  return http.post('/message/conversation', null, {
    params: {
      peerId,
      productId
    },
    skipErrorMessage: options.skipErrorMessage
  })
}

export function getConversations() {
  return http.get('/message/conversations')
}

export function getChat(peerId, params = {}) {
  return http.get('/message/chat', {
    params: {
      peerId,
      page: params.page || 1,
      size: params.size || 50
    }
  })
}

export function getMessageUnreadCount() {
  return http.get('/message/unread-count')
}

export function getOnlineStatus(userIds = []) {
  const ids = [...new Set(userIds.map(Number).filter(id => id > 0))]
  if (!ids.length) {
    return Promise.resolve({})
  }
  return http.get('/message/online-status', {
    params: {
      userIds: ids.join(',')
    }
  })
}
