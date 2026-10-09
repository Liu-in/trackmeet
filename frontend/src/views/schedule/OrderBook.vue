<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  downloadOrderBook,
  getCallingSheet,
  getHeatSheet,
  getLaneSheet,
  getOrderBook,
  getScheduleSheet,
  type MeetInfo,
  type PrintQuery,
  type SheetBundle
} from '@/api/print'
import { listSessions, type ScheduleSession } from '@/api/schedule'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 秩序册：届次信息头 + 总日程表 / 分组表 / 分道表 / 检录表四张表，
 * 支持 A4 打印（另存为 PDF）与 Excel 导出。
 * 权限编码：print:orderbook（秩序册/总日程/分组/分道）、print:calling（检录表）
 */
defineOptions({ name: 'OrderBook' })

type TabName = 'schedule' | 'heat' | 'lane' | 'calling'

interface TabMeta {
  name: TabName
  label: string
  perm: string
  /** 表格为空时的说明文案 */
  empty: string
  loader: (params: PrintQuery) => Promise<SheetBundle[]>
}

/** 四张表都与 src/api/print.ts 的函数一一对应，切换页签时按需加载并缓存到本地 ref */
const TAB_LIST: TabMeta[] = [
  {
    name: 'schedule',
    label: '总日程表',
    perm: 'print:orderbook',
    empty: '暂无已发布的日程数据，请先在日程编排中发布',
    loader: getScheduleSheet
  },
  {
    name: 'heat',
    label: '分组表',
    perm: 'print:orderbook',
    empty: '暂无已发布的分组数据，请先在分组分道中生成并发布',
    loader: getHeatSheet
  },
  {
    name: 'lane',
    label: '分道表',
    perm: 'print:orderbook',
    empty: '暂无分道数据，请先在分组分道中完成道次分配',
    loader: getLaneSheet
  },
  {
    name: 'calling',
    label: '检录表',
    perm: 'print:calling',
    empty: '暂无可打印的检录表数据',
    loader: getCallingSheet
  }
]

const meetStore = useMeetStore()
const userStore = useUserStore()

const canOrderBook = computed(() => userStore.hasPerm('print:orderbook'))
const canCalling = computed(() => userStore.hasPerm('print:calling'))
const hasAnyPerm = computed(() => canOrderBook.value || canCalling.value)
const meetReady = computed(() => !!meetStore.currentMeetId)

function canAccess(perm: string): boolean {
  return userStore.hasPerm(perm)
}

/* ------------------------------- 筛选 ------------------------------- */

const sessions = ref<ScheduleSession[]>([])
const sessionsLoading = ref(false)
const sessionId = ref<number | undefined>(undefined)
const groupId = ref<number | undefined>(undefined)
/** 是否只含已发布（默认开） */
const publishOnly = ref(true)

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

const currentSessionLabel = computed(() => {
  const hit = sessions.value.find((item) => item.id === sessionId.value)
  return hit ? sessionLabel(hit) : '全部单元'
})

const currentGroupLabel = computed(() => {
  const hit = meetStore.groups.find((item) => item.id === groupId.value)
  return hit ? hit.groupName : '全部组别'
})

function buildQuery(): PrintQuery {
  return {
    meetId: meetStore.currentMeetId,
    sessionId: sessionId.value,
    groupId: groupId.value,
    publishOnly: publishOnly.value
  }
}

/* ------------------------------- 届次信息头 ------------------------------- */

const meetInfo = ref<MeetInfo | null>(null)
const meetInfoLoading = ref(false)
const meetInfoLoaded = ref(false)

const dateRangeText = computed(() => {
  const start = meetInfo.value?.startDate
  const end = meetInfo.value?.endDate
  if (start && end) {
    return `${start} ~ ${end}`
  }
  return start || end || '-'
})

/** 打印时每张表顶部的副标题：届次 + 单元 + 组别 + 发布范围 */
const printSubtitle = computed(() => {
  const parts: string[] = []
  if (meetInfo.value?.edition) {
    parts.push(meetInfo.value.edition)
  }
  parts.push(`单元：${currentSessionLabel.value}`)
  parts.push(`组别：${currentGroupLabel.value}`)
  parts.push(publishOnly.value ? '仅已发布' : '含草稿')
  return parts.join('｜')
})

