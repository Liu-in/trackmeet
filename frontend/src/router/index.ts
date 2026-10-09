import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

/**
 * 静态路由：登录页、错误页与主框架（子路由由权限菜单动态注入）。
 */
export const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { title: '页面不存在', public: true }
  },
  {
    // 大屏公告（匿名）：会议室大屏用 ?meetId=&token= 直接打开，不套主框架、不需要登录。
    // 应用内查看走权限菜单登记的 /screen/board（需 screen:board 权限）。
    path: '/screen/public-board',
    name: 'ScreenPublicBoard',
    component: () => import('@/views/screen/ScreenBoard.vue'),
    meta: { title: '大屏公告', public: true }
  },
  {
    path: '/',
    name: 'MainLayout',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: []
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'CatchAll',
    redirect: '/404',
    meta: { public: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes,
  scrollBehavior: () => ({ top: 0 })
})

export default router
