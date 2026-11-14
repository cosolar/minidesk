<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, MagicStick, Plus } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import MarkdownEditor from '@/shared/components/MarkdownEditor.vue'
import { journalApi, type JournalType, type JournalVO } from '../api'

const queryClient = useQueryClient()

const TYPES: Array<{ key: JournalType; label: string; hint: string }> = [
  { key: 'daily', label: '日报', hint: '每天 5 分钟，记下做完的事' },
  { key: 'weekly', label: '周报', hint: '一周成果与下周计划' },
  { key: 'monthly', label: '月报', hint: '月度目标回顾与产出' },
  { key: 'yearly', label: '年报', hint: '年度回顾与来年规划' },
]

const activeType = ref<JournalType>('daily')
const activeHint = computed(() => TYPES.find((item) => item.key === activeType.value)?.hint ?? '')

const selectedId = ref<number | null>(null)
const draft = ref({
  id: null as number | null,
  title: '',
  content: '',
  periodStart: '',
  periodEnd: '',
})
const dirty = ref(false)
const saving = ref(false)

const listQuery = useQuery({
  queryKey: ['journals', activeType],
  queryFn: () => journalApi.page(activeType.value),
  staleTime: 30_000,
})

const items = computed<JournalVO[]>(() => listQuery.data.value?.list ?? [])

const draftMutation = useMutation({
  mutationFn: (type: JournalType) => journalApi.draft(type),
  onSuccess: (data) => {
    draft.value = {
      id: null,
      title: data.title,
      content: data.content ?? '',
      periodStart: data.periodStart,
      periodEnd: data.periodEnd,
    }
    selectedId.value = null
    dirty.value = true
    ElMessage.success('已按周期生成草稿，可直接修改')
  },
})

watch(activeType, () => {
  selectedId.value = null
  draft.value = { id: null, title: '', content: '', periodStart: '', periodEnd: '' }
  dirty.value = false
})

async function openItem(id: number): Promise<void> {
  if (dirty.value && draft.value.id !== null) {
    const ok = await ElMessageBox.confirm('当前日志有未保存的修改，确定切换？', '提示', {
      type: 'warning',
    }).catch(() => false)
    if (!ok) {
      return
    }
  }
  const detail = await journalApi.detail(id)
  selectedId.value = id
  draft.value = {
    id: detail.id,
    title: detail.title,
    content: detail.content ?? '',
    periodStart: detail.periodStart,
    periodEnd: detail.periodEnd,
  }
  dirty.value = false
}

function createBlank(): void {
  draft.value = {
    id: null,
    title: `${dayjs().format('YYYY-MM-DD')} ${TYPES.find((i) => i.key === activeType.value)?.label}`,
    content: '',
    periodStart: dayjs().format('YYYY-MM-DD'),
    periodEnd: dayjs().format('YYYY-MM-DD'),
  }
  selectedId.value = null
  dirty.value = true
}

