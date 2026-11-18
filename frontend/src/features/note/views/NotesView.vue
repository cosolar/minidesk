<script setup lang="ts">
import { computed, reactive, ref, watch, type Component } from 'vue'
import { useRoute } from 'vue-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  Briefcase,
  Clock,
  Coffee,
  CopyDocument,
  Delete,
  Document,
  EditPen,
  Folder,
  FolderAdd,
  FolderOpened,
  MoreFilled,
  Notebook,
  Plus,
  Reading,
  RefreshLeft,
  RefreshRight,
  Search,
  Share,
  Star,
  StarFilled,
  Tickets,
  View,
} from '@element-plus/icons-vue'
import { useDebounceFn, useWindowSize } from '@vueuse/core'
import dayjs from 'dayjs'
import MarkdownEditor from '@/shared/components/MarkdownEditor.vue'
import MarkdownPreview, { type MarkdownHeading } from '@/shared/components/MarkdownPreview.vue'
import { useAppStore } from '@/core/stores/app'
import NoteTree from '../components/NoteTree.vue'
import {
  noteApi,
  noteFolderApi,
  type NoteFolder,
  type NoteModule,
  type NoteOutline,
  type NoteRecycleBoard,
  type NoteRecycleItem,
  type NoteShare,
  type NoteSharePayload,
  type NoteSummary,
} from '../api'

const route = useRoute()
const queryClient = useQueryClient()
const appStore = useAppStore()

type ViewMode = 'preview' | 'edit'

const keyword = ref('')
const selectedId = ref<string | null>(null)
const saveState = ref<'saved' | 'saving' | 'dirty'>('saved')
/** 默认只读预览，点「编辑」才进编辑器 */
const mode = ref<ViewMode>('preview')
const outline = ref<MarkdownHeading[]>([])
const outlineCollapsed = ref(false)
const activeHeading = ref('')
const previewRef = ref<{ scrollTo: (id: string) => void } | null>(null)
const editorRef = ref<{ focus: () => void; undo: () => void; redo: () => void } | null>(null)
/** 预览正文的滚动容器，供目录「回到顶部」使用 */
const docScrollRef = ref<HTMLElement | null>(null)

/**
 * 桌面宽度走左右分屏（源码 + 实时预览）。
 * ByteMD 只在 split 模式下渲染格式化工具条（H / B / I / 列表…），
 * tab 单栏只剩「编辑 / 预览」两个 tab，所以阈值不能设太高。
 * 1200 是扣掉侧栏 240 与列表 286 后仍能给每栏留下 300px 的临界值。
 */
const { width: viewportWidth } = useWindowSize()
const editorMode = computed<'ir' | 'sv'>(() =>
  !appStore.isMobile && viewportWidth.value >= 1200 ? 'sv' : 'ir',
)
/** 抽屉固定 420px 在窄屏会从左侧溢出，小屏改成整屏宽 */
const recycleDrawerSize = computed(() => (viewportWidth.value < 480 ? '100%' : '420px'))

const MODULE_LABEL: Record<string, string> = {
  work: '工作',
  study: '学习',
  life: '生活',
}

/** 分类语义色与图标：目录树、搜索结果卡片共用 */
const MODULE_META: Record<string, { label: string; icon: Component; tone: string }> = {
  work: { label: '工作', icon: Briefcase, tone: 'work' },
  study: { label: '学习', icon: Reading, tone: 'study' },
  life: { label: '生活', icon: Coffee, tone: 'life' },
}

const DEFAULT_META = { label: '笔记', icon: Document, tone: 'all' }

/** 侧栏当前选中的目录；选中文档时高亮以文档为准 */
const selectedFolder = ref<{ module: NoteModule; folderId: string | null }>({
  module: routeModule(),
  folderId: null,
})

function routeModule(): NoteModule {
  const value = String(route.query.module ?? '')
  return value === 'study' || value === 'life' ? value : 'work'
}

const folderQuery = useQuery({
  queryKey: ['note-folders'],
  queryFn: () => noteFolderApi.list(),
  staleTime: 30_000,
})

const outlineQuery = useQuery({
  queryKey: ['note-outline'],
  queryFn: () => noteApi.outline(),
  staleTime: 10_000,
})

const folders = computed(() => folderQuery.data.value ?? [])
const outlineNotes = computed(() => outlineQuery.data.value ?? [])

/** 搜索走服务端全文，只在有关键词时请求 */
const listQuery = useQuery({
  queryKey: ['notes', keyword],
  queryFn: () => noteApi.page({ keyword: keyword.value || undefined, size: 50 }),
  enabled: computed(() => Boolean(keyword.value.trim())),
  staleTime: 10_000,
})

const searchNotes = computed(() => listQuery.data.value?.list ?? [])
const sideCount = computed(() => searchNotes.value.length)
const totalWords = computed(() =>
  outlineNotes.value.reduce((sum, note) => sum + (note.wordCount ?? 0), 0),
)

const treeSelectedKey = computed(() => {
  if (selectedId.value) {
    return `note:${selectedId.value}`
  }
  const current = selectedFolder.value
  return current.folderId ? `folder:${current.folderId}` : `root:${current.module}`
})

const selectedFolderName = computed(() => {
  const current = selectedFolder.value
  if (current.folderId) {
    return folders.value.find((folder) => folder.id === current.folderId)?.name ?? '目录'
  }
  return MODULE_LABEL[current.module] ?? '笔记'
})

/** 目录视图只列当前目录的直接文档，子目录内容在左侧树里展开 */
const folderNotes = computed(() => {
  const current = selectedFolder.value
  return outlineNotes.value.filter((note) =>
    current.folderId
      ? note.folderId === current.folderId
      : !note.folderId && note.module === current.module,
  )
})

/** 当前目录下的直接子目录；根节点展示该板块的一级目录 */
const childFolders = computed(() => {
  const current = selectedFolder.value
  return folders.value.filter((folder) =>
    current.folderId ? folder.parentId === current.folderId : !folder.parentId && folder.module === current.module,
  )
})

/** 目录卡片上的文档数按整棵子树算，和左侧树的计数保持一致 */
function descendantNotes(folderId: string): number {
  const ids = new Set<string>([folderId])
  let changed = true
  while (changed) {
    changed = false
    for (const folder of folders.value) {
      if (folder.parentId && ids.has(folder.parentId) && !ids.has(folder.id)) {
        ids.add(folder.id)
        changed = true
      }
    }
  }
  return outlineNotes.value.filter((note) => note.folderId && ids.has(note.folderId)).length
}

function noteMeta(note: Pick<NoteSummary, 'module'>) {
  return MODULE_META[note.module] ?? DEFAULT_META
}

function formatWords(count: number): string {
  return `${(count ?? 0).toLocaleString('zh-CN')} 字`
}

const draft = reactive({
  title: '',
  content: '',
  pinned: false,
})

const detailQuery = useQuery({
  queryKey: ['note', selectedId],
  queryFn: () => noteApi.detail(selectedId.value as string),
  enabled: computed(() => Boolean(selectedId.value)),
  staleTime: 0,
})

let syncing = false

watch(
  () => detailQuery.data.value,
  (detail) => {
    if (!detail || detail.id !== selectedId.value) {
      return
    }
    syncing = true
    draft.title = detail.title
    draft.content = detail.content
    draft.pinned = detail.pinned
    saveState.value = 'saved'
    // 让 watch 的同步赋值不触发自动保存
    queueMicrotask(() => {
      syncing = false
    })
  },
)

watch(selectedId, () => {
  outline.value = []
  activeHeading.value = ''
})

const activeModule = computed(
  () => detailQuery.data.value?.module ?? selectedFolder.value.module ?? 'work',
)
const updatedAtText = computed(() => detailQuery.data.value?.updatedAt ?? '')
const wordCount = computed(() => draft.content.replace(/\s/g, '').length)

/** 文档树与搜索结果都来自这两个 key，写操作后一起失效 */
function invalidateNotes(): Promise<void> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: ['notes'] }),
    queryClient.invalidateQueries({ queryKey: ['note-outline'] }),
  ]).then(() => undefined)
}

const createMutation = useMutation({
  mutationFn: (target: { module: NoteModule; folderId: string | null }) =>
    noteApi.create({
      title: '',
      content: '',
      module: target.module,
      type: 'note',
      folderId: target.folderId,
    }),
  onSuccess: async (note) => {
    await invalidateNotes()
    selectedFolder.value = { module: note.module, folderId: note.folderId ?? null }
    selectedId.value = note.id
    // 新建即写作，直接进编辑模式
    mode.value = 'edit'
  },
})

const saveMutation = useMutation({
  mutationFn: (input: { id: string; title: string; content: string; pinned: boolean }) =>
    noteApi.update(input.id, {
      title: input.title,
      content: input.content,
      pinned: input.pinned,
    }),
  onSuccess: () => {
    saveState.value = 'saved'
    void invalidateNotes()
  },
  onError: () => {
    saveState.value = 'dirty'
    ElMessage.error('保存失败')
  },
})

/** 立即落库当前草稿；切换笔记前先冲一次，避免刚打的字丢失 */
function persist(): void {
  if (!selectedId.value) {
    return
  }
  saveState.value = 'saving'
  saveMutation.mutate({
    id: selectedId.value,
    title: draft.title,
    content: draft.content,
    pinned: draft.pinned,
  })
}

const autosave = useDebounceFn(() => {
  if (!selectedId.value || syncing) {
    return
  }
  persist()
}, 1200)

watch(
  () => [draft.title, draft.content],
  () => {
    if (!selectedId.value || syncing) {
      return
    }
    saveState.value = 'dirty'
    autosave()
  },
)

function onCreate(): void {
  createMutation.mutate(selectedFolder.value)
}

function selectNote(note: NoteSummary | NoteOutline): void {
  if (note.id === selectedId.value) {
    return
  }
  if (saveState.value === 'dirty') {
    persist()
  }
  selectedId.value = note.id
  selectedFolder.value = { module: note.module, folderId: note.folderId ?? null }
}

