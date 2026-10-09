<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createTemplate,
  deleteTemplate,
  pageTemplates,
  updateTemplate,
  type PrintTemplate
} from '@/api/print'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'
import '@/styles/print.css'

/**
 * 打印模板配置：按报表类型维护纸张、方向、字号、页眉页脚、签名区与默认模板。
 * 权限编码：print:template:list（查看）/ print:template:add（新增）/ print:template:edit（编辑、设为默认）/ print:template:delete（删除）
 */
defineOptions({ name: 'TemplateConfig' })

const meetStore = useMeetStore()
const userStore = useUserStore()

const meetReady = computed(() => !!meetStore.currentMeetId)
const meetName = computed(() => meetStore.currentMeet?.meetName || '田径运动会')

const REPORT_TYPES = [
  { value: 'PROGRAM', label: '秩序册' },
  { value: 'START_LIST', label: '检录分道表' },
  { value: 'RESULT', label: '成绩公告' },
  { value: 'POINTS', label: '积分表' },
  { value: 'MEDAL', label: '奖牌榜' },
  { value: 'CERT', label: '证书' }
]

const PAGE_SIZES = [
  { value: 'A4', label: 'A4' },
  { value: 'A3', label: 'A3' }
]

const ORIENTATIONS = [
  { value: 'PORTRAIT', label: '纵向' },
  { value: 'LANDSCAPE', label: '横向' }
]

function reportTypeLabel(reportType?: string): string {
  return REPORT_TYPES.find((item) => item.value === reportType)?.label || reportType || '-'
}

function reportTypeTagType(reportType?: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  if (reportType === 'PROGRAM') {
    return 'primary'
  }
  if (reportType === 'RESULT') {
    return 'success'
  }
  if (reportType === 'POINTS' || reportType === 'MEDAL') {
    return 'warning'
  }
  if (reportType === 'CERT') {
    return 'danger'
  }
  return 'info'
}

function orientationLabel(orientation?: string): string {
  return ORIENTATIONS.find((item) => item.value === orientation)?.label || '纵向'
}

function paperText(row: PrintTemplate): string {
  return `${row.pageSize || 'A4'} ${orientationLabel(row.orientation)}`
}

function switchLabel(value?: number): '是' | '否' {
  return value === 1 ? '是' : '否'
}

/* ------------------------------- 列表查询 ------------------------------- */

const query = reactive({ page: 1, size: 10, reportType: '', keyword: '' })
const rows = ref<PrintTemplate[]>([])
const total = ref(0)
const loading = ref(false)

const canList = computed(() => userStore.hasPerm('print:template:list'))
const canAdd = computed(() => userStore.hasPerm('print:template:add'))
const canEdit = computed(() => userStore.hasPerm('print:template:edit'))
const canDelete = computed(() => userStore.hasPerm('print:template:delete'))

async function load() {
  if (!meetStore.currentMeetId) {
    rows.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const page = await pageTemplates({
      page: query.page,
      size: query.size,
      meetId: meetStore.currentMeetId,
      reportType: query.reportType || undefined,
      keyword: query.keyword || undefined
    })
    rows.value = page?.records || []
    total.value = page?.total || 0
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
  query.reportType = ''
  query.keyword = ''
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

interface TemplateForm {
  id?: number
  templateCode: string
  templateName: string
  reportType: string
  pageSize: string
  orientation: string
  fontSize: number
  showPageNo: boolean
  showPrintTime: boolean
  headerHtml: string
  footerHtml: string
  signatureHtml: string
  isDefault: boolean
  remark: string
}

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<TemplateForm>({
  id: undefined,
  templateCode: '',
  templateName: '',
  reportType: 'PROGRAM',
  pageSize: 'A4',
  orientation: 'PORTRAIT',
  fontSize: 12,
  showPageNo: true,
  showPrintTime: true,
  headerHtml: '',
  footerHtml: '',
  signatureHtml: '',
  isDefault: false,
  remark: ''
})

const rules: FormRules<TemplateForm> = {
  templateCode: [
    { required: true, message: '请输入模板编码', trigger: 'blur' },
    { min: 2, max: 64, message: '长度在 2 到 64 个字符', trigger: 'blur' }
  ],
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  reportType: [{ required: true, message: '请选择报表类型', trigger: 'change' }],
  fontSize: [{ required: true, message: '请输入基准字号', trigger: 'blur' }]
}

function resetForm() {
  form.id = undefined
  form.templateCode = ''
  form.templateName = ''
  form.reportType = query.reportType || 'PROGRAM'
  form.pageSize = 'A4'
  form.orientation = 'PORTRAIT'
  form.fontSize = 12
  form.showPageNo = true
  form.showPrintTime = true
  form.headerHtml = ''
  form.footerHtml = ''
  form.signatureHtml = ''
  form.isDefault = false
  form.remark = ''
  formRef.value?.clearValidate()
}

function handleAdd() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canAdd.value) {
    ElMessage.warning('当前账号无模板新增权限（print:template:add）')
    return
  }
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: PrintTemplate) {
  if (!canEdit.value) {
    ElMessage.warning('当前账号无模板编辑权限（print:template:edit）')
    return
  }
  isEdit.value = true
  form.id = row.id
  form.templateCode = row.templateCode || ''
  form.templateName = row.templateName || ''
  form.reportType = row.reportType || 'PROGRAM'
  form.pageSize = row.pageSize || 'A4'
  form.orientation = row.orientation || 'PORTRAIT'
  form.fontSize = row.fontSize ?? 12
  form.showPageNo = row.showPageNo !== 0
  form.showPrintTime = row.showPrintTime !== 0
  form.headerHtml = row.headerHtml || ''
  form.footerHtml = row.footerHtml || ''
  form.signatureHtml = row.signatureHtml || ''
  form.isDefault = row.isDefault === 1
  form.remark = row.remark || ''
  dialogVisible.value = true
}

