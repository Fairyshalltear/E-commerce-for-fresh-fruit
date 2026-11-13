import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore, type AccountRole } from './stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    role?: AccountRole
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('./views/HomeView.vue'),
    },
    { path: '/products', name: 'products', component: () => import('./views/ProductsView.vue') },
    { path: '/products/:productId', name: 'product-detail', component: () => import('./views/ProductDetailView.vue') },
    {
      path: '/login',
      name: 'login',
      component: () => import('./views/AuthView.vue'),
    },
    {
      path: '/account',
      name: 'account',
      component: () => import('./views/BuyerView.vue'),
      meta: { requiresAuth: true, role: 'BUYER' },
      children: [
        { path: '', name: 'account-home', redirect: { name: 'account-profile' } },
        {
          path: 'profile',
          name: 'account-profile',
          component: () => import('./views/BuyerProfileView.vue'),
        },
        {
          path: 'addresses',
          name: 'account-addresses',
          component: () => import('./views/BuyerAddressesView.vue'),
        },
        { path: 'favorites', name: 'account-favorites', component: () => import('./views/BuyerFavoritesView.vue') },
        {
          path: 'security',
          name: 'account-security',
          component: () => import('./views/BuyerSecurityView.vue'),
        },
      ],
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('./views/AdminView.vue'),
      meta: { requiresAuth: true, role: 'ADMIN' },
      children: [
        { path: '', name: 'admin-home', redirect: '/admin/products' },
        { path: 'products', component: () => import('./views/AdminProductsView.vue') },
        { path: 'products/new', component: () => import('./views/AdminProductEditView.vue') },
        { path: 'products/:productId/edit', component: () => import('./views/AdminProductEditView.vue') },
        { path: 'categories', component: () => import('./views/AdminCategoriesView.vue') },
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (!to.meta.requiresAuth) return true

  const auth = useAuthStore()
  if (!auth.session) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.role && auth.session.account.role !== to.meta.role) {
    return { name: auth.session.account.role === 'ADMIN' ? 'admin' : 'account' }
  }
  return true
})

export default router
