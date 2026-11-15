<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  ArrowRight,
  Collection,
  Delete,
  EditPen,
  Grid,
  Link,
  List,
  MagicStick,
  Plus,
  Search,
  Star,
  StarFilled,
  Timer,
} from '@element-plus/icons-vue'
import lakeImage from '@/assets/hero-lake.jpg'
import BookmarkSiteCard from '../components/BookmarkSiteCard.vue'
import {
  bookmarkApi,
  type BookmarkCategory,
  type BookmarkOverview,
  type BookmarkSite,
} from '../api'

type CategoryKey = 'all' | 'pinned' | 'uncategorized' | number
type SortKey = 'custom' | 'name' | 'clicks'

interface Section {
  key: string
  title: string
  hint: string
  icon: string
  color: string
  sites: BookmarkSite[]
}

const EMPTY: BookmarkOverview = {
  categories: [],
  sites: [],
  totalClicks: 0,
  pinnedCount: 0,
  uncategorizedCount: 0,
}

const COLORS = ['#0B7D77', '#4C6FFF', '#8B7CF6', '#FF9F43', '#F25F5C', '#2AA6C9']
const SORT_OPTIONS: Array<{ key: SortKey; label: string }> = [
  { key: 'custom', label: '自定义' },
  { key: 'name', label: '按名称' },
  { key: 'clicks', label: '最常用' },
]
const ICONS = [
  { key: 'folder', emoji: '📁', label: '文件夹' },
  { key: 'code', emoji: '💻', label: '开发' },
  { key: 'briefcase', emoji: '💼', label: '办公' },
  { key: 'picture', emoji: '🎨', label: '设计' },
  { key: 'reading', emoji: '📚', label: '阅读' },
  { key: 'star', emoji: '⭐', label: '收藏' },
  { key: 'link', emoji: '🔗', label: '链接' },
  { key: 'cloud', emoji: '☁️', label: '云端' },
  { key: 'coffee', emoji: '☕', label: '生活' },
  { key: 'game', emoji: '🎮', label: '娱乐' },
]

const queryClient = useQueryClient()
const overviewQuery = useQuery({
  queryKey: ['bookmarks'],
  queryFn: () => bookmarkApi.overview(),
  staleTime: 30_000,
})

const data = computed(() => overviewQuery.data.value ?? EMPTY)
const categories = computed(() => data.value.categories)
const sites = computed(() => data.value.sites)

const activeCategory = ref<CategoryKey>('all')
const keyword = ref('')
const sortBy = ref<SortKey>('custom')
const viewMode = ref<'grid' | 'list'>('grid')
const saving = ref(false)
const searchRef = ref<HTMLInputElement>()

// ---------- 派生数据 ----------
function categoryEmoji(icon: string | null | undefined): string {
  if (!icon) {
    return '📁'
  }
  const found = ICONS.find((item) => item.key === icon)
  return found ? found.emoji : icon.length <= 4 ? icon : '📁'
}

function accentOf(site: BookmarkSite): string {
  return site.color || categories.value.find((item) => item.id === site.categoryId)?.color || '#0B7D77'
}

function sortSites(list: BookmarkSite[]): BookmarkSite[] {
  const copy = [...list]
  if (sortBy.value === 'name') {
    copy.sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
  } else if (sortBy.value === 'clicks') {
    copy.sort((a, b) => b.clickCount - a.clickCount || a.name.localeCompare(b.name, 'zh-CN'))
  } else {
    copy.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id)
  }
  return copy
}

const pinnedTotal = computed(() => sites.value.filter((item) => item.pinned).length)
const uncategorizedTotal = computed(
  () => sites.value.filter((item) => item.categoryId === 0).length,
)

/** 常用网站：优先置顶，没有置顶时退化为访问次数最多的前 10 个 */
const pinnedSites = computed(() => {
  const pinned = sites.value.filter((site) => site.pinned)
  const pool = pinned.length
    ? pinned
    : [...sites.value].sort((a, b) => b.clickCount - a.clickCount || a.id - b.id)
  return sortSites(pool).slice(0, 10)
})

/** 热门推荐：按访问次数排序的前 12 个 */
const popularSites = computed(() =>
  [...sites.value]
    .sort((a, b) => b.clickCount - a.clickCount || a.name.localeCompare(b.name, 'zh-CN'))
    .slice(0, 12),
)

const isSearching = computed(() => Boolean(keyword.value.trim()))

const sections = computed<Section[]>(() => {
  const needle = keyword.value.trim().toLowerCase()
  // 搜索是全局的，不受当前分类筛选影响
  if (needle) {
    const hits = sites.value.filter((site) =>
      [site.name, site.description ?? '', site.url].some((value) =>
        value.toLowerCase().includes(needle),
      ),
    )
    return [{
      key: 'search', title: '搜索结果', hint: `找到 ${hits.length} 个站点`,
      icon: '🔍', color: '#0B7D77', sites: sortSites(hits),
    }]
  }

  const pool = sites.value.filter((site) => {
    if (activeCategory.value === 'pinned' && !site.pinned) {
      return false
    }
    if (activeCategory.value === 'uncategorized' && site.categoryId !== 0) {
      return false
    }
    if (typeof activeCategory.value === 'number' && site.categoryId !== activeCategory.value) {
      return false
    }
    return true
  })

  if (activeCategory.value === 'pinned') {
    return [{
      key: 'pinned', title: '置顶站点', hint: '最常用的入口，随手一点就到',
      icon: '⭐', color: '#FF9F43', sites: sortSites(pool),
    }]
  }
  if (activeCategory.value === 'uncategorized') {
    return [{
      key: 'uncategorized', title: '未分类', hint: '还没归到任何分类的站点',
      icon: '📥', color: '#6B7C87', sites: sortSites(pool),
    }]
  }
  if (typeof activeCategory.value === 'number') {
    const category = categories.value.find((item) => item.id === activeCategory.value)
    if (!category) {
      return []
    }
    return [{
      key: `cat-${category.id}`, title: category.name,
      hint: `${pool.length} 个站点 · 点击卡片即可打开`,
      icon: categoryEmoji(category.icon), color: category.color,
      sites: sortSites(pool),
    }]
  }

  const result: Section[] = categories.value.map((category) => ({
    key: `cat-${category.id}`,
    title: category.name,
    hint: `${pool.filter((site) => site.categoryId === category.id).length} 个站点`,
    icon: categoryEmoji(category.icon),
    color: category.color,
    sites: sortSites(pool.filter((site) => site.categoryId === category.id)),
  }))
  const loose = sortSites(pool.filter((site) => site.categoryId === 0))
  if (loose.length) {
    result.push({
      key: 'uncategorized', title: '未分类', hint: `${loose.length} 个站点`,
      icon: '📥', color: '#6B7C87', sites: loose,
    })
  }
  return result
})

