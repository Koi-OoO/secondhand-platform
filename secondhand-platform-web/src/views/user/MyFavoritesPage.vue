<template>
  <div class="my-favorites">
    <h2>我的收藏</h2>

    <div v-if="loading" class="loading-wrap">
      <el-skeleton :rows="3" animated />
    </div>

    <template v-else>
      <div v-if="list.length" class="favorite-grid">
        <ProductCard
          v-for="item in list"
          :key="item.favoriteId"
          :product="item.product"
          :show-seller="true"
        />
      </div>

      <div v-else class="empty-state">
        <div class="empty-icon">⭐</div>
        <p class="empty-text">还没有收藏商品</p>
        <router-link to="/products" class="btn-browse">去逛逛</router-link>
      </div>
    </template>

    <div v-if="total > size" class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next"
        background
        @current-change="fetchData"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMyFavorites } from '@/api/product'
import ProductCard from '@/components/common/ProductCard.vue'

const list = ref([])
const loading = ref(true)
const page = ref(1)
const size = ref(12)
const total = ref(0)

onMounted(() => fetchData())

async function fetchData() {
  loading.value = true
  try {
    const data = await getMyFavorites({ page: page.value, size: size.value })
    list.value = (data.records || []).filter(item => item?.product?.id)
    total.value = data.total || 0
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.my-favorites h2 {
  margin-bottom: $space-lg;
  font-size: $font-size-2xl;
  font-weight: $font-weight-bold;
  letter-spacing: $letter-spacing-tight;
  color: $color-text;
}

.favorite-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  background: $color-bg-warm;
  border: 1px dashed $color-border;
  border-radius: $radius-lg;
  .empty-icon { font-size: 48px; margin-bottom: 12px; }
  .empty-text { font-size: 16px; color: $color-text-secondary; margin-bottom: 16px; }
}

.btn-browse {
  display: inline-block;
  padding: 10px 28px;
  background: linear-gradient(135deg, $color-primary, $color-primary-dark);
  color: #fff;
  border-radius: $radius-full;
  font-weight: 700;
  box-shadow: $shadow-button;
  transition: transform $transition-fast, filter $transition-fast;
  &:hover { transform: translateY(-1px); filter: brightness(1.04); }
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: $space-xl;
}

.loading-wrap { padding: $space-lg; }

@media (min-width: 640px) {
  .favorite-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (min-width: 1024px) {
  .favorite-grid { grid-template-columns: repeat(4, 1fr); }
}
</style>