function buildPayload(): PrintTemplate {
  return {
    meetId: meetStore.currentMeetId,
    templateCode: form.templateCode.trim(),
    templateName: form.templateName.trim(),
    reportType: form.reportType,
    pageSize: form.pageSize,
    orientation: form.orientation,
    fontSize: form.fontSize,
    showPageNo: form.showPageNo ? 1 : 0,
    showPrintTime: form.showPrintTime ? 1 : 0,
    headerHtml: form.headerHtml.trim() || undefined,
    footerHtml: form.footerHtml.trim() || undefined,
    signatureHtml: form.signatureHtml.trim() || undefined,
    isDefault: form.isDefault ? 1 : 0,
    remark: form.remark.trim() || undefined
  }
}

async function handleSubmit() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (!meetStore.currentMeetId) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  submitting.value = true
  try {
    const payload = buildPayload()
    if (isEdit.value && form.id) {
      await updateTemplate(form.id, payload)
      ElMessage.success('模板已更新')
    } else {
      await createTemplate(payload)
      ElMessage.success('模板已新增')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

/* ------------------------------- 设为默认 / 删除 ------------------------------- */

const defaulting = ref<number | undefined>(undefined)

async function handleSetDefault(row: PrintTemplate) {
  if (!canEdit.value) {
    ElMessage.warning('当前账号无模板编辑权限（print:template:edit）')
    return
  }
  if (!row.id) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认把「${row.templateName || row.templateCode}」设为「${reportTypeLabel(row.reportType)}」的默认模板吗？同一报表类型仅允许一个默认模板，后端会自动把旧的默认模板置为否。`,
      '设为默认模板',
      { type: 'warning', confirmButtonText: '确认设为默认', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  defaulting.value = row.id
  try {
    await updateTemplate(row.id, { ...row, isDefault: 1 })
    ElMessage.success('已设为默认模板')
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    defaulting.value = undefined
  }
}

async function handleDelete(row: PrintTemplate) {
  if (!canDelete.value) {
    ElMessage.warning('当前账号无模板删除权限（print:template:delete）')
    return
  }
  if (!row.id) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除模板「${row.templateName || row.templateCode}」吗？删除后使用该模板的打印任务将回退到默认模板。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  loading.value = true
  try {
    await deleteTemplate(row.id)
    ElMessage.success('模板已删除')
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/* ------------------------------- 预览 ------------------------------- */

const previewVisible = ref(false)
const previewTemplate = ref<PrintTemplate | null>(null)

const PREVIEW_HEADERS = ['名次', '号码', '姓名', '单位', '成绩', '备注']
const PREVIEW_ROWS = [
  ['1', '1024', '张三', '第一中学', '11.23', 'PB'],
  ['2', '2031', '李四', '第二中学', '11.35', ''],
  ['3', '3087', '王五', '实验中学', '11.41', ''],
  ['4', '1120', '赵六', '第三中学', '11.58', '']
]

function handlePreview(row: PrintTemplate) {
  previewTemplate.value = row
  previewVisible.value = true
}

const previewStyle = computed(() => {
  const size = previewTemplate.value?.fontSize || 12
  return { fontSize: `${size}px` }
})

const previewPageClass = computed(() => ({
  'tm-print-page--landscape': previewTemplate.value?.orientation === 'LANDSCAPE',
  'preview-page-a3': previewTemplate.value?.pageSize === 'A3' && previewTemplate.value?.orientation !== 'LANDSCAPE',
  'preview-page-a3-landscape': previewTemplate.value?.pageSize === 'A3' && previewTemplate.value?.orientation === 'LANDSCAPE'
}))

const previewTitle = computed(() => previewTemplate.value?.templateName || '模板预览')

const previewSignatureParts = computed(() => {
  const text = previewTemplate.value?.signatureHtml || '记录员：____________　裁判长：____________　技术代表：____________'
  return text
    .split(/[\u3000\s]+/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
})

const previewTime = computed(() => new Date().toLocaleString('zh-CN'))

/* ------------------------------- 生命周期 ------------------------------- */

onMounted(async () => {
  if (!meetStore.loaded) {
    await meetStore.loadMeets().catch(() => undefined)
  }
  await load()
})
</script>

<template>
  <div class="tm-page template-config-page">
    <div class="tm-card no-print">
      <div class="tm-page-title">打印模板配置</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜按报表类型维护纸张规格、方向、基准字号、页眉页脚与签名区，每个报表类型仅一个默认模板
      </div>
    </div>

    <el-alert
      v-if="!canList"
      class="no-print"
      title="当前账号缺少打印模板查看权限（print:template:list）。"
      type="warning"
      :closable="false"
      show-icon
    />

    <!-- 筛选 -->
    <div class="tm-card no-print">
      <div class="tm-search-bar">
        <span>报表类型：</span>
        <el-select v-model="query.reportType" placeholder="全部类型" clearable style="width: 180px">
          <el-option v-for="item in REPORT_TYPES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <span>关键字：</span>
        <el-input
          v-model="query.keyword"
          placeholder="模板编码 / 名称"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :disabled="!meetReady || !canList" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <div class="tm-toolbar" style="margin-top: 12px">
        <div class="tm-toolbar-actions">
          <el-button v-if="canAdd" type="primary" :disabled="!meetReady" @click="handleAdd">新增模板</el-button>
        </div>
        <div class="tm-toolbar-actions">
          <span class="tm-form-tip">共 {{ total }} 个模板</span>
          <el-button :loading="loading" :disabled="!meetReady || !canList" @click="load">刷新</el-button>
        </div>
      </div>

      <div class="tm-form-tip" style="margin-top: 8px">
        未选择届次时列表与按钮不可用；模板编码在届次内唯一，保存时会由后端校验。
      </div>
    </div>

    <!-- 列表 -->
    <div class="tm-card no-print">
      <el-table v-loading="loading" :data="rows" border stripe row-key="id" max-height="560">
        <el-table-column prop="templateCode" label="模板编码" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as PrintTemplate).templateCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="templateName" label="模板名称" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as PrintTemplate).templateName || '-' }}</template>
        </el-table-column>
        <el-table-column label="报表类型" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="reportTypeTagType((row as PrintTemplate).reportType)" size="small" effect="plain">
              {{ reportTypeLabel((row as PrintTemplate).reportType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="纸张" width="120" align="center">
          <template #default="{ row }">{{ paperText(row as PrintTemplate) }}</template>
        </el-table-column>
        <el-table-column label="基准字号" width="100" align="center">
          <template #default="{ row }">{{ (row as PrintTemplate).fontSize ?? 12 }} px</template>
        </el-table-column>
        <el-table-column label="显示页码" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(row as PrintTemplate).showPageNo === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ switchLabel((row as PrintTemplate).showPageNo) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="打印时间" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(row as PrintTemplate).showPrintTime === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ switchLabel((row as PrintTemplate).showPrintTime) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="是否默认" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="(row as PrintTemplate).isDefault === 1" type="success" size="small">默认</el-tag>
            <span v-else class="tm-form-tip">否</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="(row as PrintTemplate).status === 'DISABLED' ? 'info' : 'success'" size="small">
              {{ (row as PrintTemplate).status || 'ENABLED' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as PrintTemplate).remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="270" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="canEdit" link type="primary" @click="handleEdit(row as PrintTemplate)">编辑</el-button>
            <el-button
              v-if="canEdit"
              link
              type="success"
              :loading="defaulting === (row as PrintTemplate).id"
              :disabled="(row as PrintTemplate).isDefault === 1"
              @click="handleSetDefault(row as PrintTemplate)"
            >
              设为默认
            </el-button>
            <el-button link type="info" @click="handlePreview(row as PrintTemplate)">预览</el-button>
            <el-button v-if="canDelete" link type="danger" @click="handleDelete(row as PrintTemplate)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无打印模板，可点击「新增模板」创建" />
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
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑打印模板' : '新增打印模板'"
      width="760px"
      destroy-on-close
      class="no-print"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="模板编码" prop="templateCode">
              <el-input v-model="form.templateCode" maxlength="64" placeholder="届次内唯一，如 RESULT_A4_DEFAULT" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模板名称" prop="templateName">
              <el-input v-model="form.templateName" maxlength="64" placeholder="请输入模板名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="报表类型" prop="reportType">
              <el-select v-model="form.reportType" placeholder="请选择报表类型" style="width: 100%">
                <el-option v-for="item in REPORT_TYPES" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纸张规格" prop="pageSize">
              <el-select v-model="form.pageSize" style="width: 100%">
                <el-option v-for="item in PAGE_SIZES" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="方向" prop="orientation">
              <el-select v-model="form.orientation" style="width: 100%">
                <el-option v-for="item in ORIENTATIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="基准字号" prop="fontSize">
              <el-input-number v-model="form.fontSize" :min="8" :max="14" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示页码">
              <el-switch v-model="form.showPageNo" active-text="显示" inactive-text="隐藏" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示打印时间">
              <el-switch v-model="form.showPrintTime" active-text="显示" inactive-text="隐藏" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="页眉文本">
              <el-input v-model="form.headerHtml" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="支持纯文本或简单 HTML" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="页脚文本">
              <el-input v-model="form.footerHtml" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="留空则显示打印时间" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="签名区文本">
              <el-input
                v-model="form.signatureHtml"
                type="textarea"
                :rows="2"
                maxlength="200"
                show-word-limit
                placeholder="如：记录员：____　裁判长：____　技术代表：____"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否默认">
              <el-switch v-model="form.isDefault" active-text="是" inactive-text="否" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input v-model="form.remark" maxlength="200" placeholder="模板用途说明" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="tm-form-tip">
          同一报表类型仅允许一个默认模板：勾选「是否默认」并保存后，后端会自动把该报表类型下旧的默认模板置为非默认。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 预览 -->
    <el-dialog v-model="previewVisible" :title="`模板预览：${previewTitle}`" width="900px" class="no-print preview-dialog">
      <div class="preview-scroll">
        <div class="tm-print-page" :class="previewPageClass" :style="previewStyle">
          <div class="tm-print-header">
            <div class="tm-print-title">{{ meetName }}</div>
            <div class="tm-print-subtitle">
              <span>{{ reportTypeLabel(previewTemplate?.reportType) }}示例</span>
              <span v-if="previewTemplate?.headerHtml" v-html="previewTemplate.headerHtml" />
            </div>
            <div class="tm-print-meta">
              {{ meetStore.currentMeet?.edition || '' }}
              <span v-if="meetStore.currentMeet?.place">｜{{ meetStore.currentMeet?.place }}</span>
              <span>｜纸张：{{ previewTemplate?.pageSize || 'A4' }} {{ orientationLabel(previewTemplate?.orientation) }}</span>
              <span>｜基准字号：{{ previewTemplate?.fontSize ?? 12 }} px</span>
            </div>
          </div>

          <div class="tm-print-section">
            <div class="tm-print-section-title">示例数据（模板效果预览）</div>
            <table class="tm-print-table">
              <thead>
                <tr>
                  <th v-for="(header, headerIndex) in PREVIEW_HEADERS" :key="headerIndex">{{ header }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, rowIndex) in PREVIEW_ROWS" :key="rowIndex">
                  <td v-for="(cell, cellIndex) in row" :key="cellIndex" class="tm-center">{{ cell }}</td>
                </tr>
              </tbody>
            </table>
            <div class="tm-print-note">
              该页面为纯前端示例渲染，仅用于确认纸张、字号、页眉页脚与签名区效果。
            </div>
          </div>

          <div class="tm-print-signature">
            <span v-for="(part, index) in previewSignatureParts" :key="index">{{ part }}</span>
          </div>

          <div class="tm-print-footer">
            <span v-if="previewTemplate?.showPrintTime === 1 || previewTemplate?.showPrintTime === undefined">
              打印时间：{{ previewTime }}
            </span>
            <span v-else>{{ previewTemplate?.footerHtml || '' }}</span>
            <span v-if="previewTemplate?.showPageNo !== 0">第 1 / 1 页</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.preview-scroll {
  max-height: 62vh;
  overflow: auto;
  background-color: #f5f7fa;
  padding: 12px;
}

.preview-page-a3 {
  width: 297mm;
  min-height: 420mm;
}

.preview-page-a3-landscape {
  width: 420mm;
  min-height: 297mm;
}

/* 预览页按模板纸张尺寸渲染；实际打印纸张由浏览器打印对话框决定 */
</style>
