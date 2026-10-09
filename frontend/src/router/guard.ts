import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import router from './index'

const WHITE_LIST = ['/login', '/404']

/**
 * 路由守卫：
 * 1) 白名单直接放行；
 * 2) 未登录跳登录页；
 * 3) 已登录但未加载用户信息 → 拉取 profile；
 * 4) 菜单路由未注册 → 依据权限菜单注入后重新导航。
 */
router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()
  const permissionStore = usePermissionStore()

  if (WHITE_LIST.includes(to.path) || to.meta?.public) {
    next()
    return
  }

  if (!userStore.token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  if (!userStore.profileLoaded) {
    try {
      await userStore.loadProfile()
    } catch {
      userStore.reset()
      permissionStore.reset()
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
  }

  if (!permissionStore.registered) {
    permissionStore.buildRoutes(userStore.menus)
    permissionStore.registerRoutes(router)
    next({ ...to, replace: true })
    return
  }

  next()
})

router.afterEach((to) => {
  const title = (to.meta?.title as string) || ''
  document.title = title ? `${title} - 田径运动会编排系统` : '田径运动会编排系统'
})
