<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock, Rank, Unlock } from '@element-plus/icons-vue'
import {
  addHeatMember,
  assignLanes,
  generateFieldOrders,
  generateHeats,
  getSchedule,
  listHeatMembers,
  listHeats,
  listSessions,
  lockHeat,
  moveHeatMember,
  pageSchedule,
  removeHeatMember,
  reorderFieldOrders,
  swapLanes,
  updateLane,
  type FieldOrder,
  type Heat,
  type HeatMember,
  type Lane,
  type ScheduleSession,
  type ScheduleVO
} from '@/api/schedule'
import { pageAthletes, type Athlete } from '@/api/athlete'
import { listVenues, type Venue } from '@/api/meet'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 分组分道：竞赛单元 → 日程条目 → 分组卡片（拖拽换组）→ 道次网格（分配/交换/拖拽换道）→ 田赛出场顺序。
 * 权限编码：schedule:heat:list / schedule:heat:generate / schedule:heat:edit / schedule:heat:lock
 *          schedule:lane:assign / schedule:lane:edit
 *          schedule:field:generate / schedule:field:edit
 */
defineOptions({ name: 'HeatLane' })

/** 后端实体列名为 is_locked，接口投影可能返回 locked 或 isLocked，这里两种都兼容 */
interface HeatRow extends Heat {
  isLocked?: number
}

interface HeatMemberRow extends HeatMember {
  isLocked?: number
  seedResultRaw?: string
  seedResultUnit?: string
}

interface LaneRow extends Lane {
  isLocked?: number
}

interface FieldOrderRow extends FieldOrder {
  bestSeedRaw?: string
  bestSeedUnit?: string
  isPass?: number
  isScratch?: number
  passCount?: number
  orderMode?: string
}

/** GET /schedule/{id}「日程详情（含分组分道）」的可能结构（顶层或分组内嵌套，两种都兼容） */
interface DetailHeat extends HeatRow {
  lanes?: LaneRow[]
  fieldOrders?: FieldOrderRow[]
}

interface ScheduleDetail extends ScheduleVO {
  heats?: DetailHeat[]
  lanes?: LaneRow[]
  fieldOrders?: FieldOrderRow[]
}

/** 运动员列表可能附带报名 ID（后端联表投影），用于「添加成员」 */
interface AthleteRow extends Athlete {
  registrationId?: number
}

interface LaneCell {
  laneNo: number
  lane: LaneRow | null
  member: HeatMemberRow | null
}

interface LaneGridRow {
  heat: HeatRow
  cells: LaneCell[]
}

interface LanePick {
  heatId: number
  laneNo: number
  laneId: number
}

interface MemberDrag {
  memberId: number
  fromHeatId: number
}

const meetStore = useMeetStore()
const userStore = useUserStore()

const PERIOD_LABELS: Record<string, string> = {
  MORNING: '上午',
  AFTERNOON: '下午',
  EVENING: '晚上'
}

const EVENT_TYPE_LABELS: Record<string, string> = {
  TRACK: '径赛',
  FIELD: '田赛',
  RELAY: '接力',
  ALLROUND: '全能'
}

const DRAW_MODE_OPTIONS = [
  { label: '蛇形分组（SERPENT）', value: 'SERPENT' },
  { label: '按成绩段（SEED）', value: 'SEED' },
  { label: '随机抽签（RANDOM）', value: 'RANDOM' },
  { label: '按单位（UNIT）', value: 'UNIT' }
]

const LANE_MODE_OPTIONS = [
  { label: '按成绩（SEED）', value: 'SEED' },
  { label: '抽签（DRAW）', value: 'DRAW' },
  { label: '顺序（ORDER）', value: 'ORDER' }
]

const FIELD_MODE_OPTIONS = [
  { label: '随机（RANDOM）', value: 'RANDOM' },
  { label: '按成绩（SEED）', value: 'SEED' },
  { label: '按单位（UNIT）', value: 'UNIT' },
  { label: '按号码（BIB）', value: 'BIB' }
]

/** 道次优先顺序（中间道次优先）兜底值 */
const DEFAULT_LANE_ORDER: Record<number, number[]> = {
  6: [3, 4, 2, 5, 1, 6],
  8: [4, 5, 3, 6, 2, 7, 1, 8]
}

/* ------------------------------- 权限 ------------------------------- */

const canList = computed(() => userStore.hasPerm('schedule:heat:list'))
const canGenerate = computed(() => userStore.hasPerm('schedule:heat:generate'))
const canEditHeat = computed(() => userStore.hasPerm('schedule:heat:edit'))
const canLockHeat = computed(() => userStore.hasPerm('schedule:heat:lock'))
const canAssignLane = computed(() => userStore.hasPerm('schedule:lane:assign'))
const canEditLane = computed(() => userStore.hasPerm('schedule:lane:edit'))
const canFieldGenerate = computed(() => userStore.hasPerm('schedule:field:generate'))
const canFieldEdit = computed(() => userStore.hasPerm('schedule:field:edit'))

const meetReady = computed(() => !!meetStore.currentMeetId)

/* ------------------------------- 顶部选择链路 ------------------------------- */

const sessions = ref<ScheduleSession[]>([])
const sessionsLoading = ref(false)
const sessionId = ref<number | undefined>(undefined)

const schedules = ref<ScheduleVO[]>([])
const schedulesLoading = ref(false)
const selectedScheduleId = ref<number | undefined>(undefined)

const venues = ref<Venue[]>([])

const currentSchedule = computed(() => schedules.value.find((item) => item.id === selectedScheduleId.value))

const currentSession = computed(() => sessions.value.find((item) => item.id === sessionId.value))

const eventTypeValue = computed(() => currentSchedule.value?.eventType || '')
/** 项目类型缺失时同时展示径赛/田赛两个区块，避免功能被隐藏 */
const isTrackEvent = computed(() => !eventTypeValue.value || eventTypeValue.value === 'TRACK' || eventTypeValue.value === 'RELAY')
const isFieldEvent = computed(() => !eventTypeValue.value || eventTypeValue.value === 'FIELD')
const isRelayEvent = computed(() => eventTypeValue.value === 'RELAY')

const venueOfSchedule = computed<Venue | undefined>(() => {
  const venueId = currentSchedule.value?.venueId
  if (!venueId) {
    return undefined
  }
  return venues.value.find((item) => item.id === venueId)
})

function periodLabel(period?: string): string {
  if (!period) {
    return '-'
  }
  return PERIOD_LABELS[period] || period
}

function eventTypeText(type?: string): string {
  if (!type) {
    return '未知'
  }
  return EVENT_TYPE_LABELS[type] || type
}

function sessionLabel(item: ScheduleSession): string {
  const parts: string[] = []
  if (item.sessionDate) {
    parts.push(item.sessionDate)
  }
  if (item.period) {
    parts.push(periodLabel(item.period))
  }
  parts.push(item.sessionName || `单元 ${item.id ?? ''}`)
  return parts.join(' ')
}

function roundText(row?: ScheduleVO): string {
  if (!row) {
    return '-'
  }
  return row.roundName || row.roundCode || '-'
}

function scheduleTimeText(row?: ScheduleVO): string {
  if (!row) {
    return '-'
  }
  const start = row.planStartTime || '-'
  const end = row.planEndTime
  return end ? `${start} ~ ${end}` : start
}

function scheduleLabel(row: ScheduleVO): string {
  const name = row.eventName || `项目 ${row.eventId ?? ''}`
  const round = roundText(row)
  const time = row.planStartTime || row.sessionDate || ''
  return `${name} ${round} ${time}`.trim()
}

function unitNameOf(unitId?: number): string {
  if (!unitId) {
    return '-'
  }
  const hit = meetStore.units.find((item) => item.id === unitId)
  return hit?.unitName || '-'
}