const visibleCount = computed(
  () => sections.value.reduce((sum, section) => sum + section.sites.length, 0),
)
const hasVisible = computed(() => visibleCount.value > 0)

const activeCategoryName = computed(() =>
  typeof activeCategory.value === 'number'
    ? categories.value.find((item) => item.id === activeCategory.value)?.name ?? ''
    : '',
)

const scopeTitle = computed(() => {
  if (isSearching.value) {
    return '搜索结果'
  }
  if (activeCategory.value === 'pinned') {
    return '置顶站点'
  }
  if (activeCategory.value === 'uncategorized') {
    return '未分类站点'
  }
  if (typeof activeCategory.value === 'number') {
    return categories.value.find((item) => item.id === activeCategory.value)?.name ?? '全部网站'
  }
  return '全部网站'
})

const scopeHint = computed(() =>
  isSearching.value
    ? `找到 ${visibleCount.value} 个站点`
    : `${visibleCount.value} 个站点 · 拖拽卡片可调整顺序`,
)

const stats = computed(() => [
  { label: '收藏网站', value: sites.value.length, icon: Link, tone: '#0B7D77' },
  { label: '分类管理', value: categories.value.length, icon: Collection, tone: '#4C6FFF' },
  { label: '累计访问', value: data.value.totalClicks, icon: Timer, tone: '#8B7CF6' },
  { label: '置顶站点', value: pinnedTotal.value, icon: StarFilled, tone: '#FF9F43' },
])

// ---------- 交互 ----------
function scrollToAll(): void {
  document.getElementById('bm-all')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function focusCategory(category: BookmarkCategory): void {
  activeCategory.value = category.id
  scrollToAll()
}

function resetScope(): void {
  keyword.value = ''
  activeCategory.value = 'all'
}

/** Ctrl / Cmd + K 聚焦搜索框，和顶栏全局搜索的手感一致 */
function onKeydown(event: KeyboardEvent): void {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    searchRef.value?.focus()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))

function apply(overview: BookmarkOverview): void {
  queryClient.setQueryData(['bookmarks'], overview)
}

async function run(task: Promise<BookmarkOverview>, message?: string): Promise<void> {
  try {
    const overview = await task
    apply(overview)
    if (message) {
      ElMessage.success(message)
    }
  } catch {
    // 请求拦截器已经给出错误提示
  }
}

async function openSite(site: BookmarkSite): Promise<void> {
  window.open(site.url, '_blank', 'noopener,noreferrer')
  bookmarkApi.click(site.id).catch(() => undefined)
}

// ---------- 分类编辑 ----------
const categoryDialog = ref(false)
const categoryEditingId = ref<number | null>(null)
const categoryFormRef = ref<FormInstance>()
const categoryForm = ref({ name: '', icon: 'folder', color: COLORS[0] })

function openCategoryDialog(category?: BookmarkCategory): void {
  categoryEditingId.value = category?.id ?? null
  Object.assign(categoryForm.value, {
    name: category?.name ?? '',
    icon: category?.icon && ICONS.some((item) => item.key === category.icon)
      ? category.icon : 'folder',
    color: category?.color ?? COLORS[0],
  })
  categoryDialog.value = true
}

async function saveCategory(): Promise<void> {
  const valid = await categoryFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    const payload = { name: categoryForm.value.name.trim(), icon: categoryForm.value.icon, color: categoryForm.value.color }
    const overview = categoryEditingId.value
      ? await bookmarkApi.updateCategory(categoryEditingId.value, payload)
      : await bookmarkApi.createCategory(payload)
    apply(overview)
    if (!categoryEditingId.value) {
      activeCategory.value = overview.categories[overview.categories.length - 1]?.id ?? 'all'
    }
    categoryDialog.value = false
    ElMessage.success('分类已保存')
  } catch {
    // 已由拦截器提示
  } finally {
    saving.value = false
  }
}

async function removeCategory(category: BookmarkCategory): Promise<void> {
  const ok = await ElMessageBox.confirm(
    `删除分类「${category.name}」？其中的 ${category.siteCount} 个站点会移到「未分类」，不会丢失。`,
    '删除分类', { type: 'warning' },
  ).catch(() => false)
  if (!ok) {
    return
  }
  await run(bookmarkApi.deleteCategory(category.id), '分类已删除')
  if (activeCategory.value === category.id) {
    activeCategory.value = 'all'
  }
}

// ---------- 站点编辑 ----------
const siteDialog = ref(false)
const siteEditingId = ref<number | null>(null)
const siteFormRef = ref<FormInstance>()
const siteForm = ref({
  name: '', url: '', categoryId: 0, description: '', icon: '', color: COLORS[0], pinned: false,
})

