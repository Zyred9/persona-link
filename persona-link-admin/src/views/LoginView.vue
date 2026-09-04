<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../api'

const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const message = ref('')
const submitting = ref(false)

async function submit() {
  if (!username.value.trim() || !password.value) {
    message.value = '请输入账号和密码。'
    return
  }

  submitting.value = true
  try {
    await login(username.value.trim(), password.value)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') && !route.query.redirect.startsWith('//')
      ? route.query.redirect
      : '/'
    await router.replace(redirect)
  } catch (error) {
    message.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-card">
      <div class="brand-heart" aria-hidden="true">♥</div>
      <p class="eyebrow">PERSONA LINK</p>
      <h1>心动测测</h1>
      <p class="login-subtitle">运营后台</p>
      <form @submit.prevent="submit">
        <label>账号<input v-model="username" autocomplete="username" placeholder="请输入运营账号" @input="message = ''" /></label>
        <label>密码<input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" @input="message = ''" /></label>
        <button class="primary-button" type="submit" :disabled="submitting">{{ submitting ? '登录中…' : '进入后台' }}</button>
      </form>
      <p v-if="message" class="form-message" role="alert">{{ message }}</p>
    </section>
  </main>
</template>
