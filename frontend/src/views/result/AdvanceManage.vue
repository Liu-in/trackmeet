<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRounds, pageEvents, type EventRound, type SportEvent } from '@/api/event'
import {
  confirmAdvance,
  generateAdvance,
  listAdvance,
  updateAdvance,
  type AdvanceRow
} from '@/api/result'
import { saveBlob } from '@/api/request'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 晋级管理：项目 → 来源轮次 → 目标轮次，按「每组前 N 名 + 最好成绩递补」生成晋级名单，
 * 支持本地重排顺序（逐条 updateAdvance）、移出名单、确认名单（供下一轮分组分道使用）与 CSV 导出。
 * 权限编码：result:list（查看名单）、result:advance（生成/确认）、result:advance:edit（调序/移出）
 */
defineOptions({ name: 'AdvanceManage' })

const meetStore = useMeetStore()
const userStore = useUserStore()

/** 任一权限码命中即视为有权限（兼容菜单栏 result:advance:list 与通用 result:list） */
function hasAnyPerm(...codes: string[]): boolean {
  return codes.some((code) => userStore.hasPerm(code))
}

const canList = computed(() => hasAnyPerm('result:list', 'result:advance:list'))
const canAdvance = computed(() => userStore.hasPerm('result:advance'))
const canEdit = computed(() => userStore.hasPerm('result:advance:edit'))
const meetReady = computed(() => !!meetStore.currentMeetId)

/* ------------------------------- 选择链路 ------------------------------- */

const eventId = ref<number | undefined>(undefined)
const fromRoundId = ref<number | undefined>(undefined)
const toRoundId = ref<number | undefined>(undefined)

const eventList = ref<SportEvent[]>([])
const roundList = ref<EventRound[]>([])

const eventsLoading = ref(false)
const roundsLoading = ref(false)
const loading = ref(false)
const generating = ref(false)
const confirming = ref(false)
const reordering = ref(false)

const targetRounds = computed(() => roundList.value.filter((item) => item.id !== fromRoundId.value))

const currentEvent = computed(() => eventList.value.find((item) => item.id === eventId.value))

const fromRoundName = computed(
  () => roundList.value.find((item) => item.id === fromRoundId.value)?.roundName || '-'
)

const toRoundName = computed(() => roundList.value.find((item) => item.id === toRoundId.value)?.roundName || '-')

const chainReady = computed(() => !!eventId.value && !!fromRoundId.value && !!toRoundId.value)

/* ------------------------------- 名单数据 ------------------------------- */

const rawRows = ref<AdvanceRow[]>([])

function orderOf(row: AdvanceRow): number {
  const value = row.advanceOrder
  return typeof value === 'number' && value > 0 ? value : 0
}

/**
 * 来源轮次过滤：listAdvance 只按 toRoundId 过滤，
 * 若目标轮次下存在多个来源轮次的记录，则按行上的 fromRoundId 再过滤一次（后端未返回 fromRoundId 时不过滤）。
 */
function matchFromRound(row: AdvanceRow): boolean {
  if (!fromRoundId.value || row.fromRoundId === undefined || row.fromRoundId === null) {
    return true
  }
  return row.fromRoundId === fromRoundId.value
}

/** 命中来源轮次的行（在名单内 + 已移出，用于统计被隐藏的其他来源轮次记录） */
const scopeRows = computed(() => rawRows.value.filter((row) => matchFromRound(row)))

const hiddenByRound = computed(() => rawRows.value.length - scopeRows.value.length)

/** 正常在名单内（advanceOrder > 0）的行，按晋级顺序升序 */
const rows = computed(() =>
  scopeRows.value
    .filter((row) => orderOf(row) > 0)
    .slice()
    .sort((a, b) => orderOf(a) - orderOf(b))
)

/** 已移出名单的行（advanceOrder = 0，后端仍保留记录） */
const removedRows = computed(() => scopeRows.value.filter((row) => orderOf(row) === 0))

const confirmedCount = computed(() => rows.value.filter((row) => row.isConfirmed === 1).length)

