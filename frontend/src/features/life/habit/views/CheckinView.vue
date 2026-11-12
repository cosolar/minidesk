<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { habitApi, type HabitVO } from '../api'

const queryClient = useQueryClient()
const moduleTab = ref<'study' | 'life'>('study')
const selectedId = ref<string | null>(null)

const listQuery = useQuery({
  queryKey: ['habits', moduleTab],
  queryFn: () => habitApi.list(moduleTab.value),
  staleTime: 0,
})

const habits = computed(() => listQuery.data.value ?? [])
const activeId = computed(() => selectedId.value ?? habits.value[0]?.id ?? null)

const heatQuery = useQuery({
  queryKey: ['habit-heatmap', activeId],
  queryFn: () => habitApi.heatmap(activeId.value as string),
  enabled: computed(() => Boolean(activeId.value)),
})

const checkinMutation = useMutation({
  mutationFn: (payload: { habit: HabitVO; value: number }) =>
    habitApi.checkin(payload.habit.id, payload.value),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['habits'] })
    queryClient.invalidateQueries({ queryKey: ['habit-heatmap'] })
  },
  onError: () => ElMessage.error('打卡失败'),
})

function toggle(habit: HabitVO): void {
  const value = habit.checked ? 0 : habit.targetValue
  checkinMutation.mutate({ habit, value })
}

function increase(habit: HabitVO, delta: number): void {
  const next = Math.max(habit.todayValue + delta, 0)
  checkinMutation.mutate({ habit, value: next })
}

/* ---------- 热力图 ---------- */
const year = dayjs().year()
const heatCells = computed(() => {
  const days = heatQuery.data.value?.days ?? []
  const valueMap = new Map(days.map((item) => [item.date, item.value]))
  const start = dayjs(`${year}-01-01`)
  const padding = (start.day() + 6) % 7
  const total = dayjs(`${year}-12-31`).diff(start, 'day') + 1
  const cells: Array<{ key: string; level: number; title: string }> = []

  for (let i = 0; i < padding; i++) {
    cells.push({ key: `pad-${i}`, level: -1, title: '' })
  }
  for (let i = 0; i < total; i++) {
    const date = start.add(i, 'day')
    const key = date.format('YYYY-MM-DD')
    const value = valueMap.get(key) ?? 0
    cells.push({
      key,
      level: value > 0 ? Math.min(3, Math.max(1, value)) : 0,
      title: value > 0 ? `${key} · ${value}` : `${key} · 未打卡`,
    })
  }
  return cells
})

/* ---------- 新建 ---------- */
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  type: 'boolean' as 'boolean' | 'count',
  targetValue: 1,
  unit: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
}

const createMutation = useMutation({
  mutationFn: () =>
    habitApi.create({
      module: moduleTab.value,
      name: form.name.trim(),
      type: form.type,
      targetValue: form.targetValue,
      unit: form.unit || null,
      icon: 'lucide:flame',
      color: '#35C79B',
    }),
  onSuccess: () => {
    ElMessage.success('已添加')
    dialogVisible.value = false
    form.name = ''
    form.targetValue = 1
    form.unit = ''
    queryClient.invalidateQueries({ queryKey: ['habits'] })
  },
})

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  createMutation.mutate()
}
</script>

