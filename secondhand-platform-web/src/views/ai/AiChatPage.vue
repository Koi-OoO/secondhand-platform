<template>
  <main class="ai-page">
    <section class="ai-shell" aria-labelledby="ai-title">
      <header class="ai-hero">
        <div class="ai-hero__copy">
          <p class="ai-eyebrow"><span aria-hidden="true">✦</span> MARKET SCOUT</p>
          <h1 id="ai-title">问问闲物，<em>找到合适的好物。</em></h1>
          <p class="ai-hero__hint">告诉我预算、品类或成色，我会从在售商品里帮你整理线索。</p>
        </div>
        <div class="ai-hero__badge" aria-hidden="true">
          <span>AI</span>
          <small>只读查询</small>
        </div>
      </header>

      <section ref="messageBody" class="ai-messages" aria-live="polite" aria-label="AI 对话记录">
        <article
          v-for="message in messages"
          :key="message.id"
          class="ai-message"
          :class="`ai-message--${message.role}`"
        >
          <div class="ai-message__avatar" aria-hidden="true">
            {{ message.role === 'user' ? '我' : 'AI' }}
          </div>
          <div class="ai-message__content">
            <p class="ai-message__text">{{ message.content }}</p>

            <div v-if="message.items?.length" class="ai-products" aria-label="推荐商品">
              <button
                v-for="item in message.items"
                :key="item.productId"
                type="button"
                class="ai-product"
                @click="openProduct(item)"
              >
                <span class="ai-product__index">{{ String(item.productId).padStart(2, '0') }}</span>
                <span class="ai-product__body">
                  <strong>{{ item.title || `商品 #${item.productId}` }}</strong>
                  <span class="ai-product__meta">
                    <b v-if="item.price !== undefined && item.price !== null">{{ formatProductPrice(item.price) }}</b>
                    <span v-if="item.conditionLevel">{{ conditionLabel(item.conditionLevel) }}</span>
                    <span v-if="item.stock !== undefined && item.stock !== null">库存 {{ item.stock }}</span>
                  </span>
                  <span v-if="item.tradeLocation" class="ai-product__location">⌖ {{ item.tradeLocation }}</span>
                </span>
                <span class="ai-product__arrow" aria-hidden="true">↗</span>
              </button>
            </div>
          </div>
        </article>

        <article v-if="sending" class="ai-message ai-message--assistant ai-message--loading">
          <div class="ai-message__avatar" aria-hidden="true">AI</div>
          <div class="ai-message__content">
            <p class="ai-message__text ai-message__typing"><i></i><i></i><i></i><span>正在整理商品线索</span></p>
          </div>
        </article>

        <div v-if="errorMessage" class="ai-error" role="alert">
          <span class="ai-error__mark" aria-hidden="true">!</span>
          <div>
            <strong>这次查询没有完成</strong>
            <p>{{ errorMessage }}</p>
          </div>
          <button type="button" class="ai-error__retry" :disabled="sending || !draft.trim()" @click="retryLastMessage">
            重试
          </button>
        </div>
      </section>

      <div class="ai-suggestions" aria-label="快捷提问">
        <span>试试：</span>
        <button v-for="suggestion in suggestions" :key="suggestion" type="button" @click="useSuggestion(suggestion)">
          {{ suggestion }}
        </button>
      </div>

      <form class="ai-composer" @submit.prevent="sendMessage">
        <label class="sr-only" for="ai-message-input">输入你的商品问题</label>
        <textarea
          id="ai-message-input"
          v-model="draft"
          rows="2"
          maxlength="1000"
          :disabled="sending"
          placeholder="例如：找 300 元以内、成色较新的台灯"
          @keydown.enter.exact.prevent="sendMessage"
        ></textarea>
        <div class="ai-composer__footer">
          <span>{{ draft.length }}/1000 · AI 只查询商品，不会替你下单</span>
          <button type="submit" class="ai-send" :disabled="sending || !draft.trim()">
            <span>{{ sending ? '查询中...' : '发送' }}</span>
            <span aria-hidden="true">↗</span>
          </button>
        </div>
      </form>
    </section>
  </main>
</template>

