<script setup lang="ts">
import { computed, ref } from 'vue'
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
  MoreFilled,
  Plus,
  Search,
  Star,
  StarFilled,
  Top,
} from '@element-plus/icons-vue'
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
// 移动端没有 hover，操作按钮收进「更多」浮层，避免挤占卡片文字
const mobileOpsId = ref<number | null>(null)

function toggleMobileOps(site: BookmarkSite): void {
  mobileOpsId.value = mobileOpsId.value === site.id ? null : site.id
}

const stats = computed(() => [
  { label: '网站', value: sites.value.length, icon: Link, tone: '#0B7D77' },
  { label: '分类', value: categories.value.length, icon: Collection, tone: '#4C6FFF' },
  { label: '累计访问', value: data.value.totalClicks, icon: ArrowRight, tone: '#8B7CF6' },
  { label: '置顶', value: data.value.pinnedCount, icon: StarFilled, tone: '#FF9F43' },
])

const pinnedTotal = computed(() => sites.value.filter((item) => item.pinned).length)
const uncategorizedTotal = computed(
  () => sites.value.filter((item) => item.categoryId === 0).length,
)

function categoryEmoji(icon: string | null | undefined): string {
  if (!icon) {
    return '📁'
  }
  const found = ICONS.find((item) => item.key === icon)
  return found ? found.emoji : icon.length <= 4 ? icon : '📁'
}

