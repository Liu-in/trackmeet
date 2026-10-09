<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listRounds, pageEvents, type EventRound, type SportEvent } from '@/api/event'
import { pageResults } from '@/api/result'
import { downloadResultBook, getResultSheet } from '@/api/standing'
import type { SheetBundle } from '@/api/print'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 成绩公告：按 项目 / 轮次 / 组别 / 单位 生成公告表（每张 = 项目 × 轮次），
 * 在线预览 A4 版式（原生 table 渲染 SheetBundle.headers / rows），支持打印另存 PDF 与下载 Excel。
 * 权限编码：result:list（生成与预览）、print:results（打印/导出）
 */
defineOptions({ name: 'ResultNotice' })

const meetStore = useMeetStore()
const userStore = useUserStore()

/** 任一权限码命中即视为有权限（兼容菜单栏 result:notice:list 与通用 result:list） */
function hasAnyPerm(...codes: string[]): boolean {
  return codes.some((code) => userStore.hasPerm(code))
}

const canList = computed(() => hasAnyPerm('result:list', 'result:notice:list'))
const canPrint = computed(() => hasAnyPerm('print:results', 'result:notice:export'))
const meetReady = computed(() => !!meetStore.currentMeetId)

/* ------------------------------- 筛选条件 ------------------------------- */

const query = reactive({
  eventId: undefined as number | undefined,
  roundId: undefined as number | undefined,
  groupId: undefined as number | undefined,
  unitId: undefined as number | undefined
})

const eventList = ref<SportEvent[]>([])
const roundList = ref<EventRound[]>([])
const eventsLoading = ref(false)
const roundsLoading = ref(false)

const selectedEventName = computed(() => {
  const hit = eventList.value.find((item) => item.id === query.eventId)
  return hit?.eventName || '全部项目'
})

const selectedGroupName = computed(() => {
  if (!query.groupId) {
    return '全部组别'
  }
  return meetStore.groups.find((item) => item.id === query.groupId)?.groupName || String(query.groupId)
})

const selectedUnitName = computed(() => {
  if (!query.unitId) {
    return '全部单位'
  }
  return meetStore.units.find((item) => item.id === query.unitId)?.unitName || String(query.unitId)
})

const selectedRoundName = computed(() => {
  if (!query.roundId) {
    return '全部轮次'
  }
  return roundList.value.find((item) => item.id === query.roundId)?.roundName || String(query.roundId)
})

/* ------------------------------- 公告数据 ------------------------------- */

const loading = ref(false)
const downloading = ref(false)
const sheets = ref<SheetBundle[]>([])
/** 已审核（APPROVED）成绩条数，用于顶部说明 */
const approvedCount = ref(0)
/** 是否已执行过「生成公告」，用于区分「未生成」与「无数据」 */
const generated = ref(false)
const activeNames = ref<string[]>([])
/** 打印日期（届次未填日期时用于表头） */
const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })

const meetName = computed(() => meetStore.currentMeet?.meetName || '田径运动会')

const meetDateRange = computed(() => {
  const start = meetStore.currentMeet?.startDate || ''
  const end = meetStore.currentMeet?.endDate || ''
  if (start && end) {
    return `${start} ~ ${end}`
  }
  return start || end || today
})

/** 轮次筛选在前端按表名/表键匹配（后端 SheetBundle 已按项目×轮次切分） */
function sheetMatchesRound(bundle: SheetBundle, round: EventRound): boolean {
  const text = `${bundle.sheetKey || ''} ${bundle.sheetName || ''}`.toUpperCase()
  if (round.roundCode && text.includes(round.roundCode.toUpperCase())) {
    return true
  }
  if (round.roundName && text.includes(round.roundName.toUpperCase())) {
    return true
  }
  return false
}

const visibleSheets = computed(() => {
  if (!query.roundId) {
    return sheets.value
  }
  const round = roundList.value.find((item) => item.id === query.roundId)
  if (!round) {
    return sheets.value
  }
  return sheets.value.filter((bundle) => sheetMatchesRound(bundle, round))
})

