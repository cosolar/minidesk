<script setup lang="ts">
import { computed, ref, type Component } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  Collection,
  Document,
  Grid,
  Link,
  List,
  Lock,
  Refresh,
  Search,
  Sort,
  Unlock,
} from '@element-plus/icons-vue'
import { kbApi, type KbBook } from '../api'

/** 知识库只分两类：站点公开库 / 需要授权的加密库 */
type FilterKey = 'public' | 'private'
type SortKey = 'updated' | 'docs'
type ViewMode = 'grid' | 'list'
type Category = {
  key: FilterKey
  label: string
  hint: string
  icon: Component
  tone: 'public' | 'private'
  count: number
}

const queryClient = useQueryClient()

const filter = ref<FilterKey>('public')
const sortBy = ref<SortKey>('updated')
const viewMode = ref<ViewMode>('grid')
const keyword = ref('')
const brokenCovers = ref<Set<string>>(new Set())

const statusQuery = useQuery({
  queryKey: ['kb', 'status'],
  queryFn: () => kbApi.status(),
  staleTime: 60_000,
})

const status = computed(() => statusQuery.data.value)
const connected = computed(() =>
  Boolean(status.value?.configured && status.value?.connected),
)

const publicQuery = useQuery({
  queryKey: ['kb', 'books', 'public'],
  queryFn: () => kbApi.books({ scope: 'public', size: 100 }),
  staleTime: 60_000,
})

/** 配置了令牌才拉「我的知识库」，否则只会拿到 60060 报错 */
const mineQuery = useQuery({
  queryKey: ['kb', 'books', 'mine'],
  queryFn: () => kbApi.books({ scope: 'mine', size: 100 }),
  enabled: computed(() => connected.value),
  staleTime: 60_000,
  retry: false,
})

function bookKey(book: KbBook): string {
  return `${book.orgSlug}/${book.slug}`
}

/**
 * 公开 / 加密以后端给出的发布态为准：门户匿名拉回来的库 scope 都是 public，
 * 不能用它判断；visibility 也与是否加密正交（org 库可通过公开分享发布）。
 */
function isPublic(book: KbBook): boolean {
  if (typeof book.encrypted === 'boolean') {
    return !book.encrypted
  }
  // 兼容旧接口：只有明确 public 可见性才算公开，其余按加密处理
  return book.visibility === 'public'
}

/** 我的库（含私有）优先，匿名公开库补齐其它组织，按「组织 + 库名」去重 */
const allBooks = computed<KbBook[]>(() => {
  const merged = new Map<string, KbBook>()
  for (const book of mineQuery.data.value?.list ?? []) {
    merged.set(bookKey(book), book)
  }
  for (const book of publicQuery.data.value?.list ?? []) {
    const key = bookKey(book)
    if (!merged.has(key)) {
      merged.set(key, book)
    }
  }
  return [...merged.values()]
})

const publicCount = computed(() => allBooks.value.filter(isPublic).length)
const privateCount = computed(() => allBooks.value.length - publicCount.value)
const docTotal = computed(() =>
  allBooks.value.reduce((sum, book) => sum + book.docCount, 0),
)

/** 两类互斥：公开库走匿名门户，加密库只有配置令牌后才拉得到 */
const categories = computed<Category[]>(() => [
  {
    key: 'public',
    label: '公开',
    hint: '站点公开发布，任何人都可以访问',
    icon: Unlock,
    tone: 'public',
    count: publicCount.value,
  },
  {
    key: 'private',
    label: '加密',
    hint: '已加密或需要授权，打开时可能需要访问密码',
    icon: Lock,
    tone: 'private',
    count: privateCount.value,
  },
])

const activeCategory = computed(
  () => categories.value.find((item) => item.key === filter.value) ?? categories.value[0],
)

const visibleBooks = computed<KbBook[]>(() => {
  const needle = keyword.value.trim().toLowerCase()
  const list = allBooks.value.filter((book) => {
    if (filter.value === 'public' && !isPublic(book)) {
      return false
    }
    if (filter.value === 'private' && isPublic(book)) {
      return false
    }
    if (!needle) {
      return true
    }
    return [book.name, book.description, ...(book.tags ?? [])].some((text) =>
      text.toLowerCase().includes(needle),
    )
  })
  return list.sort((left, right) =>
    sortBy.value === 'docs'
      ? right.docCount - left.docCount
      : right.updatedText.localeCompare(left.updatedText),
  )
})

