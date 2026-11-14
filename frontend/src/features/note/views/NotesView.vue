<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CopyDocument,
  Delete,
  Document,
  EditPen,
  Plus,
  Search,
  Share,
  Star,
  StarFilled,
  View,
} from '@element-plus/icons-vue'
import { useDebounceFn, useWindowSize } from '@vueuse/core'
import dayjs from 'dayjs'
import MarkdownEditor from '@/shared/components/MarkdownEditor.vue'
import MarkdownPreview, { type MarkdownHeading } from '@/shared/components/MarkdownPreview.vue'
import { useAppStore } from '@/core/stores/app'
import { noteApi, type NoteShare, type NoteSharePayload, type NoteSummary } from '../api'
import { lifeLogApi } from '@/features/life/lifelog/api'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const appStore = useAppStore()

type ViewMode = 'preview' | 'edit'

const keyword = ref('')
const selectedId = ref<string | null>(null)
const saveState = ref<'saved' | 'saving' | 'dirty'>('saved')
/** 默认只读预览，点「编辑」才进编辑器 */
const mode = ref<ViewMode>('preview')
const outline = ref<MarkdownHeading[]>([])
const activeHeading = ref('')
const previewRef = ref<{ scrollTo: (id: string) => void } | null>(null)
const editorRef = ref<{ focus: () => void } | null>(null)

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

/** 统一笔记：工作记录 / 学习笔记 / 生活日记，日记读的是 life_log */
const TABS = [
  { key: 'all', label: '全部' },
  { key: 'work', label: '工作记录' },
  { key: 'study', label: '学习笔记' },
  { key: 'diary', label: '生活日记' },
] as const

const MODULE_LABEL: Record<string, string> = {
  work: '工作记录',
  study: '学习笔记',
  life: '生活日记',
}

type TabKey = (typeof TABS)[number]['key']

const activeTab = ref<TabKey>(route.query.module === 'work' ? 'work' : 'all')
const isDiary = computed(() => activeTab.value === 'diary')
const moduleFilter = computed(() =>
  activeTab.value === 'work' || activeTab.value === 'study' ? activeTab.value : undefined,
)

const diaryQuery = useQuery({
  queryKey: ['note-diaries'],
  queryFn: () => lifeLogApi.page(undefined, 1, 100),
  enabled: isDiary,
  staleTime: 10_000,
})

const diaries = computed(() => diaryQuery.data.value?.list ?? [])

const listQuery = useQuery({
  queryKey: ['notes', moduleFilter, keyword],
  queryFn: () =>
    noteApi.page({ module: moduleFilter.value, keyword: keyword.value || undefined, size: 100 }),
  enabled: computed(() => !isDiary.value),
  staleTime: 10_000,
})

const notes = computed(() => listQuery.data.value?.list ?? [])
const sideCount = computed(() => (isDiary.value ? diaries.value.length : notes.value.length))

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

const activeModule = computed(() => detailQuery.data.value?.module ?? moduleFilter.value ?? 'work')
const updatedAtText = computed(() => detailQuery.data.value?.updatedAt ?? '')
const wordCount = computed(() => draft.content.replace(/\s/g, '').length)

