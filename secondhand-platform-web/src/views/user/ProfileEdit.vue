<template>
  <div class="profile-edit">
    <h2 class="page-title">编辑资料</h2>

    <div class="edit-card card">
      <el-form
        ref="formRef"
        :model="form"
        label-position="top"
        :label-width="100"
      >
        <!-- 头像上传 -->
        <el-form-item label="头像" class="avatar-item">
          <div class="avatar-upload">
            <el-avatar :size="72" :src="form.avatar" class="avatar-preview">
              {{ form.nickname?.charAt(0) || '?' }}
            </el-avatar>
            <div class="avatar-actions">
              <el-upload
                :http-request="handleAvatarUpload"
                :show-file-list="false"
                :before-upload="beforeAvatarUpload"
                accept="image/jpeg,image/png,image/webp"
              >
                <el-button size="small" type="primary" plain>更换头像</el-button>
              </el-upload>
              <p class="avatar-tip">支持 jpg/png/webp，≤5MB</p>
            </div>
          </div>
        </el-form-item>

        <div class="form-grid">
          <el-form-item label="昵称">
            <el-input v-model="form.nickname" placeholder="给自己取个昵称" maxlength="50" />
          </el-form-item>

          <el-form-item label="手机号">
            <el-input v-model="form.phone" placeholder="绑定手机号" maxlength="20" />
          </el-form-item>

          <el-form-item label="邮箱">
            <el-input v-model="form.email" placeholder="绑定邮箱" maxlength="100" />
          </el-form-item>

          <el-form-item label="性别">
            <el-select v-model="form.gender" placeholder="选择性别" style="width:100%">
              <el-option :value="0" label="未知" />
              <el-option :value="1" label="男" />
              <el-option :value="2" label="女" />
            </el-select>
          </el-form-item>

          <el-form-item label="生日">
            <el-date-picker
              v-model="form.birthday"
              type="date"
              placeholder="选择生日"
              value-format="YYYY-MM-DD"
              :disabled-date="disabledDate"
              style="width:100%"
            />
          </el-form-item>

          <el-form-item label="地址">
            <el-input v-model="form.address" placeholder="填写地址" maxlength="255" />
          </el-form-item>
        </div>

        <div class="form-actions">
          <el-button @click="$router.back()">取消</el-button>
          <el-button type="primary" :loading="userStore.loading" @click="handleSave">
            保存修改
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { uploadImage } from '@/api/upload'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)

const form = reactive({
  nickname: '',
  avatar: '',
  phone: '',
  email: '',
  gender: 0,
  birthday: '',
  address: ''
})

onMounted(() => {
  const u = userStore.userInfo
  if (u) {
    form.nickname = u.nickname || ''
    form.avatar = u.avatar || ''
    form.phone = u.phone || ''
    form.email = u.email || ''
    form.gender = u.gender ?? 0
    form.birthday = u.birthday || ''
    form.address = u.address || ''
  }
})

function beforeAvatarUpload(file) {
  const allowed = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowed.includes(file.type)) {
    ElMessage.error('仅支持 jpg/png/webp 格式')
    return false
  }
  return true
}

async function handleAvatarUpload({ file, onSuccess, onError }) {
  try {
    const url = await uploadImage(file)
    form.avatar = url
    ElMessage.success('头像上传成功')
    onSuccess()
  } catch {
    onError()
  }
}

// 禁止选择将来日期作为生日
function disabledDate(time) {
  return time.getTime() > Date.now()
}

async function handleSave() {
  const data = {}
  for (const [k, v] of Object.entries(form)) {
    if (v !== '' && v !== null) data[k] = v
  }

  const ok = await userStore.updateProfile(data)
  if (ok) {
    ElMessage.success('资料已更新')
    router.push('/user/profile')
  }
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.profile-edit {
  max-width: 640px;
}

.edit-card {
  padding: $space-xl;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 $space-lg;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: $space-md;
  margin-top: $space-xl;
  padding-top: $space-lg;
  border-top: 1px solid $color-border-light;
}

@media (max-width: 600px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}

// === 头像上传 ===
.avatar-item { grid-column: 1 / -1; }

.avatar-upload {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar-preview {
  flex-shrink: 0;
  border: 2px solid $color-border-light;
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.avatar-tip {
  font-size: 12px;
  color: $color-text-muted;
  margin: 0;
}
</style>
