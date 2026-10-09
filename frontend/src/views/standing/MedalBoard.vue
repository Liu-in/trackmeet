<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadMedalBoard, getMedalSheet, getMedals, type MedalRow } from '@/api/standing'
import type { SheetBundle } from '@/api/print'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 奖牌榜：按组别统计各单位金银铜牌与排序分，
 * 支持 A4 打印（另存为 PDF）与 Excel 导出。
 * 权限编码：standing:medal（查看）、print:standings（打印/导出）
 */
defineOptions({ name: 'MedalBoard' })

const meetStore = useMeetStore()
const userStore = useUserStore()

const canMedal = computed(() => userStore.hasPerm('standing:medal'))
const canPrint = computed(() => userStore.hasPerm('print:standings'))
const meetReady = computed(() => !!meetStore.currentMeetId)

/* ------------------------------- 榜单查询 ------------------------------- */

const groupId = ref<number | undefined>(undefined)
const loading = ref(false)
const downloading = ref(false)
const printing = ref(false)

const rows = ref<MedalRow[]>([])

const currentGroupName = computed(() => {
  if (!groupId.value) {
    return '全部组别'
  }
  return meetStore.groups.find((item) => item.id === groupId.value)?.groupName || String(groupId.value)
})

const meetName = computed(() => meetStore.currentMeet?.meetName || '田径运动会')

const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })

function num(value?: number): number {
  if (value === undefined || value === null || Number.isNaN(Number(value))) {
    return 0
  }
  return Number(value)
}

const unitCount = computed(() => rows.value.length)

const medalTotal = computed(() => rows.value.reduce((sum, row) => sum + num(row.totalMedal), 0))

const goldTotal = computed(() => rows.value.reduce((sum, row) => sum + num(row.gold), 0))

const goldLeader = computed(() => {
  if (!rows.value.length) {
    return '暂无数据'
  }
  const max = rows.value.reduce((value, row) => Math.max(value, num(row.gold)), 0)
  if (max <= 0) {
    return '暂无金牌'
  }
  const leaders = rows.value.filter((row) => num(row.gold) === max).map((row) => row.unitName || row.unitId || '-')
  return `${leaders.join('、')}（${max} 金）`
})

const calcTimeText = computed(() => rows.value.find((row) => !!row.calcTime)?.calcTime || '-')

async function loadMedals() {
  if (!meetStore.currentMeetId) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    const list = await getMedals({
      meetId: meetStore.currentMeetId,
      groupId: groupId.value
    })
    rows.value = Array.isArray(list) ? list : []
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

async function handleQuery() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canMedal.value) {
    ElMessage.warning('当前账号无奖牌榜查看权限（standing:medal）')
    return
  }
  await loadMedals()
  if (!rows.value.length) {
    ElMessage.warning('暂无奖牌数据，请先在积分管理中执行重算')
  }
}

/* ------------------------------- 打印 / 导出 ------------------------------- */

const printSheets = ref<SheetBundle[]>([])
const printFallback = ref(false)

async function handlePrint() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canPrint.value) {
    ElMessage.warning('当前账号无奖牌榜打印权限（print:standings）')
    return
  }
  if (!rows.value.length) {
    ElMessage.warning('暂无奖牌数据可打印')
    return
  }
  printing.value = true
  try {
    const list = await getMedalSheet({
      meetId: meetStore.currentMeetId,
      groupId: groupId.value
    })
    printSheets.value = Array.isArray(list) ? list : []
  } catch {
    printSheets.value = []
  } finally {
    printing.value = false
  }
  // 后端未返回打印表时退回使用榜单表格打印
  printFallback.value = printSheets.value.length === 0
  await nextTick()
  window.print()
}

async function handleDownloadExcel() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canPrint.value) {
    ElMessage.warning('当前账号无奖牌榜导出权限（print:standings）')
    return
  }
  downloading.value = true
  try {
    await downloadMedalBoard(
      { meetId: meetStore.currentMeetId, groupId: groupId.value },
      `${meetName.value}-奖牌榜.xlsx`
    )
    ElMessage.success('已开始下载奖牌榜')
  } catch {
    // 错误提示由 request 统一处理
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
  await loadMedals()
})
</script>