function openSiteDialog(site?: BookmarkSite): void {
  siteEditingId.value = site?.id ?? null
  Object.assign(siteForm.value, {
    name: site?.name ?? '',
    url: site?.url ?? '',
    categoryId: site?.categoryId ?? (typeof activeCategory.value === 'number' ? activeCategory.value : 0),
    description: site?.description ?? '',
    icon: site?.icon ?? '',
    color: site?.color ?? COLORS[0],
    pinned: site?.pinned ?? false,
  })
  siteDialog.value = true
}

async function saveSite(): Promise<void> {
  const valid = await siteFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    const payload = {
      name: siteForm.value.name.trim(),
      url: siteForm.value.url.trim(),
      categoryId: siteForm.value.categoryId,
      description: siteForm.value.description.trim(),
      icon: siteForm.value.icon.trim(),
      color: siteForm.value.color,
      pinned: siteForm.value.pinned,
    }
    const overview = siteEditingId.value
      ? await bookmarkApi.updateSite(siteEditingId.value, payload)
      : await bookmarkApi.createSite(payload)
    apply(overview)
    siteDialog.value = false
    ElMessage.success('站点已保存')
  } catch {
    // 已由拦截器提示
  } finally {
    saving.value = false
  }
}

async function removeSite(site: BookmarkSite): Promise<void> {
  const ok = await ElMessageBox.confirm(`删除站点「${site.name}」？`, '确认删除', {
    type: 'warning',
  }).catch(() => false)
  if (!ok) {
    return
  }
  await run(bookmarkApi.deleteSite(site.id), '站点已删除')
}

async function togglePin(site: BookmarkSite): Promise<void> {
  await run(bookmarkApi.togglePin(site.id))
}

// ---------- 拖拽排序 ----------
const dragId = ref<number | null>(null)
const dragOverId = ref<number | null>(null)

