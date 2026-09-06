<script setup lang="ts">
import { watch } from 'vue'
import { useRoute } from 'vue-router'
import ConfirmDialog from './components/ConfirmDialog.vue'
import { settleConfirm } from './composables/useConfirm'

const route = useRoute()
// 仅在导航成功后取消旧页面的待确认操作，不干扰离开页面的未保存检查。
watch(() => route.fullPath, () => settleConfirm(false), { flush: 'sync' })
</script>

<template>
  <RouterView />
  <ConfirmDialog />
</template>
