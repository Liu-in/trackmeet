<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPointsSheet,
  getTeamScores,
  pagePoints,
  recalculateStandings,
  saveStandingSnapshot,
  type PointsRow,
  type StandingOverview,
  type TeamScore
} from '@/api/standing'
import type { SheetBundle } from '@/api/print'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 积分榜：按组别 + 口径（男子/女子/总团体）展示团体名次分、破纪录加分与总分，
 * 点击单位行下钻查看积分明细，支持重算积分、保存快照与 A4 积分表打印导出。
 * 权限编码：standing:points（查看）、standing:recalc（重算）、standing:snapshot（快照）
 */
defineOptions({ name: 'PointsBoard' })

const meetStore = useMeetStore()
const userStore = useUserStore()

const canPoints = computed(() => userStore.hasPerm('standing:points'))
const canRecalc = computed(() => userStore.hasPerm('standing:recalc'))
const canSnapshot = computed(() => userStore.hasPerm('standing:snapshot'))
const meetReady = computed(() => !!meetStore.currentMeetId)

/* ------------------------------- 榜单查询 ------------------------------- */

const groupId = ref<number | undefined>(undefined)
const scoreType = ref<'MALE' | 'FEMALE' | 'TOTAL'>('TOTAL')

const SCORE_TYPE_LABELS: Record<string, string> = {
  MALE: '男子团体',
  FEMALE: '女子团体',
  TOTAL: '总团体'
}

const loading = ref(false)
const recalculating = ref(false)
const snapshotting = ref(false)
const printing = ref(false)

const teamScores = ref<TeamScore[]>([])
const overview = ref<StandingOverview | null>(null)
const snapshotRemark = ref('')

const currentGroupName = computed(() => {
  if (!groupId.value) {
    return '全部组别'
  }
  return meetStore.groups.find((item) => item.id === groupId.value)?.groupName || String(groupId.value)
})

const maxTotalPoint = computed(() =>
  teamScores.value.reduce((max, row) => Math.max(max, Number(row.totalPoint) || 0), 0)
)

const totalPointSum = computed(() =>
  teamScores.value.reduce((sum, row) => sum + (Number(row.totalPoint) || 0), 0)
)

const calcTimeText = computed(() => teamScores.value.find((row) => !!row.calcTime)?.calcTime || '-')

function num(value?: number): number {
  if (value === undefined || value === null || Number.isNaN(Number(value))) {
    return 0
  }
  return Number(value)
}

/** 纯 CSS 条形图宽度（占榜首总分百分比，最小 2% 便于可见） */
function barWidth(row: TeamScore): string {
  const max = maxTotalPoint.value
  if (!max) {
    return '0%'
  }
  const percent = Math.round((num(row.totalPoint) / max) * 100)
  return `${Math.max(percent > 0 ? 2 : 0, percent)}%`
}

async function loadTeamScores() {
  if (!meetStore.currentMeetId) {
    teamScores.value = []
    return
  }
  loading.value = true
  try {
    const list = await getTeamScores({
      meetId: meetStore.currentMeetId,
      groupId: groupId.value,
      scoreType: scoreType.value
    })
    teamScores.value = Array.isArray(list) ? list : []
  } catch {
    teamScores.value = []
  } finally {
    loading.value = false
  }
}

async function handleQuery() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canPoints.value) {
    ElMessage.warning('当前账号无积分榜查看权限（standing:points）')
    return
  }
  closeDrill()
  await loadTeamScores()
  if (!teamScores.value.length) {
    ElMessage.warning('暂无积分数据，请先执行「重算积分」')
  }
}

/* ------------------------------- 下钻明细 ------------------------------- */

const drillVisible = ref(false)
const drillUnit = ref<TeamScore | null>(null)
const pointsRows = ref<PointsRow[]>([])
const pointsLoading = ref(false)
const pointsQuery = reactive({ page: 1, size: 20 })
const pointsTotal = ref(0)