function onDragStart(site: BookmarkSite, event: DragEvent): void {
  if (sortBy.value !== 'custom') {
    event.preventDefault()
    return
  }
  dragId.value = site.id
  event.dataTransfer?.setData('text/plain', String(site.id))
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function onDragOver(site: BookmarkSite, event: DragEvent): void {
  if (!dragId.value || dragId.value === site.id) {
    return
  }
  event.preventDefault()
  dragOverId.value = site.id
}

function onDrop(site: BookmarkSite, event: DragEvent): void {
  event.preventDefault()
  const fromId = dragId.value
  dragId.value = null
  dragOverId.value = null
  if (!fromId || fromId === site.id || sortBy.value !== 'custom') {
    return
  }
  // 只允许在同一分组内排序，跨分类请用编辑里的分类选择
  const section = sections.value.find((item) => item.sites.some((entry) => entry.id === site.id))
  if (!section) {
    return
  }
  const ids = section.sites.map((item) => item.id)
  const from = ids.indexOf(fromId)
  const to = ids.indexOf(site.id)
  if (from < 0 || to < 0) {
    return
  }
  ids.splice(from, 1)
  ids.splice(to, 0, fromId)
  void run(bookmarkApi.reorder('site', ids))
}

function onDragEnd(): void {
  dragId.value = null
  dragOverId.value = null
}

async function seedSites(): Promise<void> {
  await run(bookmarkApi.seed(), '常用站点已导入')
}
</script>

<template>
  <section class="bm-page">
    <header class="bm-hero">
      <img class="bm-hero__photo" :src="lakeImage" alt="" aria-hidden="true" />
      <span class="bm-hero__tint" aria-hidden="true" />
      <span class="bm-hero__haze" aria-hidden="true" />
      <span class="bm-hero__fade" aria-hidden="true" />

      <div class="bm-hero__content">
        <div class="bm-hero__top">
          <div class="bm-hero__title">
            <h1>网址导航 <span aria-hidden="true">✨</span></h1>
            <p>收藏常用网站，快速直达，开启高效在线生活 ——</p>
          </div>
          <div class="bm-hero__actions">
            <button type="button" class="bm-glass" @click="seedSites">
              <el-icon><MagicStick /></el-icon>导入常用站点
            </button>
            <button type="button" class="bm-glass is-primary" @click="openSiteDialog()">
              <el-icon><Plus /></el-icon>添加网站
            </button>
          </div>
        </div>

        <label class="bm-search">
          <el-icon><Search /></el-icon>
          <input
            ref="searchRef"
            v-model="keyword"
            type="search"
            placeholder="搜索一下，开启高效上网之旅…"
            aria-label="搜索站点"
          />
          <span v-if="keyword" class="bm-search__count">{{ visibleCount }} 条</span>
          <kbd v-else>Ctrl K</kbd>
        </label>
      </div>
    </header>

    <ul class="bm-stats">
      <li v-for="item in stats" :key="item.label" class="bm-stat">
        <span class="bm-stat__icon" :style="{ '--tone': item.tone }">
          <el-icon><component :is="item.icon" /></el-icon>
        </span>
        <span class="bm-stat__num">{{ item.value }}</span>
        <span class="bm-stat__label">{{ item.label }}</span>
      </li>
    </ul>

    <div v-if="overviewQuery.isLoading.value" class="bm-loading">正在加载导航…</div>

    <div v-else-if="!sites.length" class="bm-empty">
      <span class="bm-empty__icon"><el-icon><Link /></el-icon></span>
      <h2>这里还空着，先放几个常用站点吧</h2>
      <p>一键导入 16 个常用站点（分为 4 类），也可以自己从零开始添加。</p>
      <div class="bm-empty__actions">
        <button type="button" class="bm-btn bm-btn--primary" @click="seedSites">
          <el-icon><MagicStick /></el-icon>导入常用站点
        </button>
        <button type="button" class="bm-btn bm-btn--ghost" @click="openSiteDialog()">
          <el-icon><Plus /></el-icon>手动添加
        </button>
      </div>
    </div>

    <template v-else>
      <section v-if="!isSearching" class="bm-panel">
        <header class="bm-panel__head">
          <span class="bm-panel__icon is-star"><el-icon><StarFilled /></el-icon></span>
          <div class="bm-panel__text">
            <h2>常用网站</h2>
            <p>置顶与最常访问的入口，点开即达</p>
          </div>
          <button type="button" class="bm-panel__more" @click="openSiteDialog()">
            <el-icon><Plus /></el-icon>添加网站
          </button>
        </header>
        <div class="bm-cards">
          <BookmarkSiteCard
            v-for="site in pinnedSites"
            :key="`pin-${site.id}`"
            :site="site"
            :accent="accentOf(site)"
            @open="openSite(site)"
            @pin="togglePin(site)"
            @edit="openSiteDialog(site)"
            @remove="removeSite(site)"
          />
        </div>
      </section>

      <section v-if="!isSearching" class="bm-tiles">
        <button
          v-for="category in categories"
          :key="category.id"
          type="button"
          class="bm-tile"
          :class="{ 'is-active': activeCategory === category.id }"
          :style="{ '--tone': category.color }"
          @click="focusCategory(category)"
        >
          <span class="bm-tile__icon">{{ categoryEmoji(category.icon) }}</span>
          <span class="bm-tile__text">
            <strong>{{ category.name }}</strong>
            <small>{{ category.siteCount }} 个站点</small>
          </span>
          <span class="bm-tile__go"><el-icon><ArrowRight /></el-icon></span>
          <span class="bm-tile__ops" @click.stop>
            <button type="button" title="编辑分类" @click.stop="openCategoryDialog(category)">
              <el-icon><EditPen /></el-icon>
            </button>
            <button type="button" title="删除分类" @click.stop="removeCategory(category)">
              <el-icon><Delete /></el-icon>
            </button>
          </span>
        </button>

        <button type="button" class="bm-tile is-add" @click="openCategoryDialog()">
          <span class="bm-tile__icon"><el-icon><Plus /></el-icon></span>
          <span class="bm-tile__text">
            <strong>新建分类</strong>
            <small>把站点分组管理</small>
          </span>
        </button>
      </section>

      <section v-if="!isSearching && popularSites.length" class="bm-panel">
        <header class="bm-panel__head">
          <span class="bm-panel__icon is-hot" aria-hidden="true">🔥</span>
          <div class="bm-panel__text">
            <h2>热门推荐</h2>
            <p>按访问次数排序，最顺手的入口都在这里</p>
          </div>
          <button
            type="button"
            class="bm-panel__more"
            @click="sortBy = 'clicks'; scrollToAll()"
          >
            更多推荐<el-icon><ArrowRight /></el-icon>
          </button>
        </header>
        <div class="bm-cards is-compact">
          <BookmarkSiteCard
            v-for="site in popularSites"
            :key="`hot-${site.id}`"
            :site="site"
            :accent="accentOf(site)"
            compact
            @open="openSite(site)"
            @pin="togglePin(site)"
            @edit="openSiteDialog(site)"
            @remove="removeSite(site)"
          />
        </div>
      </section>

      <section id="bm-all" class="bm-panel">
        <header class="bm-panel__head bm-panel__head--wrap">
          <span class="bm-panel__icon is-grid"><el-icon><Grid /></el-icon></span>
          <div class="bm-panel__text">
            <h2>{{ scopeTitle }}</h2>
            <p>{{ scopeHint }}</p>
          </div>

          <div class="bm-tools">
            <div class="bm-chips">
              <button
                type="button"
                :class="{ 'is-active': activeCategory === 'all' }"
                @click="activeCategory = 'all'"
              >全部 {{ sites.length }}</button>
              <button
                type="button"
                :class="{ 'is-active': activeCategory === 'pinned' }"
                @click="activeCategory = 'pinned'"
              >置顶 {{ pinnedTotal }}</button>
              <button
                v-if="uncategorizedTotal"
                type="button"
                :class="{ 'is-active': activeCategory === 'uncategorized' }"
                @click="activeCategory = 'uncategorized'"
              >未分类 {{ uncategorizedTotal }}</button>
              <button
                v-if="activeCategoryName && !isSearching"
                type="button"
                class="is-active"
              >{{ activeCategoryName }}</button>
              <button
                v-if="activeCategory !== 'all' || isSearching"
                type="button"
                class="is-reset"
                @click="resetScope"
              >清除筛选</button>
            </div>

            <div class="bm-sort">
              <button
                v-for="item in SORT_OPTIONS"
                :key="item.key"
                type="button"
                :class="{ 'is-active': sortBy === item.key }"
                @click="sortBy = item.key"
              >{{ item.label }}</button>
            </div>

            <div class="bm-views">
              <button
                type="button"
                title="网格视图"
                :class="{ 'is-active': viewMode === 'grid' }"
                @click="viewMode = 'grid'"
              ><el-icon><Grid /></el-icon></button>
              <button
                type="button"
                title="列表视图"
                :class="{ 'is-active': viewMode === 'list' }"
                @click="viewMode = 'list'"
              ><el-icon><List /></el-icon></button>
            </div>
          </div>
        </header>

        <div v-if="!hasVisible" class="bm-empty bm-empty--small">
          <span class="bm-empty__icon"><el-icon><Search /></el-icon></span>
          <h2>没有匹配的站点</h2>
          <p>换个关键词，或点「清除筛选」看看全部站点。</p>
        </div>

        <div v-else class="bm-sections">
          <section v-for="section in sections" :key="section.key" class="bm-section">
            <header class="bm-section__head" :style="{ '--tone': section.color }">
              <span class="bm-section__icon">{{ section.icon }}</span>
              <h3>{{ section.title }}</h3>
              <small>{{ section.hint }}</small>
              <span class="bm-section__line" />
            </header>

            <div class="bm-cards" :class="{ 'is-list': viewMode === 'list' }">
              <BookmarkSiteCard
                v-for="site in section.sites"
                :key="site.id"
                :site="site"
                :accent="accentOf(site)"
                :draggable="sortBy === 'custom'"
                :dragging="dragId === site.id"
                :dragover="dragOverId === site.id"
                @open="openSite(site)"
                @pin="togglePin(site)"
                @edit="openSiteDialog(site)"
                @remove="removeSite(site)"
                @dragstart="onDragStart(site, $event)"
                @dragover="onDragOver(site, $event)"
                @drop="onDrop(site, $event)"
                @dragend="onDragEnd"
              />
            </div>
          </section>
        </div>
      </section>
    </template>

    <el-dialog v-model="siteDialog" :title="siteEditingId ? '编辑站点' : '添加网站'" width="520px" class="bm-dialog" destroy-on-close>
      <el-form ref="siteFormRef" :model="siteForm" label-position="top">
        <div class="bm-form-row">
          <el-form-item label="名称" prop="name" :rules="[{ required: true, message: '请输入站点名称', trigger: 'blur' }]">
            <el-input v-model="siteForm.name" placeholder="如：GitHub" maxlength="120" />
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="siteForm.categoryId" class="bm-select">
              <el-option :value="0" label="未分类" />
              <el-option
                v-for="category in categories"
                :key="category.id"
                :value="category.id"
                :label="category.name"
              />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="网址" prop="url" :rules="[{ required: true, message: '请输入站点地址', trigger: 'blur' }]">
          <el-input v-model="siteForm.url" placeholder="github.com（会自动补 https://）" />
        </el-form-item>
        <el-form-item label="一句话描述">
          <el-input v-model="siteForm.description" placeholder="选填，卡片上会显示" maxlength="255" />
        </el-form-item>
        <el-form-item label="自定义图标地址（选填）">
          <el-input v-model="siteForm.icon" placeholder="留空则用站点首字母生成彩色图标" maxlength="500" />
        </el-form-item>
        <div class="bm-form-row">
          <el-form-item label="主题色">
            <div class="bm-swatches">
              <button
                v-for="color in COLORS"
                :key="color"
                type="button"
                :style="{ background: color }"
                :class="{ 'is-active': siteForm.color === color }"
                :aria-label="color"
                @click="siteForm.color = color"
              />
            </div>
          </el-form-item>
          <el-form-item label="置顶">
            <div class="bm-switch">
              <button type="button" :class="{ 'is-active': siteForm.pinned }" @click="siteForm.pinned = !siteForm.pinned">
                <el-icon><Star /></el-icon>{{ siteForm.pinned ? '已置顶' : '不置顶' }}
              </button>
            </div>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <button type="button" class="bm-btn bm-btn--ghost" @click="siteDialog = false">取消</button>
        <button type="button" class="bm-btn bm-btn--primary" :disabled="saving" @click="saveSite">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </el-dialog>

    <el-dialog v-model="categoryDialog" :title="categoryEditingId ? '编辑分类' : '新建分类'" width="440px" class="bm-dialog" destroy-on-close>
      <el-form ref="categoryFormRef" :model="categoryForm" label-position="top">
        <el-form-item label="分类名称" prop="name" :rules="[{ required: true, message: '请输入分类名称', trigger: 'blur' }]">
          <el-input v-model="categoryForm.name" placeholder="如：内部系统" maxlength="64" />
        </el-form-item>
        <el-form-item label="图标">
          <div class="bm-emoji">
            <button
              v-for="item in ICONS"
              :key="item.key"
              type="button"
              :title="item.label"
              :class="{ 'is-active': categoryForm.icon === item.key }"
              @click="categoryForm.icon = item.key"
            >{{ item.emoji }}</button>
          </div>
        </el-form-item>
        <el-form-item label="分类色">
          <div class="bm-swatches">
            <button
              v-for="color in COLORS"
              :key="color"
              type="button"
              :style="{ background: color }"
              :class="{ 'is-active': categoryForm.color === color }"
              :aria-label="color"
              @click="categoryForm.color = color"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <button type="button" class="bm-btn bm-btn--ghost" @click="categoryDialog = false">取消</button>
        <button type="button" class="bm-btn bm-btn--primary" :disabled="saving" @click="saveCategory">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.bm-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
  margin-top: -4px;
}