<script setup>
import { nextTick, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { chatWithAi } from '@/api/ai'
import { normalizeAiResponse } from '@/utils/ai'
import { CONDITION } from '@/utils/constant'

const route = useRoute()
const router = useRouter()
const messageBody = ref(null)
const draft = ref('')
const sending = ref(false)
const errorMessage = ref('')
const conversationId = ref(null)
const failedMessageId = ref(null)
let messageSequence = 0

const suggestions = ['300 元以内的台灯', '适合宿舍的显示器', '几乎全新的教材']
const messages = ref([
  {
    id: 'welcome',
    role: 'assistant',
    content: '你好！我可以帮你查找在售闲置商品。告诉我预算、品类或成色，就从市场里开始找。'
  }
])

function createMessageId() {
  messageSequence += 1
  return `${Date.now()}-${messageSequence}`
}

async function sendMessage() {
  const content = draft.value.trim()
  if (!content || sending.value) {
    return
  }

  if (failedMessageId.value) {
    messages.value = messages.value.filter(message => message.id !== failedMessageId.value)
    failedMessageId.value = null
  }

  const userMessage = { id: createMessageId(), role: 'user', content }
  messages.value.push(userMessage)
  sending.value = true
  errorMessage.value = ''

  try {
    const response = await chatWithAi(conversationId.value, content)
    if (response?.conversationId) {
      conversationId.value = String(response.conversationId)
    }
    const normalized = normalizeAiResponse(response)
    messages.value.push({
      id: createMessageId(),
      role: 'assistant',
      content: normalized.answer || '暂时没有找到符合条件的商品。你可以换个关键词或预算再试试。',
      items: normalized.items
    })
    draft.value = ''
  } catch (error) {
    failedMessageId.value = userMessage.id
    errorMessage.value = getErrorMessage(error)
    // Keep the text in the composer so the user can correct or retry it.
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

function retryLastMessage() {
  if (draft.value.trim()) {
    sendMessage()
  }
}

function useSuggestion(suggestion) {
  if (sending.value) {
    return
  }
  draft.value = suggestion
  sendMessage()
}

function openProduct(item) {
  const productId = Number(item?.productId)
  if (!Number.isSafeInteger(productId) || productId <= 0) {
    return
  }
  // Build a same-origin route from the validated id; never navigate to detailPath.
  router.push({ name: 'ProductDetail', params: { id: String(productId) } })
}

function formatProductPrice(value) {
  const price = Number(value)
  return Number.isFinite(price) ? `¥${price.toFixed(2)}` : '价格待确认'
}

function conditionLabel(level) {
  return CONDITION[level] || `成色 ${level}`
}

function getErrorMessage(error) {
  if (error?.response?.status === 401) {
    router.replace({ name: 'Login', query: { redirect: route.fullPath } })
    return '登录状态已失效，请重新登录后再试。'
  }
  if (error?.code === 'ECONNABORTED' || error?.code === 'ETIMEDOUT') {
    return 'AI 响应时间较长，请稍后重试。'
  }
  return error?.message || '服务暂时不可用，请稍后重试。'
}

async function scrollToBottom() {
  await nextTick()
  if (messageBody.value) {
    messageBody.value.scrollTop = messageBody.value.scrollHeight
  }
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.ai-page {
  width: 100%;
  max-width: 980px;
  margin: 0 auto;
  padding: $space-xl $space-lg $space-2xl;
}

.ai-shell {
  position: relative;
  overflow: hidden;
  border: $border-ink;
  border-radius: $radius-xl;
  background: $color-bg-card;
  box-shadow: 7px 7px 0 $color-ink;
}

.ai-hero {
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: $space-lg;
  padding: 34px 38px 30px;
  border-bottom: $border-ink;
  background:
    radial-gradient(circle at 82% 18%, rgba($color-yellow, 0.48) 0 9%, transparent 9.3%),
    linear-gradient(125deg, $color-paper-2 0%, $color-paper 100%);

  &::after {
    position: absolute;
    right: 126px;
    bottom: 24px;
    width: 110px;
    height: 10px;
    border-top: 2px dashed rgba($color-ink, 0.3);
    content: '';
    transform: rotate(-7deg);
  }
}

.ai-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px;
  color: $color-red-deep;
  font-family: $font-family-mono;
  font-size: 11px;
  font-weight: $font-weight-heavy;
  letter-spacing: 1.7px;

  span {
    display: inline-grid;
    width: 19px;
    height: 19px;
    place-items: center;
    border: 1.5px solid $color-ink;
    border-radius: 50%;
    background: $color-yellow;
    color: $color-ink;
  }
}

.ai-hero h1 {
  margin: 0;
  color: $color-text;
  font-family: $font-family-display;
  font-size: clamp(32px, 5vw, 46px);
  font-weight: $font-weight-heavy;
  line-height: 1.2;

  em {
    display: block;
    color: $color-red;
    font-style: normal;
  }
}

.ai-hero__hint {
  max-width: 560px;
  margin: 13px 0 0;
  color: $color-text-secondary;
  font-size: $font-size-sm;
  line-height: $line-height-relaxed;
}

.ai-hero__badge {
  position: relative;
  z-index: 1;
  display: grid;
  width: 100px;
  height: 100px;
  flex-shrink: 0;
  place-items: center;
  align-content: center;
  border: 2px solid $color-ink;
  border-radius: 50%;
  background: $color-red;
  box-shadow: 4px 4px 0 $color-ink;
  color: #fff;
  transform: rotate(8deg);

  span {
    font-family: $font-family-display;
    font-size: 32px;
    font-weight: $font-weight-heavy;
    line-height: 1;
  }

  small {
    margin-top: 5px;
    font-family: $font-family-mono;
    font-size: 9px;
    font-weight: $font-weight-bold;
    letter-spacing: 1px;
  }
}

.ai-messages {
  min-height: 380px;
  max-height: 56vh;
  overflow-y: auto;
  padding: 28px 38px 22px;
  background:
    linear-gradient(rgba($color-paper-2, 0.86), rgba($color-paper-2, 0.86)),
    repeating-linear-gradient(0deg, transparent 0 31px, rgba($color-ink, 0.045) 32px);
  scroll-behavior: smooth;
}

.ai-message {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  max-width: 780px;
  margin-bottom: 20px;

  &--user {
    flex-direction: row-reverse;
    margin-left: auto;

    .ai-message__content {
      align-items: flex-end;
    }

    .ai-message__text {
      border-color: $color-ink;
      background: $color-yellow;
      box-shadow: 3px 3px 0 $color-ink;
    }
  }
}

.ai-message__avatar {
  display: grid;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  place-items: center;
  border: 2px solid $color-ink;
  border-radius: 50%;
  background: $color-ink;
  color: $color-paper;
  font-family: $font-family-mono;
  font-size: 10px;
  font-weight: $font-weight-heavy;
}

.ai-message--user .ai-message__avatar {
  background: $color-red;
}

.ai-message__content {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}

.ai-message__text {
  max-width: min(640px, 100%);
  margin: 0;
  padding: 12px 15px;
  border: 1.5px solid rgba($color-ink, 0.3);
  border-radius: 4px 13px 13px 13px;
  background: $color-bg-card;
  color: $color-text;
  line-height: $line-height-relaxed;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-message--user .ai-message__text {
  border-radius: 13px 4px 13px 13px;
}

.ai-message__typing {
  display: flex;
  align-items: center;
  gap: 4px;
  color: $color-text-secondary;

  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: $color-red;
    animation: dotPulse 1.1s ease-in-out infinite;

    &:nth-child(2) { animation-delay: 0.16s; }
    &:nth-child(3) { animation-delay: 0.32s; }
  }

  span {
    margin-left: 6px;
    font-size: $font-size-xs;
  }
}

@keyframes dotPulse {
  0%, 100% { opacity: 0.35; transform: translateY(0); }
  45% { opacity: 1; transform: translateY(-3px); }
}

.ai-products {
  display: grid;
  width: min(700px, 100%);
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.ai-product {
  position: relative;
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 11px;
  padding: 13px 12px 12px;
  border: $border-ink;
  border-radius: $radius-md;
  background: $color-paper-2;
  box-shadow: 3px 3px 0 $color-ink;
  color: $color-text;
  text-align: left;
  cursor: pointer;
  transition: transform $transition-fast, box-shadow $transition-fast, background $transition-fast;

  &:hover,
  &:focus-visible {
    outline: none;
    background: $color-bg-hover;
    box-shadow: 5px 5px 0 $color-ink;
    transform: translate(-2px, -2px);
  }
}

.ai-product__index {
  display: grid;
  width: 31px;
  height: 31px;
  flex-shrink: 0;
  place-items: center;
  border: 1.5px solid $color-ink;
  border-radius: 50%;
  background: $color-yellow;
  font-family: $font-family-mono;
  font-size: 10px;
  font-weight: $font-weight-heavy;
}

.ai-product__body {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 5px;

  strong {
    overflow: hidden;
    font-size: $font-size-sm;
    line-height: 1.4;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.ai-product__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: $color-text-secondary;
  font-size: 11px;

  b {
    color: $color-red-deep;
    font-family: $font-family-mono;
    font-size: 14px;
  }
}

.ai-product__location {
  overflow: hidden;
  color: $color-text-muted;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-product__arrow {
  flex-shrink: 0;
  color: $color-red;
  font-size: 20px;
  line-height: 1;
}

.ai-error {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 2px 0 0 44px;
  padding: 11px 13px;
  border: 1.5px solid $color-red;
  border-radius: $radius-md;
  background: #fff3ee;

  &__mark {
    display: grid;
    width: 23px;
    height: 23px;
    flex-shrink: 0;
    place-items: center;
    border: 1.5px solid $color-red;
    border-radius: 50%;
    color: $color-red-deep;
    font-family: $font-family-mono;
    font-weight: $font-weight-heavy;
  }

  strong {
    color: $color-text;
    font-size: $font-size-sm;
  }

  p {
    margin: 3px 0 0;
    color: $color-text-secondary;
    font-size: $font-size-xs;
  }

  &__retry {
    margin-left: auto;
    padding: 6px 11px;
    border: 1.5px solid $color-ink;
    border-radius: $radius-full;
    background: $color-yellow;
    color: $color-text;
    font-size: 12px;
    font-weight: $font-weight-bold;
    cursor: pointer;

    &:disabled {
      cursor: not-allowed;
      opacity: 0.55;
    }
  }
}

.ai-suggestions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 0 38px 16px;
  color: $color-text-muted;
  font-size: $font-size-xs;

  button {
    padding: 5px 10px;
    border: 1.5px dashed rgba($color-ink, 0.4);
    border-radius: $radius-full;
    background: transparent;
    color: $color-text-secondary;
    font-size: 12px;
    cursor: pointer;

    &:hover {
      border-style: solid;
      background: $color-yellow;
      color: $color-text;
    }
  }
}

.ai-composer {
  padding: 18px 38px 24px;
  border-top: $border-ink;
  background: $color-paper;

  textarea {
    display: block;
    width: 100%;
    min-height: 66px;
    resize: vertical;
    padding: 13px 15px;
    border: $border-ink;
    border-radius: $radius-md;
    outline: 0;
    background: $color-bg-card;
    color: $color-text;
    font: inherit;
    line-height: 1.6;

    &:focus {
      box-shadow: 3px 3px 0 $color-ink;
    }

    &:disabled {
      background: $color-paper-3;
      cursor: wait;
    }

    &::placeholder {
      color: $color-text-muted;
    }
  }
}

.ai-composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 10px;
  color: $color-text-muted;
  font-size: 11px;
}

.ai-send {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 96px;
  height: 40px;
  justify-content: center;
  padding: 0 17px;
  border: $border-ink;
  border-radius: $radius-full;
  background: $color-red;
  box-shadow: $shadow-button;
  color: #fff;
  font-size: $font-size-sm;
  font-weight: $font-weight-heavy;
  cursor: pointer;
  transition: transform $transition-fast, box-shadow $transition-fast, background $transition-fast;

  &:hover:not(:disabled) {
    background: $color-red-deep;
    box-shadow: $shadow-hover;
    transform: translate(-2px, -2px);
  }

  &:disabled {
    background: $color-muted;
    box-shadow: 2px 2px 0 $color-ink-2;
    cursor: not-allowed;
  }
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: $bp-sm) {
  .ai-page {
    padding: 12px 0 $space-xl;
  }

  .ai-shell {
    border-right: 0;
    border-left: 0;
    border-radius: 0;
    box-shadow: none;
  }

  .ai-hero {
    align-items: flex-start;
    padding: 26px $space-md 24px;
  }

  .ai-hero__badge {
    width: 74px;
    height: 74px;

    span { font-size: 24px; }
    small { font-size: 8px; }
  }

  .ai-messages {
    min-height: 420px;
    padding: 22px $space-md 18px;
  }

  .ai-products {
    grid-template-columns: 1fr;
  }

  .ai-error {
    margin-left: 0;
  }

  .ai-suggestions,
  .ai-composer {
    padding-right: $space-md;
    padding-left: $space-md;
  }

  .ai-composer__footer {
    align-items: flex-end;
  }

  .ai-composer__footer > span {
    max-width: 55%;
    line-height: 1.5;
  }
}
</style>