<template>
  <div class="tm-page medal-board-page">
    <div class="tm-card no-print">
      <div class="tm-page-title">奖牌榜</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜{{ currentGroupName }}｜参赛单位
        {{ unitCount }} 个｜奖牌合计 {{ medalTotal }} 枚｜计算时间 {{ calcTimeText }}
      </div>
    </div>

    <el-alert
      v-if="!canMedal"
      class="no-print"
      title="当前账号缺少奖牌榜查看权限（standing:medal）。"
      type="warning"
      :closable="false"
      show-icon
    />

    <el-alert
      class="no-print"
      title="排序规则：默认金牌优先（金 → 银 → 铜），可在「规则配置」中改为奖牌总数优先或积分优先；排序分 sortScore 为后端按当前规则计算值。"
      type="info"
      :closable="false"
      show-icon
    />

    <!-- 统计卡 -->
    <div class="tm-stat-grid no-print">
      <div class="tm-stat-card">
        <div class="tm-stat-label">参赛单位数</div>
        <div class="tm-stat-value">{{ unitCount }}</div>
      </div>
      <div class="tm-stat-card">
        <div class="tm-stat-label">奖牌总数</div>
        <div class="tm-stat-value">{{ medalTotal }}</div>
      </div>
      <div class="tm-stat-card">
        <div class="tm-stat-label">金牌总数</div>
        <div class="tm-stat-value">{{ goldTotal }}</div>
      </div>
      <div class="tm-stat-card">
        <div class="tm-stat-label">金牌最多的单位</div>
        <div class="tm-stat-value" style="font-size: 16px">{{ goldLeader }}</div>
      </div>
    </div>

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
        <el-button :loading="loading" :disabled="!meetReady || !canMedal" @click="handleQuery">查询</el-button>
      </div>

      <div class="tm-toolbar" style="margin-top: 12px">
        <div class="tm-toolbar-actions">
          <el-button type="primary" :loading="printing" :disabled="!meetReady || !canPrint" @click="handlePrint">
            打印 / 另存为 PDF
          </el-button>
          <el-button :loading="downloading" :disabled="!meetReady || !canPrint" @click="handleDownloadExcel">
            下载 Excel
          </el-button>
          <el-button :loading="loading" :disabled="!meetReady || !canMedal" @click="loadMedals">刷新</el-button>
        </div>
        <div class="tm-toolbar-actions">
          <span class="tm-form-tip">共 {{ rows.length }} 个单位</span>
        </div>
      </div>

      <div class="tm-form-tip" style="margin-top: 8px">
        未选择届次时按钮不可用；打印时统计卡、筛选与按钮会自动隐藏，仅输出 A4 奖牌榜表格。
      </div>
    </div>

    <!-- 奖牌榜表格 -->
    <div class="tm-card no-print">
      <el-table v-loading="loading" :data="rows" border stripe row-key="id" max-height="560">
        <el-table-column prop="rankNo" label="名次" width="80" align="center">
          <template #default="{ row }">
            <span :class="{ 'rank-top': num((row as MedalRow).rankNo) > 0 && num((row as MedalRow).rankNo) <= 3 }">
              {{ (row as MedalRow).rankNo ?? '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="unitName" label="单位" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as MedalRow).unitName || (row as MedalRow).unitId || '-' }}</template>
        </el-table-column>
        <el-table-column label="组别" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as MedalRow).groupName || currentGroupName }}</template>
        </el-table-column>
        <el-table-column label="金" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" class="medal-tag medal-gold">{{ num((row as MedalRow).gold) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="银" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" class="medal-tag medal-silver">{{ num((row as MedalRow).silver) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="铜" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" class="medal-tag medal-bronze">{{ num((row as MedalRow).bronze) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="奖牌总数" width="110" align="center">
          <template #default="{ row }">
            <span class="total-medal">{{ num((row as MedalRow).totalMedal) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="排序分" width="110" align="center">
          <template #default="{ row }">{{ num((row as MedalRow).sortScore) }}</template>
        </el-table-column>
        <el-table-column prop="calcTime" label="计算时间" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as MedalRow).calcTime || '-' }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无奖牌数据，请先在积分管理中执行重算" />
        </template>
      </el-table>
    </div>

    <!-- A4 打印区：优先使用后端奖牌打印表，缺失时退回榜单表格 -->
    <div class="print-area">
      <template v-if="printSheets.length">
        <div v-for="(bundle, index) in printSheets" :key="`${index}-${bundle.sheetKey}`" class="sheet-block">
          <div class="sheet-header">
            <div class="sheet-header-main">{{ meetName }}</div>
            <div class="sheet-header-sub">
              <span>{{ bundle.sheetName || '奖牌榜' }}</span>
              <span class="sheet-header-gap">{{ currentGroupName }}</span>
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

      <div v-else-if="printFallback && rows.length" class="sheet-block">
        <div class="sheet-header">
          <div class="sheet-header-main">{{ meetName }}</div>
          <div class="sheet-header-sub">
            <span>奖牌榜</span>
            <span class="sheet-header-gap">{{ currentGroupName }}</span>
            <span class="sheet-header-gap">打印日期：{{ today }}</span>
          </div>
        </div>
        <table class="sheet-table">
          <thead>
            <tr>
              <th>名次</th>
              <th>单位</th>
              <th>金</th>
              <th>银</th>
              <th>铜</th>
              <th>奖牌总数</th>
              <th>排序分</th>
              <th>计算时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in rows" :key="row.id ?? index">
              <td>{{ row.rankNo ?? '-' }}</td>
              <td>{{ row.unitName || row.unitId || '-' }}</td>
              <td>{{ num(row.gold) }}</td>
              <td>{{ num(row.silver) }}</td>
              <td>{{ num(row.bronze) }}</td>
              <td>{{ num(row.totalMedal) }}</td>
              <td>{{ num(row.sortScore) }}</td>
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
.rank-top {
  font-weight: 700;
  color: #e6a23c;
}

.total-medal {
  font-weight: 700;
}

.medal-tag {
  min-width: 34px;
  border: none;
  color: #303133;
  font-weight: 600;
}

.medal-gold {
  background-color: #fadb14;
  color: #7a5c00;
}

.medal-silver {
  background-color: #c0c4cc;
  color: #ffffff;
}

.medal-bronze {
  background-color: #e6a23c;
  color: #ffffff;
}

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

  .medal-board-page {
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
