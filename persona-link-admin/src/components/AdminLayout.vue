<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { getMe, logout as logoutApi } from '../api'
import { getProfile, saveProfile } from '../auth'
import AdminIcon from './AdminIcon.vue'
import { confirmPendingChanges, discardPendingChanges } from '../composables/useUnsavedChanges'

const router = useRouter()
const route = useRoute()
const menuOpen = ref(false)
const operatorName = ref(getProfile()?.displayName ?? '管理员')
const roleType = ref(getProfile()?.roleType ?? 3)

const navigation = [
  { icon: 'overview', label: '运营总览', to: '/' },
  { icon: 'content', label: '题型管理', to: '/types' },
  { icon: 'home', label: '首页配置', to: '/home-config' },
  { icon: 'ai', label: 'AI题库助手', to: '/ai-question-bank' },
  { icon: 'category', label: '分类标签', to: '/categories' },
  { icon: 'calendar', label: '发布排期', to: '/releases' },
  { icon: 'analytics', label: '数据看板', to: '/analytics' },
  { icon: 'audit', label: '操作审计', to: '/audits' },
  { icon: 'settings', label: '系统设置', to: '/settings', adminOnly: true },
]
const visibleNavigation = computed(() => navigation.filter((item) => !item.adminOnly || roleType.value === 1))

function closeMenu() {
  menuOpen.value = false
}

async function logout() {
  if (!await confirmPendingChanges('当前内容尚未保存。请取消并保存草稿，或确认放弃修改并退出登录。')) return
  discardPendingChanges()
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
          <span aria-hidden="true"><AdminIcon :name="item.icon" /></span>{{ item.label }}
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