/* 已有数据就不再切回骨架屏，避免 status 后到导致列表闪烁 */
const isLoading = computed(
  () =>
    allBooks.value.length === 0 &&
    (publicQuery.isLoading.value || mineQuery.isLoading.value),
)
const isFailed = computed(
  () => publicQuery.isError.value && allBooks.value.length === 0,
)

function markCoverBroken(id: string): void {
  brokenCovers.value = new Set(brokenCovers.value).add(id)
}

function refresh(): void {
  queryClient.invalidateQueries({ queryKey: ['kb'] })
}

function openSite(): void {
  const siteUrl = status.value?.siteUrl
  if (!siteUrl) {
    ElMessage.warning('还没有配置知识库站点地址')
    return
  }
  window.open(siteUrl, '_blank', 'noopener')
}
</script>

<template>
  <div class="kb-page">
    <header class="page-head">
      <div class="page-head__main">
        <h1>全部知识库</h1>
        <p class="page-head__sub">
          这里只负责引导，点击卡片在新标签页打开知识库站点；文档的读写都在第三方完成。
        </p>
      </div>
      <ul class="kb-stats">
        <li class="kb-stat is-total">
          <span class="kb-stat__icon"><el-icon><Collection /></el-icon></span>
          <span class="kb-stat__text"><b>{{ allBooks.length }}</b><span>知识库</span></span>
        </li>
        <li class="kb-stat is-public">
          <span class="kb-stat__icon"><el-icon><Unlock /></el-icon></span>
          <span class="kb-stat__text"><b>{{ publicCount }}</b><span>公开</span></span>
        </li>
        <li class="kb-stat is-private">
          <span class="kb-stat__icon"><el-icon><Lock /></el-icon></span>
          <span class="kb-stat__text"><b>{{ privateCount }}</b><span>加密</span></span>
        </li>
        <li class="kb-stat is-docs">
          <span class="kb-stat__icon"><el-icon><Document /></el-icon></span>
          <span class="kb-stat__text"><b>{{ docTotal }}</b><span>文档</span></span>
        </li>
      </ul>
    </header>

    <div class="kb-toolbar">
      <div class="kb-toolbar__left">
        <div class="kb-filters" role="tablist" aria-label="知识库分类">
          <button
            v-for="cat in categories"
            :key="cat.key"
            type="button"
            role="tab"
            class="kb-filter"
            :class="[`is-${cat.tone}`, { 'is-active': filter === cat.key }]"
            :aria-selected="filter === cat.key"
            @click="filter = cat.key"
          >
            <span class="kb-filter__icon">
              <el-icon><component :is="cat.icon" /></el-icon>
            </span>
            <span class="kb-filter__label">{{ cat.label }}</span>
            <span class="kb-filter__count">{{ cat.count }}</span>
          </button>
        </div>

        <div class="kb-search">
          <el-input
            v-model="keyword"
            size="small"
            placeholder="搜索知识库 / 标签"
            clearable
            :prefix-icon="Search"
          />
          <span v-if="keyword" class="kb-search__count">{{ visibleBooks.length }}</span>
        </div>
      </div>

      <div class="kb-toolbar__right">
        <div class="kb-sort">
          <el-icon class="kb-sort__icon"><Sort /></el-icon>
          <el-select v-model="sortBy" size="small" class="kb-sort__select">
            <el-option label="最近更新" value="updated" />
            <el-option label="文档最多" value="docs" />
          </el-select>
        </div>

        <div class="kb-views" role="group" aria-label="视图切换">
          <button
            type="button"
            :class="{ 'is-active': viewMode === 'grid' }"
            title="网格视图"
            aria-label="网格视图"
            @click="viewMode = 'grid'"
          >
            <el-icon><Grid /></el-icon>
          </button>
          <button
            type="button"
            :class="{ 'is-active': viewMode === 'list' }"
            title="列表视图"
            aria-label="列表视图"
            @click="viewMode = 'list'"
          >
            <el-icon><List /></el-icon>
          </button>
        </div>

        <button type="button" class="kb-action" title="刷新知识库" @click="refresh">
          <el-icon class="kb-action__spin"><Refresh /></el-icon>
          <span>刷新</span>
        </button>
        <button type="button" class="kb-action kb-action--primary" @click="openSite">
          <el-icon><Link /></el-icon>
          <span>打开站点</span>
          <el-icon class="kb-action__arrow"><ArrowRight /></el-icon>
        </button>
      </div>
    </div>

    <p v-if="status && !connected" class="kb-hint">
      <span class="kb-hint__dot" />
      {{ status.message || '知识库集成尚未启用' }}，当前展示站点门户发布的知识库；
      <RouterLink to="/settings">去设置访问令牌</RouterLink>
      后可看到自己未发布的知识库。
    </p>

    <div class="kb-section">
      <span class="kb-section__icon" :class="`is-${activeCategory.tone}`">
        <el-icon><component :is="activeCategory.icon" /></el-icon>
      </span>
      <span class="kb-section__text">
        <strong>{{ activeCategory.label }}知识库</strong>
        <small>{{ activeCategory.hint }}</small>
      </span>
      <span class="kb-section__count">{{ visibleBooks.length }} 个</span>
    </div>

    <div v-if="isLoading" class="kb-wall" :class="`is-${viewMode}`">
      <div v-for="index in 5" :key="index" class="kb-skeleton">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="image" class="kb-skeleton__cover" />
            <div class="kb-skeleton__body">
              <el-skeleton-item variant="h3" style="width: 62%" />
              <el-skeleton-item variant="text" style="width: 90%" />
              <el-skeleton-item variant="text" style="width: 48%" />
            </div>
          </template>
        </el-skeleton>
      </div>
    </div>

    <div v-else-if="visibleBooks.length" class="kb-wall" :class="`is-${viewMode}`">
      <a
        v-for="book in visibleBooks"
        :key="bookKey(book)"
        class="kb-card"
        :href="book.url"
        target="_blank"
        rel="noopener noreferrer"
      >
        <div class="kb-card__cover">
          <img
            v-if="book.coverSrc && !brokenCovers.has(book.id)"
            :src="book.coverSrc"
            :alt="book.name"
            loading="lazy"
            @error="markCoverBroken(book.id)"
          />
          <span v-else class="kb-card__cover-fallback">{{ book.name.slice(0, 1) }}</span>
          <span class="kb-card__badge" :class="isPublic(book) ? 'is-public' : 'is-private'">
            <el-icon>
              <Unlock v-if="isPublic(book)" />
              <Lock v-else />
            </el-icon>
            {{ isPublic(book) ? '公开' : '加密' }}
          </span>
        </div>

        <div class="kb-card__body">
          <h3 class="kb-card__title">{{ book.name }}</h3>
          <p class="kb-card__desc">{{ book.description || '暂无描述' }}</p>
          <div v-if="book.tags?.length" class="kb-card__tags">
            <span v-for="tag in book.tags.slice(0, 3)" :key="tag" class="kb-card__tag">
              {{ tag }}
            </span>
          </div>
        </div>

        <div class="kb-card__foot">
          <span class="kb-card__meta">
            <el-icon><Document /></el-icon>
            {{ book.docCount }} 篇
          </span>
          <span class="kb-card__meta kb-card__meta--time">{{ book.updatedText }}</span>
          <span class="kb-card__go">
            <el-icon><ArrowRight /></el-icon>
          </span>
        </div>
      </a>
    </div>

    <div v-else class="kb-empty">
      <el-icon class="kb-empty__icon"><Collection /></el-icon>
      <p v-if="isFailed">知识库暂时无法访问，请稍后重试。</p>
      <p v-else-if="filter === 'private'">
        当前没有加密知识库；配置访问令牌后可看到更多未发布的库。
      </p>
      <p v-else-if="keyword">没有匹配「{{ keyword }}」的知识库。</p>
      <p v-else>还没有可展示的知识库。</p>
      <div class="kb-empty__actions">
        <button type="button" class="kb-action" @click="refresh">
          <el-icon class="kb-action__spin"><Refresh /></el-icon>
          <span>重新加载</span>
        </button>
        <button type="button" class="kb-action kb-action--primary" @click="openSite">
          <el-icon><Link /></el-icon>
          <span>打开知识库站点</span>
          <el-icon class="kb-action__arrow"><ArrowRight /></el-icon>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.kb-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 8px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;

  h1 {
    font-size: 22px;
    font-weight: 700;
  }
}

