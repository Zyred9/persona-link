import { onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { confirmAction } from './useConfirm'

interface UnsavedState {
  dirty: { value: boolean }
  saving?: { value: boolean }
  message: string
  revision: number
  active: boolean
}

const activeStates = new Set<UnsavedState>()

export async function confirmPendingChanges(message?: string): Promise<boolean> {
  if ([...activeStates].some((state) => state.saving?.value)) {
    await confirmAction('内容正在保存，请稍候再离开。', { title: '暂时无法离开', confirmText: '知道了' })
    return false
  }
  const pending = [...activeStates].find((state) => state.dirty.value)
  const snapshot = [...activeStates].map((state) => ({ state, revision: state.revision }))
  const confirmed = !pending || await confirmAction(message ?? pending.message, { title: '放弃未保存的修改？', confirmText: '放弃修改', danger: true })
  return confirmed && snapshot.length === activeStates.size && snapshot.every(({ state, revision }) => state.active && state.revision === revision && !state.saving?.value)
}

export function discardPendingChanges() {
  activeStates.forEach((state) => { state.dirty.value = false })
}

export function useUnsavedChanges(
  message = '当前内容尚未保存，确认放弃修改并离开吗？',
  saving?: { value: boolean },
) {
  const dirty = ref(false)
  const state: UnsavedState = { dirty, saving, message, revision: 0, active: true }

  function markDirty() {
    state.revision += 1
    dirty.value = true
  }

  function markSaved() {
    state.revision += 1
    dirty.value = false
  }

  async function confirmDiscard(confirmMessage = message): Promise<boolean> {
    if (saving?.value) {
      await confirmAction('内容正在保存，请稍候再离开。', { title: '暂时无法离开', confirmText: '知道了' })
      return false
    }
    const revision = state.revision
    const confirmed = !dirty.value || await confirmAction(confirmMessage, { title: '放弃未保存的修改？', confirmText: '放弃修改', danger: true })
    // 等待确认期间若异步加载或保存改变了内容，旧确认不能放弃新的修改。
    return confirmed && state.active && revision === state.revision && !saving?.value
  }

  function handleBeforeUnload(event: BeforeUnloadEvent) {
    if (!dirty.value && !saving?.value) return
    event.preventDefault()
    event.returnValue = ''
  }

  onMounted(() => {
    activeStates.add(state)
    window.addEventListener('beforeunload', handleBeforeUnload)
  })
  onBeforeUnmount(() => {
    state.active = false
    activeStates.delete(state)
    window.removeEventListener('beforeunload', handleBeforeUnload)
  })
  onBeforeRouteLeave(() => confirmDiscard())

  return { dirty, markDirty, markSaved, confirmDiscard }
}