async function loadMeetInfo() {
  if (!canOrderBook.value || !meetStore.currentMeetId) {
    meetInfo.value = null
    meetInfoLoaded.value = false
    return
  }
  meetInfoLoading.value = true
  try {
    const book = await getOrderBook({ meetId: meetStore.currentMeetId, publishOnly: publishOnly.value })
    meetInfo.value = book?.meetInfo || null
    meetInfoLoaded.value = true
  } catch {
    meetInfo.value = null
  } finally {
    meetInfoLoading.value = false
  }
}

/* ------------------------------- 四张表（按需加载 + 缓存） ------------------------------- */

const activeTab = ref<TabName>('schedule')

const sheets = reactive<Record<TabName, SheetBundle[] | null>>({
  schedule: null,
  heat: null,
  lane: null,
  calling: null
})

const tabLoading = reactive<Record<TabName, boolean>>({
  schedule: false,
  heat: false,
  lane: false,
  calling: false
})

const allLoading = ref(false)
const downloading = ref(false)

function tabMeta(name: TabName): TabMeta {
  return TAB_LIST.find((item) => item.name === name) || TAB_LIST[0]
}

function tabAccessible(name: TabName): boolean {
  return canAccess(tabMeta(name).perm)
}

function sheetList(name: TabName): SheetBundle[] {
  return sheets[name] || []
}

const loadedRowCount = computed(() =>
  TAB_LIST.reduce(
    (sum, tab) => sum + sheetList(tab.name).reduce((inner, bundle) => inner + (bundle.rows?.length || 0), 0),
    0
  )
)

const loadedSheetCount = computed(() => TAB_LIST.reduce((sum, tab) => sum + sheetList(tab.name).length, 0))

/** 按需加载单张表；force=true 时强制重新请求（缓存命中直接返回） */
async function loadTab(name: TabName, force = false) {
  const meta = tabMeta(name)
  if (!canAccess(meta.perm)) {
    sheets[name] = []
    return
  }
  if (!meetStore.currentMeetId) {
    sheets[name] = []
    return
  }
  if (sheets[name] !== null && !force) {
    return
  }
  tabLoading[name] = true
  try {
    const list = await meta.loader(buildQuery())
    sheets[name] = Array.isArray(list) ? list : []
  } catch {
    sheets[name] = []
  } finally {
    tabLoading[name] = false
  }
}

function resetSheets() {
  sheets.schedule = null
  sheets.heat = null
  sheets.lane = null
  sheets.calling = null
}

async function loadAllTabs(force = true) {
  if (!meetStore.currentMeetId) {
    ElMessage.warning('请先在顶部选择届次')
    return
  }
  allLoading.value = true
  try {
    await Promise.all(TAB_LIST.filter((tab) => canAccess(tab.perm)).map((tab) => loadTab(tab.name, force)))
  } finally {
    allLoading.value = false
  }
}

/** 刷新：重取届次信息 + 清空缓存 + 重新加载当前页签 */
async function handleRefresh() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶部选择届次')
    return
  }
  resetSheets()
  await loadMeetInfo()
  await loadTab(activeTab.value, true)
}

function handleTabChange(name: string | number) {
  const target = String(name) as TabName
  if (!tabAccessible(target)) {
    return
  }
  activeTab.value = target
  loadTab(target)
}

/* ------------------------------- 打印 / 导出 ------------------------------- */

/** 打印前把所有有权限的表都加载出来，避免只打印当前页签 */
async function handlePrint() {
  if (!hasAnyPerm.value) {
    ElMessage.warning('当前账号无秩序册打印权限')
    return
  }
  if (!meetReady.value) {
    ElMessage.warning('请先在顶部选择届次')
    return
  }
  await loadAllTabs(false)
  window.print()
}

async function handleDownloadExcel() {
  if (!canOrderBook.value) {
    ElMessage.warning('当前账号无秩序册导出权限（print:orderbook）')
    return
  }
  if (!meetReady.value) {
    ElMessage.warning('请先在顶部选择届次')
    return
  }
  downloading.value = true
  try {
    await downloadOrderBook(buildQuery(), '秩序册.xlsx')
    ElMessage.success('已开始下载：秩序册.xlsx')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    downloading.value = false
  }
}

/* ------------------------------- 初始化与联动 ------------------------------- */

