<template>
  <main class="admin-page"><header class="admin-topbar"><RouterLink class="brand" to="/">鲜果集<span class="brand-note">管理后台</span></RouterLink><div class="topbar-actions"><RouterLink to="/">查看商城</RouterLink><span>{{ auth.session?.account.displayName }}</span><button class="text-button" @click="logout">退出登录</button></div></header>
    <div class="admin-layout"><aside class="admin-sidebar"><p class="eyebrow">运营管理</p>
      <RouterLink v-if="can('PRODUCTS')" to="/admin/products" :class="{ active: route.path.startsWith('/admin/products') }">商品管理</RouterLink>
      <RouterLink v-if="can('PRODUCTS')" to="/admin/categories" active-class="active">分类管理</RouterLink>
      <RouterLink v-if="can('BUYERS')" to="/admin/buyers" :class="{ active: route.path.startsWith('/admin/buyers') }">买家管理</RouterLink>
      <RouterLink v-if="can('ORDERS')" to="/admin/orders" :class="{ active: route.path.startsWith('/admin/orders') }">订单管理</RouterLink>
      <RouterLink v-if="can('AFTER_SALES')" to="/admin/after-sales" :class="{ active: route.path.startsWith('/admin/after-sales') }">售后处理</RouterLink>
      <RouterLink v-if="can('PRODUCTS')" to="/admin/reviews" :class="{ active: route.path.startsWith('/admin/reviews') }">评价管理</RouterLink>
      <RouterLink v-if="can('INVENTORY')" to="/admin/inventory" :class="{ active: route.path.startsWith('/admin/inventory') }">库存管理<span class="inventory-menu-count">{{ alerts.count.value === null ? '…' : alerts.count.value }}{{ alerts.error.value ? ' !' : '' }}</span></RouterLink>
      <RouterLink v-if="can('STATISTICS')" to="/admin/analytics" :class="{ active: route.path.startsWith('/admin/analytics') }">经营统计</RouterLink>
      <RouterLink v-if="can('CONTENT')" to="/admin/content" :class="{ active: route.path.startsWith('/admin/content') }">首页内容</RouterLink>
      <template v-if="owner"><hr><RouterLink to="/admin/staff" :class="{ active: route.path.startsWith('/admin/staff') }">店员与权限</RouterLink><RouterLink to="/admin/audit-logs" :class="{ active: route.path.startsWith('/admin/audit-logs') }">系统日志</RouterLink></template>
      <p v-if="alerts.error.value" class="inventory-alert-error" role="alert">预警刷新失败<button class="text-action" @click="alerts.refresh">重试</button></p><p class="admin-sidebar-note">单店经营管理</p></aside><section class="admin-content"><RouterView /></section></div>
  </main>
</template>
<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { computed } from 'vue'
import { useInventoryAlerts } from '../composables/useInventoryAlerts'
import { useAuthStore } from '../stores/auth'
const router = useRouter(), route = useRoute(), auth = useAuthStore()
const alerts = useInventoryAlerts()
const owner = computed(() => auth.session?.account.role === 'OWNER')
function can(module: string) { return owner.value || auth.session?.account.modulePermissions?.includes(module) === true }
function logout() { auth.logout(); void router.replace('/') }
</script>
