<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createBonusRule,
  createScoreRule,
  deleteBonusRule,
  deleteScoreRule,
  getScoreRule,
  listScoreRuleItems,
  pageBonusRules,
  pageScoreRules,
  updateBonusRule,
  updateScoreRule,
  saveScoreRuleItems,
  type BonusRule,
  type ScoreRule,
  type ScoreRuleItem
} from '@/api/event'
import { DICT_TYPE } from '@/api/dict'
import DictSelect from '@/components/DictSelect.vue'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 名次分与加分规则：左侧名次分方案、右侧名次分明细、下方加分规则。
 */
defineOptions({ name: 'ScoreRule' })

const meetStore = useMeetStore()
const userStore = useUserStore()

const meetId = computed(() => meetStore.currentMeetId)

const TIE_POLICY_OPTIONS = [
  { label: '均分', value: 'AVERAGE' },
  { label: '满额', value: 'FULL' },
  { label: '顺延', value: 'ORDER' }
]

const SCORE_TARGET_OPTIONS = [
  { label: '单项', value: 'EVENT' },
  { label: '团体', value: 'TEAM' }
]

const APPLY_EVENT_TYPE_OPTIONS = [
  { label: '全部', value: 'ALL' },
  { label: '径赛', value: 'TRACK' },
  { label: '田赛', value: 'FIELD' },
  { label: '全能', value: 'ALLROUND' },
  { label: '接力', value: 'RELAY' }
]

const APPLY_GENDER_OPTIONS = [
  { label: '全部', value: 'ALL' },
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' },
  { label: '混合', value: 'MIXED' }
]

const ROUND_CODE_OPTIONS = [
  { label: '及格赛', value: 'QUALIFY' },
  { label: '预赛', value: 'PRE' },
  { label: '次赛', value: 'R1' },
  { label: '复赛', value: 'R2' },
  { label: '半决赛', value: 'SEMI' },
  { label: '决赛', value: 'FINAL' }
]

const STATUS_OPTIONS = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' }
]

const BONUS_TYPE_OPTIONS = [
  { label: '破纪录', value: 'RECORD' },
  { label: '接力双倍', value: 'RELAY' },
  { label: '破平局', value: 'BREAK_TIE' }
]

const RECORD_TYPE_LABELS: Record<string, string> = {
  WORLD: '世界纪录',
  ASIA: '亚洲纪录',
  NATION: '全国纪录',
  MEET: '赛会纪录',
  PROVINCE: '省级纪录',
  CITY: '市级纪录',
  GROUP: '组别纪录'
}

/** 名次分速填表：前 8 名 9-7-6-5-4-3-2-1，之后按 1 分递减（最小 0） */
const SCORE_TABLE = [9, 7, 6, 5, 4, 3, 2, 1]

const RULE_STATUS = {
  enabled: 'ENABLED',
  disabled: 'DISABLED'
} as const

function optionLabel(options: { label: string; value: string }[], value?: string): string {
  if (!value) {
    return '-'
  }
  return options.find((item) => item.value === value)?.label || value
}

function requireMeet(): boolean {
  if (!meetId.value) {
    ElMessage.warning('请先在顶部选择届次')
    return false
  }
  return true
}

// ================= 左侧：名次分方案 =================
const rulesLoading = ref(false)
const rules = ref<ScoreRule[]>([])
const ruleQuery = reactive({ keyword: '' })
const rulePagination = reactive({ page: 1, size: 20, total: 0 })

async function loadRules() {
  if (!meetId.value) {
    rules.value = []
    rulePagination.total = 0
    return
  }
  rulesLoading.value = true
  try {
    const page = await pageScoreRules({
      page: rulePagination.page,
      size: rulePagination.size,
      meetId: meetId.value,
      keyword: ruleQuery.keyword?.trim() || undefined
    })
    rules.value = page?.records || []
    rulePagination.total = page?.total || 0
  } catch {
    // 错误提示由响应拦截器统一处理
    rules.value = []
    rulePagination.total = 0
  } finally {
    rulesLoading.value = false
  }
}

function handleRuleSearch() {
  rulePagination.page = 1
  loadRules()
}

function handleRulePageChange(page: number) {
  rulePagination.page = page
  loadRules()
}

