<template>
  <main class="page-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/">鲜果集</RouterLink>
      <button class="text-button" type="button" @click="signOut">退出登录</button>
    </header>
    <section class="account-panel">
      <p class="eyebrow">买家账户</p>
      <h1>你好，{{ auth.session?.account.displayName }}</h1>
      <p>用户名：{{ auth.session?.account.username }}</p>
      <p>身份：买家</p>
      <div class="account-actions">
        <RouterLink class="primary-link" to="/">返回商城首页</RouterLink>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

function signOut() {
  auth.logout()
  void router.replace('/')
}
</script>
