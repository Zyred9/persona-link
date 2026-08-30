const DEMO_SESSION_KEY = 'persona-link-admin-demo-session'

export function hasDemoSession(): boolean {
  return sessionStorage.getItem(DEMO_SESSION_KEY) === 'active'
}

export function createDemoSession(): void {
  sessionStorage.setItem(DEMO_SESSION_KEY, 'active')
}

export function clearDemoSession(): void {
  sessionStorage.removeItem(DEMO_SESSION_KEY)
}