function venueNameOf(venueId?: number): string {
  if (!venueId) {
    return '-'
  }
  const hit = venues.value.find((item) => item.id === venueId)
  return hit?.venueName || '-'
}

async function loadSessions() {
  if (!meetStore.currentMeetId || !canList.value) {
    sessions.value = []
    sessionId.value = undefined
    return
  }
  sessionsLoading.value = true
  try {
    const page = await listSessions(meetStore.currentMeetId)
    sessions.value = page?.records || []
    sessionId.value = sessions.value.length > 0 ? sessions.value[0].id : undefined
  } catch {
    sessions.value = []
    sessionId.value = undefined
  } finally {
    sessionsLoading.value = false
  }
}

async function loadVenues() {
  if (!meetStore.currentMeetId) {
    venues.value = []
    return
  }
  try {
    venues.value = (await listVenues({ meetId: meetStore.currentMeetId })) || []
  } catch {
    venues.value = []
  }
}

async function loadSchedules() {
  // 切换单元先清空旧日程与旧分组数据，避免错位展示
  selectedScheduleId.value = undefined
  heats.value = []
  membersMap.value = {}
  laneRecords.value = []
  fieldOrdersMap.value = {}
  selectedLaneA.value = null
  if (!sessionId.value || !meetStore.currentMeetId) {
    schedules.value = []
    return
  }
  schedulesLoading.value = true
  try {
    const page = await pageSchedule({
      meetId: meetStore.currentMeetId,
      sessionId: sessionId.value,
      page: 1,
      size: 200
    })
    schedules.value = page?.records || []
    selectedScheduleId.value = schedules.value.length > 0 ? schedules.value[0].id : undefined
  } catch {
    schedules.value = []
    selectedScheduleId.value = undefined
  } finally {
    schedulesLoading.value = false
  }
}

/* ------------------------------- 分组 / 成员 ------------------------------- */

const heats = ref<HeatRow[]>([])
const membersMap = ref<Record<number, HeatMemberRow[]>>({})
const heatsLoading = ref(false)

/** 道次记录：接口未提供「按日程查道次」的列表接口，来源为 ①日程详情 ②道次分配接口返回值 */
const laneRecords = ref<LaneRow[]>([])
/** 田赛出场顺序：来源为 ①生成接口返回值 ②日程详情 */
const fieldOrdersMap = ref<Record<number, FieldOrderRow[]>>({})

const totalMemberCount = computed(() => heats.value.reduce((sum, heat) => sum + membersOf(heat.id).length, 0))
const lockedHeatCount = computed(() => heats.value.filter((heat) => isHeatLocked(heat)).length)

function isHeatLocked(heat?: HeatRow): boolean {
  if (!heat) {
    return false
  }
  return heat.isLocked === 1
}

function isLaneLocked(lane?: LaneRow): boolean {
  if (!lane) {
    return false
  }
  return lane.isLocked === 1
}

function membersOf(heatId?: number): HeatMemberRow[] {
  if (typeof heatId !== 'number') {
    return []
  }
  return membersMap.value[heatId] || []
}

function fieldOrdersOf(heatId?: number): FieldOrderRow[] {
  if (typeof heatId !== 'number') {
    return []
  }
  return fieldOrdersMap.value[heatId] || []
}

function memberOfHeat(heatId: number | undefined, heatMemberId?: number): HeatMemberRow | undefined {
  if (typeof heatMemberId !== 'number') {
    return undefined
  }
  return membersOf(heatId).find((item) => item.id === heatMemberId)
}

function seedText(member: HeatMemberRow): string {
  return member.seedResultRaw || member.seedResult || '-'
}

function bestSeedText(order: FieldOrderRow, member?: HeatMemberRow): string {
  if (order.bestSeedRaw) {
    return order.bestSeedRaw
  }
  if (typeof order.bestSeed === 'number') {
    return String(order.bestSeed)
  }
  if (member) {
    return seedText(member)
  }
  return '-'
}

/** 道数：优先取分组道数，其次场地道数，兜底 8 道 */
const laneCount = computed(() => {
  const heatMax = heats.value.reduce((max, heat) => Math.max(max, heat.laneCount ?? 0), 0)
  if (heatMax > 0) {
    return heatMax
  }
  const venueCount = venueOfSchedule.value?.laneCount
  if (venueCount && venueCount > 0) {
    return venueCount
  }
  return 8
})

const laneNumbers = computed<number[]>(() => Array.from({ length: laneCount.value }, (_item, index) => index + 1))

function parseLaneOrderNumbers(json?: string): number[] | null {
  if (!json) {
    return null
  }
  try {
    const parsed: unknown = JSON.parse(json)
    if (Array.isArray(parsed) && parsed.length > 0 && parsed.every((item) => typeof item === 'number')) {
      return parsed as number[]
    }
    return null
  } catch {
    return null
  }
}

const laneOrderNumbers = computed<number[]>(() => {
  const parsed = parseLaneOrderNumbers(venueOfSchedule.value?.laneOrderJson)
  if (parsed) {
    return parsed
  }
  const fallback = DEFAULT_LANE_ORDER[laneCount.value]
  return fallback || laneNumbers.value
})

const laneOrderText = computed(() => (laneOrderNumbers.value.length ? laneOrderNumbers.value.join('、') : '-'))

async function loadAllMembers() {
  const map: Record<number, HeatMemberRow[]> = {}
  await Promise.all(
    heats.value.map(async (heat) => {
      const heatId = heat.id
      if (typeof heatId !== 'number') {
        return
      }
      try {
        const list = await listHeatMembers(heatId)
        map[heatId] = (list || []).slice().sort((a, b) => (a.orderNo ?? 0) - (b.orderNo ?? 0))
      } catch {
        map[heatId] = []
      }
    })
  )
  membersMap.value = map
}

function groupFieldOrders(list: FieldOrderRow[]): Record<number, FieldOrderRow[]> {
  const map: Record<number, FieldOrderRow[]> = {}
  list.forEach((item) => {
    const heatId = item.heatId
    if (typeof heatId !== 'number') {
      return
    }
    if (!map[heatId]) {
      map[heatId] = []
    }
    map[heatId].push(item)
  })
  Object.keys(map).forEach((key) => {
    const heatId = Number(key)
    map[heatId].sort((a, b) => (a.orderNo ?? 0) - (b.orderNo ?? 0))
  })
  return map
}

/**
 * 日程详情（文档说明「含分组分道」）作为道次 / 田赛顺序的补充来源；
 * 接口未返回或结构不含分道信息时静默降级，保持本地缓存不覆盖。
 */
async function loadDetailSnapshot() {
  if (typeof selectedScheduleId.value !== 'number') {
    return
  }
  try {
    const detail = (await getSchedule(selectedScheduleId.value)) as ScheduleDetail | undefined
    if (!detail) {
      return
    }
    const lanes: LaneRow[] = []
    if (Array.isArray(detail.lanes)) {
      lanes.push(...detail.lanes)
    }
    if (Array.isArray(detail.heats)) {
      detail.heats.forEach((heat) => {
        if (Array.isArray(heat.lanes)) {
          lanes.push(...heat.lanes)
        }
      })
    }
    if (lanes.length > 0) {
      laneRecords.value = lanes
    }

    const orders: FieldOrderRow[] = []
    if (Array.isArray(detail.fieldOrders)) {
      orders.push(...detail.fieldOrders)
    }
    if (Array.isArray(detail.heats)) {
      detail.heats.forEach((heat) => {
        if (Array.isArray(heat.fieldOrders)) {
          orders.push(...heat.fieldOrders)
        }
      })
    }
    if (orders.length > 0) {
      fieldOrdersMap.value = groupFieldOrders(orders)
    }
  } catch {
    // 日程详情不可用时忽略：道次由「道次分配」返回值补齐，田赛顺序由「生成出场顺序」返回值补齐
  }
}