async function save(): Promise<void> {
  if (!draft.value.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  saving.value = true
  try {
    const payload = {
      journalType: activeType.value,
      title: draft.value.title,
      date: draft.value.periodStart || dayjs().format('YYYY-MM-DD'),
      content: draft.value.content,
    }
    if (draft.value.id) {
      await journalApi.update(draft.value.id, payload)
    } else {
      const created = await journalApi.create(payload)
      draft.value.id = created.id
      selectedId.value = created.id
    }
    dirty.value = false
    ElMessage.success('已保存')
    queryClient.invalidateQueries({ queryKey: ['journals', activeType.value] })
  } finally {
    saving.value = false
  }
}

async function removeItem(item: JournalVO): Promise<void> {
  const ok = await ElMessageBox.confirm(`删除「${item.title}」？`, '确认删除', { type: 'warning' }).catch(
    () => false,
  )
  if (!ok || !item.id) {
    return
  }
  await journalApi.remove(item.id)
  if (selectedId.value === item.id) {
    selectedId.value = null
    draft.value = { id: null, title: '', content: '', periodStart: '', periodEnd: '' }
  }
  queryClient.invalidateQueries({ queryKey: ['journals', activeType.value] })
  ElMessage.success('已删除')
}
</script>

<template>
  <section class="journal-page">
    <header class="page-head">
      <div>
        <h1>日志</h1>
        <p class="page-head__sub">{{ activeHint }}</p>
      </div>
      <div class="page-head__actions">
        <el-button :icon="MagicStick" :loading="draftMutation.isPending.value" @click="draftMutation.mutate(activeType)">
          自动汇总
        </el-button>
        <el-button type="primary" :icon="Plus" @click="createBlank">新建</el-button>
        <el-button :loading="saving" :disabled="!draft.title" @click="save">保存</el-button>
      </div>
    </header>

    <div class="journal-body">
      <aside class="journal-list">
        <el-radio-group v-model="activeType" size="small" class="journal-tabs">
          <el-radio-button v-for="item in TYPES" :key="item.key" :value="item.key">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>

        <div v-if="listQuery.isLoading.value" class="journal-list__hint">加载中…</div>
        <div v-else-if="!items.length" class="journal-list__hint">
          还没有{{ TYPES.find((i) => i.key === activeType)?.label }}，点「自动汇总」试试
        </div>
        <ul v-else class="journal-list__items">
          <li
            v-for="item in items"
            :key="item.id ?? item.periodStart"
            class="journal-item"
            :class="{ 'is-active': selectedId === item.id }"
            @click="item.id && openItem(item.id)"
          >
            <div class="journal-item__main">
              <span class="journal-item__title">{{ item.title }}</span>
              <span class="journal-item__period">{{ item.periodStart }} ~ {{ item.periodEnd }}</span>
            </div>
            <el-button
              text
              size="small"
              :icon="Delete"
              class="journal-item__del"
              @click.stop="removeItem(item)"
            />
          </li>
        </ul>
      </aside>

      <div class="journal-editor">
        <div v-if="!draft.title && !draft.content" class="journal-editor__empty">
          <p>选一篇日志继续写，或点「自动汇总」按周期生成</p>
          <div class="journal-editor__quick">
            <el-button
              v-for="item in TYPES"
              :key="item.key"
              plain
              @click="activeType = item.key; draftMutation.mutate(item.key)"
            >
              生成{{ item.label }}
            </el-button>
          </div>
        </div>
        <template v-else>
          <div class="journal-editor__head">
            <el-input v-model="draft.title" placeholder="日志标题" class="journal-editor__title" @input="dirty = true" />
            <span class="journal-editor__range">{{ draft.periodStart }} ~ {{ draft.periodEnd }}</span>
            <span v-if="dirty" class="journal-editor__flag">未保存</span>
          </div>
          <MarkdownEditor v-model="draft.content" :height="560" placeholder="写下今天的进展、卡点与想法…" @update:model-value="dirty = true" />
        </template>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.journal-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 8px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;

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
}

.journal-body {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.journal-list {
  @include card($radius-lg);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: calc(100vh - 200px);
}

.journal-tabs {
  display: flex;
  flex-wrap: wrap;
}

.journal-list__hint {
  padding: 24px 8px;
  font-size: 12px;
  color: var(--text-tertiary);
  text-align: center;
}

.journal-list__items {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow: auto;
  @include scrollbar;
}

.journal-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.16s ease;

  &:hover {
    background: var(--bg-hover);
  }

  &.is-active {
    background: var(--brand-50);
  }
}

.journal-item__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.journal-item__title {
  font-size: 13px;
  font-weight: 600;
  @include ellipsis(1);
}

.journal-item__period {
  font-size: 11px;
  color: var(--text-tertiary);
}

.journal-item__del {
  opacity: 0;
  transition: opacity 0.16s ease;
}

.journal-item:hover .journal-item__del {
  opacity: 1;
}

.journal-editor {
  // 内含 Markdown 编辑器，全屏依赖 position: fixed，不能用带 backdrop-filter 的玻璃卡
  @include card-solid($radius-lg);
  padding: 16px;
  min-height: 420px;
}

.journal-editor__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  min-height: 380px;
  color: var(--text-tertiary);
  font-size: 13px;
}

.journal-editor__quick {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.journal-editor__head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.journal-editor__title {
  max-width: 420px;

  :deep(.el-input__inner) {
    font-size: 16px;
    font-weight: 600;
  }
}

.journal-editor__range {
  font-size: 12px;
  color: var(--text-tertiary);
}

.journal-editor__flag {
  margin-left: auto;
  font-size: 12px;
  color: var(--warning);
}

@media (max-width: 1024px) {
  .journal-body {
    grid-template-columns: 1fr;
  }

  .journal-list {
    max-height: 320px;
  }
}
</style>