<template>
  <div class="checkin-page">
    <header class="page-head">
      <div>
        <h1>打卡</h1>
        <p class="page-head__sub">连续记录比偶尔努力更有用</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="dialogVisible = true">新建打卡项</el-button>
    </header>

    <div class="tabs">
      <button
        v-for="tab in [
          { key: 'study', label: '学习打卡' },
          { key: 'life', label: '生活习惯' },
        ]"
        :key="tab.key"
        type="button"
        class="tabs__item"
        :class="{ 'is-active': moduleTab === tab.key }"
        @click="moduleTab = tab.key as 'study' | 'life'"
      >
        {{ tab.label }}
      </button>
    </div>

    <el-skeleton v-if="listQuery.isLoading.value" :rows="4" animated />

    <div v-else-if="habits.length" class="habit-grid">
      <article
        v-for="habit in habits"
        :key="habit.id"
        class="habit-card"
        :class="{ 'is-active': activeId === habit.id }"
        @click="selectedId = habit.id"
      >
        <div class="habit-card__head">
          <span
            class="habit-card__icon"
            :style="{
              background: `${habit.color ?? '#35C79B'}1A`,
              color: habit.color ?? '#35C79B',
            }"
          >
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden="true">
              <path d="M13 2c.6 3.2-.8 4.6-2.3 6.1C9.2 9.6 7.5 11.4 7.5 14a6.5 6.5 0 0 0 13 0c0-4.6-3-8.4-7.5-12Z" opacity=".9" />
            </svg>
          </span>
          <div class="habit-card__title">
            <strong>{{ habit.name }}</strong>
            <span>连续 {{ habit.streak }} 天</span>
          </div>
        </div>

        <div class="habit-card__body" @click.stop>
          <template v-if="habit.type === 'count'">
            <div class="counter">
              <button type="button" @click="increase(habit, -1)">−</button>
              <span class="tabular">{{ habit.todayValue }}/{{ habit.targetValue }}{{ habit.unit }}</span>
              <button type="button" @click="increase(habit, 1)">+</button>
            </div>
          </template>
          <button
            v-else
            class="check-btn"
            type="button"
            :class="{ 'is-checked': habit.checked }"
            @click="toggle(habit)"
          >
            <svg v-if="habit.checked" viewBox="0 0 16 16" width="12" height="12" aria-hidden="true">
              <path d="M3.5 8.4 6.4 11 12.5 4.9" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            {{ habit.checked ? '今天已完成' : '打卡' }}
          </button>
        </div>
      </article>
    </div>

    <el-empty v-else description="还没有打卡项">
      <el-button type="primary" @click="dialogVisible = true">创建第一个</el-button>
    </el-empty>

    <section v-if="activeId" class="heatmap-panel">
      <header class="heatmap-panel__head">
        <h3>{{ heatQuery.data.value?.name ?? '打卡记录' }} · {{ year }} 年</h3>
        <div class="heatmap-panel__stats">
          <span>最长连续 {{ heatQuery.data.value?.longestStreak ?? 0 }} 天</span>
          <span>累计 {{ heatQuery.data.value?.totalDays ?? 0 }} 天</span>
        </div>
      </header>

      <div class="heatmap">
        <div
          v-for="cell in heatCells"
          :key="cell.key"
          class="heatmap__cell"
          :class="`level-${cell.level}`"
          :title="cell.title"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" title="新建打卡项" width="400px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：背单词 / 喝水" maxlength="20" />
        </el-form-item>

        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio-button value="boolean">完成 / 未完成</el-radio-button>
            <el-radio-button value="count">按次数累计</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <div v-if="form.type === 'count'" class="form-row">
          <el-form-item label="目标值">
            <el-input-number v-model="form.targetValue" :min="1" :max="200" />
          </el-form-item>
          <el-form-item label="单位">
            <el-input v-model="form.unit" placeholder="杯 / 页" style="width: 110px" />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createMutation.isPending.value" @click="submit">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.checkin-page {
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

.tabs {
  display: inline-flex;
  gap: 4px;
  align-self: flex-start;
  padding: 3px;
  border-radius: 10px;
  background: var(--bg-subtle);
}

.tabs__item {
  padding: 6px 16px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12.5px;
  cursor: pointer;

  &.is-active {
    background: var(--bg-card);
    color: var(--brand-700);
    font-weight: 600;
    box-shadow: var(--shadow-card);
  }
}

.habit-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;

  @include respond-below(lg) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @include respond-below(sm) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.habit-card {
  padding: 14px 16px;
  @include card($radius-lg);
  cursor: pointer;
  transition:
    border-color $duration-fast $ease-out,
    box-shadow $duration-fast $ease-out;

  &.is-active {
    border-color: var(--brand-400);
    box-shadow: 0 0 0 3px var(--brand-50);
  }
}

.habit-card__head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.habit-card__icon {
  @include flex-center;
  width: 36px;
  height: 36px;
  border-radius: 11px;
  flex: 0 0 36px;
}

.habit-card__title {
  display: flex;
  flex-direction: column;

  strong {
    font-size: 13.5px;
  }

  span {
    margin-top: 2px;
    font-size: 10.5px;
    color: var(--text-tertiary);
  }
}

.habit-card__body {
  margin-top: 12px;
}

.check-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  width: 100%;
  justify-content: center;
  padding: 8px 0;
  border-radius: 9px;
  border: 1px dashed var(--border-strong);
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all $duration-fast $ease-out;

  &.is-checked {
    border-style: solid;
    border-color: var(--brand-500);
    background: var(--brand-50);
    color: var(--brand-700);
    font-weight: 600;
  }
}

.counter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 6px;
  border-radius: 9px;
  background: var(--bg-subtle);

  button {
    width: 26px;
    height: 26px;
    border: none;
    border-radius: 7px;
    background: var(--bg-card);
    color: var(--text-secondary);
    cursor: pointer;
    box-shadow: var(--shadow-card);
  }

  span {
    font-size: 12.5px;
    font-weight: 600;
  }
}

.heatmap-panel {
  padding: 16px 18px;
  @include card($radius-xl);
}

.heatmap-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;

  h3 {
    font-size: 13.5px;
    font-weight: 600;
  }
}

.heatmap-panel__stats {
  display: flex;
  gap: 14px;
  font-size: 11px;
  color: var(--text-tertiary);
}

.heatmap {
  display: grid;
  grid-template-rows: repeat(7, 11px);
  grid-auto-flow: column;
  grid-auto-columns: 11px;
  gap: 3px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.heatmap__cell {
  width: 11px;
  height: 11px;
  border-radius: 2.5px;

  &.level--1 {
    background: transparent;
  }

  &.level-0 {
    background: var(--bg-subtle);
  }

  &.level-1 {
    background: var(--brand-200);
  }

  &.level-2 {
    background: var(--brand-400);
  }

  &.level-3 {
    background: var(--brand-600);
  }
}

.form-row {
  display: flex;
  gap: 12px;
}
</style>
