<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { useDebounceFn } from '@vueuse/core'
import { Search } from '@element-plus/icons-vue'
import { searchApi, type SearchHit } from '../api'

const route = useRoute()
const router = useRouter()

const keyword = ref(String(route.query.q ?? ''))
const submitted = ref(keyword.value)
const inputRef = ref<HTMLInputElement>()

const runSearch = useDebounceFn((value: string) => {
  submitted.value = value.trim()
}, 280)

watch(keyword, (value) => {
  if (!value.trim()) {
    submitted.value = ''
  }
  runSearch(value)
})

const query = useQuery({
  queryKey: ['search', submitted],
  queryFn: () => searchApi.query(submitted.value),
  enabled: computed(() => submitted.value.length > 0),
  staleTime: 30_000,
})

const result = computed(() => query.data.value)
const groups = computed(() => (result.value?.groups ?? []).filter((group) => group.hits.length > 0))

function open(hit: SearchHit): void {
  router.push(hit.route)
}

function submit(): void {
  submitted.value = keyword.value.trim()
  router.replace({ query: submitted.value ? { q: submitted.value } : {} })
}

const HOT = ['任务', '会议', '记账', '笔记']

onMounted(() => {
  inputRef.value?.focus()
})
</script>

<template>
  <section class="search-page">
    <header class="page-head">
      <h1>全局搜索</h1>
      <p class="page-head__sub">任务、笔记、项目、日志、资产、会议、联系人、记账一次搜完</p>
    </header>

    <div class="search-box">
      <el-icon class="search-box__icon"><Search /></el-icon>
      <input
        ref="inputRef"
        v-model="keyword"
        class="search-box__input"
        placeholder="输入关键词，回车搜索（Ctrl + K 可随时唤起）"
        @keyup.enter="submit"
      />
      <kbd class="search-box__kbd">Enter</kbd>
    </div>

    <div v-if="!submitted" class="search-hint">
      <p>试试搜索：</p>
      <div class="search-hint__tags">
        <button v-for="word in HOT" :key="word" type="button" @click="keyword = word; submit()">
          {{ word }}
        </button>
      </div>
    </div>

    <div v-else-if="query.isFetching.value" class="search-loading">搜索中…</div>

    <div v-else-if="!groups.length" class="search-empty">
      <p>没有找到「{{ submitted }}」相关内容</p>
      <span>换个关键词，或检查拼写</span>
    </div>

    <div v-else class="search-result">
      <p class="search-result__summary">共 {{ result?.total ?? 0 }} 条结果</p>

      <section v-for="group in groups" :key="group.key" class="search-group">
        <h2 class="search-group__title">
          {{ group.label }}
          <span>{{ group.hits.length }}</span>
        </h2>

        <ul class="search-group__list">
          <li v-for="hit in group.hits" :key="`${group.key}-${hit.id}`" class="search-hit" @click="open(hit)">
            <div class="search-hit__title">{{ hit.title }}</div>
            <p v-if="hit.snippet" class="search-hit__snippet">{{ hit.snippet }}</p>
            <span v-if="hit.meta" class="search-hit__meta">{{ hit.meta }}</span>
          </li>
        </ul>
      </section>
    </div>
  </section>
</template>

<style scoped lang="scss">
.search-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding-top: 8px;
  max-width: 920px;
}

.page-head {
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

.search-box {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  height: 52px;
  border-radius: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  box-shadow: 0 8px 24px rgba(23, 74, 60, 0.06);

  &:focus-within {
    border-color: var(--brand-300, #c6efe1);
    box-shadow: 0 10px 28px rgba(53, 199, 155, 0.12);
  }
}

.search-box__icon {
  font-size: 17px;
  color: var(--text-tertiary);
}

.search-box__input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-family: inherit;
  font-size: 14px;
  color: var(--text-primary);

  &::placeholder {
    color: #a9b7b3;
  }
}

.search-box__kbd {
  padding: 2px 8px;
  border-radius: 7px;
  background: var(--bg-subtle);
  color: #a6b4b0;
  font-size: 10px;
  font-family: inherit;
}

.search-hint {
  font-size: 12.5px;
  color: var(--text-tertiary);

  p {
    margin-bottom: 10px;
  }
}

.search-hint__tags {
  display: flex;
  gap: 8px;

  button {
    padding: 5px 14px;
    border-radius: 999px;
    border: 1px solid var(--border-color);
    background: var(--bg-card);
    color: var(--text-secondary);
    font-size: 12px;
    cursor: pointer;

    &:hover {
      border-color: var(--brand-200);
      color: var(--brand-700);
    }
  }
}

.search-loading,
.search-empty {
  @include card($radius-lg);
  padding: 40px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 12.5px;

  p {
    color: var(--text-secondary);
    font-size: 14px;
    margin-bottom: 4px;
  }
}

.search-result__summary {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 12px;
}

.search-group {
  & + & {
    margin-top: 20px;
  }
}

.search-group__title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-secondary);

  span {
    padding: 0 6px;
    border-radius: 999px;
    background: var(--bg-subtle);
    color: var(--text-tertiary);
    font-size: 10px;
    font-weight: 500;
  }
}

.search-group__list {
  @include card($radius-lg);
  overflow: hidden;
}

.search-hit {
  padding: 14px 16px;
  cursor: pointer;
  transition: background $duration-fast $ease-out;

  & + & {
    border-top: 1px solid #f5f9f7;
  }

  &:hover {
    background: var(--bg-subtle);
  }
}

.search-hit__title {
  font-size: 13.5px;
  font-weight: 600;
  color: #24393d;
}

.search-hit__snippet {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
  @include ellipsis(2);
}

.search-hit__meta {
  display: inline-block;
  margin-top: 6px;
  font-size: 10.5px;
  color: var(--text-tertiary);
}
</style>
