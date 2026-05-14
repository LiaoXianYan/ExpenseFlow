import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'Login', component: () => import('../views/login/LoginView.vue'), meta: { noAuth: true } },
  {
    path: '/', component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/dashboard/DashboardView.vue') },
      { path: 'travel', name: 'TravelList', component: () => import('../views/travel/TravelListView.vue') },
      { path: 'travel/create', name: 'TravelCreate', component: () => import('../views/travel/TravelFormView.vue') },
      { path: 'travel/:id/edit', name: 'TravelEdit', component: () => import('../views/travel/TravelFormView.vue') },
      { path: 'report', name: 'ReportList', component: () => import('../views/report/ReportListView.vue') },
      { path: 'report/create', name: 'ReportCreate', component: () => import('../views/report/ReportFormView.vue') },
      { path: 'report/:id/edit', name: 'ReportEdit', component: () => import('../views/report/ReportFormView.vue') },
      { path: 'invoice', name: 'InvoiceUpload', component: () => import('../views/invoice/InvoiceUploadView.vue') },
      { path: 'approval', name: 'ApprovalWorkbench', component: () => import('../views/approval/ApprovalWorkbench.vue') },
      { path: 'ai-review', name: 'AIReview', component: () => import('../views/ai/AIReviewView.vue') },
      { path: 'ai-assistant', name: 'AIAssistant', component: () => import('../views/ai/AIAssistantView.vue') },
      { path: 'notification', name: 'NotificationCenter', component: () => import('../views/notification/NotificationCenter.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (!to.meta.noAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