const isConfirmedList = computed(() => rows.value.length > 0 && confirmedCount.value === rows.value.length)

const ADVANCE_TYPE_META: Record<string, { label: string; type: 'success' | 'primary' | 'warning' }> = {
  GROUP_TOP: { label: '每组前 N', type: 'success' },
  BEST_LOSER: { label: '最好成绩递补', type: 'primary' },
  MANUAL: { label: '手动', type: 'warning' }
}

function typeMeta(type?: string) {
  if (!type) {
    return { label: '-', type: 'warning' as const }
  }
  return ADVANCE_TYPE_META[type] || { label: type, type: 'warning' as const }
}

/* ------------------------------- 生成对话框 ------------------------------- */

const dialogVisible = ref(false)

const genForm = reactive({
  groupTopN: 2,
  bestLoserCount: 8,
  targetCount: 16,
  excludeIllegalWind: true
})

function openGenerateDialog() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canAdvance.value) {
    ElMessage.warning('当前账号无晋级名单生成权限（result:advance）')
    return
  }
  if (!chainReady.value) {
    ElMessage.warning('请先选择项目、来源轮次与目标轮次')
    return
  }
  dialogVisible.value = true
}

async function handleGenerate() {
  if (!meetStore.currentMeetId || !eventId.value || !fromRoundId.value || !toRoundId.value) {
    ElMessage.warning('请先选择项目、来源轮次与目标轮次')
    return
  }
  if (genForm.groupTopN < 0 || genForm.bestLoserCount < 0 || genForm.targetCount <= 0) {
    ElMessage.warning('每组前 N 名与递补数不能为负，目标人数需大于 0')
    return
  }
  generating.value = true
  try {
    const list = await generateAdvance({
      meetId: meetStore.currentMeetId,
      eventId: eventId.value,
      fromRoundId: fromRoundId.value,
      toRoundId: toRoundId.value,
      groupTopN: genForm.groupTopN,
      bestLoserCount: genForm.bestLoserCount,
      targetCount: genForm.targetCount,
      excludeIllegalWind: genForm.excludeIllegalWind
    })
    rawRows.value = Array.isArray(list) ? list : []
    dialogVisible.value = false
    if (rawRows.value.length) {
      ElMessage.success(`已生成 ${rawRows.value.length} 条晋级名单（未确认状态，请核对后确认）`)
    } else {
      ElMessage.warning('未生成晋级名单，请检查来源轮次成绩是否已审核并完成排名')
    }
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    generating.value = false
  }
}

/* ------------------------------- 名单查询 ------------------------------- */

async function loadList() {
  if (!meetStore.currentMeetId || !eventId.value || !toRoundId.value) {
    rawRows.value = []
    return
  }
  loading.value = true
  try {
    const list = await listAdvance({
      meetId: meetStore.currentMeetId,
      eventId: eventId.value,
      toRoundId: toRoundId.value
    })
    rawRows.value = Array.isArray(list) ? list : []
  } catch {
    rawRows.value = []
  } finally {
    loading.value = false
  }
}

async function handleQuery() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canList.value) {
    ElMessage.warning('当前账号无晋级名单查看权限（result:list / result:advance:list）')
    return
  }
  if (!chainReady.value) {
    ElMessage.warning('请先选择项目、来源轮次与目标轮次')
    return
  }
  await loadList()
  if (!rawRows.value.length) {
    ElMessage.warning('该目标轮次暂无晋级名单，请点击「生成晋级名单」')
  }
}

/* ------------------------------- 顺序调整 / 移出 ------------------------------- */

async function moveRow(row: AdvanceRow, delta: number) {
  if (!canEdit.value) {
    ElMessage.warning('当前账号无晋级名单编辑权限（result:advance:edit）')
    return
  }
  if (!row.id) {
    return
  }
  const list = rows.value.slice()
  const index = list.findIndex((item) => item.id === row.id)
  const target = index + delta
  if (index < 0 || target < 0 || target >= list.length) {
    return
  }
  const current = list[index]
  list[index] = list[target]
  list[target] = current

  reordering.value = true
  try {
    // 依次提交新的晋级顺序（1 基）
    for (let i = 0; i < list.length; i += 1) {
      const item = list[i]
      if (!item.id) {
        continue
      }
      await updateAdvance(item.id, { advanceOrder: i + 1 })
    }
    list.forEach((item, i) => {
      item.advanceOrder = i + 1
    })
    ElMessage.success('已更新晋级顺序')
  } catch {
    // 提交失败时回滚为服务端数据，避免本地与服务端不一致
    await loadList()
  } finally {
    reordering.value = false
  }
}