async function loadSessions() {
  if (!meetStore.currentMeetId) {
    sessions.value = []
    sessionId.value = undefined
    return
  }
  sessionsLoading.value = true
  try {
    const page = await listSessions(meetStore.currentMeetId)
    sessions.value = page?.records || []
    sessionId.value = undefined
  } catch {
    sessions.value = []
    sessionId.value = undefined
  } finally {
    sessionsLoading.value = false
  }
}

async function reloadAll() {
  resetSheets()
  await Promise.all([loadSessions(), loadMeetInfo()])
  await loadTab(activeTab.value, true)
}

// 筛选条件变化后自动重载（同时保留「刷新」按钮）
watch([sessionId, groupId, publishOnly], () => {
  if (!meetReady.value || !hasAnyPerm.value) {
    return
  }
  resetSheets()
  loadTab(activeTab.value, true)
})

watch(
  () => meetStore.currentMeetId,
  async () => {
    groupId.value = undefined
    activeTab.value = canOrderBook.value ? 'schedule' : 'calling'
    await reloadAll()
  }
)

onMounted(async () => {
  if (!meetStore.loaded) {
    await meetStore.loadMeets().catch(() => undefined)
  }
  if (meetStore.currentMeetId && (!meetStore.groups.length || !meetStore.units.length)) {
    await meetStore.loadGroupsAndUnits().catch(() => undefined)
  }
  activeTab.value = canOrderBook.value ? 'schedule' : 'calling'
  await reloadAll()
})
</script>

