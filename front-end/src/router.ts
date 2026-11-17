import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore, type AccountRole } from './stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    role?: AccountRole
    module?: string
    ownerOnly?: boolean
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
    { path: '/cart', name: 'cart', component: () => import('./views/CartView.vue'), meta: { requiresAuth: true, role: 'BUYER' } },
    { path: '/checkout', name: 'checkout', component: () => import('./views/BuyerCheckoutView.vue'), meta: { requiresAuth: true, role: 'BUYER' } },
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
        { path: 'orders', name: 'account-orders', component: () => import('./views/BuyerOrdersView.vue') },
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
        { path: 'ai-history', name: 'account-ai-history', component: () => import('./views/BuyerAiHistoryView.vue') },
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
      meta: { requiresAuth: true },
      children: [
        { path: 'products', component: () => import('./views/AdminProductsView.vue'), meta: { module: 'PRODUCTS' } },
        { path: 'products/new', component: () => import('./views/AdminProductEditView.vue'), meta: { module: 'PRODUCTS' } },
        { path: 'products/:productId/edit', component: () => import('./views/AdminProductEditView.vue'), meta: { module: 'PRODUCTS' } },
        { path: 'categories', component: () => import('./views/AdminCategoriesView.vue'), meta: { module: 'PRODUCTS' } },
        { path: 'buyers', component: () => import('./views/AdminBuyersView.vue'), meta: { module: 'BUYERS' } },
        { path: 'buyers/:buyerId', component: () => import('./views/AdminBuyerDetailView.vue'), meta: { module: 'BUYERS' } },
        { path: 'orders', name: 'admin-orders', component: () => import('./views/AdminOrdersView.vue'), meta: { module: 'ORDERS' } },
        { path: 'after-sales', name: 'admin-after-sales', component: () => import('./views/AdminAfterSalesView.vue'), meta: { module: 'AFTER_SALES' } },
        { path: 'inventory/documents/:documentId', component: () => import('./views/AdminInventoryDocumentView.vue'), meta: { module: 'INVENTORY' } },
        { path: 'inventory/:section?', component: () => import('./views/AdminInventoryView.vue'), meta: { module: 'INVENTORY' } },
        { path: 'reviews', name: 'admin-reviews', component: () => import('./views/AdminReviewsView.vue'), meta: { module: 'PRODUCTS' } },
        { path: 'analytics', name: 'admin-analytics', component: () => import('./views/AdminAnalyticsView.vue'), meta: { module: 'STATISTICS' } },
        { path: 'content', name: 'admin-content', component: () => import('./views/AdminContentView.vue'), meta: { module: 'CONTENT' } },
        { path: 'ai/knowledge', component: () => import('./views/AdminAiKnowledgeView.vue'), meta: { module: 'AI' } },
        { path: 'ai/conversations', component: () => import('./views/AdminAiRecordsView.vue'), meta: { module: 'AI' } },
        { path: 'ai/rules', component: () => import('./views/AdminAiRulesView.vue'), meta: { module: 'AI' } },
        { path: 'ai/statistics', component: () => import('./views/AdminAiStatisticsView.vue'), meta: { module: 'AI' } },
        { path: 'ai/models', component: () => import('./views/AdminAiModelsView.vue'), meta: { ownerOnly: true } },
        { path: 'staff', name: 'admin-staff', component: () => import('./views/AdminStaffView.vue'), meta: { ownerOnly: true } },
        { path: 'audit-logs', name: 'admin-audit-logs', component: () => import('./views/AdminAuditLogsView.vue'), meta: { ownerOnly: true } },
      ],
    },
  ],
})
const moduleHome: Record<string, string> = { PRODUCTS: 'products', ORDERS: 'orders', INVENTORY: 'inventory', AFTER_SALES: 'after-sales', CONTENT: 'content', STATISTICS: 'analytics', BUYERS: 'buyers', AI: 'ai/knowledge' }

router.beforeEach((to) => {
  if (!to.meta.requiresAuth) return true

  const auth = useAuthStore()
  if (!auth.session) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.role && auth.session.account.role !== to.meta.role) {
    return { name: auth.session.account.role !== 'BUYER' ? 'admin' : 'account' }
  }
  if (to.meta.role === 'BUYER' && auth.session.account.role === 'BUYER') return true
  if (auth.session.account.role === 'BUYER') return { name: 'account' }
  if (to.meta.ownerOnly && auth.session.account.role !== 'OWNER') return { name: 'admin' }
  if (to.meta.module && auth.session.account.role !== 'OWNER'
      && !auth.session.account.modulePermissions?.includes(to.meta.module)) return { name: 'admin' }
  if (to.path === '/admin') {
    const first = ['PRODUCTS', 'ORDERS', 'INVENTORY', 'AFTER_SALES', 'CONTENT', 'STATISTICS', 'BUYERS', 'AI']
      .find((module) => auth.session?.account.role === 'OWNER' || auth.session?.account.modulePermissions?.includes(module))
    return first ? { path: `/admin/${moduleHome[first]}` } : { name: 'login' }
  }
  return true
})

export default router
