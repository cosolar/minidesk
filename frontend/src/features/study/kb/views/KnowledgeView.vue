<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { Document, FolderOpened, Link, Plus, Refresh } from '@element-plus/icons-vue'
import { useDebounceFn } from '@vueuse/core'
import { kbApi, type KbDocNode, type KbScope } from '../api'

interface TreeNode {
  label: string
  path: string
  type: string
  children?: TreeNode[]
}

const queryClient = useQueryClient()

const scope = ref<KbScope>('mine')
const keyword = ref('')
const docKeyword = ref('')
const searchKeyword = ref('')
const selectedSlug = ref('')
const selectedPath = ref('')
const previewHost = ref<HTMLDivElement>()
const darkMode = ref(false)

const statusQuery = useQuery({
  queryKey: ['kb', 'status'],
  queryFn: () => kbApi.status(),
  staleTime: 60_000,
})

const booksQuery = useQuery({
  queryKey: ['kb', 'books', scope, keyword],
  queryFn: () =>
    kbApi.books({ scope: scope.value, keyword: keyword.value || undefined, size: 50 }),
  staleTime: 60_000,
})

const books = computed(() => booksQuery.data.value?.list ?? [])
const currentBook = computed(
  () => books.value.find((item) => item.slug === selectedSlug.value) ?? null,
)
const org = computed(
  () => currentBook.value?.orgSlug || statusQuery.data.value?.defaultOrg || '',
)
const status = computed(() => statusQuery.data.value)
const connected = computed(() => Boolean(status.value?.configured && status.value?.connected))

/** 个人库为空时自动展示站点公开库，避免第一次进来是一屏空白 */
let autoSwitched = false
watch(
  () => booksQuery.data.value,
  (page) => {
    if (scope.value === 'mine' && page && page.list.length === 0 && !autoSwitched) {
      autoSwitched = true
      scope.value = 'public'
    }
  },
)

watch(scope, () => {
  selectedSlug.value = ''
  selectedPath.value = ''
  docKeyword.value = ''
  searchKeyword.value = ''
})

watch(
  books,
  (list) => {
    if (!list.length) {
      selectedSlug.value = ''
      return
    }
    if (!list.some((item) => item.slug === selectedSlug.value)) {
      selectedSlug.value = list[0].slug
    }
  },
  { immediate: true },
)

const treeQuery = useQuery({
  queryKey: ['kb', 'tree', scope, selectedSlug, org],
  queryFn: () =>
    kbApi.tree({ scope: scope.value, org: org.value || undefined, slug: selectedSlug.value }),
  enabled: computed(() => Boolean(selectedSlug.value)),
  staleTime: 60_000,
})

function toTree(nodes: KbDocNode[]): TreeNode[] {
  return nodes.map((node) => ({
    label: node.name.toLowerCase().endsWith('.md') ? node.name.slice(0, -3) : node.name,
    path: node.path,
    type: node.type,
    children: node.children?.length ? toTree(node.children) : undefined,
  }))
}

const treeData = computed(() => toTree(treeQuery.data.value ?? []))
const expandedKeys = ref<string[]>([])

function firstDoc(nodes: KbDocNode[]): string {
  for (const node of nodes) {
    if (node.type === 'doc') {
      return node.path
    }
    const found = firstDoc(node.children ?? [])
    if (found) {
      return found
    }
  }
  return ''
}

function hasPath(nodes: KbDocNode[], path: string): boolean {
  return nodes.some(
    (node) => node.path === path || hasPath(node.children ?? [], path),
  )
}

watch(
  () => treeQuery.data.value,
  (nodes) => {
    if (!nodes) {
      return
    }
    if (!selectedPath.value || !hasPath(nodes, selectedPath.value)) {
      selectedPath.value = firstDoc(nodes)
    }
    expandedKeys.value = nodes.slice(0, 3).map((node) => node.path)
  },
)

const commitKeyword = useDebounceFn((value: string) => {
  searchKeyword.value = value.trim()
}, 300)
watch(docKeyword, (value) => commitKeyword(value))

const searchQuery = useQuery({
  queryKey: ['kb', 'search', scope, selectedSlug, org, searchKeyword],
  queryFn: () =>
    kbApi.search({
      scope: scope.value,
      org: org.value || undefined,
      slug: selectedSlug.value,
      q: searchKeyword.value,
      bookName: currentBook.value?.name,
    }),
  enabled: computed(
    () => Boolean(selectedSlug.value && searchKeyword.value.length > 0),
  ),
  staleTime: 30_000,
})

