import { defineStore } from 'pinia'
import type { Router, RouteRecordRaw } from 'vue-router'
import type { MenuNode } from '@/api/auth'
import { buildRoutesFromMenus } from '@/router/helper'

interface PermissionState {
  routes: RouteRecordRaw[]
  registered: boolean
  menus: MenuNode[]
}

export const usePermissionStore = defineStore('permission', {
  state: (): PermissionState => ({
    routes: [],
    registered: false,
    menus: []
  }),

  actions: {
    buildRoutes(menus: MenuNode[]) {
      this.menus = menus || []
      this.routes = buildRoutesFromMenus(this.menus)
      this.registered = false
    },

    registerRoutes(router: Router) {
      if (this.registered) {
        return
      }
      this.routes.forEach((route) => {
        router.addRoute('MainLayout', route)
      })
      this.registered = true
    },

    reset() {
      this.routes = []
      this.menus = []
      this.registered = false
    }
  }
})
