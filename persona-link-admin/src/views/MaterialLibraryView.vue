<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { assetUrl, deleteAsset, getAssets, uploadImage, type AssetItem } from '../api'
import { isReadOnly } from '../auth'
import { confirmAction } from '../composables/useConfirm'

const fileInput = ref<HTMLInputElement>()
const uploading = ref(false)
const keyword = ref('')
const assets = ref<AssetItem[]>([])
const typeFilter = ref('')
const message = ref('')
const visibleAssets = computed(() => assets.value.filter((item) => !typeFilter.value || item.imageType === typeFilter.value))
const readOnly = isReadOnly()

async function load() {
  try { assets.value = await getAssets(keyword.value) }
  catch (error) { message.value = error instanceof Error ? error.message : '素材加载失败' }
}

async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  if (uploading.value) return
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    await uploadImage(file)
    message.value = '素材上传成功'
    await load()
  } catch (error) { message.value = error instanceof Error ? error.message : '素材上传失败' }
  finally { uploading.value = false; input.value = '' }
}

async function copyUrl(item: AssetItem) {
  try { await navigator.clipboard.writeText(item.url); message.value = '素材链接已复制' }
  catch { message.value = '复制失败，请手动复制' }
}

async function remove(item: AssetItem) {
  if (readOnly || !await confirmAction(`确定删除素材 ${item.fileName} 吗？`, { title: '删除素材', confirmText: '确认删除', danger: true })) return
  try { await deleteAsset(item.fileName); message.value = '素材已删除'; await load() }
  catch (error) { message.value = error instanceof Error ? error.message : '删除失败' }
}

onMounted(load)
</script>

<template>
  <section class="page-stack material-page">
    <header class="page-header">
      <div><h1>素材库 <span>♡</span></h1><p>统一管理题型图标与封面，已被业务引用的素材不会被误删。</p></div>
      <button class="primary-button" type="button" :disabled="readOnly || uploading" @click="fileInput?.click()">{{ uploading ? '上传中…' : '＋ 上传图片' }}</button>
      <input ref="fileInput" class="file-input" type="file" accept="image/png,image/jpeg,image/webp" :disabled="readOnly || uploading" @change="upload" />
    </header>
    <form class="panel toolbar" @submit.prevent="load"><input v-model="keyword" placeholder="按文件名搜索" /><select v-model="typeFilter" aria-label="图片类型"><option value="">全部类型</option><option>PNG</option><option>JPG</option><option>WEBP</option></select><button class="secondary-button" type="submit">搜索</button><span>{{ visibleAssets.length }} 个素材</span></form>
    <p v-if="message" class="material-message" role="status">{{ message }}</p>
    <div v-if="visibleAssets.length" class="asset-grid">
      <article v-for="item in visibleAssets" :key="item.fileName" class="panel asset-card">
        <img :src="assetUrl(item.url)" :alt="item.fileName" />
        <div class="asset-meta"><b>{{ item.imageType }}</b><span>{{ (item.size / 1024).toFixed(1) }} KB</span><span>{{ new Date(item.updatedAt).toLocaleString() }}</span></div>
        <code :title="item.url">{{ item.url }}</code>
        <p>{{ item.referenceCount ? `已被 ${item.referenceCount} 处使用` : '未使用' }}</p>
        <div class="actions"><button class="secondary-button" @click="copyUrl(item)">复制 URL</button><button class="secondary-button" :disabled="readOnly || item.referenceCount > 0" @click="remove(item)">删除</button></div>
      </article>
    </div>
    <button v-else class="panel empty" type="button" :disabled="readOnly || uploading" @click="fileInput?.click()">{{ keyword ? '没有匹配的素材' : '暂无素材，点击上传第一张图片' }}</button>
  </section>
</template>

<style scoped>
.material-page h1 span{color:#ad79ef}.file-input{display:none}.toolbar{display:flex;align-items:center;gap:10px;padding:14px}.toolbar input{flex:1;min-width:180px}.toolbar input,.toolbar select{padding:10px 12px;border:1px solid #cbc1c9;border-radius:10px;background:#fff}.toolbar span{color:var(--muted);font-size:13px}.material-message{margin:0;color:#76519f;text-align:center;font-weight:700}.asset-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:16px}.asset-card{display:grid;gap:10px;padding:13px}.asset-card img{width:100%;height:170px;object-fit:contain;border-radius:12px;background:#f8f5f1}.asset-meta{display:flex;gap:8px;align-items:center;font-size:12px;color:var(--muted)}.asset-meta b{padding:3px 7px;border-radius:99px;background:var(--purple-soft);color:#76519f}.asset-card code{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.asset-card p{margin:0;font-size:13px;color:var(--muted)}.actions{display:flex;gap:8px}.actions button{flex:1}.actions button:disabled{opacity:.45}.empty{min-height:280px;border-style:dashed;color:#76519f;font-weight:800}@media(max-width:620px){.toolbar{align-items:stretch;flex-direction:column}.toolbar span{text-align:center}}
</style>
