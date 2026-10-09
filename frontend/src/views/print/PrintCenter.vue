<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  downloadPdf,
  downloadReportExcel,
  getCallingSheet,
  getHeatSheet,
  getLaneSheet,
  getOrderBook,
  getPdfCapability,
  getScheduleSheet,
  listReports,
  pagePrintLogs,
  pageTemplates,
  savePrintLog,
  type MeetInfo,
  type PdfCapability,
  type PdfReportQuery,
  type PrintLog,
  type PrintQuery,
  type PrintTemplate,
  type ReportMeta,
  type ReportType,
  type SheetBundle
} from '@/api/print'
import { getMedalSheet, getResultSheet, getTeamScoreSheet } from '@/api/standing'
import { pageCertificates, type Certificate } from '@/api/standing'
import { listRounds, pageEvents, type EventRound, type SportEvent } from '@/api/event'
import { listSessions, pageSchedule, type ScheduleSession, type ScheduleVO } from '@/api/schedule'
import { listVenues, type Venue } from '@/api/meet'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'
import '@/styles/print.css'

/**
 * 打印中心：左侧报表清单 + 右侧参数区 + A4 在线预览 + PDF/Excel 下载与打印留痕。
 * 权限编码：由后端 /print/reports 返回的 permCode 决定（如 print:center / print:orderbook / print:calling / print:results / print:standings）。
 */
defineOptions({ name: 'PrintCenter' })

const meetStore = useMeetStore()
const userStore = useUserStore()

const meetReady = computed(() => !!meetStore.currentMeetId)
const meetName = computed(() => meetStore.currentMeet?.meetName || '田径运动会')
const meetShortName = computed(
  () => meetStore.currentMeet?.shortName || meetStore.currentMeet?.edition || meetStore.currentMeet?.meetName || '届次'
)

/* ------------------------------- 报表清单 ------------------------------- */

const REPORTS_FALLBACK: ReportMeta[] = [
  { reportType: 'order-book', reportName: '秩序册', formats: ['PDF', 'XLSX', 'HTML'], permCode: 'print:orderbook', description: '总日程表 / 分组表 / 分道表 / 检录表' },
  { reportType: 'results', reportName: '成绩公告', formats: ['PDF', 'XLSX'], permCode: 'print:results', description: '按项目 / 组别 / 单位输出成绩单' },
  { reportType: 'points', reportName: '积分表', formats: ['PDF', 'XLSX'], permCode: 'print:standings', description: '团体总分（男 / 女 / 合计）' },
  { reportType: 'medals', reportName: '奖牌榜', formats: ['PDF', 'XLSX'], permCode: 'print:standings', description: '各单位金银铜与排序分' },
  { reportType: 'calling', reportName: '检录表', formats: ['PDF', 'XLSX'], permCode: 'print:calling', description: '按检录时间排序的检录清单' },
  { reportType: 'lanes', reportName: '分道表', formats: ['PDF', 'XLSX'], permCode: 'print:orderbook', description: '径赛 / 接力分组分道表' },
  { reportType: 'certificates', reportName: '证书', formats: ['PDF', 'XLSX'], permCode: 'certificate:list', description: '获奖证书 / 参赛证书' }
]

const reports = ref<ReportMeta[]>([])
const reportsLoading = ref(false)
const activeReportType = ref<string>('order-book')

const activeReport = computed<ReportMeta | undefined>(
  () => reports.value.find((item) => item.reportType === activeReportType.value) || reports.value[0]
)

const reportName = computed(() => activeReport.value?.reportName || '报表')

const REPORT_ICONS: Record<string, string> = {
  'order-book': '📘',
  results: '🏁',
  points: '📊',
  medals: '🏅',
  calling: '📣',
  lanes: '🛣️',
  certificates: '🎖️'
}

function reportIcon(reportType: string): string {
  return REPORT_ICONS[reportType] || '📄'
}

function canAccess(report: ReportMeta): boolean {
  return userStore.hasPerm(report.permCode)
}

/** 当前报表是否具备权限（无报表时按无权处理） */
const canUseCurrentReport = computed(() => {
  const meta = activeReport.value
  return meta ? userStore.hasPerm(meta.permCode) : false
})

/** 有权限的报表数量，为 0 时提示联系管理员 */
const accessibleCount = computed(() => reports.value.filter((item) => canAccess(item)).length)

async function loadReports() {
  reportsLoading.value = true
  try {
    const list = await listReports()
    reports.value = Array.isArray(list) && list.length > 0 ? list : REPORTS_FALLBACK
  } catch {
    reports.value = REPORTS_FALLBACK
  } finally {
    reportsLoading.value = false
    const first = reports.value.find((item) => canAccess(item))
    if (first) {
      activeReportType.value = first.reportType
    }
  }
}

function handleReportSelect(meta: ReportMeta) {
  if (!canAccess(meta)) {
    ElMessage.warning(`当前账号无「${meta.reportName}」打印权限（${meta.permCode}）`)
    return
  }
  if (meta.reportType === activeReportType.value) {
    return
  }
  activeReportType.value = meta.reportType
}

/* ------------------------------- PDF 能力探测 ------------------------------- */