function selectFolder(payload: { module: NoteModule; folderId: string | null }): void {
  if (saveState.value === 'dirty') {
    persist()
  }
  selectedId.value = null
  selectedFolder.value = payload
}

/** 新建文档：目录树里的「新建文档」会在指定目录下创建 */
function createNoteIn(payload: { module: NoteModule; folderId: string | null }): void {
  createMutation.mutate(payload)
}

async function promptFolderName(title: string, initial = ''): Promise<string | null> {
  const result = await ElMessageBox.prompt('目录名', title, {
    inputValue: initial,
    inputPlaceholder: '例如：需求文档',
    inputValidator: (value: string) => (value.trim() ? true : '目录名不能为空'),
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  }).catch(() => null)
  return result ? result.value.trim() : null
}

const folderMutation = useMutation({
  mutationFn: (input: { action: 'create' | 'rename'; name: string; module?: NoteModule; parentId?: string | null; id?: string }) => {
    if (input.action === 'rename' && input.id) {
      return noteFolderApi.rename(input.id, input.name)
    }
    return noteFolderApi.create({ name: input.name, module: input.module, parentId: input.parentId })
  },
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['note-folders'] })
    ElMessage.success('目录已更新')
  },
})

async function createFolderIn(payload: { module: NoteModule; parentId: string | null }): Promise<void> {
  const name = await promptFolderName('新建目录')
  if (!name) {
    return
  }
  folderMutation.mutate({ action: 'create', name, module: payload.module, parentId: payload.parentId })
}

async function renameFolder(folder: NoteFolder): Promise<void> {
  const name = await promptFolderName('重命名目录', folder.name)
  if (!name || name === folder.name) {
    return
  }
  folderMutation.mutate({ action: 'rename', name, id: folder.id })
}

async function deleteFolder(folder: NoteFolder): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    `删除「${folder.name}」？里面的文档会一起移入回收站，可在回收站恢复。`,
    '删除目录',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  ).catch(() => false)
  if (!confirmed) {
    return
  }
  const result = await noteFolderApi.remove(folder.id)
  if (selectedFolder.value.folderId === folder.id) {
    selectedFolder.value = { module: folder.module, folderId: null }
    selectedId.value = null
  }
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['note-folders'] }),
    invalidateNotes(),
  ])
  ElMessage.success(
    result.moved > 0 ? `已删除目录，${result.moved} 篇文档移入回收站` : '已删除目录',
  )
}

const moveNoteMutation = useMutation({
  mutationFn: (input: { noteId: string; folderId: string | null; module: NoteModule }) =>
    noteApi.move(input.noteId, { folderId: input.folderId, module: input.module }),
  onSuccess: async () => {
    await invalidateNotes()
    ElMessage.success('已移动')
  },
})

const moveFolderMutation = useMutation({
  mutationFn: (input: { folderId: string; parentId: string | null }) =>
    noteFolderApi.move(input.folderId, input.parentId),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['note-folders'] })
    ElMessage.success('已移动')
  },
})

/** 「移动到…」弹窗：目标目录下拉由全部目录平铺成带路径的选项 */
const moveDialogVisible = ref(false)
const moveTarget = ref<string>('root')
const moveRequestNote = ref<NoteOutline | null>(null)

const moveOptions = computed(() => {
  const options: { value: string; label: string }[] = [
    { value: 'root:work', label: '工作（根目录）' },
    { value: 'root:study', label: '学习（根目录）' },
    { value: 'root:life', label: '生活（根目录）' },
  ]
  const byParent = new Map<string | null, NoteFolder[]>()
  folders.value.forEach((folder) => {
    const bucket = byParent.get(folder.parentId) ?? []
    bucket.push(folder)
    byParent.set(folder.parentId, bucket)
  })
  const walk = (parentId: string | null, prefix: string, module: NoteModule): void => {
    for (const folder of byParent.get(parentId) ?? []) {
      if (folder.module !== module) {
        continue
      }
      const path = `${prefix} / ${folder.name}`
      options.push({ value: `folder:${folder.id}`, label: path })
      walk(folder.id, path, module)
    }
  }
  walk(null, '工作', 'work')
  walk(null, '学习', 'study')
  walk(null, '生活', 'life')
  return options
})

function openMoveDialog(note: NoteOutline): void {
  moveRequestNote.value = note
  moveTarget.value = note.folderId ? `folder:${note.folderId}` : `root:${note.module}`
  moveDialogVisible.value = true
}

function confirmMove(): void {
  const note = moveRequestNote.value
  if (!note) {
    return
  }
  const [kind, value] = moveTarget.value.split(':')
  const payload =
    kind === 'folder'
      ? { noteId: note.id, folderId: value, module: note.module }
      : { noteId: note.id, folderId: null, module: value as NoteModule }
  moveDialogVisible.value = false
  moveRequestNote.value = null
  moveNoteMutation.mutate(payload)
}

function switchMode(next: ViewMode): void {
  mode.value = next
}

/** 返回目录：先把未落库的改动冲一次，再回到当前所属目录 */
function closeNote(): void {
  if (!selectedId.value) {
    return
  }
  if (saveState.value === 'dirty') {
    persist()
  }
  selectedId.value = null
  mode.value = 'preview'
}

/** 笔记在树里的位置：板块 → 各级目录，供顶栏面包屑使用 */
const noteFolderPath = computed<NoteFolder[]>(() => {
  const folderId = detailQuery.data.value?.folderId ?? null
  if (!folderId) {
    return []
  }
  const index = new Map(folders.value.map((folder) => [folder.id, folder]))
  const path: NoteFolder[] = []
  let cursor = index.get(folderId)
  while (cursor && path.length < 12) {
    path.unshift(cursor)
    cursor = cursor.parentId ? index.get(cursor.parentId) : undefined
  }
  return path
})

/** 目录条目按层级缩进：标题越深字越浅 */
function headingTone(item: MarkdownHeading): string {
  return `is-level-${Math.min(item.level, 3)}`
}

function gotoModuleRoot(): void {
  closeNote()
  selectedFolder.value = { module: activeModule.value, folderId: null }
}

function gotoFolder(folder: NoteFolder): void {
  closeNote()
  selectedFolder.value = { module: folder.module, folderId: folder.id }
}

function moveCurrentNote(): void {
  if (!selectedId.value) {
    return
  }
  openMoveDialog({
    id: selectedId.value,
    title: draft.title,
    module: activeModule.value,
    folderId: detailQuery.data.value?.folderId ?? null,
    wordCount: 0,
    pinned: draft.pinned,
    updatedAt: updatedAtText.value,
  })
}

/** 「更多」菜单统一出口，避免把三个动作散在模板里 */
function onNoteCommand(command: string | number | object): void {
  if (command === 'pin') {
    void togglePin()
  } else if (command === 'move') {
    moveCurrentNote()
  } else if (command === 'remove') {
    removeCurrent()
  }
}

function onEditorReady(): void {
  // 空笔记直接落光标；已有内容不抢焦点，避免一进来就被滚到文末
  if (!draft.content.trim()) {
    editorRef.value?.focus()
  }
}

function goHeading(id: string): void {
  activeHeading.value = id
  previewRef.value?.scrollTo(id)
}

function scrollDocTop(): void {
  docScrollRef.value?.scrollTo({ top: 0, behavior: 'smooth' })
}

async function confirmRemove(id: string, title: string): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`将「${title || '无标题'}」移入回收站？`, '移入回收站', {
    type: 'warning',
    confirmButtonText: '移入回收站',
    cancelButtonText: '取消',
  }).catch(() => false)
  if (!confirmed) {
    return
  }
  await noteApi.remove(id)
  if (selectedId.value === id) {
    selectedId.value = null
    draft.title = ''
    draft.content = ''
    mode.value = 'preview'
  }
  await invalidateNotes()
  ElMessage.success('已移入回收站，可在回收站恢复')
}

function removeNote(note: { id: string; title: string }): void {
  void confirmRemove(note.id, note.title)
}

function removeCurrent(): void {
  if (selectedId.value) {
    void confirmRemove(selectedId.value, draft.title)
  }
}

async function togglePin(): Promise<void> {
  if (!selectedId.value) {
    return
  }
  draft.pinned = !draft.pinned
  await noteApi.togglePin(selectedId.value, draft.pinned)
  void invalidateNotes()
}

function relative(value: string): string {
  if (!value) {
    return '—'
  }
  const target = dayjs(value)
  if (!target.isValid()) {
    return '—'
  }
  const minutes = dayjs().diff(target, 'minute')
  if (minutes < 1) {
    return '刚刚'
  }
  if (minutes < 60) {
    return `${minutes} 分钟前`
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return `${hours} 小时前`
  }
  const days = Math.floor(hours / 24)
  if (days < 7) {
    return `${days} 天前`
  }
  return target.format('YYYY-MM-DD')
}

function moduleLabel(value: string): string {
  return MODULE_LABEL[value] ?? '笔记'
}

const saveLabel = computed(() => {
  if (saveState.value === 'saving') {
    return '保存中…'
  }
  return saveState.value === 'dirty' ? '未保存' : '已保存'
})

/** 分享：一篇笔记一条可编辑短链，公开页 /share/note/{token} 未登录可读 */
const shareVisible = ref(false)
const shareLoading = ref(false)
const shareSaving = ref(false)
const shareInfo = ref<NoteShare | null>(null)
const shareForm = reactive({
  token: '',
  password: '',
  clearPassword: false,
  expireMode: 'never' as 'never' | '1d' | '7d' | '30d' | 'custom',
  expireAt: '',
})

const shareLink = computed(() =>
  shareForm.token.trim() ? `${window.location.origin}/share/note/${shareForm.token.trim()}` : '',
)

const shareExpireLabel = computed(() => {
  if (!shareInfo.value || !shareInfo.value.expireAt) {
    return '永久有效'
  }
  return `有效期至 ${dayjs(shareInfo.value.expireAt).format('YYYY-MM-DD HH:mm')}`
})

