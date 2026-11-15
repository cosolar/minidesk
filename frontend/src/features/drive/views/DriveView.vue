<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import type { Component } from 'vue'
import {
  ArrowRight,
  Check,
  CircleCheck,
  Cloudy,
  Coin,
  Connection,
  CopyDocument,
  DataLine,
  Delete,
  Document,
  Download,
  EditPen,
  Files,
  Folder,
  FolderAdd,
  FolderOpened,
  Grid,
  Headset,
  InfoFilled,
  List,
  Monitor,
  Picture,
  Plus,
  Refresh,
  Search,
  Setting,
  UploadFilled,
  VideoCamera,
  View,
  Warning,
} from '@element-plus/icons-vue'
import {
  driveApi,
  fileUrl,
  uploadDriveFile,
  type DriveFile,
  type DriveTestResult,
  type UploadTask,
} from '../api'

type SortKey = 'name' | 'size' | 'time' | 'type'

const SORT_OPTIONS: Array<{ key: SortKey; label: string }> = [
  { key: 'name', label: '名称' },
  { key: 'size', label: '大小' },
  { key: 'time', label: '最近' },
  { key: 'type', label: '类型' },
]

const TEXT_EXT = ['txt', 'md', 'csv', 'json', 'xml', 'yaml', 'yml', 'log', 'html', 'css', 'js', 'ts']
const queryClient = useQueryClient()

const parentId = ref(0)
const crumbs = ref<Array<{ id: number; name: string }>>([{ id: 0, name: '我的网盘' }])
const keyword = ref('')
const viewMode = ref<'grid' | 'list'>('grid')
const sortBy = ref<SortKey>('name')
const dragActive = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const listQuery = useQuery({
  queryKey: ['drive-files', parentId, keyword],
  queryFn: () => driveApi.list(parentId.value, keyword.value || undefined),
  staleTime: 10_000,
})
const statsQuery = useQuery({
  queryKey: ['drive-stats'],
  queryFn: () => driveApi.stats(),
  staleTime: 30_000,
})
const configQuery = useQuery({
  queryKey: ['drive-config'],
  queryFn: () => driveApi.config(),
  staleTime: 60_000,
})

const items = computed(() => listQuery.data.value ?? [])
const stats = computed(() => statsQuery.data.value)
const config = computed(() => configQuery.data.value)
const isS3 = computed(() => config.value?.storageType === 's3')

function kindOf(file: DriveFile): { label: string; icon: Component; tone: string } {
  const mime = file.mime ?? ''
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (file.type === 'folder') {
    return { label: '文件夹', icon: Folder, tone: '#FFB020' }
  }
  if (mime.startsWith('image/') || ['png', 'jpg', 'jpeg', 'webp', 'gif', 'svg', 'bmp'].includes(ext)) {
    return { label: '图片', icon: Picture, tone: '#4C6FFF' }
  }
  if (mime.startsWith('video/') || ['mp4', 'mov', 'mkv', 'webm', 'avi'].includes(ext)) {
    return { label: '视频', icon: VideoCamera, tone: '#8B7CF6' }
  }
  if (mime.startsWith('audio/') || ['mp3', 'wav', 'flac', 'm4a'].includes(ext)) {
    return { label: '音频', icon: Headset, tone: '#2AA6C9' }
  }
  if (ext === 'pdf') {
    return { label: 'PDF', icon: Document, tone: '#F25F5C' }
  }
  if (['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'md', 'csv', 'json', 'xml'].includes(ext)) {
    return { label: '文档', icon: Document, tone: '#0B7D77' }
  }
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) {
    return { label: '压缩包', icon: Files, tone: '#FF9F43' }
  }
  return { label: '文件', icon: Files, tone: '#6B7C87' }
}

function isImage(file: DriveFile): boolean {
  return file.type === 'file' && (file.mime ?? '').startsWith('image/')
}
function isVideo(file: DriveFile): boolean {
  return file.type === 'file' && (file.mime ?? '').startsWith('video/')
}
function isText(file: DriveFile): boolean {
  if (file.type !== 'file') {
    return false
  }
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  return (file.mime ?? '').startsWith('text/') || TEXT_EXT.includes(ext)
}

function sorted(list: DriveFile[]): DriveFile[] {
  const copy = [...list]
  if (sortBy.value === 'size') {
    copy.sort((a, b) => b.size - a.size || a.name.localeCompare(b.name, 'zh-CN'))
  } else if (sortBy.value === 'time') {
    copy.sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
  } else if (sortBy.value === 'type') {
    copy.sort((a, b) => a.name.split('.').pop()!.localeCompare(b.name.split('.').pop()!))
  } else {
    copy.sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
  }
  return copy
}

const folders = computed(() => sorted(items.value.filter((item) => item.type === 'folder')))
const files = computed(() => sorted(items.value.filter((item) => item.type === 'file')))

function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes
  let index = -1
  do {
    value /= 1024
    index++
  } while (value >= 1024 && index < units.length - 1)
  return `${value.toFixed(value < 10 ? 1 : 0)} ${units[index]}`
}

function formatDate(value: string): string {
  if (!value) {
    return ''
  }
  return value.replace('T', ' ').slice(0, 16)
}

const usedPercent = computed(() => {
  const current = stats.value
  if (!current || !current.capacityBytes) {
    return 0
  }
  return Math.min(100, Math.max(1, (current.usedBytes / current.capacityBytes) * 100))
})

const statCards = computed(() => [
  { label: '文件', value: stats.value?.fileCount ?? 0, icon: Files, tone: '#0B7D77' },
  { label: '文件夹', value: stats.value?.folderCount ?? 0, icon: FolderOpened, tone: '#FFB020' },
  { label: '图片', value: stats.value?.imageCount ?? 0, icon: Picture, tone: '#4C6FFF' },
  { label: '文档', value: stats.value?.documentCount ?? 0, icon: Document, tone: '#8B7CF6' },
])

