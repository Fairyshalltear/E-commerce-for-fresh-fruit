<template>
  <main class="admin-page"><header class="admin-topbar"><RouterLink class="brand" to="/">鲜果集<span class="brand-note">管理后台</span></RouterLink><div class="topbar-actions"><RouterLink to="/">查看商城</RouterLink><span>{{ auth.session?.account.displayName }}</span><button class="text-button" @click="logout">退出登录</button></div></header>
    <div class="admin-layout"><aside class="admin-sidebar"><p class="eyebrow">运营管理</p><RouterLink to="/admin/products" :class="{ active: route.path.startsWith('/admin/products') }">商品管理</RouterLink><RouterLink to="/admin/categories" active-class="active">分类管理</RouterLink><RouterLink to="/admin/buyers" :class="{ active: route.path.startsWith('/admin/buyers') }">买家管理</RouterLink><RouterLink to="/admin/orders" :class="{ active: route.path.startsWith('/admin/orders') }">订单管理</RouterLink><RouterLink to="/admin/after-sales" :class="{ active: route.path.startsWith('/admin/after-sales') }">售后处理</RouterLink><RouterLink to="/admin/reviews" :class="{ active: route.path.startsWith('/admin/reviews') }">评价管理</RouterLink><RouterLink to="/admin/inventory" :class="{ active: route.path.startsWith('/admin/inventory') }">库存管理<span class="inventory-menu-count">{{ alerts.count.value === null ? '…' : alerts.count.value }}{{ alerts.error.value ? ' !' : '' }}</span></RouterLink><p v-if="alerts.error.value" class="inventory-alert-error" role="alert">预警刷新失败<button class="text-action" @click="alerts.refresh">重试</button></p><p class="admin-sidebar-note">商品、交易与买家账户</p></aside><section class="admin-content"><RouterView /></section></div>
  </main>
</template>
<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { useInventoryAlerts } from '../composables/useInventoryAlerts'
import { useAuthStore } from '../stores/auth'
const router = useRouter(), route = useRoute(), auth = useAuthStore()
const alerts = useInventoryAlerts()
function logout() { auth.logout(); void router.replace('/') }
</script>
