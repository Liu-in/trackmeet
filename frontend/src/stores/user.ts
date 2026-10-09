import { defineStore } from 'pinia'
import { getProfile, login as loginApi, logout as logoutApi, type LoginParams, type MenuNode, type UserInfo } from '@/api/auth'
import { MEET_ID_KEY, REFRESH_TOKEN_KEY, TOKEN_KEY } from '@/api/request'

interface UserState {
  token: string
  refreshToken: string
  userId?: number
  username: string
  realName: string
  unitId?: number
  roles: string[]
  permissions: string[]
  menus: MenuNode[]
  profileLoaded: boolean
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    refreshToken: localStorage.getItem(REFRESH_TOKEN_KEY) || '',
    username: '',
    realName: '',
    roles: [],
    permissions: [],
    menus: [],
    profileLoaded: false
  }),

  getters: {
    isSuperAdmin: (state) => state.roles.includes('SUPER_ADMIN'),
    displayName: (state) => state.realName || state.username
  },

  actions: {
    async login(params: LoginParams) {
      const result = await loginApi(params)
      this.token = result.accessToken
      this.refreshToken = result.refreshToken
      localStorage.setItem(TOKEN_KEY, result.accessToken)
      localStorage.setItem(REFRESH_TOKEN_KEY, result.refreshToken)
      this.applyUserInfo(result.userInfo)
      return result
    },

    async loadProfile() {
      const info = await getProfile()
      this.applyUserInfo(info)
      return info
    },

    applyUserInfo(info: UserInfo) {
      this.userId = info.id
      this.username = info.username
      this.realName = info.realName || info.username
      this.unitId = info.unitId
      this.roles = info.roles || []
      this.permissions = info.permissions || []
      this.menus = info.menus || []
      this.profileLoaded = true
    },

    async logout() {
      try {
        await logoutApi()
      } catch {
        // 忽略登出接口异常，本地状态照常清理
      }
      this.reset()
    },

    reset() {
      this.token = ''
      this.refreshToken = ''
      this.userId = undefined
      this.username = ''
      this.realName = ''
      this.roles = []
      this.permissions = []
      this.menus = []
      this.profileLoaded = false
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(REFRESH_TOKEN_KEY)
      localStorage.removeItem(MEET_ID_KEY)
    },

    hasPerm(permCode?: string): boolean {
      if (!permCode) {
        return true
      }
      if (this.roles.includes('SUPER_ADMIN')) {
        return true
      }
      return this.permissions.includes(permCode)
    }
  }
})