/** 默认 token 用易读字符，避免 0/O、1/l 混淆 */
function randomToken(): string {
  const alphabet = 'abcdefghijkmnpqrstuvwxyz23456789'
  let token = ''
  for (let index = 0; index < 8; index += 1) {
    token += alphabet[Math.floor(Math.random() * alphabet.length)]
  }
  return token
}

async function openShare(): Promise<void> {
  if (!selectedId.value) {
    return
  }
  shareVisible.value = true
  shareLoading.value = true
  shareInfo.value = null
  shareForm.password = ''
  shareForm.clearPassword = false
  try {
    const info = await noteApi.getShare(selectedId.value)
    shareInfo.value = info
    shareForm.token = info?.token ?? randomToken()
    if (info?.expireAt) {
      shareForm.expireMode = 'custom'
      shareForm.expireAt = dayjs(info.expireAt).format('YYYY-MM-DDTHH:mm:ss')
    } else {
      shareForm.expireMode = 'never'
      shareForm.expireAt = ''
    }
  } finally {
    shareLoading.value = false
  }
}

function resolveExpireAt(): string | null {
  if (shareForm.expireMode === 'never') {
    return null
  }
  if (shareForm.expireMode === 'custom') {
    return shareForm.expireAt ? dayjs(shareForm.expireAt).format('YYYY-MM-DDTHH:mm:ss') : null
  }
  const days = Number(shareForm.expireMode.replace('d', ''))
  return dayjs().add(days, 'day').format('YYYY-MM-DDTHH:mm:ss')
}

async function saveShare(): Promise<void> {
  if (!selectedId.value) {
    return
  }
  const token = shareForm.token.trim()
  if (!/^[A-Za-z0-9_-]{4,32}$/.test(token)) {
    ElMessage.warning('链接只能包含字母、数字、中划线或下划线，长度 4-32 位')
    return
  }
  if (shareForm.expireMode === 'custom' && !shareForm.expireAt) {
    ElMessage.warning('请选择失效时间')
    return
  }
  const payload: NoteSharePayload = {
    token,
    expireAt: resolveExpireAt(),
    enabled: true,
  }
  if (shareForm.clearPassword) {
    payload.clearPassword = true
  } else if (shareForm.password) {
    payload.password = shareForm.password
  }
  shareSaving.value = true
  try {
    shareInfo.value = await noteApi.saveShare(selectedId.value, payload)
    shareForm.password = ''
    shareForm.clearPassword = false
    ElMessage.success('分享链接已保存')
  } finally {
    shareSaving.value = false
  }
}

async function copyShareLink(): Promise<void> {
  if (!shareLink.value) {
    return
  }
  try {
    await navigator.clipboard.writeText(shareLink.value)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选中链接复制')
  }
}

async function cancelShare(): Promise<void> {
  if (!selectedId.value) {
    return
  }
  const confirmed = await ElMessageBox.confirm('关闭分享后，原链接会立即失效。', '关闭分享', {
    type: 'warning',
    confirmButtonText: '关闭分享',
    cancelButtonText: '取消',
  }).catch(() => false)
  if (!confirmed) {
    return
  }
  await noteApi.cancelShare(selectedId.value)
  shareInfo.value = null
  shareVisible.value = false
  ElMessage.success('已关闭分享')
}

/** 回收站：软删除的笔记保留 note_recycle_days 天，到期由定时任务彻底删除 */
const recycleVisible = ref(false)
const recycleLoading = ref(false)
const recycleBoard = ref<NoteRecycleBoard>({ keepDays: 30, items: [] })
const recycleBusy = ref('')

async function loadRecycle(): Promise<void> {
  recycleLoading.value = true
  try {
    recycleBoard.value = await noteApi.recycle()
  } finally {
    recycleLoading.value = false
  }
}

function openRecycle(): void {
  recycleVisible.value = true
  void loadRecycle()
}

async function restoreRecycled(item: NoteRecycleItem): Promise<void> {
  recycleBusy.value = item.id
  try {
    await noteApi.restore(item.id)
    await Promise.all([
      loadRecycle(),
      invalidateNotes(),
    ])
    ElMessage.success('已恢复到原分类')
  } finally {
    recycleBusy.value = ''
  }
}

async function purgeRecycled(item: NoteRecycleItem): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    `彻底删除后无法恢复，「${item.title || '无标题'}」确定删除？`,
    '彻底删除',
    { type: 'warning', confirmButtonText: '彻底删除', cancelButtonText: '取消' },
  ).catch(() => false)
  if (!confirmed) {
    return
  }
  recycleBusy.value = item.id
  try {
    await noteApi.purge(item.id)
    await loadRecycle()
    ElMessage.success('已彻底删除')
  } finally {
    recycleBusy.value = ''
  }
}

async function clearRecycled(): Promise<void> {
  const total = recycleBoard.value.items.length
  const confirmed = await ElMessageBox.confirm(
    `回收站里 ${total} 篇笔记会被永久删除，无法恢复。`,
    '清空回收站',
    { type: 'warning', confirmButtonText: '全部删除', cancelButtonText: '取消' },
  ).catch(() => false)
  if (!confirmed) {
    return
  }
  recycleBusy.value = 'all'
  try {
    const result = await noteApi.clearRecycle()
    await loadRecycle()
    ElMessage.success(`已彻底删除 ${result.removed} 篇`)
  } finally {
    recycleBusy.value = ''
  }
}
</script>