function hostOf(url: string): string {
  return url.replace(/^https?:\/\//, '').replace(/\/.*$/, '')
}

function initialOf(name: string): string {
  return name.trim().slice(0, 1).toUpperCase() || 'W'
}

function onIconError(event: Event): void {
  const target = event.target as HTMLImageElement | null
  if (target) {
    target.style.display = 'none'
  }
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

const sections = computed<Section[]>(() => {
  const needle = keyword.value.trim().toLowerCase()
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
    if (!needle) {
      return true
    }
    return [site.name, site.description ?? '', site.url].some((value) =>
      value.toLowerCase().includes(needle),
    )
  })

  if (needle) {
    return [{
      key: 'search', title: '搜索结果', hint: `找到 ${pool.length} 个站点`,
      icon: '🔍', color: '#0B7D77', sites: sortSites(pool),
    }]
  }
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

const hasVisible = computed(() => sections.value.some((section) => section.sites.length > 0))

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
  mobileOpsId.value = null
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
  mobileOpsId.value = null
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
  mobileOpsId.value = null
  const ok = await ElMessageBox.confirm(`删除站点「${site.name}」？`, '确认删除', {
    type: 'warning',
  }).catch(() => false)
  if (!ok) {
    return
  }
  await run(bookmarkApi.deleteSite(site.id), '站点已删除')
}

async function togglePin(site: BookmarkSite): Promise<void> {
  mobileOpsId.value = null
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

function onDrop(target: BookmarkSite, section: Section, event: DragEvent): void {
  event.preventDefault()
  const fromId = dragId.value
  dragId.value = null
  dragOverId.value = null
  if (!fromId || fromId === target.id || sortBy.value !== 'custom') {
    return
  }
  const ids = section.sites.map((item) => item.id)
  const from = ids.indexOf(fromId)
  const to = ids.indexOf(target.id)
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
      <div class="bm-hero__left">
        <span class="bm-hero__badge"><el-icon><Collection /></el-icon></span>
        <div>
          <h1>网站导航</h1>
          <p>把每天要开的站点收进一页，按分类整理，点开即达</p>
        </div>
      </div>
      <div class="bm-hero__actions">
        <button type="button" class="bm-btn bm-btn--ghost" @click="seedSites">
          <el-icon><MagicStick /></el-icon>导入常用站点
        </button>
        <button type="button" class="bm-btn bm-btn--primary" @click="openSiteDialog()">
          <el-icon><Plus /></el-icon>添加网站
        </button>
      </div>
    </header>

    <ul class="bm-stats">
      <li v-for="item in stats" :key="item.label" class="bm-stat">
        <span class="bm-stat__icon" :style="{ '--tone': item.tone }">
          <el-icon><component :is="item.icon" /></el-icon>
        </span>
        <b>{{ item.value }}</b>
        <span class="bm-stat__label">{{ item.label }}</span>
      </li>
    </ul>

    <div class="bm-body">
      <aside class="bm-rail">
        <p class="bm-rail__label">我的分类</p>
        <button
          type="button"
          class="bm-cat"
          :class="{ 'is-active': activeCategory === 'all' }"
          @click="activeCategory = 'all'"
        >
          <span class="bm-cat__icon is-neutral">🌐</span>
          <span class="bm-cat__name">全部网站</span>
          <span class="bm-cat__count">{{ sites.length }}</span>
        </button>
        <button
          type="button"
          class="bm-cat"
          :class="{ 'is-active': activeCategory === 'pinned' }"
          @click="activeCategory = 'pinned'"
        >
          <span class="bm-cat__icon is-neutral">⭐</span>
          <span class="bm-cat__name">置顶</span>
          <span class="bm-cat__count">{{ pinnedTotal }}</span>
        </button>

        <div class="bm-rail__divider" />

        <button
          v-for="category in categories"
          :key="category.id"
          type="button"
          class="bm-cat"
          :class="{ 'is-active': activeCategory === category.id }"
          :style="{ '--tone': category.color }"
          @click="activeCategory = category.id"
        >
          <span class="bm-cat__icon">{{ categoryEmoji(category.icon) }}</span>
          <span class="bm-cat__name">{{ category.name }}</span>
          <span class="bm-cat__count">{{ category.siteCount }}</span>
          <span class="bm-cat__ops" @click.stop>
            <button type="button" class="bm-cat__op" title="编辑分类" @click.stop="openCategoryDialog(category)">
              <el-icon><EditPen /></el-icon>
            </button>
            <button type="button" class="bm-cat__op" title="删除分类" @click.stop="removeCategory(category)">
              <el-icon><Delete /></el-icon>
            </button>
          </span>
        </button>

        <button
          v-if="uncategorizedTotal"
          type="button"
          class="bm-cat"
          :class="{ 'is-active': activeCategory === 'uncategorized' }"
          @click="activeCategory = 'uncategorized'"
        >
          <span class="bm-cat__icon is-neutral">📥</span>
          <span class="bm-cat__name">未分类</span>
          <span class="bm-cat__count">{{ uncategorizedTotal }}</span>
        </button>

        <button type="button" class="bm-cat bm-cat--add" @click="openCategoryDialog()">
          <el-icon><Plus /></el-icon>新建分类
        </button>

        <div class="bm-rail__tip">
          <span class="bm-rail__tip-icon"><el-icon><MagicStick /></el-icon></span>
          <p>小提示：在「自定义排序」下拖动卡片，可以调整站点顺序。</p>
        </div>
      </aside>

      <div class="bm-main">
        <div class="bm-toolbar">
          <div class="bm-search">
            <el-icon><Search /></el-icon>
            <input v-model="keyword" type="search" placeholder="搜索名称、描述或域名" />
            <span v-if="keyword" class="bm-search__count">
              {{ sections.reduce((sum, section) => sum + section.sites.length, 0) }} 条
            </span>
          </div>
          <div class="bm-toolbar__right">
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
        </div>

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

        <div v-else-if="!hasVisible" class="bm-empty bm-empty--small">
          <span class="bm-empty__icon"><el-icon><Search /></el-icon></span>
          <h2>没有匹配的站点</h2>
          <p>换个关键词，或切换到「全部网站」看看。</p>
        </div>

        <div v-else class="bm-sections">
          <section v-for="section in sections" :key="section.key" class="bm-section">
            <header class="bm-section__head" :style="{ '--tone': section.color }">
              <span class="bm-section__icon">{{ section.icon }}</span>
              <div class="bm-section__text">
                <h2>{{ section.title }}</h2>
                <p>{{ section.hint }}</p>
              </div>
              <span class="bm-section__line" />
            </header>
            <div v-if="section.sites.length" class="bm-cards" :class="{ 'is-list': viewMode === 'list' }">
              <article
                v-for="site in section.sites"
                :key="site.id"
                class="bm-card"
                :class="{
                  'is-dragging': dragId === site.id,
                  'is-dragover': dragOverId === site.id,
                }"
                :style="{ '--accent': accentOf(site) }"
                :draggable="sortBy === 'custom'"
                @click="openSite(site)"
                @dragstart="onDragStart(site, $event)"
                @dragover="onDragOver(site, $event)"
                @drop="onDrop(site, section, $event)"
                @dragend="onDragEnd"
              >
                <span class="bm-card__stripe" />
                <div class="bm-card__logo">
                  <img v-if="site.icon" :src="site.icon" alt="" @error="onIconError" />
                  <span v-else>{{ initialOf(site.name) }}</span>
                </div>
                <div class="bm-card__body">
                  <div class="bm-card__title">
                    <h3>{{ site.name }}</h3>
                    <el-icon v-if="site.pinned" class="bm-card__pin"><StarFilled /></el-icon>
                  </div>
                  <p class="bm-card__desc">{{ site.description || hostOf(site.url) }}</p>
                  <div class="bm-card__meta">
                    <span class="bm-card__host">{{ hostOf(site.url) }}</span>
                    <span>{{ site.clickCount }} 次访问</span>
                  </div>
                </div>
                <button
                  type="button"
                  class="bm-card__more"
                  :class="{ 'is-open': mobileOpsId === site.id }"
                  title="更多操作"
                  @click.stop="toggleMobileOps(site)"
                >
                  <el-icon><MoreFilled /></el-icon>
                </button>
                <div class="bm-card__ops" :class="{ 'is-open': mobileOpsId === site.id }" @click.stop>
                  <button type="button" :title="site.pinned ? '取消置顶' : '置顶'" @click.stop="togglePin(site)">
                    <el-icon><Top /></el-icon>
                  </button>
                  <button type="button" title="编辑" @click.stop="openSiteDialog(site)">
                    <el-icon><EditPen /></el-icon>
                  </button>
                  <button type="button" title="删除" @click.stop="removeSite(site)">
                    <el-icon><Delete /></el-icon>
                  </button>
                </div>
                <el-icon class="bm-card__go"><ArrowRight /></el-icon>
              </article>
            </div>
          </section>
        </div>
      </div>
    </div>

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
  gap: 14px;
  min-height: 100%;
  padding-top: 8px;
}

// ---------- 头部 ----------
.bm-hero {
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

  &::after {
    content: '';
    position: absolute;
    right: -30px;
    top: -60px;
    width: 190px;
    height: 190px;
    border-radius: 50%;
    background: color-mix(in srgb, var(--brand-300, var(--brand-400)) 12%, transparent);
    pointer-events: none;
  }

  &__left {
    display: flex;
    align-items: center;
    gap: 14px;
    min-width: 0;
  }

  &__badge {
    @include flex-center;
    flex: 0 0 auto;
    width: 44px;
    height: 44px;
    border-radius: 14px;
    color: #fff;
    font-size: 21px;
    background: linear-gradient(140deg, var(--brand-400), var(--brand-600));
    box-shadow: 0 8px 18px color-mix(in srgb, var(--brand-500) 28%, transparent);
  }

  h1 {
    font-size: 20px;
    font-weight: 700;
    letter-spacing: 0.2px;
    color: var(--text-primary);
  }

  p {
    margin-top: 3px;
    font-size: 12px;
    color: var(--text-secondary);
  }

  &__actions {
    position: relative;
    z-index: 1;
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
  }
}

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

    &:hover { color: var(--brand-600); border-color: var(--brand-300, var(--brand-400)); background: var(--bg-hover); }
  }
}