<template>
  <div class="tm-page order-book-page">
    <div class="tm-card">
      <div class="tm-page-title">秩序册</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜在线预览总日程表、分组表、分道表与检录表，可直接 A4 打印（另存为 PDF）或导出 Excel
      </div>
    </div>

    <el-alert
      v-if="!hasAnyPerm"
      title="当前账号缺少「秩序册」或「检录表」打印权限（print:orderbook / print:calling）。"
      type="warning"
      :closable="false"
      show-icon
    />

    <!-- 筛选栏（打印时隐藏） -->
    <div class="tm-card no-print">
      <div class="tm-search-bar">
        <span>竞赛单元：</span>
        <el-select
          v-model="sessionId"
          :loading="sessionsLoading"
          :disabled="!meetReady"
          placeholder="全部单元"
          filterable
          clearable
          style="width: 260px"
        >
          <el-option v-for="item in sessions" :key="item.id" :label="sessionLabel(item)" :value="item.id" />
        </el-select>
        <span>组别：</span>
        <el-select v-model="groupId" :disabled="!meetReady" placeholder="全部组别" filterable clearable style="width: 180px">
          <el-option v-for="item in meetStore.groups" :key="item.id" :label="item.groupName" :value="item.id" />
        </el-select>
        <span>只含已发布：</span>
        <el-switch v-model="publishOnly" />
        <el-button
          type="primary"
          :loading="meetInfoLoading"
          :disabled="!meetReady || !hasAnyPerm"
          @click="handleRefresh"
        >
          刷新
        </el-button>
      </div>

      <div class="tm-toolbar" style="margin-top: 12px">
        <div class="tm-toolbar-actions">
          <el-button type="primary" :disabled="!meetReady || !hasAnyPerm" @click="handlePrint">打印 / 另存为 PDF</el-button>
          <el-button
            v-if="canOrderBook"
            :loading="downloading"
            :disabled="!meetReady"
            @click="handleDownloadExcel"
          >
            下载 Excel
          </el-button>
          <el-button :loading="allLoading" :disabled="!meetReady || !hasAnyPerm" @click="loadAllTabs(true)">全部加载</el-button>
        </div>
        <div class="tm-toolbar-actions">
          <span class="tm-form-tip">已加载 {{ loadedSheetCount }} 张表 / {{ loadedRowCount }} 行</span>
        </div>
      </div>
      <div class="tm-form-tip" style="margin-top: 8px">
        未选择届次时无法加载；打印时筛选栏与按钮会自动隐藏，每张表独占分页并重复表头，打印对话框中建议关闭「页眉页脚」。
      </div>
    </div>

    <!-- 届次信息头 -->
    <div v-loading="meetInfoLoading" class="tm-card">
      <div class="tm-page-title" style="margin-bottom: 8px">届次信息</div>
      <el-descriptions v-if="meetInfo" :column="3" border size="small">
        <el-descriptions-item label="届次名称">{{ meetInfo.meetName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="届数/版本">{{ meetInfo.edition || '-' }}</el-descriptions-item>
        <el-descriptions-item label="简称">{{ meetInfo.shortName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主办">{{ meetInfo.host || '-' }}</el-descriptions-item>
        <el-descriptions-item label="承办">{{ meetInfo.organizer || '-' }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ dateRangeText }}</el-descriptions-item>
        <el-descriptions-item label="地点" :span="3">{{ meetInfo.place || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty
        v-else
        :description="meetInfoLoaded ? '暂无届次信息，请先在运动会管理中完善届次资料' : '暂无已发布的日程数据，请先在日程编排中发布'"
      />
    </div>

    <!-- 四张表 -->
    <div class="tm-card">
      <el-tabs v-model="activeTab" type="border-card" @tab-change="handleTabChange">
        <el-tab-pane
          v-for="tab in TAB_LIST"
          :key="tab.name"
          :label="tab.label"
          :name="tab.name"
          :disabled="!canAccess(tab.perm)"
          lazy
        >
          <div v-loading="tabLoading[tab.name]" class="sheet-pane">
            <template v-if="sheetList(tab.name).length > 0">
              <div v-for="bundle in sheetList(tab.name)" :key="bundle.sheetKey" class="sheet-block">
                <!-- 打印专用表头：每张表顶部出现届次信息 -->
                <div class="print-only sheet-print-header">
                  <div class="sheet-print-title">{{ meetInfo?.meetName || '秩序册' }}</div>
                  <div class="sheet-print-sub">{{ printSubtitle }}</div>
                </div>

                <div class="sheet-title">{{ bundle.sheetName || tab.label }}</div>

                <table class="sheet-table">
                  <thead>
                    <tr>
                      <th v-for="(header, headerIndex) in bundle.headers" :key="headerIndex">{{ header }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, rowIndex) in bundle.rows" :key="rowIndex">
                      <td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td>
                    </tr>
                    <tr v-if="!bundle.rows || bundle.rows.length === 0">
                      <td :colspan="bundle.headers.length || 1" class="sheet-table-empty">暂无数据</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </template>
            <el-empty v-else :description="tab.empty" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
.print-only {
  display: none;
}

.sheet-pane {
  min-height: 200px;
}

.sheet-block {
  margin-bottom: 24px;
}

.sheet-block:last-child {
  margin-bottom: 0;
}

.sheet-title {
  font-size: 15px;
  font-weight: 600;
  text-align: center;
  margin: 4px 0 8px;
}

.sheet-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.sheet-table th,
.sheet-table td {
  border: 1px solid #dcdfe6;
  padding: 4px 6px;
  text-align: center;
  word-break: break-all;
}

.sheet-table thead th {
  background-color: #f5f7fa;
  font-weight: 600;
  color: #303133;
}

.sheet-table-empty {
  color: #909399;
}

/* A4 打印：隐藏交互元素、表格不跨页断裂、页边距与表头加粗 */
@media print {
  @page {
    size: A4;
    margin: 12mm;
  }

  .no-print {
    display: none !important;
  }

  .order-book-page {
    gap: 0;
  }

  .tm-card {
    box-shadow: none;
    padding: 0;
    margin: 0;
    border-radius: 0;
    background-color: #fff;
  }

  .print-only {
    display: block;
  }

  .sheet-print-header {
    text-align: center;
    margin-bottom: 6px;
  }

  .sheet-print-title {
    font-size: 16px;
    font-weight: 700;
  }

  .sheet-print-sub {
    font-size: 11px;
    color: #000;
  }

  .sheet-block {
    break-inside: avoid;
    page-break-inside: avoid;
    margin-bottom: 0;
  }

  .sheet-block + .sheet-block {
    break-before: page;
    page-break-before: always;
  }

  .sheet-table {
    font-size: 11px;
  }

  .sheet-table th,
  .sheet-table td {
    border: 1px solid #000;
    padding: 2px 4px;
  }

  .sheet-table thead th {
    background-color: transparent !important;
    font-weight: 700;
    color: #000;
  }

  .sheet-table thead {
    display: table-header-group;
  }

  .sheet-table tr {
    break-inside: avoid;
    page-break-inside: avoid;
  }

  :deep(.el-tabs__header),
  :deep(.el-loading-mask) {
    display: none !important;
  }

  :deep(.el-descriptions__label),
  :deep(.el-descriptions__cell) {
    background-color: transparent !important;
    color: #000 !important;
  }
}
</style>