// ---------- 头部实景 ----------
.bm-hero {
  position: relative;
  margin: 0 (-1 * $content-padding);
  height: 228px;
  overflow: hidden;
  isolation: isolate;
}

.bm-hero__photo {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 46%;
  z-index: 0;
  filter: var(--hero-photo-filter);
  opacity: var(--hero-photo-opacity);
  -webkit-mask-image: linear-gradient(180deg, #000 0%, #000 54%, rgba(0, 0, 0, 0.5) 80%, transparent 100%);
  mask-image: linear-gradient(180deg, #000 0%, #000 54%, rgba(0, 0, 0, 0.5) 80%, transparent 100%);
}

.bm-hero__tint {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: var(--hero-tint);
  mix-blend-mode: var(--hero-tint-blend, normal);
  opacity: var(--hero-tint-opacity, 1);
}

.bm-hero__haze {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: var(--hero-haze);
}

.bm-hero__fade {
  position: absolute;
  inset: auto 0 0 0;
  height: 96px;
  z-index: 1;
  background: linear-gradient(180deg, transparent 0%, var(--hero-fade-mid) 64%, var(--bg-page) 100%);
}

.bm-hero__content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  padding: 26px 42px 44px;
}

.bm-hero__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.bm-hero__title {
  h1 {
    font-family: var(--font-display);
    font-size: 30px;
    font-weight: 700;
    line-height: 1.2;
    letter-spacing: 0.6px;
    color: var(--hero-title);
    text-shadow: 0 1px 18px rgba(10, 30, 40, 0.12);

    span {
      font-size: 19px;
      vertical-align: 6px;
    }
  }

  p {
    margin-top: 8px;
    font-size: 12.5px;
    letter-spacing: 0.5px;
    color: var(--hero-sub);
  }
}

.bm-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.bm-glass {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border: 1px solid var(--glass-border);
  border-radius: 11px;
  background: color-mix(in srgb, var(--surface-glass-strong) 82%, transparent);
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(23, 74, 60, 0.08);
  backdrop-filter: blur(12px);
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 10px 22px rgba(23, 74, 60, 0.14);
  }

  &:active {
    transform: scale(0.98);
  }

  &.is-primary {
    color: #fff;
    border-color: transparent;
    background: linear-gradient(135deg, var(--brand-400), var(--brand-600));
    box-shadow: 0 8px 20px color-mix(in srgb, var(--brand-500) 28%, transparent);
  }
}

