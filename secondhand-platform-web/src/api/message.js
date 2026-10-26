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

export function getConversations(options = {}) {
  return http.get('/message/conversations', options)
}

export function getChat(peerId, params = {}, options = {}) {
  return http.get('/message/chat', {
    ...options,
    params: {
      peerId,
      page: params.page || 1,
      size: params.size || 50
    }
  })
}

export function getMessageUnreadCount(options = {}) {
  return http.get('/message/unread-count', options)
}

export function getOnlineStatus(userIds = [], options = {}) {
  const ids = [...new Set(userIds.map(Number).filter(id => id > 0))]
  if (!ids.length) {
    return Promise.resolve({})
  }
  return http.get('/message/online-status', {
    ...options,
    params: {
      userIds: ids.join(',')
    }
  })
}
