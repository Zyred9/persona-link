<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { confirmState, settleConfirm } from '../composables/useConfirm'

const dialog = ref<HTMLDialogElement>()
const cancelButton = ref<HTMLButtonElement>()
let previousFocus: HTMLElement | null = null
const backdropPointerDown = ref(false)

watch(() => confirmState.open, open => {
  if (open && dialog.value) {
    previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    dialog.value.showModal()
    cancelButton.value?.focus()
  } else {
    dialog.value?.close()
    if (previousFocus?.isConnected) previousFocus.focus()
    previousFocus = null
  }
}, { flush: 'post', immediate: true })

function finish(confirmed: boolean) {
  // 先关闭并恢复焦点，再让调用方继续（调用方可能立即打开下一个弹窗）。
  dialog.value?.close()
  if (previousFocus?.isConnected) previousFocus.focus()
  previousFocus = null
  settleConfirm(confirmed)
}

function onBackdropClick(event: MouseEvent) {
  if (backdropPointerDown.value && isBackdrop(event)) finish(false)
  backdropPointerDown.value = false
}

function isBackdrop(event: MouseEvent) {
  const bounds = dialog.value?.getBoundingClientRect()
  return event.target === dialog.value && !!bounds
    && (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom)
}

onBeforeUnmount(() => finish(false))
</script>

<template>
  <Teleport to="body">
    <dialog
      ref="dialog"
      class="confirm-dialog"
      :class="{ 'is-danger': confirmState.danger }"
      aria-labelledby="confirm-dialog-title"
      aria-describedby="confirm-dialog-message"
      @cancel.prevent.stop="finish(false)"
      @keydown.stop
      @keyup.stop
      @pointerdown="backdropPointerDown = isBackdrop($event)"
      @click="onBackdropClick"
    >
      <section class="confirm-card">
        <header class="confirm-heading">
          <span class="confirm-symbol" aria-hidden="true">{{ confirmState.danger ? '!' : '?' }}</span>
          <h2 id="confirm-dialog-title">{{ confirmState.title }}</h2>
        </header>
        <p id="confirm-dialog-message">{{ confirmState.message }}</p>
        <footer class="confirm-actions">
          <button ref="cancelButton" type="button" class="confirm-cancel" autofocus @click="finish(false)">{{ confirmState.cancelText }}</button>
          <button type="button" class="confirm-accept" @click="finish(true)">{{ confirmState.confirmText }}</button>
        </footer>
      </section>
    </dialog>
  </Teleport>
</template>

<style scoped>
.confirm-dialog {
  width: min(460px, calc(100vw - 32px));
  max-height: calc(100dvh - 32px);
  margin: auto;
  padding: 0;
  overflow: auto;
  overscroll-behavior: contain;
  border: 1px solid #d8cce2;
  border-radius: 22px;
  background: var(--paper);
  color: var(--ink);
  box-shadow: 0 24px 80px rgb(33 29 34 / 24%);
}
.confirm-dialog[open] { animation: confirm-enter 140ms ease-out; }
.confirm-dialog::backdrop { background: rgb(33 29 34 / 42%); backdrop-filter: blur(3px); }
.confirm-card { padding: 28px; }
.confirm-heading { display: flex; align-items: center; gap: 14px; }
.confirm-symbol { display: grid; place-items: center; flex: none; width: 44px; height: 44px; border-radius: 15px; background: var(--purple-soft); color: #694293; font-size: 25px; font-weight: 700; }
.confirm-heading h2 { margin: 0; font-size: 20px; line-height: 1.5; overflow-wrap: anywhere; }
#confirm-dialog-message { margin: 20px 0 28px; color: #675d6b; font-size: 14px; line-height: 1.85; white-space: pre-wrap; overflow-wrap: anywhere; }
.confirm-actions { display: flex; justify-content: flex-end; gap: 12px; }
.confirm-actions button { min-width: 100px; min-height: 42px; padding: 10px 18px; border: 1px solid transparent; border-radius: 11px; font-size: 14px; font-weight: 700; line-height: 1.5; overflow-wrap: anywhere; }
.confirm-actions .confirm-cancel { border-color: #ddd5e0; background: white; }
.confirm-accept { background: var(--purple); }
.confirm-actions button:hover { filter: brightness(.97); }
.confirm-actions button:focus-visible { outline: 3px solid #9568c8; outline-offset: 3px; }
.is-danger .confirm-symbol { background: var(--coral-soft); color: #b33b2d; }
.is-danger .confirm-accept { background: var(--coral); }
@keyframes confirm-enter { from { opacity: 0; transform: translateY(8px) scale(.98); } to { opacity: 1; transform: none; } }
@media (max-width: 480px) {
  .confirm-card { padding: 24px 20px; }
  .confirm-actions button { flex: 1; min-width: 0; }
}
@media (prefers-reduced-motion: reduce) { .confirm-dialog[open] { animation: none; } }
</style>