.bm-search {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  max-width: 720px;
  height: 44px;
  padding: 0 14px 0 16px;
  border: 1px solid var(--glass-border);
  border-radius: 14px;
  background: color-mix(in srgb, var(--surface-glass-strong) 90%, transparent);
  color: var(--text-tertiary);
  box-shadow: 0 10px 26px rgba(23, 74, 60, 0.08);
  backdrop-filter: blur(14px) saturate(1.1);
  cursor: text;
  transition: border-color $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &:focus-within {
    border-color: color-mix(in srgb, var(--brand-400) 55%, transparent);
    box-shadow: 0 12px 30px rgba(23, 74, 60, 0.12),
      0 0 0 3px color-mix(in srgb, var(--brand-400) 14%, transparent);
  }

  input {
    flex: 1;
    min-width: 0;
    border: 0;
    outline: 0;
    background: transparent;
    color: var(--text-primary);
    font-size: 13px;

    &::placeholder {
      color: var(--text-tertiary);
    }

    &::-webkit-search-cancel-button {
      display: none;
    }
  }

  kbd {
    flex: 0 0 auto;
    padding: 2px 7px;
    border: 1px solid var(--border-color);
    border-radius: 7px;
    background: var(--bg-subtle);
    color: var(--text-tertiary);
    font-family: var(--font-number);
    font-size: 10.5px;
  }

  &__count {
    flex: 0 0 auto;
    font-size: 11px;
    color: var(--brand-600);
  }
}

// ---------- 统计条 ----------
.bm-stats {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px;
  margin: -30px 20px 0;
  padding: 13px 22px;
  list-style: none;
  border: 1px solid var(--glass-border);
  border-radius: 18px;
  background: var(--surface-glass-strong);
  box-shadow: 0 12px 30px rgba(23, 74, 60, 0.08);
  backdrop-filter: blur(12px);
}

.bm-stat {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  padding: 0 6px;

  & + & {
    border-left: 1px solid var(--border-color);
    padding-left: 16px;
  }

  &__icon {
    @include flex-center;
    flex: 0 0 auto;
    width: 30px;
    height: 30px;
    border-radius: 10px;
    color: var(--tone);
    font-size: 15px;
    background: color-mix(in srgb, var(--tone) 13%, transparent);
  }

  &__num {
    font-family: var(--font-number);
    font-size: 20px;
    font-weight: 700;
    line-height: 1;
    color: var(--text-primary);
    font-variant-numeric: tabular-nums;
  }

  &__label {
    font-size: 11.5px;
    color: var(--text-tertiary);
    white-space: nowrap;
    @include ellipsis(1);
  }
}

// ---------- 面板 ----------
.bm-panel {
  @include card($radius-xl);
  padding: 16px 18px 18px;
}

.bm-panel__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 13px;

  &--wrap {
    flex-wrap: wrap;
  }
}

.bm-panel__icon {
  @include flex-center;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  border-radius: 10px;
  font-size: 15px;

  &.is-star {
    color: #f0a020;
    background: color-mix(in srgb, #f0a020 14%, transparent);
  }

  &.is-hot {
    background: color-mix(in srgb, #f25f5c 12%, transparent);
  }

  &.is-grid {
    color: var(--brand-600);
    background: color-mix(in srgb, var(--brand-400) 14%, transparent);
  }
}

.bm-panel__text {
  flex: 1;
  min-width: 0;

  h2 {
    font-family: var(--font-display);
    font-size: 14.5px;
    font-weight: 700;
    color: var(--text-primary);
  }

  p {
    margin-top: 1px;
    font-size: 11px;
    color: var(--text-tertiary);
    @include ellipsis(1);
  }
}

.bm-panel__more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
  padding: 4px 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text-tertiary);
  font-size: 11.5px;
  cursor: pointer;
  transition: color $duration-fast $ease-out, background $duration-fast $ease-out;

  &:hover {
    color: var(--brand-600);
    background: var(--bg-hover);
  }
}

// ---------- 站点网格 ----------
.bm-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(212px, 1fr));
  gap: 10px;

  &.is-compact {
    grid-template-columns: repeat(auto-fill, minmax(178px, 1fr));
  }

  &.is-list {
    grid-template-columns: minmax(0, 1fr);
  }
}

