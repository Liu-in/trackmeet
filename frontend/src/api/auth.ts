import { get, post } from './request'

/** 菜单节点（后端 /api/auth/profile 返回） */
export interface MenuNode {
  id: number
  parentId: number
  menuName: string
  menuType: 'D' | 'M' | 'B'
  path?: string
  component?: string
  icon?: string
  permCode?: string
  visible?: number
  keepAlive?: number
  sortNo?: number
  children?: MenuNode[]
}

/** 当前用户信息 */
export interface UserInfo {
  id: number
  username: string
  realName?: string
  phone?: string
  email?: string
  deptId?: number
  unitId?: number
  status?: string
  forcePwdChange?: number
  roles: string[]
  permissions: string[]
  menus: MenuNode[]
}

export interface LoginParams {
  username: string
  password: string
  captchaKey?: string
  captchaCode?: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  userInfo: UserInfo
}

export interface CaptchaResult {
  captchaKey: string
  imageBase64: string
  expireSeconds: number
}

export function login(data: LoginParams) {
  return post<LoginResult>('/auth/login', data)
}

export function getProfile() {
  return get<UserInfo>('/auth/profile')
}

export function logout() {
  return post<void>('/auth/logout')
}

export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return post<void>('/auth/change-password', data)
}

export function getCaptcha() {
  return get<CaptchaResult>('/auth/captcha')
}
