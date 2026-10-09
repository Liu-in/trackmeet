<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  downloadCertificate,
  generateCertificates,
  pageCertificates,
  printCertificates,
  type Certificate
} from '@/api/standing'
import { downloadPdf, getPdfCapability, type PdfReportQuery } from '@/api/print'
import { pageEvents, type SportEvent } from '@/api/event'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'
import '@/styles/print.css'

/**
 * 证书打印：证书分页查询、批量生成、单张下载、批量下载（带进度）、批量打印计数与 A4 证书预览。
 * 权限编码：certificate:list（查看/下载）/ certificate:generate（生成）/ certificate:print（打印计数）/ certificate:export（批量下载与 PDF 导出）
 */
defineOptions({ name: 'CertificateList' })

const meetStore = useMeetStore()
const userStore = useUserStore()

const meetReady = computed(() => !!meetStore.currentMeetId)
const meetName = computed(() => meetStore.currentMeet?.meetName || '田径运动会')
const meetShortName = computed(
  () => meetStore.currentMeet?.shortName || meetStore.currentMeet?.edition || meetStore.currentMeet?.meetName || '届次'
)

const CERT_TYPES = [
  { value: 'RANK', label: '名次证书' },
  { value: 'RESULT', label: '成绩证书' },
  { value: 'MEDAL', label: '奖牌证书' },
  { value: 'PARTICIPATION', label: '参赛证书' }
]

function certTypeLabel(certType?: string): string {
  return CERT_TYPES.find((item) => item.value === certType)?.label || certType || '-'
}

function certTypeTagType(certType?: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  if (certType === 'RANK') {
    return 'warning'
  }
  if (certType === 'RESULT') {
    return 'success'
  }
  if (certType === 'MEDAL') {
    return 'danger'
  }
  return 'primary'
}

function statusText(status?: string): string {
  if (status === 'ISSUED') {
    return '已签发'
  }
  if (status === 'GENERATED') {
    return '已生成'
  }
  if (status === 'PRINTED') {
    return '已打印'
  }
  if (status === 'DRAFT') {
    return '草稿'
  }
  return status || '-'
}

function statusTagType(status?: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  if (status === 'ISSUED' || status === 'PRINTED') {
    return 'success'
  }
  if (status === 'GENERATED') {
    return 'primary'
  }
  if (status === 'DRAFT') {
    return 'warning'
  }
  return 'info'
}

/* ------------------------------- 权限 ------------------------------- */

const canList = computed(() => userStore.hasPerm('certificate:list'))
const canGenerate = computed(() => userStore.hasPerm('certificate:generate'))
const canExport = computed(() => userStore.hasPerm('certificate:export'))
const canPrint = computed(() => userStore.hasPerm('certificate:print'))

/* ------------------------------- 列表查询 ------------------------------- */

