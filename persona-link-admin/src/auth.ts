const TOKEN_KEY = 'persona-link-admin-token'
const PROFILE_KEY = 'persona-link-admin-profile'

export interface AdminProfile {
  adminId: number
  displayName: string
  roleType: number
}

export function getToken(): string {
  return sessionStorage.getItem(TOKEN_KEY) ?? ''
}

export function getProfile(): AdminProfile | null {
  const value = sessionStorage.getItem(PROFILE_KEY)
  if (!value) return null
  try {
    return JSON.parse(value) as AdminProfile
  } catch {
    clearSession()
    return null
  }
}

export function saveSession(token: string, profile: AdminProfile): void {
  sessionStorage.setItem(TOKEN_KEY, token)
  saveProfile(profile)
}

export function saveProfile(profile: AdminProfile): void {
  sessionStorage.setItem(PROFILE_KEY, JSON.stringify(profile))
}

export function hasSession(): boolean {
  return Boolean(getToken())
}

export function isAdmin(): boolean {
  return getProfile()?.roleType === 1
}

export function isReadOnly(): boolean {
  return getProfile()?.roleType === 3
}

export function clearSession(): void {
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(PROFILE_KEY)
}