async function loadPoints() {
  if (!meetStore.currentMeetId || !drillUnit.value?.unitId) {
    pointsRows.value = []
    pointsTotal.value = 0
    return
  }
  pointsLoading.value = true
  try {
    const page = await pagePoints({
      page: pointsQuery.page,
      size: pointsQuery.size,
      meetId: meetStore.currentMeetId,
      unitId: drillUnit.value.unitId,
      groupId: groupId.value
    })
    pointsRows.value = page?.records || []
    pointsTotal.value = page?.total || 0
  } catch {
    pointsRows.value = []
    pointsTotal.value = 0
  } finally {
    pointsLoading.value = false
  }
}

async function handleRowClick(row: TeamScore) {
  if (!row.unitId) {
    ElMessage.warning('该行缺少单位信息，无法下钻')
    return
  }
  drillUnit.value = row
  drillVisible.value = true
  pointsQuery.page = 1
  await loadPoints()
}

function closeDrill() {
  drillVisible.value = false
  drillUnit.value = null
  pointsRows.value = []
  pointsTotal.value = 0
  pointsQuery.page = 1
}

function handlePointsPageChange(page: number) {
  pointsQuery.page = page
  loadPoints()
}

function handlePointsSizeChange(size: number) {
  pointsQuery.size = size
  pointsQuery.page = 1
  loadPoints()
}

const drillPointSum = computed(() => pointsRows.value.reduce((sum, row) => sum + num(row.totalPoint), 0))

/* ------------------------------- 重算积分 ------------------------------- */