// ================= 名次分方案 新增 / 编辑 =================
const ruleDialogVisible = ref(false)
const ruleDialogMode = ref<'add' | 'edit'>('add')
const editingRuleId = ref<number | undefined>(undefined)
const ruleSubmitting = ref(false)
const ruleFormRef = ref<FormInstance>()

function createEmptyRuleForm(): ScoreRule {
  return {
    meetId: meetId.value,
    ruleName: '',
    ruleCode: '',
    tiePolicy: 'AVERAGE',
    scoreTarget: 'EVENT',
    applyEventType: 'ALL',
    applyGender: 'ALL',
    effectiveRoundCode: 'FINAL',
    isTeamScore: 1,
    isDefault: 0,
    status: RULE_STATUS.enabled,
    remark: ''
  }
}

const ruleForm = reactive<ScoreRule>(createEmptyRuleForm())

const ruleFormRules: FormRules = {
  ruleName: [{ required: true, message: '请输入方案名称', trigger: 'blur' }],
  ruleCode: [{ required: true, message: '请输入方案编码', trigger: 'blur' }]
}

function handleRuleAdd() {
  if (!requireMeet()) {
    return
  }
  ruleDialogMode.value = 'add'
  editingRuleId.value = undefined
  Object.assign(ruleForm, createEmptyRuleForm())
  ruleFormRef.value?.clearValidate()
  ruleDialogVisible.value = true
}

function handleRuleEdit(row: ScoreRule) {
  ruleDialogMode.value = 'edit'
  editingRuleId.value = row.id
  Object.assign(ruleForm, createEmptyRuleForm(), row)
  ruleFormRef.value?.clearValidate()
  ruleDialogVisible.value = true
}

async function handleRuleSubmit() {
  if (!ruleFormRef.value || !requireMeet()) {
    return
  }
  try {
    await ruleFormRef.value.validate()
  } catch {
    return
  }
  const payload: ScoreRule = { ...ruleForm, meetId: meetId.value }
  ruleSubmitting.value = true
  try {
    if (ruleDialogMode.value === 'add') {
      const newId = await createScoreRule(payload)
      ElMessage.success('新增名次分方案成功')
      ruleDialogVisible.value = false
      await loadRules()
      if (newId) {
        await selectRule({ ...payload, id: newId })
      }
    } else if (editingRuleId.value) {
      await updateScoreRule(editingRuleId.value, payload)
      ElMessage.success('保存名次分方案成功')
      ruleDialogVisible.value = false
      await loadRules()
      if (selectedRule.value?.id === editingRuleId.value) {
        await selectRule({ ...selectedRule.value, ...payload, id: editingRuleId.value })
      }
    }
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    ruleSubmitting.value = false
  }
}

async function handleRuleDelete(row: ScoreRule) {
  if (!row.id || row.isDefault === 1) {
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除名次分方案「${row.ruleName}」吗？其名次分明细将一并删除。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  rulesLoading.value = true
  try {
    await deleteScoreRule(row.id)
    ElMessage.success('删除成功')
    if (selectedRule.value?.id === row.id) {
      selectedRule.value = null
      items.value = []
    }
    await loadRules()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    rulesLoading.value = false
  }
}

// ================= 右侧：名次分明细 =================
const selectedRule = ref<ScoreRule | null>(null)
const items = ref<ScoreRuleItem[]>([])
const itemsLoading = ref(false)
const itemsSaving = ref(false)
const selectedRows = ref<ScoreRuleItem[]>([])
const quickRankCount = ref(8)

async function loadItems(ruleId: number) {
  itemsLoading.value = true
  try {
    const list = await listScoreRuleItems(ruleId)
    if (list && list.length > 0) {
      items.value = list
    } else {
      const detail = await getScoreRule(ruleId)
      items.value = detail?.items || []
    }
  } catch {
    items.value = []
  } finally {
    itemsLoading.value = false
  }
}

async function selectRule(row: ScoreRule) {
  selectedRule.value = row
  selectedRows.value = []
  if (!row.id) {
    items.value = []
    return
  }
  await loadItems(row.id)
}

function handleSelectionChange(rows: ScoreRuleItem[]) {
  selectedRows.value = rows
}

function handleRowClick(row: ScoreRule) {
  selectRule(row)
}

function rowClassName({ row }: { row: ScoreRule }) {
  return row.id === selectedRule.value?.id ? 'tm-rule-row-active' : ''
}

function nextRankNo(): number {
  if (items.value.length === 0) {
    return 1
  }
  return Math.max(...items.value.map((item) => item.rankNo || 0)) + 1
}

function handleAddItem() {
  const rankNo = nextRankNo()
  items.value.push({
    rankNo,
    rankFrom: rankNo,
    rankTo: rankNo,
    rankLabel: `第${rankNo}名`,
    pointValue: 0,
    tiePolicy: undefined,
    isTeamScore: 1,
    sortNo: items.value.length + 1
  })
}

function handleRemoveSelectedItems() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先勾选要删除的明细行')
    return
  }
  const removing = new Set(selectedRows.value)
  items.value = items.value.filter((item) => !removing.has(item))
  selectedRows.value = []
}