const emptyText = computed(() => {
  if (!generated.value) {
    return '请先选择项目/组别等条件，点击「生成公告」'
  }
  if (query.roundId && sheets.value.length > 0 && visibleSheets.value.length === 0) {
    return '当前轮次在本届次公告表中没有匹配的表格，请切换轮次或选择「全部轮次」'
  }
  return '暂无已审核成绩，请先在成绩审核中通过成绩'
})

const roundFilterTip = computed(() => {
  if (!query.roundId || sheets.value.length === 0) {
    return ''
  }
  return `轮次筛选为在线预览过滤：本届次共 ${sheets.value.length} 张表，当前匹配 ${visibleSheets.value.length} 张（按表名/轮次编码匹配）`
})

const totalRowCount = computed(() =>
  visibleSheets.value.reduce((sum, bundle) => sum + (bundle.rows?.length || 0), 0)
)

/* ------------------------------- 数据加载 ------------------------------- */

async function loadEvents() {
  if (!meetStore.currentMeetId) {
    eventList.value = []
    return
  }
  eventsLoading.value = true
  try {
    const page = await pageEvents({ page: 1, size: 200, meetId: meetStore.currentMeetId })
    eventList.value = page.records || []
  } catch {
    eventList.value = []
  } finally {
    eventsLoading.value = false
  }
}

async function loadRounds(eventId?: number) {
  if (!eventId) {
    roundList.value = []
    query.roundId = undefined
    return
  }
  roundsLoading.value = true
  try {
    const list = await listRounds(eventId)
    roundList.value = Array.isArray(list) ? list : []
  } catch {
    roundList.value = []
  } finally {
    query.roundId = undefined
    roundsLoading.value = false
  }
}

/** 统计已审核成绩条数（用于顶部说明，失败不影响公告生成） */
async function loadApprovedCount() {
  approvedCount.value = 0
  if (!meetStore.currentMeetId) {
    return
  }
  try {
    const page = await pageResults({
      page: 1,
      size: 1,
      meetId: meetStore.currentMeetId,
      eventId: query.eventId,
      unitId: query.unitId,
      auditStatus: 'APPROVED'
    })
    approvedCount.value = page?.total || 0
  } catch {
    approvedCount.value = 0
  }
}

/** 生成公告：拉取 SheetBundle[] 并默认展开全部表格 */
async function handleGenerate() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canList.value) {
    ElMessage.warning('当前账号无成绩公告查看权限（result:list / result:notice:list）')
    return
  }
  loading.value = true
  try {
    const list = await getResultSheet({
      meetId: meetStore.currentMeetId,
      eventId: query.eventId,
      groupId: query.groupId,
      unitId: query.unitId
    })
    sheets.value = Array.isArray(list) ? list : []
    generated.value = true
    activeNames.value = sheets.value.map((bundle, index) => sheetKeyOf(bundle, index))
    if (!sheets.value.length) {
      ElMessage.warning('暂无已审核成绩，请先在成绩审核中通过成绩')
    } else {
      ElMessage.success(`已生成 ${sheets.value.length} 张公告表`)
    }
    await loadApprovedCount()
  } catch {
    sheets.value = []
    generated.value = true
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

function handleReset() {
  query.eventId = undefined
  query.roundId = undefined
  query.groupId = undefined
  query.unitId = undefined
  roundList.value = []
  sheets.value = []
  approvedCount.value = 0
  generated.value = false
  activeNames.value = []
}

async function handleEventChange(eventId?: number) {
  sheets.value = []
  generated.value = false
  activeNames.value = []
  await loadRounds(eventId)
}

function sheetKeyOf(bundle: SheetBundle, index: number): string {
  return `${index}-${bundle.sheetKey || bundle.sheetName || 'sheet'}`
}

function expandAll() {
  activeNames.value = visibleSheets.value.map((bundle, index) => sheetKeyOf(bundle, index))
}

function collapseAll() {
  activeNames.value = []
}

/* ------------------------------- 打印 / 导出 ------------------------------- */

async function handlePrint() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canPrint.value) {
    ElMessage.warning('当前账号无成绩公告打印权限（print:results / result:notice:export）')
    return
  }
  if (!generated.value) {
    await handleGenerate()
    if (!sheets.value.length) {
      return
    }
  }
  // 打印前展开全部表格，保证所有表格都进入打印流
  expandAll()
  await nextTick()
  window.print()
}