function invalidate(): void {
  void queryClient.invalidateQueries({ queryKey: ['drive-files'] })
  void queryClient.invalidateQueries({ queryKey: ['drive-stats'] })
}

function enterFolder(file: DriveFile): void {
  if (file.type !== 'folder') {
    void openPreview(file)
    return
  }
  parentId.value = file.id
  crumbs.value.push({ id: file.id, name: file.name })
  keyword.value = ''
}

function goCrumb(index: number): void {
  const target = crumbs.value[index]
  if (!target) {
    return
  }
  crumbs.value = crumbs.value.slice(0, index + 1)
  parentId.value = target.id
  keyword.value = ''
}

function downloadFile(file: DriveFile): void {
  const anchor = document.createElement('a')
  anchor.href = fileUrl(file, true)
  anchor.target = '_blank'
  anchor.rel = 'noopener'
  anchor.click()
}

async function copyLink(file: DriveFile): Promise<void> {
  const url = /^https?:\/\//.test(file.link)
    ? file.link
    : `${location.origin}${fileUrl(file)}`
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('浏览器不允许复制，请手动打开')
  }
}

// ---------- 预览 ----------
const previewVisible = ref(false)
const previewFile = ref<DriveFile | null>(null)
const previewText = ref('')

async function openPreview(file: DriveFile): Promise<void> {
  if (file.type === 'folder') {
    enterFolder(file)
    return
  }
  if (!isImage(file) && !isVideo(file) && !isText(file)) {
    downloadFile(file)
    return
  }
  previewFile.value = file
  previewText.value = ''
  previewVisible.value = true
  if (isText(file)) {
    try {
      const response = await fetch(fileUrl(file))
      previewText.value = (await response.text()).slice(0, 100_000)
    } catch {
      previewText.value = '无法读取文件内容'
    }
  }
}

// ---------- 上传 ----------
const uploads = ref<UploadTask[]>([])
const uploading = computed(() => uploads.value.some((item) => item.status === 'uploading'))

async function handleFiles(list: FileList | File[]): Promise<void> {
  const pending = Array.from(list)
  if (!pending.length) {
    return
  }
  for (const file of pending) {
    const task: UploadTask = { name: file.name, size: file.size, percent: 0, status: 'uploading' }
    uploads.value.push(task)
    try {
      await uploadDriveFile(file, parentId.value, (percent) => {
        task.percent = percent
      })
      task.percent = 100
      task.status = 'done'
    } catch (error) {
      task.status = 'error'
      task.message = error instanceof Error ? error.message : '上传失败'
      ElMessage.error(`${file.name}：${task.message}`)
    }
  }
  invalidate()
  window.setTimeout(() => {
    uploads.value = uploads.value.filter((item) => item.status === 'uploading')
  }, 4000)
}

function onChoose(event: Event): void {
  const input = event.target as HTMLInputElement | null
  if (input?.files?.length) {
    void handleFiles(input.files)
    input.value = ''
  }
}

function onDrop(event: DragEvent): void {
  dragActive.value = false
  if (event.dataTransfer?.files?.length) {
    void handleFiles(event.dataTransfer.files)
  }
}

function clearUploads(): void {
  uploads.value = []
}

// ---------- 新建 / 重命名 / 删除 ----------
const nameDialog = ref(false)
const nameMode = ref<'folder' | 'rename'>('folder')
const nameTarget = ref<DriveFile | null>(null)
const nameFormRef = ref<FormInstance>()
const nameForm = reactive({ name: '' })
const saving = ref(false)

function openNameDialog(mode: 'folder' | 'rename', file?: DriveFile): void {
  nameMode.value = mode
  nameTarget.value = file ?? null
  nameForm.name = mode === 'rename' ? (file?.name ?? '') : ''
  nameDialog.value = true
}

