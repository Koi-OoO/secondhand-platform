import http from './index'

const AI_CHAT_TIMEOUT = 40_000

/**
 * Send one plain-text message to the Java AI facade.
 * The browser only sends the conversation id and message; Session auth is
 * carried by the shared axios client.
 */
export function chatWithAi(conversationId, message) {
  const payload = { message }
  if (conversationId) {
    payload.conversationId = conversationId
  }

  return http.post('/ai/chat', payload, { timeout: AI_CHAT_TIMEOUT })
}