async function handleDownloadExcel() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canPrint.value) {
    ElMessage.warning('当前账号无成绩公告导出权限（print:results / result:notice:export）')
    return
  }
  downloading.value = true
  try {
    await downloadResultBook(
      { meetId: meetStore.currentMeetId, eventId: query.eventId, groupId: query.groupId },
      `${meetName.value}-成绩册.xlsx`
    )
    ElMessage.success('已开始下载成绩册')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    downloading.value = false
  }
}

/* ------------------------------- 生命周期 ------------------------------- */

onMounted(async () => {
  if (!meetStore.loaded) {
    await meetStore.loadMeets().catch(() => undefined)
  }
  if (meetStore.currentMeetId && (!meetStore.groups.length || !meetStore.units.length)) {
    await meetStore.loadGroupsAndUnits().catch(() => undefined)
  }
  await loadEvents()
})
</script>

<template>
  <div class="tm-page result-notice-page">
    <div class="tm-card no-print">
      <div class="tm-page-title">成绩公告</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜已审核成绩 {{ approvedCount }} 条｜公告表
        {{ visibleSheets.length }} 张 / {{ totalRowCount }} 行
      </div>
    </div>

    <el-alert
      v-if="!canList"
      class="no-print"
      title="当前账号缺少成绩公告查看权限（result:list / result:notice:list），无法生成公告。"
      type="warning"
      :closable="false"
      show-icon
    />

    <!-- 筛选栏（打印时隐藏） -->
    <div class="tm-card no-print">
      <div class="tm-search-bar">
        <span>项目：</span>
        <el-select
          v-model="query.eventId"
          :loading="eventsLoading"
          :disabled="!meetReady"
          placeholder="全部项目"
          filterable
          clearable
          style="width: 220px"
          @change="handleEventChange"
        >
          <el-option v-for="item in eventList" :key="item.id" :label="item.eventName" :value="item.id as number" />
        </el-select>

        <span>轮次：</span>
        <el-select
          v-model="query.roundId"
          :loading="roundsLoading"
          :disabled="!meetReady || !query.eventId"
          placeholder="全部轮次"
          filterable
          clearable
          style="width: 160px"
        >
          <el-option v-for="item in roundList" :key="item.id" :label="item.roundName" :value="item.id as number" />
        </el-select>

        <span>组别：</span>
        <el-select
          v-model="query.groupId"
          :disabled="!meetReady"
          placeholder="全部组别"
          filterable
          clearable
          style="width: 160px"
        >
          <el-option v-for="item in meetStore.groups" :key="item.id" :label="item.groupName" :value="item.id as number" />
        </el-select>

        <span>单位：</span>
        <el-select
          v-model="query.unitId"
          :disabled="!meetReady"
          placeholder="全部单位"
          filterable
          clearable
          style="width: 180px"
        >
          <el-option v-for="item in meetStore.units" :key="item.id" :label="item.unitName" :value="item.id as number" />
        </el-select>

        <el-button type="primary" :loading="loading" :disabled="!meetReady || !canList" @click="handleGenerate">
          生成公告
        </el-button>
        <el-button :disabled="!meetReady" @click="handleReset">重置</el-button>
      </div>

      <div class="tm-toolbar" style="margin-top: 12px">
        <div class="tm-toolbar-actions">
          <el-button type="primary" :disabled="!meetReady || !canPrint" @click="handlePrint">
            打印 / 另存为 PDF
          </el-button>
          <el-button :loading="downloading" :disabled="!meetReady || !canPrint" @click="handleDownloadExcel">
            下载 Excel
          </el-button>
        </div>
        <div class="tm-toolbar-actions">
          <el-button :disabled="!visibleSheets.length" @click="expandAll">全部展开</el-button>
          <el-button :disabled="!visibleSheets.length" @click="collapseAll">全部折叠</el-button>
        </div>
      </div>

      <div class="tm-form-tip" style="margin-top: 8px">
        公告范围：{{ selectedEventName }} / {{ selectedRoundName }} / {{ selectedGroupName }} / {{ selectedUnitName }}。
        {{ roundFilterTip || '未选择届次时无法生成；打印时筛选栏与按钮会自动隐藏，每张表独占分页。' }}
      </div>
    </div>

    <!-- 公告表 -->
    <div v-loading="loading" class="tm-card">
      <template v-if="visibleSheets.length > 0">
        <el-collapse v-model="activeNames">
          <el-collapse-item
            v-for="(bundle, index) in visibleSheets"
            :key="sheetKeyOf(bundle, index)"
            :name="sheetKeyOf(bundle, index)"
          >
            <template #title>
              <span class="sheet-title">{{ bundle.sheetName || '成绩公告表' }}</span>
            </template>

            <div class="sheet-block">
              <!-- 表头：届次 + 项目 + 日期，打印时同样显示 -->
              <div class="sheet-header">
                <div class="sheet-header-main">{{ meetName }}</div>
                <div class="sheet-header-sub">
                  <span>{{ bundle.sheetName || selectedEventName }}</span>
                  <span class="sheet-header-gap">竞赛日期：{{ meetDateRange }}</span>
                </div>
              </div>

              <table class="sheet-table">
                <thead>
                  <tr>
                    <th v-for="(header, headerIndex) in bundle.headers" :key="headerIndex">{{ header }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, rowIndex) in bundle.rows" :key="rowIndex">
                    <td v-for="(cell, cellIndex) in bundle.headers" :key="cellIndex">
                      {{ (row && row[cellIndex] !== undefined && row[cellIndex] !== null ? row[cellIndex] : '-') }}
                    </td>
                  </tr>
                  <tr v-if="!bundle.rows || bundle.rows.length === 0">
                    <td :colspan="bundle.headers.length || 1" class="sheet-table-empty">暂无数据</td>
                  </tr>
                </tbody>
              </table>

              <!-- 签名栏 -->
              <div class="sheet-sign">
                <span>记录员：____________</span>
                <span>裁判长：____________</span>
                <span>技术代表：____________</span>
                <span>公告日期：{{ today }}</span>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </template>

      <el-empty v-else :description="emptyText" />
    </div>
  </div>