.page-head__sub {
  max-width: 520px;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.kb-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  list-style: none;
}

.kb-stat {
  --tone: var(--brand-500);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px 6px 7px;
  border: 1px solid color-mix(in srgb, var(--border-color) 78%, var(--bg-card));
  border-radius: 12px;
  background: color-mix(in srgb, var(--bg-card) 82%, transparent);
  box-shadow: 0 2px 10px color-mix(in srgb, var(--brand-500) 4%, transparent);

  &.is-private {
    --tone: var(--c-life);
  }

  &.is-docs {
    --tone: var(--c-study);
  }

  &__icon {
    @include flex-center;
    width: 26px;
    height: 26px;
    border-radius: 8px;
    background: color-mix(in srgb, var(--tone) 13%, transparent);
    color: var(--tone);
    font-size: 13px;
  }

  &__text {
    display: flex;
    align-items: baseline;
    gap: 5px;

    b {
      font-family: var(--font-number);
      font-size: 15px;
      font-weight: 700;
      color: var(--text-primary);
      font-variant-numeric: tabular-nums;
    }

    span {
      font-size: 11px;
      color: var(--text-tertiary);
    }
  }
}

.kb-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 11px 13px;
  @include card($radius-lg);
}

.kb-toolbar__left,
.kb-toolbar__right {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.kb-toolbar__left {
  flex: 1 1 auto;
}

.kb-toolbar__right {
  flex: 0 0 auto;
  margin-left: auto;
}

.kb-filters {
  display: inline-flex;
  gap: 3px;
  flex: 0 0 auto;
  padding: 4px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--bg-subtle);
}

