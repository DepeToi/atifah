import { createRouter, createWebHistory } from 'vue-router'

import AdminLayout from '@/layouts/AdminLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: AdminLayout,
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '仪表盘', subtitle: 'DAU、打卡量、情绪分布与社区活跃度总览' },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UsersView.vue'),
          meta: { title: '用户管理', subtitle: '用户查询、状态管理与隐私数据脱敏展示' },
        },
        {
          path: 'content-review',
          name: 'content-review',
          component: () => import('@/views/ContentReviewView.vue'),
          meta: { title: '内容审核', subtitle: '机审队列、人审工作台与举报处置' },
        },
        {
          path: 'operations',
          name: 'operations',
          component: () => import('@/views/OperationsView.vue'),
          meta: { title: '运营配置', subtitle: '推荐位、活动、推送与敏感词管理' },
        },
        {
          path: 'system',
          name: 'system',
          component: () => import('@/views/SystemView.vue'),
          meta: { title: '系统管理', subtitle: 'RBAC、菜单、操作审计与告警看板' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '页面不存在' },
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : ''
  document.title = title ? `${title} · atiFah 管理后台` : 'atiFah 管理后台'
})

export default router
