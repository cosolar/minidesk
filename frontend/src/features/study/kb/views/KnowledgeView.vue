<script setup lang="ts">
import { computed, ref } from 'vue'
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
  Share,
} from '@element-plus/icons-vue'
import { kbApi, type KbBook } from '../api'

type FilterKey = 'all' | 'shared' | 'private'
type SortKey = 'updated' | 'docs'
type ViewMode = 'grid' | 'list'

const queryClient = useQueryClient()

const filter = ref<FilterKey>('all')
const sortBy = ref<SortKey>('updated')
const viewMode = ref<ViewMode>('grid')
const keyword = ref('')
const brokenCovers = ref<Set<string>>(new Set())

const tabs: { key: FilterKey; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'shared', label: '分享' },
  { key: 'private', label: '加密' },
]

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

function isShared(book: KbBook): boolean {
  return book.scope === 'public' || book.visibility === 'public'
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

const sharedCount = computed(() => allBooks.value.filter(isShared).length)
const privateCount = computed(() => allBooks.value.length - sharedCount.value)
const docTotal = computed(() =>
  allBooks.value.reduce((sum, book) => sum + book.docCount, 0),
)

const visibleBooks = computed<KbBook[]>(() => {
  const needle = keyword.value.trim().toLowerCase()
  const list = allBooks.value.filter((book) => {
    if (filter.value === 'shared' && !isShared(book)) {
      return false
    }
    if (filter.value === 'private' && isShared(book)) {
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
        <li class="kb-stat">
          <el-icon><Collection /></el-icon>
          <b>{{ allBooks.length }}</b>
          <span>知识库</span>
        </li>
        <li class="kb-stat">
          <el-icon><Share /></el-icon>
          <b>{{ sharedCount }}</b>
          <span>分享</span>
        </li>
        <li class="kb-stat">
          <el-icon><Lock /></el-icon>
          <b>{{ privateCount }}</b>
          <span>加密</span>
        </li>
        <li class="kb-stat">
          <el-icon><Document /></el-icon>
          <b>{{ docTotal }}</b>
          <span>文档</span>
        </li>
      </ul>
    </header>

    <div class="kb-toolbar">
      <div class="kb-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          class="kb-tabs__item"
          :class="{ 'is-active': filter === tab.key }"
          @click="filter = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>

      <el-input
        v-model="keyword"
        class="kb-search"
        size="small"
        placeholder="搜索知识库 / 标签"
        clearable
        :prefix-icon="Search"
      />

      <div class="kb-toolbar__right">
        <el-select v-model="sortBy" size="small" class="kb-sort">
          <el-option label="最近更新" value="updated" />
          <el-option label="文档最多" value="docs" />
        </el-select>
        <div class="kb-views">
          <button
            type="button"
            :class="{ 'is-active': viewMode === 'grid' }"
            title="网格视图"
            @click="viewMode = 'grid'"
          >
            <el-icon><Grid /></el-icon>
          </button>
          <button
            type="button"
            :class="{ 'is-active': viewMode === 'list' }"
            title="列表视图"
            @click="viewMode = 'list'"
          >
            <el-icon><List /></el-icon>
          </button>
        </div>
        <el-button size="small" :icon="Refresh" @click="refresh">刷新</el-button>
        <el-button size="small" :icon="Link" @click="openSite">打开站点</el-button>
      </div>
    </div>

    <p v-if="status && !connected" class="kb-hint">
      {{ status.message || '知识库集成尚未启用' }}，当前只展示站点公开库；
      <RouterLink to="/settings">去设置访问令牌</RouterLink>
      后可看到自己的全部知识库。
    </p>

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
          <span class="kb-card__badge" :class="isShared(book) ? 'is-shared' : 'is-private'">
            <el-icon>
              <Share v-if="isShared(book)" />
              <Lock v-else />
            </el-icon>
            {{ isShared(book) ? '分享' : '加密' }}
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
      <p v-else-if="filter === 'private' && !connected">
        配置知识库访问令牌后，这里才会显示未公开的加密库。
      </p>
      <p v-else-if="keyword">没有匹配「{{ keyword }}」的知识库。</p>
      <p v-else>还没有可展示的知识库。</p>
      <div class="kb-empty__actions">
        <el-button size="small" :icon="Refresh" @click="refresh">重新加载</el-button>
        <el-button size="small" :icon="Link" @click="openSite">打开知识库站点</el-button>
      </div>
    </div>
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
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--bg-card) 70%, transparent);
  border: 1px solid color-mix(in srgb, var(--border-color) 70%, var(--bg-card));
  font-size: 11.5px;
  color: var(--text-tertiary);

  .el-icon {
    color: var(--brand-500);
    font-size: 13px;
  }

  b {
    font-family: var(--font-number);
    font-size: 14px;
    color: var(--text-primary);
    font-variant-numeric: tabular-nums;
  }
}

.kb-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 10px 14px;
  @include card($radius-lg);
}

.kb-tabs {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: 10px;
  background: var(--bg-subtle);
}

.kb-tabs__item {
  padding: 5px 16px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12.5px;
  cursor: pointer;
  transition: all $duration-fast $ease-out;

  &.is-active {
    background: var(--bg-card);
    color: var(--brand-700);
    font-weight: 600;
    box-shadow: var(--shadow-card);
  }
}

.kb-search {
  width: 200px;
}

.kb-toolbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.kb-sort {
  width: 118px;
}

.kb-views {
  display: flex;
  gap: 2px;
  padding: 2px;
  border-radius: 8px;
  background: var(--bg-subtle);

  button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 24px;
    border: none;
    border-radius: 6px;
    background: transparent;
    color: var(--text-tertiary);
    cursor: pointer;

    &.is-active {
      background: var(--bg-card);
      color: var(--brand-600);
      box-shadow: var(--shadow-card);
    }
  }
}

.kb-hint {
  margin: -4px 2px 0;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--text-tertiary);

  a {
    color: var(--brand-600);
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
  gap: 4px;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 10.5px;
  font-weight: 600;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);

  &.is-shared {
    background: color-mix(in srgb, var(--bg-card) 82%, transparent);
    color: var(--brand-700);
  }

  &.is-private {
    background: color-mix(in srgb, var(--c-alert) 16%, var(--bg-card));
    color: var(--c-alert);
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
  margin-top: 4px;
}

@include respond-below(sm) {
  .kb-search,
  .kb-sort {
    width: 100%;
  }

  .kb-toolbar__right {
    width: 100%;
    margin-left: 0;
    flex-wrap: wrap;
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
