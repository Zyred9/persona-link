<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const question = ref('当工作和生活发生冲突时，你更倾向于？')
const options = ref([
  '优先完成工作，生活可以稍后再说',
  '尽量平衡，两边都照顾',
  '优先生活，工作能晚就晚',
  '看具体情况，灵活调整',
])
const feedback = ref('')
const previewActive = ref(false)
const letters = 'ABCDEFGH'
const colorClasses = ['yellow', 'mint', 'purple', 'coral']
const valid = computed(() => question.value.trim() && options.value.length >= 2 && options.value.every((option) => option.trim()))

function showFeedback(message: string) {
  feedback.value = message
  window.setTimeout(() => { feedback.value = '' }, 2200)
}

function addOption() {
  if (options.value.length >= letters.length) return showFeedback('最多添加 8 个选项')
  options.value.push('')
}

function removeOption(index: number) {
  if (options.value.length <= 2) return showFeedback('至少保留 2 个选项')
  options.value.splice(index, 1)
}

function save(label: string) {
  if (!valid.value) return showFeedback('请填写完整题干和至少 2 个选项')
  showFeedback(`${label}成功（仅本地演示，未接后端）`)
}
</script>

<template>
  <section class="question-page">
    <div class="demo-notice"><b>演示模式</b> 题目编辑与保存反馈均为本地交互，尚未接入后端。</div>
    <header class="editor-header">
      <div><button class="back-link" type="button" @click="router.push('/types')">‹ 返回题型</button><h1>题目编辑 <span>✦</span></h1></div>
      <div class="header-actions">
        <button class="secondary-action" type="button" @click="save('保存草稿')">保存草稿</button>
        <button class="secondary-action" type="button" @click="previewActive = !previewActive">{{ previewActive ? '关闭高亮' : '预览' }}</button>
        <button class="primary-action" type="button" @click="save('提交审核')">提交审核</button>
      </div>
    </header>

    <ol class="steps" aria-label="题型配置进度">
      <li class="active"><b>1</b><span>基础信息</span></li><li class="active"><b>2</b><span>题目配置</span></li><li><b>3</b><span>计分规则</span></li><li><b>4</b><span>结果内容</span></li><li><b>5</b><span>发布设置</span></li>
    </ol>

    <div class="question-layout">
      <article class="question-card">
        <div class="question-index">第 1 题</div>
        <label>题干<textarea v-model="question" maxlength="120" rows="3" /></label>
        <div class="option-list">
          <div v-for="(option, index) in options" :key="index" class="option-editor">
            <span class="letter" :class="colorClasses[index % colorClasses.length]">{{ letters[index] }}</span>
            <input v-model="options[index]" :aria-label="`选项 ${letters[index]}`" maxlength="60" placeholder="请输入选项内容" />
            <button type="button" :aria-label="`删除选项 ${letters[index]}`" @click="removeOption(index)">♲</button>
          </div>
        </div>
        <button class="add-option" type="button" @click="addOption">＋ 添加选项</button>
      </article>

      <aside class="preview-card" :class="{ highlighted: previewActive }">
        <h2>移动端预览 <span>✦</span></h2>
        <div class="phone">
          <div class="phone-status"><b>9:41</b><span>▮▮ ◒ ▰</span></div>
          <div class="mini-head"><span>‹</span><span>第 1 题 / 共 22 题</span><span>•••</span></div>
          <div class="mini-body">
            <h3>{{ question || '请填写题干' }} <i>♡</i></h3>
            <div v-for="(option, index) in options" :key="index" class="preview-option">
              <span class="letter" :class="colorClasses[index % colorClasses.length]">{{ letters[index] }}</span>
              <p>{{ option || '待填写选项' }}</p>
            </div>
          </div>
          <div class="home-bar"></div>
        </div>
      </aside>
    </div>
    <div v-if="feedback" class="toast" role="status">{{ feedback }}</div>
  </section>
</template>