const pdfCapability = ref<PdfCapability | null>(null)
const pdfCapabilityLoading = ref(false)
const pdfAvailable = computed(() => pdfCapability.value?.available !== false)

async function loadPdfCapability() {
  pdfCapabilityLoading.value = true
  try {
    const capability = await getPdfCapability()
    pdfCapability.value = capability || null
  } catch {
    // 探测接口不可用时按「不可用」处理，避免下载按钮误报成功
    pdfCapability.value = { available: false, message: 'PDF 能力探测接口不可用，请改用浏览器打印后另存为 PDF' }
  } finally {
    pdfCapabilityLoading.value = false
  }
}

/* ------------------------------- 参数区 ------------------------------- */

const params = reactive<{
  groupId?: number
  sessionId?: number
  scheduleId?: number
  eventId?: number
  roundId?: number
  unitId?: number
  venueId?: number
  publishOnly: boolean
  scoreType: string
  certType: string
  templateId?: number
}>({
  groupId: undefined,
  sessionId: undefined,
  scheduleId: undefined,
  eventId: undefined,
  roundId: undefined,
  unitId: undefined,
  venueId: undefined,
  publishOnly: true,
  scoreType: 'TOTAL',
  certType: 'RANK',
  templateId: undefined
})

const PERIOD_LABELS: Record<string, string> = {
  MORNING: '上午',
  AFTERNOON: '下午',
  EVENING: '晚上'
}

function periodLabel(period?: string): string {
  if (!period) {
    return ''
  }
  return PERIOD_LABELS[period] || period
}

function sessionLabel(item: ScheduleSession): string {
  const date = item.sessionDate || ''
  const period = periodLabel(item.period)
  const name = item.sessionName || `单元 ${item.id ?? ''}`
  return `${date} ${period} ${name}`.trim()
}

function scheduleLabel(item: ScheduleVO): string {
  const time = item.planStartTime || ''
  const event = item.eventName || `项目 ${item.eventId ?? ''}`
  const round = item.roundName || item.roundCode || ''
  return `${time} ${event} ${round}`.trim()
}

const SCORE_TYPES = [
  { value: 'MALE', label: '男子组' },
  { value: 'FEMALE', label: '女子组' },
  { value: 'TOTAL', label: '合计' }
]

const CERT_TYPES = [
  { value: 'RANK', label: '名次证书' },
  { value: 'RESULT', label: '成绩证书' },
  { value: 'MEDAL', label: '奖牌证书' },
  { value: 'PARTICIPATION', label: '参赛证书' }
]

/** 当前报表需要展示的参数控件 */
const showSession = computed(() => ['order-book', 'calling', 'lanes'].includes(activeReportType.value))
const showSchedule = computed(() => ['calling', 'lanes', 'results'].includes(activeReportType.value))
const showEvent = computed(() => ['results', 'points', 'certificates'].includes(activeReportType.value))
const showRound = computed(() => ['results', 'points'].includes(activeReportType.value))
const showUnit = computed(() => ['results', 'points', 'certificates'].includes(activeReportType.value))
const showScoreType = computed(() => activeReportType.value === 'points')
const showCertType = computed(() => activeReportType.value === 'certificates')
const showPublishOnly = computed(() => ['order-book', 'calling', 'lanes', 'results'].includes(activeReportType.value))

/* ------------------------------- 下拉数据 ------------------------------- */

const sessions = ref<ScheduleSession[]>([])
const sessionsLoading = ref(false)
const schedules = ref<ScheduleVO[]>([])
const schedulesLoading = ref(false)
const events = ref<SportEvent[]>([])
const eventsLoading = ref(false)
const rounds = ref<EventRound[]>([])
const roundsLoading = ref(false)
const venues = ref<Venue[]>([])
const templates = ref<PrintTemplate[]>([])
const templatesLoading = ref(false)

async function loadSessions() {
  if (!meetStore.currentMeetId) {
    sessions.value = []
    return
  }
  sessionsLoading.value = true
  try {
    const page = await listSessions(meetStore.currentMeetId)
    sessions.value = page?.records || []
  } catch {
    sessions.value = []
  } finally {
    sessionsLoading.value = false
  }
}

async function loadSchedules() {
  if (!meetStore.currentMeetId) {
    schedules.value = []
    return
  }
  schedulesLoading.value = true
  try {
    const page = await pageSchedule({ page: 1, size: 200, meetId: meetStore.currentMeetId, sessionId: params.sessionId })
    schedules.value = page?.records || []
  } catch {
    schedules.value = []
  } finally {
    schedulesLoading.value = false
  }
}

async function loadEvents() {
  if (!meetStore.currentMeetId) {
    events.value = []
    return
  }
  eventsLoading.value = true
  try {
    const page = await pageEvents({ page: 1, size: 200, meetId: meetStore.currentMeetId, groupId: params.groupId })
    events.value = page?.records || []
  } catch {
    events.value = []
  } finally {
    eventsLoading.value = false
  }
}

async function loadRounds() {
  if (!params.eventId) {
    rounds.value = []
    params.roundId = undefined
    return
  }
  roundsLoading.value = true
  try {
    const list = await listRounds(params.eventId)
    rounds.value = Array.isArray(list) ? list : []
  } catch {
    rounds.value = []
  } finally {
    roundsLoading.value = false
  }
}