const docQuery = useQuery({
  queryKey: ['kb', 'doc', scope, selectedSlug, org, selectedPath],
  queryFn: () =>
    kbApi.doc({
      scope: scope.value,
      org: org.value || undefined,
      slug: selectedSlug.value,
      path: selectedPath.value,
    }),
  enabled: computed(() => Boolean(selectedSlug.value && selectedPath.value)),
  staleTime: 0,
})

const doc = computed(() => docQuery.data.value)

onMounted(() => {
  darkMode.value = document.documentElement.dataset.theme === 'dark'
  void import('vditor/dist/index.css')
})

/** Markdown 正文交给 Vditor 渲染；公开库的 html 由知识库服务端渲染好，直接展示 */
watch(
  doc,
  async (value) => {
    await nextTick()
    if (!previewHost.value) {
      return
    }
    previewHost.value.innerHTML = ''
    if (!value?.markdown) {
      return
    }
    const { default: VditorClass } = await import('vditor')
    if (!previewHost.value) {
      return
    }
    VditorClass.preview(previewHost.value, value.markdown, {
      mode: darkMode.value ? 'dark' : 'light',
    })
  },
  { flush: 'post' },
)

function onNodeClick(value: unknown): void {
  const node = value as TreeNode
  if (node.type === 'doc') {
    selectedPath.value = node.path
  }
}

function selectHit(hit: { path: string }): void {
  selectedPath.value = hit.path
  docKeyword.value = ''
}

function openExternal(url?: string): void {
  if (!url) {
    ElMessage.warning('暂无可打开的链接')
    return
  }
  window.open(url, '_blank', 'noopener')
}

function refreshAll(): void {
  queryClient.invalidateQueries({ queryKey: ['kb'] })
}

const createBookVisible = ref(false)
const bookForm = reactive({ name: '', description: '' })
const createBookMutation = useMutation({
  mutationFn: () =>
    kbApi.createBook({ name: bookForm.name, description: bookForm.description }),
  onSuccess: () => {
    ElMessage.success('知识库已创建')
    createBookVisible.value = false
    bookForm.name = ''
    bookForm.description = ''
    scope.value = 'mine'
    queryClient.invalidateQueries({ queryKey: ['kb', 'books'] })
  },
})

const createDocVisible = ref(false)
const docForm = reactive({ path: '', content: '' })
const createDocMutation = useMutation({
  mutationFn: () =>
    kbApi.createDoc({
      org: org.value || undefined,
      slug: selectedSlug.value,
      path: docForm.path,
      content: docForm.content,
    }),
  onSuccess: (result) => {
    ElMessage.success('文档已写入知识库')
    createDocVisible.value = false
    docForm.path = ''
    docForm.content = ''
    queryClient.invalidateQueries({ queryKey: ['kb', 'tree'] })
    selectedPath.value = result.path
    queryClient.invalidateQueries({ queryKey: ['kb', 'doc'] })
  },
})

function openCreateDoc(): void {
  if (!connected.value) {
    ElMessage.warning('请先在设置页配置具备读写权限的访问令牌')
    return
  }
  docForm.path = ''
  docForm.content = ''
  createDocVisible.value = true
}
</script>

