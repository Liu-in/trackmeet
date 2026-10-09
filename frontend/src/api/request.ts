import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 后端统一返回体：{ code, message, data, traceId, timestamp }
 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
  traceId?: string
  timestamp?: number
}

/**
 * 统一分页结构：{ records, total, page, size, pages }
 */
export interface PageResult<T = unknown> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}

export const TOKEN_KEY = 'trackmeet_token'
export const REFRESH_TOKEN_KEY = 'trackmeet_refresh_token'
export const MEET_ID_KEY = 'trackmeet_meet_id'

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30000
})

/** 请求拦截：注入访问令牌与届次上下文（X-Meet-Id） */
service.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  const meetId = localStorage.getItem(MEET_ID_KEY)
  if (meetId && !config.headers['X-Meet-Id']) {
    config.headers['X-Meet-Id'] = meetId
  }
  return config
})

function redirectToLogin(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`
  }
}

/** 响应拦截：code=0 直接返回 data，其余统一提示 */
service.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response
    }
    const body = response.data as ApiResult
    if (body && typeof body.code === 'number') {
      if (body.code === 0) {
        return body.data as never
      }
      if (body.code === 401) {
        ElMessage.error(body.message || '登录已失效，请重新登录')
        redirectToLogin()
      } else {
        ElMessage.error(body.message || '请求失败')
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return response.data as never
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      ElMessage.error('登录已失效，请重新登录')
      redirectToLogin()
    } else if (status === 403) {
      ElMessage.error('无权限访问该功能')
    } else if (status === 404) {
      ElMessage.error('接口不存在：' + (error?.config?.url || ''))
    } else {
      ElMessage.error(error?.response?.data?.message || error.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export function get<T = unknown>(url: string, params?: object): Promise<T> {
  return service.get(url, { params }) as unknown as Promise<T>
}

export function post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return service.post(url, data, config) as unknown as Promise<T>
}

export function put<T = unknown>(url: string, data?: unknown): Promise<T> {
  return service.put(url, data) as unknown as Promise<T>
}

export function del<T = unknown>(url: string, params?: object): Promise<T> {
  return service.delete(url, { params }) as unknown as Promise<T>
}

/** 上传文件（multipart/form-data） */
export function upload<T = unknown>(url: string, file: File, extra?: Record<string, string | number | boolean>): Promise<T> {
  const formData = new FormData()
  formData.append('file', file)
  if (extra) {
    Object.entries(extra).forEach(([key, value]) => formData.append(key, String(value)))
  }
  return service.post(url, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }) as unknown as Promise<T>
}

/** 下载二进制响应（Excel/PDF 等） */
export async function download(
  url: string,
  params?: object,
  filename?: string,
  method: 'get' | 'post' = 'get'
): Promise<void> {
  const response = method === 'post'
    ? await service.post(url, params, { responseType: 'blob' })
    : await service.get(url, { params, responseType: 'blob' })
  const blob = response.data as Blob
  const disposition = (response.headers?.['content-disposition'] as string) || ''
  let finalName = filename || 'download'
  const match = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (match && match[1]) {
    finalName = decodeURIComponent(match[1])
  }
  saveBlob(blob, finalName)
}

/** 保存 Blob 到本地 */
export function saveBlob(blob: Blob, filename: string): void {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

/** 保存 Base64 内容（后端返回的错误报告等） */
export function saveBase64(base64: string, filename: string, mime = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'): void {
  const binary = window.atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }
  saveBlob(new Blob([bytes], { type: mime }), filename)
}

export default service
