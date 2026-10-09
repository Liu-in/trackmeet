import type { RouteRecordRaw } from 'vue-router'
import type { MenuNode } from '@/api/auth'

/**
 * 视图组件动态加载：
 * 后端菜单的 component 形如 athlete/AthleteList，映射到 src/views/athlete/AthleteList.vue；
 * 未实现的页面回退到占位组件，保证菜单不出现空白页。
 */
const viewModules = import.meta.glob('../views/**/*.vue')

const Placeholder = () => import('@/views/common/Placeholder.vue')

export function loadView(component?: string) {
  if (!component) {
    return Placeholder
  }
  const key = `../views/${component}.vue`
  const loader = viewModules[key]
  return loader ? (loader as () => Promise<unknown>) : Placeholder
}

/**
 * 菜单树 → 路由（仅取目录 D 与菜单 M；按钮 B 用于按钮权限）。
 */
export function buildRoutesFromMenus(menus: MenuNode[] | undefined | null): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = []
  const walk = (nodes: MenuNode[] | undefined | null) => {
    if (!nodes || nodes.length === 0) {
      return
    }
    for (const node of nodes) {
      if (node.menuType === 'M' && node.path) {
        routes.push({
          path: node.path,
          name: node.path,
          component: loadView(node.component),
          meta: {
            title: node.menuName,
            icon: node.icon,
            permCode: node.permCode,
            keepAlive: node.keepAlive === 1
          }
        })
      }
      if (node.children && node.children.length > 0) {
        walk(node.children)
      }
    }
  }
  walk(menus)
  return routes
}

/**
 * 是否拥有按钮权限（SUPER_ADMIN 始终放行）。
 */
export function hasPermission(permissions: string[], roles: string[], permCode?: string): boolean {
  if (!permCode) {
    return true
  }
  if (roles.includes('SUPER_ADMIN')) {
    return true
  }
  return permissions.includes(permCode)
}