// ---------- 统计 ----------
.bm-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.bm-stat {
  @include card($radius-lg);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;

  &__icon {
    @include flex-center;
    width: 32px;
    height: 32px;
    border-radius: 10px;
    color: var(--tone);
    font-size: 16px;
    background: color-mix(in srgb, var(--tone) 12%, transparent);
  }

  b {
    font-family: var(--font-number);
    font-size: 20px;
    font-weight: 700;
    color: var(--text-primary);
    line-height: 1;
  }

  &__label {
    font-size: 11.5px;
    color: var(--text-tertiary);
  }
}

// ---------- 主体 ----------
.bm-body {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 14px;
  flex: 1;
  min-height: 0;
  align-items: start;
}

.bm-rail {
  @include card($radius-xl);
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 16px 12px;

  &__label {
    padding: 0 10px 8px;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 1px;
    color: var(--text-tertiary);
    text-transform: uppercase;
  }

  &__divider {
    height: 1px;
    margin: 10px 8px;
    background: var(--border-color);
  }

  &__tip {
    display: flex;
    gap: 9px;
    margin-top: 14px;
    padding: 12px;
    border: 1px dashed color-mix(in srgb, var(--brand-400) 35%, var(--border-color));
    border-radius: 12px;
    background: color-mix(in srgb, var(--brand-50) 55%, transparent);

    p {
      font-size: 11px;
      line-height: 1.6;
      color: var(--text-secondary);
    }
  }

  &__tip-icon {
    @include flex-center;
    flex: 0 0 auto;
    width: 22px;
    height: 22px;
    border-radius: 8px;
    color: var(--brand-600);
    background: color-mix(in srgb, var(--brand-400) 16%, transparent);
    font-size: 12px;
  }
}

