<template>
  <div class="profile-page">
    <h2 class="page-title">个人资料</h2>

    <!-- 个人信息卡片 -->
    <div class="profile-card card">
      <div class="profile-card__head">
        <el-avatar :size="80" :src="userStore.avatar" class="profile-avatar">
          {{ userStore.nickname.charAt(0) }}
        </el-avatar>
        <div class="profile-head__info">
          <h3>{{ userStore.nickname }}</h3>
          <span class="profile-username">@{{ userStore.username }}</span>
          <span class="profile-credit">
            信誉分 <strong>{{ userStore.userInfo?.creditScore ?? 100 }}</strong>
          </span>
        </div>
        <div class="profile-head__actions">
          <router-link :to="`/seller/${userStore.userInfo?.id}`" class="btn-edit">
            <el-icon><Shop /></el-icon>
            我的主页
          </router-link>
          <router-link to="/user/profile/edit" class="btn-edit">
            <el-icon><Edit /></el-icon>
            编辑资料
          </router-link>
        </div>
      </div>
    </div>

    <!-- 详细信息 -->
    <div class="profile-detail card">
      <h4 class="section-title">详细信息</h4>
      <div class="detail-grid">
        <div class="detail-item">
          <span class="detail-label">用户名</span>
          <span class="detail-value">{{ userStore.username }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">昵称</span>
          <span class="detail-value">{{ userStore.nickname || '未设置' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">手机号</span>
          <span class="detail-value">{{ userStore.userInfo?.phone || '未绑定' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">邮箱</span>
          <span class="detail-value">{{ userStore.userInfo?.email || '未绑定' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">性别</span>
          <span class="detail-value">{{ genderLabel }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">地址</span>
          <span class="detail-value">{{ userStore.userInfo?.address || '未设置' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">生日</span>
          <span class="detail-value">{{ birthdayDisplay }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">注册时间</span>
          <span class="detail-value">{{ formatDate(userStore.userInfo?.createTime) }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">账号状态</span>
          <span class="detail-value">
            <el-tag :type="userStore.userInfo?.status === 1 ? 'success' : 'danger'" size="small">
              {{ userStore.userInfo?.status === 1 ? '正常' : '已禁用' }}
            </el-tag>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Edit, Shop } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { GENDER } from '@/utils/constant'
import { formatDate, formatDateShort } from '@/utils/format'

const userStore = useUserStore()

const genderLabel = computed(() => GENDER[userStore.userInfo?.gender] || '未知')
const birthdayDisplay = computed(() => formatDateShort(userStore.userInfo?.birthday) || '未设置')
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.profile-page {
  display: flex;
  flex-direction: column;
  gap: $space-lg;
}

// === 头像卡片 ===
.profile-card {
  padding: $space-xl;

  &__head {
    display: flex;
    align-items: center;
    gap: $space-lg;
  }
}

.profile-avatar {
  flex-shrink: 0;
  border: 3px solid $color-primary-lighter;
}

.profile-head__info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;

  h3 {
    font-size: $font-size-xl;
    font-weight: 600;
  }
}

.profile-username {
  font-size: $font-size-sm;
  color: $color-text-muted;
}

.profile-credit {
  display: inline-block;
  width: fit-content;
  font-size: $font-size-sm;
  color: $color-success;
  background: rgba($color-success, 0.08);
  padding: 2px 10px;
  border-radius: $radius-full;
  margin-top: 4px;

  strong {
    font-weight: 700;
  }
}

.profile-head__actions {
  display: flex;
  gap: $space-sm;
  flex-shrink: 0;
}

.btn-edit {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 20px;
  background: $color-bg-warm;
  color: $color-primary;
  font-size: $font-size-sm;
  font-weight: 500;
  border-radius: $radius-full;
  border: 1px solid $color-primary-lighter;
  transition: all $transition-normal;

  &:hover {
    background: $color-primary;
    color: #fff;
    border-color: $color-primary;
  }
}

// === 详细信息 ===
.profile-detail {
  padding: $space-xl;
}

.section-title {
  font-size: $font-size-lg;
  font-weight: 600;
  margin-bottom: $space-lg;
  color: $color-text;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;

  .detail-item {
    display: flex;
    align-items: center;
    padding: $space-md 0;
    border-bottom: 1px solid $color-border-light;
  }

  .detail-label {
    width: 80px;
    flex-shrink: 0;
    font-size: $font-size-sm;
    color: $color-text-muted;
  }

  .detail-value {
    font-size: $font-size-md;
    color: $color-text;
  }
}

@media (max-width: 768px) {
  .profile-card__head {
    flex-direction: column;
    text-align: center;
  }
  .profile-head__info {
    align-items: center;
  }
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