<template>
  <div class="notes-page">
    <aside class="note-side">
      <div class="note-side__top">
        <header class="note-side__head">
          <div class="note-side__brand">
            <span class="note-side__mark"><el-icon><Notebook /></el-icon></span>
            <span class="note-side__heading">
              <strong>笔记</strong>
              <small>{{ outlineNotes.length }} 篇 · {{ formatWords(totalWords) }}</small>
            </span>
          </div>
          <div class="note-side__actions">
            <el-tooltip content="回收站" placement="left">
              <button type="button" class="note-recycle" aria-label="回收站" @click="openRecycle">
                <el-icon><Delete /></el-icon>
              </button>
            </el-tooltip>
            <el-tooltip content="新建笔记" placement="left">
              <button type="button" class="note-create" aria-label="新建" @click="onCreate">
                <el-icon><Plus /></el-icon>
              </button>
            </el-tooltip>
          </div>
        </header>

        <div class="note-search">
          <el-input
            v-model="keyword"
            :prefix-icon="Search"
            placeholder="搜索标题或正文"
            clearable
            size="small"
          />
          <span v-if="keyword" class="note-search__result">{{ sideCount }} 条</span>
        </div>
      </div>

      <div class="note-side__list">
        <template v-if="keyword">
          <div v-if="listQuery.isLoading.value" class="note-skeletons">
            <div v-for="n in 3" :key="n" class="note-skeleton"><i /><i /><i /></div>
          </div>

          <template v-else-if="searchNotes.length">
            <ul class="note-group__items">
                <li
                  v-for="note in searchNotes"
                  :key="note.id"
                  class="note-card"
                  :class="[
                    `is-${noteMeta(note).tone}`,
                    { 'is-active': note.id === selectedId, 'is-pinned': note.pinned },
                  ]"
                  @click="selectNote(note)"
                >
                  <span class="note-card__rail" />
                  <div class="note-card__head">
                    <span class="note-card__icon">
                      <el-icon><component :is="noteMeta(note).icon" /></el-icon>
                    </span>
                    <span class="note-card__title">{{ note.title || '无标题' }}</span>
                  </div>
                  <p class="note-card__excerpt">
                    {{ note.excerpt || '空白笔记，点开写点什么…' }}
                  </p>
                  <div class="note-card__meta">
                    <span v-if="note.pinned" class="note-card__pin"><el-icon><StarFilled /></el-icon></span>
                    <span class="note-card__time"><el-icon><Clock /></el-icon>{{ relative(note.updatedAt) }}</span>
                    <span class="note-card__tag">{{ noteMeta(note).label }}</span>
                    <span class="note-card__words">{{ formatWords(note.wordCount) }}</span>
                  </div>
                  <button
                    class="note-card__delete"
                    type="button"
                    aria-label="删除"
                    @click.stop="removeNote(note)"
                  >
                    <el-icon><Delete /></el-icon>
                  </button>
                </li>
            </ul>
          </template>

          <div v-else class="note-empty">
            <span class="note-empty__art"><el-icon><Search /></el-icon></span>
            <strong>没有找到匹配的笔记</strong>
            <p>换个关键词试试，或清空搜索条件。</p>
          </div>
        </template>

        <NoteTree
          v-else
          :folders="folders"
          :notes="outlineNotes"
          :selected-key="treeSelectedKey"
          @select-folder="selectFolder"
          @select-note="selectNote"
          @create-folder="createFolderIn"
          @create-note="createNoteIn"
          @rename-folder="renameFolder"
          @delete-folder="deleteFolder"
          @delete-note="removeNote"
          @move-note="moveNoteMutation.mutate"
          @move-folder="moveFolderMutation.mutate"
          @move-request="openMoveDialog"
        />
      </div>

      <footer class="note-side__foot">
        <span class="note-side__foot-icon"><el-icon><EditPen /></el-icon></span>
        <span class="note-side__foot-text">
          <strong>随手记一笔</strong>
          <small>Markdown 书写 · 自动保存</small>
        </span>
        <button type="button" class="note-side__foot-btn" aria-label="新建笔记" @click="onCreate">
          <el-icon><Plus /></el-icon>
        </button>
      </footer>
    </aside>

    <section class="note-main">
      <template v-if="selectedId">
        <header class="note-bar">
          <button type="button" class="note-bar__back" aria-label="返回目录" @click="closeNote">
            <el-icon><ArrowLeft /></el-icon>
          </button>

          <nav class="note-crumb" aria-label="当前位置">
            <button type="button" class="note-crumb__link" @click="closeNote">
              <el-icon><Notebook /></el-icon>
              笔记
            </button>
            <span class="note-crumb__sep">/</span>
            <button type="button" class="note-crumb__link" @click="gotoModuleRoot">
              {{ moduleLabel(activeModule) }}
            </button>
            <template v-for="node in noteFolderPath" :key="node.id">
              <span class="note-crumb__sep">/</span>
              <button type="button" class="note-crumb__link" @click="gotoFolder(node)">
                {{ node.name }}
              </button>
            </template>
            <span class="note-crumb__sep">/</span>
            <span class="note-crumb__current">{{ draft.title || '无标题' }}</span>
          </nav>

          <div class="note-bar__tools">
            <template v-if="mode === 'edit'">
              <el-tooltip content="撤销（Ctrl/⌘ + Z）" placement="bottom">
                <button type="button" class="icon-btn" aria-label="撤销" @click="editorRef?.undo()">
                  <el-icon><RefreshLeft /></el-icon>
                </button>
              </el-tooltip>
              <el-tooltip content="重做（Ctrl/⌘ + Shift + Z）" placement="bottom">
                <button type="button" class="icon-btn" aria-label="重做" @click="editorRef?.redo()">
                  <el-icon><RefreshRight /></el-icon>
                </button>
              </el-tooltip>
            </template>

            <span class="save-chip" :class="`is-${saveState}`">
              <i class="save-chip__dot" />
              {{ saveLabel }}
            </span>

            <div class="mode-switch" role="tablist" aria-label="视图模式">
              <button
                type="button"
                class="mode-switch__item"
                :class="{ 'is-active': mode === 'edit' }"
                role="tab"
                @click="switchMode('edit')"
              >
                <el-icon><EditPen /></el-icon>
                编辑
              </button>
              <button
                type="button"
                class="mode-switch__item"
                :class="{ 'is-active': mode === 'preview' }"
                role="tab"
                @click="switchMode('preview')"
              >
                <el-icon><View /></el-icon>
                预览
              </button>
            </div>

            <span class="note-bar__divider" />

            <button type="button" class="bar-btn" aria-label="分享这篇笔记" @click="openShare">
              <el-icon><Share /></el-icon>
              <span class="bar-btn__text">分享</span>
            </button>

            <el-dropdown trigger="click" placement="bottom-end" @command="onNoteCommand">
              <button type="button" class="bar-btn" aria-label="更多操作">
                <el-icon><MoreFilled /></el-icon>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="pin">
                    <el-icon>
                      <component :is="draft.pinned ? StarFilled : Star" />
                    </el-icon>
                    {{ draft.pinned ? '取消置顶' : '置顶笔记' }}
                  </el-dropdown-item>
                  <el-dropdown-item command="move">
                    <el-icon><FolderOpened /></el-icon>
                    移动到目录
                  </el-dropdown-item>
                  <el-dropdown-item command="remove" divided>
                    <el-icon><Delete /></el-icon>
                    <span class="bar-menu__danger">移入回收站</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </header>

        <div v-if="detailQuery.isLoading.value" class="doc-loading">
          <el-skeleton :rows="8" animated />
        </div>

        <div
          v-else-if="mode === 'preview'"
          class="doc-body"
          :class="{ 'has-outline': outline.length, 'is-outline-collapsed': outlineCollapsed }"
        >
          <div ref="docScrollRef" class="doc-scroll">
            <article class="doc-article">
              <header class="doc-article__head">
                <div class="doc-article__kicker">
                  <span class="doc-article__module" :class="`is-${activeModule}`">
                    {{ moduleLabel(activeModule) }}
                  </span>
                  <template v-for="node in noteFolderPath" :key="node.id">
                    <span class="doc-article__sep">/</span>
                    <span class="doc-article__crumb">{{ node.name }}</span>
                  </template>
                </div>

                <h1 class="doc-article__title">{{ draft.title || '无标题' }}</h1>

                <div class="doc-article__meta">
                  <span class="doc-meta">
                    <el-icon><Clock /></el-icon>
                    更新于 {{ relative(updatedAtText) }}
                  </span>
                  <span class="doc-meta">
                    <el-icon><EditPen /></el-icon>
                    {{ wordCount }} 字
                  </span>
                  <span v-if="draft.pinned" class="doc-meta is-pinned">
                    <el-icon><StarFilled /></el-icon>
                    置顶
                  </span>
                </div>

                <span class="doc-article__rule" aria-hidden="true" />
              </header>

              <MarkdownPreview
                v-if="draft.content.trim()"
                ref="previewRef"
                :source="draft.content"
                @outline="outline = $event"
                @active="activeHeading = $event"
              />
              <div v-else class="doc-empty">
                <p>这篇笔记还是空的</p>
                <el-button type="primary" :icon="EditPen" @click="switchMode('edit')">
                  开始书写
                </el-button>
              </div>
            </article>
          </div>

          <aside
            v-if="outline.length"
            class="doc-outline"
            :class="{ 'is-collapsed': outlineCollapsed }"
          >
            <div class="doc-outline__head">
              <span class="doc-outline__title">
                <el-icon><Tickets /></el-icon>
                <span class="doc-outline__label">目录</span>
              </span>
              <div class="doc-outline__tools">
                <span v-show="!outlineCollapsed" class="doc-outline__count">{{ outline.length }}</span>
                <button
                  type="button"
                  class="doc-outline__toggle"
                  :aria-label="outlineCollapsed ? '展开目录' : '收起目录'"
                  @click="outlineCollapsed = !outlineCollapsed"
                >
                  <el-icon>
                    <component :is="outlineCollapsed ? ArrowDown : ArrowUp" />
                  </el-icon>
                </button>
              </div>
            </div>

            <div v-show="!outlineCollapsed" class="doc-outline__scroll">
              <ul class="doc-outline__list">
                <li v-for="item in outline" :key="item.id">
                  <button
                    type="button"
                    class="doc-outline__item"
                    :class="[headingTone(item), { 'is-active': activeHeading === item.id }]"
                    @click="goHeading(item.id)"
                  >
                    <i class="doc-outline__marker" />
                    <span class="doc-outline__text">{{ item.text }}</span>
                  </button>
                </li>
              </ul>
            </div>

            <button
              v-show="!outlineCollapsed"
              type="button"
              class="doc-outline__top"
              @click="scrollDocTop"
            >
              <el-icon><ArrowUp /></el-icon>
              <span>回到顶部</span>
            </button>
          </aside>
        </div>

        <div v-else class="doc-edit">
          <el-input v-model="draft.title" class="doc-edit__title" placeholder="无标题笔记" />
          <div class="doc-edit__body">
            <MarkdownEditor
              ref="editorRef"
              v-model="draft.content"
              :mode="editorMode"
              height="100%"
              placeholder="从这里开始写…支持 Markdown、表格、代码块"
              @ready="onEditorReady"
            />
          </div>
        </div>
      </template>

      <template v-else>
        <header class="folder-bar">
          <div class="folder-bar__title">
            <span class="folder-bar__icon"><el-icon><Folder /></el-icon></span>
            <strong>{{ selectedFolderName }}</strong>
            <small>
              <template v-if="childFolders.length">{{ childFolders.length }} 个目录 · </template>
              {{ folderNotes.length }} 篇文档
            </small>
          </div>
          <div class="folder-bar__actions">
            <el-button
              size="small"
              :icon="FolderAdd"
              @click="createFolderIn({ module: selectedFolder.module, parentId: selectedFolder.folderId })"
            >
              新建子目录
            </el-button>
            <el-button size="small" type="primary" :icon="Plus" @click="onCreate">新建文档</el-button>
          </div>
        </header>

        <div v-if="childFolders.length || folderNotes.length" class="folder-grid">
          <article
            v-for="folder in childFolders"
            :key="`folder-${folder.id}`"
            class="folder-card is-subfolder"
            @click="selectFolder({ module: folder.module, folderId: folder.id })"
          >
            <div class="folder-card__head">
              <span class="folder-card__icon is-folder"><el-icon><FolderOpened /></el-icon></span>
              <strong>{{ folder.name }}</strong>
            </div>
            <div class="folder-card__meta">
              <span>{{ descendantNotes(folder.id) }} 篇文档</span>
              <span class="folder-card__enter">
                进入
                <el-icon><ArrowRight /></el-icon>
              </span>
            </div>
          </article>

          <article
            v-for="note in folderNotes"
            :key="note.id"
            class="folder-card"
            @click="selectNote(note)"
          >
            <div class="folder-card__head">
              <span class="folder-card__icon"><el-icon><Document /></el-icon></span>
              <strong>{{ note.title || '无标题' }}</strong>
            </div>
            <div class="folder-card__meta">
              <span><el-icon><Clock /></el-icon>{{ relative(note.updatedAt) }}</span>
              <span>{{ formatWords(note.wordCount) }}</span>
            </div>
            <button
              type="button"
              class="folder-card__delete"
              aria-label="删除"
              @click.stop="removeNote(note)"
            >
              <el-icon><Delete /></el-icon>
            </button>
          </article>
        </div>

        <div v-else class="note-main__empty">
          <span class="note-main__empty-icon"><el-icon><Document /></el-icon></span>
          <h2>「{{ selectedFolderName }}」还是空的</h2>
          <p>新建一篇 Markdown 文档，或在这个目录下再建子目录。</p>
          <el-button type="primary" :icon="Plus" @click="onCreate">新建文档</el-button>
        </div>
      </template>
    </section>

    <el-dialog v-model="moveDialogVisible" title="移动到" width="420px">
      <el-select v-model="moveTarget" class="move-select" filterable placeholder="选择目标目录">
        <el-option
          v-for="option in moveOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <template #footer>
        <el-button @click="moveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmMove">移动</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="shareVisible" title="分享这篇笔记" width="520px">
      <el-skeleton v-if="shareLoading" :rows="4" animated />
      <div v-else class="share-form">
        <div class="share-field">
          <label>分享链接</label>
          <div class="share-link">
            <span class="share-link__prefix">/share/note/</span>
            <el-input
              v-model="shareForm.token"
              maxlength="32"
              placeholder="自定义短链"
              class="share-link__input"
            />
            <el-button :icon="CopyDocument" :disabled="!shareInfo" @click="copyShareLink">
              复制
            </el-button>
          </div>
          <p class="share-hint">4-32 位字母、数字、中划线或下划线，保存后立即生效。</p>
        </div>

        <div class="share-field">
          <label>访问密码</label>
          <el-input
            v-model="shareForm.password"
            type="password"
            show-password
            :placeholder="shareInfo?.hasPassword ? '已设置，留空保持不变' : '可选，留空表示无需密码'"
          />
          <el-checkbox v-if="shareInfo?.hasPassword" v-model="shareForm.clearPassword">
            移除访问密码
          </el-checkbox>
        </div>

        <div class="share-field">
          <label>有效期</label>
          <el-select v-model="shareForm.expireMode" style="width: 100%">
            <el-option label="永久有效" value="never" />
            <el-option label="1 天后失效" value="1d" />
            <el-option label="7 天后失效" value="7d" />
            <el-option label="30 天后失效" value="30d" />
            <el-option label="自定义时间" value="custom" />
          </el-select>
          <el-date-picker
            v-if="shareForm.expireMode === 'custom'"
            v-model="shareForm.expireAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择失效时间"
            style="width: 100%"
          />
        </div>

        <p v-if="shareInfo" class="share-status">
          当前状态：已开启 · {{ shareExpireLabel }}
          <span v-if="shareInfo.hasPassword"> · 需要密码</span>
        </p>
        <p v-else class="share-status">还没有分享过这篇笔记，保存后即可生成公开链接。</p>
      </div>
      <template #footer>
        <el-button v-if="shareInfo" type="danger" plain @click="cancelShare">关闭分享</el-button>
        <el-button @click="shareVisible = false">取消</el-button>
        <el-button type="primary" :loading="shareSaving" @click="saveShare">
          {{ shareInfo ? '保存修改' : '创建分享' }}
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="recycleVisible" title="回收站" :size="recycleDrawerSize" class="recycle-drawer">
      <div class="recycle">
        <div class="recycle__head">
          <p>
            删除的笔记会保留 <strong>{{ recycleBoard.keepDays }}</strong> 天，到期自动彻底删除。
          </p>
          <el-button
            v-if="recycleBoard.items.length"
            size="small"
            text
            type="danger"
            :loading="recycleBusy === 'all'"
            @click="clearRecycled"
          >
            清空回收站
          </el-button>
        </div>

        <el-skeleton v-if="recycleLoading" :rows="5" animated />
        <div v-else-if="!recycleBoard.items.length" class="recycle__empty">
          <span class="recycle__empty-art"><el-icon><Delete /></el-icon></span>
          <strong>回收站是空的</strong>
          <p>删掉的笔记会先放到这里，{{ recycleBoard.keepDays }} 天内都可以恢复。</p>
        </div>
        <ul v-else class="recycle__list">
          <li v-for="item in recycleBoard.items" :key="item.id" class="recycle-item">
            <div class="recycle-item__top">
              <span class="recycle-item__title">{{ item.title || '无标题' }}</span>
              <span class="recycle-item__days">剩 {{ item.remainingDays }} 天</span>
            </div>
            <p class="recycle-item__excerpt">{{ item.excerpt || '空白笔记' }}</p>
            <div class="recycle-item__meta">
              <span>{{ moduleLabel(item.module) }}</span>
              <i class="recycle-item__dot" />
              <span>{{ formatWords(item.wordCount) }}</span>
              <span class="recycle-item__time">{{ relative(item.deletedAt || '') }}删除</span>
            </div>
            <div class="recycle-item__actions">
              <el-button
                size="small"
                :icon="RefreshLeft"
                :loading="recycleBusy === item.id"
                @click="restoreRecycled(item)"
              >
                恢复
              </el-button>
              <el-button
                size="small"
                text
                type="danger"
                :icon="Delete"
                :disabled="recycleBusy === item.id"
                @click="purgeRecycled(item)"
              >
                彻底删除
              </el-button>
            </div>
          </li>
        </ul>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
