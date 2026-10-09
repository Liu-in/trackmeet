import { del, download, get, post, put, type PageResult } from './request'

/**
 * 通用「表」结构：由后端把秩序册各表拍平为 表头 + 行数据（全部字符串），
 * 前端据此渲染 A4 表格并调用 window.print() 直接打印/另存为 PDF。
 */
export interface SheetBundle {
  sheetKey: string
  sheetName: string
  headers: string[]
  rows: string[][]
}

export interface MeetInfo {
  meetName?: string
  edition?: string
  shortName?: string
  startDate?: string
  endDate?: string
  place?: string
  host?: string
  organizer?: string
}

export interface OrderBook {
  meetInfo?: MeetInfo
  scheduleSheets?: SheetBundle[]
  heatSheets?: SheetBundle[]
  laneSheets?: SheetBundle[]
  callingSheets?: SheetBundle[]
}

export interface PrintQuery {
  meetId?: number
  sessionId?: number
  groupId?: number
  eventId?: number
  unitId?: number
  venueId?: number
  publishOnly?: boolean
  format?: string
}

/** 秩序册（JSON：含届次信息与四张表） */
export function getOrderBook(params: PrintQuery) {
  return get<OrderBook>('/print/order-book', { ...params, format: 'json' })
}

/** 总日程表 */
export function getScheduleSheet(params: PrintQuery) {
  return get<SheetBundle[]>('/print/order-book/schedule', params)
}

/** 分组表 */
export function getHeatSheet(params: PrintQuery) {
  return get<SheetBundle[]>('/print/heats', params)
}

/** 分道表 */
export function getLaneSheet(params: PrintQuery) {
  return get<SheetBundle[]>('/print/lanes', params)
}

/** 检录表 */
export function getCallingSheet(params: PrintQuery) {
  return get<SheetBundle[]>('/print/calling-sheets', params)
}

/** 下载秩序册 Excel（含 4 个 Sheet） */
export function downloadOrderBook(params: PrintQuery, filename = '秩序册.xlsx') {
  return download('/print/order-book/export', { ...params, format: 'xlsx' }, filename)
}

// ==================== 打印中心：PDF / Excel ====================

/** PDF 生成能力（无中文字体时 available=false，前端应禁用 PDF 按钮并提示） */
export interface PdfCapability {
  available: boolean
  fontPath?: string
  fontName?: string
  embeddedMode?: string
  message?: string
  candidates?: string[]
}

/** 报表类型：与后端 /api/print/pdf/{report} 的 {report} 一致 */
export type ReportType = 'order-book' | 'results' | 'calling' | 'lanes' | 'points' | 'medals' | 'certificates'

export interface ReportMeta {
  reportType: string
  reportName: string
  formats: string[]
  permCode: string
  description?: string
}

export interface PdfReportQuery extends PrintQuery {
  reportType?: string
  roundId?: number
  heatId?: number
  scoreType?: string
  certType?: string
  templateId?: number
}

export interface PrintTemplate {
  id?: number
  meetId?: number
  templateCode?: string
  templateName?: string
  /** PROGRAM 秩序册 / START_LIST 检录分道表 / RESULT 成绩公告 / POINTS 积分表 / MEDAL 奖牌榜 / CERT 证书 */
  reportType?: string
  headerHtml?: string
  footerHtml?: string
  logoFileId?: number
  signatureHtml?: string
  pageSize?: string
  orientation?: string
  fontSize?: number
  showPageNo?: number
  showPrintTime?: number
  isDefault?: number
  extraJson?: string
  status?: string
  remark?: string
}

export interface PrintLog {
  id?: number
  reportType?: string
  reportName?: string
  paramsJson?: string
  format?: string
  meetRefId?: number
  templateId?: number
  operatorId?: number
  operatorName?: string
  printCount?: number
  printDevice?: string
  ip?: string
  status?: string
  errorMsg?: string
  printTime?: string
  createTime?: string
}

export interface PrintLogQuery {
  page?: number
  size?: number
  meetId?: number
  reportType?: string
  format?: string
  status?: string
  keyword?: string
  beginTime?: string
  endTime?: string
}

/** PDF 能力探测 */
export function getPdfCapability() {
  return get<PdfCapability>('/print/pdf/capability')
}

/** 报表清单（打印中心左侧列表） */
export function listReports() {
  return get<ReportMeta[]>('/print/reports')
}

/** 下载 PDF：report 取 ReportType */
export function downloadPdf(report: ReportType, params: PdfReportQuery, filename = '报表.pdf') {
  return download(`/print/pdf/${report}`, params, filename)
}

/** 统一 Excel 导出：report 取 ReportType */
export function downloadReportExcel(report: ReportType, params: PdfReportQuery, filename = '报表.xlsx') {
  return download(`/print/excel/${report}`, params, filename)
}

// ==================== 打印模板 ====================
export function pageTemplates(params: { page?: number; size?: number; meetId?: number; reportType?: string; keyword?: string }) {
  return get<PageResult<PrintTemplate>>('/print/templates', params)
}

export function getTemplate(id: number) {
  return get<PrintTemplate>(`/print/templates/${id}`)
}

export function getDefaultTemplate(reportType: string) {
  return get<PrintTemplate>('/print/templates/default', { reportType })
}

export function createTemplate(data: PrintTemplate) {
  return post<number>('/print/templates', data)
}

export function updateTemplate(id: number, data: PrintTemplate) {
  return put<void>(`/print/templates/${id}`, data)
}

export function deleteTemplate(id: number) {
  return del<void>(`/print/templates/${id}`)
}

// ==================== 打印留痕 ====================
export function pagePrintLogs(params: PrintLogQuery) {
  return get<PageResult<PrintLog>>('/print/logs', params)
}

export function savePrintLog(data: Partial<PrintLog>) {
  return post<number>('/print/logs', data)
}