.kb-filter {
  --tone: var(--brand-500);
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 9px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition:
    background $duration-fast $ease-out,
    color $duration-fast $ease-out,
    box-shadow $duration-fast $ease-out;

  &.is-private {
    --tone: var(--c-life);
  }

  &:hover {
    color: var(--text-primary);
  }

  &.is-active {
    background: var(--bg-card);
    color: var(--tone);
    font-weight: 650;
    box-shadow: var(--shadow-card);

    .kb-filter__icon {
      background: color-mix(in srgb, var(--tone) 18%, transparent);
    }

    .kb-filter__count {
      background: color-mix(in srgb, var(--tone) 14%, transparent);
      color: var(--tone);
    }

    &::after {
      content: '';
      position: absolute;
      bottom: 2px;
      left: 50%;
      width: 22px;
      height: 2px;
      border-radius: 2px;
      background: var(--tone);
      transform: translateX(-50%);
      opacity: 0.75;
    }
  }

  &__icon {
    @include flex-center;
    width: 19px;
    height: 19px;
    border-radius: 6px;
    background: color-mix(in srgb, var(--tone) 10%, transparent);
    color: var(--tone);
    font-size: 11px;
  }

  &__label {
    font-size: 12.5px;
    white-space: nowrap;
  }

  &__count {
    min-width: 20px;
    padding: 1px 5px;
    border-radius: 6px;
    background: var(--bg-card);
    color: var(--text-tertiary);
    font-family: var(--font-number);
    font-size: 10.5px;
    text-align: center;
  }
}

.kb-search {
  position: relative;
  flex: 1 1 210px;
  min-width: 150px;
  max-width: 280px;

  :deep(.el-input__wrapper) {
    border-radius: 10px;
    background: var(--bg-card);
    box-shadow: 0 0 0 1px var(--border-color) inset;
    transition: box-shadow $duration-fast $ease-out;

    &.is-focus {
      box-shadow: 0 0 0 1px color-mix(in srgb, var(--brand-400) 60%, var(--border-color)) inset;
    }
  }

  :deep(.el-input__inner) {
    padding-right: 38px;
  }

  &__count {
    position: absolute;
    top: 50%;
    right: 8px;
    transform: translateY(-50%);
    padding: 1px 6px;
    border-radius: 6px;
    background: var(--brand-50);
    color: var(--brand-600);
    font-family: var(--font-number);
    font-size: 10px;
    pointer-events: none;
  }
}

.kb-sort {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex: 0 0 auto;
  padding-left: 8px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-card);

  &__icon {
    color: var(--text-tertiary);
    font-size: 13px;
  }

  &__select {
    width: 98px;
  }

  :deep(.el-select__wrapper) {
    min-height: 28px;
    padding: 0 6px;
    background: transparent;
    box-shadow: none;
    font-size: 12px;
  }
}

.kb-views {
  display: inline-flex;
  gap: 2px;
  flex: 0 0 auto;
  padding: 3px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-subtle);

  button {
    @include flex-center;
    width: 27px;
    height: 25px;
    border: none;
    border-radius: 7px;
    background: transparent;
    color: var(--text-tertiary);
    font-size: 13px;
    cursor: pointer;
    transition: all $duration-fast $ease-out;

    &:hover {
      color: var(--brand-600);
    }

    &.is-active {
      background: linear-gradient(140deg, var(--brand-500), var(--brand-700));
      color: var(--text-inverse);
      box-shadow: 0 3px 10px color-mix(in srgb, var(--brand-500) 26%, transparent);
    }
  }
}