// 正文左右留白：随视口缩放，但不再给内容区设固定版心
$doc-gutter: clamp(20px, 3vw, 44px);

.notes-page {
  display: grid;
  grid-template-columns: 292px minmax(0, 1fr);
  // 高度完全交给外层内容区（grid 1fr）分配，两列各自内部滚动，
  // 不再用 calc(100vh - Npx) 猜顶栏高度，底部就不会留空白。
  height: 100%;
  grid-template-rows: minmax(0, 1fr);
  gap: 14px;
  min-height: 0;

  @include respond-below(md) {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto;
    height: auto;
    gap: 10px;
    min-height: 0;
  }
}

// ---------- 左侧目录 ----------
.note-side {
  display: flex;
  flex-direction: column;
  min-height: 0;
  @include card($radius-xl);
  overflow: hidden;

  @include respond-below(md) {
    max-height: 46vh;
  }
}

// 头部：品牌色晕染 + 图标徽章，和右侧文档区拉开层次
.note-side__top {
  position: relative;
  flex: 0 0 auto;
  padding: 16px 15px 14px;
  border-bottom: 1px solid color-mix(in srgb, var(--border-color) 78%, transparent);
  background:
    radial-gradient(
      120% 100% at 0% 0%,
      color-mix(in srgb, var(--brand-400) 18%, transparent),
      transparent 62%
    ),
    radial-gradient(
      84% 120% at 100% 0%,
      color-mix(in srgb, var(--brand-200) 26%, transparent),
      transparent 58%
    ),
    linear-gradient(
      180deg,
      color-mix(in srgb, var(--brand-50) 58%, var(--bg-card)),
      var(--bg-card)
    );
  overflow: hidden;

  // 右上角一圈极淡的品牌光环，避免头部成为一块死板色块
  &::after {
    position: absolute;
    top: -48px;
    right: -36px;
    width: 132px;
    height: 132px;
    border: 1px solid color-mix(in srgb, var(--brand-400) 22%, transparent);
    border-radius: 50%;
    box-shadow: 0 0 0 18px color-mix(in srgb, var(--brand-400) 6%, transparent);
    content: '';
    pointer-events: none;
  }
}

.note-side__head {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.note-side__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.note-side__mark {
  @include flex-center;
  flex: 0 0 auto;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: linear-gradient(145deg, var(--brand-400), var(--brand-600));
  color: var(--text-inverse);
  font-size: 18px;
  box-shadow:
    0 7px 16px color-mix(in srgb, var(--brand-500) 30%, transparent),
    inset 0 1px 0 rgb(255 255 255 / 32%);
}

.note-side__heading {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  strong {
    font-family: var(--font-display);
    font-size: 16px;
    font-weight: 700;
    letter-spacing: -0.01em;
    line-height: 1.2;
    color: var(--text-primary);
  }

  small {
    font-size: 10.5px;
    color: var(--text-tertiary);
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }
}

.note-create {
  @include flex-center;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  padding: 0;
  border: none;
  border-radius: 11px;
  background: linear-gradient(145deg, var(--brand-500), var(--brand-700));
  color: var(--text-inverse);
  font-size: 16px;
  cursor: pointer;
  box-shadow: 0 6px 16px color-mix(in srgb, var(--brand-500) 30%, transparent);
  transition:
    transform $duration-fast $ease-out,
    box-shadow $duration-fast $ease-out;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 20px color-mix(in srgb, var(--brand-500) 38%, transparent);
  }

  &:active {
    transform: translateY(0);
  }
}

.note-side__actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.note-recycle {
  @include flex-center;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1px solid var(--border-color);
  border-radius: 11px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 15px;
  cursor: pointer;
  transition:
    color $duration-fast $ease-out,
    border-color $duration-fast $ease-out,
    background-color $duration-fast $ease-out;

  &:hover {
    color: var(--brand-600);
    border-color: color-mix(in srgb, var(--brand-400) 55%, var(--border-color));
    background: var(--bg-card);
  }
}

// ---------- 回收站抽屉 ----------
.recycle-drawer {
  /* 420px 在 390px 窄屏上会横向溢出，按视口收口 */
  max-width: 100vw;

  :deep(.el-drawer__header) {
    margin-bottom: 0;
    padding: 18px 20px 14px;
    border-bottom: 1px solid var(--border-color);
    color: var(--text-primary);
    font-weight: 600;
  }

  :deep(.el-drawer__body) {
    padding: 16px 18px 20px;
    background: var(--bg-page);
  }
}

.recycle {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.recycle__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: $radius-lg;
  background: var(--bg-subtle);

  p {
    color: var(--text-secondary);
    font-size: 12px;

    strong {
      color: var(--brand-600);
    }
  }
}

.recycle__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 54px 16px;
  border: 1px dashed var(--border-color);
  border-radius: $radius-lg;
  background: var(--bg-card);
  text-align: center;

  strong {
    color: var(--text-primary);
    font-size: 14px;
  }

  p {
    max-width: 240px;
    color: var(--text-tertiary);
    font-size: 12px;
  }
}

.recycle__empty-art {
  @include flex-center;
  width: 46px;
  height: 46px;
  margin-bottom: 4px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--brand-100) 70%, transparent);
  color: var(--brand-600);
  font-size: 20px;
}

.recycle__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.recycle-item {
  padding: 12px 14px;
  border: 1px solid var(--border-color);
  border-radius: $radius-lg;
  background: var(--bg-card);
  transition:
    border-color $duration-fast $ease-out,
    box-shadow $duration-fast $ease-out;

  &:hover {
    border-color: color-mix(in srgb, var(--brand-300) 60%, var(--border-color));
    box-shadow: 0 6px 18px color-mix(in srgb, var(--brand-700) 8%, transparent);
  }
}

.recycle-item__top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.recycle-item__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-weight: 600;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recycle-item__days {
  flex: 0 0 auto;
  padding: 2px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--brand-100) 80%, transparent);
  color: var(--brand-700);
  font-size: 11px;
  white-space: nowrap;
}

.recycle-item__excerpt {
  display: -webkit-box;
  margin-top: 6px;
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.recycle-item__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  color: var(--text-tertiary);
  font-size: 11px;
}

.recycle-item__dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: currentcolor;
  opacity: 0.5;
}