// ---------- 分类磁贴 ----------
.bm-tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(212px, 1fr));
  gap: 12px;
}

.bm-tile {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid color-mix(in srgb, var(--tone) 20%, var(--border-color));
  border-radius: 16px;
  background: linear-gradient(
    118deg,
    color-mix(in srgb, var(--tone) 13%, var(--bg-card)),
    color-mix(in srgb, var(--tone) 4%, var(--bg-card))
  );
  text-align: left;
  cursor: pointer;
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out,
    border-color $duration-fast $ease-out;

  &:hover {
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--tone) 38%, var(--border-color));
    box-shadow: 0 12px 26px color-mix(in srgb, var(--tone) 16%, transparent);

    .bm-tile__go {
      opacity: 1;
      transform: translateX(0);
    }

    .bm-tile__ops {
      opacity: 1;
      pointer-events: auto;
    }
  }

  &.is-active {
    border-color: color-mix(in srgb, var(--tone) 48%, var(--border-color));
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--tone) 18%, transparent);
  }

  &__icon {
    @include flex-center;
    flex: 0 0 auto;
    width: 36px;
    height: 36px;
    border-radius: 12px;
    font-size: 17px;
    background: color-mix(in srgb, var(--tone) 20%, transparent);
  }

  &__text {
    flex: 1;
    min-width: 0;

    strong {
      display: block;
      font-size: 13px;
      font-weight: 700;
      color: var(--text-primary);
      @include ellipsis(1);
    }

    small {
      display: block;
      margin-top: 2px;
      font-size: 11px;
      color: var(--text-tertiary);
    }
  }

  &__go {
    flex: 0 0 auto;
    color: color-mix(in srgb, var(--tone) 78%, var(--text-secondary));
    font-size: 13px;
    opacity: 0.55;
    transform: translateX(-3px);
    transition: opacity $duration-fast $ease-out, transform $duration-fast $ease-out;
  }

  &__ops {
    position: absolute;
    top: 50%;
    right: 8px;
    display: flex;
    gap: 1px;
    padding: 2px;
    border-radius: 9px;
    background: color-mix(in srgb, var(--bg-card) 94%, transparent);
    box-shadow: var(--shadow-card);
    opacity: 0;
    pointer-events: none;
    transform: translateY(-50%);
    transition: opacity $duration-fast $ease-out;

    button {
      @include flex-center;
      width: 24px;
      height: 24px;
      border: 0;
      border-radius: 7px;
      background: transparent;
      color: var(--text-tertiary);
      font-size: 12px;
      cursor: pointer;

      &:hover {
        color: var(--brand-600);
        background: var(--bg-hover);
      }
    }
  }

  &.is-add {
    justify-content: center;
    border-style: dashed;
    border-color: color-mix(in srgb, var(--brand-400) 34%, var(--border-color));
    background: color-mix(in srgb, var(--brand-50) 60%, var(--bg-card));

    .bm-tile__icon {
      color: var(--brand-600);
      background: color-mix(in srgb, var(--brand-400) 14%, transparent);
    }

    .bm-tile__text strong {
      color: var(--brand-700, var(--brand-600));
    }
  }
}

// ---------- 全部站点工具条 ----------
.bm-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.bm-chips {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-subtle);

  button {
    height: 26px;
    padding: 0 10px;
    border: 0;
    border-radius: 7px;
    background: transparent;
    color: var(--text-secondary);
    font-size: 11.5px;
    white-space: nowrap;
    cursor: pointer;
    transition: background $duration-fast $ease-out, color $duration-fast $ease-out;

    &:hover {
      color: var(--text-primary);
    }

    &.is-active {
      color: var(--brand-700, var(--brand-600));
      font-weight: 600;
      background: var(--bg-card);
      box-shadow: 0 2px 6px rgba(23, 74, 60, 0.08);
    }

    &.is-reset {
      color: var(--text-tertiary);
      text-decoration: underline;
      text-underline-offset: 2px;
    }
  }
}

.bm-sort,
.bm-views {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-card);

  button {
    height: 26px;
    padding: 0 10px;
    border: 0;
    border-radius: 7px;
    background: transparent;
    color: var(--text-tertiary);
    font-size: 11.5px;
    cursor: pointer;
    transition: background $duration-fast $ease-out, color $duration-fast $ease-out;

    &:hover {
      color: var(--text-primary);
    }

    &.is-active {
      color: #fff;
      font-weight: 600;
      background: linear-gradient(135deg, var(--brand-400), var(--brand-600));
    }
  }
}

.bm-views button {
  @include flex-center;
  width: 28px;
  padding: 0;
  font-size: 13px;
}

