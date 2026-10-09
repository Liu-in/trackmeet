<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile, type UploadFiles } from 'element-plus'
import DictSelect from '@/components/DictSelect.vue'
import {
  createAthlete,
  deleteAthlete,
  downloadAthleteTemplate,
  importAthletes,
  pageAthletes,
  saveImportErrorFile,
  updateAthlete,
  type Athlete,
  type AthleteImportResult
} from '@/api/athlete'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 运动员管理：条件查询、增删改、Excel 导入（先校验后导入）。
 * 权限编码：athlete:list / athlete:add / athlete:edit / athlete:delete / athlete:import
 */
defineOptions({ name: 'AthleteList' })

/** 列表展示行：接口若带单位/组别名称则直接使用，否则用 store 映射 */
interface AthleteRow extends Athlete {
  unitName?: string
  groupName?: string
}

const meetStore = useMeetStore()
const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)
const importing = ref(false)

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  unitId: undefined as number | undefined,
  groupId: undefined as number | undefined,
  gender: '',
  qualificationStatus: ''
})

const rows = ref<AthleteRow[]>([])
const total = ref(0)

const meetReady = computed(() => !!meetStore.currentMeetId)

const QUALIFICATION_OPTIONS = [
  { value: 'QUALIFIED', label: '合格', type: 'success' },
  { value: 'PENDING', label: '待审', type: 'warning' },
  { value: 'DISQUALIFIED', label: '不合格', type: 'danger' },
  { value: 'BANNED', label: '禁赛', type: 'info' }
] as const

function genderText(gender?: string): string {
  if (gender === 'MALE') {
    return '男'
  }
  if (gender === 'FEMALE') {
    return '女'
  }
  return gender || '-'
}

function genderTagType(gender?: string): 'primary' | 'danger' | 'info' {
  if (gender === 'MALE') {
    return 'primary'
  }
  if (gender === 'FEMALE') {
    return 'danger'
  }
  return 'info'
}

function qualificationMeta(status?: string) {
  return QUALIFICATION_OPTIONS.find((item) => item.value === status) || { value: '', label: status || '-', type: 'info' as const }
}

function unitName(unitId?: number): string {
  if (!unitId) {
    return '-'
  }
  const hit = meetStore.units.find((item) => item.id === unitId)
  return hit?.unitName || String(unitId)
}

function groupName(groupId?: number): string {
  if (!groupId) {
    return '-'
  }
  const hit = meetStore.groups.find((item) => item.id === groupId)
  return hit?.groupName || String(groupId)
}

function sourceText(source?: string): string {
  if (source === 'IMPORT') {
    return '导入'
  }
  if (source === 'MANUAL') {
    return '手工录入'
  }
  return source || '-'
}

/* ------------------------------- 列表查询 ------------------------------- */

async function load() {
  if (!meetStore.currentMeetId) {
    rows.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const page = await pageAthletes({
      page: query.page,
      size: query.size,
      meetId: meetStore.currentMeetId,
      keyword: query.keyword || undefined,
      unitId: query.unitId,
      groupId: query.groupId,
      gender: query.gender || undefined,
      qualificationStatus: query.qualificationStatus || undefined
    })
    rows.value = (page.records || []) as AthleteRow[]
    total.value = page.total || 0
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  query.page = 1
  load()
}

function handleReset() {
  query.page = 1
  query.keyword = ''
  query.unitId = undefined
  query.groupId = undefined
  query.gender = ''
  query.qualificationStatus = ''
  load()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  load()
}

function handlePageChange(page: number) {
  query.page = page
  load()
}

/* ------------------------------- 新增 / 编辑 ------------------------------- */

interface AthleteForm {
  id?: number
  athleteName: string
  gender: string
  birthDate: string
  idCard: string
  unitId?: number
  groupId?: number
  bibNo: string
  className: string
  phone: string
  guardian: string
  guardianPhone: string
  qualificationStatus: string
  isRelayOnly: number
  remark: string
}

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = reactive<AthleteForm>({
  id: undefined,
  athleteName: '',
  gender: 'MALE',
  birthDate: '',
  idCard: '',
  unitId: undefined,
  groupId: undefined,
  bibNo: '',
  className: '',
  phone: '',
  guardian: '',
  guardianPhone: '',
  qualificationStatus: 'QUALIFIED',
  isRelayOnly: 0,
  remark: ''
})

function resetForm() {
  form.id = undefined
  form.athleteName = ''
  form.gender = 'MALE'
  form.birthDate = ''
  form.idCard = ''
  form.unitId = meetStore.units.length === 1 ? meetStore.units[0].id : undefined
  form.groupId = undefined
  form.bibNo = ''
  form.className = ''
  form.phone = ''
  form.guardian = ''
  form.guardianPhone = ''
  form.qualificationStatus = 'QUALIFIED'
  form.isRelayOnly = 0
  form.remark = ''
}

function handleAdd() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: AthleteRow) {
  isEdit.value = true
  form.id = row.id
  form.athleteName = row.athleteName || ''
  form.gender = row.gender || 'MALE'
  form.birthDate = row.birthDate || ''
  form.idCard = row.idCard || ''
  form.unitId = row.unitId
  form.groupId = row.groupId
  form.bibNo = row.bibNo || ''
  form.className = row.className || ''
  form.phone = row.phone || ''
  form.guardian = row.guardian || ''
  form.guardianPhone = row.guardianPhone || ''
  form.qualificationStatus = row.qualificationStatus || 'QUALIFIED'
  form.isRelayOnly = row.isRelayOnly ?? 0
  form.remark = row.remark || ''
  dialogVisible.value = true
}

