import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getUserProfile } from '@/api/user'

const routes = [
  {
    path: '/seller/:id',
    name: 'SellerProfile',
    component: () => import('@/views/user/SellerProfilePage.vue'),
    meta: { layout: 'default' }
  },
  // === 认证页（无布局） ===
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/LoginPage.vue'),
    meta: { layout: 'blank', guest: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/RegisterPage.vue'),
    meta: { layout: 'blank', guest: true }
  },

  // === 公共页 ===
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/home/HomePage.vue'),
    meta: { layout: 'default' }
  },

  // === 商品浏览 ===
  {
    path: '/products',
    name: 'Products',
    component: () => import('@/views/product/ProductListPage.vue'),
    meta: { layout: 'default' }
  },
  {
    path: '/product/:id',
    name: 'ProductDetail',
    component: () => import('@/views/product/ProductDetailPage.vue'),
    meta: { layout: 'default' }
  },

  // === 商品发布（需登录） ===
  {
    path: '/product/publish',
    name: 'ProductPublish',
    component: () => import('@/views/product/ProductPublishPage.vue'),
    meta: { requiresAuth: true, layout: 'default' }
  },
  {
    path: '/product/:id/edit',
    name: 'ProductEdit',
    component: () => import('@/views/product/ProductPublishPage.vue'),
    meta: { requiresAuth: true, layout: 'default' }
  },

  // === 用户中心（需登录） ===
  {
    path: '/user',
    component: () => import('@/layouts/UserLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: '/user/profile'
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/user/ProfilePage.vue')
      },
      {
        path: 'profile/edit',
        name: 'ProfileEdit',
        component: () => import('@/views/user/ProfileEdit.vue')
      },
      {
        path: 'products',
        name: 'MyProducts',
        component: () => import('@/views/user/MyProductsPage.vue')
      },
      {
        path: 'favorites',
        name: 'MyFavorites',
        component: () => import('@/views/user/MyFavoritesPage.vue')
      },
      {
        path: 'orders',
        name: 'MyOrders',
        component: () => import('@/views/user/MyOrdersPage.vue')
      }
    ]
  },

  // === 404 ===
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { layout: 'blank' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

// 鉴权守卫：需要登录的页面校验 session
router.beforeEach((to, from, next) => {
  if (to.matched.some(r => r.meta.requiresAuth)) {
    const userStore = useUserStore()

    // store 中已有登录态 → 直接放行（App.vue onMounted 已恢复 session）
    if (userStore.isLoggedIn) {
      next()
      return
    }

    // store 中无登录态 → 尝试静默恢复一次 session
    getUserProfile({ skipAuthRedirect: true })
      .then(data => {
        userStore.userInfo = data
        next()
      })
      .catch(() => {
        next({ name: 'Login', query: { redirect: to.fullPath } })
      })
  } else {
    next()
  }
})

export default router