</template>

<style scoped>
.sheet-title {
  font-size: 14px;
  font-weight: 600;
}

.sheet-block {
  margin-bottom: 8px;
}

.sheet-header {
  text-align: center;
  margin-bottom: 8px;
}

.sheet-header-main {
  font-size: 16px;
  font-weight: 700;
}

.sheet-header-sub {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
}

.sheet-header-gap {
  margin-left: 16px;
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

.sheet-sign {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  margin-top: 10px;
}

/* A4 打印：隐藏筛选与按钮、表格不跨页断裂、A4 页边距与表格边框 */
@media print {
  @page {
    size: A4;
    margin: 12mm;
  }

  .no-print {
    display: none !important;
  }

  .result-notice-page {
    gap: 0;
  }

  .tm-card {
    box-shadow: none;
    padding: 0;
    margin: 0;
    border-radius: 0;
    background-color: #fff;
  }

  .sheet-block {
    break-inside: avoid;
    page-break-inside: avoid;
  }

  .sheet-block + .sheet-block {
    break-before: page;
    page-break-before: always;
  }

  .sheet-header-main {
    font-size: 16px;
    font-weight: 700;
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

  :deep(.el-collapse-item__header) {
    display: none !important;
  }

  :deep(.el-collapse-item__wrap) {
    display: block !important;
    height: auto !important;
    border-bottom: none;
  }

  :deep(.el-collapse-item__content) {
    display: block !important;
    padding-bottom: 0;
  }

  :deep(.el-loading-mask) {
    display: none !important;
  }
}
</style>
