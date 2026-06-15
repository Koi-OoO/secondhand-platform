<template>
  <div class="my-products">
    <h2>我的商品</h2>

    <div v-if="loading" class="loading-wrap">
      <el-skeleton :rows="3" animated />
    </div>

    <template v-else>
      <!-- 商品列表 -->
      <div v-if="products.length" class="product-list">
        <div v-for="item in products" :key="item.id" class="product-item card">
          <div class="product-item__img" @click="$router.push(`/product/${item.id}`)">
            <img :src="getFirstImage(item)" :alt="item.title" />
          </div>
          <div class="product-item__info" @click="$router.push(`/product/${item.id}`)">
            <div class="product-item__top">
              <h4>{{ item.title }}</h4>
              <el-tag :type="statusType(item.status)" size="small">
                {{ statusMap[item.status] || '未知' }}
              </el-tag>
            </div>
            <div class="product-item__price">{{ formatPrice(item.price) }}</div>
            <div class="product-item__meta">
              <span>{{ item.viewCount }} 浏览</span>
              <span>{{ formatDate(item.createTime) }}</span>
            </div>
          </div>
          <div class="product-item__actions">
            <el-button
              v-if="item.status === 2"
              size="small"
              @click="$router.push(`/product/${item.id}/edit`)"
            >
              编辑
            </el-button>
            <el-button
              v-if="item.status === 1"
              type="danger"
              size="small"
              plain
              :loading="actionLoading[item.id] === 'off'"
              :disabled="!!actionLoading[item.id]"
              @click="handleOffShelf(item)"
            >
              下架
            </el-button>
            <el-button
              v-if="item.status === 2"
              type="success"
              size="small"
              plain
              :loading="actionLoading[item.id] === 'on'"
              :disabled="!!actionLoading[item.id]"
              @click="handleOnShelf(item)"
            >
              上架
            </el-button>
            <span v-if="item.status === 3" class="sold-label">已售出</span>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="empty-state card">
        <div class="empty-icon">📦</div>
        <p class="empty-text">还没有发布过商品</p>
        <router-link to="/product/publish" class="btn-publish">发布第一件商品</router-link>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProductPage, offShelfProduct, onShelfProduct } from '@/api/product'
import { useUserStore } from '@/stores/user'
import { formatPrice, formatDate } from '@/utils/format'
import { PRODUCT_STATUS, PRODUCT_STATUS_COLOR } from '@/utils/constant'

const userStore = useUserStore()
const products = ref([])
const loading = ref(true)
const actionLoading = ref({})
const statusMap = PRODUCT_STATUS

onMounted(fetchProducts)

async function fetchProducts() {
  loading.value = true
  try {
    const data = await getProductPage({
      page: 1,
      size: 50,
      sellerId: userStore.userInfo?.id
    })
    products.value = data.records || []
  } catch {
    products.value = []
  } finally {
    loading.value = false
  }
}

function getFirstImage(item) {
  if (item.images?.length && item.images[0].url) return item.images[0].url
  return ''
}

function statusType(status) {
  return PRODUCT_STATUS_COLOR[status] || 'info'
}

async function handleOffShelf(item) {
  if (!item?.id || item.status !== 1 || actionLoading.value[item.id]) return
  try {
    await ElMessageBox.confirm(
      `确定下架「${item.title}」吗？下架后不再公开可见。`,
      '确认下架',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    actionLoading.value[item.id] = 'off'
    await offShelfProduct(item.id)
    ElMessage.success('下架成功')
    item.status = 2
  } catch { /* 取消或接口错误 */ }
  finally {
    delete actionLoading.value[item.id]
  }
}

async function handleOnShelf(item) {
  if (!item?.id || item.status !== 2 || actionLoading.value[item.id]) return
  try {
    await ElMessageBox.confirm(
      `确定上架「${item.title}」吗？上架后将重新对外可见。`,
      '确认上架',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
    )
    actionLoading.value[item.id] = 'on'
    await onShelfProduct(item.id)
    ElMessage.success('上架成功')
    item.status = 1
  } catch { /* 取消或接口错误 */ }
  finally {
    delete actionLoading.value[item.id]
  }
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.my-products {
  h2 {
    margin-bottom: $space-lg;
    font-size: $font-size-2xl;
    font-weight: $font-weight-bold;
    letter-spacing: $letter-spacing-tight;
    color: $color-text;
  }
}

.product-item {
  display: flex;
  gap: $space-md;
  padding: $space-md;
  margin-bottom: $space-md;
  transition: all $transition-fast;

  &__img {
    width: 120px;
    height: 120px;
    flex-shrink: 0;
    border-radius: $radius-md;
    overflow: hidden;
    cursor: pointer;
    background: $color-bg;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  &__info {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    cursor: pointer;
    min-width: 0;
  }

  &__top {
    display: flex;
    align-items: center;
    gap: $space-sm;
    margin-bottom: $space-sm;

    h4 {
      font-size: $font-size-md;
      font-weight: 600;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  &__price {
    font-size: $font-size-lg;
    font-weight: 700;
    color: $color-primary;
  }

  &__meta {
    display: flex;
    gap: $space-md;
    font-size: $font-size-xs;
    color: $color-text-muted;
  }

  &__actions {
    display: flex;
    flex-direction: column;
    gap: 8px;
    justify-content: center;
    flex-shrink: 0;
  }
}

.sold-label {
  font-size: $font-size-sm;
  color: $color-text-muted;
  font-weight: 600;
}

// 空状态
.empty-state {
  text-align: center;
  padding: $space-2xl $space-lg;
  background: $color-bg-warm;
  border: 1px dashed $color-border;
  border-radius: $radius-lg;

  .empty-icon { font-size: 48px; margin-bottom: $space-md; }
  .empty-text { font-size: $font-size-lg; color: $color-text-secondary; margin-bottom: $space-lg; }
}

.btn-publish {
  display: inline-block;
  padding: 10px 24px;
  background: linear-gradient(135deg, $color-primary, $color-primary-dark);
  color: #fff;
  border-radius: $radius-full;
  font-weight: 700;
  box-shadow: $shadow-button;
  transition: transform $transition-fast, filter $transition-fast;
  &:hover { transform: translateY(-1px); filter: brightness(1.04); }
}

.loading-wrap { padding: $space-xl; }
</style>
