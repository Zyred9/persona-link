import { reactive } from 'vue'

interface ConfirmOptions {
  title?: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
}

export const confirmState = reactive({
  open: false,
  message: '',
  title: '操作确认',
  confirmText: '确认',
  cancelText: '取消',
  danger: false,
})

let resolvePending: ((confirmed: boolean) => void) | undefined

export function confirmAction(message: string, options: ConfirmOptions = {}): Promise<boolean> {
  // 同时触发的操作不覆盖当前确认，也不排队执行过期操作。
  if (resolvePending) return Promise.resolve(false)
  return new Promise(resolve => {
    resolvePending = resolve
    Object.assign(confirmState, {
      message,
      title: options.title ?? '操作确认',
      confirmText: options.confirmText ?? '确认',
      cancelText: options.cancelText ?? '取消',
      danger: options.danger ?? false,
      open: true,
    })
  })
}

export function settleConfirm(confirmed: boolean): void {
  const resolve = resolvePending
  resolvePending = undefined
  confirmState.open = false
  resolve?.(confirmed)
}