async function handleGenerateTopN() {
  const count = quickRankCount.value
  if (!count || count < 1) {
    ElMessage.warning('请输入需要生成的名次数量')
    return
  }
  if (items.value.length > 0) {
    try {
      await ElMessageBox.confirm(`将覆盖当前 ${items.value.length} 行明细，确定继续吗？`, '一键生成确认', {
        type: 'warning',
        confirmButtonText: '确定生成',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }
  }
  const generated: ScoreRuleItem[] = []
  for (let rank = 1; rank <= count; rank += 1) {
    const point = rank <= SCORE_TABLE.length ? SCORE_TABLE[rank - 1] : Math.max(0, 1 - (rank - SCORE_TABLE.length))
    generated.push({
      rankNo: rank,
      rankFrom: rank,
      rankTo: rank,
      rankLabel: `第${rank}名`,
      pointValue: point,
      tiePolicy: undefined,
      isTeamScore: 1,
      sortNo: rank
    })
  }
  items.value = generated
  ElMessage.success(`已按 9-7-6-5-4-3-2-1 规则生成前 ${count} 名`)
}

async function handleSaveItems() {
  const ruleId = selectedRule.value?.id
  if (!ruleId) {
    ElMessage.warning('请先选择左侧方案')
    return
  }
  const invalid = items.value.some((item) => !Number.isFinite(item.rankNo) || item.rankNo < 1)
  if (invalid) {
    ElMessage.warning('名次序号不能为空且必须大于 0')
    return
  }
  const rankNos = items.value.map((item) => item.rankNo)
  if (new Set(rankNos).size !== rankNos.length) {
    ElMessage.warning('名次序号不能重复')
    return
  }
  itemsSaving.value = true
  try {
    await saveScoreRuleItems(
      ruleId,
      items.value.map((item, index) => ({ ...item, sortNo: index + 1 }))
    )
    ElMessage.success('名次分明细保存成功')
    await loadItems(ruleId)
    await loadRules()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    itemsSaving.value = false
  }
}

// ================= 加分规则 =================
const bonusLoading = ref(false)
const bonusRules = ref<BonusRule[]>([])
const bonusQuery = reactive<{ bonusType: string }>({ bonusType: '' })
const bonusPagination = reactive({ page: 1, size: 50, total: 0 })

async function loadBonusRules() {
  if (!meetId.value) {
    bonusRules.value = []
    bonusPagination.total = 0
    return
  }
  bonusLoading.value = true
  try {
    const page = await pageBonusRules({
      page: bonusPagination.page,
      size: bonusPagination.size,
      meetId: meetId.value,
      bonusType: bonusQuery.bonusType || undefined
    })
    bonusRules.value = page?.records || []
    bonusPagination.total = page?.total || 0
  } catch {
    bonusRules.value = []
    bonusPagination.total = 0
  } finally {
    bonusLoading.value = false
  }
}

function handleBonusSearch() {
  bonusPagination.page = 1
  loadBonusRules()
}

function handleBonusPageChange(page: number) {
  bonusPagination.page = page
  loadBonusRules()
}

const bonusDialogVisible = ref(false)
const bonusDialogMode = ref<'add' | 'edit'>('add')
const editingBonusId = ref<number | undefined>(undefined)
const bonusSubmitting = ref(false)
const bonusFormRef = ref<FormInstance>()

function createEmptyBonusForm(): BonusRule {
  return {
    meetId: meetId.value,
    ruleName: '',
    ruleCode: '',
    bonusType: 'RECORD',
    recordType: undefined,
    bonusPoint: 9,
    isStackable: 0,
    factor: 2,
    applyEventType: 'ALL',
    applyGender: 'ALL',
    effectiveRoundCodes: 'FINAL',
    isInTeamScore: 1,
    priority: 1,
    status: RULE_STATUS.enabled,
    remark: ''
  }
}

const bonusForm = reactive<BonusRule>(createEmptyBonusForm())

const bonusFormRules = computed<FormRules>(() => ({
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
  bonusType: [{ required: true, message: '请选择加分类型', trigger: 'change' }],
  recordType:
    bonusForm.bonusType === 'RECORD'
      ? [{ required: true, message: '请选择纪录层级', trigger: 'change' }]
      : []
}))

function handleBonusAdd() {
  if (!requireMeet()) {
    return
  }
  bonusDialogMode.value = 'add'
  editingBonusId.value = undefined
  Object.assign(bonusForm, createEmptyBonusForm())
  bonusFormRef.value?.clearValidate()
  bonusDialogVisible.value = true
}

function handleBonusEdit(row: BonusRule) {
  bonusDialogMode.value = 'edit'
  editingBonusId.value = row.id
  Object.assign(bonusForm, createEmptyBonusForm(), row)
  bonusFormRef.value?.clearValidate()
  bonusDialogVisible.value = true
}

async function handleBonusSubmit() {
  if (!bonusFormRef.value || !requireMeet()) {
    return
  }
  try {
    await bonusFormRef.value.validate()
  } catch {
    return
  }
  const payload: BonusRule = {
    ...bonusForm,
    meetId: meetId.value,
    recordType: bonusForm.bonusType === 'RECORD' ? bonusForm.recordType : undefined,
    bonusPoint: bonusForm.bonusType === 'RELAY' ? undefined : bonusForm.bonusPoint,
    factor: bonusForm.bonusType === 'RELAY' ? bonusForm.factor ?? 2 : undefined
  }
  bonusSubmitting.value = true
  try {
    if (bonusDialogMode.value === 'add') {
      await createBonusRule(payload)
      ElMessage.success('新增加分规则成功')
    } else if (editingBonusId.value) {
      await updateBonusRule(editingBonusId.value, payload)
      ElMessage.success('保存加分规则成功')
    }
    bonusDialogVisible.value = false
    await loadBonusRules()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    bonusSubmitting.value = false
  }
}

async function handleBonusDelete(row: BonusRule) {
  if (!row.id) {
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除加分规则「${row.ruleName}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  bonusLoading.value = true
  try {
    await deleteBonusRule(row.id)
    ElMessage.success('删除成功')
    await loadBonusRules()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    bonusLoading.value = false
  }
}

// ================= 生命周期 =================
onMounted(async () => {
  await Promise.all([loadRules(), loadBonusRules()])
})

watch(meetId, async () => {
  rulePagination.page = 1
  bonusPagination.page = 1
  selectedRule.value = null
  items.value = []
  await Promise.all([loadRules(), loadBonusRules()])
})
</script>

<template>
  <div class="tm-page">
    <div class="tm-card">
      <div class="tm-page-title">名次分与加分规则</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜左侧维护名次分方案，右侧编辑名次分明细，下方维护加分规则
      </div>
    </div>

    <el-row :gutter="12">
      <!-- 名次分方案 -->
      <el-col :span="10">
        <div class="tm-card">
          <div class="tm-toolbar" style="margin-bottom: 12px">
            <div class="tm-toolbar-actions">
              <el-button
                v-if="userStore.hasPerm('rule:score:add')"
                type="primary"
                :disabled="!meetId"
                @click="handleRuleAdd"
              >
                新增方案
              </el-button>
            </div>
            <div class="tm-toolbar-actions">
              <el-input
                v-model="ruleQuery.keyword"
                placeholder="方案名称/编码"
                clearable
                style="width: 160px"
                @keyup.enter="handleRuleSearch"
              />
              <el-button @click="handleRuleSearch">查询</el-button>
              <el-button :disabled="!meetId" @click="loadRules">刷新</el-button>
            </div>
          </div>

          <el-table
            v-loading="rulesLoading"
            :data="rules"
            border
            stripe
            highlight-current-row
            :row-class-name="rowClassName"
            @row-click="handleRowClick"
          >
            <el-table-column prop="ruleName" label="方案名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="ruleCode" label="编码" min-width="110" show-overflow-tooltip />
            <el-table-column label="并列策略" width="100" align="center">
              <template #default="{ row }">{{ optionLabel(TIE_POLICY_OPTIONS, row.tiePolicy) }}</template>
            </el-table-column>
            <el-table-column label="适用类型" width="100" align="center">
              <template #default="{ row }">{{ optionLabel(APPLY_EVENT_TYPE_OPTIONS, row.applyEventType) }}</template>
            </el-table-column>
            <el-table-column label="默认" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isDefault === 1" type="success" effect="light">默认</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" effect="light">
                  {{ row.status === 'ENABLED' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right" align="center">
              <template #default="{ row }">
                <el-button v-if="userStore.hasPerm('rule:score:edit')" link type="primary" @click.stop="handleRuleEdit(row)">
                  编辑
                </el-button>
                <el-button v-if="userStore.hasPerm('rule:score:list')" link type="primary" @click.stop="selectRule(row)">
                  明细
                </el-button>
                <el-tooltip :disabled="row.isDefault !== 1" content="默认方案不可删除" placement="top">
                  <span>
                    <el-button
                      v-if="userStore.hasPerm('rule:score:delete')"
                      link
                      type="danger"
                      :disabled="row.isDefault === 1"
                      @click.stop="handleRuleDelete(row)"
                    >
                      删除
                    </el-button>
                  </span>
                </el-tooltip>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty :description="meetId ? '暂无名次分方案' : '请先在顶部选择届次'" />
            </template>
          </el-table>

          <div class="tm-pagination">
            <el-pagination
              :current-page="rulePagination.page"
              :page-size="rulePagination.size"
              :total="rulePagination.total"
              layout="total, prev, pager, next"
              background
              @current-change="handleRulePageChange"
            />
          </div>
        </div>
      </el-col>

      <!-- 名次分明细 -->
      <el-col :span="14">
        <div class="tm-card">
          <div class="tm-toolbar" style="margin-bottom: 12px">
            <div class="tm-toolbar-actions">
              <span class="tm-page-title" style="font-size: 14px">
                名次分明细{{ selectedRule ? `：${selectedRule.ruleName}` : '' }}
              </span>
            </div>
            <div class="tm-toolbar-actions">
              <el-input-number v-model="quickRankCount" :min="1" :max="100" controls-position="right" style="width: 110px" />
              <el-button :disabled="!selectedRule" @click="handleGenerateTopN">一键生成前 N 名</el-button>
            </div>
          </div>

          <el-empty v-if="!selectedRule" description="请选择左侧方案" />
          <template v-else>
            <div class="tm-toolbar" style="margin-bottom: 12px">
              <div class="tm-toolbar-actions">
                <el-button :disabled="!userStore.hasPerm('rule:score:edit')" @click="handleAddItem">添加一行</el-button>
                <el-button
                  type="danger"
                  plain
                  :disabled="!userStore.hasPerm('rule:score:edit')"
                  @click="handleRemoveSelectedItems"
                >
                  删除选中行
                </el-button>
              </div>
              <div class="tm-toolbar-actions">
                <el-button type="primary" :loading="itemsSaving" :disabled="!userStore.hasPerm('rule:score:edit')" @click="handleSaveItems">
                  保存明细
                </el-button>
              </div>
            </div>

            <el-table
              v-loading="itemsLoading"
              :data="items"
              border
              stripe
              max-height="420"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="45" />
              <el-table-column label="名次序号" width="120">
                <template #default="{ row }">
                  <el-input-number v-model="row.rankNo" :min="1" :max="999" size="small" controls-position="right" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="区间起" width="110">
                <template #default="{ row }">
                  <el-input-number v-model="row.rankFrom" :min="1" :max="999" size="small" controls-position="right" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="区间止" width="110">
                <template #default="{ row }">
                  <el-input-number v-model="row.rankTo" :min="1" :max="999" size="small" controls-position="right" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="名称" min-width="120">
                <template #default="{ row }">
                  <el-input v-model="row.rankLabel" size="small" maxlength="50" placeholder="如 第1名" />
                </template>
              </el-table-column>
              <el-table-column label="分值" width="120">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.pointValue"
                    :min="0"
                    :precision="2"
                    :step="0.5"
                    size="small"
                    controls-position="right"
                    style="width: 100%"
                  />
                </template>
              </el-table-column>
              <el-table-column label="并列策略" width="130">
                <template #default="{ row }">
                  <el-select v-model="row.tiePolicy" size="small" clearable placeholder="继承方案" style="width: 100%">
                    <el-option v-for="item in TIE_POLICY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="计入团体分" width="110" align="center">
                <template #default="{ row }">
                  <el-switch v-model="row.isTeamScore" :active-value="1" :inactive-value="0" />
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="暂无明细，可点击「添加一行」或「一键生成前 N 名」" />
              </template>
            </el-table>
            <div class="tm-form-tip" style="margin-top: 8px">
              保存明细前会校验名次序号非空且不重复；区间行填写「区间起/区间止」，单名次行三者保持一致。
            </div>
          </template>
        </div>
      </el-col>
    </el-row>

    <!-- 加分规则 -->
    <div class="tm-card">
      <div class="tm-toolbar" style="margin-bottom: 12px">
        <div class="tm-toolbar-actions">
          <span class="tm-page-title" style="font-size: 14px">加分规则</span>
          <el-button
            v-if="userStore.hasPerm('rule:bonus:edit')"
            type="primary"
            :disabled="!meetId"
            @click="handleBonusAdd"
          >
            新增加分规则
          </el-button>
        </div>
        <div class="tm-toolbar-actions">
          <el-select v-model="bonusQuery.bonusType" placeholder="加分类型" clearable style="width: 150px" @change="handleBonusSearch">
            <el-option v-for="item in BONUS_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button :disabled="!meetId" @click="loadBonusRules">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="bonusLoading" :data="bonusRules" border stripe>
        <el-table-column prop="ruleName" label="规则名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="ruleCode" label="编码" min-width="110" show-overflow-tooltip />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag effect="light">{{ optionLabel(BONUS_TYPE_OPTIONS, row.bonusType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="纪录层级" width="110" align="center">
          <template #default="{ row }">{{ row.recordType ? RECORD_TYPE_LABELS[row.recordType] || row.recordType : '-' }}</template>
        </el-table-column>
        <el-table-column prop="bonusPoint" label="加分值" width="90" align="center" />
        <el-table-column prop="factor" label="倍数" width="80" align="center" />
        <el-table-column label="是否叠加" width="100" align="center">
          <template #default="{ row }">{{ row.isStackable === 1 ? '叠加' : '不叠加' }}</template>
        </el-table-column>
        <el-table-column label="适用类型" width="100" align="center">
          <template #default="{ row }">{{ optionLabel(APPLY_EVENT_TYPE_OPTIONS, row.applyEventType) }}</template>
        </el-table-column>
        <el-table-column label="计入团体分" width="110" align="center">
          <template #default="{ row }">{{ row.isInTeamScore === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="90" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" effect="light">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="userStore.hasPerm('rule:bonus:edit')" link type="primary" @click="handleBonusEdit(row)">编辑</el-button>
            <el-button v-if="userStore.hasPerm('rule:bonus:edit')" link type="danger" @click="handleBonusDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="meetId ? '暂无加分规则' : '请先在顶部选择届次'" />
        </template>
      </el-table>

      <div class="tm-pagination">
        <el-pagination
          :current-page="bonusPagination.page"
          :page-size="bonusPagination.size"
          :total="bonusPagination.total"
          layout="total, prev, pager, next"
          background
          @current-change="handleBonusPageChange"
        />
      </div>
    </div>

    <!-- 名次分方案 新增 / 编辑 -->
    <el-dialog
      v-model="ruleDialogVisible"
      :title="ruleDialogMode === 'add' ? '新增名次分方案' : '编辑名次分方案'"
      width="700px"
      destroy-on-close
    >
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="ruleFormRules" label-width="130px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="方案名称" prop="ruleName">
              <el-input v-model="ruleForm.ruleName" maxlength="100" placeholder="如：单项前八名 9-7-6-5" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="方案编码" prop="ruleCode">
              <el-input v-model="ruleForm.ruleCode" maxlength="64" placeholder="届次内唯一，如：SR_TOP8" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="并列策略">
              <el-select v-model="ruleForm.tiePolicy" style="width: 100%">
                <el-option v-for="item in TIE_POLICY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计分对象">
              <el-select v-model="ruleForm.scoreTarget" style="width: 100%">
                <el-option v-for="item in SCORE_TARGET_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适用项目类型">
              <el-select v-model="ruleForm.applyEventType" style="width: 100%">
                <el-option v-for="item in APPLY_EVENT_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适用性别">
              <el-select v-model="ruleForm.applyGender" style="width: 100%">
                <el-option v-for="item in APPLY_GENDER_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生效轮次">
              <el-select v-model="ruleForm.effectiveRoundCode" clearable placeholder="空默认决赛" style="width: 100%">
                <el-option v-for="item in ROUND_CODE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计入团体分">
              <el-switch v-model="ruleForm.isTeamScore" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否默认方案">
              <el-switch v-model="ruleForm.isDefault" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="ruleForm.status" style="width: 100%">
                <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="ruleForm.remark" maxlength="100" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="tm-form-tip">标记为默认方案后，届次默认规则未单独指定名次分方案时将使用该方案；默认方案不可删除。</div>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="ruleSubmitting" @click="handleRuleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 加分规则 新增 / 编辑 -->
    <el-dialog
      v-model="bonusDialogVisible"
      :title="bonusDialogMode === 'add' ? '新增加分规则' : '编辑加分规则'"
      width="760px"
      destroy-on-close
    >
      <el-form ref="bonusFormRef" :model="bonusForm" :rules="bonusFormRules" label-width="130px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="规则名称" prop="ruleName">
              <el-input v-model="bonusForm.ruleName" maxlength="100" placeholder="如：破赛会纪录加 9 分" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规则编码" prop="ruleCode">
              <el-input v-model="bonusForm.ruleCode" maxlength="64" placeholder="届次内唯一，如：BR_MEET" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="加分类型" prop="bonusType">
              <el-select v-model="bonusForm.bonusType" style="width: 100%">
                <el-option v-for="item in BONUS_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="bonusForm.bonusType === 'RECORD'" :span="12">
            <el-form-item label="纪录层级" prop="recordType">
              <DictSelect v-model="bonusForm.recordType" :dict-type="DICT_TYPE.recordType" placeholder="请选择纪录层级" />
            </el-form-item>
          </el-col>
          <el-col v-if="bonusForm.bonusType !== 'RELAY'" :span="12">
            <el-form-item label="加分值">
              <el-input-number
                v-model="bonusForm.bonusPoint"
                :min="0"
                :precision="2"
                :step="0.5"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col v-if="bonusForm.bonusType === 'RELAY'" :span="12">
            <el-form-item label="倍数">
              <el-input-number
                v-model="bonusForm.factor"
                :min="0"
                :precision="2"
                :step="0.5"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col v-if="bonusForm.bonusType === 'RECORD'" :span="12">
            <el-form-item label="是否叠加">
              <el-switch v-model="bonusForm.isStackable" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适用项目类型">
              <el-select v-model="bonusForm.applyEventType" style="width: 100%">
                <el-option v-for="item in APPLY_EVENT_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适用性别">
              <el-select v-model="bonusForm.applyGender" style="width: 100%">
                <el-option v-for="item in APPLY_GENDER_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生效轮次码">
              <el-input v-model="bonusForm.effectiveRoundCodes" maxlength="100" placeholder="逗号分隔，默认 FINAL" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计入团体分">
              <el-switch v-model="bonusForm.isInTeamScore" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="bonusForm.priority" :min="0" :max="999" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="bonusForm.status" style="width: 100%">
                <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="bonusForm.remark" maxlength="100" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="tm-form-tip">
          破纪录：填写纪录层级与加分值；接力双倍：填写倍数（默认 2）；破平局：填写加分值。生效轮次码用于限定加分只在指定轮次生效。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="bonusDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="bonusSubmitting" @click="handleBonusSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
:deep(.tm-rule-row-active) {
  background-color: var(--el-color-primary-light-9);
}
</style>
