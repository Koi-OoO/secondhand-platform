import http from './index'

const AI_CHAT_TIMEOUT = 40_000

/**
 * 向 Java AI 入口发送单条问题，只携带会话 ID 和本次文本。
 * 错误由聊天页面在校验当前账号后处理，避免旧请求跳转或通知新账号。
 */
export function chatWithAi(conversationId, message) {
  const payload = { message }
  if (conversationId) {
    payload.conversationId = conversationId
  }

  return http.post('/ai/chat', payload, { timeout: AI_CHAT_TIMEOUT, skipAuthRedirect: true, skipErrorMessage: true })
}
