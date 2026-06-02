import { createRouter, createWebHistory } from 'vue-router'
import { authState, clearAdminAuth, getAccessToken, isAdminRole } from '@/stores/adminAuth.js'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: {
      title: '登录',
      public: true
    }
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    meta: {
      requiresAuth: true
    },
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: {
          title: '首页',
          subtitle: '查看系统概览、资源统计和当前管理工作台状态',
          menuKey: 'dashboard',
          requiresAuth: true
        }
      },
      {
        path: 'ppt',
        redirect: '/ppt/templates',
        meta: {
          requiresAuth: true,
          menuKey: 'ppt'
        }
      },
      {
        path: 'ppt/templates',
        name: 'ppt-templates',
        component: () => import('@/views/ppt/PptTemplateManageView.vue'),
        meta: {
          title: '模板管理',
          subtitle: '维护 PPT 模板、引擎键、预览素材和启用状态',
          menuKey: 'ppt',
          requiresAuth: true
        }
      },
      {
        path: 'ppt/categories',
        name: 'ppt-categories',
        component: () => import('@/views/ppt/PptCategoryManageView.vue'),
        meta: {
          title: '分类管理',
          subtitle: '维护 PPT 场景和风格的分类体系',
          menuKey: 'ppt',
          requiresAuth: true
        }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = getAccessToken()
  const redirect = Array.isArray(to.query.redirect) ? to.query.redirect[0] : to.query.redirect

  if (to.meta.requiresAuth && !token) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath
      }
    }
  }

  if (to.name === 'login' && token) {
    return redirect || '/dashboard'
  }

  if (to.meta.requiresAuth && token && authState.user?.role && !isAdminRole(authState.user.role)) {
    clearAdminAuth()
    return {
      name: 'login',
      query: {
        redirect: to.fullPath,
        error: '当前账号没有管理端访问权限'
      }
    }
  }

  return true
})

router.afterEach((to) => {
  const title = to.meta?.title ? `${to.meta.title} - EduSpark Admin` : 'EduSpark Admin'
  document.title = title
})

export default router