.kb-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex: 0 0 auto;
  height: 31px;
  padding: 0 11px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition:
    transform $duration-fast $ease-out,
    border-color $duration-fast $ease-out,
    color $duration-fast $ease-out,
    box-shadow $duration-fast $ease-out;

  .el-icon {
    font-size: 13px;
  }

  &:hover {
    transform: translateY(-1px);
    border-color: color-mix(in srgb, var(--brand-400) 55%, var(--border-color));
    color: var(--brand-700);
    box-shadow: var(--shadow-card);
  }

  &:active {
    transform: translateY(0);
  }

  &__spin,
  &__arrow {
    transition: transform $duration-base $ease-out;
  }

  &:hover &__spin {
    transform: rotate(90deg);
  }

  &:hover &__arrow {
    transform: translateX(2px);
  }

  &--primary {
    border-color: transparent;
    background: linear-gradient(135deg, var(--brand-500), var(--brand-700));
    color: var(--text-inverse);
    font-weight: 600;
    box-shadow: 0 6px 16px color-mix(in srgb, var(--brand-500) 28%, transparent);

    &:hover {
      border-color: transparent;
      color: var(--text-inverse);
      transform: translateY(-2px);
      box-shadow: 0 10px 22px color-mix(in srgb, var(--brand-500) 38%, transparent);
    }
  }
}

.kb-hint {
  position: relative;
  display: block;
  margin: -4px 2px 0;
  padding: 8px 11px 8px 25px;
  border: 1px dashed color-mix(in srgb, var(--brand-400) 30%, var(--border-color));
  border-radius: 10px;
  background: color-mix(in srgb, var(--brand-50) 45%, transparent);
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--text-secondary);

  &__dot {
    position: absolute;
    top: 14px;
    left: 11px;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--brand-500);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--brand-400) 16%, transparent);
  }

  a {
    color: var(--brand-600);
    font-weight: 600;
    white-space: nowrap;
  }
}

.kb-section {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 2px 2px -2px;

  &__icon {
    --tone: var(--brand-500);
    @include flex-center;
    width: 30px;
    height: 30px;
    border-radius: 10px;
    background: color-mix(in srgb, var(--tone) 13%, transparent);
    color: var(--tone);
    font-size: 14px;

    &.is-private {
      --tone: var(--c-life);
    }
  }

  &__text {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;

    strong {
      font-size: 13px;
      font-weight: 650;
      color: var(--text-primary);
    }

    small {
      font-size: 10.5px;
      color: var(--text-tertiary);
    }
  }

  &__count {
    margin-left: auto;
    padding: 3px 9px;
    border-radius: 999px;
    background: var(--bg-subtle);
    color: var(--text-tertiary);
    font-family: var(--font-number);
    font-size: 11px;
  }
}

.kb-wall {
  display: grid;
  gap: 16px;

  &.is-grid {
    grid-template-columns: repeat(auto-fill, minmax(212px, 1fr));
  }

  &.is-list {
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
  }
}

.kb-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  @include card($radius-xl);
  color: inherit;
  text-decoration: none;
  transition:
    transform $duration-base $ease-out,
    box-shadow $duration-base $ease-out,
    border-color $duration-base $ease-out;

  &:hover {
    transform: translateY(-3px);
    border-color: color-mix(in srgb, var(--brand-400) 46%, var(--border-color));
    box-shadow: var(--shadow-card-hover);
  }
}

.kb-card__cover {
  position: relative;
  height: 128px;
  background: linear-gradient(
    140deg,
    color-mix(in srgb, var(--brand-400) 24%, var(--bg-subtle)),
    var(--bg-subtle)
  );

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.kb-card__cover-fallback {
  @include flex-center;
  height: 100%;
  font-family: var(--font-display);
  font-size: 34px;
  font-weight: 700;
  color: color-mix(in srgb, var(--brand-600) 62%, var(--text-tertiary));
}

.kb-card__badge {
  position: absolute;
  left: 10px;
  bottom: 10px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border: 1px solid color-mix(in srgb, var(--bg-card) 60%, transparent);
  border-radius: 999px;
  font-size: 10.5px;
  font-weight: 600;
  backdrop-filter: blur(10px) saturate(1.2);
  -webkit-backdrop-filter: blur(10px) saturate(1.2);
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.08);

  .el-icon {
    font-size: 11px;
  }

  &.is-public {
    background: color-mix(in srgb, var(--bg-card) 88%, transparent);
    color: var(--brand-700);
  }

  &.is-private {
    background: color-mix(in srgb, var(--c-life) 20%, var(--bg-card));
    border-color: color-mix(in srgb, var(--c-life) 32%, transparent);
    color: color-mix(in srgb, var(--c-life) 80%, var(--text-primary));
  }
}