async function handleRecalculate() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canRecalc.value) {
    ElMessage.warning('当前账号无积分重算权限（standing:recalc）')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认重算「${currentGroupName.value}」的积分吗？将重新计算名次分/破纪录加分/接力双倍并刷新榜单。`,
      '重算积分',
      { type: 'warning', confirmButtonText: '确认重算', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  recalculating.value = true
  try {
    const result = await recalculateStandings({
      meetId: meetStore.currentMeetId,
      groupId: groupId.value
    })
    overview.value = result || null
    await loadTeamScores()
    if (drillUnit.value) {
      await loadPoints()
    }
    ElMessage.success('积分已重算并刷新榜单')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    recalculating.value = false
  }
}

/* ------------------------------- 保存快照 ------------------------------- */

async function handleSnapshot() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canSnapshot.value) {
    ElMessage.warning('当前账号无积分快照权限（standing:snapshot）')
    return
  }
  if (!teamScores.value.length) {
    ElMessage.warning('暂无可快照的积分数据，请先重算积分')
    return
  }
  const defaultValue = `${SCORE_TYPE_LABELS[scoreType.value]} 积分快照 ${new Date().toLocaleString('zh-CN')}`
  let remark = defaultValue
  try {
    const result = await ElMessageBox.prompt('请输入快照说明', '保存快照', {
      confirmButtonText: '保存快照',
      cancelButtonText: '取消',
      inputValue: defaultValue,
      inputPlaceholder: '如：预赛结束 v1 / 决赛前快照',
      inputValidator: (value: string) => {
        const text = (value || '').trim()
        if (!text) {
          return '快照说明不能为空'
        }
        if (text.length > 100) {
          return '快照说明不能超过 100 字'
        }
        return true
      }
    })
    remark = (result.value || '').trim()
  } catch {
    return
  }
  snapshotting.value = true
  try {
    const result = await saveStandingSnapshot({
      meetId: meetStore.currentMeetId,
      scoreType: scoreType.value
    })
    overview.value = result || overview.value
    snapshotRemark.value = remark
    ElMessage.success(`快照已保存：${remark}`)
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    snapshotting.value = false
  }
}

/* ------------------------------- 导出 / 打印积分表 ------------------------------- */

const printSheets = ref<SheetBundle[]>([])
const printFallback = ref(false)

const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })

const meetName = computed(() => meetStore.currentMeet?.meetName || '田径运动会')

async function handleExportPoints() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canPoints.value) {
    ElMessage.warning('当前账号无积分榜查看权限（standing:points）')
    return
  }
  printing.value = true
  try {
    const list = await getPointsSheet({
      meetId: meetStore.currentMeetId,
      groupId: groupId.value
    })
    printSheets.value = Array.isArray(list) ? list : []
  } catch {
    printSheets.value = []
  } finally {
    printing.value = false
  }
  printFallback.value = printSheets.value.length === 0
  if (printFallback.value && !teamScores.value.length) {
    ElMessage.warning('暂无可打印的积分数据')
    return
  }
  await nextTick()
  window.print()
  if (printFallback.value) {
    ElMessage.warning('后端未返回积分打印表，已改用当前榜单表格打印')
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
  await loadTeamScores()
})
</script>

<template>
  <div class="tm-page points-board-page">
    <div class="tm-card no-print">
      <div class="tm-page-title">积分榜</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜{{ currentGroupName }}｜{{
          SCORE_TYPE_LABELS[scoreType]
        }}｜参赛单位 {{ teamScores.length }} 个｜总分合计 {{ totalPointSum }}｜计算时间 {{ calcTimeText }}
      </div>
    </div>

    <el-alert
      v-if="!canPoints"
      class="no-print"
      title="当前账号缺少积分榜查看权限（standing:points）。"
      type="warning"
      :closable="false"
      show-icon
    />

    <!-- 工具栏 -->
    <div class="tm-card no-print">
      <div class="tm-search-bar">
        <span>组别：</span>
        <el-select
          v-model="groupId"
          :disabled="!meetReady"
          placeholder="全部组别"
          filterable
          clearable
          style="width: 180px"
          @change="handleQuery"
        >
          <el-option v-for="item in meetStore.groups" :key="item.id" :label="item.groupName" :value="item.id as number" />
        </el-select>

        <span>口径：</span>
        <el-radio-group v-model="scoreType" :disabled="!meetReady" @change="handleQuery">
          <el-radio-button value="MALE">男子团体</el-radio-button>
          <el-radio-button value="FEMALE">女子团体</el-radio-button>
          <el-radio-button value="TOTAL">总团体</el-radio-button>
        </el-radio-group>

        <el-button :loading="loading" :disabled="!meetReady || !canPoints" @click="handleQuery">查询</el-button>
      </div>

      <div class="tm-toolbar" style="margin-top: 12px">
        <div class="tm-toolbar-actions">
          <el-button
            type="primary"
            :loading="recalculating"
            :disabled="!meetReady || !canRecalc"
            @click="handleRecalculate"
          >
            重算积分
          </el-button>
          <el-button :loading="snapshotting" :disabled="!meetReady || !canSnapshot" @click="handleSnapshot">
            保存快照
          </el-button>
          <el-button :loading="printing" :disabled="!meetReady || !canPoints" @click="handleExportPoints">
            导出积分表
          </el-button>
          <el-button :loading="loading" :disabled="!meetReady || !canPoints" @click="loadTeamScores">刷新</el-button>
        </div>
        <div class="tm-toolbar-actions">
          <span class="tm-form-tip">点击单位行可下钻查看该单位积分明细</span>
        </div>
      </div>

      <div class="tm-form-tip" style="margin-top: 8px">
        重算会重新计算名次分、破纪录加分与接力双倍并刷新榜单；快照接口仅提交届次与口径（快照说明仅用于本次操作确认）。
        <span v-if="snapshotRemark">最近快照说明：{{ snapshotRemark }}</span>
      </div>
    </div>

    <!-- 重算结果概览 -->
    <div v-if="overview" class="tm-stat-grid no-print">
      <div class="tm-stat-card">
        <div class="tm-stat-label">积分明细</div>
        <div class="tm-stat-value">{{ overview.pointRows ?? 0 }}</div>
      </div>
      <div class="tm-stat-card">
        <div class="tm-stat-label">团体榜行数</div>
        <div class="tm-stat-value">{{ overview.teamRows ?? 0 }}</div>
      </div>
      <div class="tm-stat-card">
        <div class="tm-stat-label">奖牌榜行数</div>
        <div class="tm-stat-value">{{ overview.medalRows ?? 0 }}</div>
      </div>
      <div class="tm-stat-card">
        <div class="tm-stat-label">计入项目数</div>
        <div class="tm-stat-value">{{ overview.eventCount ?? 0 }}</div>
      </div>
      <div class="tm-stat-card">
        <div class="tm-stat-label">运动员数</div>
        <div class="tm-stat-value">{{ overview.athleteCount ?? 0 }}</div>
      </div>
      <div class="tm-stat-card">
        <div class="tm-stat-label">计算时间</div>
        <div class="tm-stat-value" style="font-size: 15px">{{ overview.calcTime || '-' }}</div>
      </div>
    </div>

    <el-alert
      v-if="overview && overview.messages && overview.messages.length"
      class="no-print"
      :title="overview.messages.join('；')"
      type="info"
      :closable="false"
      show-icon
    />

    <!-- 团体榜 -->
    <div class="tm-card no-print">
      <el-table
        v-loading="loading"
        :data="teamScores"
        border
        stripe
        highlight-current-row
        row-key="id"
        max-height="520"
        @row-click="handleRowClick"
      >
        <el-table-column prop="rankNo" label="名次" width="80" align="center">
          <template #default="{ row }">
            {{ (row as TeamScore).rankNo ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="unitName" label="单位" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as TeamScore).unitName || (row as TeamScore).unitId || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="组别" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as TeamScore).groupName || currentGroupName }}
          </template>
        </el-table-column>
        <el-table-column prop="rankPointSum" label="名次分合计" width="120" align="center">
          <template #default="{ row }">{{ num((row as TeamScore).rankPointSum) }}</template>
        </el-table-column>
        <el-table-column prop="bonusPointSum" label="破纪录加分" width="120" align="center">
          <template #default="{ row }">{{ num((row as TeamScore).bonusPointSum) }}</template>
        </el-table-column>
        <el-table-column prop="totalPoint" label="总分" width="110" align="center">
          <template #default="{ row }">
            <span class="total-point">{{ num((row as TeamScore).totalPoint) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总分占比" min-width="180">
          <template #default="{ row }">
            <div class="bar-track">
              <div class="bar-fill" :style="{ width: barWidth(row as TeamScore) }" />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="calcTime" label="计算时间" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as TeamScore).calcTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="handleRowClick(row as TeamScore)">积分明细</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无积分数据，请先执行「重算积分」" />
        </template>
      </el-table>
    </div>

    <!-- 下钻：单位积分明细 -->
    <div v-if="drillVisible" class="tm-card no-print">
      <div class="tm-toolbar" style="margin-bottom: 12px">
        <div class="tm-toolbar-actions">
          <span class="tm-page-title">{{ drillUnit?.unitName || '单位' }}｜积分明细</span>
          <el-tag type="info" size="small">{{ SCORE_TYPE_LABELS[scoreType] }}</el-tag>
          <span class="tm-form-tip">共 {{ pointsTotal }} 条｜本页合计 {{ drillPointSum }} 分</span>
        </div>
        <div class="tm-toolbar-actions">
          <el-button :loading="pointsLoading" @click="loadPoints">刷新明细</el-button>
          <el-button @click="closeDrill">收起</el-button>
        </div>
      </div>

      <el-table v-loading="pointsLoading" :data="pointsRows" border stripe size="small" max-height="420">
        <el-table-column prop="eventName" label="项目" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as PointsRow).eventName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="athleteName" label="运动员" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as PointsRow).athleteName || '-' }}
            <span v-if="(row as PointsRow).bibNo" class="tm-form-tip">（{{ (row as PointsRow).bibNo }}）</span>
          </template>
        </el-table-column>
        <el-table-column prop="groupName" label="组别" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as PointsRow).groupName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="rankNo" label="名次" width="80" align="center">
          <template #default="{ row }">{{ (row as PointsRow).rankNo ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="名次分" width="90" align="center">
          <template #default="{ row }">{{ num((row as PointsRow).rankPoint) }}</template>
        </el-table-column>
        <el-table-column label="加分" width="80" align="center">
          <template #default="{ row }">{{ num((row as PointsRow).bonusPoint) }}</template>
        </el-table-column>
        <el-table-column label="接力倍数" width="100" align="center">
          <template #default="{ row }">{{ num((row as PointsRow).relayFactor) || 1 }}</template>
        </el-table-column>
        <el-table-column label="合计" width="90" align="center">
          <template #default="{ row }">
            <span class="total-point">{{ num((row as PointsRow).totalPoint) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="calcTime" label="计算时间" width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as PointsRow).calcTime || '-' }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="该单位暂无积分明细" />
        </template>
      </el-table>

      <div class="tm-pagination">
        <el-pagination
          :current-page="pointsQuery.page"
          :page-size="pointsQuery.size"
          :total="pointsTotal"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handlePointsSizeChange"
          @current-change="handlePointsPageChange"
        />
      </div>
    </div>

    <!-- A4 打印区：优先使用后端积分打印表，缺失时退回榜单表格 -->
    <div class="print-area">
      <template v-if="printSheets.length">
        <div v-for="(bundle, index) in printSheets" :key="`${index}-${bundle.sheetKey}`" class="sheet-block">
          <div class="sheet-header">
            <div class="sheet-header-main">{{ meetName }}</div>
            <div class="sheet-header-sub">
              <span>{{ bundle.sheetName || '积分表' }}</span>
              <span class="sheet-header-gap">{{ SCORE_TYPE_LABELS[scoreType] }}｜{{ currentGroupName }}</span>
              <span class="sheet-header-gap">打印日期：{{ today }}</span>
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
                  {{ row && row[cellIndex] !== undefined && row[cellIndex] !== null ? row[cellIndex] : '-' }}
                </td>
              </tr>
              <tr v-if="!bundle.rows || bundle.rows.length === 0">
                <td :colspan="bundle.headers.length || 1" class="sheet-table-empty">暂无数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <div v-else-if="printFallback && teamScores.length" class="sheet-block">
        <div class="sheet-header">
          <div class="sheet-header-main">{{ meetName }}</div>
          <div class="sheet-header-sub">
            <span>团体总分表</span>
            <span class="sheet-header-gap">{{ SCORE_TYPE_LABELS[scoreType] }}｜{{ currentGroupName }}</span>
            <span class="sheet-header-gap">打印日期：{{ today }}</span>
          </div>
        </div>
        <table class="sheet-table">
          <thead>
            <tr>
              <th>名次</th>
              <th>单位</th>
              <th>名次分合计</th>
              <th>破纪录加分</th>
              <th>总分</th>
              <th>计算时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in teamScores" :key="row.id ?? index">
              <td>{{ row.rankNo ?? '-' }}</td>
              <td>{{ row.unitName || row.unitId || '-' }}</td>
              <td>{{ num(row.rankPointSum) }}</td>
              <td>{{ num(row.bonusPointSum) }}</td>
              <td>{{ num(row.totalPoint) }}</td>
              <td>{{ row.calcTime || '-' }}</td>
            </tr>
          </tbody>
        </table>
        <div class="sheet-sign">
          <span>记录员：____________</span>
          <span>裁判长：____________</span>
          <span>公告日期：{{ today }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.total-point {
  font-weight: 700;
  color: #303133;
}

.bar-track {
  height: 12px;
  background-color: #f0f2f5;
  border-radius: 6px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #66b1ff, #409eff);
  border-radius: 6px;
  transition: width 0.3s ease;
}

/* 打印区在屏幕上同样作为 A4 预览显示 */
.print-area {
  margin-top: 0;
}

.sheet-block {
  background-color: #fff;
  border-radius: 4px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
  margin-bottom: 12px;
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

@media print {
  @page {
    size: A4;
    margin: 12mm;
  }

  .no-print {
    display: none !important;
  }

  .points-board-page {
    gap: 0;
  }

  .print-area {
    margin: 0;
  }

  .sheet-block {
    background-color: #fff;
    box-shadow: none;
    padding: 0;
    margin: 0;
    border-radius: 0;
    break-inside: avoid;
    page-break-inside: avoid;
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
}
</style>
