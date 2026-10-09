<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createRound,
  deleteRound,
  getRoundRule,
  listRounds,
  pageEvents,
  saveRoundRule,
  updateRound,
  type EventRound,
  type EventRoundRule,
  type SportEvent
} from '@/api/event'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 轮次管理与轮次级晋级规则配置。
 */
defineOptions({ name: 'RoundList' })

const route = useRoute()
const meetStore = useMeetStore()
const userStore = useUserStore()

const meetId = computed(() => meetStore.currentMeetId)

/** 轮次码选项（与字典 round_code 保持一致） */
const ROUND_CODE_OPTIONS = [
  { label: '及格赛', value: 'QUALIFY', tag: 'info' },
  { label: '预赛', value: 'PRE', tag: 'primary' },
  { label: '次赛', value: 'R1', tag: 'primary' },
  { label: '复赛', value: 'R2', tag: 'primary' },
  { label: '半决赛', value: 'SEMI', tag: 'warning' },
  { label: '决赛', value: 'FINAL', tag: 'danger' }
] as const

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const ROUND_STATUS_OPTIONS = [
  { label: '已计划', value: 'PLANNED' },
  { label: '就绪', value: 'READY' },
  { label: '进行中', value: 'RUNNING' },
  { label: '已完成', value: 'FINISHED' }
]

const ROUND_STATUS_LABELS: Record<string, string> = {
  PLANNED: '已计划',
  READY: '就绪',
  RUNNING: '进行中',
  FINISHED: '已完成'
}

/** 平局规则默认值 */
const DEFAULT_TIE_BREAK_JSON = '{"order":["SECOND_BEST","BEST_ATTEMPT_ROUND","DRAW"],"allowParallelRank":true}'
/** 晋级规则默认值 */
const DEFAULT_ADVANCE_JSON = '{"groupTop":2,"bestLoser":8,"target":16,"excludeIllegalWind":true}'

// ---------------- 项目选择与轮次列表 ----------------
const eventLoading = ref(false)
const events = ref<SportEvent[]>([])
const selectedEventId = ref<number | undefined>(undefined)
const rounds = ref<EventRound[]>([])
const loading = ref(false)

const currentEvent = computed(() => events.value.find((item) => item.id === selectedEventId.value))

function roundCodeLabel(value?: string): string {
  if (!value) {
    return '-'
  }
  return ROUND_CODE_OPTIONS.find((item) => item.value === value)?.label || value
}

function roundCodeTag(value?: string): TagType {
  return (ROUND_CODE_OPTIONS.find((item) => item.value === value)?.tag || 'info') as TagType
}

function roundStatusLabel(value?: string): string {
  if (!value) {
    return '-'
  }
  return ROUND_STATUS_LABELS[value] || value
}

async function loadEvents() {
  if (!meetId.value) {
    events.value = []
    rounds.value = []
    selectedEventId.value = undefined
    return
  }
  eventLoading.value = true
  try {
    const result = await pageEvents({ page: 1, size: 200, meetId: meetId.value })
    events.value = result?.records || []
    const queryId = Number(route.query.eventId)
    if (Number.isFinite(queryId) && queryId > 0) {
      selectedEventId.value = queryId
    } else if (events.value.length > 0) {
      selectedEventId.value = events.value[0].id
    } else {
      selectedEventId.value = undefined
    }
  } catch {
    // 错误提示由响应拦截器统一处理
    events.value = []
  } finally {
    eventLoading.value = false
  }
  await loadRounds()
}

async function loadRounds() {
  if (!selectedEventId.value) {
    rounds.value = []
    return
  }
  loading.value = true
  try {
    rounds.value = (await listRounds(selectedEventId.value)) || []
  } catch {
    rounds.value = []
  } finally {
    loading.value = false
  }
}

watch(selectedEventId, () => {
  loadRounds()
})

watch(meetId, () => {
  loadEvents()
})

// ---------------- 轮次新增 / 编辑 ----------------
const dialogVisible = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const editingId = ref<number | undefined>(undefined)
const submitting = ref(false)
const formRef = ref<FormInstance>()

function createEmptyRound(): EventRound {
  return {
    eventId: selectedEventId.value,
    roundCode: 'PRE',
    roundName: '',
    roundNo: undefined,
    isFinal: 0,
    planHeatCount: undefined,
    planAdvanceCount: undefined,
    status: 'PLANNED'
  }
}

const form = reactive<EventRound>(createEmptyRound())

const formRules: FormRules = {
  roundCode: [{ required: true, message: '请选择轮次码', trigger: 'change' }],
  roundName: [{ required: true, message: '请输入轮次名称', trigger: 'blur' }]
}