async function loadVenues() {
  if (!meetStore.currentMeetId) {
    venues.value = []
    return
  }
  try {
    const list = await listVenues({ meetId: meetStore.currentMeetId })
    venues.value = Array.isArray(list) ? list : []
  } catch {
    venues.value = []
  }
}

const TEMPLATE_REPORT_TYPES: Record<string, string> = {
  'order-book': 'PROGRAM',
  calling: 'START_LIST',
  lanes: 'START_LIST',
  results: 'RESULT',
  points: 'POINTS',
  medals: 'MEDAL',
  certificates: 'CERT'
}

async function loadTemplates() {
  if (!meetStore.currentMeetId) {
    templates.value = []
    return
  }
  templatesLoading.value = true
  try {
    const page = await pageTemplates({
      page: 1,
      size: 100,
      meetId: meetStore.currentMeetId,
      reportType: TEMPLATE_REPORT_TYPES[activeReportType.value]
    })
    templates.value = page?.records || []
  } catch {
    templates.value = []
  } finally {
    templatesLoading.value = false
  }
}

const currentTemplate = computed<PrintTemplate | undefined>(
  () => templates.value.find((item) => item.id === params.templateId) || undefined
)

/* ------------------------------- A4 预览取数 ------------------------------- */

const sheets = ref<SheetBundle[]>([])
const certificates = ref<Certificate[]>([])
const meetInfo = ref<MeetInfo | null>(null)
const previewLoading = ref(false)

const isCertificateReport = computed(() => activeReportType.value === 'certificates')

const previewPageCount = computed(() =>
  isCertificateReport.value ? certificates.value.length : sheets.value.length
)

const previewRowCount = computed(() =>
  sheets.value.reduce((sum, bundle) => sum + (bundle.rows?.length || 0), 0)
)

/** 是否已有可打印内容（用于空态判断） */
const hasPreviewContent = computed(() =>
  isCertificateReport.value ? certificates.value.length > 0 : sheets.value.length > 0
)

/** 传给后端下载/导出接口的参数 */
function buildPdfQuery(): PdfReportQuery {
  return {
    meetId: meetStore.currentMeetId,
    reportType: activeReportType.value,
    sessionId: params.sessionId,
    groupId: params.groupId,
    eventId: params.eventId,
    roundId: params.roundId,
    unitId: params.unitId,
    venueId: params.venueId,
    publishOnly: params.publishOnly,
    scoreType: showScoreType.value ? params.scoreType : undefined,
    certType: showCertType.value ? params.certType : undefined,
    templateId: params.templateId
  }
}

function buildSheetQuery(): PrintQuery {
  return {
    meetId: meetStore.currentMeetId,
    sessionId: params.sessionId,
    groupId: params.groupId,
    eventId: params.eventId,
    unitId: params.unitId,
    venueId: params.venueId,
    publishOnly: params.publishOnly
  }
}

/** 按报表类型取数：秩序册可由 getOrderBook 一次取全，其余走专用表接口 */
async function fetchSheets(): Promise<{ bundles: SheetBundle[]; info: MeetInfo | null }> {
  const query = buildSheetQuery()
  const reportType = activeReportType.value
  if (reportType === 'order-book') {
    const book = await getOrderBook(query)
    const bundles: SheetBundle[] = [
      ...(book?.scheduleSheets || []),
      ...(book?.heatSheets || []),
      ...(book?.laneSheets || []),
      ...(book?.callingSheets || [])
    ]
    return { bundles, info: book?.meetInfo || null }
  }
  if (reportType === 'results') {
    const list = await getResultSheet(query)
    return { bundles: Array.isArray(list) ? list : [], info: null }
  }
  if (reportType === 'points') {
    const list = await getTeamScoreSheet({
      meetId: meetStore.currentMeetId,
      groupId: params.groupId,
      scoreType: params.scoreType
    })
    return { bundles: Array.isArray(list) ? list : [], info: null }
  }
  if (reportType === 'medals') {
    const list = await getMedalSheet({ meetId: meetStore.currentMeetId, groupId: params.groupId })
    return { bundles: Array.isArray(list) ? list : [], info: null }
  }
  if (reportType === 'calling') {
    const list = await getCallingSheet({ ...query, sessionId: params.sessionId })
    return { bundles: Array.isArray(list) ? list : [], info: null }
  }
  if (reportType === 'lanes') {
    const list = await getLaneSheet({ ...query, scheduleId: params.scheduleId } as PrintQuery)
    return { bundles: Array.isArray(list) ? list : [], info: null }
  }
  return { bundles: [], info: null }
}