async function submitName(): Promise<void> {
  const valid = await nameFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    if (nameMode.value === 'folder') {
      await driveApi.createFolder(parentId.value, nameForm.name.trim())
      ElMessage.success('文件夹已创建')
    } else if (nameTarget.value) {
      await driveApi.rename(nameTarget.value.id, nameForm.name.trim())
      ElMessage.success('已重命名')
    }
    nameDialog.value = false
    invalidate()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function removeItem(file: DriveFile): Promise<void> {
  const extra = file.type === 'folder' && file.childCount
    ? `其中包含 ${file.childCount} 个项目，将一起删除。`
    : ''
  const ok = await ElMessageBox.confirm(
    `确定删除「${file.name}」？${extra}此操作不可恢复。`,
    '确认删除', { type: 'warning' },
  ).catch(() => false)
  if (!ok) {
    return
  }
  try {
    await driveApi.remove(file.id)
    ElMessage.success('已删除')
    invalidate()
  } catch {
    // 拦截器已提示
  }
}

// ---------- 存储配置 ----------
const configVisible = ref(false)
const testResult = ref<DriveTestResult | null>(null)
const testing = ref(false)
const configForm = reactive({
  storageType: 'local',
  endpoint: '',
  bucket: '',
  region: 'us-east-1',
  accessKey: '',
  secretKey: '',
  pathStyle: true,
  prefix: '',
  publicUrl: '',
  acl: '',
})

watch(config, (value) => {
  if (!value) {
    return
  }
  Object.assign(configForm, {
    storageType: value.storageType || 'local',
    endpoint: value.endpoint || '',
    bucket: value.bucket || '',
    region: value.region || 'us-east-1',
    accessKey: value.accessKey || '',
    secretKey: value.maskedSecret || '',
    pathStyle: value.pathStyle,
    prefix: value.prefix || '',
    publicUrl: value.publicUrl || '',
    acl: value.acl || '',
  })
  testResult.value = null
}, { immediate: true })

async function saveStorage(): Promise<void> {
  if (configForm.storageType === 's3' && (!configForm.endpoint.trim() || !configForm.bucket.trim())) {
    ElMessage.warning('请填写 Endpoint 与 Bucket')
    return
  }
  saving.value = true
  try {
    const saved = await driveApi.saveConfig({
      storageType: configForm.storageType,
      endpoint: configForm.endpoint.trim(),
      bucket: configForm.bucket.trim(),
      region: configForm.region.trim() || 'us-east-1',
      accessKey: configForm.accessKey.trim(),
      secretKey: configForm.secretKey,
      pathStyle: configForm.pathStyle,
      prefix: configForm.prefix.trim(),
      publicUrl: configForm.publicUrl.trim(),
      acl: configForm.acl.trim(),
    })
    queryClient.setQueryData(['drive-config'], saved)
    configForm.secretKey = saved.maskedSecret
    void queryClient.invalidateQueries({ queryKey: ['drive-stats'] })
    ElMessage.success('存储配置已保存')
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function testStorage(): Promise<void> {
  testing.value = true
  try {
    await driveApi.saveConfig({
      storageType: configForm.storageType,
      endpoint: configForm.endpoint.trim(),
      bucket: configForm.bucket.trim(),
      region: configForm.region.trim() || 'us-east-1',
      accessKey: configForm.accessKey.trim(),
      secretKey: configForm.secretKey,
      pathStyle: configForm.pathStyle,
      prefix: configForm.prefix.trim(),
      publicUrl: configForm.publicUrl.trim(),
      acl: configForm.acl.trim(),
    })
    testResult.value = await driveApi.testConfig()
    const saved = await driveApi.config()
    queryClient.setQueryData(['drive-config'], saved)
    configForm.secretKey = saved.maskedSecret
    void queryClient.invalidateQueries({ queryKey: ['drive-stats'] })
  } catch {
    testResult.value = { ok: false, message: '配置保存失败，请检查输入', storageType: configForm.storageType }
  } finally {
    testing.value = false
  }
}
</script>

<template>
  <section
    class="dv-page"
    @dragover.prevent="dragActive = true"
    @dragleave.prevent="dragActive = false"
    @drop.prevent="onDrop"
  >
    <header class="dv-hero">
      <div class="dv-hero__left">
        <span class="dv-hero__badge"><el-icon><Cloudy /></el-icon></span>
        <div>
          <h1>我的网盘</h1>
          <p>上传、整理、预览文件；本地磁盘与 S3 对象存储随时切换</p>
        </div>
      </div>
      <div class="dv-hero__actions">
        <span class="dv-storage" :class="{ 'is-s3': isS3 }">
          <el-icon><component :is="isS3 ? Connection : Monitor" /></el-icon>
          {{ stats?.storageLabel ?? '本地磁盘' }}
          <i v-if="stats && !stats.ready" class="dv-storage__warn" />
        </span>
        <button type="button" class="dv-btn dv-btn--ghost" @click="configVisible = true">
          <el-icon><Setting /></el-icon>存储设置
        </button>
        <button type="button" class="dv-btn dv-btn--ghost" @click="openNameDialog('folder')">
          <el-icon><FolderAdd /></el-icon>新建文件夹
        </button>
        <button type="button" class="dv-btn dv-btn--primary" @click="fileInput?.click()">
          <el-icon><UploadFilled /></el-icon>上传文件
        </button>
        <input ref="fileInput" type="file" multiple hidden @change="onChoose" />
      </div>
    </header>

    <div class="dv-overview">
      <ul class="dv-stats">
        <li v-for="item in statCards" :key="item.label" class="dv-stat">
          <span class="dv-stat__icon" :style="{ '--tone': item.tone }">
            <el-icon><component :is="item.icon" /></el-icon>
          </span>
          <b>{{ item.value }}</b>
          <span class="dv-stat__label">{{ item.label }}</span>
        </li>
      </ul>
      <div class="dv-usage">
        <div class="dv-usage__head">
          <span><el-icon><Coin /></el-icon>空间用量</span>
          <b>{{ formatSize(stats?.usedBytes ?? 0) }}</b>
        </div>
        <div class="dv-usage__bar">
          <i :style="{ width: `${usedPercent}%` }" />
        </div>
        <p v-if="stats?.capacityBytes">
          磁盘总容量 {{ formatSize(stats.capacityBytes) }} · 已用 {{ usedPercent.toFixed(2) }}%
        </p>
        <p v-else>对象存储不提供容量上限，仅统计已上传文件的总大小</p>
      </div>
    </div>

    <div class="dv-toolbar">
      <nav class="dv-crumbs" aria-label="目录路径">
        <button
          v-for="(crumb, index) in crumbs"
          :key="`${crumb.id}-${index}`"
          type="button"
          :class="{ 'is-current': index === crumbs.length - 1 }"
          @click="goCrumb(index)"
        >
          <el-icon v-if="index === 0"><FolderOpened /></el-icon>
          <span>{{ crumb.name }}</span>
          <el-icon v-if="index < crumbs.length - 1" class="dv-crumbs__sep"><ArrowRight /></el-icon>
        </button>
      </nav>
      <div class="dv-toolbar__right">
        <div class="dv-search">
          <el-icon><Search /></el-icon>
          <input v-model="keyword" type="search" placeholder="搜索当前网盘的文件" />
          <span v-if="keyword" class="dv-search__count">{{ items.length }} 条</span>
        </div>
        <div class="dv-sort">
          <button
            v-for="item in SORT_OPTIONS"
            :key="item.key"
            type="button"
            :class="{ 'is-active': sortBy === item.key }"
            @click="sortBy = item.key"
          >{{ item.label }}</button>
        </div>
        <div class="dv-views">
          <button type="button" title="网格视图" :class="{ 'is-active': viewMode === 'grid' }" @click="viewMode = 'grid'">
            <el-icon><Grid /></el-icon>
          </button>
          <button type="button" title="列表视图" :class="{ 'is-active': viewMode === 'list' }" @click="viewMode = 'list'">
            <el-icon><List /></el-icon>
          </button>
        </div>
        <button type="button" class="dv-icon-btn" title="刷新" @click="invalidate">
          <el-icon><Refresh /></el-icon>
        </button>
      </div>
    </div>

    <div class="dv-content">
      <div v-if="listQuery.isLoading.value" class="dv-loading">正在读取文件…</div>

      <template v-else-if="folders.length || files.length">
        <section v-if="folders.length" class="dv-section">
          <header class="dv-section__head"><el-icon><Folder /></el-icon>文件夹<span>{{ folders.length }}</span></header>
          <div class="dv-folders">
            <article
              v-for="folder in folders"
              :key="folder.id"
              class="dv-folder"
              @click="enterFolder(folder)"
            >
              <span class="dv-folder__icon"><el-icon><FolderOpened /></el-icon></span>
              <div class="dv-folder__body">
                <h3>{{ folder.name }}</h3>
                <p>{{ folder.childCount }} 个项目 · {{ formatDate(folder.createdAt) }}</p>
              </div>
              <div class="dv-folder__ops" @click.stop>
                <button type="button" title="重命名" @click.stop="openNameDialog('rename', folder)"><el-icon><EditPen /></el-icon></button>
                <button type="button" title="删除" @click.stop="removeItem(folder)"><el-icon><Delete /></el-icon></button>
              </div>
              <el-icon class="dv-folder__go"><ArrowRight /></el-icon>
            </article>
          </div>
        </section>

        <section v-if="files.length" class="dv-section">
          <header class="dv-section__head"><el-icon><Files /></el-icon>文件<span>{{ files.length }}</span></header>
          <div class="dv-files" :class="{ 'is-list': viewMode === 'list' }">
            <article
              v-for="file in files"
              :key="file.id"
              class="dv-file"
              :style="{ '--tone': kindOf(file).tone }"
              @click="openPreview(file)"
            >
              <div class="dv-file__thumb">
                <img v-if="isImage(file)" :src="fileUrl(file)" :alt="file.name" loading="lazy" />
                <span v-else class="dv-file__icon"><el-icon><component :is="kindOf(file).icon" /></el-icon></span>
                <span class="dv-file__kind">{{ kindOf(file).label }}</span>
              </div>
              <div class="dv-file__body">
                <h3 :title="file.name">{{ file.name }}</h3>
                <p>{{ formatSize(file.size) }} · {{ formatDate(file.createdAt) }}</p>
                <span class="dv-file__storage">{{ file.storage === 's3' ? '对象存储' : '本地' }}</span>
              </div>
              <div class="dv-file__ops" @click.stop>
                <button type="button" title="预览" @click.stop="openPreview(file)"><el-icon><View /></el-icon></button>
                <button type="button" title="复制链接" @click.stop="copyLink(file)"><el-icon><CopyDocument /></el-icon></button>
                <button type="button" title="下载" @click.stop="downloadFile(file)"><el-icon><Download /></el-icon></button>
                <button type="button" title="重命名" @click.stop="openNameDialog('rename', file)"><el-icon><EditPen /></el-icon></button>
                <button type="button" title="删除" @click.stop="removeItem(file)"><el-icon><Delete /></el-icon></button>
              </div>
            </article>
          </div>
        </section>
      </template>

      <div v-else class="dv-empty">
        <span class="dv-empty__icon"><el-icon><UploadFilled /></el-icon></span>
        <h2>{{ keyword ? '没有找到匹配的文件' : '这个文件夹还是空的' }}</h2>
        <p>{{ keyword ? '换个关键词试试。' : '把文件拖到这里，或点击按钮上传；也可以先建个文件夹分类整理。' }}</p>
        <div class="dv-empty__actions">
          <button type="button" class="dv-btn dv-btn--primary" @click="fileInput?.click()">
            <el-icon><UploadFilled /></el-icon>上传文件
          </button>
          <button type="button" class="dv-btn dv-btn--ghost" @click="openNameDialog('folder')">
            <el-icon><Plus /></el-icon>新建文件夹
          </button>
        </div>
      </div>
    </div>

    <div v-if="dragActive" class="dv-dropzone">
      <el-icon><UploadFilled /></el-icon>
      <b>松开鼠标即可上传</b>
      <span>支持多文件，单个不超过 100MB</span>
    </div>

    <div v-if="uploads.length" class="dv-uploads">
      <header>
        <span><el-icon><DataLine /></el-icon>{{ uploading ? '正在上传' : '上传完成' }}</span>
        <button type="button" title="关闭" @click="clearUploads"><el-icon><Delete /></el-icon></button>
      </header>
      <ul>
        <li v-for="(task, index) in uploads" :key="`${task.name}-${index}`" :class="`is-${task.status}`">
          <div class="dv-uploads__row">
            <span class="dv-uploads__name">{{ task.name }}</span>
            <span class="dv-uploads__size">
              <el-icon v-if="task.status === 'done'"><CircleCheck /></el-icon>
              <el-icon v-else-if="task.status === 'error'"><Warning /></el-icon>
              <template v-else>{{ task.percent }}%</template>
            </span>
          </div>
          <div class="dv-uploads__bar"><i :style="{ width: `${task.percent}%` }" /></div>
          <p v-if="task.message">{{ task.message }}</p>
        </li>
      </ul>
    </div>

    <el-dialog v-model="nameDialog" :title="nameMode === 'folder' ? '新建文件夹' : '重命名'" width="420px" class="dv-dialog" destroy-on-close>
      <el-form ref="nameFormRef" :model="nameForm" label-position="top">
        <el-form-item label="名称" prop="name" :rules="[{ required: true, message: '请输入名称', trigger: 'blur' }]">
          <el-input v-model="nameForm.name" placeholder="不要包含 / 等路径符号" maxlength="120" @keyup.enter="submitName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button type="button" class="dv-btn dv-btn--ghost" @click="nameDialog = false">取消</button>
        <button type="button" class="dv-btn dv-btn--primary" :disabled="saving" @click="submitName">
          {{ saving ? '保存中…' : '确定' }}
        </button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" :title="previewFile?.name ?? '预览'" width="min(900px, 92vw)" class="dv-dialog dv-preview" destroy-on-close>
      <div v-if="previewFile" class="dv-preview__body">
        <img v-if="isImage(previewFile)" :src="fileUrl(previewFile)" :alt="previewFile.name" />
        <video v-else-if="isVideo(previewFile)" :src="fileUrl(previewFile)" controls />
        <pre v-else-if="isText(previewFile)">{{ previewText }}</pre>
        <div v-else class="dv-preview__placeholder">该文件暂不支持在线预览，请下载后查看。</div>
      </div>
      <template #footer>
        <button type="button" class="dv-btn dv-btn--ghost" @click="previewVisible = false">关闭</button>
        <button v-if="previewFile" type="button" class="dv-btn dv-btn--primary" @click="downloadFile(previewFile)">
          <el-icon><Download /></el-icon>下载文件
        </button>
      </template>
    </el-dialog>

    <el-drawer v-model="configVisible" title="存储设置" size="min(480px, 96vw)" class="dv-drawer">
      <div class="dv-config">
        <div class="dv-config__intro">
          <el-icon><InfoFilled /></el-icon>
          <p>默认使用本地磁盘，文件保存在 data 目录；切换到 S3 后，新文件上传到对象存储，历史文件仍按原位置读取。密钥加密保存，页面只显示掩码。</p>
        </div>

        <div class="dv-config__switch">
          <button type="button" :class="{ 'is-active': configForm.storageType === 'local' }" @click="configForm.storageType = 'local'">
            <el-icon><Monitor /></el-icon>
            <b>本地磁盘</b>
            <span>零配置，跟随备份一起打包</span>
          </button>
          <button type="button" :class="{ 'is-active': configForm.storageType === 's3' }" @click="configForm.storageType = 's3'">
            <el-icon><Connection /></el-icon>
            <b>S3 对象存储</b>
            <span>RustFS / MinIO / AWS S3</span>
          </button>
        </div>

        <template v-if="configForm.storageType === 's3'">
          <p class="dv-config__label">连接信息</p>
          <div class="dv-config__grid">
            <label>Endpoint
              <input v-model="configForm.endpoint" type="url" placeholder="https://s3.example.com" />
            </label>
            <label>Bucket
              <input v-model="configForm.bucket" placeholder="img" />
            </label>
            <label>Region
              <input v-model="configForm.region" placeholder="us-east-1" />
            </label>
            <label>Access Key
              <input v-model="configForm.accessKey" placeholder="访问密钥 ID" autocomplete="off" />
            </label>
            <label class="is-wide">Secret Key
              <input v-model="configForm.secretKey" type="password" placeholder="留空或掩码表示不修改" autocomplete="new-password" />
            </label>
            <label class="is-wide">对象前缀（选填）
              <input v-model="configForm.prefix" placeholder="minidesk/ ，用于隔离其他业务的对象" />
            </label>
            <label class="is-wide">公链前缀（选填）
              <input v-model="configForm.publicUrl" placeholder="https://oss.example.com/proxy/plain/img/ ，填了才会生成直链" />
            </label>
            <label>ACL（选填）
              <input v-model="configForm.acl" placeholder="public-read" />
            </label>
            <label class="dv-config__check">
              <input v-model="configForm.pathStyle" type="checkbox" />
              <span>使用 Path-style 寻址（RustFS / MinIO 通常开启）</span>
            </label>
          </div>
          <p class="dv-config__hint">
            使用 AWS SigV4 签名，仅依赖 JDK，无需安装 SDK。若 Endpoint 是自建服务，请确认服务端时间与本机相差不超过 15 分钟。
          </p>
        </template>

        <div v-if="testResult" class="dv-config__result" :class="{ 'is-ok': testResult.ok }">
          <el-icon><component :is="testResult.ok ? CircleCheck : Warning" /></el-icon>
          <span>{{ testResult.message }}</span>
        </div>
      </div>
      <template #footer>
        <div class="dv-config__footer">
          <button type="button" class="dv-btn dv-btn--ghost" :disabled="testing || saving" @click="testStorage">
            {{ testing ? '测试中…' : '测试连接' }}
          </button>
          <button type="button" class="dv-btn dv-btn--primary" :disabled="saving" @click="saveStorage">
            <el-icon><Check /></el-icon>{{ saving ? '保存中…' : '保存配置' }}
          </button>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped lang="scss">
.dv-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 100%;
  padding-top: 8px;
  position: relative;
}

.dv-hero {
  @include card($radius-xl);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px;
  overflow: hidden;
  background:
    radial-gradient(520px 160px at 6% -20%, color-mix(in srgb, var(--brand-400) 18%, transparent), transparent 70%),
    var(--bg-card);

  &__left { display: flex; align-items: center; gap: 14px; min-width: 0; }

  &__badge {
    @include flex-center;
    flex: 0 0 auto;
    width: 44px;
    height: 44px;
    border-radius: 14px;
    color: #fff;
    font-size: 21px;
    background: linear-gradient(140deg, #3f9d96, #08645f);
    box-shadow: 0 8px 18px color-mix(in srgb, var(--brand-500) 28%, transparent);
  }

  h1 { font-size: 20px; font-weight: 700; color: var(--text-primary); }
  p { margin-top: 3px; font-size: 12px; color: var(--text-secondary); }
  &__actions { position: relative; z-index: 1; display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
}

.dv-storage {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 11px;
  border: 1px solid color-mix(in srgb, var(--brand-400) 32%, var(--border-color));
  border-radius: 9px;
  background: color-mix(in srgb, var(--brand-50) 62%, transparent);
  color: var(--brand-700);
  font-size: 11.5px;
  font-weight: 600;

  &.is-s3 { border-color: color-mix(in srgb, #8B7CF6 40%, transparent); background: color-mix(in srgb, #8B7CF6 12%, transparent); color: #7361dd; }
  &__warn { width: 6px; height: 6px; border-radius: 50%; background: #ff6b6b; }
}

.dv-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: 10px;
  font-size: 12.5px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &:active { transform: scale(0.97); }
  &:disabled { opacity: 0.6; cursor: not-allowed; }

  &--primary {
    color: #fff;
    background: linear-gradient(135deg, var(--brand-400), var(--brand-600));
    box-shadow: 0 6px 16px color-mix(in srgb, var(--brand-500) 24%, transparent);
    &:hover { box-shadow: 0 10px 22px color-mix(in srgb, var(--brand-500) 32%, transparent); }
  }

  &--ghost {
    color: var(--text-secondary);
    border-color: color-mix(in srgb, var(--border-strong) 80%, transparent);
    background: color-mix(in srgb, var(--bg-card) 82%, transparent);
    &:hover { color: var(--brand-600); background: var(--bg-hover); }
  }
}

.dv-overview {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(240px, 1fr);
  gap: 12px;
}

.dv-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.dv-stat {
  @include card($radius-lg);
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 12px 13px;

  &__icon {
    @include flex-center;
    width: 30px;
    height: 30px;
    border-radius: 9px;
    color: var(--tone);
    font-size: 15px;
    background: color-mix(in srgb, var(--tone) 12%, transparent);
  }

  b { font-family: var(--font-number); font-size: 19px; font-weight: 700; color: var(--text-primary); line-height: 1; }
  &__label { font-size: 11px; color: var(--text-tertiary); }
}

.dv-usage {
  @include card($radius-lg);
  padding: 12px 16px;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 11.5px;
    color: var(--text-secondary);

    span { display: inline-flex; align-items: center; gap: 5px; }
    b { font-family: var(--font-number); font-size: 15px; color: var(--text-primary); }
  }

  &__bar {
    position: relative;
    height: 7px;
    margin: 10px 0 7px;
    border-radius: 6px;
    background: var(--bg-subtle);
    overflow: hidden;

    i {
      position: absolute;
      inset: 0 auto 0 0;
      min-width: 3px;
      border-radius: 6px;
      background: linear-gradient(90deg, var(--brand-400), var(--brand-600));
    }
  }

  p { font-size: 10.5px; color: var(--text-tertiary); }
}

.dv-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  &__right { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
}

.dv-crumbs {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  flex-wrap: wrap;

  button {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    height: 30px;
    padding: 0 9px;
    border: 0;
    border-radius: 9px;
    background: transparent;
    color: var(--text-secondary);
    font-size: 12px;
    cursor: pointer;

    &:hover { background: var(--bg-hover); }
    &.is-current { color: var(--brand-700); font-weight: 700; background: color-mix(in srgb, var(--brand-100) 65%, transparent); }
  }

  &__sep { color: var(--text-tertiary); font-size: 11px; }
}

.dv-search {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 210px;
  height: 34px;
  padding: 0 11px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-card);
  color: var(--text-tertiary);
  box-shadow: var(--shadow-card);

  &:focus-within { border-color: color-mix(in srgb, var(--brand-400) 60%, transparent); box-shadow: 0 0 0 3px color-mix(in srgb, var(--brand-400) 12%, transparent); }

  input {
    flex: 1;
    min-width: 0;
    border: 0;
    outline: 0;
    background: transparent;
    color: var(--text-primary);
    font-size: 12px;
    &::placeholder { color: var(--text-tertiary); }
  }

  &__count { font-size: 10.5px; color: var(--brand-600); }
}

.dv-sort, .dv-views {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-card);
  box-shadow: var(--shadow-card);

  button {
    height: 26px;
    padding: 0 10px;
    border: 0;
    border-radius: 7px;
    background: transparent;
    color: var(--text-tertiary);
    font-size: 11.5px;
    cursor: pointer;

    &:hover { color: var(--text-primary); }
    &.is-active { color: #fff; font-weight: 600; background: linear-gradient(135deg, var(--brand-400), var(--brand-600)); }
  }
}

.dv-views button { @include flex-center; width: 28px; padding: 0; font-size: 13px; }

.dv-icon-btn {
  @include flex-center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;

  &:hover { color: var(--brand-600); background: var(--bg-hover); }
}

.dv-content { display: flex; flex-direction: column; gap: 20px; }
.dv-loading { padding: 60px; text-align: center; color: var(--text-tertiary); font-size: 13px; }

.dv-section {
  &__head {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 10px;
    font-size: 13px;
    font-weight: 700;
    color: var(--text-primary);

    span {
      margin-left: 4px;
      padding: 1px 7px;
      border-radius: 8px;
      background: var(--bg-subtle);
      color: var(--text-tertiary);
      font-size: 10.5px;
      font-weight: 500;
    }
  }
}

.dv-folders {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 11px;
}

.dv-folder {
  @include card($radius-lg);
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  cursor: pointer;
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &:hover { transform: translateY(-2px); box-shadow: var(--shadow-card-hover); }

  &__icon {
    @include flex-center;
    flex: 0 0 auto;
    width: 38px;
    height: 38px;
    border-radius: 11px;
    color: #ffb020;
    font-size: 19px;
    background: color-mix(in srgb, #ffb020 14%, transparent);
  }

  &__body {
    flex: 1;
    min-width: 0;
    h3 { font-size: 12.5px; font-weight: 700; color: var(--text-primary); @include ellipsis(1); }
    p { margin-top: 3px; font-size: 10.5px; color: var(--text-tertiary); }
  }

  &__ops {
    display: none;
    gap: 1px;
    padding: 2px;
    border-radius: 8px;
    background: var(--bg-card);
    box-shadow: var(--shadow-card);

    button {
      @include flex-center;
      width: 22px;
      height: 22px;
      border: 0;
      border-radius: 6px;
      background: transparent;
      color: var(--text-tertiary);
      font-size: 12px;
      cursor: pointer;
      &:hover { color: var(--brand-600); background: var(--bg-hover); }
    }
  }

  &:hover &__ops { display: flex; }
  &:hover &__go { display: none; }

  &__go { position: absolute; right: 12px; color: #ffb020; opacity: 0.6; font-size: 13px; }
}

.dv-files {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(178px, 1fr));
  gap: 12px;

  &.is-list { grid-template-columns: 1fr; }
}

.dv-file {
  @include card($radius-lg);
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  cursor: pointer;
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out, border-color $duration-fast $ease-out;

  &:hover { transform: translateY(-3px); border-color: color-mix(in srgb, var(--tone) 38%, var(--border-color)); box-shadow: var(--shadow-card-hover); }

  &__thumb {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    height: 112px;
    background: color-mix(in srgb, var(--tone) 8%, var(--bg-subtle));
    overflow: hidden;

    img { width: 100%; height: 100%; object-fit: cover; }
  }

  &__icon {
    @include flex-center;
    width: 52px;
    height: 52px;
    border-radius: 16px;
    color: #fff;
    font-size: 25px;
    background: linear-gradient(140deg, color-mix(in srgb, var(--tone) 82%, #fff), var(--tone));
    box-shadow: 0 10px 22px color-mix(in srgb, var(--tone) 26%, transparent);
  }

  &__kind {
    position: absolute;
    right: 8px;
    bottom: 8px;
    padding: 2px 7px;
    border-radius: 7px;
    background: color-mix(in srgb, var(--bg-card) 88%, transparent);
    color: var(--tone);
    font-size: 10px;
    font-weight: 600;
  }

  &__body { padding: 11px 12px 12px; min-width: 0; }

  h3 { font-size: 12.5px; font-weight: 700; color: var(--text-primary); @include ellipsis(1); }
  p { margin-top: 4px; font-size: 10.5px; color: var(--text-tertiary); @include ellipsis(1); }

  &__storage {
    display: inline-block;
    margin-top: 7px;
    padding: 1px 7px;
    border-radius: 6px;
    background: color-mix(in srgb, var(--tone) 11%, transparent);
    color: var(--tone);
    font-size: 10px;
  }

  &__ops {
    position: absolute;
    left: 8px;
    right: 8px;
    top: 8px;
    display: flex;
    justify-content: flex-end;
    gap: 2px;
    padding: 3px;
    border-radius: 9px;
    background: color-mix(in srgb, var(--bg-card) 90%, transparent);
    box-shadow: var(--shadow-card);
    opacity: 0;
    transition: opacity $duration-fast $ease-out;

    button {
      @include flex-center;
      width: 23px;
      height: 23px;
      border: 0;
      border-radius: 7px;
      background: transparent;
      color: var(--text-tertiary);
      font-size: 12px;
      cursor: pointer;
      &:hover { color: var(--brand-600); background: var(--bg-hover); }
    }
  }

  &:hover &__ops { opacity: 1; }
}

.dv-files.is-list .dv-file {
  flex-direction: row;
  align-items: center;
  gap: 12px;
  padding: 8px 12px 8px 8px;

  .dv-file__thumb { flex: 0 0 auto; width: 48px; height: 48px; border-radius: 10px; }
  .dv-file__icon { width: 28px; height: 28px; border-radius: 9px; font-size: 14px; }
  .dv-file__kind { display: none; }
  .dv-file__body { flex: 1; padding: 0; }
  .dv-file__storage { display: none; }
  .dv-file__ops { position: static; opacity: 1; box-shadow: none; background: transparent; padding: 0; }
}

.dv-empty {
  @include card($radius-xl);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 52px 24px;
  border-style: dashed;
  text-align: center;

  &__icon {
    @include flex-center;
    width: 62px;
    height: 62px;
    border-radius: 20px;
    color: var(--brand-600);
    font-size: 27px;
    background: color-mix(in srgb, var(--brand-100) 65%, transparent);
  }

  h2 { font-size: 16px; font-weight: 700; color: var(--text-primary); }
  p { font-size: 12.5px; color: var(--text-secondary); }
  &__actions { display: flex; gap: 10px; margin-top: 8px; flex-wrap: wrap; justify-content: center; }
}

.dv-dropzone {
  @include flex-center;
  position: fixed;
  inset: 12px;
  z-index: 60;
  flex-direction: column;
  gap: 10px;
  border: 2px dashed var(--brand-500);
  border-radius: 20px;
  background: color-mix(in srgb, var(--bg-card) 92%, transparent);
  color: var(--brand-700);
  pointer-events: none;

  .el-icon { font-size: 42px; }
  b { font-size: 18px; }
  span { font-size: 12px; color: var(--text-secondary); }
}

.dv-uploads {
  @include card($radius-lg);
  position: fixed;
  right: 18px;
  bottom: 18px;
  z-index: 55;
  width: 300px;
  padding: 12px;
  box-shadow: var(--shadow-float);

  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
    font-size: 12px;
    font-weight: 700;
    color: var(--text-primary);

    span { display: inline-flex; align-items: center; gap: 5px; }
    button { @include flex-center; width: 22px; height: 22px; border: 0; background: transparent; color: var(--text-tertiary); cursor: pointer; }
  }

  ul { margin: 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: 9px; max-height: 220px; overflow-y: auto; @include scrollbar(4px); }

  &__row { display: flex; align-items: center; justify-content: space-between; gap: 8px; font-size: 11px; color: var(--text-secondary); }
  &__name { @include ellipsis(1); }
  &__size { flex: 0 0 auto; color: var(--brand-600); }
  &__bar { height: 4px; margin-top: 4px; border-radius: 4px; background: var(--bg-subtle); overflow: hidden; }
  &__bar i { display: block; height: 100%; border-radius: 4px; background: linear-gradient(90deg, var(--brand-400), var(--brand-600)); transition: width 0.2s ease; }
  li.is-done &__size { color: #35c79b; }
  li.is-error &__size { color: #ff6b6b; }
  li p { margin-top: 3px; font-size: 10px; color: #ff6b6b; }
}

.dv-dialog {
  :deep(.el-dialog) { border-radius: 18px; }
  :deep(.el-dialog__title) { font-weight: 700; }
  :deep(.el-form-item__label) { font-size: 12px; font-weight: 600; color: var(--text-secondary); }
  :deep(.el-dialog__footer) { display: flex; justify-content: flex-end; gap: 10px; }
}

.dv-preview {
  :deep(.el-dialog__body) { padding-top: 8px; }
  &__body {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 220px;
    max-height: 68vh;
    overflow: auto;
    border-radius: 12px;
    background: var(--bg-subtle);

    img, video { max-width: 100%; max-height: 66vh; object-fit: contain; }
    pre { width: 100%; margin: 0; padding: 18px; font-size: 12px; line-height: 1.7; white-space: pre-wrap; color: var(--text-primary); }
  }
  &__placeholder { padding: 40px; color: var(--text-secondary); font-size: 13px; }
}

.dv-drawer {
  :deep(.el-drawer__header) { margin-bottom: 12px; font-weight: 700; }
  :deep(.el-drawer__footer) { padding: 14px 20px; border-top: 1px solid var(--border-color); }
}

.dv-config {
  display: flex;
  flex-direction: column;
  gap: 16px;
  font-size: 12px;

  &__intro {
    display: flex;
    gap: 9px;
    padding: 12px;
    border: 1px dashed color-mix(in srgb, var(--brand-400) 35%, var(--border-color));
    border-radius: 12px;
    background: color-mix(in srgb, var(--brand-50) 50%, transparent);
    color: var(--text-secondary);

    .el-icon { flex: 0 0 auto; margin-top: 2px; color: var(--brand-600); }
    p { line-height: 1.7; font-size: 11.5px; }
  }

  &__switch {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;

    button {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 4px;
      padding: 13px;
      border: 1px solid var(--border-color);
      border-radius: 12px;
      background: var(--bg-card);
      color: var(--text-secondary);
      text-align: left;
      cursor: pointer;

      .el-icon { font-size: 18px; color: var(--text-tertiary); }
      b { font-size: 12.5px; color: var(--text-primary); }
      span { font-size: 10.5px; color: var(--text-tertiary); }

      &.is-active {
        border-color: color-mix(in srgb, var(--brand-500) 55%, transparent);
        background: color-mix(in srgb, var(--brand-50) 62%, transparent);
        box-shadow: 0 0 0 2px color-mix(in srgb, var(--brand-400) 12%, transparent);
        .el-icon { color: var(--brand-600); }
      }
    }
  }

  &__label { font-size: 11px; font-weight: 700; letter-spacing: 0.6px; color: var(--text-tertiary); }

  &__grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;

    label {
      display: flex;
      flex-direction: column;
      gap: 5px;
      font-size: 11px;
      font-weight: 600;
      color: var(--text-secondary);

      &.is-wide { grid-column: 1 / -1; }

      input {
        height: 34px;
        padding: 0 10px;
        border: 1px solid var(--border-color);
        border-radius: 9px;
        background: var(--bg-card);
        color: var(--text-primary);
        font-size: 12px;
        font-weight: 400;
        outline: 0;

        &:focus { border-color: color-mix(in srgb, var(--brand-400) 60%, transparent); box-shadow: 0 0 0 3px color-mix(in srgb, var(--brand-400) 12%, transparent); }
        &::placeholder { color: var(--text-tertiary); }
      }
    }
  }

  &__check {
    grid-column: 1 / -1;
    flex-direction: row !important;
    align-items: center;
    gap: 8px !important;
    font-weight: 400 !important;

    input { width: 15px; height: 15px; accent-color: var(--brand-500); }
  }

  &__hint { font-size: 10.5px; line-height: 1.7; color: var(--text-tertiary); }

  &__result {
    display: flex;
    align-items: center;
    gap: 7px;
    padding: 10px 12px;
    border-radius: 10px;
    background: color-mix(in srgb, #ff6b6b 10%, transparent);
    color: #e05555;
    font-size: 11.5px;

    &.is-ok { background: color-mix(in srgb, #35c79b 12%, transparent); color: #1f9d78; }
  }

  &__footer { display: flex; justify-content: flex-end; gap: 10px; }
}

@include respond-below(md) {
  .dv-overview { grid-template-columns: 1fr; }
  .dv-toolbar__right { width: 100%; }
  .dv-search { flex: 1; width: auto; }
}

@include respond-below(sm) {
  .dv-hero { flex-direction: column; align-items: flex-start; padding: 16px; }
  .dv-hero__actions { width: 100%; }
  .dv-hero__actions .dv-btn { flex: 1; }
  .dv-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
  .dv-stat { padding: 10px 11px; }
  .dv-toolbar { gap: 8px; }
  .dv-crumbs { width: 100%; }
  .dv-search { flex-basis: 100%; }
  .dv-toolbar__right { justify-content: space-between; }
  .dv-sort button { padding: 0 7px; font-size: 11px; }
  .dv-folders { grid-template-columns: 1fr; }
  .dv-files { grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 9px; }
  .dv-file__ops { opacity: 1; }
  .dv-uploads { left: 12px; right: 12px; bottom: 70px; width: auto; }
  .dv-config__grid { grid-template-columns: 1fr; }
  .dv-config__switch { grid-template-columns: 1fr; }
}
</style>
