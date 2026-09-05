import { clearSession, getToken, isReadOnly, saveSession, type AdminProfile } from './auth'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResponse<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface AdminLoginResponse extends AdminProfile {
  token: string
  expiresAt: string
}

export interface AnalyticsMetrics {
  uv: number
  pv: number
  startedCount: number
  completedCount: number
  shareCount: number
  reportCount: number
}

export interface AnalyticsOverview {
  days: number
  updatedAt: string
  metrics: AnalyticsMetrics
  trend: Array<{ statDate: string; uv: number; pv: number; startedCount: number; completedCount: number }>
  topTests: Array<{ testId: string; testName: string; startedCount: number; completedCount: number; completionRate: number }>
  officialTrend: Array<{ statDate: string; sessionCount: number; visitPv: number; visitUv: number; visitUvNew: number; stayTimeUv: number; stayTimeSession: number; visitDepth: number; syncedAt: string }>
}

export interface Category {
  id: number
  categoryName: string
  testCount: number
  status: number
  sortNo: number
  updateDate: string
}

export interface TestItem {
  id: number
  testName: string
  testType: number
  categoryId: number
  categoryName: string
  iconUrl?: string
  coverUrl?: string
  status: number
  currentVersionNo?: number
  currentVersionStatus?: number
  homeDisplay?: number
  homeSort?: number
  updateDate?: string
}

export interface MiniappHomeTest {
  testId: string
  title: string
  coverUrl: string
}

export interface MiniappHome {
  categories: Array<{ categoryId: string; categoryName: string }>
  focusTests: MiniappHomeTest[]
  recommendedTests: MiniappHomeTest[]
  allTests: MiniappHomeTest[]
}

export interface ScoreDimension {
  id: number
  versionId: number
  dimensionCode?: string
  dimensionName: string
  sortNo: number
}

export interface TestVersion {
  id: number
  testId: number
  versionNo: number
  title: string
  coverUrl: string
  description?: string
  estimatedMinutes: number
  drawQuestionCount: number
  versionStatus: number
  versionNote?: string
  scheduledAt?: string
  publishedAt?: string
  offlineAt?: string
  questionCount: number
  dimensions: ScoreDimension[]
}

export interface QuestionOption {
  id?: number
  optionCode?: string
  optionText: string
  dimensionId?: number
  scoreValue: number
  sortNo?: number
}

export interface Question {
  id: number
  versionId: number
  questionType: number
  dimensionId?: number
  minSelectCount: number
  maxSelectCount: number
  questionNo: number
  questionText: string
  requiredFlag: number
  sortNo?: number
  options: QuestionOption[]
}

export interface ResultTemplate {
  id?: number
  dimensionId?: number
  resultCode?: string
  resultName: string
  scoreMin: number
  scoreMax: number
  basicResultJson: unknown
  deepResultJson?: unknown
  shareCopyJson?: unknown
  sortNo?: number
}

export interface ResultConfig {
  versionId: number
  dimensions: ScoreDimension[]
  templates: ResultTemplate[]
}

export interface AiGenerationTask {
  id: number
  taskNo: string
  testId: number
  versionId: number
  testName: string
  testType: number
  categoryId: number
  coverUrl: string
  description?: string
  estimatedMinutes: number
  drawQuestionCount: number
  modelName: string
  targetQuestionCount: number
  generatedQuestionCount: number
  currentBatchNo: number
  totalBatchCount: number
  completedBatchCount: number
  retryCount: number
  taskStatus: number
  errorMessage?: string
  completedAt?: string
  submittedAt?: string
}

export interface AiGenerationTaskSummary {
  id: number
  taskNo: string
  testName: string
  targetQuestionCount: number
  generatedQuestionCount: number
  taskStatus: number
  errorMessage?: string
  operatorId: number
  createDate: string
  completedAt?: string
  submittedAt?: string
}

export interface AiGenerationCreatePayload {
  requestId: string
  testName: string
  testType: number
  categoryId: number
  coverUrl: string
  description?: string
  estimatedMinutes: number
  promptText: string
  targetQuestionCount: number
  drawQuestionCount: number
}

export interface AuditLog {
  id: number
  bizType: number
  bizId: number
  actionType: number
  beforeSnapshot?: string
  afterSnapshot?: string
  reason?: string
  operatorId: number
  createDate: string
}

export interface AdminAccount {
  id: number
  username: string
  displayName: string
  roleType: number
  status: number
  lastLoginAt?: string
  createDate: string
}