.bm-cat {
  position: relative;
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  min-height: 38px;
  padding: 6px 9px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12.5px;
  text-align: left;
  cursor: pointer;
  transition: background $duration-fast $ease-out, color $duration-fast $ease-out;

  &:hover { background: var(--bg-hover); color: var(--text-primary); }

  &.is-active {
    color: var(--brand-700);
    font-weight: 600;
    background: color-mix(in srgb, var(--brand-100) 72%, transparent);
    border-color: color-mix(in srgb, var(--brand-400) 30%, transparent);
    box-shadow: inset 3px 0 0 var(--tone, var(--brand-500));
  }

  &__icon {
    @include flex-center;
    flex: 0 0 auto;
    width: 26px;
    height: 26px;
    border-radius: 8px;
    font-size: 14px;
    background: color-mix(in srgb, var(--tone, var(--brand-400)) 13%, transparent);

    &.is-neutral { background: var(--bg-subtle); }
  }

  &__name {
    flex: 1;
    min-width: 0;
    @include ellipsis(1);
  }

  &__count {
    flex: 0 0 auto;
    min-width: 22px;
    padding: 1px 6px;
    border-radius: 8px;
    background: var(--bg-subtle);
    color: var(--text-tertiary);
    font-size: 10.5px;
    text-align: center;
  }

  &__ops {
    display: none;
    gap: 2px;
    flex: 0 0 auto;
  }

  &:hover &__ops { display: flex; }
  &:hover &__count { display: none; }

  &__op {
    @include flex-center;
    width: 20px;
    height: 20px;
    border: 0;
    border-radius: 6px;
    background: transparent;
    color: var(--text-tertiary);
    font-size: 12px;
    cursor: pointer;

    &:hover { color: var(--brand-600); background: var(--bg-card); }
  }

  &--add {
    justify-content: center;
    margin-top: 8px;
    border: 1px dashed color-mix(in srgb, var(--brand-400) 38%, var(--border-color));
    color: var(--brand-600);
    font-weight: 600;

    &:hover { background: color-mix(in srgb, var(--brand-50) 60%, transparent); }
  }
}

.bm-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.bm-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;

  &__right {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }
}

.bm-search {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1 1 240px;
  max-width: 340px;
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--border-color);
  border-radius: 11px;
  background: var(--bg-card);
  color: var(--text-tertiary);
  box-shadow: var(--shadow-card);
  transition: border-color $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &:focus-within {
    border-color: color-mix(in srgb, var(--brand-400) 60%, transparent);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--brand-400) 12%, transparent);
  }

  input {
    flex: 1;
    min-width: 0;
    border: 0;
    outline: 0;
    background: transparent;
    color: var(--text-primary);
    font-size: 12.5px;

    &::placeholder { color: var(--text-tertiary); }
  }

  &__count {
    flex: 0 0 auto;
    font-size: 11px;
    color: var(--brand-600);
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
  box-shadow: var(--shadow-card);

  button {
    height: 28px;
    padding: 0 11px;
    border: 0;
    border-radius: 7px;
    background: transparent;
    color: var(--text-tertiary);
    font-size: 11.5px;
    cursor: pointer;
    transition: background $duration-fast $ease-out, color $duration-fast $ease-out;

    &:hover { color: var(--text-primary); }

    &.is-active {
      color: #fff;
      font-weight: 600;
      background: linear-gradient(135deg, var(--brand-400), var(--brand-600));
    }
  }
}