<template>
  <div class="kb-page">
    <header class="page-head">
      <div>
        <h1>知识库</h1>
        <p class="page-head__sub">连接 docs.minims.cn，文档树与正文实时读取，不在应用内重复造一份</p>
      </div>
      <div class="page-head__actions">
        <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
        <el-button :icon="Link" @click="openExternal(status?.siteUrl)">打开站点</el-button>
        <el-button v-if="scope === 'mine'" type="primary" :icon="Plus" @click="openCreateDoc">
          新建文档
        </el-button>
      </div>
    </header>

    <section class="status-bar" :class="connected ? 'is-ok' : 'is-off'">
      <div class="status-bar__main">
        <span class="dot" />
        <template v-if="connected">
          已连接 · {{ status?.displayName || status?.username }} · 组织 {{ status?.defaultOrg }}
        </template>
        <template v-else>
          {{ status?.message || '正在检查知识库连接' }}
          （公开库无需令牌也可浏览）
        </template>
      </div>
      <RouterLink v-if="!connected" to="/settings" class="status-bar__link">去设置</RouterLink>
      <span v-else class="status-bar__site">{{ status?.apiHost }}</span>
    </section>

    <div class="scope-tabs">
      <button
        type="button"
        :class="{ 'is-active': scope === 'mine' }"
        @click="scope = 'mine'"
      >
        我的知识库
      </button>
      <button
        type="button"
        :class="{ 'is-active': scope === 'public' }"
        @click="scope = 'public'"
      >
        站点公开库
      </button>
      <span class="scope-tabs__hint">
        {{ scope === 'mine' ? '需要访问令牌，含私有库' : '匿名只读，仅已发布内容' }}
      </span>
    </div>

    <div class="kb-board">
      <section class="kb-panel kb-panel--books">
        <div class="kb-panel__head">
          <h2>知识库</h2>
          <el-button
            v-if="scope === 'mine' && connected"
            link
            :icon="Plus"
            @click="createBookVisible = true"
          >
            新建
          </el-button>
        </div>
        <el-input v-model="keyword" placeholder="搜索知识库名称" clearable size="small" />
        <div class="kb-panel__body">
          <el-skeleton v-if="booksQuery.isLoading.value" :rows="5" animated />
          <ul v-else-if="books.length" class="book-list">
            <li
              v-for="book in books"
              :key="book.id"
              class="book-item"
              :class="{ 'is-active': book.slug === selectedSlug }"
              @click="selectedSlug = book.slug"
            >
              <div class="book-item__title">
                <el-icon><FolderOpened /></el-icon>
                <span>{{ book.name }}</span>
              </div>
              <p class="book-item__desc">{{ book.description || '暂无描述' }}</p>
              <div class="book-item__meta">
                <span>{{ book.docCount }} 篇</span>
                <span>{{ book.visibility }}</span>
              </div>
            </li>
          </ul>
          <div v-else class="kb-empty">
            <p>{{ scope === 'mine' ? '你还没有自己的知识库' : '没有匹配的知识库' }}</p>
            <el-button v-if="scope === 'mine' && connected" size="small" @click="createBookVisible = true">
              创建第一个
            </el-button>
            <el-button v-else-if="scope === 'mine'" size="small" @click="scope = 'public'">
              看看站点公开库
            </el-button>
          </div>
        </div>
      </section>

      <section class="kb-panel kb-panel--tree">
        <div class="kb-panel__head">
          <h2>目录</h2>
          <span class="kb-panel__count">{{ treeQuery.data.value?.length ?? 0 }} 个顶层节点</span>
        </div>
        <el-input v-model="docKeyword" placeholder="搜索文档标题" clearable size="small" />
        <div class="kb-panel__body">
          <ul v-if="searchKeyword && searchQuery.data.value?.length" class="hit-list">
            <li v-for="hit in searchQuery.data.value" :key="hit.path" @click="selectHit(hit)">
              <el-icon><Document /></el-icon>
              <span>{{ hit.title }}</span>
            </li>
          </ul>
          <p v-else-if="searchKeyword && !searchQuery.isLoading.value" class="kb-tip">没有匹配的文档</p>
          <el-skeleton v-else-if="treeQuery.isLoading.value" :rows="6" animated />
          <el-tree
            v-else-if="treeData.length"
            :key="selectedSlug"
            :data="treeData"
            node-key="path"
            :props="{ label: 'label', children: 'children' }"
            :default-expanded-keys="expandedKeys"
            :current-node-key="selectedPath"
            highlight-current
            :expand-on-click-node="false"
            @node-click="onNodeClick"
          />
          <div v-else class="kb-empty">
            <p>{{ selectedSlug ? '这个知识库还没有文档' : '先选一个知识库' }}</p>
          </div>
        </div>
      </section>

      <section class="kb-panel kb-panel--doc">
        <div class="kb-panel__head">
          <div class="doc-title">
            <h2>{{ doc?.title || doc?.name || '文档预览' }}</h2>
            <p v-if="selectedPath" class="doc-title__path">{{ selectedPath }}</p>
          </div>
          <el-button v-if="doc" link :icon="Link" @click="openExternal(doc.sourceUrl)">
            打开原文
          </el-button>
        </div>
        <div class="kb-panel__body kb-preview-wrap">
          <el-skeleton v-if="docQuery.isLoading.value" :rows="10" animated />
          <div v-else-if="doc?.markdown" ref="previewHost" class="kb-preview vditor-reset" />
          <div v-else-if="doc?.html" class="kb-preview vditor-reset" v-html="doc.html" />
          <div v-else class="kb-empty">
            <el-icon class="kb-empty__icon"><Document /></el-icon>
            <p>从中间目录选一篇文档开始阅读</p>
          </div>
        </div>
      </section>
    </div>

    <el-dialog v-model="createBookVisible" title="新建知识库" width="440px">
      <p class="dialog-hint">会在知识库站点的默认组织下创建一个私有库。</p>
      <el-input v-model="bookForm.name" placeholder="知识库名称" class="dialog-input" />
      <el-input
        v-model="bookForm.description"
        type="textarea"
        :rows="3"
        placeholder="描述（可选）"
        class="dialog-input"
      />
      <template #footer>
        <el-button @click="createBookVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="createBookMutation.isPending.value"
          :disabled="!bookForm.name.trim()"
          @click="createBookMutation.mutate()"
        >
          创建
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createDocVisible" title="写入知识库文档" width="560px">
      <p class="dialog-hint">
        目标：{{ currentBook?.name || '未选择' }}。路径相对知识库根目录，.md 可省略。
      </p>
      <el-input v-model="docForm.path" placeholder="例如 笔记/今日总结" class="dialog-input" />
      <el-input
        v-model="docForm.content"
        type="textarea"
        :rows="10"
        placeholder="Markdown 正文"
        class="dialog-input"
      />
      <template #footer>
        <el-button @click="createDocVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="createDocMutation.isPending.value"
          :disabled="!docForm.path.trim() || !docForm.content.trim() || !selectedSlug"
          @click="createDocMutation.mutate()"
        >
          写入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.kb-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 8px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;

  h1 {
    font-size: 22px;
    font-weight: 700;
  }
}