async function moveUp(row: AdvanceRow) {
  await moveRow(row, -1)
}

async function moveDown(row: AdvanceRow) {
  await moveRow(row, 1)
}

async function removeRow(row: AdvanceRow) {
  if (!canEdit.value) {
    ElMessage.warning('当前账号无晋级名单编辑权限（result:advance:edit）')
    return
  }
  if (!row.id) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认将「${row.athleteName || '该运动员'}」移出晋级名单吗？移出后可通过重新生成恢复。`,
      '移出名单',
      { type: 'warning', confirmButtonText: '确认移出', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  reordering.value = true
  try {
    await updateAdvance(row.id, { advanceType: 'MANUAL', advanceOrder: 0 })
    row.advanceOrder = 0
    row.advanceType = 'MANUAL'
    ElMessage.success('已移出晋级名单')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    reordering.value = false
  }
}

/* ------------------------------- 确认名单 ------------------------------- */

async function handleConfirm() {
  if (!canAdvance.value) {
    ElMessage.warning('当前账号无晋级名单确认权限（result:advance）')
    return
  }
  if (!meetStore.currentMeetId || !eventId.value || !toRoundId.value) {
    ElMessage.warning('请先选择项目与目标轮次')
    return
  }
  if (!rows.value.length) {
    ElMessage.warning('当前没有可确认的晋级名单')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认「${currentEvent.value?.eventName || '该项目'}」${fromRoundName.value} → ${toRoundName.value} 的 ${rows.value.length} 条晋级名单吗？确认后该名单将用于下一轮分组分道。`,
      '确认晋级名单',
      { type: 'warning', confirmButtonText: '确认名单', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  confirming.value = true
  try {
    await confirmAdvance({
      meetId: meetStore.currentMeetId,
      eventId: eventId.value,
      toRoundId: toRoundId.value
    })
    ElMessage.success('晋级名单已确认，可用于下一轮分组分道')
    await loadList()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    confirming.value = false
  }
}

/* ------------------------------- CSV 导出 ------------------------------- */

function csvCell(value: unknown): string {
  const text = value === undefined || value === null ? '' : String(value)
  return `"${text.replace(/"/g, '""')}"`
}

function handleExportCsv() {
  if (!canList.value) {
    ElMessage.warning('当前账号无晋级名单查看权限（result:list / result:advance:list）')
    return
  }
  if (!rows.value.length) {
    ElMessage.warning('当前没有可导出的晋级名单')
    return
  }
  const header = ['晋级顺序', '来源组次', '来源名次', '号码', '姓名', '单位', '来源成绩', '晋级类型']
  const lines = [header.map(csvCell).join(',')]
  rows.value.forEach((row, index) => {
    lines.push(
      [
        orderOf(row) || index + 1,
        row.sourceHeatNo ?? '',
        row.sourceRank ?? '',
        row.bibNo ?? '',
        row.athleteName ?? '',
        row.unitName ?? '',
        row.sourceResultRaw ?? (row.sourceResultValue !== undefined ? row.sourceResultValue : ''),
        typeMeta(row.advanceType).label
      ]
        .map(csvCell)
        .join(',')
    )
  })
  const csv = `\uFEFF${lines.join('\r\n')}`
  const filename = `晋级名单-${currentEvent.value?.eventName || '项目'}-${toRoundName.value}.csv`
  saveBlob(new Blob([csv], { type: 'text/csv;charset=utf-8' }), filename)
  ElMessage.success(`已导出 ${rows.value.length} 条晋级名单`)
}

/* ------------------------------- 联动与初始化 ------------------------------- */

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

async function handleEventChange(value?: number) {
  eventId.value = value
  fromRoundId.value = undefined
  toRoundId.value = undefined
  rawRows.value = []
  if (!value) {
    roundList.value = []
    return
  }
  roundsLoading.value = true
  try {
    const list = await listRounds(value)
    roundList.value = Array.isArray(list) ? list : []
  } catch {
    roundList.value = []
  } finally {
    roundsLoading.value = false
  }
}

function handleFromRoundChange() {
  toRoundId.value = undefined
  rawRows.value = []
}

function handleToRoundChange() {
  rawRows.value = []
  if (chainReady.value) {
    loadList()
  }
}

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
  <div class="tm-page">
    <div class="tm-card">
      <div class="tm-page-title">晋级管理</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜项目 → 来源轮次 → 目标轮次，生成并确认晋级名单
      </div>
    </div>

    <el-alert
      title="晋级规则：各组前 N 名直接晋级 + 其余人员按最好成绩递补至目标人数；DNS / DNF / DSQ / NM 与超风速成绩不参与递补。"
      type="info"
      :closable="false"
      show-icon
    />

    <el-alert
      v-if="!canList"
      title="当前账号缺少晋级名单查看权限（result:list / result:advance:list）。"
      type="warning"
      :closable="false"
      show-icon
    />

    <!-- 选择链路 -->
    <div class="tm-card">
      <div class="tm-search-bar">
        <span>项目：</span>
        <el-select
          v-model="eventId"
          :loading="eventsLoading"
          :disabled="!meetReady"
          placeholder="请选择项目"
          filterable
          style="width: 220px"
          @change="handleEventChange"
        >
          <el-option v-for="item in eventList" :key="item.id" :label="item.eventName" :value="item.id as number" />
        </el-select>

        <span>来源轮次：</span>
        <el-select
          v-model="fromRoundId"
          :loading="roundsLoading"
          :disabled="!meetReady || !eventId"
          placeholder="请选择来源轮次"
          filterable
          style="width: 170px"
          @change="handleFromRoundChange"
        >
          <el-option v-for="item in roundList" :key="item.id" :label="item.roundName" :value="item.id as number" />
        </el-select>

        <span>目标轮次：</span>
        <el-select
          v-model="toRoundId"
          :loading="roundsLoading"
          :disabled="!meetReady || !fromRoundId"
          placeholder="请选择目标轮次"
          filterable
          style="width: 170px"
          @change="handleToRoundChange"
        >
          <el-option v-for="item in targetRounds" :key="item.id" :label="item.roundName" :value="item.id as number" />
        </el-select>

        <el-button type="primary" :loading="loading" :disabled="!meetReady || !canList" @click="handleQuery">
          查询名单
        </el-button>
      </div>

      <div class="tm-toolbar" style="margin-top: 12px">
        <div class="tm-toolbar-actions">
          <el-button type="primary" :disabled="!chainReady || !canAdvance" @click="openGenerateDialog">
            生成晋级名单
          </el-button>
          <el-button
            type="success"
            :loading="confirming"
            :disabled="!chainReady || !rows.length || !canAdvance"
            @click="handleConfirm"
          >
            确认名单
          </el-button>
          <el-button :loading="loading" :disabled="!chainReady || !canList" @click="loadList">刷新</el-button>
          <el-button :disabled="!rows.length" @click="handleExportCsv">导出 CSV</el-button>
        </div>
        <div class="tm-toolbar-actions">
          <span class="tm-form-tip">
            名单 {{ rows.length }} 条｜已确认 {{ confirmedCount }} 条｜已移出 {{ removedRows.length }} 条
            <template v-if="hiddenByRound">｜其他来源轮次记录 {{ hiddenByRound }} 条（未显示）</template>
          </span>
        </div>
      </div>

      <div class="tm-form-tip" style="margin-top: 8px">
        未选择届次或轮次时按钮不可用；顺序调整会逐条提交新的 advanceOrder，确认后的名单才用于下一轮分组分道。
      </div>
    </div>

    <!-- 名单表格 -->
    <div class="tm-card">
      <div class="tm-toolbar" style="margin-bottom: 12px">
        <div class="tm-toolbar-actions">
          <el-tag v-if="isConfirmedList" type="success" size="small">名单已确认</el-tag>
          <el-tag v-else-if="rows.length" type="warning" size="small">名单未确认</el-tag>
          <span class="tm-form-tip">{{ currentEvent?.eventName || '未选择项目' }}｜{{ fromRoundName }} → {{ toRoundName }}</span>
        </div>
      </div>

      <el-table v-loading="loading || reordering" :data="rows" border stripe row-key="id" max-height="520">
        <el-table-column prop="advanceOrder" label="晋级顺序" width="90" align="center">
          <template #default="{ row }">
            {{ orderOf(row as AdvanceRow) || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="sourceHeatNo" label="来源组次" width="90" align="center">
          <template #default="{ row }">
            {{ (row as AdvanceRow).sourceHeatNo ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="sourceRank" label="来源名次" width="90" align="center">
          <template #default="{ row }">
            {{ (row as AdvanceRow).sourceRank ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="bibNo" label="号码" width="90" align="center" />
        <el-table-column prop="athleteName" label="姓名" min-width="110" show-overflow-tooltip />
        <el-table-column prop="unitName" label="单位" min-width="140" show-overflow-tooltip />
        <el-table-column label="来源成绩" width="120" align="center">
          <template #default="{ row }">
            {{ (row as AdvanceRow).sourceResultRaw || ((row as AdvanceRow).sourceResultValue ?? '-') }}
          </template>
        </el-table-column>
        <el-table-column label="晋级类型" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="typeMeta((row as AdvanceRow).advanceType).type" size="small">
              {{ typeMeta((row as AdvanceRow).advanceType).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row, $index }">
            <el-button
              link
              type="primary"
              :disabled="!canEdit || $index === 0 || reordering"
              @click="moveUp(row as AdvanceRow)"
            >
              上移
            </el-button>
            <el-button
              link
              type="primary"
              :disabled="!canEdit || $index === rows.length - 1 || reordering"
              @click="moveDown(row as AdvanceRow)"
            >
              下移
            </el-button>
            <el-button link type="danger" :disabled="!canEdit || reordering" @click="removeRow(row as AdvanceRow)">
              移出名单
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无晋级名单，请选择项目与轮次后点击「生成晋级名单」" />
        </template>
      </el-table>

      <div v-if="removedRows.length" class="tm-form-tip" style="margin-top: 8px">
        已移出名单 {{ removedRows.length }} 人（advanceOrder = 0，列表不显示）：{{
          removedRows
            .map((item) => item.athleteName || item.bibNo || item.id)
            .join('、')
        }}
      </div>
    </div>

    <!-- 生成对话框 -->
    <el-dialog v-model="dialogVisible" title="生成晋级名单" width="520px" destroy-on-close>
      <el-alert
        title="生成将覆盖未确认的既有名单，已确认名单不受影响。"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />
      <el-form label-width="150px">
        <el-form-item label="每组前 N 名晋级">
          <el-input-number v-model="genForm.groupTopN" :min="0" :max="20" />
        </el-form-item>
        <el-form-item label="剩余最好成绩递补数">
          <el-input-number v-model="genForm.bestLoserCount" :min="0" :max="200" />
        </el-form-item>
        <el-form-item label="目标人数">
          <el-input-number v-model="genForm.targetCount" :min="1" :max="200" />
        </el-form-item>
        <el-form-item label="排除超风速成绩">
          <el-switch v-model="genForm.excludeIllegalWind" />
          <span class="tm-form-tip" style="margin-left: 8px">DNS / DNF / DSQ / NM 始终不参与递补</span>
        </el-form-item>
      </el-form>
      <div class="tm-form-tip">
        {{ currentEvent?.eventName || '未选择项目' }}｜{{ fromRoundName }} → {{ toRoundName }}；默认每组前 2 名 +
        递补 8 人、目标 16 人。
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="handleGenerate">确认生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>