/** 刷新当前日程的分组 + 成员 + 分道/顺序快照（所有写操作后统一调用） */
async function refreshAll() {
  if (!canList.value || typeof selectedScheduleId.value !== 'number') {
    heats.value = []
    membersMap.value = {}
    return
  }
  heatsLoading.value = true
  try {
    const list = await listHeats(selectedScheduleId.value)
    heats.value = (list || []).slice().sort((a, b) => (a.heatNo ?? 0) - (b.heatNo ?? 0))
    await Promise.all([loadAllMembers(), loadDetailSnapshot()])
  } catch {
    heats.value = []
    membersMap.value = {}
  } finally {
    heatsLoading.value = false
  }
}

function ensureSchedule(): boolean {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶部选择届次')
    return false
  }
  if (typeof selectedScheduleId.value !== 'number') {
    ElMessage.warning('请先选择日程条目')
    return false
  }
  return true
}

/* ------------------------------- 生成分组分道 ------------------------------- */

const genVisible = ref(false)
const genSubmitting = ref(false)
const genForm = reactive({
  drawMode: 'SERPENT',
  heatCount: undefined as number | undefined,
  heatSize: undefined as number | undefined,
  avoidSameUnit: true,
  laneMode: 'SEED'
})

function openGenerateDialog() {
  if (!ensureSchedule()) {
    return
  }
  genForm.drawMode = 'SERPENT'
  genForm.heatCount = undefined
  genForm.heatSize = laneCount.value
  genForm.avoidSameUnit = true
  genForm.laneMode = isFieldEvent.value ? 'ORDER' : 'SEED'
  genVisible.value = true
}

function clearHeatCount() {
  genForm.heatCount = undefined
}

function clearHeatSize() {
  genForm.heatSize = undefined
}