.page-head__sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.page-head__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-radius: 12px;
  font-size: 12px;
  background: var(--c-warn-bg, rgba(234, 160, 60, 0.1));
  color: var(--text-secondary);

  &.is-ok {
    background: var(--brand-50);
    color: var(--brand-700);
  }

  .dot {
    display: inline-block;
    width: 7px;
    height: 7px;
    margin-right: 8px;
    border-radius: 50%;
    background: currentColor;
  }
}

.status-bar__link {
  color: var(--brand-600);
  white-space: nowrap;
}

.status-bar__site {
  color: var(--text-tertiary);
  white-space: nowrap;
}

.scope-tabs {
  display: flex;
  align-items: center;
  gap: 6px;

  button {
    padding: 6px 14px;
    border: 0;
    border-radius: 8px;
    background: transparent;
    color: var(--text-secondary);
    font-size: 12.5px;
    cursor: pointer;

    &.is-active {
      background: var(--brand-50);
      color: var(--brand-700);
      font-weight: 600;
    }
  }
}

.scope-tabs__hint {
  margin-left: auto;
  font-size: 11px;
  color: var(--text-tertiary);
}

.kb-board {
  display: grid;
  grid-template-columns: 270px 280px minmax(0, 1fr);
  gap: 14px;
  align-items: stretch;

  @include respond-below(md) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.kb-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  min-height: 480px;
  @include card($radius-xl);

  @include respond-below(md) {
    min-height: 260px;
    max-height: 60vh;
  }
}

.kb-panel__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;

  h2 {
    font-size: 14px;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.kb-panel__count {
  font-size: 11px;
  color: var(--text-tertiary);
  white-space: nowrap;
}

.kb-panel__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.book-list,
.hit-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
  list-style: none;
}

.book-item {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: var(--bg-subtle, rgba(0, 0, 0, 0.02));
  cursor: pointer;

  &.is-active {
    border-color: var(--brand-400);
    background: var(--brand-50);
  }
}

.book-item__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.book-item__desc {
  margin-top: 6px;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.book-item__meta {
  display: flex;
  gap: 10px;
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-tertiary);
}

.hit-list li {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 9px;
  border-radius: 8px;
  font-size: 12px;
  cursor: pointer;

  &:hover {
    background: var(--brand-50);
  }

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.kb-tip {
  margin-top: 12px;
  font-size: 11.5px;
  color: var(--text-tertiary);
}

.kb-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 28px 12px;
  text-align: center;
  font-size: 12px;
  color: var(--text-tertiary);
}

.kb-empty__icon {
  font-size: 28px;
  opacity: 0.5;
}

.doc-title {
  min-width: 0;

  p {
    margin-top: 3px;
    font-size: 11px;
    color: var(--text-tertiary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.kb-preview-wrap {
  padding-right: 4px;
}

.kb-preview {
  font-size: 13.5px;
  line-height: 1.8;
  word-break: break-word;

  :deep(img) {
    max-width: 100%;
  }

  :deep(pre) {
    overflow: auto;
  }
}

.dialog-hint {
  margin-bottom: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-secondary);
}

.dialog-input {
  margin-bottom: 10px;
}
</style>
