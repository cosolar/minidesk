<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Star, StarFilled } from '@element-plus/icons-vue'
import { useDebounceFn } from '@vueuse/core'
import dayjs from 'dayjs'
import MarkdownEditor from '@/shared/components/MarkdownEditor.vue'
import { noteApi, type NoteSummary } from '../api'
import { lifeLogApi } from '@/features/life/lifelog/api'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()

const keyword = ref('')
const selectedId = ref<string | null>(null)
const saveState = ref<'saved' | 'saving' | 'dirty'>('saved')

/** 统一笔记：工作记录 / 学习笔记 / 生活日记，日记读的是 life_log */
const TABS = [
  { key: 'all', label: '全部' },
  { key: 'work', label: '工作记录' },
  { key: 'study', label: '学习笔记' },
  { key: 'diary', label: '生活日记' },
] as const

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

function onCreate(): void {
  if (isDiary.value) {
    router.push('/life/diary')
    return
  }
  createMutation.mutate()
}

const listQuery = useQuery({
  queryKey: ['notes', moduleFilter, keyword],
  queryFn: () =>
    noteApi.page({ module: moduleFilter.value, keyword: keyword.value || undefined, size: 100 }),
  enabled: computed(() => !isDiary.value),
  staleTime: 10_000,
})

const notes = computed(() => listQuery.data.value?.list ?? [])

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

const createMutation = useMutation({
  mutationFn: () => noteApi.create({ title: '', content: '', module: moduleFilter.value ?? 'work', type: 'note' }),
  onSuccess: async (note) => {
    await queryClient.invalidateQueries({ queryKey: ['notes'] })
    selectedId.value = note.id
  },
})

