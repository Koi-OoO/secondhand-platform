/** 最近账号只是登录页的展示记录，不能用作登录凭证或判断用户权限。 */
export const RECENT_ACCOUNTS_KEY = 'campus:recent-accounts:v1'
const MAX_ACCOUNTS = 5
const defaultStorage = () => window.localStorage

function safeAvatar(value) {
  if (typeof value !== 'string') return ''
  const avatar = value.trim()
  if (avatar.length > 2048 || /[\\\u0000-\u0020]/.test(avatar)) return ''
  if (avatar.startsWith('/') && !avatar.startsWith('//')) return avatar
  try {
    const url = new URL(avatar)
    return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? avatar : ''
  } catch {
    return ''
  }
}

function displayAccount(value) {
  if (!value || typeof value.username !== 'string') return null
  const username = value.username.trim()
  if (!username || username.length > 100) return null
  // 只挑选这三个字段，禁止把整个用户资料、密码或令牌直接存入 localStorage。
  return {
    username,
    nickname: typeof value.nickname === 'string' ? value.nickname.trim().slice(0, 100) || username : username,
    avatar: safeAvatar(value.avatar),
  }
}

function resolveStorage(storage) {
  return typeof storage === 'function' ? storage() : storage
}

export function loadRecentAccounts(storage = defaultStorage) {
  try {
    const raw = resolveStorage(storage)?.getItem(RECENT_ACCOUNTS_KEY)
    if (!raw || raw.length > 32000) return []
    const saved = JSON.parse(raw)
    if (saved?.version !== 1 || !Array.isArray(saved.accounts)) return []
    const accounts = []
    for (const value of saved.accounts) {
      const account = displayAccount(value)
      if (account && !accounts.some(item => item.username === account.username)) accounts.push(account)
      if (accounts.length === MAX_ACCOUNTS) break
    }
    return accounts
  } catch {
    // 浏览器禁用存储、数据损坏都不应阻止正常输入账号密码登录。
    return []
  }
}

function saveRecentAccounts(accounts, storage) {
  try {
    resolveStorage(storage)?.setItem(RECENT_ACCOUNTS_KEY, JSON.stringify({ version: 1, accounts }))
  } catch { /* 存储不可用时降级为普通登录，不影响服务端会话。 */ }
  return accounts
}

export function rememberRecentAccount(profile, storage = defaultStorage) {
  const accounts = loadRecentAccounts(storage)
  const account = displayAccount(profile)
  if (!account) return accounts
  return saveRecentAccounts(
    [account, ...accounts.filter(item => item.username !== account.username)].slice(0, MAX_ACCOUNTS),
    storage,
  )
}

export function removeRecentAccount(username, storage = defaultStorage) {
  const accounts = loadRecentAccounts(storage).filter(item => item.username !== username)
  // 只更新这个列表，不删除服务器账号，也不清除按账号保存的 AI 聊天记录。
  return saveRecentAccounts(accounts, storage)
}