export interface AssetItem {
  fileName: string
  url: string
  imageType: string
  size: number
  updatedAt: string
  referenceCount: number
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export function assetUrl(url: string): string {
  if (!url || /^https?:\/\//.test(url)) return url
  return `${API_BASE_URL}${url.startsWith('/') ? '' : '/'}${url}`
}

function queryString(params: Record<string, unknown>): string {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value))
  })
  const value = query.toString()
  return value ? `?${value}` : ''
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  if (isReadOnly() && method !== 'GET' && method !== 'HEAD' && path !== '/api/admin/auth/logout') {
    throw new Error('当前账号为只读角色，无权修改数据')
  }
  const headers = new Headers(init.headers)
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers })
  const body = await response.json().catch(() => null) as ApiResponse<T> | null
  if (response.status === 401) {
    clearSession()
    if (!location.pathname.startsWith('/login')) location.assign(`/login?redirect=${encodeURIComponent(location.pathname)}`)
  }
  if (!response.ok || !body || body.code !== 0) throw new Error(body?.message || `服务端响应异常：${response.status}`)
  return body.data
}

export function getHealth(): Promise<string> { return request('/api/health') }

export async function login(username: string, password: string): Promise<AdminLoginResponse> {
  const session = await request<AdminLoginResponse>('/api/admin/auth/login', {
    method: 'POST', body: JSON.stringify({ username, password }),
  })
  saveSession(session.token, session)
  return session
}

export function getMe(): Promise<AdminProfile> { return request('/api/admin/auth/me') }

export async function logout(): Promise<void> {
  try { await request('/api/admin/auth/logout', { method: 'POST' }) } finally { clearSession() }
}