.recycle-item__time {
  margin-left: auto;
}

.recycle-item__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
}

// 搜索框
.note-search {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;

  :deep(.el-input) {
    flex: 1;
    min-width: 0;
  }

  :deep(.el-input__wrapper) {
    border-radius: 11px;
    background: color-mix(in srgb, var(--bg-card) 78%, transparent);
    box-shadow: 0 0 0 1px var(--border-color) inset;
    transition:
      box-shadow $duration-fast $ease-out,
      background-color $duration-fast $ease-out;

    &:hover {
      box-shadow: 0 0 0 1px var(--border-strong) inset;
    }

    &.is-focus {
      background: var(--bg-card);
      box-shadow:
        0 0 0 1px color-mix(in srgb, var(--brand-400) 62%, var(--border-color)) inset,
        0 4px 12px color-mix(in srgb, var(--brand-500) 10%, transparent);
    }
  }

  :deep(.el-input__prefix) {
    color: var(--text-tertiary);
  }

  &__result {
    flex: 0 0 auto;
    font-size: 10.5px;
    color: var(--text-tertiary);
  }
}

// 列表：分组标题 + 卡片，卡片之间留白而不是挤成一行行文字
.note-side__list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 10px 10px 12px;
  @include scrollbar(6px);
}

.note-group + .note-group {
  margin-top: 14px;
}

.note-group__label {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 2px 8px;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.05em;
  color: var(--text-tertiary);

  i {
    flex: 1;
    height: 1px;
    background: var(--border-color);
  }

  em {
    font-style: normal;
    font-family: var(--font-number);
    font-weight: 500;
  }
}

.note-group__items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.note-card {
  --tone: var(--brand-500);
  position: relative;
  padding: 10px 10px 9px 13px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--bg-card);
  cursor: pointer;
  overflow: hidden;
  transition:
    transform $duration-fast $ease-out,
    border-color $duration-fast $ease-out,
    box-shadow $duration-fast $ease-out;

  &.is-work {
    --tone: var(--c-work);
  }

  &.is-study {
    --tone: var(--c-study);
  }

  &.is-life {
    --tone: var(--c-life);
  }

  &:hover {
    transform: translateY(-1px);
    border-color: color-mix(in srgb, var(--tone) 36%, var(--border-color));
    box-shadow: var(--shadow-card);

    .note-card__delete {
      opacity: 1;
    }
  }

  &.is-active {
    border-color: color-mix(in srgb, var(--brand-400) 52%, var(--border-color));
    background: linear-gradient(
      115deg,
      color-mix(in srgb, var(--brand-400) 13%, var(--bg-card)),
      var(--bg-card) 76%
    );
    box-shadow: 0 8px 20px color-mix(in srgb, var(--brand-500) 12%, transparent);

    .note-card__title {
      color: var(--brand-700);
    }

    .note-card__rail {
      opacity: 1;
      background: linear-gradient(180deg, var(--brand-400), var(--brand-600));
    }
  }

  &__rail {
    position: absolute;
    top: 11px;
    bottom: 11px;
    left: 0;
    width: 3px;
    border-radius: 0 3px 3px 0;
    background: var(--tone);
    opacity: 0.65;
  }

  &__head {
    display: flex;
    align-items: center;
    gap: 7px;
    padding-right: 20px;
  }

  &__icon {
    @include flex-center;
    flex: 0 0 auto;
    width: 22px;
    height: 22px;
    border-radius: 7px;
    background: color-mix(in srgb, var(--tone) 13%, transparent);
    color: var(--tone);
    font-size: 12px;
  }

  &__title {
    @include ellipsis(1);
    min-width: 0;
    font-size: 12.5px;
    font-weight: 600;
    color: var(--text-primary);
  }

  &__excerpt {
    @include ellipsis(2);
    min-height: 30px;
    margin: 7px 0 8px;
    font-size: 10.8px;
    line-height: 1.5;
    color: var(--text-tertiary);
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
    font-size: 10px;
    color: var(--text-tertiary);
  }

  &__pin {
    flex: 0 0 auto;
    color: $c-warn;
    font-size: 11px;
  }

  &__time {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    flex: 0 0 auto;

    .el-icon {
      font-size: 10px;
    }
  }

  &__tag {
    @include ellipsis(1);
    max-width: 64px;
    padding: 1.5px 6px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--tone) 12%, transparent);
    color: var(--tone);
    font-size: 9.5px;
  }

  &__words {
    flex: 0 0 auto;
    margin-left: auto;
    font-family: var(--font-number);
  }

  &__delete {
    @include flex-center;
    position: absolute;
    top: 8px;
    right: 8px;
    width: 20px;
    height: 20px;
    padding: 0;
    border: none;
    border-radius: 6px;
    background: color-mix(in srgb, var(--bg-card) 90%, transparent);
    color: var(--text-tertiary);
    opacity: 0;
    cursor: pointer;
    transition:
      opacity $duration-fast $ease-out,
      color $duration-fast $ease-out,
      background $duration-fast $ease-out;

    &:hover {
      background: var(--c-alert-bg);
      color: var(--c-alert);
    }
  }
}

.note-skeletons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.note-skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--bg-card);

  i {
    display: block;
    height: 9px;
    border-radius: 5px;
    background: linear-gradient(
      90deg,
      var(--bg-subtle) 25%,
      var(--bg-hover) 50%,
      var(--bg-subtle) 75%
    );
    background-size: 200% 100%;
    animation: note-shimmer 1.4s ease infinite;
  }

  i:nth-child(1) {
    width: 58%;
    height: 12px;
  }

  i:nth-child(2) {
    width: 100%;
  }

  i:nth-child(3) {
    width: 42%;
  }
}

@keyframes note-shimmer {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}

.note-empty {
  @include flex-center;
  flex-direction: column;
  gap: 7px;
  margin-top: 18px;
  padding: 26px 16px;
  border: 1px dashed var(--border-strong);
  border-radius: 14px;
  background: color-mix(in srgb, var(--bg-subtle) 55%, transparent);
  text-align: center;

  &__art {
    @include flex-center;
    width: 46px;
    height: 46px;
    margin-bottom: 4px;
    border-radius: 15px;
    background: color-mix(in srgb, var(--brand-400) 12%, var(--bg-card));
    color: var(--brand-500);
    font-size: 21px;
  }

  strong {
    font-size: 12.5px;
    color: var(--text-primary);
  }

  p {
    max-width: 210px;
    margin: 0 0 4px;
    font-size: 11px;
    line-height: 1.6;
    color: var(--text-tertiary);
  }
}

.note-side__foot {
  display: flex;
  align-items: center;
  gap: 9px;
  flex: 0 0 auto;
  margin: 8px 10px 10px;
  padding: 9px 10px;
  border: 1px solid color-mix(in srgb, var(--brand-400) 16%, var(--border-color));
  border-radius: 13px;
  background:
    radial-gradient(120% 140% at 0% 0%, color-mix(in srgb, var(--brand-400) 12%, transparent), transparent 62%),
    color-mix(in srgb, var(--bg-subtle) 72%, var(--bg-card));

  &-icon {
    @include flex-center;
    flex: 0 0 auto;
    width: 30px;
    height: 30px;
    border-radius: 10px;
    background: color-mix(in srgb, var(--brand-400) 14%, transparent);
    color: var(--brand-600);
    font-size: 14px;
  }

  &-text {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;

    strong {
      font-size: 11.5px;
      font-weight: 600;
      color: var(--text-secondary);
    }

    small {
      font-size: 10px;
      color: var(--text-tertiary);
      white-space: nowrap;
    }
  }

  &-btn {
    @include flex-center;
    width: 28px;
    height: 28px;
    margin-left: auto;
    padding: 0;
    border: 1px solid var(--border-color);
    border-radius: 9px;
    background: var(--bg-card);
    color: var(--brand-600);
    cursor: pointer;
    transition:
      transform $duration-fast $ease-out,
      border-color $duration-fast $ease-out,
      background $duration-fast $ease-out;

    &:hover {
      transform: translateY(-1px);
      border-color: var(--brand-400);
      background: var(--brand-50);
    }
  }
}

@include respond-below(md) {
  .note-side__top {
    padding: 10px 12px;
  }

  .note-side__mark {
    width: 30px;
    height: 30px;
    border-radius: 10px;
    font-size: 15px;
  }

  .note-side__heading small,
  .note-side__foot {
    display: none;
  }

  .note-search {
    margin-top: 8px;
  }

  .note-side__list {
    padding: 10px;
  }

  .note-card__excerpt {
    -webkit-line-clamp: 1;
    min-height: 0;
    margin: 5px 0 7px;
  }
}

// ---------- 右侧文档 ----------
.note-main {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  // 编辑器全屏是 position: fixed，卡片不能带 backdrop-filter，否则会被裁成卡片大小
  @include card-solid($radius-xl);
  overflow: hidden;

  @include respond-below(md) {
    height: 68vh;
    min-height: 380px;
  }
}

.note-bar {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
  // 顶栏贴边：正文有自己的阅读留白，工具栏不必跟着缩进
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-color);
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--bg-card) 97%, var(--brand-50)),
    color-mix(in srgb, var(--bg-card) 88%, var(--bg-subtle))
  );
}

.note-bar__back {
  @include flex-center;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 9px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  cursor: pointer;
  transition:
    background $duration-fast $ease-out,
    color $duration-fast $ease-out;

  &:hover {
    background: var(--brand-50);
    color: var(--brand-600);
  }
}

.note-crumb {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
}

.note-crumb__link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 0 1 auto;
  max-width: 140px;
  padding: 4px 6px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  @include ellipsis(1);
  transition:
    background $duration-fast $ease-out,
    color $duration-fast $ease-out;

  .el-icon {
    font-size: 13px;
    color: var(--brand-500);
  }

  &:hover {
    background: var(--bg-hover);
    color: var(--brand-600);
  }
}

.note-crumb__sep {
  flex: 0 0 auto;
  color: var(--border-strong);
  font-size: 11px;
}

