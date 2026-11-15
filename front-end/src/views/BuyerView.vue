<template>
  <main class="account-page page-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/">鲜果集</RouterLink>
      <div class="topbar-actions">
        <RouterLink to="/">商城首页</RouterLink>
        <button class="text-button" type="button" @click="signOut">退出登录</button>
      </div>
    </header>

    <div class="account-heading">
      <div>
        <p class="eyebrow">买家账户</p>
        <h1>个人中心</h1>
      </div>
      <span class="account-identity">{{ auth.session?.account.displayName }}</span>
    </div>

    <div class="account-layout">
      <aside class="account-sidebar" aria-label="个人中心导航">
        <RouterLink to="/account/orders" active-class="account-nav-active">我的订单</RouterLink>
        <RouterLink to="/account/profile" active-class="account-nav-active">
          <User class="account-nav-icon" aria-hidden="true" />
          个人资料
        </RouterLink>
        <RouterLink to="/account/addresses" active-class="account-nav-active">
          <Location class="account-nav-icon" aria-hidden="true" />
          收货地址
        </RouterLink>
        <RouterLink to="/account/favorites" active-class="account-nav-active">♡ 我的收藏</RouterLink>
        <RouterLink to="/account/security" active-class="account-nav-active">
          <Lock class="account-nav-icon" aria-hidden="true" />
          账户安全
        </RouterLink>
      </aside>
      <section class="account-content">
        <RouterView />
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { Location, Lock, User } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

function signOut() {
  auth.logout()
  void router.replace('/')
}
</script>