export function getDashboard(days = 7): Promise<AnalyticsOverview> { return request(`/api/admin/dashboard${queryString({ days })}`) }
export function getAnalytics(days = 7): Promise<AnalyticsOverview> { return request(`/api/admin/analytics/overview${queryString({ days })}`) }
export function getCategories(params: Record<string, unknown> = {}): Promise<PageResponse<Category>> { return request(`/api/admin/categories${queryString({ page: 1, size: 100, ...params })}`) }
export function createCategory(categoryName: string, status = 1): Promise<Category> { return request('/api/admin/categories', { method: 'POST', body: JSON.stringify({ categoryName, status }) }) }
export function updateCategory(category: Category): Promise<Category> { return request(`/api/admin/categories/${category.id}`, { method: 'PUT', body: JSON.stringify({ categoryName: category.categoryName, status: category.status }) }) }
export function updateCategoryOrder(orderedIds: number[]): Promise<void> { return request('/api/admin/categories/order', { method: 'PUT', body: JSON.stringify({ orderedIds }) }) }
export function deleteCategories(ids: number[]): Promise<void> { return request('/api/admin/categories/deletes', { method: 'POST', body: JSON.stringify(ids) }) }
export function getTests(params: Record<string, unknown> = {}): Promise<PageResponse<TestItem>> { return request(`/api/admin/tests${queryString({ page: 1, size: 100, ...params })}`) }
export function getTest(id: number): Promise<TestItem> { return request(`/api/admin/tests/${id}`) }
export function saveTest(item: Pick<TestItem, 'testName' | 'testType' | 'categoryId' | 'iconUrl' | 'status'>, id?: number): Promise<TestItem> { return request(id ? `/api/admin/tests/${id}` : '/api/admin/tests', { method: id ? 'PUT' : 'POST', body: JSON.stringify(item) }) }
export function updateTestStatus(id: number, status: number): Promise<TestItem> { return request(`/api/admin/tests/${id}/status`, { method: 'PUT', body: JSON.stringify({ status }) }) }
export function updateTestHomeDisplay(id: number, homeDisplay: number, homeSort: number): Promise<TestItem> { return request(`/api/admin/tests/${id}/home-display`, { method: 'PUT', body: JSON.stringify({ homeDisplay, homeSort }) }) }
export function getMiniappHome(): Promise<MiniappHome> { return request('/api/miniapp/home') }
export function deleteTests(ids: number[]): Promise<void> { return request('/api/admin/tests/deletes', { method: 'POST', body: JSON.stringify(ids) }) }
export function getTestVersions(testId: number): Promise<TestVersion[]> { return request(`/api/admin/tests/${testId}/versions`) }
export function getVersion(versionId: number): Promise<TestVersion> { return request(`/api/admin/test-versions/${versionId}`) }
export function saveVersion(testId: number, payload: unknown, versionId?: number): Promise<TestVersion> { return request(versionId ? `/api/admin/test-versions/${versionId}` : `/api/admin/tests/${testId}/versions`, { method: versionId ? 'PUT' : 'POST', body: JSON.stringify(payload) }) }
export function copyVersionAsDraft(versionId: number): Promise<TestVersion> { return request(`/api/admin/test-versions/${versionId}/draft-copy`, { method: 'POST' }) }
export function getQuestions(versionId: number): Promise<Question[]> { return request(`/api/admin/test-versions/${versionId}/questions`) }
export function saveQuestion(versionId: number, payload: unknown, questionId?: number): Promise<Question> { return request(questionId ? `/api/admin/questions/${questionId}` : `/api/admin/test-versions/${versionId}/questions`, { method: questionId ? 'PUT' : 'POST', body: JSON.stringify(payload) }) }
export function deleteQuestion(questionId: number): Promise<void> { return request(`/api/admin/questions/${questionId}`, { method: 'DELETE' }) }
export function getResultConfig(versionId: number): Promise<ResultConfig> { return request(`/api/admin/test-versions/${versionId}/results`) }
export function saveResultConfig(versionId: number, templates: ResultTemplate[]): Promise<ResultConfig> { return request(`/api/admin/test-versions/${versionId}/results`, { method: 'PUT', body: JSON.stringify({ templates }) }) }
export function checkPublish(versionId: number): Promise<{ passed: boolean; errors: string[] }> { return request(`/api/admin/test-versions/${versionId}/publish-check`) }
export function createAiGenerationTask(payload: AiGenerationCreatePayload): Promise<AiGenerationTask> { return request('/api/admin/ai-question-bank/tasks', { method: 'POST', body: JSON.stringify(payload) }) }
export function getAiGenerationTasks(params: Record<string, unknown> = {}): Promise<PageResponse<AiGenerationTaskSummary>> { return request(`/api/admin/ai-question-bank/tasks${queryString({ page: 1, size: 10, ...params })}`) }
export function getAiGenerationTask(taskId: number): Promise<AiGenerationTask> { return request(`/api/admin/ai-question-bank/tasks/${taskId}`) }
export function retryAiGenerationTask(taskId: number): Promise<AiGenerationTask> { return request(`/api/admin/ai-question-bank/tasks/${taskId}/retry`, { method: 'POST' }) }
export function submitAiGenerationTask(taskId: number): Promise<AiGenerationTask> { return request(`/api/admin/ai-question-bank/tasks/${taskId}/submit`, { method: 'POST' }) }
export function publishVersion(versionId: number): Promise<TestVersion> { return request(`/api/admin/test-versions/${versionId}/publish`, { method: 'POST' }) }
export function scheduleVersion(versionId: number, scheduledAt: string): Promise<TestVersion> { return request(`/api/admin/test-versions/${versionId}/schedule`, { method: 'POST', body: JSON.stringify({ scheduledAt }) }) }
export function cancelVersionSchedule(versionId: number): Promise<TestVersion> { return request(`/api/admin/test-versions/${versionId}/schedule/cancel`, { method: 'POST' }) }
export function offlineVersion(versionId: number, reason = ''): Promise<TestVersion> { return request(`/api/admin/test-versions/${versionId}/offline${queryString({ reason })}`, { method: 'POST' }) }
export function archiveVersion(versionId: number): Promise<TestVersion> { return request(`/api/admin/test-versions/${versionId}/archive`, { method: 'POST' }) }
export function getContentAudits(params: Record<string, unknown> = {}): Promise<PageResponse<AuditLog>> { return request(`/api/admin/content-audits${queryString({ page: 1, size: 20, ...params })}`) }
export function getAdminAccounts(params: Record<string, unknown> = {}): Promise<PageResponse<AdminAccount>> { return request(`/api/admin/accounts${queryString({ page: 1, size: 20, ...params })}`) }
export function createAdminAccount(payload: { username: string; password: string; displayName: string; roleType: number; status: number }): Promise<AdminAccount> { return request('/api/admin/accounts', { method: 'POST', body: JSON.stringify(payload) }) }
export function updateAdminAccount(id: number, payload: { displayName: string; roleType: number; status: number }): Promise<AdminAccount> { return request(`/api/admin/accounts/${id}`, { method: 'PUT', body: JSON.stringify(payload) }) }
export function resetAdminPassword(id: number, password: string): Promise<void> { return request(`/api/admin/accounts/${id}/password/reset`, { method: 'POST', body: JSON.stringify({ password }) }) }

export async function uploadImage(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  return (await request<{ url: string }>('/api/admin/assets/images', { method: 'POST', body: form })).url
}
export function getAssets(keyword = ''): Promise<AssetItem[]> { return request(`/api/admin/assets/images${queryString({ keyword })}`) }
export function deleteAsset(fileName: string): Promise<void> { return request(`/api/admin/assets/images/${encodeURIComponent(fileName)}`, { method: 'DELETE' }) }
