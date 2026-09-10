import { createRouter, createWebHistory } from 'vue-router'
import { hasSession, isAdmin } from './auth'
import AdminLayout from './components/AdminLayout.vue'
import LoginView from './views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    {
      path: '/',
      component: AdminLayout,
      meta: { requiresAuth: true },
      children: [
        { path: 'app-configs', name: 'app-configs', component: () => import('./views/AppConfigView.vue'), meta: { adminOnly: true } },
        { path: '', name: 'dashboard', component: () => import('./views/DashboardView.vue') },
        { path: 'home-config', name: 'home-config', component: () => import('./views/HomeConfigView.vue') },
        { path: 'ad-config', name: 'ad-config', component: () => import('./views/AdConfigView.vue'), meta: { adminOnly: true } },
        { path: 'feedbacks', name: 'feedbacks', component: () => import('./views/FeedbackView.vue'), meta: { adminOnly: true } },
        { path: 'legal-documents', name: 'legal-documents', component: () => import('./views/LegalDocumentsView.vue'), meta: { adminOnly: true } },
        { path: 'types', name: 'types', component: () => import('./views/TypeManagementView.vue') },
        { path: 'types/:id/questions', name: 'question-editor', component: () => import('./views/QuestionEditorView.vue') },
        { path: 'types/:id/results', name: 'result-rules', component: () => import('./views/ResultRulesView.vue') },
        { path: 'ai-question-bank', name: 'ai-question-bank', component: () => import('./views/AiQuestionBankView.vue') },
        { path: 'categories', name: 'categories', component: () => import('./views/CategoriesView.vue') },
        { path: 'materials', name: 'materials', component: () => import('./views/MaterialLibraryView.vue') },
        { path: 'releases', name: 'releases', component: () => import('./views/ReleasesView.vue') },
        { path: 'analytics', name: 'analytics', component: () => import('./views/AnalyticsView.vue') },
        { path: 'audits', name: 'audits', component: () => import('./views/AuditLogView.vue') },
        { path: 'settings', name: 'settings', component: () => import('./views/SettingsView.vue'), meta: { adminOnly: true } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !hasSession()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && hasSession()) {
    return { name: 'dashboard' }
  }
  if (to.meta.adminOnly && !isAdmin()) return { name: 'dashboard' }
  return true
})

export default router