// ---------- 分组 ----------
.bm-sections {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.bm-section {
  &__head {
    display: flex;
    align-items: center;
    gap: 9px;
    margin-bottom: 9px;
  }

  &__icon {
    @include flex-center;
    flex: 0 0 auto;
    width: 26px;
    height: 26px;
    border-radius: 9px;
    font-size: 13px;
    background: color-mix(in srgb, var(--tone) 13%, transparent);
  }

  h3 {
    font-size: 12.5px;
    font-weight: 700;
    color: var(--text-primary);
    white-space: nowrap;
  }

  small {
    font-size: 11px;
    color: var(--text-tertiary);
    white-space: nowrap;
  }

  &__line {
    flex: 1;
    height: 1px;
    background: linear-gradient(90deg, var(--border-color), transparent);
  }
}

// ---------- 空态 ----------
.bm-loading {
  padding: 60px 20px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}

.bm-empty {
  @include card($radius-xl);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 48px 24px;
  text-align: center;

  &__icon {
    @include flex-center;
    width: 62px;
    height: 62px;
    border-radius: 20px;
    color: var(--brand-600);
    font-size: 28px;
    background: color-mix(in srgb, var(--brand-100) 65%, transparent);
    box-shadow: 0 10px 24px color-mix(in srgb, var(--brand-500) 14%, transparent);
  }

  h2 {
    font-size: 15px;
    font-weight: 700;
    color: var(--text-primary);
  }

  p {
    font-size: 12.5px;
    color: var(--text-secondary);
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 10px;
    margin-top: 8px;
  }

  &--small {
    padding: 34px 24px;
    border: 1px dashed var(--border-color);
    background: var(--bg-subtle);
    box-shadow: none;
  }

  &--small &__icon {
    width: 46px;
    height: 46px;
    font-size: 20px;
  }
}

// ---------- 按钮 ----------
.bm-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 34px;
  padding: 0 15px;
  border: 1px solid transparent;
  border-radius: 10px;
  font-size: 12.5px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out,
    background $duration-fast $ease-out;

  &:active {
    transform: scale(0.97);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  &--primary {
    color: #fff;
    background: linear-gradient(135deg, var(--brand-400), var(--brand-600));
    box-shadow: 0 6px 16px color-mix(in srgb, var(--brand-500) 24%, transparent);

    &:hover {
      box-shadow: 0 10px 22px color-mix(in srgb, var(--brand-500) 32%, transparent);
    }
  }

  &--ghost {
    color: var(--text-secondary);
    border-color: color-mix(in srgb, var(--border-strong) 80%, transparent);
    background: color-mix(in srgb, var(--bg-card) 82%, transparent);

    &:hover {
      color: var(--brand-600);
      border-color: var(--brand-300, var(--brand-400));
      background: var(--bg-hover);
    }
  }
}

// ---------- 弹窗 ----------
.bm-dialog {
  :deep(.el-dialog) {
    border-radius: 18px;
  }

  :deep(.el-dialog__title) {
    font-weight: 700;
  }

  :deep(.el-form-item__label) {
    font-size: 12px;
    font-weight: 600;
    color: var(--text-secondary);
  }

  :deep(.el-dialog__footer) {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
  }
}

.bm-form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.bm-select {
  width: 100%;
}

.bm-swatches {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding-top: 4px;

  button {
    width: 24px;
    height: 24px;
    border: 2px solid transparent;
    border-radius: 8px;
    cursor: pointer;
    transition: transform $duration-fast $ease-out;

    &:hover {
      transform: scale(1.1);
    }

    &.is-active {
      border-color: var(--text-primary);
      box-shadow: 0 0 0 2px var(--bg-card) inset;
    }
  }
}

.bm-emoji {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;

  button {
    @include flex-center;
    width: 32px;
    height: 32px;
    border: 1px solid var(--border-color);
    border-radius: 9px;
    background: var(--bg-card);
    font-size: 16px;
    cursor: pointer;
    transition: border-color $duration-fast $ease-out, background $duration-fast $ease-out;

    &:hover {
      background: var(--bg-hover);
    }

    &.is-active {
      border-color: var(--brand-500);
      background: color-mix(in srgb, var(--brand-100) 70%, transparent);
    }
  }
}

.bm-switch {
  padding-top: 4px;

  button {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 30px;
    padding: 0 12px;
    border: 1px solid var(--border-color);
    border-radius: 9px;
    background: var(--bg-card);
    color: var(--text-secondary);
    font-size: 12px;
    cursor: pointer;

    &.is-active {
      color: #ff9f43;
      border-color: color-mix(in srgb, #ff9f43 45%, transparent);
      background: color-mix(in srgb, #ff9f43 10%, transparent);
    }
  }
}

@include respond-below(md) {
  .bm-hero {
    height: 246px;
  }

  .bm-hero__content {
    padding: 22px 20px 48px;
  }

  .bm-stats {
    margin: -28px 10px 0;
    padding: 11px 12px;
    gap: 2px;
  }

  .bm-stat {
    gap: 7px;

    & + & {
      padding-left: 10px;
    }

    &__num {
      font-size: 17px;
    }

    &__label {
      font-size: 10.5px;
    }
  }

  .bm-tools {
    width: 100%;
  }
}

@include respond-below(sm) {
  .bm-hero {
    height: 248px;
  }

  .bm-hero__content {
    padding: 18px 16px 52px;
    gap: 13px;
  }

  .bm-hero__top {
    flex-direction: column;
    gap: 12px;
  }

  .bm-hero__title h1 {
    font-size: 23px;
  }

  .bm-hero__title p {
    font-size: 11.5px;
  }

  .bm-hero__actions {
    width: 100%;

    .bm-glass {
      flex: 1;
    }
  }

  .bm-search {
    height: 40px;
    font-size: 12px;
  }

  .bm-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px 2px;
    padding: 12px 14px;

    .bm-stat:nth-child(3) {
      border-left: 0;
      padding-left: 0;
    }
  }

  .bm-panel {
    padding: 14px 13px 15px;
  }

  .bm-panel__head {
    gap: 8px;
  }

  .bm-cards {
    grid-template-columns: minmax(0, 1fr);
    gap: 9px;

    &.is-compact {
      grid-template-columns: minmax(0, 1fr);
    }
  }

  .bm-tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 9px;
  }

  .bm-tile {
    padding: 12px;
    gap: 9px;

    &__icon {
      width: 32px;
      height: 32px;
      font-size: 15px;
    }

    &__go {
      display: none;
    }
  }

  .bm-chips {
    flex: 1 1 100%;
    overflow-x: auto;
    @include scrollbar(4px);
  }

  .bm-form-row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