function handleAdd() {
  if (!selectedEventId.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  dialogMode.value = 'add'
  editingId.value = undefined
  Object.assign(form, createEmptyRound())
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

function handleEdit(row: EventRound) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(form, createEmptyRound(), row)
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value || !selectedEventId.value) {
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  const payload: EventRound = {
    eventId: selectedEventId.value,
    roundCode: form.roundCode,
    roundName: form.roundName?.trim() || '',
    roundNo: form.roundNo,
    isFinal: form.isFinal ?? 0,
    planHeatCount: form.planHeatCount,
    planAdvanceCount: form.planAdvanceCount,
    status: form.status || 'PLANNED'
  }
  submitting.value = true
  try {
    if (dialogMode.value === 'add') {
      await createRound(selectedEventId.value, payload)
      ElMessage.success('新增轮次成功')
    } else if (editingId.value) {
      await updateRound(editingId.value, payload)
      ElMessage.success('保存轮次成功')
    }
    dialogVisible.value = false
    await loadRounds()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: EventRound) {
  if (!row.id) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定删除轮次「${row.roundName || roundCodeLabel(row.roundCode)}」吗？其晋级规则将一并删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  loading.value = true
  try {
    await deleteRound(row.id)
    ElMessage.success('删除成功')
    await loadRounds()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    loading.value = false
  }
}

// ---------------- 晋级规则 ----------------
const ruleVisible = ref(false)
const ruleLoading = ref(false)
const ruleSubmitting = ref(false)
const currentRound = ref<EventRound | null>(null)
const ruleFormRef = ref<FormInstance>()

function createEmptyRule(): EventRoundRule {
  return {
    admitRank: undefined,
    groupTopN: undefined,
    bestLoserCount: undefined,
    targetCount: undefined,
    sourceRoundId: undefined,
    tieBreakJson: DEFAULT_TIE_BREAK_JSON,
    advanceJson: DEFAULT_ADVANCE_JSON
  }
}

const ruleForm = reactive<EventRoundRule>(createEmptyRule())

/** 可作为晋级来源的其它轮次（同一项目内） */
const sourceRoundOptions = computed(() =>
  rounds.value.filter((item) => item.id !== currentRound.value?.id && item.id !== undefined)
)

function validateJson(text?: string): boolean {
  if (!text || !text.trim()) {
    return true
  }
  try {
    JSON.parse(text)
    return true
  } catch {
    ElMessage.warning('JSON 格式不正确')
    return false
  }
}

async function handleRuleOpen(row: EventRound) {
  if (!row.id) {
    return
  }
  currentRound.value = row
  Object.assign(ruleForm, createEmptyRule())
  ruleVisible.value = true
  ruleLoading.value = true
  try {
    const rule = await getRoundRule(row.id)
    ruleForm.admitRank = rule?.admitRank ?? undefined
    ruleForm.groupTopN = rule?.groupTopN ?? undefined
    ruleForm.bestLoserCount = rule?.bestLoserCount ?? undefined
    ruleForm.targetCount = rule?.targetCount ?? undefined
    ruleForm.sourceRoundId = rule?.sourceRoundId ?? undefined
    ruleForm.tieBreakJson = rule?.tieBreakJson || DEFAULT_TIE_BREAK_JSON
    ruleForm.advanceJson = rule?.advanceJson || DEFAULT_ADVANCE_JSON
  } catch {
    // 轮次级规则尚未配置时接口返回 null，保持默认值即可（错误提示由拦截器统一处理）
  } finally {
    ruleLoading.value = false
  }
}

async function handleRuleSubmit() {
  const roundId = currentRound.value?.id
  if (!roundId) {
    return
  }
  if (!validateJson(ruleForm.tieBreakJson) || !validateJson(ruleForm.advanceJson)) {
    return
  }
  ruleSubmitting.value = true
  try {
    await saveRoundRule(roundId, {
      admitRank: ruleForm.admitRank,
      groupTopN: ruleForm.groupTopN,
      bestLoserCount: ruleForm.bestLoserCount,
      targetCount: ruleForm.targetCount,
      sourceRoundId: ruleForm.sourceRoundId,
      tieBreakJson: ruleForm.tieBreakJson?.trim(),
      advanceJson: ruleForm.advanceJson?.trim()
    })
    ElMessage.success('晋级规则保存成功')
    ruleVisible.value = false
    await loadRounds()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    ruleSubmitting.value = false
  }
}

function resetAdvanceJson() {
  ruleForm.advanceJson = DEFAULT_ADVANCE_JSON
}

function resetTieBreakJson() {
  ruleForm.tieBreakJson = DEFAULT_TIE_BREAK_JSON
}

onMounted(() => {
  loadEvents()
})
</script>

<template>
  <div class="tm-page">
    <div class="tm-card">
      <div class="tm-page-title">轮次管理</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜维护项目各轮次与轮次级晋级规则
      </div>
    </div>

    <div class="tm-card">
      <div class="tm-search-bar">
        <span>所属项目：</span>
        <el-select
          v-model="selectedEventId"
          :loading="eventLoading"
          placeholder="请选择项目"
          filterable
          clearable
          style="width: 320px"
        >
          <el-option v-for="item in events" :key="item.id" :label="item.eventName" :value="item.id" />
        </el-select>
        <el-button :disabled="!selectedEventId" @click="loadRounds">刷新</el-button>
      </div>
      <div v-if="!meetId" class="tm-form-tip" style="margin-top: 8px">请先在顶部选择届次。</div>
    </div>

    <div class="tm-card">
      <div class="tm-toolbar" style="margin-bottom: 12px">
        <div class="tm-toolbar-actions">
          <el-button
            v-if="userStore.hasPerm('event:round:add')"
            type="primary"
            :disabled="!selectedEventId"
            @click="handleAdd"
          >
            新增轮次
          </el-button>
        </div>
        <div class="tm-form-tip">
          {{ currentEvent ? `当前项目：${currentEvent.eventName}（共 ${rounds.length} 个轮次）` : '未选择项目' }}
        </div>
      </div>

      <el-table v-loading="loading" :data="rounds" border stripe>
        <el-table-column label="轮次码" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="roundCodeTag(row.roundCode)" effect="light">{{ roundCodeLabel(row.roundCode) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="roundName" label="轮次名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="roundNo" label="序号" width="80" align="center" />
        <el-table-column label="是否决赛" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isFinal === 1" type="danger" effect="dark">决赛</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="planHeatCount" label="计划组数" width="100" align="center" />
        <el-table-column prop="planAdvanceCount" label="计划晋级人数" width="130" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">{{ roundStatusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="userStore.hasPerm('event:round:edit')" link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="userStore.hasPerm('event:rule:list')" link type="primary" @click="handleRuleOpen(row)">
              晋级规则
            </el-button>
            <el-button v-if="userStore.hasPerm('event:round:delete')" link type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="events.length === 0 ? '请先在项目管理中创建项目' : '该项目暂无轮次，可新增轮次'" />
        </template>
      </el-table>
    </div>

    <!-- 新增 / 编辑轮次 -->
    <el-dialog v-model="dialogVisible" :title="dialogMode === 'add' ? '新增轮次' : '编辑轮次'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="轮次码" prop="roundCode">
              <el-select v-model="form.roundCode" placeholder="请选择轮次码" style="width: 100%">
                <el-option v-for="item in ROUND_CODE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="轮次名称" prop="roundName">
              <el-input v-model="form.roundName" maxlength="100" placeholder="如：预赛" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="序号">
              <el-input-number v-model="form.roundNo" :min="1" :max="20" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否决赛">
              <el-switch v-model="form.isFinal" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划组数">
              <el-input-number v-model="form.planHeatCount" :min="0" :max="99" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划晋级人数">
              <el-input-number
                v-model="form.planAdvanceCount"
                :min="0"
                :max="9999"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option v-for="item in ROUND_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <div class="tm-form-tip">轮次序号与是否决赛留空时由服务端按项目内顺序与轮次码自动维护。</div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 晋级规则 -->
    <el-dialog
      v-model="ruleVisible"
      :title="`晋级规则 - ${currentRound?.roundName || roundCodeLabel(currentRound?.roundCode)}`"
      width="720px"
      destroy-on-close
    >
      <el-form ref="ruleFormRef" v-loading="ruleLoading" :model="ruleForm" label-width="150px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="录取名次">
              <el-input-number v-model="ruleForm.admitRank" :min="0" :max="999" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="每组前 N 名晋级">
              <el-input-number v-model="ruleForm.groupTopN" :min="0" :max="99" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="递补人数">
              <el-input-number
                v-model="ruleForm.bestLoserCount"
                :min="0"
                :max="999"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标晋级人数">
              <el-input-number v-model="ruleForm.targetCount" :min="0" :max="9999" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="来源轮次">
              <el-select v-model="ruleForm.sourceRoundId" placeholder="默认上一轮" clearable style="width: 100%">
                <el-option
                  v-for="item in sourceRoundOptions"
                  :key="item.id"
                  :label="`${item.roundName || roundCodeLabel(item.roundCode)}（第${item.roundNo ?? '-'}轮）`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="平局规则 JSON">
              <el-input v-model="ruleForm.tieBreakJson" type="textarea" :rows="3" placeholder="平局判定顺序 JSON" />
              <div class="tm-form-tip">
                默认 {{ DEFAULT_TIE_BREAK_JSON }}
                <el-button link type="primary" @click="resetTieBreakJson">恢复默认</el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="晋级规则 JSON">
              <el-input v-model="ruleForm.advanceJson" type="textarea" :rows="3" placeholder="晋级规则扩展 JSON" />
              <div class="tm-form-tip">
                默认 {{ DEFAULT_ADVANCE_JSON }}
                <el-button link type="primary" @click="resetAdvanceJson">恢复默认</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <div class="tm-form-tip">
          晋级结果 = 各组前 N 名直接晋级 + 剩余人员按最好成绩递补至目标人数；提交前会校验 JSON 文本格式。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="ruleVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="ruleSubmitting"
          :disabled="!userStore.hasPerm('event:rule:edit')"
          @click="handleRuleSubmit"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
