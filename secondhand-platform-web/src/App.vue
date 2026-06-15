<template>
  <el-config-provider :locale="zhCn">
    <div id="app-root">
      <div class="bg-texture"></div>
      <!--
        路由布局策略：
        - /user/* → UserLayout 已是路由父组件，直接 <router-view />
        - meta.layout='blank' → 用 BlankLayout 包裹
        - 其他 → 用 DefaultLayout 包裹（Header + Footer）
      -->
      <BlankLayout v-if="blankLayout">
        <router-view v-slot="{ Component, route: r }">
          <transition name="page" mode="out-in">
            <component :is="Component" :key="r.path" />
          </transition>
        </router-view>
      </BlankLayout>

      <DefaultLayout v-else-if="!isUserRoute">
        <router-view v-slot="{ Component, route: r }">
          <transition name="page" mode="out-in">
            <component :is="Component" :key="r.path" />
          </transition>
        </router-view>
      </DefaultLayout>

      <router-view v-else v-slot="{ Component, route: r }">
        <transition name="page" mode="out-in">
          <component :is="Component" :key="r.path" />
        </transition>
      </router-view>

      <TopProgressBar />
      <BackToTop />
    </div>
  </el-config-provider>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { useUserStore } from '@/stores/user'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import BlankLayout from '@/layouts/BlankLayout.vue'
import BackToTop from '@/components/common/BackToTop.vue'
import TopProgressBar from '@/components/common/TopProgressBar.vue'

const route = useRoute()
const userStore = useUserStore()

const isUserRoute = computed(() => route.path.startsWith('/user'))
const blankLayout = computed(() => route.meta.layout === 'blank')

onMounted(() => {
  // 不在游客页（登录/注册）触发 restoreSession，避免 401 → 重定向 → 循环闪屏
  if (!route.meta.guest) {
    userStore.restoreSession()
  }
})
</script>

<style lang="scss">
#app-root {
  min-height: 100vh;
  position: relative;
}
</style>