.bm-views button {
  @include flex-center;
  width: 30px;
  padding: 0;
  font-size: 14px;
}

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
  padding: 52px 24px;
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

  h2 { font-size: 16px; font-weight: 700; color: var(--text-primary); }
  p { font-size: 12.5px; color: var(--text-secondary); }

  &__actions {
    display: flex;
    gap: 10px;
    margin-top: 8px;
    flex-wrap: wrap;
    justify-content: center;
  }

  &--small { padding: 36px 24px; }
  &--small &__icon { width: 48px; height: 48px; font-size: 21px; }
}

// ---------- 分组与卡片 ----------
.bm-sections {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.bm-section {
  &__head {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 11px;
  }

  &__icon {
    @include flex-center;
    flex: 0 0 auto;
    width: 30px;
    height: 30px;
    border-radius: 10px;
    font-size: 15px;
    background: color-mix(in srgb, var(--tone) 13%, transparent);
  }

  &__text {
    min-width: 0;

    h2 {
      font-size: 13.5px;
      font-weight: 700;
      color: var(--text-primary);
    }

    p {
      margin-top: 1px;
      font-size: 11px;
      color: var(--text-tertiary);
    }
  }

  &__line {
    flex: 1;
    height: 1px;
    background: linear-gradient(90deg, var(--border-color), transparent);
  }
}

.bm-cards {
  display: grid;
  // auto-fit：条目少于可容纳列数时把空轨道收掉，卡片自动铺满整行
  grid-template-columns: repeat(auto-fit, minmax(228px, 1fr));
  gap: 12px;

  &.is-list { grid-template-columns: 1fr; }
}

.bm-card {
  @include card($radius-lg);
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 11px;
  min-width: 0;
  padding: 14px 13px 12px;
  cursor: pointer;
  overflow: hidden;
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out,
    border-color $duration-fast $ease-out;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: radial-gradient(180px 90px at 0% 0%, color-mix(in srgb, var(--accent) 9%, transparent), transparent 75%);
    opacity: 0;
    transition: opacity $duration-fast $ease-out;
    pointer-events: none;
  }

  &:hover {
    transform: translateY(-3px);
    border-color: color-mix(in srgb, var(--accent) 36%, var(--border-color));
    box-shadow: var(--shadow-card-hover);

    &::before { opacity: 1; }
  }

  &.is-dragging { opacity: 0.42; }
  &.is-dragover { border-color: var(--accent); box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 26%, transparent); }

  &__stripe {
    position: absolute;
    left: 0;
    top: 14px;
    bottom: 14px;
    width: 3px;
    border-radius: 0 3px 3px 0;
    background: var(--accent);
    opacity: 0.75;
  }

  &__logo {
    @include flex-center;
    position: relative;
    flex: 0 0 auto;
    width: 38px;
    height: 38px;
    border-radius: 11px;
    color: #fff;
    font-size: 16px;
    font-weight: 700;
    background: linear-gradient(140deg,
      color-mix(in srgb, var(--accent) 82%, #fff),
      color-mix(in srgb, var(--accent) 88%, #000));
    box-shadow: 0 6px 14px color-mix(in srgb, var(--accent) 26%, transparent);
    overflow: hidden;

    img { width: 100%; height: 100%; object-fit: cover; }
  }

  &__body {
    position: relative;
    flex: 1;
    min-width: 0;
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 5px;

    h3 {
      font-size: 13px;
      font-weight: 700;
      color: var(--text-primary);
      @include ellipsis(1);
    }
  }

  &__pin { flex: 0 0 auto; color: #ff9f43; font-size: 12px; }

  &__desc {
    margin-top: 3px;
    font-size: 11.5px;
    line-height: 1.5;
    color: var(--text-secondary);
    @include ellipsis(2);
  }

  &__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-top: 9px;
    padding-top: 8px;
    border-top: 1px dashed var(--border-color);
    font-size: 10.5px;
    color: var(--text-tertiary);
  }

  &__host {
    max-width: 62%;
    @include ellipsis(1);
  }

  &__ops {
    position: absolute;
    right: 8px;
    top: 8px;
    display: flex;
    gap: 2px;
    padding: 2px;
    border-radius: 9px;
    background: color-mix(in srgb, var(--bg-card) 92%, transparent);
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

  &__more {
    @include flex-center;
    display: none;
    position: absolute;
    right: 9px;
    top: 9px;
    width: 26px;
    height: 26px;
    border: 0;
    border-radius: 8px;
    background: var(--bg-hover);
    color: var(--text-tertiary);
    font-size: 14px;
    cursor: pointer;
    z-index: 3;

    &.is-open {
      color: var(--brand-600);
      background: color-mix(in srgb, var(--brand-500) 14%, var(--bg-card));
    }
  }

  &__go {
    position: absolute;
    right: 12px;
    bottom: 12px;
    color: var(--accent);
    font-size: 13px;
    opacity: 0;
    transform: translateX(-4px);
    transition: opacity $duration-fast $ease-out, transform $duration-fast $ease-out;
  }

  &:hover &__go { opacity: 0.8; transform: translateX(0); }
}

.bm-cards.is-list .bm-card {
  align-items: center;

  &__meta { margin-top: 4px; padding-top: 0; border-top: 0; justify-content: flex-start; gap: 20px; }
  &__desc { @include ellipsis(1); }
  &__go { display: none; }
}

// ---------- 弹窗 ----------
.bm-dialog {
  :deep(.el-dialog) { border-radius: 18px; }
  :deep(.el-dialog__title) { font-weight: 700; }
  :deep(.el-form-item__label) { font-size: 12px; font-weight: 600; color: var(--text-secondary); }
  :deep(.el-dialog__footer) { display: flex; justify-content: flex-end; gap: 10px; }
}

.bm-form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.bm-select { width: 100%; }

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

    &:hover { transform: scale(1.1); }
    &.is-active { border-color: var(--text-primary); box-shadow: 0 0 0 2px var(--bg-card) inset; }
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

    &:hover { background: var(--bg-hover); }
    &.is-active { border-color: var(--brand-500); background: color-mix(in srgb, var(--brand-100) 70%, transparent); }
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

    &.is-active { color: #ff9f43; border-color: color-mix(in srgb, #ff9f43 45%, transparent); background: color-mix(in srgb, #ff9f43 10%, transparent); }
  }
}

@include respond-below(md) {
  .bm-body { grid-template-columns: 1fr; }
  .bm-rail {
    position: static;
    flex-direction: row;
    align-items: center;
    gap: 8px;
    padding: 10px;
    overflow-x: auto;
    @include scrollbar(4px);

    &__label, &__divider, &__tip, & .bm-cat--add { display: none; }
  }
  .bm-cat {
    flex: 0 0 auto;
    width: auto;
    min-width: max-content;
    padding: 6px 11px;

    &.is-active { box-shadow: inset 0 -3px 0 var(--tone, var(--brand-500)); }
    &__ops, &:hover &__count { display: none; }
    &__count { display: inline-block; }
  }
}

@include respond-below(sm) {
  .bm-hero { flex-direction: column; align-items: flex-start; padding: 16px; }
  .bm-hero__actions { width: 100%; }
  .bm-hero__actions .bm-btn { flex: 1; }
  .bm-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
  .bm-stat { padding: 10px 12px; }
  .bm-toolbar { gap: 8px; }
  .bm-search { max-width: none; flex-basis: 100%; }
  .bm-toolbar__right { width: 100%; justify-content: space-between; }
  .bm-sort button { padding: 0 8px; font-size: 11px; }
  // 窄屏改单列，卡片铺满一行，文字不再被操作按钮挤压
  .bm-cards { grid-template-columns: minmax(0, 1fr); gap: 9px; }
  .bm-card { padding: 13px 12px 11px; gap: 11px; }
  .bm-card__logo { width: 36px; height: 36px; font-size: 15px; }
  .bm-card__title { padding-right: 34px; }
  .bm-card__desc { @include ellipsis(2); }
  .bm-card__go { display: none; }
  .bm-card__more { display: flex; }

  // 触屏没有 hover：点「更多」才展开操作浮层，其余时间完全不占位
  .bm-card__ops,
  .bm-card:hover .bm-card__ops {
    position: absolute;
    right: 9px;
    top: 40px;
    z-index: 4;
    opacity: 0;
    pointer-events: none;
    background: var(--bg-card);
    box-shadow: var(--shadow-card-hover);
  }
  .bm-card__ops.is-open,
  .bm-card:hover .bm-card__ops.is-open {
    opacity: 1;
    pointer-events: auto;
  }
  .bm-card__ops button { width: 27px; height: 27px; font-size: 13px; }
  .bm-form-row { grid-template-columns: 1fr; gap: 0; }
}
</style>