const createMutation = useMutation({
  mutationFn: () =>
    noteApi.create({ title: '', content: '', module: moduleFilter.value ?? 'work', type: 'note' }),
  onSuccess: async (note) => {
    await queryClient.invalidateQueries({ queryKey: ['notes'] })
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
    queryClient.invalidateQueries({ queryKey: ['notes'] })
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
  if (isDiary.value) {
    router.push('/life/diary')
    return
  }
  createMutation.mutate()
}

function selectNote(note: NoteSummary): void {
  if (note.id === selectedId.value) {
    return
  }
  if (saveState.value === 'dirty') {
    persist()
  }
  selectedId.value = note.id
}

function switchMode(next: ViewMode): void {
  mode.value = next
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

async function confirmRemove(id: string, title: string): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`删除「${title || '无标题'}」？`, '确认删除', {
    type: 'warning',
    confirmButtonText: '删除',
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
  await queryClient.invalidateQueries({ queryKey: ['notes'] })
  ElMessage.success('已删除')
}

function removeNote(note: NoteSummary): void {
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
  queryClient.invalidateQueries({ queryKey: ['notes'] })
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
</script>

<template>
  <div class="notes-page">
    <aside class="note-side">
      <header class="note-side__head">
        <div class="note-side__title">
          笔记
          <span class="note-side__count">{{ sideCount }}</span>
        </div>
        <button type="button" class="icon-btn icon-btn--primary" aria-label="新建笔记" @click="onCreate">
          <el-icon><Plus /></el-icon>
        </button>
      </header>

      <div class="note-tabs">
        <button
          v-for="tab in TABS"
          :key="tab.key"
          type="button"
          class="note-tabs__item"
          :class="{ 'is-active': activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>

      <el-input
        v-if="!isDiary"
        v-model="keyword"
        :prefix-icon="Search"
        placeholder="搜索标题或正文"
        clearable
        size="small"
        class="note-side__search"
      />

      <div class="note-side__list">
        <ul v-if="isDiary" class="note-items">
          <li
            v-for="item in diaries"
            :key="item.id"
            class="note-item"
            @click="router.push('/life/diary')"
          >
            <div class="note-item__title">{{ item.title || '无标题' }}</div>
            <div class="note-item__meta">
              <span>{{ item.logDate }}</span>
              <span>{{ item.mood || '记录' }}</span>
            </div>
          </li>
          <li v-if="!diaries.length" class="note-side__empty">
            <p>还没有日记，去「日记」写一篇</p>
            <el-button size="small" @click="router.push('/life/diary')">去写日记</el-button>
          </li>
        </ul>

        <el-skeleton v-else-if="listQuery.isLoading.value" :rows="6" animated />

        <ul v-else-if="notes.length" class="note-items">
          <li
            v-for="note in notes"
            :key="note.id"
            class="note-item"
            :class="{ 'is-active': note.id === selectedId }"
            @click="selectNote(note)"
          >
            <div class="note-item__title">
              <el-icon v-if="note.pinned" class="note-item__pin"><StarFilled /></el-icon>
              <span class="note-item__text">{{ note.title || '无标题' }}</span>
            </div>
            <div class="note-item__meta">
              <span>{{ note.wordCount }} 字</span>
              <span>{{ relative(note.updatedAt) }}</span>
            </div>
            <button
              class="note-item__delete"
              type="button"
              aria-label="删除"
              @click.stop="removeNote(note)"
            >
              <el-icon><Delete /></el-icon>
            </button>
          </li>
        </ul>

        <div v-else class="note-side__empty">
          <p>还没有笔记</p>
          <el-button size="small" @click="createMutation.mutate()">新建一篇</el-button>
        </div>
      </div>
    </aside>

    <section class="note-main">
      <template v-if="selectedId">
        <header class="note-bar">
          <div class="note-bar__left">
            <div class="mode-switch" role="tablist" aria-label="视图模式">
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
            </div>
            <div class="note-bar__crumb">
              <span class="note-bar__module">{{ moduleLabel(activeModule) }}</span>
              <span class="note-bar__sep">/</span>
              <span class="note-bar__name">{{ draft.title || '无标题' }}</span>
            </div>
          </div>

          <div class="note-bar__right">
            <span class="save-chip" :class="`is-${saveState}`">
              <i class="save-chip__dot" />
              {{ saveLabel }}
            </span>
            <el-tooltip :content="draft.pinned ? '取消置顶' : '置顶'" placement="top">
              <button
                type="button"
                class="icon-btn"
                :class="{ 'is-on': draft.pinned }"
                aria-label="置顶"
                @click="togglePin"
              >
                <el-icon>
                  <component :is="draft.pinned ? StarFilled : Star" />
                </el-icon>
              </button>
            </el-tooltip>
            <el-tooltip content="分享这篇笔记" placement="top">
              <button
                type="button"
                class="icon-btn"
                aria-label="分享这篇笔记"
                @click="openShare"
              >
                <el-icon><Share /></el-icon>
              </button>
            </el-tooltip>
            <el-tooltip content="删除笔记" placement="top">
              <button
                type="button"
                class="icon-btn icon-btn--danger"
                aria-label="删除笔记"
                @click="removeCurrent"
              >
                <el-icon><Delete /></el-icon>
              </button>
            </el-tooltip>
          </div>
        </header>

        <div v-if="detailQuery.isLoading.value" class="doc-loading">
          <el-skeleton :rows="8" animated />
        </div>

        <div v-else-if="mode === 'preview'" class="doc-body" :class="{ 'has-outline': outline.length }">
          <div class="doc-scroll">
            <article class="doc-article">
              <h1 class="doc-article__title">{{ draft.title || '无标题' }}</h1>
              <div class="doc-article__meta">
                <span>{{ wordCount }} 字</span>
                <i class="doc-article__dot" />
                <span>更新于 {{ relative(updatedAtText) }}</span>
                <i class="doc-article__dot" />
                <span>{{ moduleLabel(activeModule) }}</span>
              </div>

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

          <aside v-if="outline.length" class="doc-outline">
            <div class="doc-outline__head">
              目录
              <span>{{ outline.length }}</span>
            </div>
            <ul class="doc-outline__list">
              <li v-for="item in outline" :key="item.id">
                <button
                  type="button"
                  class="doc-outline__item"
                  :class="{ 'is-active': activeHeading === item.id }"
                  :style="{ paddingLeft: `${10 + (item.level - 1) * 12}px` }"
                  @click="goHeading(item.id)"
                >
                  {{ item.text }}
                </button>
              </li>
            </ul>
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

      <div v-else class="note-main__empty">
        <span class="note-main__empty-icon">
          <el-icon><Document /></el-icon>
        </span>
        <h2>选择一篇笔记开始阅读</h2>
        <p>左侧按工作、学习、生活分组，也可以直接新建一篇，用 Markdown 记录想法。</p>
        <el-button type="primary" :icon="Plus" @click="createMutation.mutate()">新建笔记</el-button>
      </div>
    </section>

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
  </div>
</template>

<style scoped lang="scss">
.notes-page {
  display: grid;
  grid-template-columns: 272px minmax(0, 1fr);
  gap: 14px;
  height: calc(100vh - 118px);
  min-height: 560px;
  padding-top: 8px;

  @include respond-below(md) {
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
    height: auto;
    min-height: 0;
  }
}

// ---------- 左侧目录 ----------
.note-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  padding: 14px 12px 10px;
  @include card($radius-xl);
  overflow: hidden;

  @include respond-below(md) {
    max-height: 42vh;
  }
}

.note-side__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2px;
}

.note-side__title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 650;
  color: var(--text-primary);
}

.note-side__count {
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--bg-subtle);
  color: var(--text-tertiary);
  font-size: 10.5px;
  font-weight: 500;
}

