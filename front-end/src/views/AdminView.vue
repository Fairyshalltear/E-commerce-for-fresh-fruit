<template>
  <main class="page-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/">鲜果集后台</RouterLink>
      <button class="text-button" type="button" @click="signOut">退出登录</button>
    </header>
    <section class="account-panel">
      <p class="eyebrow">管理员工作台</p>
      <h1>{{ auth.session?.account.displayName }}</h1>
      <p>管理员账户已通过角色验证。</p>
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
