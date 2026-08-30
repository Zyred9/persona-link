<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { clearDemoSession } from '../auth'

const router = useRouter()
const route = useRoute()
const menuOpen = ref(false)

const navigation = [
  { icon: '⌁', label: '运营总览', to: '/' },
  { icon: '⌂', label: '首页配置', to: '/home-config' },
  { icon: '▤', label: '题型管理', to: '/types' },
  { icon: '✧', label: 'AI 题库助手', to: '/ai-question-bank' },
  { icon: '◇', label: '分类标签', to: '/categories' },
  { icon: '□', label: '发布管理', to: '/releases' },
  { icon: '▥', label: '数据看板', to: '/analytics' },
  { icon: '⚙', label: '系统设置', to: '/settings' },
]

function closeMenu() {
  menuOpen.value = false
}

function logout() {
  clearDemoSession()
  void router.replace('/login')
}
</script>

<template>
  <main class="admin-shell">
    <header class="top-header">
      <button class="menu-toggle" type="button" aria-label="打开导航" @click="menuOpen = !menuOpen">☰</button>
      <RouterLink class="brand" to="/" @click="closeMenu"><span>♥</span> 心动测测 <b>· 运营后台</b></RouterLink>
      <div class="operator">
        <span class="demo-badge">演示模式</span>
        <span class="operator-face" aria-hidden="true">⌣</span>
        <span class="operator-name">运营小鹿</span>
        <button class="logout-button" type="button" @click="logout">退出</button>
      </div>
    </header>

    <aside class="sidebar" :class="{ open: menuOpen }">
      <nav aria-label="后台主导航">
        <RouterLink
          v-for="item in navigation"
          :key="item.to"
          class="nav-item"
          :class="{ active: route.path === item.to || (item.to !== '/' && route.path.startsWith(`${item.to}/`)) }"
          :to="item.to"
          @click="closeMenu"
        >
          <span aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
        </RouterLink>
      </nav>
      <div class="sidebar-note">
        <strong>本地 UI 演示</strong>
        <p>编辑结果不会写入服务端。</p>
      </div>
      <div class="sidebar-mascot" aria-hidden="true"><span>♥</span><i>✦</i></div>
    </aside>
    <button v-if="menuOpen" class="menu-mask" aria-label="关闭导航" @click="closeMenu"></button>

    <section class="workspace">
      <RouterView />
    </section>
  </main>
</template>