.note-tabs {
  display: flex;
  gap: 2px;
  padding: 3px;
  border-radius: 11px;
  background: var(--bg-subtle);
}

.note-tabs__item {
  flex: 1;
  padding: 5px 2px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 11.5px;
  cursor: pointer;
  transition:
    background $duration-fast $ease-out,
    color $duration-fast $ease-out;

  &:hover {
    color: var(--text-primary);
  }

  &.is-active {
    background: var(--bg-card);
    color: var(--brand-700);
    font-weight: 600;
    box-shadow: var(--shadow-card);
  }
}

.note-side__search {
  :deep(.el-input__wrapper) {
    border-radius: 10px;
    background: var(--bg-subtle);
    box-shadow: none;
  }
}

.note-side__list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  margin: 0 -4px;
  padding: 0 4px 4px;
}

.note-items {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.note-item {
  position: relative;
  padding: 8px 26px 8px 11px;
  border-radius: 10px;
  cursor: pointer;
  transition: background $duration-fast $ease-out;

  &::before {
    content: '';
    position: absolute;
    top: 9px;
    bottom: 9px;
    left: 0;
    width: 2.5px;
    border-radius: 2px;
    background: var(--brand-400);
    opacity: 0;
    transition: opacity $duration-fast $ease-out;
  }

  &:hover {
    background: var(--bg-hover);

    .note-item__delete {
      opacity: 1;
    }
  }

  &.is-active {
    background: linear-gradient(
      100deg,
      color-mix(in srgb, var(--brand-400) 17%, transparent),
      color-mix(in srgb, var(--brand-400) 4%, transparent)
    );
    box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--brand-400) 20%, transparent);

    &::before {
      opacity: 1;
      background: linear-gradient(180deg, var(--brand-400), var(--brand-600));
    }

    .note-item__text {
      color: var(--brand-700);
      font-weight: 600;
    }
  }
}