.kb-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px 8px;
}

.kb-card__title {
  @include ellipsis(1);
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-primary);
}

.kb-card__desc {
  @include ellipsis(2);
  min-height: 34px;
  font-size: 11.5px;
  line-height: 1.55;
  color: var(--text-secondary);
}

.kb-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.kb-card__tag {
  padding: 2px 7px;
  border-radius: 6px;
  background: var(--brand-50);
  font-size: 10.5px;
  color: var(--brand-700);
}

.kb-card__foot {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px 12px;
}

.kb-card__meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--text-tertiary);
  white-space: nowrap;

  &--time {
    margin-left: auto;
    font-variant-numeric: tabular-nums;
  }
}

.kb-card__go {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--brand-50);
  color: var(--brand-600);
  transition: all $duration-fast $ease-out;

  .kb-card:hover & {
    background: var(--brand-500);
    color: var(--text-inverse);
  }
}

/* 列表视图：封面缩成方形缩略图，其余内容横向排布 */
.kb-wall.is-list .kb-card {
  flex-direction: row;
  align-items: center;
  gap: 14px;
  padding: 10px 14px 10px 10px;

  .kb-card__cover {
    flex: none;
    width: 92px;
    height: 68px;
    border-radius: 10px;
    overflow: hidden;
  }

  .kb-card__cover-fallback {
    font-size: 24px;
  }

  .kb-card__badge {
    left: 6px;
    bottom: 6px;
    padding: 1px 7px;
  }

  .kb-card__body {
    padding: 0;
    gap: 4px;
  }

  .kb-card__desc {
    min-height: 0;
    -webkit-line-clamp: 1;
  }

  .kb-card__foot {
    flex: none;
    padding: 0;
  }

  .kb-card__meta--time {
    margin-left: 0;
  }
}

.kb-skeleton {
  padding: 0 0 14px;
  overflow: hidden;
  @include card($radius-xl);
}

.kb-skeleton__cover {
  width: 100%;
  height: 128px;
}

.kb-skeleton__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px 0;
}

.kb-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 320px;
  padding: 32px 16px;
  text-align: center;
  font-size: 12.5px;
  color: var(--text-tertiary);
  @include card($radius-xl);
}

.kb-empty__icon {
  font-size: 30px;
  color: var(--brand-400);
  opacity: 0.6;
}

.kb-empty__actions {
  display: flex;
  gap: 8px;
  margin-top: 6px;

  .kb-action {
    height: 33px;
  }
}

@include respond-below(sm) {
  .kb-toolbar {
    align-items: stretch;
    gap: 9px;
    padding: 10px;
  }

  .kb-toolbar__left {
    flex-wrap: wrap;
    width: 100%;
    gap: 8px;
  }

  .kb-filters {
    display: flex;
    width: 100%;

    .kb-filter {
      flex: 1;
      justify-content: center;
    }
  }

  .kb-search {
    flex: 1 1 100%;
    max-width: none;
  }

  .kb-toolbar__right {
    width: 100%;
    margin-left: 0;
    flex-wrap: wrap;
    gap: 8px;
  }

  .kb-sort {
    flex: 1 1 auto;
  }

  .kb-action--primary {
    flex: 1 1 100%;
  }

  .kb-stats {
    gap: 6px;
  }

  .kb-stat {
    padding: 5px 9px 5px 6px;
  }

  .kb-stat__text b {
    font-size: 13px;
  }

  .kb-wall.is-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 12px;
  }

  .kb-card__cover {
    height: 96px;
  }

  /* 窄卡片一排放不下"文档数 + 更新时间 + 箭头"，更新时间让位 */
  .kb-wall.is-grid .kb-card__meta--time {
    display: none;
  }

  .kb-wall.is-grid .kb-card__go {
    margin-left: auto;
  }
}
</style>