function currentParamText(): string {
  const parts: string[] = []
  const group = meetStore.groups.find((item) => item.id === params.groupId)
  parts.push(`组别：${group?.groupName || '全部'}`)
  if (showSession.value) {
    const session = sessions.value.find((item) => item.id === params.sessionId)
    parts.push(`单元：${session ? sessionLabel(session) : '全部'}`)
  }
  if (showEvent.value) {
    const event = events.value.find((item) => item.id === params.eventId)
    parts.push(`项目：${event?.eventName || '全部'}`)
  }
  if (showScoreType.value) {
    const score = SCORE_TYPES.find((item) => item.value === params.scoreType)
    parts.push(`口径：${score?.label || params.scoreType}`)
  }
  if (showCertType.value) {
    const cert = CERT_TYPES.find((item) => item.value === params.certType)
    parts.push(`证书类型：${cert?.label || params.certType}`)
  }
  if (showPublishOnly.value) {
    parts.push(params.publishOnly ? '仅已发布' : '含草稿')
  }
  return parts.join('｜')
}

const previewParamText = computed(() => currentParamText())

async function loadPreview() {
  if (!meetStore.currentMeetId) {
    sheets.value = []
    certificates.value = []
    meetInfo.value = null
    return
  }
  if (!canUseCurrentReport.value) {
    sheets.value = []
    certificates.value = []
    return
  }
  previewLoading.value = true
  try {
    if (isCertificateReport.value) {
      const page = await pageCertificates({
        page: 1,
        size: 20,
        meetId: meetStore.currentMeetId,
        certType: params.certType,
        unitId: params.unitId
      })
      certificates.value = page?.records || []
      sheets.value = []
      meetInfo.value = null
    } else {
      const result = await fetchSheets()
      sheets.value = result.bundles
      meetInfo.value = result.info
      certificates.value = []
    }
  } catch {
    sheets.value = []
    certificates.value = []
  } finally {
    previewLoading.value = false
  }
}

/* ------------------------------- 预览表头 / 页脚 ------------------------------- */

const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })

const meetDateText = computed(() => {
  const start = meetInfo.value?.startDate || meetStore.currentMeet?.startDate
  const end = meetInfo.value?.endDate || meetStore.currentMeet?.endDate
  if (start && end) {
    return `${start} ~ ${end}`
  }
  return start || end || '-'
})

const templateFontSize = computed(() => currentTemplate.value?.fontSize || 12)

const previewPageStyle = computed(() => ({ fontSize: `${templateFontSize.value}px` }))

const templateLandscape = computed(() => currentTemplate.value?.orientation === 'LANDSCAPE')

function printTimeText(): string {
  return new Date().toLocaleString('zh-CN')
}

/* ------------------------------- 工具栏操作 ------------------------------- */

const downloadingPdf = ref(false)
const downloadingExcel = ref(false)
const logsVisible = ref(false)