function validateForm(): string | null {
  if (!form.athleteName.trim()) {
    return '请输入运动员姓名'
  }
  if (!form.gender) {
    return '请选择性别'
  }
  if (!form.birthDate) {
    return '请选择出生日期'
  }
  if (!form.idCard.trim()) {
    return '请输入身份证号'
  }
  if (!form.unitId) {
    return '请选择参赛单位'
  }
  if (!form.groupId) {
    return '请选择所属组别'
  }
  return null
}

async function handleSubmit() {
  const invalid = validateForm()
  if (invalid) {
    ElMessage.warning(invalid)
    return
  }
  if (!meetStore.currentMeetId) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  const payload: Athlete = {
    meetId: meetStore.currentMeetId,
    athleteName: form.athleteName.trim(),
    gender: form.gender,
    birthDate: form.birthDate,
    idCard: form.idCard.trim(),
    unitId: form.unitId,
    groupId: form.groupId,
    bibNo: form.bibNo.trim() || undefined,
    className: form.className.trim() || undefined,
    phone: form.phone.trim() || undefined,
    guardian: form.guardian.trim() || undefined,
    guardianPhone: form.guardianPhone.trim() || undefined,
    qualificationStatus: form.qualificationStatus || 'QUALIFIED',
    isRelayOnly: form.isRelayOnly,
    remark: form.remark.trim() || undefined
  }
  submitting.value = true
  try {
    if (isEdit.value && form.id) {
      await updateAthlete(form.id, payload)
      ElMessage.success('运动员已更新')
    } else {
      payload.entrySource = 'MANUAL'
      await createAthlete(payload)
      ElMessage.success('运动员已新增')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: AthleteRow) {
  if (!row.id) {
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除运动员「${row.athleteName}」吗？删除后其报名记录需另行处理。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  loading.value = true
  try {
    await deleteAthlete(row.id)
    ElMessage.success('运动员已删除')
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/* ------------------------------- 批量导入 ------------------------------- */

const importVisible = ref(false)
const importFile = ref<File | null>(null)
const importFileList = ref<UploadFile[]>([])
const importResult = ref<AthleteImportResult | null>(null)

function openImport() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  importFile.value = null
  importFileList.value = []
  importResult.value = null
  importVisible.value = true
}

function handleFileChange(file: UploadFile, files: UploadFiles) {
  const latest = files[files.length - 1] || file
  importFile.value = latest?.raw || null
  importResult.value = null
}

function handleFileRemove(_file: UploadFile) {
  importFile.value = null
  importResult.value = null
}

async function handleDownloadTemplate() {
  try {
    await downloadAthleteTemplate()
  } catch {
    // 错误提示由 request 拦截器统一处理
  }
}

async function runImport(dryRun: boolean) {
  if (!importFile.value) {
    ElMessage.warning('请先选择要导入的 Excel 文件')
    return
  }
  if (!meetStore.currentMeetId) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  importing.value = true
  try {
    const result = await importAthletes(importFile.value, meetStore.currentMeetId, dryRun)
    importResult.value = result
    if (!dryRun) {
      ElMessage.success(result?.message || '导入完成')
      await load()
    }
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    importing.value = false
  }
}

function handleSaveErrorFile() {
  if (importResult.value) {
    saveImportErrorFile(importResult.value)
  }
}

/* ------------------------------- 初始化 ------------------------------- */

onMounted(async () => {
  if (!meetStore.loaded) {
    await meetStore.loadMeets().catch(() => undefined)
  }
  if (!meetStore.units.length && meetStore.currentMeetId) {
    await meetStore.loadGroupsAndUnits().catch(() => undefined)
  }
  await load()
})
</script>

<template>
  <div class="tm-page">
    <div class="tm-card">
      <div class="tm-page-title">运动员管理</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜共 {{ total }} 名运动员
      </div>
    </div>

    <div class="tm-card">
      <div class="tm-search-bar">
        <el-input
          v-model="query.keyword"
          placeholder="姓名 / 证件号 / 号码"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="query.unitId" placeholder="参赛单位" clearable style="width: 180px">
          <el-option v-for="item in meetStore.unitOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.groupId" placeholder="组别" clearable style="width: 160px">
          <el-option v-for="item in meetStore.groupOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <DictSelect v-model="query.gender" dict-type="gender" placeholder="性别" width="140px" />
        <DictSelect
          v-model="query.qualificationStatus"
          dict-type="qualification_status"
          placeholder="资格状态"
          width="160px"
        />
        <el-button type="primary" :disabled="!meetReady" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>

    <div class="tm-card">
      <div class="tm-toolbar" style="margin-bottom: 12px">
        <div class="tm-toolbar-actions">
          <el-button v-if="userStore.hasPerm('athlete:add')" type="primary" @click="handleAdd">新增运动员</el-button>
          <el-button v-if="userStore.hasPerm('athlete:import')" @click="openImport">批量导入</el-button>
          <el-button v-if="userStore.hasPerm('athlete:import')" @click="handleDownloadTemplate">下载导入模板</el-button>
        </div>
        <div class="tm-toolbar-actions">
          <el-button :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe max-height="560">
        <el-table-column prop="bibNo" label="号码" width="100" />
        <el-table-column prop="athleteName" label="姓名" min-width="110" />
        <el-table-column label="性别" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="genderTagType((row as AthleteRow).gender)" size="small" effect="plain">
              {{ genderText((row as AthleteRow).gender) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="birthDate" label="出生日期" width="120" />
        <el-table-column prop="idCard" label="身份证号" min-width="180" show-overflow-tooltip />
        <el-table-column label="单位" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as AthleteRow).unitName || unitName((row as AthleteRow).unitId) }}
          </template>
        </el-table-column>
        <el-table-column label="组别" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as AthleteRow).groupName || groupName((row as AthleteRow).groupId) }}
          </template>
        </el-table-column>
        <el-table-column prop="className" label="班级" min-width="110" show-overflow-tooltip />
        <el-table-column prop="phone" label="联系电话" width="130" />
        <el-table-column label="资格状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="qualificationMeta((row as AthleteRow).qualificationStatus).type" size="small">
              {{ qualificationMeta((row as AthleteRow).qualificationStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数据来源" width="110" align="center">
          <template #default="{ row }">{{ sourceText((row as AthleteRow).entrySource) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="userStore.hasPerm('athlete:edit')" link type="primary" @click="handleEdit(row as AthleteRow)">
              编辑
            </el-button>
            <el-button
              v-if="userStore.hasPerm('athlete:delete')"
              link
              type="danger"
              @click="handleDelete(row as AthleteRow)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无运动员数据" />
        </template>
      </el-table>

      <div class="tm-pagination">
        <el-pagination
          :current-page="query.page"
          :page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑运动员' : '新增运动员'" width="760px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="姓名" required>
              <el-input v-model="form.athleteName" maxlength="50" placeholder="请输入运动员姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别" required>
              <DictSelect v-model="form.gender" dict-type="gender" placeholder="请选择性别" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出生日期" required>
              <el-date-picker
                v-model="form.birthDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择出生日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="身份证号" required>
              <el-input v-model="form.idCard" maxlength="32" placeholder="届次内唯一" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="参赛单位" required>
              <el-select v-model="form.unitId" placeholder="请选择参赛单位" style="width: 100%">
                <el-option
                  v-for="item in meetStore.unitOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="组别" required>
              <el-select v-model="form.groupId" placeholder="请选择组别" style="width: 100%">
                <el-option
                  v-for="item in meetStore.groupOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="号码">
              <el-input v-model="form.bibNo" maxlength="32" placeholder="留空自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班级">
              <el-input v-model="form.className" maxlength="64" placeholder="班级 / 年级" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="form.phone" maxlength="32" placeholder="请输入联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="监护人">
              <el-input v-model="form.guardian" maxlength="50" placeholder="监护人姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="监护人电话">
              <el-input v-model="form.guardianPhone" maxlength="32" placeholder="监护人联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="资格状态">
              <DictSelect v-model="form.qualificationStatus" dict-type="qualification_status" placeholder="请选择" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仅参加接力">
              <el-switch v-model="form.isRelayOnly" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="100" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div class="tm-form-tip">带 * 为必填项；号码留空时后端按单位号段自动生成。</div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入 -->
    <el-dialog v-model="importVisible" title="批量导入运动员" width="880px" destroy-on-close>
      <el-steps :active="importResult ? 3 : importFile ? 2 : 1" simple style="margin-bottom: 16px">
        <el-step title="下载模板并填写" />
        <el-step title="选择文件" />
        <el-step title="校验 / 导入" />
      </el-steps>

      <div class="tm-toolbar" style="margin-bottom: 12px">
        <div class="tm-toolbar-actions">
          <el-button @click="handleDownloadTemplate">下载导入模板</el-button>
        </div>
        <div class="tm-form-tip">模板包含填写说明 Sheet 与示例行，请勿修改表头。</div>
      </div>

      <el-upload
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        drag
        :file-list="importFileList"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
      >
        <div style="padding: 12px">将 Excel 文件拖到此处，或<em>点击选择文件</em></div>
      </el-upload>

      <div class="tm-toolbar" style="margin: 16px 0 12px">
        <div class="tm-toolbar-actions">
          <el-button :disabled="!importFile" :loading="importing" @click="runImport(true)">仅校验（dryRun）</el-button>
          <el-button type="primary" :disabled="!importFile" :loading="importing" @click="runImport(false)">
            正式导入
          </el-button>
        </div>
      </div>

      <template v-if="importResult">
        <el-alert
          :title="importResult.message"
          :type="importResult.failCount > 0 ? 'warning' : 'success'"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
        />
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="总行数">{{ importResult.totalCount }}</el-descriptions-item>
          <el-descriptions-item label="成功">{{ importResult.successCount }}</el-descriptions-item>
          <el-descriptions-item label="失败">{{ importResult.failCount }}</el-descriptions-item>
          <el-descriptions-item label="自动生成号码">{{ importResult.autoBibCount }}</el-descriptions-item>
          <el-descriptions-item label="任务 ID">{{ importResult.taskId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="模式">{{ importResult.dryRun ? '仅校验' : '正式导入' }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="importResult.errors && importResult.errors.length > 0">
          <div class="tm-page-title" style="margin: 12px 0 8px">错误明细（{{ importResult.errors.length }} 条）</div>
          <div class="tm-error-box">
            <el-table :data="importResult.errors" border size="small" max-height="280">
              <el-table-column prop="rowNum" label="行号" width="80" align="center" />
              <el-table-column prop="athleteName" label="姓名" width="120" />
              <el-table-column prop="idCard" label="身份证号" min-width="170" show-overflow-tooltip />
              <el-table-column prop="unitCode" label="单位编码" width="110" />
              <el-table-column prop="groupCode" label="组别编码" width="110" />
              <el-table-column prop="errorMessage" label="错误原因" min-width="220" show-overflow-tooltip />
            </el-table>
          </div>
        </template>

        <div v-if="importResult.errorFileBase64" style="margin-top: 12px">
          <el-button type="danger" plain @click="handleSaveErrorFile">下载错误报告</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