async function submitGenerate() {
  if (!ensureSchedule() || typeof selectedScheduleId.value !== 'number') {
    return
  }
  const scheduleId = selectedScheduleId.value
  try {
    await ElMessageBox.confirm('重新生成将按新参数覆盖当前分组的成员安排，已锁定的分组会保留。确认继续吗？', '生成确认', {
      type: 'warning',
      confirmButtonText: '确认生成',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  genSubmitting.value = true
  try {
    const result = await generateHeats({
      scheduleId,
      drawMode: genForm.drawMode,
      heatCount: genForm.heatCount,
      heatSize: genForm.heatSize,
      avoidSameUnit: genForm.avoidSameUnit,
      laneMode: genForm.laneMode
    })
    // 重新生成会重建分组与道次记录，旧的道次 id 全部失效，先清空再刷新
    laneRecords.value = []
    fieldOrdersMap.value = {}
    selectedLaneA.value = null
    genVisible.value = false
    await refreshAll()
    if (result && Array.isArray(result.lanes) && result.lanes.length > 0) {
      mergeLaneRecords(result.lanes as LaneRow[])
    }
    ElMessage.success('分组分道已生成')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    genSubmitting.value = false
  }
}

/* ------------------------------- 锁定分组 ------------------------------- */

async function handleLockHeat(heat: HeatRow) {
  const heatId = heat.id
  if (typeof heatId !== 'number') {
    return
  }
  const next = !isHeatLocked(heat)
  if (next) {
    try {
      await ElMessageBox.confirm(
        `锁定「第 ${heat.heatNo} 组」后，该组不会被后续重新生成覆盖，确认锁定吗？`,
        '锁定确认',
        { type: 'warning', confirmButtonText: '确认锁定', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }
  heatsLoading.value = true
  try {
    await lockHeat(heatId, next)
    heat.isLocked = next ? 1 : 0
    ElMessage.success(next ? '分组已锁定' : '分组已解锁')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    heatsLoading.value = false
  }
}

/* ------------------------------- 拖拽换组 ------------------------------- */

const dragMember = ref<MemberDrag | null>(null)
const dragOverHeatId = ref<number | undefined>(undefined)
const movingMember = ref(false)

function isMemberDraggable(heat: HeatRow, member: HeatMemberRow): boolean {
  return (
    canEditHeat.value &&
    !isHeatLocked(heat) &&
    typeof heat.id === 'number' &&
    typeof member.id === 'number'
  )
}

function onMemberDragStart(event: DragEvent, heat: HeatRow, member: HeatMemberRow) {
  if (!isMemberDraggable(heat, member) || typeof heat.id !== 'number' || typeof member.id !== 'number') {
    event.preventDefault()
    return
  }
  dragMember.value = { memberId: member.id, fromHeatId: heat.id }
  dragOverHeatId.value = undefined
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(member.id))
  }
}

function onMemberDragEnd() {
  dragMember.value = null
  dragOverHeatId.value = undefined
}

function onCardDragOver(event: DragEvent, heat: HeatRow) {
  const state = dragMember.value
  if (!state || typeof heat.id !== 'number' || state.fromHeatId === heat.id) {
    return
  }
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
  dragOverHeatId.value = heat.id
}

function onCardDragLeave(heat: HeatRow) {
  if (dragOverHeatId.value === heat.id) {
    dragOverHeatId.value = undefined
  }
}

/** 拖到另一张分组卡片：把成员移动到目标组末尾（targetOrderNo = 目标组人数 + 1） */
async function onCardDrop(event: DragEvent, heat: HeatRow) {
  event.preventDefault()
  const state = dragMember.value
  const targetHeatId = heat.id
  dragMember.value = null
  dragOverHeatId.value = undefined
  if (!state || typeof targetHeatId !== 'number' || state.fromHeatId === targetHeatId) {
    return
  }
  if (isHeatLocked(heat)) {
    ElMessage.warning('目标分组已锁定，请先解锁')
    return
  }
  movingMember.value = true
  try {
    await moveHeatMember({
      memberId: state.memberId,
      targetHeatId,
      targetOrderNo: membersOf(targetHeatId).length + 1
    })
    ElMessage.success(`已移动到第 ${heat.heatNo} 组`)
    await refreshAll()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    movingMember.value = false
  }
}

/* ------------------------------- 成员移出 / 添加 ------------------------------- */

async function handleRemoveMember(heat: HeatRow, member: HeatMemberRow) {
  const heatId = heat.id
  const memberId = member.id
  if (typeof heatId !== 'number' || typeof memberId !== 'number') {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认把「${member.athleteName || member.bibNo || '该运动员'}」移出第 ${heat.heatNo} 组吗？`,
      '移出确认',
      { type: 'warning', confirmButtonText: '确认移出', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  heatsLoading.value = true
  try {
    await removeHeatMember(heatId, memberId)
    ElMessage.success('已移出该成员')
    await refreshAll()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    heatsLoading.value = false
  }
}

const addVisible = ref(false)
const addSubmitting = ref(false)
const addTargetHeat = ref<HeatRow | null>(null)
const athleteLoading = ref(false)
const athleteKeyword = ref('')
const athleteOptions = ref<AthleteRow[]>([])
const selectedAthleteId = ref<number | undefined>(undefined)
const manualRegistrationId = ref<number | undefined>(undefined)

const selectedAthlete = computed(() => athleteOptions.value.find((item) => item.id === selectedAthleteId.value))

/** 表单可直接使用的报名 ID：运动员列表带 registrationId 时自动带出 */
const resolvedRegistrationId = computed<number | undefined>(
  () => selectedAthlete.value?.registrationId ?? manualRegistrationId.value
)

async function searchAthletes() {
  if (!meetStore.currentMeetId) {
    athleteOptions.value = []
    return
  }
  athleteLoading.value = true
  try {
    const page = await pageAthletes({
      meetId: meetStore.currentMeetId,
      keyword: athleteKeyword.value.trim() || undefined,
      page: 1,
      size: 50
    })
    athleteOptions.value = (page?.records || []) as AthleteRow[]
  } catch {
    athleteOptions.value = []
  } finally {
    athleteLoading.value = false
  }
}

function openAddMember(heat: HeatRow) {
  if (!ensureSchedule()) {
    return
  }
  if (isHeatLocked(heat)) {
    ElMessage.warning('该分组已锁定，请先解锁')
    return
  }
  addTargetHeat.value = heat
  athleteKeyword.value = ''
  selectedAthleteId.value = undefined
  manualRegistrationId.value = undefined
  athleteOptions.value = []
  addVisible.value = true
  searchAthletes()
}

function athleteLabel(item: AthleteRow): string {
  const bib = item.bibNo ? `号码 ${item.bibNo}` : '无号码'
  const unit = item.unitName || unitNameOf(item.unitId)
  return `${item.athleteName}（${bib}／${unit}）`
}

async function submitAddMember() {
  const heatId = addTargetHeat.value?.id
  if (typeof heatId !== 'number') {
    return
  }
  const registrationId = resolvedRegistrationId.value
  if (typeof registrationId !== 'number' || registrationId <= 0) {
    ElMessage.warning('请选择运动员，或手工填写该运动员在本届次本项目下的报名 ID')
    return
  }
  addSubmitting.value = true
  try {
    await addHeatMember(heatId, { registrationId })
    ElMessage.success('成员已添加')
    addVisible.value = false
    await refreshAll()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    addSubmitting.value = false
  }
}

/* ------------------------------- 道次：分配 / 换道 / 交换 ------------------------------- */

const laneAssigning = ref<'' | 'SEED' | 'DRAW' | 'ORDER'>('')
const laneSwapping = ref(false)
const selectedLaneA = ref<LanePick | null>(null)

function laneModeLabel(mode: string): string {
  return LANE_MODE_OPTIONS.find((item) => item.value === mode)?.label || mode
}

function mergeLaneRecords(list: LaneRow[]) {
  const map = new Map<number, LaneRow>()
  laneRecords.value.forEach((item) => {
    if (typeof item.id === 'number') {
      map.set(item.id, item)
    }
  })
  list.forEach((item) => {
    if (typeof item.id === 'number') {
      map.set(item.id, item)
    }
  })
  laneRecords.value = Array.from(map.values())
}

function findLaneByMember(heatMemberId?: number): LaneRow | undefined {
  if (typeof heatMemberId !== 'number') {
    return undefined
  }
  return laneRecords.value.find((item) => item.heatMemberId === heatMemberId)
}

/** 按「分组 + 道次号」找道次记录：接口未回传 heatId 时用该组成员 id 兜底匹配 */
function findLaneByHeatNo(heatId: number, laneNo: number): LaneRow | undefined {
  const heatMembers = membersOf(heatId)
  return laneRecords.value.find(
    (item) =>
      item.laneNo === laneNo &&
      (item.heatId === heatId || heatMembers.some((member) => member.id === item.heatMemberId))
  )
}

const laneGrid = computed<LaneGridRow[]>(() =>
  heats.value.map((heat) => {
    const heatId = heat.id
    const heatMembers = membersOf(heatId)
    return {
      heat,
      cells: laneNumbers.value.map((laneNo) => {
        const member = heatMembers.find((item) => item.laneNo === laneNo) || null
        let lane: LaneRow | null = null
        if (typeof heatId === 'number') {
          lane = findLaneByHeatNo(heatId, laneNo) || null
        }
        if (!lane && member) {
          lane = findLaneByMember(member.id) || null
        }
        return { laneNo, lane, member }
      })
    }
  })
)

function isCellSelected(heat: HeatRow, cell: LaneCell): boolean {
  const current = selectedLaneA.value
  return !!current && current.heatId === heat.id && current.laneNo === cell.laneNo
}

function isCellDragOver(heat: HeatRow, cell: LaneCell): boolean {
  return dragOverLaneKey.value === `${heat.id ?? ''}-${cell.laneNo}`
}

function patchMemberLaneNo(heatId: number, heatMemberId: number, laneNo: number) {
  const list = membersMap.value[heatId]
  if (!list) {
    return
  }
  membersMap.value = {
    ...membersMap.value,
    [heatId]: list.map((item) => (item.id === heatMemberId ? { ...item, laneNo } : item))
  }
}

/** 本地乐观更新道次号：接口不返回分道详情时，界面仍与刚提交的结果一致 */
function applyLocalLaneNo(laneId: number, laneNo: number, heatMemberId?: number) {
  laneRecords.value = laneRecords.value.map((item) => (item.id === laneId ? { ...item, laneNo } : item))
  if (typeof heatMemberId !== 'number') {
    return
  }
  heats.value.forEach((heat) => {
    const heatId = heat.id
    if (typeof heatId !== 'number') {
      return
    }
    if (membersOf(heatId).some((member) => member.id === heatMemberId)) {
      patchMemberLaneNo(heatId, heatMemberId, laneNo)
    }
  })
}

function applyLocalSwap(first: LaneRow, second: LaneRow) {
  const firstNo = first.laneNo
  const secondNo = second.laneNo
  if (typeof first.id === 'number') {
    applyLocalLaneNo(first.id, secondNo, first.heatMemberId)
  }
  if (typeof second.id === 'number') {
    applyLocalLaneNo(second.id, firstNo, second.heatMemberId)
  }
}

async function handleAssignLanes(mode: 'SEED' | 'DRAW' | 'ORDER') {
  if (!ensureSchedule()) {
    return
  }
  const heatIds = heats.value.map((heat) => heat.id).filter((id): id is number => typeof id === 'number')
  if (heatIds.length === 0) {
    ElMessage.warning('请先生成分组')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认按「${laneModeLabel(mode)}」为 ${heatIds.length} 个分组重新分配道次吗？已锁定的道次会保留。`,
      '道次分配确认',
      { type: 'warning', confirmButtonText: '确认分配', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  laneAssigning.value = mode
  try {
    const lanes = await assignLanes({ heatIds, mode })
    selectedLaneA.value = null
    await Promise.all([loadAllMembers(), loadDetailSnapshot()])
    if (Array.isArray(lanes) && lanes.length > 0) {
      mergeLaneRecords(lanes as LaneRow[])
    }
    ElMessage.success('道次分配完成')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    laneAssigning.value = ''
  }
}

/** 交换两个道次记录（内部复用 swapLanes） */
async function doSwapLanes(first: LaneRow, second: LaneRow) {
  if (typeof first.id !== 'number' || typeof second.id !== 'number') {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认交换「第 ${first.laneNo} 道」与「第 ${second.laneNo} 道」吗？`,
      '交换道次确认',
      { type: 'warning', confirmButtonText: '确认交换', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  laneSwapping.value = true
  try {
    await swapLanes({ laneIdA: first.id, laneIdB: second.id })
    applyLocalSwap(first, second)
    selectedLaneA.value = null
    ElMessage.success('道次已交换')
    await refreshAll()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    laneSwapping.value = false
  }
}

/** 「选择 A → 选择 B」式交换：第一次点击选中，第二次点击执行交换 */
async function pickLaneForSwap(heat: HeatRow, cell: LaneCell) {
  if (!canEditLane.value) {
    ElMessage.warning('无道次调整权限（schedule:lane:edit）')
    return
  }
  if (isHeatLocked(heat)) {
    ElMessage.warning('该分组已锁定，请先解锁')
    return
  }
  if (typeof heat.id !== 'number') {
    return
  }
  const lane = cell.lane
  if (!lane || typeof lane.id !== 'number') {
    ElMessage.warning('该道次暂无分配记录，请先执行道次分配')
    return
  }
  const current = selectedLaneA.value
  if (!current) {
    selectedLaneA.value = { heatId: heat.id, laneNo: cell.laneNo, laneId: lane.id }
    ElMessage.info(`已选择 A：第 ${cell.laneNo} 道，请再选择 B 道次`)
    return
  }
  if (current.heatId === heat.id && current.laneNo === cell.laneNo) {
    selectedLaneA.value = null
    return
  }
  if (current.heatId !== heat.id) {
    ElMessage.warning('请在同一分组内选择两个道次进行交换')
    return
  }
  const first = laneRecords.value.find((item) => item.id === current.laneId)
  if (!first) {
    selectedLaneA.value = null
    ElMessage.warning('道次记录已过期，请刷新后重试')
    return
  }
  await doSwapLanes(first, lane)
}

/* 道次单元格拖拽换道 */
const dragLane = ref<LanePick | null>(null)
const dragOverLaneKey = ref('')

function onLaneDragStart(event: DragEvent, heat: HeatRow, cell: LaneCell) {
  const lane = cell.lane
  if (!canEditLane.value || isHeatLocked(heat) || !lane || typeof lane.id !== 'number' || typeof heat.id !== 'number') {
    event.preventDefault()
    return
  }
  dragLane.value = { heatId: heat.id, laneNo: cell.laneNo, laneId: lane.id }
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(lane.id))
  }
}

function onLaneDragOver(event: DragEvent, heat: HeatRow, cell: LaneCell) {
  const state = dragLane.value
  if (!state || typeof heat.id !== 'number' || state.heatId !== heat.id || state.laneNo === cell.laneNo) {
    return
  }
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
  dragOverLaneKey.value = `${heat.id}-${cell.laneNo}`
}

function onLaneDragEnd() {
  dragLane.value = null
  dragOverLaneKey.value = ''
}

async function onLaneDrop(event: DragEvent, heat: HeatRow, cell: LaneCell) {
  event.preventDefault()
  const state = dragLane.value
  dragLane.value = null
  dragOverLaneKey.value = ''
  if (!state || typeof heat.id !== 'number' || state.heatId !== heat.id || state.laneNo === cell.laneNo) {
    return
  }
  const targetLane = cell.lane
  if (!targetLane || typeof targetLane.id !== 'number') {
    ElMessage.warning('目标道次暂无分配记录，请使用「换道」按钮调整')
    return
  }
  if (isLaneLocked(targetLane)) {
    ElMessage.warning('目标道次已锁定，请先解锁')
    return
  }
  const first = laneRecords.value.find((item) => item.id === state.laneId)
  if (!first) {
    ElMessage.warning('道次记录已过期，请刷新后重试')
    return
  }
  await doSwapLanes(first, targetLane)
}

/* 行内「换道」对话框 */
const laneDialogVisible = ref(false)
const laneSubmitting = ref(false)
const laneTargetNo = ref<number | undefined>(undefined)
const laneEditingHeat = ref<HeatRow | null>(null)
const laneEditingMember = ref<HeatMemberRow | null>(null)

function openLaneDialog(heat: HeatRow, member: HeatMemberRow) {
  const lane = findLaneByMember(member.id)
  if (!lane || typeof lane.id !== 'number') {
    ElMessage.warning('未找到该运动员的道次记录，请先执行道次分配后再换道')
    return
  }
  if (isLaneLocked(lane)) {
    ElMessage.warning('该道次已锁定，请先解锁')
    return
  }
  laneEditingHeat.value = heat
  laneEditingMember.value = member
  laneTargetNo.value = member.laneNo ?? lane.laneNo
  laneDialogVisible.value = true
}

async function submitLaneChange() {
  const heatId = laneEditingHeat.value?.id
  const member = laneEditingMember.value
  const lane = findLaneByMember(member?.id)
  const target = laneTargetNo.value
  if (typeof heatId !== 'number' || !member || !lane || typeof lane.id !== 'number') {
    ElMessage.warning('道次记录缺失，无法调整')
    return
  }
  if (typeof target !== 'number' || target < 1 || target > laneCount.value) {
    ElMessage.warning(`请输入 1 ~ ${laneCount.value} 之间的道次号`)
    return
  }
  if (target === lane.laneNo) {
    laneDialogVisible.value = false
    return
  }
  const occupied = findLaneByHeatNo(heatId, target)
  if (occupied && typeof occupied.id === 'number' && occupied.id !== lane.id) {
    laneDialogVisible.value = false
    ElMessage.warning(`第 ${target} 道已被占用，可改用「交换道次」完成调整`)
    await doSwapLanes(lane, occupied)
    return
  }
  laneSubmitting.value = true
  try {
    await updateLane(lane.id, target)
    applyLocalLaneNo(lane.id, target, lane.heatMemberId)
    laneDialogVisible.value = false
    ElMessage.success('道次已调整')
    await refreshAll()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    laneSubmitting.value = false
  }
}

/* ------------------------------- 田赛出场顺序 ------------------------------- */

const fieldMode = ref('SEED')
const fieldGenerating = ref(false)
const fieldSavingHeatId = ref<number | undefined>(undefined)

function fieldModeLabel(mode: string): string {
  return FIELD_MODE_OPTIONS.find((item) => item.value === mode)?.label || mode
}

async function handleGenerateFieldOrders() {
  if (!ensureSchedule() || typeof selectedScheduleId.value !== 'number') {
    return
  }
  const scheduleId = selectedScheduleId.value
  try {
    await ElMessageBox.confirm(
      `确认按「${fieldModeLabel(fieldMode.value)}」重新生成田赛出场顺序吗？已锁定的顺序会保留。`,
      '出场顺序生成确认',
      { type: 'warning', confirmButtonText: '确认生成', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  fieldGenerating.value = true
  try {
    const orders = await generateFieldOrders({ scheduleId, mode: fieldMode.value })
    if (Array.isArray(orders) && orders.length > 0) {
      fieldOrdersMap.value = groupFieldOrders(orders as FieldOrderRow[])
    } else {
      await loadDetailSnapshot()
    }
    ElMessage.success('出场顺序已生成')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    fieldGenerating.value = false
  }
}

/** 把该组完整成员 id 数组按新顺序提交 */
async function submitFieldOrder(heatId: number) {
  const list = fieldOrdersMap.value[heatId]
  if (!list || list.length === 0) {
    return
  }
  const memberIds = list.map((item) => item.heatMemberId)
  if (memberIds.some((id) => typeof id !== 'number')) {
    ElMessage.warning('出场顺序缺少成员 ID，无法提交调整')
    return
  }
  fieldSavingHeatId.value = heatId
  try {
    await reorderFieldOrders({ heatId, memberIds: memberIds as number[] })
    ElMessage.success('出场顺序已更新')
    await loadDetailSnapshot()
  } catch {
    await loadDetailSnapshot()
  } finally {
    fieldSavingHeatId.value = undefined
  }
}

async function moveFieldOrderRow(heat: HeatRow, index: number, delta: number) {
  const heatId = heat.id
  if (typeof heatId !== 'number' || !canFieldEdit.value) {
    return
  }
  const list = fieldOrdersMap.value[heatId]
  if (!list || list.length === 0) {
    return
  }
  const target = index + delta
  if (target < 0 || target >= list.length) {
    return
  }
  const next = list.slice()
  const moved = next[index]
  next.splice(index, 1)
  next.splice(target, 0, moved)
  fieldOrdersMap.value = {
    ...fieldOrdersMap.value,
    [heatId]: next.map((item, order) => ({ ...item, orderNo: order + 1 }))
  }
  await submitFieldOrder(heatId)
}

/* ------------------------------- 初始化与联动 ------------------------------- */

function resetAll() {
  sessions.value = []
  sessionId.value = undefined
  schedules.value = []
  selectedScheduleId.value = undefined
  heats.value = []
  membersMap.value = {}
  laneRecords.value = []
  fieldOrdersMap.value = {}
  selectedLaneA.value = null
  dragMember.value = null
  dragOverHeatId.value = undefined
  dragLane.value = null
  dragOverLaneKey.value = ''
}

watch(sessionId, () => {
  loadSchedules()
})

watch(selectedScheduleId, () => {
  laneRecords.value = []
  fieldOrdersMap.value = {}
  selectedLaneA.value = null
  refreshAll()
})

watch(
  () => meetStore.currentMeetId,
  async () => {
    resetAll()
    await Promise.all([loadSessions(), loadVenues()])
  }
)

onMounted(async () => {
  if (!meetStore.loaded) {
    await meetStore.loadMeets().catch(() => undefined)
  }
  if (meetStore.currentMeetId && (!meetStore.units.length || !meetStore.groups.length)) {
    await meetStore.loadGroupsAndUnits().catch(() => undefined)
  }
  await Promise.all([loadSessions(), loadVenues()])
})
</script>

<template>
  <div class="tm-page">
    <div class="tm-card">
      <div class="tm-page-title">分组分道</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜生成分组、拖拽换组、道次分配与交换、田赛出场顺序
      </div>
    </div>

    <el-alert
      v-if="!canList"
      title="当前账号缺少「分组分道查看」权限（schedule:heat:list），页面数据不可加载。"
      type="warning"
      :closable="false"
      show-icon
    />

    <div class="tm-card">
      <div class="tm-search-bar">
        <span>竞赛单元：</span>
        <el-select
          v-model="sessionId"
          :loading="sessionsLoading"
          :disabled="!meetReady"
          placeholder="请选择竞赛单元"
          filterable
          clearable
          style="width: 260px"
        >
          <el-option v-for="item in sessions" :key="item.id" :label="sessionLabel(item)" :value="item.id" />
        </el-select>
        <span>日程条目：</span>
        <el-select
          v-model="selectedScheduleId"
          :loading="schedulesLoading"
          :disabled="!sessionId"
          placeholder="请选择项目轮次"
          filterable
          clearable
          style="width: 360px"
        >
          <el-option v-for="item in schedules" :key="item.id" :label="scheduleLabel(item)" :value="item.id" />
        </el-select>
        <el-button :loading="heatsLoading" :disabled="!selectedScheduleId" @click="refreshAll()">刷新</el-button>
      </div>

      <div v-if="!meetReady" class="tm-form-tip" style="margin-top: 8px">请先在顶部选择届次。</div>
      <div v-else-if="canList && !sessionsLoading && sessions.length === 0" class="tm-form-tip" style="margin-top: 8px">
        当前届次暂无竞赛单元，请先在日程编排中创建竞赛单元。
      </div>
      <div v-else-if="sessionId && !schedulesLoading && schedules.length === 0" class="tm-form-tip" style="margin-top: 8px">
        该竞赛单元暂无日程条目，请先在日程编排中生成日程。
      </div>

      <el-descriptions v-if="currentSchedule" :column="4" border size="small" style="margin-top: 12px">
        <el-descriptions-item label="项目">{{ currentSchedule.eventName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="轮次">{{ roundText(currentSchedule) }}</el-descriptions-item>
        <el-descriptions-item label="项目类型">{{ eventTypeText(currentSchedule.eventType) }}</el-descriptions-item>
        <el-descriptions-item label="是否接力">{{ eventTypeValue ? (isRelayEvent ? '是' : '否') : '-' }}</el-descriptions-item>
        <el-descriptions-item label="场地">{{ currentSchedule.venueName || venueNameOf(currentSchedule.venueId) }}</el-descriptions-item>
        <el-descriptions-item label="计划时间">{{ scheduleTimeText(currentSchedule) }}</el-descriptions-item>
        <el-descriptions-item label="可用道数">{{ laneCount }}</el-descriptions-item>
        <el-descriptions-item label="单元">{{ currentSession?.sessionName || currentSchedule.sessionName || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="currentSchedule && !eventTypeValue" class="tm-form-tip" style="margin-top: 8px">
        日程未返回项目类型（eventType），已同时显示径赛道次与田赛顺序区块。
      </div>
    </div>

    <template v-if="canList">
      <div class="tm-card">
        <div class="tm-toolbar">
          <div class="tm-toolbar-actions">
            <el-button v-if="canGenerate" type="primary" :disabled="!selectedScheduleId" @click="openGenerateDialog">
              生成分组分道
            </el-button>
            <template v-if="isFieldEvent">
              <span class="tm-form-tip">田赛顺序：</span>
              <el-select v-model="fieldMode" :disabled="!selectedScheduleId" style="width: 160px">
                <el-option v-for="item in FIELD_MODE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-button
                v-if="canFieldGenerate"
                :loading="fieldGenerating"
                :disabled="!selectedScheduleId"
                @click="handleGenerateFieldOrders"
              >
                生成出场顺序
              </el-button>
            </template>
          </div>
          <div class="tm-toolbar-actions">
            <el-tag v-if="movingMember" type="warning" size="small">正在移动成员…</el-tag>
            <el-button :loading="heatsLoading" :disabled="!selectedScheduleId" @click="refreshAll()">刷新分组</el-button>
          </div>
        </div>
        <div class="tm-form-tip" style="margin-top: 8px">
          共 {{ heats.length }} 组 / {{ totalMemberCount }} 人{{ lockedHeatCount > 0 ? `，其中 ${lockedHeatCount} 组已锁定` : '' }}。
          锁定的分组在重新生成时会保留；拖拽成员到其它分组卡片即可换组。
        </div>
      </div>

      <div class="tm-card">
        <div class="tm-page-title" style="margin-bottom: 12px">分组卡片</div>
        <div v-loading="heatsLoading">
          <el-empty v-if="heats.length === 0" description="请先生成分组" />
          <div v-else class="heat-grid">
            <el-card
              v-for="heat in heats"
              :key="heat.id"
              class="heat-card"
              :class="{ 'is-drop-target': dragOverHeatId === heat.id }"
              shadow="never"
              @dragover="onCardDragOver($event, heat)"
              @dragleave="onCardDragLeave(heat)"
              @drop="onCardDrop($event, heat)"
            >
              <template #header>
                <div class="heat-card-header">
                  <span class="heat-card-title">第 {{ heat.heatNo }} 组</span>
                  <el-tag size="small" type="info" effect="plain">{{ membersOf(heat.id).length }} 人</el-tag>
                  <el-tag v-if="heat.laneCount" size="small" effect="plain">{{ heat.laneCount }} 道</el-tag>
                  <el-tag v-if="isHeatLocked(heat)" size="small" type="warning">已锁定</el-tag>
                  <span class="heat-card-spacer" />
                  <el-tooltip
                    v-if="canLockHeat"
                    :content="isHeatLocked(heat) ? '解锁该分组' : '锁定该分组（重新生成时保留）'"
                    placement="top"
                  >
                    <el-button link :type="isHeatLocked(heat) ? 'warning' : 'primary'" @click="handleLockHeat(heat)">
                      <el-icon><Lock v-if="isHeatLocked(heat)" /><Unlock v-else /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-icon v-else-if="isHeatLocked(heat)" style="color: #e6a23c"><Lock /></el-icon>
                </div>
              </template>

              <div class="member-list">
                <div
                  v-for="member in membersOf(heat.id)"
                  :key="member.id"
                  class="member-row"
                  :class="{ 'is-draggable': isMemberDraggable(heat, member) }"
                  :draggable="isMemberDraggable(heat, member)"
                  :title="isMemberDraggable(heat, member) ? '拖到其它分组卡片可换组' : '该分组已锁定或无权调整'"
                  @dragstart="onMemberDragStart($event, heat, member)"
                  @dragend="onMemberDragEnd"
                >
                  <span class="member-lane">{{ member.laneNo ?? '-' }}</span>
                  <span class="member-bib">{{ member.bibNo || '-' }}</span>
                  <span class="member-name">{{ member.athleteName || '-' }}</span>
                  <span class="member-unit">{{ member.unitName || unitNameOf(member.unitId) }}</span>
                  <span class="member-seed">{{ seedText(member) }}</span>
                  <span class="member-ops">
                    <el-button v-if="canEditHeat" link type="danger" @click="handleRemoveMember(heat, member)">移出</el-button>
                    <el-button v-if="canEditLane && isTrackEvent" link type="primary" @click="openLaneDialog(heat, member)">
                      换道
                    </el-button>
                  </span>
                </div>
                <div v-if="membersOf(heat.id).length === 0" class="tm-form-tip">本组暂无成员</div>
              </div>

              <template v-if="isFieldEvent">
                <el-divider content-position="left">田赛出场顺序</el-divider>
                <el-table
                  v-if="fieldOrdersOf(heat.id).length > 0"
                  v-loading="fieldSavingHeatId === heat.id"
                  :data="fieldOrdersOf(heat.id)"
                  size="small"
                  border
                >
                  <el-table-column prop="orderNo" label="顺序" width="60" align="center" />
                  <el-table-column label="号码" width="70" align="center">
                    <template #default="{ row }">
                      {{ memberOfHeat(heat.id, (row as FieldOrderRow).heatMemberId)?.bibNo || '-' }}
                    </template>
                  </el-table-column>
                  <el-table-column label="姓名" min-width="80">
                    <template #default="{ row }">
                      {{ memberOfHeat(heat.id, (row as FieldOrderRow).heatMemberId)?.athleteName || '-' }}
                    </template>
                  </el-table-column>
                  <el-table-column label="单位" min-width="100" show-overflow-tooltip>
                    <template #default="{ row }">
                      {{
                        memberOfHeat(heat.id, (row as FieldOrderRow).heatMemberId)?.unitName ||
                        unitNameOf(memberOfHeat(heat.id, (row as FieldOrderRow).heatMemberId)?.unitId)
                      }}
                    </template>
                  </el-table-column>
                  <el-table-column label="最好种子成绩" width="110" align="center">
                    <template #default="{ row }">
                      {{ bestSeedText(row as FieldOrderRow, memberOfHeat(heat.id, (row as FieldOrderRow).heatMemberId)) }}
                    </template>
                  </el-table-column>
                  <el-table-column label="标记" width="110" align="center">
                    <template #default="{ row }">
                      <el-tag v-if="(row as FieldOrderRow).isScratch === 1" size="small" type="danger" effect="plain">弃权</el-tag>
                      <el-tag
                        v-else-if="(row as FieldOrderRow).isPass === 1"
                        size="small"
                        type="warning"
                        effect="plain"
                      >
                        免跳{{ (row as FieldOrderRow).passCount ? `×${(row as FieldOrderRow).passCount}` : '' }}
                      </el-tag>
                      <span v-else>-</span>
                    </template>
                  </el-table-column>
                  <el-table-column v-if="canFieldEdit" label="调整" width="110" align="center">
                    <template #default="{ $index }">
                      <el-button
                        link
                        type="primary"
                        :disabled="$index === 0 || fieldSavingHeatId === heat.id"
                        @click="moveFieldOrderRow(heat, $index, -1)"
                      >
                        上移
                      </el-button>
                      <el-button
                        link
                        type="primary"
                        :disabled="$index === fieldOrdersOf(heat.id).length - 1 || fieldSavingHeatId === heat.id"
                        @click="moveFieldOrderRow(heat, $index, 1)"
                      >
                        下移
                      </el-button>
                    </template>
                  </el-table-column>
                  <template #empty>
                    <el-empty description="暂无出场顺序" />
                  </template>
                </el-table>
                <div v-else class="tm-form-tip">该组尚未生成田赛出场顺序，可在上方选择排序方式后点「生成出场顺序」。</div>
              </template>

              <div class="heat-card-footer">
                <el-button v-if="canEditHeat" link type="primary" @click="openAddMember(heat)">添加成员</el-button>
              </div>
            </el-card>
          </div>
        </div>
      </div>

      <div v-if="isTrackEvent && heats.length > 0" class="tm-card">
        <div class="tm-page-title">道次网格</div>
        <div class="tm-toolbar" style="margin: 8px 0 12px">
          <div class="tm-toolbar-actions">
            <el-button
              v-if="canAssignLane"
              :loading="laneAssigning === 'SEED'"
              @click="handleAssignLanes('SEED')"
            >
              按成绩分配道次
            </el-button>
            <el-button v-if="canAssignLane" :loading="laneAssigning === 'DRAW'" @click="handleAssignLanes('DRAW')">
              抽签分配
            </el-button>
            <el-button v-if="canAssignLane" :loading="laneAssigning === 'ORDER'" @click="handleAssignLanes('ORDER')">
              顺序分配
            </el-button>
            <el-button v-if="canEditLane" :disabled="!selectedLaneA" @click="selectedLaneA = null">取消选择</el-button>
          </div>
          <div class="tm-toolbar-actions">
            <el-tag v-if="laneSwapping" type="warning" size="small">正在交换道次…</el-tag>
            <span class="tm-form-tip">
              当前选择：{{ selectedLaneA ? `A = 第 ${selectedLaneA.laneNo} 道` : '未选择' }}
            </span>
          </div>
        </div>
        <div class="tm-form-tip" style="margin-bottom: 12px">
          交换道次：点单元格内「选择 A」后再点另一道的「选择 B」；也可直接把道次单元格拖到目标道次。
          道次优先顺序（中间道次优先）：8 道 4、5、3、6、2、7、1、8；6 道 3、4、2、5、1、6。当前场地顺序：{{ laneOrderText }}。
        </div>
        <div class="lane-grid-wrap">
          <table class="lane-grid">
            <thead>
              <tr>
                <th class="lane-grid-corner">分组</th>
                <th v-for="laneNo in laneNumbers" :key="laneNo">{{ laneNo }} 道</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in laneGrid" :key="row.heat.id">
                <th>第 {{ row.heat.heatNo }} 组</th>
                <td
                  v-for="cell in row.cells"
                  :key="cell.laneNo"
                  class="lane-cell"
                  :class="{
                    'is-selected': isCellSelected(row.heat, cell),
                    'is-drag-over': isCellDragOver(row.heat, cell),
                    'is-draggable': canEditLane && !isHeatLocked(row.heat) && !!cell.lane
                  }"
                  :draggable="canEditLane && !isHeatLocked(row.heat) && !!cell.lane"
                  @dragstart="onLaneDragStart($event, row.heat, cell)"
                  @dragover="onLaneDragOver($event, row.heat, cell)"
                  @dragleave="dragOverLaneKey = ''"
                  @drop="onLaneDrop($event, row.heat, cell)"
                  @dragend="onLaneDragEnd"
                >
                  <template v-if="cell.member">
                    <div class="lane-cell-name">{{ cell.member.athleteName || '-' }}</div>
                    <div class="lane-cell-bib">{{ cell.member.bibNo || '-' }}</div>
                    <el-button
                      v-if="canEditLane"
                      class="lane-cell-op"
                      link
                      type="primary"
                      size="small"
                      @click="pickLaneForSwap(row.heat, cell)"
                    >
                      {{ isCellSelected(row.heat, cell) ? '取消 A' : selectedLaneA ? '选择 B' : '选择 A' }}
                    </el-button>
                  </template>
                  <span v-else class="lane-cell-empty">-</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>

    <!-- 生成分组分道 -->
    <el-dialog v-model="genVisible" title="生成分组分道" width="680px" destroy-on-close>
      <el-alert
        title="重新生成会保留已锁定的分组；未锁定的分组将按下方参数重建，原分组内的道次与顺序会被覆盖。"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />
      <el-form :model="genForm" label-width="120px">
        <el-form-item label="分组方式">
          <el-select v-model="genForm.drawMode" style="width: 100%">
            <el-option v-for="item in DRAW_MODE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <div class="tm-form-tip">
            SERPENT 蛇形（按种子成绩首尾相接分入各组）／SEED 按成绩段（强弱分层成组）／RANDOM 随机抽签／UNIT 按单位集中。
          </div>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="组数">
              <el-input-number v-model="genForm.heatCount" :min="1" :max="99" controls-position="right" style="width: 100%" />
              <div class="tm-form-tip">
                留空表示按每组人数自动计算组数。
                <el-button link type="primary" @click="clearHeatCount">留空</el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="每组人数">
              <el-input-number v-model="genForm.heatSize" :min="1" :max="99" controls-position="right" style="width: 100%" />
              <div class="tm-form-tip">
                留空默认取场地道数（当前 {{ laneCount }}）。
                <el-button link type="primary" @click="clearHeatSize">留空</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="同单位分散">
          <el-switch v-model="genForm.avoidSameUnit" />
          <span class="tm-form-tip" style="margin-left: 8px">同单位运动员尽量分散到不同组</span>
        </el-form-item>
        <el-form-item label="道次分配方式">
          <el-select v-model="genForm.laneMode" style="width: 100%">
            <el-option v-for="item in LANE_MODE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <div class="tm-form-tip">田赛项目不使用道次，该项仅对径赛生效。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button type="primary" :loading="genSubmitting" @click="submitGenerate">确认生成</el-button>
      </template>
    </el-dialog>

    <!-- 添加成员 -->
    <el-dialog v-model="addVisible" title="添加成员到分组" width="720px" destroy-on-close>
      <el-alert
        title="添加成员接口（POST /heats/{id}/members）需要报名 ID（registrationId）。运动员列表接口不返回报名信息，若下方未自动带出报名 ID，请到「报名管理」查到该运动员在本项目下的报名 ID 后手工填写。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />
      <el-form label-width="120px">
        <el-form-item label="目标分组">
          <el-tag type="info" effect="plain">第 {{ addTargetHeat?.heatNo ?? '-' }} 组</el-tag>
        </el-form-item>
        <el-form-item label="选择运动员">
          <div class="athlete-picker">
            <el-input
              v-model="athleteKeyword"
              placeholder="姓名 / 证件号 / 号码"
              clearable
              style="width: 200px"
              @keyup.enter="searchAthletes"
            />
            <el-button :loading="athleteLoading" @click="searchAthletes">搜索</el-button>
            <el-select
              v-model="selectedAthleteId"
              :loading="athleteLoading"
              placeholder="请选择运动员"
              filterable
              clearable
              style="flex: 1"
            >
              <el-option v-for="item in athleteOptions" :key="item.id" :label="athleteLabel(item)" :value="item.id" />
            </el-select>
          </div>
          <div class="tm-form-tip">仅用于选人参考；实际入组以「报名 ID」为准（一个运动员在同一项目只能有一条报名记录）。</div>
        </el-form-item>
        <el-form-item label="报名 ID">
          <el-input-number
            v-model="manualRegistrationId"
            :min="1"
            :max="99999999"
            controls-position="right"
            placeholder="报名记录 ID"
            style="width: 220px"
          />
          <span class="tm-form-tip" style="margin-left: 8px">
            {{ selectedAthlete?.registrationId ? '已从运动员列表自动带出' : '运动员列表未提供报名 ID，请手工填写' }}
          </span>
        </el-form-item>
      </el-form>
      <div class="tm-form-tip">
        最终提交：registrationId = {{ resolvedRegistrationId ?? '未确定' }}。
      </div>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="addSubmitting" @click="submitAddMember">添加</el-button>
      </template>
    </el-dialog>

    <!-- 换道（输入目标道次） -->
    <el-dialog v-model="laneDialogVisible" title="调整道次" width="520px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="运动员">
          <span>{{ laneEditingMember?.athleteName || '-' }}（{{ laneEditingMember?.bibNo || '无号码' }}）</span>
        </el-form-item>
        <el-form-item label="当前分组">
          <span>第 {{ laneEditingHeat?.heatNo ?? '-' }} 组</span>
        </el-form-item>
        <el-form-item label="目标道次">
          <el-input-number v-model="laneTargetNo" :min="1" :max="laneCount" controls-position="right" />
          <span class="tm-form-tip" style="margin-left: 8px">可选 1 ~ {{ laneCount }} 道</span>
        </el-form-item>
      </el-form>
      <div class="tm-form-tip">
        若目标道次已被占用，提交时会提示并转为「交换道次」（swapLanes）；确需占位可先移出该道运动员。
      </div>
      <template #footer>
        <el-button @click="laneDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="laneSubmitting" @click="submitLaneChange">确认调整</el-button>
      </template>
    </el-dialog>

    <div class="tm-card">
      <div class="tm-form-tip">
        <el-icon style="vertical-align: middle"><Rank /></el-icon>
        拖拽说明：分组卡片内的成员行可拖到其它分组卡片换组；道次网格中的单元格可拖到同组其它道次交换。已锁定分组内的行不可拖拽。
      </div>
    </div>
  </div>
</template>

<style scoped>
.heat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 12px;
}

.heat-card {
  border: 1px solid #ebeef5;
}

.heat-card.is-drop-target {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
  background-color: #f5faff;
}

.heat-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.heat-card-title {
  font-weight: 600;
}

.heat-card-spacer {
  flex: 1;
}

.heat-card-footer {
  margin-top: 8px;
  text-align: right;
}

.member-list {
  display: flex;
  flex-direction: column;
}

.member-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  border-bottom: 1px dashed #ebeef5;
  font-size: 13px;
}

.member-row.is-draggable {
  cursor: grab;
}

.member-row.is-draggable:hover {
  background-color: #f5f7fa;
}

.member-row:last-child {
  border-bottom: none;
}

.member-lane {
  flex: 0 0 32px;
  text-align: center;
  font-weight: 600;
  color: #409eff;
}

.member-bib {
  flex: 0 0 56px;
  color: #909399;
}

.member-name {
  flex: 1 1 64px;
  font-weight: 500;
}

.member-unit {
  flex: 1 1 80px;
  color: #606266;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.member-seed {
  flex: 0 0 72px;
  text-align: right;
  color: #909399;
}

.member-ops {
  flex: 0 0 auto;
  white-space: nowrap;
}

.lane-grid-wrap {
  overflow-x: auto;
}

.lane-grid {
  border-collapse: collapse;
  width: 100%;
  min-width: 660px;
}

.lane-grid th,
.lane-grid td {
  border: 1px solid #dcdfe6;
  padding: 4px 6px;
  text-align: center;
  font-size: 12px;
}

.lane-grid thead th {
  background-color: #f5f7fa;
  font-weight: 600;
}

.lane-grid tbody th {
  background-color: #fafafa;
  white-space: nowrap;
  width: 84px;
}

.lane-grid-corner {
  width: 84px;
}

.lane-cell {
  vertical-align: middle;
  min-width: 74px;
  height: 52px;
}

.lane-cell.is-draggable {
  cursor: grab;
}

.lane-cell.is-selected {
  background-color: #ecf5ff;
  box-shadow: inset 0 0 0 2px #409eff;
}

.lane-cell.is-drag-over {
  background-color: #fdf6ec;
  box-shadow: inset 0 0 0 2px #e6a23c;
}

.lane-cell-name {
  font-weight: 600;
  color: #303133;
}

.lane-cell-bib {
  color: #909399;
}

.lane-cell-empty {
  color: #c0c4cc;
}

.lane-cell-op {
  margin-top: 2px;
}

.athlete-picker {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
}
</style>