.note-crumb__current {
  min-width: 0;
  padding-left: 2px;
  color: var(--text-primary);
  font-weight: 600;
  @include ellipsis(1);
}

.note-bar__tools {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
}

.note-bar__divider {
  width: 1px;
  height: 18px;
  margin: 0 2px;
  background: var(--border-color);
}

.bar-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--border-color);
  border-radius: 9px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition:
    border-color $duration-fast $ease-out,
    background $duration-fast $ease-out,
    color $duration-fast $ease-out;

  .el-icon {
    font-size: 14px;
  }

  &:hover {
    border-color: var(--brand-200);
    background: var(--brand-50);
    color: var(--brand-600);
  }
}

.bar-menu__danger {
  color: $c-alert;
}

.mode-switch {
  display: inline-flex;
  gap: 2px;
  flex: 0 0 auto;
  padding: 3px;
  border: 1px solid var(--border-color);
  border-radius: 11px;
  background: color-mix(in srgb, var(--bg-subtle) 80%, transparent);
}

.mode-switch__item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition:
    background $duration-fast $ease-out,
    color $duration-fast $ease-out;

  .el-icon {
    font-size: 13px;
  }

  &:hover {
    color: var(--text-primary);
  }

  &.is-active {
    background: var(--bg-card);
    color: var(--brand-600);
    font-weight: 650;
    box-shadow:
      0 1px 3px rgb(23 74 60 / 10%),
      0 0 0 1px color-mix(in srgb, var(--brand-400) 22%, transparent);
  }
}

.save-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-right: 4px;
  padding: 3px 9px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: color-mix(in srgb, var(--bg-subtle) 70%, transparent);
  color: var(--text-tertiary);
  font-size: 10.5px;
  white-space: nowrap;

  &__dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: var(--border-strong);
  }

  &.is-saved {
    color: var(--brand-600);

    .save-chip__dot {
      background: var(--brand-500);
    }
  }

  &.is-saving {
    color: var(--text-secondary);

    .save-chip__dot {
      background: $c-warn;
    }
  }

  &.is-dirty {
    color: $c-warn;

    .save-chip__dot {
      background: $c-warn;
    }
  }
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 9px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition:
    background $duration-fast $ease-out,
    color $duration-fast $ease-out;

  &:hover {
    background: var(--bg-hover);
    color: var(--brand-700);
  }

  &.is-on {
    background: var(--bg-hover);
    color: $c-warn;
  }

  &--primary {
    background: var(--brand-500);
    color: var(--text-inverse);
    box-shadow: var(--shadow-card);

    &:hover {
      background: var(--brand-600);
      color: var(--text-inverse);
    }
  }

  &--danger:hover {
    color: var(--c-alert);
  }
}

.doc-loading {
  flex: 1;
  padding: 32px 40px;
}

.doc-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  // 行高必须受限，否则预览正文会把整页撑高、doc-scroll 自己的滚动条不生效
  grid-template-rows: minmax(0, 1fr);

  &.has-outline {
    grid-template-columns: minmax(0, 1fr) 242px;
  }

  &.has-outline.is-outline-collapsed {
    grid-template-columns: minmax(0, 1fr) 56px;
  }

  @include respond-below(lg) {
    &.has-outline,
    &.has-outline.is-outline-collapsed {
      grid-template-columns: minmax(0, 1fr);
    }
  }
}

.doc-scroll {
  width: 100%;
  min-width: 0;
  overflow-x: auto;
  overflow-y: auto;
}

.doc-article {
  // 正文占满右侧可用宽度，宽屏两侧不再留固定版心的空白
  box-sizing: border-box;
  width: 100%;
  max-width: none;
  margin: 0;
  padding: 34px $doc-gutter 72px;
  // 顶部一层极淡的品牌色晕染，阅读区从工具栏往下有自然的过渡
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--brand-50) 46%, transparent) 0,
    transparent 240px
  );

  @include respond-below(md) {
    padding: 24px 18px 56px;
  }
}

// 标题区：栏目 / 目录路径 → 大标题 → 元信息胶囊 → 品牌短线
.doc-article__head {
  margin-bottom: 30px;
  padding-bottom: 22px;
  border-bottom: 1px solid color-mix(in srgb, var(--border-color) 82%, transparent);
}

.doc-article__kicker {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 11px;
  font-size: 11px;
  color: var(--text-tertiary);
}

.doc-article__module {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 9px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--brand-400) 12%, transparent);
  color: var(--brand-600);
  font-weight: 650;
  letter-spacing: 0.04em;

  &::before {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: currentColor;
    content: '';
  }

  &.is-work {
    background: color-mix(in srgb, var(--c-work) 13%, transparent);
    color: var(--c-work);
  }

  &.is-study {
    background: color-mix(in srgb, var(--c-study) 14%, transparent);
    color: var(--c-study);
  }

  &.is-life {
    background: color-mix(in srgb, var(--c-life) 16%, transparent);
    color: var(--c-life);
  }
}

.doc-article__sep {
  color: var(--border-strong);
}

.doc-article__crumb {
  max-width: 180px;
  @include ellipsis(1);
}

.doc-article__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 30px;
  font-weight: 700;
  line-height: 1.32;
  letter-spacing: -0.02em;
  color: var(--text-primary);
  text-wrap: balance;

  @include respond-below(md) {
    font-size: 24px;
  }
}

.doc-article__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 14px;
}

.doc-meta {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: color-mix(in srgb, var(--bg-subtle) 68%, transparent);
  color: var(--text-tertiary);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;

  .el-icon {
    font-size: 12px;
    color: var(--brand-500);
  }

  &.is-pinned {
    border-color: color-mix(in srgb, var(--brand-400) 26%, var(--border-color));
    color: var(--brand-600);

    .el-icon {
      color: var(--brand-500);
    }
  }
}

.doc-article__rule {
  display: block;
  width: 46px;
  height: 2px;
  margin-top: 20px;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--brand-400), transparent);
}

// 正文排版（标题/引用/表格/代码块/高亮）统一在 styles/markdown.scss 的 .markdown-body 里定义

.doc-empty {
  @include flex-center;
  flex-direction: column;
  gap: 12px;
  padding: 64px 0;
  color: var(--text-tertiary);
  font-size: 12.5px;
}

// 右侧目录：不套卡片，用一条细分隔线与正文并排，阅读时更像书页边注
.doc-outline {
  display: flex;
  flex-direction: column;
  min-height: 0;
  margin: 20px 12px 20px 0;
  padding-left: 14px;
  border-left: 1px solid var(--border-color);
  overflow: hidden;

  &.is-collapsed {
    padding-left: 0;
    align-items: center;

    .doc-outline__head {
      flex-direction: column;
      gap: 10px;
      padding: 2px 0 10px;
      border-bottom: none;
    }

    .doc-outline__title {
      flex-direction: column;
      gap: 8px;
    }

    .doc-outline__label {
      writing-mode: vertical-rl;
      letter-spacing: 0.22em;
    }

    .doc-outline__tools {
      flex-direction: column;
      gap: 4px;
    }
  }

  @include respond-below(lg) {
    display: none;
  }
}

.doc-outline__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex: 0 0 auto;
  padding: 0 2px 10px;
}

.doc-outline__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 10.5px;
  font-weight: 650;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-tertiary);
  @include ellipsis(1);

  .el-icon {
    flex: 0 0 auto;
    font-size: 13px;
    color: var(--brand-500);
  }
}

.doc-outline__tools {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: 0 0 auto;
}

.doc-outline__count {
  min-width: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--brand-400) 11%, transparent);
  color: var(--brand-600);
  font-family: var(--font-number);
  font-size: 10px;
  line-height: 17px;
  text-align: center;
}

.doc-outline__toggle {
  @include flex-center;
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  transition:
    background $duration-fast $ease-out,
    color $duration-fast $ease-out;

  .el-icon {
    font-size: 12px;
  }

  &:hover {
    background: var(--bg-hover);
    color: var(--text-primary);
  }
}

.doc-outline__scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 2px 2px 4px 0;
  @include scrollbar(3px);
}

.doc-outline__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: 4px;
}

.doc-outline__item {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  padding: 6px 8px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.45;
  text-align: left;
  cursor: pointer;
  transition:
    background $duration-fast $ease-out,
    color $duration-fast $ease-out,
    padding-left $duration-fast $ease-out;

  &.is-level-2 {
    padding-left: 18px;
    font-size: 11.5px;
  }

  &.is-level-3 {
    padding-left: 30px;
    font-size: 11px;
  }

  &:hover {
    background: color-mix(in srgb, var(--brand-400) 8%, transparent);
    color: var(--text-primary);
  }

  &.is-active {
    background: color-mix(in srgb, var(--brand-400) 13%, transparent);
    color: var(--brand-600);
    font-weight: 650;

    .doc-outline__marker {
      background: var(--brand-500);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--brand-500) 18%, transparent);
    }
  }
}

.doc-outline__marker {
  flex: 0 0 auto;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--border-strong);
  transition:
    background $duration-fast $ease-out,
    box-shadow $duration-fast $ease-out;
}

.doc-outline__text {
  min-width: 0;
  @include ellipsis(1);
}

// 目录底部的轻量动作，不抢正文视线
.doc-outline__top {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  flex: 0 0 auto;
  margin-top: 6px;
  padding: 7px 0 2px;
  border: none;
  border-top: 1px dashed var(--border-color);
  background: transparent;
  color: var(--text-tertiary);
  font-size: 10.5px;
  cursor: pointer;
  transition: color $duration-fast $ease-out;

  &:hover {
    color: var(--brand-600);
  }
}

// ---------- 编辑态 ----------
.doc-edit {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px $doc-gutter 14px;

  @include respond-below(md) {
    padding: 10px 18px 14px;
  }
}

.doc-edit__title {
  flex: 0 0 auto;

  :deep(.el-input__wrapper) {
    padding: 0 4px;
    background: transparent;
    box-shadow: none;
  }

  :deep(.el-input__inner) {
    height: 40px;
    font-family: var(--font-display);
    font-size: 22px;
    font-weight: 700;
    color: var(--text-primary);

    &::placeholder {
      font-weight: 600;
      color: var(--text-tertiary);
    }
  }
}