<style scoped>
.question-page { color: var(--ink, #211d22); }
.demo-notice { margin-bottom: 18px; padding: 11px 15px; border: 1.5px dashed #9b74dc; border-radius: 12px; background: #f3ebff; color: #6d4c9d; font-size: 13px; }.demo-notice b { margin-right: 8px; color: #ff543d; }
.editor-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 22px; }.editor-header h1 { margin: 5px 0 0; font: 700 clamp(34px, 4vw, 46px) "STKaiti", "KaiTi", serif; }.editor-header h1 span { color: #a473df; }.back-link { padding: 0; border: 0; background: transparent; color: #766f78; cursor: pointer; }
.header-actions { display: flex; flex-wrap: wrap; gap: 13px; }.primary-action, .secondary-action { padding: 11px 22px; border: 1.5px solid #211d22; border-radius: 12px; background: white; font: inherit; font-weight: 800; cursor: pointer; }.primary-action { background: #ff654f; color: white; box-shadow: 2px 3px 0 #211d22; }
.steps { display: grid; grid-template-columns: repeat(5, 1fr); max-width: 760px; margin: 26px 0 22px; padding: 0; list-style: none; }.steps li { position: relative; display: grid; justify-items: center; gap: 7px; color: #7e7780; font-size: 13px; }.steps li:not(:last-child)::after { content: ""; position: absolute; z-index: 0; top: 18px; left: calc(50% + 24px); width: calc(100% - 48px); border-top: 1.5px solid #aaa2aa; }.steps b { z-index: 1; display: grid; place-items: center; width: 37px; height: 37px; border: 1.5px solid #aaa2aa; border-radius: 50%; background: #fffdf8; font-size: 16px; }.steps .active { color: #211d22; font-weight: 800; }.steps .active b { border-color: #8e58dc; background: #caa1ff; }.steps li:first-child:not(:last-child)::after { border-color: #8e58dc; }
.question-layout { display: grid; grid-template-columns: minmax(0, 1.55fr) minmax(330px, .8fr); gap: 28px; align-items: start; }.question-card, .preview-card { padding: 26px; border: 1.5px solid #211d22; border-radius: 19px; background: #fffdf9; }.question-index { display: inline-block; padding: 11px 24px; border: 1.5px solid #211d22; border-radius: 12px; background: #d7b3ff; font-size: 19px; font-weight: 800; }.question-card > label { display: grid; gap: 10px; margin-top: 26px; font-weight: 800; }.question-card textarea { width: 100%; resize: vertical; padding: 16px; border: 1px solid #aaa2aa; border-radius: 7px; background: white; font: inherit; font-size: 17px; line-height: 1.7; }
.option-list { display: grid; gap: 13px; margin-top: 20px; }.option-editor, .preview-option { display: flex; align-items: center; gap: 16px; min-height: 64px; padding: 10px 14px; border: 1.5px solid #211d22; border-radius: 12px; background: white; }.option-editor input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent; font: inherit; }.option-editor button { width: 34px; border: 0; background: transparent; color: #736d75; font-size: 20px; cursor: pointer; }.letter { flex: none; display: grid; place-items: center; width: 39px; height: 39px; border: 1.5px solid #211d22; border-radius: 50%; font-size: 21px; font-weight: 800; }.letter.yellow { background: #ffda65; }.letter.mint { background: #a9e3c8; }.letter.purple { background: #d0adff; }.letter.coral { background: #ffa39e; }.add-option { width: 100%; margin-top: 15px; padding: 15px; border: 1.5px dashed #8d858e; border-radius: 12px; background: transparent; font: inherit; font-weight: 700; cursor: pointer; }
.preview-card { background: linear-gradient(145deg, #f1e6ff, #d7c1ff); transition: transform .2s, box-shadow .2s; }.preview-card.highlighted { transform: translateY(-4px); box-shadow: 7px 9px 0 #211d22; }.preview-card h2 { margin: 0 0 13px; font: 700 24px "STKaiti", "KaiTi", serif; }.preview-card h2 span { color: #f3c640; }.phone { position: relative; min-height: 600px; overflow: hidden; border: 2px solid #211d22; border-radius: 35px; background-color: #fffdf9; background-image: linear-gradient(#efe7de 1px, transparent 1px), linear-gradient(90deg, #efe7de 1px, transparent 1px); background-size: 26px 26px; }.phone-status, .mini-head { display: flex; justify-content: space-between; gap: 10px; padding: 11px 22px; font-size: 12px; }.mini-head { align-items: center; padding-top: 4px; font-size: 13px; }.mini-head span:last-child { padding: 4px 10px; border: 1px solid #211d22; border-radius: 999px; }.mini-body { padding: 28px 15px 48px; }.mini-body h3 { position: relative; margin: 0 10px 25px; font: 700 23px/1.65 "STKaiti", "KaiTi", serif; }.mini-body h3 i { color: #b276ed; font-size: 34px; font-style: normal; }.preview-option { min-height: 59px; margin-bottom: 13px; padding: 9px 11px; }.preview-option p { margin: 0; font-size: 14px; line-height: 1.5; }.preview-option .letter { width: 35px; height: 35px; font-size: 18px; }.home-bar { position: absolute; left: 50%; bottom: 13px; width: 115px; height: 5px; border-radius: 999px; background: #211d22; transform: translateX(-50%); }
.toast { position: fixed; z-index: 60; right: 28px; bottom: 28px; padding: 13px 18px; border: 1.5px solid #211d22; border-radius: 12px; background: #211d22; color: white; box-shadow: 4px 5px 0 #d9bdff; }
@media (max-width: 1100px) { .question-layout { grid-template-columns: 1fr; }.preview-card { max-width: 480px; }.phone { min-height: 560px; } }
@media (max-width: 760px) { .editor-header { align-items: stretch; flex-direction: column; }.header-actions { display: grid; grid-template-columns: 1fr 1fr; }.header-actions .primary-action { grid-column: 1 / -1; }.steps { overflow-x: auto; grid-template-columns: repeat(5, 115px); padding-bottom: 8px; }.question-card, .preview-card { padding: 17px; }.preview-card { max-width: 100%; }.phone { min-height: 540px; }.demo-notice { line-height: 1.6; } }
</style>
