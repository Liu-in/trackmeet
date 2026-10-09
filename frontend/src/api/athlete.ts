import { del, download, get, post, put, saveBase64, upload, type PageResult } from './request'

/** 运动员 */
export interface Athlete {
  id?: number
  meetId?: number
  athleteName: string
  gender: string
  birthDate?: string
  idCard: string
  groupId?: number
  unitId?: number
  bibNo?: string
  className?: string
  phone?: string
  guardian?: string
  guardianPhone?: string
  qualificationStatus?: string
  qualifyRemark?: string
  isRelayOnly?: number
  entrySource?: string
  remark?: string
  /** 以下为后端联表投影可能返回的展示字段（可选） */
  unitName?: string
  groupName?: string
  age?: number
}

export interface AthleteQuery {
  page?: number
  size?: number
  keyword?: string
  meetId?: number
  unitId?: number
  groupId?: number
  gender?: string
  qualificationStatus?: string
  bibNo?: string
}

/** 导入结果 */
export interface AthleteImportError {
  rowNum: number
  athleteName?: string
  idCard?: string
  unitCode?: string
  groupCode?: string
  errorMessage: string
}

export interface AthleteImportResult {
  dryRun: boolean
  totalCount: number
  successCount: number
  failCount: number
  autoBibCount: number
  taskId?: number
  message: string
  errors: AthleteImportError[]
  errorFileBase64?: string
  errorFileName?: string
}

export function pageAthletes(params: AthleteQuery) {
  return get<PageResult<Athlete>>('/athletes', params)
}

export function getAthlete(id: number) {
  return get<Athlete>(`/athletes/${id}`)
}

export function createAthlete(data: Athlete) {
  return post<number>('/athletes', data)
}

export function updateAthlete(id: number, data: Athlete) {
  return put<void>(`/athletes/${id}`, data)
}

export function deleteAthlete(id: number) {
  return del<void>(`/athletes/${id}`)
}

/** 下载导入模板（xlsx） */
export function downloadAthleteTemplate() {
  return download('/athletes/import-template', undefined, '运动员导入模板.xlsx')
}

/** 导入运动员；dryRun=true 时仅校验不落库 */
export function importAthletes(file: File, meetId?: number, dryRun = false) {
  const extra: Record<string, string | number | boolean> = { dryRun }
  if (meetId) {
    extra.meetId = meetId
  }
  return upload<AthleteImportResult>('/athletes/import', file, extra)
}

/** 保存导入错误报告到本地 */
export function saveImportErrorFile(result: AthleteImportResult) {
  if (result.errorFileBase64) {
    saveBase64(result.errorFileBase64, result.errorFileName || '运动员导入错误报告.xlsx')
  }
}