.doc-edit__body {
  flex: 1;
  min-height: 0;
  display: flex;
  padding-top: 6px;
  border-top: 1px solid var(--border-color);

  :deep(.md-editor) {
    flex: 1;
    min-width: 0;
    height: 100%;
  }

  // 编辑器嵌在卡片里，去掉 ByteMD 自带的外框，只留内容
  :deep(.bytemd) {
    border: none;
    border-radius: 0;
    background: transparent;
  }

  // 编辑工具栏：做成一条独立的圆角「格式条」，不再是从上贯到下的一条灰带
  :deep(.bytemd-toolbar) {
    display: flex;
    align-items: center;
    flex: 0 0 auto;
    margin: 6px 0 10px;
    padding: 5px 7px;
    border: 1px solid var(--border-color);
    border-radius: 12px;
    background: linear-gradient(
      180deg,
      color-mix(in srgb, var(--bg-card) 88%, var(--brand-50)),
      var(--bg-subtle)
    );
    box-shadow:
      inset 0 1px 0 rgb(255 255 255 / 55%),
      0 1px 2px rgb(23 74 60 / 4%);
    overflow-x: auto;
    overflow-y: hidden;
    @include scrollbar(0);
  }

  :deep(.bytemd-toolbar-left),
  :deep(.bytemd-toolbar-right) {
    display: flex;
    align-items: center;
    gap: 2px;
    float: none;
  }

  :deep(.bytemd-toolbar-right) {
    margin-left: auto;
    padding-left: 8px;
    border-left: 1px dashed var(--border-color);
  }

  :deep(.bytemd-toolbar-icon) {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex: 0 0 auto;
    width: 28px;
    height: 28px;
    margin: 0;
    border-radius: 8px;
    color: var(--text-secondary);
    transition:
      background $duration-fast $ease-out,
      color $duration-fast $ease-out,
      transform $duration-fast $ease-out;

    svg {
      width: 20px;
      height: 20px;
      padding: 2px;
    }

    &:hover {
      background: color-mix(in srgb, var(--brand-400) 13%, transparent);
      color: var(--brand-700);
    }

    &:active {
      transform: scale(0.93);
    }
  }

  :deep(.bytemd-toolbar-icon-active) {
    background: color-mix(in srgb, var(--brand-400) 17%, transparent);
    color: var(--brand-700);
  }

  // 单栏模式（窄屏 / 中等宽度）下 ByteMD 用「编辑 / 预览」文字标签切换，
  // 默认只有内边距，这里做成和页面模式开关一致的胶囊
  :deep(.bytemd-toolbar-tab) {
    display: inline-flex;
    align-items: center;
    height: 28px;
    padding: 0 12px;
    border-radius: 8px;
    color: var(--text-secondary);
    font-size: 12.5px;
    line-height: 28px;
    transition:
      background $duration-fast $ease-out,
      color $duration-fast $ease-out;

    & + & {
      margin-left: 2px;
    }

    &:hover {
      background: color-mix(in srgb, var(--brand-400) 10%, transparent);
      color: var(--text-primary);
    }
  }

  :deep(.bytemd-toolbar-tab-active) {
    background: var(--bg-card);
    color: var(--brand-600);
    font-weight: 650;
    box-shadow:
      0 1px 3px rgb(23 74 60 / 9%),
      0 0 0 1px color-mix(in srgb, var(--brand-400) 20%, transparent);
  }

  :deep(.bytemd-status) {
    display: none;
  }

  :deep(.bytemd-split .bytemd-preview) {
    border-left: 1px dashed var(--border-color);
  }

  :deep(.bytemd-preview .markdown-body) {
    padding: 10px 4% 24px;
  }
}

// ---------- 未选中 ----------
.note-main__empty {
  @include flex-center;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  padding: 40px 24px;
  text-align: center;
}

.note-main__empty-icon {
  @include flex-center;
  width: 56px;
  height: 56px;
  margin-bottom: 4px;
  border-radius: 18px;
  background: var(--brand-50);
  color: var(--brand-500);
  font-size: 24px;
}

.note-main__empty h2 {
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.note-main__empty p {
  max-width: 330px;
  margin-bottom: 6px;
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.7;
}

// ---------- 目录视图 ----------
.folder-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border-color);
}

.folder-bar__title {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 8px;
  min-width: 0;

  strong {
    overflow: hidden;
    max-width: 320px;
    color: var(--text-primary);
    font-family: var(--font-display);
    font-size: 15px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    color: var(--text-tertiary);
    font-size: 11px;
    white-space: nowrap;
  }
}

.folder-bar__icon {
  display: inline-flex;
  align-items: center;
  color: var(--brand-500);
  font-size: 16px;
}

.folder-bar__actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.folder-grid {
  display: grid;
  flex: 1;
  // 固定上限 280px：两三个条目时不会被拉成一整行的大色块
  grid-template-columns: repeat(auto-fill, minmax(220px, 280px));
  align-content: start;
  gap: 12px;
  min-height: 0;
  padding: 18px 20px 28px;
  overflow-y: auto;
}

.folder-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 16px 14px;
  border: 1px solid var(--border-color);
  border-radius: 15px;
  background:
    radial-gradient(120% 120% at 0% 0%, color-mix(in srgb, var(--brand-400) 8%, transparent), transparent 62%),
    var(--bg-card);
  cursor: pointer;
  overflow: hidden;
  transition:
    border-color $duration-fast $ease-out,
    transform $duration-fast $ease-out,
    box-shadow $duration-fast $ease-out;

  // 顶部一条品牌细线，卡片在网格里更容易被识别
  &::before {
    position: absolute;
    top: 0;
    left: 16px;
    width: 34px;
    height: 2px;
    border-radius: 0 0 2px 2px;
    background: linear-gradient(90deg, var(--brand-400), transparent);
    content: '';
    opacity: 0.7;
  }

  &:hover {
    border-color: color-mix(in srgb, var(--brand-400) 42%, var(--border-color));
    box-shadow: var(--shadow-card);
    transform: translateY(-2px);
  }

  &:hover .folder-card__delete {
    opacity: 1;
  }
}

.folder-card__head {
  display: flex;
  align-items: center;
  gap: 8px;

  strong {
    overflow: hidden;
    color: var(--text-primary);
    font-size: 13px;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.folder-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--brand-400) 12%, transparent);
  color: var(--brand-600);
  font-size: 14px;

  &.is-folder {
    background: color-mix(in srgb, var(--brand-400) 18%, transparent);
  }
}

// 子目录卡片：比文档卡更「导航」，悬停时右侧出现进入提示
.folder-card.is-subfolder {
  border-color: color-mix(in srgb, var(--brand-400) 24%, var(--border-color));

  .folder-card__meta {
    justify-content: space-between;
  }

  &:hover {
    border-color: color-mix(in srgb, var(--brand-400) 52%, var(--border-color));
  }
}

.folder-card__enter {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-left: auto;
  color: var(--brand-600);
  font-size: 10.5px;
  opacity: 0;
  transition: opacity $duration-fast $ease-out;

  .folder-card.is-subfolder:hover & {
    opacity: 1;
  }
}

.folder-card__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--text-tertiary);
  font-size: 11px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
}

.folder-card__delete {
  position: absolute;
  top: 8px;
  right: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: $radius-sm;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  opacity: 0;
  transition: opacity $duration-fast, background $duration-fast;

  &:hover {
    background: var(--bg-subtle);
    color: var(--danger-500, #dc2626);
  }
}

.move-select {
  width: 100%;
}

// 窄屏按钮会把目录名挤成「工…」，标题独占一行、操作另起一行右对齐
@include respond-below(md) {
  .folder-bar__title {
    flex-basis: 100%;
  }

  .folder-bar__actions {
    justify-content: flex-end;
  }

  .folder-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  }
}

// ---------- 分享弹窗 ----------
.share-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.share-field {
  display: flex;
  flex-direction: column;
  gap: 6px;

  label {
    font-size: 12px;
    color: var(--text-secondary);
  }
}

.share-link {
  display: flex;
  align-items: center;
  gap: 6px;

  &__prefix {
    flex: 0 0 auto;
    padding: 0 8px;
    height: 32px;
    display: inline-flex;
    align-items: center;
    border-radius: 8px;
    background: var(--bg-subtle);
    color: var(--text-tertiary);
    font-size: 12px;
    font-family: var(--font-number);
  }

  &__input {
    flex: 1;
    min-width: 0;
  }
}

.share-hint {
  margin: 0;
  font-size: 11.5px;
  color: var(--text-tertiary);
}

.share-status {
  margin: 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 12px;
}

// ---------- 中等宽度：顶栏只留当前标题，避免面包屑压到右侧工具 ----------
@include respond-below(lg) {
  .note-crumb__link,
  .note-crumb__sep {
    display: none;
  }

  .note-crumb__current {
    font-size: 12.5px;
  }

  .note-bar__tools {
    gap: 5px;
  }

  // 中等宽度优先把空间让给标题：保存状态与按钮文案退成图标
  .save-chip,
  .bar-btn__text {
    display: none;
  }
}

// ---------- 窄屏适配（必须写在基础规则之后） ----------
@include respond-below(md) {
  .note-bar {
    gap: 8px;
    padding: 8px 12px;
  }

  // 面包屑中间层级太多，窄屏只留当前标题，避免把工具区挤到换行
  .note-crumb__link,
  .note-crumb__sep {
    display: none;
  }

  .note-crumb__current {
    font-size: 13px;
  }

  .note-bar__divider,
  .save-chip {
    display: none;
  }

  // 窄屏把撤销/重做收起来，否则标题只剩一两个字；编辑器的历史仍可用快捷键
  .note-bar .icon-btn {
    display: none;
  }

  .bar-btn {
    padding: 0 8px;
  }

  .bar-btn__text {
    display: none;
  }

  .mode-switch__item {
    padding: 4px 8px;
  }
}
</style>
