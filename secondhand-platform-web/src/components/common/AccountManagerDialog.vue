<template>
  <el-dialog v-model="visible" title="切换与管理账号" class="account-manager" width="min(480px, calc(100vw - 32px))" append-to-body
    :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" @closed="resetForm">
    <p class="account-manager__intro">本次会话内，已添加的账号可直接切换。退出全部账号后，需要重新验证密码。</p>

    <p v-if="error" class="account-manager__error" role="alert">{{ error }}</p>
    <p v-if="reading" class="account-manager__hint" role="status">正在读取账号…</p>
    <ul v-else class="account-manager__list" aria-label="本次会话已授权账号">
      <li v-for="account in userStore.sessionAccounts" :key="account.userId" class="account-manager__row" :class="{ 'is-current': account.current }">
        <el-avatar :size="42" :src="account.avatar || ''">{{ (account.nickname || account.username).charAt(0) }}</el-avatar>
        <div class="account-manager__identity">
          <strong>{{ account.nickname || account.username }}</strong>
          <span>@{{ account.username }}</span>
        </div>
        <span v-if="account.current" class="account-manager__current">当前</span>
        <el-button v-else :disabled="busy" size="small" @click="switchTo(account.userId)">切换</el-button>
        <button class="account-manager__remove" type="button" :disabled="busy" :aria-label="`移除账号 ${account.username}`" @click="remove(account)">×</button>
      </li>
    </ul>

    <form v-if="adding" class="account-manager__form" aria-label="添加账号" @submit.prevent="add">
      <h3>添加一个账号</h3>
      <label for="managed-account-username">用户名</label>
      <el-input id="managed-account-username" ref="usernameInput" v-model="form.username" autocomplete="username" maxlength="50" :disabled="busy" placeholder="输入已有账号的用户名" />
      <label for="managed-account-password">密码</label>
      <el-input id="managed-account-password" v-model="form.password" type="password" autocomplete="current-password" show-password :disabled="busy" placeholder="验证一次，添加到本次会话" />
      <div class="account-manager__form-actions">
        <el-button :disabled="busy" @click="resetForm">取消</el-button>
        <el-button type="primary" native-type="submit" :loading="userStore.switching" :disabled="reading">验证并添加</el-button>
      </div>
    </form>
    <el-button v-else class="account-manager__add" :disabled="busy || userStore.sessionAccounts.length >= 5 || expired" @click="showAdd">
      ＋ 添加账号 <span class="account-manager__count">{{ userStore.sessionAccounts.length }}/5</span>
    </el-button>

    <p class="account-manager__hint">仅保存本次会话的授权，不保存密码；密码变更、账号停用或授权到期后需重新验证。</p>
    <template #footer>
      <el-button v-if="expired" type="primary" @click="goToLogin">重新登录</el-button>
      <el-button v-else :disabled="busy" @click="logoutAll">退出全部账号</el-button>
      <el-button :disabled="busy" @click="visible = false">完成</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const userStore = useUserStore()
const reading = ref(false)
const adding = ref(false)
const expired = ref(false)
const error = ref('')
const usernameInput = ref(null)
const form = reactive({ username: '', password: '' })
const busy = computed(() => reading.value || userStore.switching || userStore.loading)

watch(visible, async open => {
  if (!open) return
  resetForm()
  error.value = ''
  expired.value = false
  await readAccounts()
})

function describeError(failure) {
  if (failure?.response?.status === 401) {
    expired.value = true
    return '登录会话已失效，请重新登录。'
  }
  return failure?.response?.data?.message || failure?.message || '操作失败，请稍后重试'
}

async function readAccounts() {
  reading.value = true
  try {
    await userStore.loadSessionAccounts()
  } catch (failure) {
    error.value = describeError(failure)
  } finally { reading.value = false }
}

function resetForm() {
  adding.value = false
  form.username = ''
  form.password = ''
}

async function showAdd() {
  error.value = ''
  adding.value = true
  await nextTick()
  usernameInput.value?.focus()
}

async function add() {
  if (busy.value) return
  error.value = ''
  if (!form.username.trim() || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }
  try {
    if (await userStore.addAccount(form.username.trim(), form.password)) {
      resetForm()
      ElMessage.success('已添加，点击“切换”即可登录该账号')
    }
  } catch (failure) {
    error.value = describeError(failure)
    if (!expired.value) await readAccounts()
  } finally { form.password = '' }
}

async function switchTo(userId) {
  error.value = ''
  try {
    await userStore.switchAccount(userId)
  } catch (failure) {
    error.value = describeError(failure)
    // 后端可能刚清理了过期授权，重新读取后列表与实际状态保持一致。
    if (!expired.value) await readAccounts()
  }
}

async function remove(account) {
  try {
    await ElMessageBox.confirm(
      account.current ? '移除后会切换到另一个已授权账号；如果没有其他账号，将退出登录。不会删除账号。' : '移除后，本次会话不再允许免密切换到此账号。不会删除账号。',
      `移除 ${account.nickname || account.username}？`,
      { confirmButtonText: '移除授权', cancelButtonText: '取消', type: 'warning' },
    )
  } catch { return }
  error.value = ''
  try {
    if (await userStore.removeAccount(account.userId)) ElMessage.success('已移除账号授权')
  } catch (failure) {
    error.value = describeError(failure)
    if (!expired.value) await readAccounts()
  }
}

async function logoutAll() {
  error.value = ''
  try { await userStore.logoutAllAccounts() }
  catch (failure) { error.value = describeError(failure) }
}

function goToLogin() { window.location.assign('/login') }
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.account-manager {
  &__intro { margin: 0 0 18px; line-height: 1.7; color: $color-text-secondary; }
  &__list { list-style: none; padding: 0; margin: 0 0 16px; }
  &__row { display: flex; align-items: center; gap: 10px; padding: 13px 10px; border-bottom: 1px solid rgba($color-ink, .15); }
  &__row.is-current { background: rgba($color-yellow, .2); border-radius: 10px; }
  &__identity { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
  &__identity strong, &__identity span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  &__identity strong { color: $color-text; }
  &__identity span, &__count { color: $color-text-muted; font-size: 12px; }
  &__current { color: $color-teal; font-size: 12px; font-weight: 700; white-space: nowrap; }
  &__remove { flex-shrink: 0; width: 32px; height: 32px; border: 0; border-radius: 50%; background: transparent; color: $color-text-secondary; font-size: 23px; cursor: pointer; }
  &__remove:hover:not(:disabled) { background: $color-primary-lighter; color: $color-red; }
  &__remove:disabled { opacity: .4; cursor: not-allowed; }
  &__remove:focus-visible { outline: 2px solid $color-ink; }
  &__add { width: 100%; min-height: 42px; border-style: dashed; }
  &__count { margin-left: 12px; }
  &__hint { color: $color-text-muted; font-size: 12px; line-height: 1.7; margin: 16px 0 0; }
  &__error { padding: 10px 12px; background: $color-primary-lighter; border-left: 3px solid $color-red; color: $color-red-deep; line-height: 1.6; }
  &__form { padding: 16px; background: rgba($color-yellow, .13); border: 1px dashed rgba($color-ink, .4); border-radius: 10px; }
  &__form h3 { margin: 0 0 14px; color: $color-text; font-size: 15px; }
  &__form label { display: block; font-size: 13px; margin: 12px 0 6px; }
  &__form-actions { display: flex; justify-content: flex-end; margin-top: 18px; }
}
</style>