const saveMutation = useMutation({
  mutationFn: () =>
    noteApi.update(selectedId.value as string, {
      title: draft.title,
      content: draft.content,
      pinned: draft.pinned,
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

const autosave = useDebounceFn(() => {
  if (!selectedId.value || syncing) {
    return
  }
  saveState.value = 'saving'
  saveMutation.mutate()
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

function selectNote(note: NoteSummary): void {
  if (note.id === selectedId.value) {
    return
  }
  selectedId.value = note.id
}

async function removeNote(note: NoteSummary): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`删除「${note.title}」？`, '确认删除', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  }).catch(() => false)
  if (!confirmed) {
    return
  }
  await noteApi.remove(note.id)
  if (selectedId.value === note.id) {
    selectedId.value = null
    draft.title = ''
    draft.content = ''
  }
  await queryClient.invalidateQueries({ queryKey: ['notes'] })
  ElMessage.success('已删除')
}

async function togglePin(): Promise<void> {
  if (!selectedId.value) {
    return
  }
  draft.pinned = !draft.pinned
  await noteApi.togglePin(selectedId.value, draft.pinned)
  queryClient.invalidateQueries({ queryKey: ['notes'] })
}

function updatedText(value: string): string {
  return dayjs(value).format('MM-DD HH:mm')
}

const saveLabel = computed(() => {
  if (saveState.value === 'saving') {
    return '保存中…'
  }
  return saveState.value === 'dirty' ? '未保存' : '已保存'
})
</script>

<template>
  <div class="notes-page">
    <aside class="notes-list">
      <header class="notes-list__head">
        <h1>笔记</h1>
        <el-button type="primary" :icon="Plus" circle size="small" @click="onCreate" />
      </header>

      <div class="notes-tabs">
        <button
          v-for="tab in TABS"
          :key="tab.key"
          type="button"
          class="notes-tabs__item"
          :class="{ 'is-active': activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>

      <el-input
        v-if="!isDiary"
        v-model="keyword"
        placeholder="搜索标题或正文"
        clearable
        size="small"
        class="notes-list__search"
      />

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
        <li v-if="!diaries.length" class="notes-list__empty">
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
            {{ note.title || '无标题' }}
          </div>
          <div class="note-item__meta">
            <span>{{ note.wordCount }} 字</span>
            <span>{{ updatedText(note.updatedAt) }}</span>
          </div>
          <button class="note-item__delete" type="button" aria-label="删除" @click.stop="removeNote(note)">
            <el-icon><Delete /></el-icon>
          </button>
        </li>
      </ul>

      <div v-else class="notes-list__empty">
        <p>还没有笔记</p>
        <el-button size="small" @click="createMutation.mutate()">新建一篇</el-button>
      </div>
    </aside>

    <section class="notes-editor">
      <template v-if="selectedId">
        <header class="notes-editor__head">
          <el-input v-model="draft.title" placeholder="标题（留空则取正文首行）" class="notes-editor__title" />
          <div class="notes-editor__actions">
            <span class="save-state" :class="`is-${saveState}`">{{ saveLabel }}</span>
            <el-button link :icon="draft.pinned ? StarFilled : Star" @click="togglePin">
              {{ draft.pinned ? '已置顶' : '置顶' }}
            </el-button>
          </div>
        </header>

        <MarkdownEditor v-model="draft.content" :height="560" placeholder="写点什么…支持 Markdown 与快捷键" />
      </template>

      <div v-else class="notes-editor__empty">
        <el-icon class="notes-editor__empty-icon"><Star /></el-icon>
        <p>选择左侧笔记开始编辑，或新建一篇</p>
        <el-button type="primary" @click="createMutation.mutate()">新建笔记</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.notes-page {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
  padding-top: 8px;
  align-items: start;

  @include respond-below(md) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.notes-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 12px;
  @include card($radius-xl);
  height: calc(100vh - 120px);
  overflow: hidden;

  @include respond-below(md) {
    height: auto;
    max-height: 40vh;
  }
}

.notes-list__head {
  display: flex;
  align-items: center;
  justify-content: space-between;

  h1 {
    font-size: 15px;
    font-weight: 600;
  }
}

.notes-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 10px;
  padding: 3px;
  border-radius: 10px;
  background: var(--bg-subtle);
}

.notes-tabs__item {
  flex: 1;
  padding: 5px 4px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 11.5px;
  cursor: pointer;
  transition: background $duration-fast $ease-out, color $duration-fast $ease-out;

  &.is-active {
    background: var(--bg-card);
    color: var(--brand-700);
    font-weight: 600;
    box-shadow: 0 2px 8px rgba(23, 74, 60, 0.08);
  }
}

.notes-list__search {
  flex: 0 0 auto;
}

.note-items {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0 -4px;
  padding: 0 4px;
}

.note-item {
  position: relative;
  padding: 9px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background $duration-fast $ease-out;

  &:hover {
    background: var(--bg-hover);

    .note-item__delete {
      opacity: 1;
    }
  }

  &.is-active {
    background: var(--brand-50);

    .note-item__title {
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
  @include ellipsis(1);
}

.note-item__pin {
  color: var(--c-warn);
  font-size: 11px;
}

.note-item__meta {
  display: flex;
  gap: 10px;
  margin-top: 3px;
  font-size: 10.5px;
  color: var(--text-tertiary);
}

.note-item__delete {
  position: absolute;
  top: 8px;
  right: 8px;
  opacity: 0;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: opacity $duration-fast $ease-out;

  &:hover {
    color: var(--c-alert);
  }
}

.notes-list__empty {
  @include flex-center;
  flex-direction: column;
  gap: 10px;
  padding: 40px 0;
  color: var(--text-tertiary);
  font-size: 12px;
}

.notes-editor {
  @include card($radius-xl);
  padding: 14px 16px 16px;
  min-height: calc(100vh - 120px);
}

.notes-editor__head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.notes-editor__title {
  :deep(.el-input__wrapper) {
    box-shadow: none;
    padding-left: 0;

    input {
      font-size: 18px;
      font-weight: 600;
    }
  }
}

.notes-editor__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.save-state {
  font-size: 10.5px;
  color: var(--text-tertiary);

  &.is-saved {
    color: var(--brand-600);
  }

  &.is-dirty {
    color: var(--c-warn);
  }
}

.notes-editor__empty {
  @include flex-center;
  flex-direction: column;
  gap: 12px;
  min-height: 420px;
  color: var(--text-tertiary);
  font-size: 12.5px;
}

.notes-editor__empty-icon {
  font-size: 34px;
  color: var(--brand-200);
}
</style>
