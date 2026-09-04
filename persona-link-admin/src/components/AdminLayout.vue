<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { getMe, logout as logoutApi } from '../api'
import { getProfile, saveProfile } from '../auth'

const router = useRouter()
const route = useRoute()
const menuOpen = ref(false)
const operatorName = ref(getProfile()?.displayName ?? '管理员')
const roleType = ref(getProfile()?.roleType ?? 3)

const navigation = [
  { icon: '⌁', label: '运营总览', to: '/' },
  { icon: '▤', label: '题型管理', to: '/types' },
  { icon: '✦', label: 'AI题库助手', to: '/ai-question-bank' },
  { icon: '◇', label: '分类标签', to: '/categories' },
  { icon: '▧', label: '素材库', to: '/materials' },
  { icon: '□', label: '发布排期', to: '/releases' },
  { icon: '▥', label: '数据看板', to: '/analytics' },
  { icon: '≡', label: '操作审计', to: '/audits' },
  { icon: '⚙', label: '系统设置', to: '/settings', adminOnly: true },
]
const visibleNavigation = computed(() => navigation.filter((item) => !item.adminOnly || roleType.value === 1))

function closeMenu() {
  menuOpen.value = false
}

async function logout() {
  try { await logoutApi() } finally { await router.replace('/login') }
}

onMounted(async () => {
  const profile = await getMe()
  saveProfile(profile)
  operatorName.value = profile.displayName
  roleType.value = profile.roleType
})
</script>

<template>
  <main class="admin-shell">
    <header class="top-header">
      <button class="menu-toggle" type="button" aria-label="打开导航" @click="menuOpen = !menuOpen">☰</button>
      <RouterLink class="brand" to="/" @click="closeMenu"><span>♥</span> 心动测测 <b>· 运营后台</b></RouterLink>
      <div class="operator">
        <span class="operator-face" aria-hidden="true">⌣</span>
        <span class="operator-name">{{ operatorName }}</span>
        <span v-if="roleType === 3" class="role-chip">只读</span>
        <button class="logout-button" type="button" @click="logout">退出</button>
      </div>
    </header>

    <aside class="sidebar" :class="{ open: menuOpen }">
      <nav aria-label="后台主导航">
        <RouterLink
          v-for="item in visibleNavigation"
          :key="item.to"
          class="nav-item"
          :class="{ active: route.path === item.to || (item.to !== '/' && route.path.startsWith(`${item.to}/`)) }"
          :to="item.to"
          @click="closeMenu"
        >
          <span aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
        </RouterLink>
      </nav>
      <div class="sidebar-mascot" aria-hidden="true"><span>♥</span><i>✦</i></div>
    </aside>
    <button v-if="menuOpen" class="menu-mask" aria-label="关闭导航" @click="closeMenu"></button>

    <section class="workspace">
      <RouterView />
    </section>
  </main>
</template>