.note-item__title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  color: var(--text-primary);
}

.note-item__text {
  @include ellipsis(1);
}

.note-item__pin {
  flex: 0 0 auto;
  color: $c-warn;
  font-size: 11px;
}

.note-item__meta {
  display: flex;
  gap: 8px;
  margin-top: 2px;
  font-size: 10.5px;
  color: var(--text-tertiary);
}

.note-item__delete {
  position: absolute;
  top: 7px;
  right: 6px;
  padding: 2px;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  opacity: 0;
  cursor: pointer;
  transition:
    opacity $duration-fast $ease-out,
    color $duration-fast $ease-out;

  &:hover {
    color: var(--c-alert);
  }
}

.note-side__empty {
  @include flex-center;
  flex-direction: column;
  gap: 8px;
  padding: 34px 12px;
  color: var(--text-tertiary);
  font-size: 12px;
  text-align: center;
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex: 0 0 auto;
  padding: 10px 16px;
  border-bottom: 1px solid var(--border-color);
  background: linear-gradient(
    180deg,
    var(--bg-card) 0%,
    color-mix(in srgb, var(--bg-card) 88%, var(--bg-subtle)) 100%
  );
}

.note-bar__left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.note-bar__right {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
}

.mode-switch {
  display: inline-flex;
  gap: 2px;
  flex: 0 0 auto;
  padding: 2px;
  border-radius: 10px;
  background: var(--bg-subtle);
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
    color: var(--brand-700);
    font-weight: 600;
    box-shadow: var(--shadow-card);
  }
}

.note-bar__crumb {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 11.5px;
  color: var(--text-tertiary);

  @include respond-below(lg) {
    display: none;
  }
}

.note-bar__module {
  flex: 0 0 auto;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 11px;
}

.note-bar__sep {
  color: var(--border-strong);
}

.note-bar__name {
  @include ellipsis(1);
  color: var(--text-secondary);
}

.save-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-right: 4px;
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--bg-subtle);
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

  &.has-outline {
    grid-template-columns: minmax(0, 1fr) 196px;
  }

  @include respond-below(lg) {
    &.has-outline {
      grid-template-columns: minmax(0, 1fr);
    }
  }
}

.doc-scroll {
  min-width: 0;
  overflow-y: auto;
}

.doc-article {
  max-width: 788px;
  margin: 0 auto;
  padding: 30px 44px 72px;

  @include respond-below(md) {
    padding: 22px 18px 56px;
  }
}

.doc-article__title {
  font-family: var(--font-display);
  font-size: 27px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}

.doc-article__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: 10px 0 24px;
  font-size: 11.5px;
  color: var(--text-tertiary);
}

.doc-article__dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--border-strong);
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

.doc-outline {
  min-height: 0;
  overflow-y: auto;
  padding: 26px 12px 26px 16px;
  border-left: 1px solid var(--border-color);

  @include respond-below(lg) {
    display: none;
  }
}

.doc-outline__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  padding-left: 10px;
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--text-secondary);

  span {
    color: var(--text-tertiary);
    font-weight: 500;
  }
}

.doc-outline__list {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.doc-outline__item {
  width: 100%;
  padding: 5px 10px;
  border: none;
  border-left: 2px solid transparent;
  border-radius: 0 8px 8px 0;
  background: transparent;
  color: var(--text-tertiary);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  @include ellipsis(1);
  transition:
    background $duration-fast $ease-out,
    color $duration-fast $ease-out;

  &:hover {
    background: var(--bg-hover);
    color: var(--text-primary);
  }

  &.is-active {
    border-left-color: var(--brand-500);
    background: var(--brand-50);
    color: var(--brand-700);
    font-weight: 600;
  }
}

// ---------- 编辑态 ----------
.doc-edit {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 16px 14px;
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

  :deep(.bytemd-toolbar) {
    padding: 2px 0 8px;
    border-bottom: none;
    background: transparent;
  }

  :deep(.bytemd-status) {
    display: none;
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
</style>