function sanitizeFilePart(text: string): string {
  return text.replace(/[\\/:*?"<>|]/g, '').trim() || '报表'
}

function dateStamp(): string {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`
}

function buildFileName(extension: string): string {
  return `${sanitizeFilePart(reportName.value)}_${sanitizeFilePart(meetShortName.value)}_${dateStamp()}.${extension}`
}

/** 下载成功后写留痕：失败只提示，不阻塞主流程 */
async function writePrintLog(format: string) {
  try {
    await savePrintLog({
      reportType: activeReportType.value,
      reportName: reportName.value,
      format,
      paramsJson: JSON.stringify(buildPdfQuery()),
      meetRefId: meetStore.currentMeetId,
      templateId: params.templateId,
      printCount: 1,
      status: 'SUCCESS'
    })
  } catch {
    ElMessage.warning('打印留痕写入失败，不影响文件下载')
  }
}

function ensureReady(): boolean {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return false
  }
  if (!canUseCurrentReport.value) {
    ElMessage.warning(`当前账号无「${reportName.value}」打印权限`)
    return false
  }
  return true
}

const REPORT_TYPE_MAP: Record<string, ReportType> = {
  'order-book': 'order-book',
  results: 'results',
  calling: 'calling',
  lanes: 'lanes',
  points: 'points',
  medals: 'medals',
  certificates: 'certificates'
}

function currentReportType(): ReportType {
  return REPORT_TYPE_MAP[activeReportType.value] || 'order-book'
}

async function handlePrint() {
  if (!ensureReady()) {
    return
  }
  await loadPreview()
  if (!previewPageCount.value) {
    ElMessage.warning('当前条件下没有可打印的内容')
    return
  }
  await nextTick()
  window.print()
}

async function handleDownloadPdf() {
  if (!ensureReady()) {
    return
  }
  if (!pdfAvailable.value) {
    ElMessage.warning('服务端缺少中文字体，无法生成 PDF，请改用「打印 / 另存为 PDF」')
    return
  }
  downloadingPdf.value = true
  try {
    const filename = buildFileName('pdf')
    await downloadPdf(currentReportType(), buildPdfQuery(), filename)
    ElMessage.success(`已开始下载：${filename}`)
    await writePrintLog('PDF')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    downloadingPdf.value = false
  }
}

async function handleDownloadExcel() {
  if (!ensureReady()) {
    return
  }
  downloadingExcel.value = true
  try {
    const filename = buildFileName('xlsx')
    await downloadReportExcel(currentReportType(), buildPdfQuery(), filename)
    ElMessage.success(`已开始下载：${filename}`)
    await writePrintLog('XLSX')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    downloadingExcel.value = false
  }
}

async function handleRefreshPreview() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  await Promise.all([loadSessions(), loadSchedules(), loadEvents(), loadVenues(), loadTemplates()])
  await loadPreview()
}

async function handleResetParams() {
  params.groupId = undefined
  params.sessionId = undefined
  params.scheduleId = undefined
  params.eventId = undefined
  params.roundId = undefined
  params.unitId = undefined
  params.venueId = undefined
  params.publishOnly = true
  params.scoreType = 'TOTAL'
  params.certType = 'RANK'
  params.templateId = undefined
}

/* ------------------------------- 打印留痕抽屉 ------------------------------- */

const logs = ref<PrintLog[]>([])
const logsLoading = ref(false)
const logsTotal = ref(0)
const logsQuery = reactive({ page: 1, size: 10, reportType: '', format: '' })

async function loadLogs() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  logsLoading.value = true
  try {
    const page = await pagePrintLogs({
      page: logsQuery.page,
      size: logsQuery.size,
      meetId: meetStore.currentMeetId,
      reportType: logsQuery.reportType || undefined,
      format: logsQuery.format || undefined
    })
    logs.value = page?.records || []
    logsTotal.value = page?.total || 0
  } catch {
    logs.value = []
    logsTotal.value = 0
  } finally {
    logsLoading.value = false
  }
}

function openLogs() {
  logsVisible.value = true
  logsQuery.page = 1
  loadLogs()
}

function handleLogPageChange(page: number) {
  logsQuery.page = page
  loadLogs()
}

function handleLogSizeChange(size: number) {
  logsQuery.size = size
  logsQuery.page = 1
  loadLogs()
}

/* ------------------------------- 联动与生命周期 ------------------------------- */

let previewTimer: number | undefined

function schedulePreview() {
  if (previewTimer !== undefined) {
    window.clearTimeout(previewTimer)
  }
  previewTimer = window.setTimeout(() => {
    loadPreview()
  }, 250)
}

watch(activeReportType, async () => {
  handleResetParams()
  await Promise.all([loadTemplates(), loadSchedules()])
  await loadPreview()
})

watch(
  () => params.groupId,
  async () => {
    await Promise.all([loadEvents(), loadPreview()])
  }
)

watch(
  () => params.sessionId,
  async () => {
    await loadSchedules()
    await loadPreview()
  }
)

watch(
  () => params.eventId,
  async () => {
    await loadRounds()
    await loadPreview()
  }
)

watch(
  () => [params.roundId, params.scheduleId, params.unitId, params.venueId, params.certType, params.scoreType, params.publishOnly, params.templateId],
  () => {
    schedulePreview()
  }
)

watch(
  () => meetStore.currentMeetId,
  async () => {
    handleResetParams()
    await Promise.all([loadSessions(), loadSchedules(), loadEvents(), loadVenues(), loadTemplates()])
    await Promise.all([loadReports(), loadPdfCapability(), loadPreview()])
  }
)

onMounted(async () => {
  if (!meetStore.loaded) {
    await meetStore.loadMeets().catch(() => undefined)
  }
  if (meetStore.currentMeetId && (!meetStore.groups.length || !meetStore.units.length)) {
    await meetStore.loadGroupsAndUnits().catch(() => undefined)
  }
  await Promise.all([loadReports(), loadPdfCapability()])
  await Promise.all([loadSessions(), loadSchedules(), loadEvents(), loadVenues(), loadTemplates()])
  await loadPreview()
})
</script>

<template>
  <div class="tm-page print-center-page">
    <div class="tm-card no-print">
      <div class="tm-page-title">打印中心</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜选择左侧报表 → 配置右侧参数 → A4 预览确认 → 打印或下载 PDF / Excel
      </div>
    </div>

    <el-alert
      v-if="!meetReady"
      class="no-print"
      title="尚未选择届次：请先在顶栏选择届次后再使用打印中心。"
      type="warning"
      :closable="false"
      show-icon
    />

    <el-alert
      v-if="accessibleCount === 0"
      class="no-print"
      title="当前账号没有任何报表打印权限，请联系管理员分配权限。"
      type="error"
      :closable="false"
      show-icon
    />

    <!-- PDF 能力提示 -->
    <el-alert
      v-if="pdfCapability && pdfAvailable"
      class="no-print"
      :title="`服务端 PDF 生成可用${pdfCapability.fontName ? '（中文字体：' + pdfCapability.fontName + '）' : ''}${pdfCapability.embeddedMode ? '｜嵌入模式：' + pdfCapability.embeddedMode : ''}`"
      type="success"
      :closable="false"
      show-icon
    />
    <el-alert
      v-else-if="pdfCapability && !pdfAvailable"
      class="no-print"
      :title="pdfCapability.message || '服务端缺少可嵌入的中文字体，暂时无法生成 PDF'"
      type="warning"
      :closable="false"
      show-icon
    >
      <template #default>
        <div class="tm-form-tip">
          可改用工具栏「打印 / 另存为 PDF」：在浏览器打印对话框中选择「另存为 PDF」即可获得 PDF 文件。
          <span v-if="pdfCapability.candidates && pdfCapability.candidates.length">
            可用字体候选：{{ pdfCapability.candidates.join('、') }}
          </span>
        </div>
      </template>
    </el-alert>

    <!-- 统计卡 -->
    <div class="tm-stat-grid no-print">
      <div class="tm-stat-card">
        <div class="tm-stat-label">本次预览张数</div>
        <div class="tm-stat-value">{{ previewPageCount }}</div>
      </div>
      <div class="tm-stat-card">
        <div class="tm-stat-label">当前报表</div>
        <div class="tm-stat-value" style="font-size: 18px">{{ reportName }}</div>
      </div>
      <div class="tm-stat-card">
        <div class="tm-stat-label">是否已选模板</div>
        <div class="tm-stat-value" style="font-size: 18px">{{ currentTemplate ? currentTemplate.templateName || '已选模板' : '未选模板' }}</div>
      </div>
      <div class="tm-stat-card">
        <div class="tm-stat-label">PDF 可用性</div>
        <div class="tm-stat-value" style="font-size: 18px">
          <el-tag :type="pdfAvailable ? 'success' : 'warning'" size="small">
            {{ pdfCapabilityLoading ? '探测中…' : pdfAvailable ? '可用' : '不可用' }}
          </el-tag>
        </div>
      </div>
    </div>

    <div class="print-layout">
      <!-- 左侧报表清单 -->
      <div class="tm-card print-side no-print">
        <div class="print-side-title">报表清单</div>
        <div v-loading="reportsLoading" class="print-side-list">
          <div
            v-for="meta in reports"
            :key="meta.reportType"
            class="print-report-item"
            :class="{
              'is-active': meta.reportType === activeReportType,
              'is-disabled': !canAccess(meta)
            }"
            @click="handleReportSelect(meta)"
          >
            <span class="print-report-icon">{{ reportIcon(meta.reportType) }}</span>
            <div class="print-report-main">
              <div class="print-report-name">{{ meta.reportName }}</div>
              <div class="print-report-desc">{{ meta.description || meta.permCode }}</div>
            </div>
            <div class="print-report-tags">
              <el-tag v-for="format in meta.formats" :key="format" size="small" effect="plain">{{ format }}</el-tag>
            </div>
          </div>
          <el-empty v-if="!reportsLoading && !reports.length" description="暂无可用报表" />
        </div>
        <div class="tm-form-tip" style="margin-top: 8px">
          权限不足的报表已置灰，鼠标悬停可查看所需权限编码。
        </div>
      </div>

      <!-- 右侧配置 + 预览 -->
      <div class="print-main">
        <div class="tm-card no-print">
          <div class="tm-toolbar">
            <div class="tm-toolbar-actions">
              <el-button type="primary" :disabled="!meetReady" @click="handlePrint">打印 / 另存为 PDF</el-button>
              <el-tooltip
                :content="pdfAvailable ? '调用服务端生成 PDF 后下载' : '服务端缺少中文字体，请改用浏览器打印另存为 PDF'"
                placement="top"
              >
                <span>
                  <el-button
                    type="success"
                    :loading="downloadingPdf"
                    :disabled="!meetReady || !pdfAvailable"
                    @click="handleDownloadPdf"
                  >
                    下载 PDF
                  </el-button>
                </span>
              </el-tooltip>
              <el-button :loading="downloadingExcel" :disabled="!meetReady" @click="handleDownloadExcel">导出 Excel</el-button>
              <el-button :loading="previewLoading" :disabled="!meetReady" @click="handleRefreshPreview">刷新预览</el-button>
              <el-button :disabled="!meetReady" @click="openLogs">打印留痕</el-button>
            </div>
            <div class="tm-toolbar-actions">
              <span class="tm-form-tip">共 {{ previewPageCount }} 页 / {{ previewRowCount }} 行</span>
            </div>
          </div>

          <el-divider />

          <!-- 参数区 -->
          <div class="tm-search-bar">
            <span>组别：</span>
            <el-select v-model="params.groupId" :disabled="!meetReady" placeholder="全部组别" filterable clearable style="width: 160px">
              <el-option v-for="item in meetStore.groups" :key="item.id" :label="item.groupName" :value="item.id as number" />
            </el-select>

            <template v-if="showSession">
              <span>竞赛单元：</span>
              <el-select
                v-model="params.sessionId"
                :loading="sessionsLoading"
                :disabled="!meetReady"
                placeholder="全部单元"
                filterable
                clearable
                style="width: 240px"
              >
                <el-option v-for="item in sessions" :key="item.id" :label="sessionLabel(item)" :value="item.id as number" />
              </el-select>
            </template>

            <template v-if="showSchedule">
              <span>日程条目：</span>
              <el-select
                v-model="params.scheduleId"
                :loading="schedulesLoading"
                :disabled="!meetReady"
                placeholder="全部日程"
                filterable
                clearable
                style="width: 260px"
              >
                <el-option v-for="item in schedules" :key="item.id" :label="scheduleLabel(item)" :value="item.id as number" />
              </el-select>
            </template>

            <template v-if="showEvent">
              <span>项目：</span>
              <el-select
                v-model="params.eventId"
                :loading="eventsLoading"
                :disabled="!meetReady"
                placeholder="全部项目"
                filterable
                clearable
                style="width: 200px"
              >
                <el-option v-for="item in events" :key="item.id" :label="item.eventName" :value="item.id as number" />
              </el-select>
            </template>

            <template v-if="showRound">
              <span>轮次：</span>
              <el-select
                v-model="params.roundId"
                :loading="roundsLoading"
                :disabled="!meetReady || !params.eventId"
                placeholder="全部轮次"
                filterable
                clearable
                style="width: 160px"
              >
                <el-option v-for="item in rounds" :key="item.id" :label="item.roundName || item.roundCode" :value="item.id as number" />
              </el-select>
            </template>

            <template v-if="showUnit">
              <span>单位：</span>
              <el-select v-model="params.unitId" :disabled="!meetReady" placeholder="全部单位" filterable clearable style="width: 180px">
                <el-option v-for="item in meetStore.units" :key="item.id" :label="item.unitName" :value="item.id as number" />
              </el-select>
            </template>

            <template v-if="showScoreType">
              <span>口径：</span>
              <el-select v-model="params.scoreType" :disabled="!meetReady" style="width: 130px">
                <el-option v-for="item in SCORE_TYPES" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </template>

            <template v-if="showCertType">
              <span>证书类型：</span>
              <el-select v-model="params.certType" :disabled="!meetReady" style="width: 150px">
                <el-option v-for="item in CERT_TYPES" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </template>

            <span>场地：</span>
            <el-select v-model="params.venueId" :disabled="!meetReady" placeholder="全部场地" filterable clearable style="width: 160px">
              <el-option v-for="item in venues" :key="item.id" :label="item.venueName" :value="item.id as number" />
            </el-select>

            <template v-if="showPublishOnly">
              <span>仅已发布：</span>
              <el-switch v-model="params.publishOnly" :disabled="!meetReady" />
            </template>

            <span>模板：</span>
            <el-select
              v-model="params.templateId"
              :loading="templatesLoading"
              :disabled="!meetReady"
              placeholder="使用默认模板"
              filterable
              clearable
              style="width: 200px"
            >
              <el-option
                v-for="item in templates"
                :key="item.id"
                :label="`${item.templateName || item.templateCode}${item.isDefault === 1 ? '（默认）' : ''}`"
                :value="item.id as number"
              />
            </el-select>

            <el-button @click="handleResetParams">重置参数</el-button>
          </div>

          <div class="tm-form-tip" style="margin-top: 8px">
            报表：{{ reportName }}｜{{ previewParamText }}｜未选择届次时参数与按钮不可用；打印时筛选与按钮自动隐藏，仅输出 A4 报表。
          </div>
        </div>

        <!-- A4 预览 -->
        <div v-loading="previewLoading" class="print-preview">
          <template v-if="isCertificateReport">
            <div
              v-for="(cert, index) in certificates"
              :key="cert.id ?? index"
              class="tm-print-page tm-cert-page"
              :style="previewPageStyle"
            >
              <div class="tm-print-header">
                <div class="tm-print-title">获奖证书</div>
                <div class="tm-print-subtitle">{{ meetName }}</div>
                <div class="tm-print-meta">证书编号：{{ cert.certNo || '-' }}｜签发日期：{{ cert.issueDate || today }}</div>
              </div>
              <div class="tm-print-section">
                <div class="tm-print-section-title">获奖信息</div>
                <table class="tm-print-table">
                  <tbody>
                    <tr>
                      <td class="tm-center">姓名</td>
                      <td class="tm-center">{{ cert.athleteName || '-' }}</td>
                      <td class="tm-center">单位</td>
                      <td class="tm-center">{{ cert.unitName || '-' }}</td>
                    </tr>
                    <tr>
                      <td class="tm-center">项目</td>
                      <td class="tm-center">{{ cert.eventName || '-' }}</td>
                      <td class="tm-center">名次</td>
                      <td class="tm-center">{{ cert.rankNo ?? '-' }}</td>
                    </tr>
                    <tr>
                      <td class="tm-center">成绩</td>
                      <td class="tm-center" colspan="3">{{ cert.resultText || '-' }}</td>
                    </tr>
                  </tbody>
                </table>
                <div class="tm-print-note">证书类型：{{ cert.certType || params.certType }}｜打印次数：{{ cert.printCount ?? 0 }}</div>
              </div>
              <div class="tm-print-signature">
                <span>记录员：____________</span>
                <span>裁判长：____________</span>
                <span>技术代表：____________</span>
              </div>
              <div class="tm-print-footer">
                <span>打印时间：{{ printTimeText() }}</span>
                <span>第 {{ index + 1 }} / {{ certificates.length }} 页</span>
              </div>
            </div>
            <div v-if="!previewLoading && !hasPreviewContent" class="tm-card">
              <div class="tm-print-empty">暂无证书数据：请先调整筛选条件或前往「证书打印」生成证书。</div>
            </div>          </template>

          <template v-else>
            <div
              v-for="(bundle, index) in sheets"
              :key="`${index}-${bundle.sheetKey}`"
              class="tm-print-page"
              :class="{ 'tm-print-page--landscape': templateLandscape }"
              :style="previewPageStyle"
            >
              <div class="tm-print-header">
                <div class="tm-print-title">{{ meetName }}</div>
                <div class="tm-print-subtitle">
                  <span>{{ reportName }}</span>
                  <span v-if="currentTemplate?.headerHtml" v-html="currentTemplate.headerHtml" />
                </div>
                <div class="tm-print-meta">
                  {{ meetInfo?.edition || meetStore.currentMeet?.edition || '' }}
                  <span v-if="meetDateText !== '-'">｜{{ meetDateText }}</span>
                  <span v-if="meetInfo?.place || meetStore.currentMeet?.place">｜{{ meetInfo?.place || meetStore.currentMeet?.place }}</span>
                  <span v-if="meetInfo?.host || meetStore.currentMeet?.host">｜主办：{{ meetInfo?.host || meetStore.currentMeet?.host }}</span>
                </div>
                <div class="tm-print-meta">{{ previewParamText }}</div>
              </div>

              <div class="tm-print-section">
                <div class="tm-print-section-title">{{ bundle.sheetName || reportName }}</div>
                <table class="tm-print-table">
                  <thead>
                    <tr>
                      <th v-for="(header, headerIndex) in bundle.headers" :key="headerIndex">{{ header }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, rowIndex) in bundle.rows" :key="rowIndex">
                      <td v-for="(cell, cellIndex) in bundle.headers" :key="cellIndex" class="tm-center">
                        {{ row && row[cellIndex] !== undefined && row[cellIndex] !== null ? row[cellIndex] : '-' }}
                      </td>
                    </tr>
                    <tr v-if="!bundle.rows || bundle.rows.length === 0">
                      <td :colspan="bundle.headers.length || 1" class="tm-center">暂无数据</td>
                    </tr>
                  </tbody>
                </table>
                <div class="tm-print-note">共 {{ bundle.rows?.length || 0 }} 行｜纸张：{{ currentTemplate?.pageSize || 'A4' }}｜方向：{{ templateLandscape ? '横向' : '纵向' }}</div>
              </div>

              <div class="tm-print-signature">
                <span>记录员：____________</span>
                <span>裁判长：____________</span>
                <span>技术代表：____________</span>
              </div>

              <div class="tm-print-footer">
                <span>{{ currentTemplate?.footerHtml || '打印时间：' + printTimeText() }}</span>
                <span v-if="currentTemplate?.showPageNo !== 0">第 {{ index + 1 }} / {{ sheets.length }} 页</span>
              </div>
            </div>

            <div v-if="!previewLoading && !hasPreviewContent" class="tm-card">
              <div class="tm-print-empty">暂无数据：请调整参数、发布相应数据，或检查当前账号的报表权限。</div>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- 打印留痕 -->
    <el-drawer v-model="logsVisible" title="打印留痕" size="720px" class="no-print">
      <div class="tm-search-bar">
        <el-select v-model="logsQuery.reportType" placeholder="全部报表类型" clearable style="width: 160px" @change="loadLogs">
          <el-option v-for="item in reports" :key="item.reportType" :label="item.reportName" :value="item.reportType" />
        </el-select>
        <el-select v-model="logsQuery.format" placeholder="全部格式" clearable style="width: 140px" @change="loadLogs">
          <el-option label="PDF" value="PDF" />
          <el-option label="XLSX" value="XLSX" />
          <el-option label="PRINT" value="PRINT" />
        </el-select>
        <el-button :loading="logsLoading" @click="loadLogs">刷新</el-button>
      </div>

      <el-table v-loading="logsLoading" :data="logs" border stripe style="margin-top: 12px">
        <el-table-column label="报表" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as PrintLog).reportName || (row as PrintLog).reportType || '-' }}</template>
        </el-table-column>
        <el-table-column label="格式" width="90" align="center">
          <template #default="{ row }">{{ (row as PrintLog).format || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as PrintLog).operatorName || (row as PrintLog).operatorId || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as PrintLog).printTime || (row as PrintLog).createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(row as PrintLog).status === 'FAIL' ? 'danger' : 'success'" size="small">
              {{ (row as PrintLog).status || 'SUCCESS' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时 / 说明" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as PrintLog).errorMsg || `${(row as PrintLog).printCount ?? 1} 份` }}
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无打印留痕" />
        </template>
      </el-table>

      <div class="tm-pagination">
        <el-pagination
          :current-page="logsQuery.page"
          :page-size="logsQuery.size"
          :total="logsTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="handleLogSizeChange"
          @current-change="handleLogPageChange"
        />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.print-layout {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.print-side {
  width: 268px;
  flex: 0 0 268px;
}

.print-side-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.print-side-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 80px;
}

.print-report-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 8px 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.print-report-item:hover {
  border-color: #409eff;
}

.print-report-item.is-active {
  border-color: #409eff;
  background-color: #ecf5ff;
}

.print-report-item.is-disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background-color: #fafafa;
}

.print-report-icon {
  font-size: 18px;
  line-height: 1.2;
}

.print-report-main {
  flex: 1;
  min-width: 0;
}

.print-report-name {
  font-size: 13px;
  font-weight: 600;
}

.print-report-desc {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
  word-break: break-all;
}

.print-report-tags {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.print-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.print-preview {
  min-height: 200px;
}

.tm-cert-page .tm-print-section-title {
  text-align: center;
}
</style>