const query = reactive({ page: 1, size: 10, certType: '', unitId: undefined as number | undefined, keyword: '' })
const rows = ref<Certificate[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  if (!meetStore.currentMeetId) {
    rows.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const page = await pageCertificates({
      page: query.page,
      size: query.size,
      meetId: meetStore.currentMeetId,
      certType: query.certType || undefined,
      unitId: query.unitId,
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
  query.certType = ''
  query.unitId = undefined
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

/* ------------------------------- 选择 ------------------------------- */

const selectedRows = ref<Certificate[]>([])

function handleSelectionChange(selection: Certificate[]) {
  selectedRows.value = selection || []
}

function selectedIds(): number[] {
  return selectedRows.value.map((item) => item.id).filter((id): id is number => typeof id === 'number')
}

/** 预览用证书：优先选中项，其次当前页 */
const previewCertificates = computed<Certificate[]>(() =>
  selectedRows.value.length > 0 ? selectedRows.value.slice(0, 20) : rows.value.slice(0, 20)
)

/* ------------------------------- 生成证书 ------------------------------- */

const generateVisible = ref(false)
const generating = ref(false)
const events = ref<SportEvent[]>([])
const eventsLoading = ref(false)

const generateForm = reactive({
  certType: 'RANK',
  eventId: undefined as number | undefined,
  groupId: undefined as number | undefined,
  unitId: undefined as number | undefined
})

async function loadEvents() {
  if (!meetStore.currentMeetId) {
    events.value = []
    return
  }
  eventsLoading.value = true
  try {
    const page = await pageEvents({ page: 1, size: 200, meetId: meetStore.currentMeetId })
    events.value = page?.records || []
  } catch {
    events.value = []
  } finally {
    eventsLoading.value = false
  }
}

function openGenerate() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canGenerate.value) {
    ElMessage.warning('当前账号无证书生成权限（certificate:generate）')
    return
  }
  generateForm.certType = query.certType || 'RANK'
  generateForm.eventId = undefined
  generateForm.groupId = undefined
  generateForm.unitId = undefined
  generateVisible.value = true
  loadEvents()
}

async function handleGenerate() {
  if (!meetStore.currentMeetId) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!generateForm.certType) {
    ElMessage.warning('请选择证书类型')
    return
  }
  generating.value = true
  try {
    const count = await generateCertificates({
      meetId: meetStore.currentMeetId,
      certType: generateForm.certType,
      eventId: generateForm.eventId,
      groupId: generateForm.groupId,
      unitId: generateForm.unitId
    })
    ElMessage.success(`已生成 ${count ?? 0} 条证书`)
    generateVisible.value = false
    query.page = 1
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    generating.value = false
  }
}

/* ------------------------------- 下载与打印 ------------------------------- */

function sanitizeFilePart(text: string): string {
  return text.replace(/[\\/:*?"<>|]/g, '').trim() || '证书'
}

function dateStamp(): string {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`
}

const downloadingId = ref<number | undefined>(undefined)

async function handleDownloadOne(row: Certificate) {
  if (!canList.value) {
    ElMessage.warning('当前账号无证书查看权限（certificate:list）')
    return
  }
  if (!row.id) {
    return
  }
  downloadingId.value = row.id
  try {
    const filename = `${sanitizeFilePart(row.certNo || '证书')}_${sanitizeFilePart(row.athleteName || '')}_${dateStamp()}.xlsx`
    await downloadCertificate(row.id, meetStore.currentMeetId, filename)
    ElMessage.success('已开始下载证书文件')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    downloadingId.value = undefined
  }
}

/* 批量下载：逐张触发，带进度条 */
const batchVisible = ref(false)
const batchRunning = ref(false)
const batchDone = ref(0)
const batchTotal = ref(0)

const batchPercent = computed(() => (batchTotal.value === 0 ? 0 : Math.round((batchDone.value / batchTotal.value) * 100)))

async function runBatchDownload() {
  const targets = selectedRows.value.filter((item): item is Certificate & { id: number } => typeof item.id === 'number')
  if (!targets.length) {
    ElMessage.warning('请先勾选要下载的证书')
    return
  }
  batchVisible.value = true
  batchRunning.value = true
  batchDone.value = 0
  batchTotal.value = targets.length
  let failed = 0
  for (const cert of targets) {
    try {
      const filename = `${sanitizeFilePart(cert.certNo || '证书')}_${sanitizeFilePart(cert.athleteName || '')}_${dateStamp()}.xlsx`
      await downloadCertificate(cert.id, meetStore.currentMeetId, filename)
    } catch {
      failed += 1
    } finally {
      batchDone.value += 1
    }
    // 间隔 300ms，避免浏览器把连续下载判定为恶意行为
    await new Promise((resolve) => window.setTimeout(resolve, 300))
  }
  batchRunning.value = false
  if (failed > 0) {
    ElMessage.warning(`批量下载完成：成功 ${batchTotal.value - failed} 张，失败 ${failed} 张`)
  } else {
    ElMessage.success(`批量下载完成：共 ${batchTotal.value} 张`)
  }
}

function handleBatchDownload() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canExport.value) {
    ElMessage.warning('当前账号无证书导出权限（certificate:export）')
    return
  }
  runBatchDownload()
}

/* 批量打印：先计数后刷新，再触发浏览器打印 */
const printing = ref(false)

async function markPrinted(ids: number[]) {
  try {
    await printCertificates(ids, meetStore.currentMeetId)
  } catch {
    ElMessage.warning('打印次数累加失败，不影响本次打印')
  }
}

async function handleBatchPrint() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canPrint.value) {
    ElMessage.warning('当前账号无证书打印权限（certificate:print）')
    return
  }
  const ids = selectedIds()
  if (!ids.length) {
    ElMessage.warning('请先勾选要打印的证书')
    return
  }
  printing.value = true
  try {
    await markPrinted(ids)
    await load()
  } finally {
    printing.value = false
  }
  await nextTick()
  window.print()
}

async function handlePrintPreview() {
  if (!previewCertificates.value.length) {
    ElMessage.warning('暂无可打印的证书，请先查询或生成证书')
    return
  }
  const ids = previewCertificates.value.map((item) => item.id).filter((id): id is number => typeof id === 'number')
  if (ids.length) {
    await markPrinted(ids)
    await load()
  }
  window.print()
}

/** 单张打印记录：累加 print_count 并刷新列表 */
async function handlePrintOne(row: Certificate) {
  if (!canPrint.value) {
    ElMessage.warning('当前账号无证书打印权限（certificate:print）')
    return
  }
  if (!row.id) {
    return
  }
  printing.value = true
  try {
    await printCertificates([row.id], meetStore.currentMeetId)
    ElMessage.success('已累加打印次数')
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    printing.value = false
  }
}

/* 批量打印 PDF：走服务端统一 PDF 能力 */
const pdfCapabilityAvailable = ref(true)
const downloadingPdf = ref(false)

async function loadPdfCapability() {
  try {
    const capability = await getPdfCapability()
    pdfCapabilityAvailable.value = capability?.available !== false
  } catch {
    pdfCapabilityAvailable.value = false
  }
}

async function handleDownloadPdf() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canExport.value) {
    ElMessage.warning('当前账号无证书导出权限（certificate:export）')
    return
  }
  if (!pdfCapabilityAvailable.value) {
    ElMessage.warning('服务端缺少中文字体，无法生成 PDF，请改用「打印 / 另存为 PDF」')
    return
  }
  downloadingPdf.value = true
  try {
    const params: PdfReportQuery = {
      meetId: meetStore.currentMeetId,
      reportType: 'certificates',
      certType: query.certType || undefined,
      eventId: undefined,
      groupId: undefined,
      unitId: query.unitId
    }
    const filename = `证书_${sanitizeFilePart(meetShortName.value)}_${dateStamp()}.pdf`
    await downloadPdf('certificates', params, filename)
    ElMessage.success(`已开始下载：${filename}`)
  } catch {
    pdfCapabilityAvailable.value = false
  } finally {
    downloadingPdf.value = false
  }
}

/* ------------------------------- 安全渲染 ------------------------------- */

/** 去掉 HTML 标签并转义剩余特殊字符，避免 v-html 注入 */
function safeText(value?: string | number | null): string {
  if (value === undefined || value === null) {
    return ''
  }
  return String(value)
    .replace(/<[^>]+>/g, '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })

function printTimeText(): string {
  return new Date().toLocaleString('zh-CN')
}

/** 名次中文（1 → 第一名） */
const RANK_LABELS = ['一', '二', '三', '四', '五', '六', '七', '八']

function rankText(rankNo?: number): string {
  if (!rankNo || rankNo < 1) {
    return ''
  }
  const label = RANK_LABELS[rankNo - 1]
  return label ? `第${label}名` : `第 ${rankNo} 名`
}

/* ------------------------------- 生命周期 ------------------------------- */

onMounted(async () => {
  if (!meetStore.loaded) {
    await meetStore.loadMeets().catch(() => undefined)
  }
  if (meetStore.currentMeetId && (!meetStore.groups.length || !meetStore.units.length)) {
    await meetStore.loadGroupsAndUnits().catch(() => undefined)
  }
  await Promise.all([load(), loadPdfCapability()])
})
</script>

<template>
  <div class="tm-page certificate-page">
    <div class="tm-card no-print">
      <div class="tm-page-title">证书打印</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜共 {{ total }} 张证书｜支持批量生成、单张下载、批量下载（带进度）、服务端 PDF 与 A4 证书预览打印
      </div>
    </div>

    <el-alert
      v-if="!canList"
      class="no-print"
      title="当前账号缺少证书查看权限（certificate:list）。"
      type="warning"
      :closable="false"
      show-icon
    />

    <!-- 筛选 -->
    <div class="tm-card no-print">
      <div class="tm-search-bar">
        <span>证书类型：</span>
        <el-select v-model="query.certType" placeholder="全部类型" clearable style="width: 170px">
          <el-option v-for="item in CERT_TYPES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <span>单位：</span>
        <el-select v-model="query.unitId" placeholder="全部单位" filterable clearable style="width: 180px">
          <el-option v-for="item in meetStore.units" :key="item.id" :label="item.unitName" :value="item.id as number" />
        </el-select>
        <span>关键字：</span>
        <el-input
          v-model="query.keyword"
          placeholder="证书编号 / 姓名"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :disabled="!meetReady || !canList" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <div class="tm-toolbar" style="margin-top: 12px">
        <div class="tm-toolbar-actions">
          <el-button v-if="canGenerate" type="primary" :disabled="!meetReady" @click="openGenerate">生成证书</el-button>
          <el-button v-if="canExport" :disabled="!meetReady || !selectedRows.length" @click="handleBatchDownload">
            批量下载
          </el-button>
          <el-button
            v-if="canExport"
            type="success"
            :loading="downloadingPdf"
            :disabled="!meetReady || !pdfCapabilityAvailable"
            @click="handleDownloadPdf"
          >
            批量打印 PDF
          </el-button>
          <el-button type="primary" :loading="printing" :disabled="!meetReady || !canList" @click="handlePrintPreview">
            打印 / 另存为 PDF
          </el-button>
        </div>
        <div class="tm-toolbar-actions">
          <span class="tm-form-tip">
            已选 {{ selectedRows.length }} 张｜预览 {{ previewCertificates.length }} 张
          </span>
          <el-button :loading="loading" :disabled="!meetReady || !canList" @click="load">刷新</el-button>
        </div>
      </div>

      <div class="tm-form-tip" style="margin-top: 8px">
        未选择届次时按钮不可用；勾选证书后可批量下载或批量打印，A4 预览区一证一页，打印时筛选与按钮自动隐藏。
      </div>
    </div>

    <!-- 列表 -->
    <div class="tm-card no-print">
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="rows"
        border
        stripe
        row-key="id"
        max-height="520"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column label="证书编号" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as Certificate).certNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="certTypeTagType((row as Certificate).certType)" size="small" effect="plain">
              {{ certTypeLabel((row as Certificate).certType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="姓名" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as Certificate).athleteName || '-' }}</template>
        </el-table-column>
        <el-table-column label="单位" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as Certificate).unitName || '-' }}</template>
        </el-table-column>
        <el-table-column label="项目" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as Certificate).eventName || '-' }}</template>
        </el-table-column>
        <el-table-column label="名次" width="90" align="center">
          <template #default="{ row }">{{ (row as Certificate).rankNo ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="成绩" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as Certificate).resultText || '-' }}</template>
        </el-table-column>
        <el-table-column label="签发日期" width="120" align="center">
          <template #default="{ row }">{{ (row as Certificate).issueDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="打印次数" width="100" align="center">
          <template #default="{ row }">{{ (row as Certificate).printCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType((row as Certificate).status)" size="small">
              {{ statusText((row as Certificate).status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :loading="downloadingId === (row as Certificate).id"
              @click="handleDownloadOne(row as Certificate)"
            >
              下载单张
            </el-button>
            <el-button link type="info" @click="handlePrintOne(row as Certificate)">打印记录</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无证书数据，可点击「生成证书」批量创建" />
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

    <!-- A4 证书预览 -->
    <div class="certificate-preview">
      <div
        v-for="(cert, index) in previewCertificates"
        :key="cert.id ?? index"
        class="tm-print-page tm-print-page--landscape cert-page"
      >
        <div class="cert-body">
          <div class="tm-print-header cert-header">
            <div class="tm-print-title cert-title" v-html="safeText(meetName)" />
            <div class="tm-print-subtitle cert-subtitle">获 奖 证 书</div>
            <div class="tm-print-meta">
              {{ certTypeLabel(cert.certType) }}｜证书编号：<span v-html="safeText(cert.certNo || '-')" />
            </div>
          </div>

          <div class="cert-content">
            <p class="cert-line">
              <span class="cert-strong" v-html="safeText(cert.athleteName || '　')" />
              <span class="cert-unit" v-html="safeText(cert.unitName || '')" />
            </p>
            <p class="cert-line">
              在 <span class="cert-strong" v-html="safeText(cert.eventName || '-')" /> 比赛中，
            </p>
            <p class="cert-line">
              以优异成绩获得
              <span class="cert-strong" v-html="safeText(rankText(cert.rankNo) || '　')" />
              <span v-if="cert.resultText">，成绩 <span class="cert-strong" v-html="safeText(cert.resultText)" />。</span>
              <span v-else>。</span>
            </p>
            <p class="cert-line cert-note">特发此证，以资鼓励。</p>
          </div>

          <div class="tm-print-signature cert-signature">
            <span v-html="safeText(meetName || '　')" />
            <div class="cert-seal">
              <span>（盖章）</span>
              <span>签发日期：{{ cert.issueDate || today }}</span>
            </div>
          </div>

          <div class="tm-print-footer">
            <span>打印时间：{{ printTimeText() }}</span>
            <span>第 {{ index + 1 }} / {{ previewCertificates.length }} 页</span>
          </div>
        </div>
      </div>

      <div v-if="!previewCertificates.length" class="tm-card">
        <div class="tm-print-empty">暂无证书记录：请先查询或点击「生成证书」批量创建。</div>
      </div>
    </div>

    <!-- 生成证书 -->
    <el-dialog v-model="generateVisible" title="生成证书" width="560px" destroy-on-close class="no-print">
      <el-form :model="generateForm" label-width="100px">
        <el-form-item label="证书类型" required>
          <el-select v-model="generateForm.certType" style="width: 100%">
            <el-option v-for="item in CERT_TYPES" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <el-select
            v-model="generateForm.eventId"
            :loading="eventsLoading"
            placeholder="全部项目"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option v-for="item in events" :key="item.id" :label="item.eventName" :value="item.id as number" />
          </el-select>
        </el-form-item>
        <el-form-item label="组别">
          <el-select v-model="generateForm.groupId" placeholder="全部组别" filterable clearable style="width: 100%">
            <el-option v-for="item in meetStore.groups" :key="item.id" :label="item.groupName" :value="item.id as number" />
          </el-select>
        </el-form-item>
        <el-form-item label="单位">
          <el-select v-model="generateForm.unitId" placeholder="全部单位" filterable clearable style="width: 100%">
            <el-option v-for="item in meetStore.units" :key="item.id" :label="item.unitName" :value="item.id as number" />
          </el-select>
        </el-form-item>
        <div class="tm-form-tip">
          生成的证书数量取决于所选范围内的成绩 / 名次数据；不选项目与单位时按全部范围内已审核成绩生成。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="generateVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="handleGenerate">开始生成</el-button>
      </template>
    </el-dialog>

    <!-- 批量下载进度 -->
    <el-dialog
      v-model="batchVisible"
      title="批量下载证书"
      width="460px"
      :close-on-click-modal="false"
      :show-close="!batchRunning"
      class="no-print"
    >
      <el-progress :percentage="batchPercent" :status="batchRunning ? undefined : 'success'" />
      <div class="tm-form-tip" style="margin-top: 12px">
        已处理 {{ batchDone }} / {{ batchTotal }} 张{{ batchRunning ? '，浏览器会依次保存文件，请勿关闭页面' : '，全部处理完成' }}
      </div>
      <template #footer>
        <el-button :disabled="batchRunning" @click="batchVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.certificate-preview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cert-page {
  display: flex;
  align-items: center;
  justify-content: center;
  /* 证书为横向纸张，内容少时保持 A4 横向最小高度，内容多时自然增高 */
  min-height: 180mm;
}

.cert-body {
  width: 100%;
}

.cert-header {
  border-bottom: none;
}

.cert-title {
  font-size: 22px;
}

.cert-subtitle {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 10px;
  margin-top: 10px;
}

.cert-content {
  margin-top: 26px;
  line-height: 2.4;
  font-size: 15px;
  text-indent: 2em;
}

.cert-line {
  margin: 0 0 8px;
}

.cert-strong {
  font-weight: 700;
  border-bottom: 1px solid #1f2329;
  padding: 0 6px;
}

.cert-unit {
  margin-left: 12px;
}

.cert-note {
  margin-top: 24px;
}

.cert-signature {
  margin-top: 40px;
  font-size: 14px;
}

.cert-seal {
  display: flex;
  flex-direction: column;
  gap: 18px;
  text-align: right;
}
</style>
