<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { reviewApi } from '../api'

const queryClient = useQueryClient()
const type = ref<'daily' | 'weekly' | 'monthly'>('daily')
const date = ref(dayjs().format('YYYY-MM-DD'))
const generating = ref(false)
const saving = ref(false)

const detailQuery = useQuery({
  queryKey: ['review', type, date],
  queryFn: () => reviewApi.today(type.value),
  staleTime: 0,
})

const form = reactive({
  summary: '',
  highlights: '',
  issues: '',
  nextPlan: '',
})

watch(
  () => detailQuery.data.value,
  (review) => {
    form.summary = review?.summary ?? ''
    form.highlights = review?.highlights ?? ''
    form.issues = review?.issues ?? ''
    form.nextPlan = review?.nextPlan ?? ''
  },
  { immediate: true },
)

const stats = computed(() => {
  const raw = detailQuery.data.value?.statsJson
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as { done: number; start: string; end: string }
  } catch {
    return null
  }
})

const typeLabel = computed(() => ({ daily: '日复盘', weekly: '周复盘', monthly: '月复盘' })[type.value])

const saveMutation = useMutation({
  mutationFn: () =>
    reviewApi.save({
      type: type.value,
      date: date.value,
      module: 'work',
      summary: form.summary,
      highlights: form.highlights,
      issues: form.issues,
      nextPlan: form.nextPlan,
    }),
  onSuccess: () => {
    ElMessage.success('已保存')
    queryClient.invalidateQueries({ queryKey: ['review'] })
  },
  onSettled: () => {
    saving.value = false
  },
})

async function generate(): Promise<void> {
  generating.value = true
  try {
    const review = await reviewApi.generate({ type: type.value, date: date.value, module: 'work' })
    form.summary = review.summary ?? ''
    ElMessage.success('已根据任务自动汇总')
    queryClient.invalidateQueries({ queryKey: ['review'] })
  } finally {
    generating.value = false
  }
}

function save(): void {
  saving.value = true
  saveMutation.mutate()
}
</script>

<template>
  <div class="review-page">
    <header class="page-head">
      <div>
        <h1>复盘</h1>
        <p class="page-head__sub">任务完成情况自动汇总，你只需补三句话</p>
      </div>
      <div class="page-head__actions">
        <el-radio-group v-model="type">
          <el-radio-button value="daily">日</el-radio-button>
          <el-radio-button value="weekly">周</el-radio-button>
          <el-radio-button value="monthly">月</el-radio-button>
        </el-radio-group>
        <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" style="width: 150px" />
      </div>
    </header>

    <div class="review-meta">
      <span class="review-meta__label">{{ typeLabel }} · {{ date }}</span>
      <span v-if="stats" class="review-meta__stat">已完成 {{ stats.done }} 条任务</span>
      <el-button :icon="MagicStick" :loading="generating" size="small" @click="generate">
        自动汇总
      </el-button>
    </div>

    <div class="review-grid">
      <section class="block">
        <h3>今天做了什么</h3>
        <el-input v-model="form.summary" type="textarea" :rows="5" placeholder="点「自动汇总」可自动填入" />
      </section>

      <section class="block">
        <h3>收获 / 亮点</h3>
        <el-input v-model="form.highlights" type="textarea" :rows="5" placeholder="值得记下来的进展" />
      </section>

      <section class="block">
        <h3>问题 / 卡点</h3>
        <el-input v-model="form.issues" type="textarea" :rows="5" placeholder="哪里不顺，为什么" />
      </section>

      <section class="block">
        <h3>下一步</h3>
        <el-input v-model="form.nextPlan" type="textarea" :rows="5" placeholder="明天/下周最重要的一件事" />
      </section>
    </div>

    <footer class="review-foot">
      <span class="review-foot__hint">统计快照保存后不随任务改动而变化</span>
      <el-button type="primary" :loading="saving" @click="save">保存复盘</el-button>
    </footer>
  </div>
</template>

<style scoped lang="scss">
.review-page {
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
  gap: 10px;
  align-items: center;
}

.review-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  @include card($radius-lg);
}

.review-meta__label {
  font-size: 12.5px;
  font-weight: 600;
}

.review-meta__stat {
  font-size: 11.5px;
  color: var(--brand-600);
}

.review-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;

  @include respond-below(md) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.block {
  padding: 14px 16px;
  @include card($radius-xl);

  h3 {
    margin-bottom: 10px;
    font-size: 13px;
    font-weight: 600;
  }
}

.review-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.review-foot__hint {
  font-size: 11px;
  color: var(--text-tertiary);
}
</style>
