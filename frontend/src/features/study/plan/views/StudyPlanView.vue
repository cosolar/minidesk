<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { studyPlanApi, type StudyPlanNode } from '../api'

const queryClient = useQueryClient()

const { data, isLoading } = useQuery({
  queryKey: ['study-plans'],
  queryFn: () => studyPlanApi.forest(),
  staleTime: 0,
})

const years = computed(() => data.value ?? [])
const activeYear = ref<string | null>(null)
const activeMonth = ref<string | null>(null)

const yearNode = computed(() => years.value.find((node) => node.id === activeYear.value) ?? years.value[0] ?? null)
const months = computed(() => yearNode.value?.children ?? [])
const monthNode = computed(() => months.value.find((node) => node.id === activeMonth.value) ?? months.value[0] ?? null)
const weeks = computed(() => monthNode.value?.children ?? [])

const dialogVisible = ref(false)
const form = reactive({ title: '', targetHours: 100, level: 1, parentId: '' as string | null })

const createMutation = useMutation({
  mutationFn: () =>
    studyPlanApi.create({
      title: form.title.trim(),
      level: form.level,
      parentId: form.parentId || null,
      targetHours: form.level === 3 ? 0 : form.targetHours,
      startDate: dayjs().format('YYYY-MM-DD'),
    }),
  onSuccess: () => {
    ElMessage.success('已添加')
    dialogVisible.value = false
    form.title = ''
    queryClient.invalidateQueries({ queryKey: ['study-plans'] })
  },
})

function openAdd(level: number, parentId: string | null): void {
  form.level = level
  form.parentId = parentId
  form.title = level === 1 ? `${dayjs().year()} 年度计划` : ''
  form.targetHours = level === 1 ? 200 : level === 2 ? 16 : 0
  dialogVisible.value = true
}

async function remove(node: StudyPlanNode): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`删除「${node.title}」及其子计划？`, '确认删除', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  }).catch(() => false)
  if (!confirmed) {
    return
  }
  await studyPlanApi.remove(node.id)
  queryClient.invalidateQueries({ queryKey: ['study-plans'] })
}

function selectYear(id: string): void {
  activeYear.value = id
  activeMonth.value = null
}
</script>

<template>
  <div class="plan-page">
    <header class="page-head">
      <div>
        <h1>学习计划</h1>
        <p class="page-head__sub">年 → 月 → 周 逐层拆解，进度按任务完成率自动汇总</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openAdd(1, null)">新建年度计划</el-button>
    </header>

    <el-skeleton v-if="isLoading" :rows="6" animated />

    <el-empty v-else-if="!years.length" description="还没有年度计划">
      <el-button type="primary" @click="openAdd(1, null)">创建第一个</el-button>
    </el-empty>

    <div v-else class="plan-body">
      <aside class="col col--years">
        <header class="col__head">
          <h3>年度</h3>
        </header>
        <ul class="node-list">
          <li
            v-for="node in years"
            :key="node.id"
            class="node"
            :class="{ 'is-active': yearNode?.id === node.id }"
            @click="selectYear(node.id)"
          >
            <div class="node__title">{{ node.title }}</div>
            <div class="node__meta">
              <span class="tabular">{{ node.progress }}%</span>
              <span>{{ node.taskDone }}/{{ node.taskTotal }} 任务</span>
            </div>
            <div class="node__bar"><span :style="{ width: `${node.progress}%` }" /></div>
          </li>
        </ul>
      </aside>

      <section class="col col--months">
        <header class="col__head">
          <h3>月度</h3>
          <button type="button" @click="openAdd(2, yearNode?.id ?? null)">
            <el-icon><Plus /></el-icon>
          </button>
        </header>

        <ul v-if="months.length" class="node-list">
          <li
            v-for="node in months"
            :key="node.id"
            class="node"
            :class="{ 'is-active': monthNode?.id === node.id }"
            @click="activeMonth = node.id"
          >
            <div class="node__title">{{ node.title }}</div>
            <div class="node__meta">
              <span class="tabular">{{ node.progress }}%</span>
              <span>{{ node.taskDone }}/{{ node.taskTotal }} 任务</span>
            </div>
            <div class="node__bar"><span :style="{ width: `${node.progress}%` }" /></div>
          </li>
        </ul>
        <el-empty v-else description="还没有月计划" :image-size="60" />
      </section>

      <section class="col col--weeks">
        <header class="col__head">
          <h3>周计划</h3>
          <button type="button" @click="openAdd(3, monthNode?.id ?? null)">
            <el-icon><Plus /></el-icon>
          </button>
        </header>

        <ul v-if="weeks.length" class="week-list">
          <li v-for="node in weeks" :key="node.id" class="week">
            <div class="week__head">
              <span class="week__title">{{ node.title }}</span>
              <span class="week__progress tabular">{{ node.progress }}%</span>
            </div>
            <div class="node__bar"><span :style="{ width: `${node.progress}%` }" /></div>
            <div class="week__foot">
              <span>{{ node.taskDone }}/{{ node.taskTotal }} 个任务完成</span>
              <button type="button" class="week__delete" @click="remove(node)">删除</button>
            </div>
          </li>
        </ul>
        <el-empty v-else description="还没有周计划，先加一个" :image-size="60" />
      </section>
    </div>

    <el-dialog v-model="dialogVisible" :title="`新建${['', '年度', '月度', '周'][form.level]}计划`" width="400px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="名称">
          <el-input v-model="form.title" placeholder="例如：2026 年度计划 / 11 月 / 第 2 周" maxlength="40" />
        </el-form-item>
        <el-form-item v-if="form.level < 3" label="目标时长（小时）">
          <el-input-number v-model="form.targetHours" :min="0" :max="2000" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createMutation.isPending.value" @click="createMutation.mutate()">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.plan-page {
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

.plan-body {
  display: grid;
  grid-template-columns: 1.1fr 1fr 1.1fr;
  gap: 16px;
  align-items: start;

  @include respond-below(lg) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.col {
  padding: 14px 16px;
  @include card($radius-xl);
  min-height: 300px;
}

.col__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;

  h3 {
    font-size: 13.5px;
    font-weight: 600;
  }

  button {
    @include flex-center;
    width: 22px;
    height: 22px;
    border: none;
    border-radius: 7px;
    background: var(--bg-subtle);
    color: var(--text-secondary);
    cursor: pointer;

    &:hover {
      background: var(--brand-50);
      color: var(--brand-600);
    }
  }
}

.node-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.node {
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background $duration-fast $ease-out;

  &:hover {
    background: var(--bg-hover);
  }

  &.is-active {
    background: var(--brand-50);

    .node__title {
      color: var(--brand-700);
      font-weight: 600;
    }
  }
}

.node__title {
  font-size: 13px;
  @include ellipsis(1);
}

.node__meta {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 10.5px;
  color: var(--text-tertiary);
}

.node__bar {
  height: 4px;
  margin-top: 6px;
  border-radius: 999px;
  background: var(--bg-subtle);
  overflow: hidden;

  span {
    display: block;
    height: 100%;
    border-radius: 999px;
    background: linear-gradient(90deg, var(--brand-400), var(--brand-500));
    transition: width 500ms $ease-out;
  }
}

.week-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.week {
  padding: 12px 14px;
  border-radius: $radius-md;
  background: var(--bg-subtle);
}

.week__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.week__title {
  font-size: 13px;
  font-weight: 500;
}

.week__progress {
  font-size: 12px;
  font-weight: 600;
  color: var(--brand-700);
}

.week__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 10.5px;
  color: var(--text-tertiary);
}

.week__delete {
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  font-size: 10.5px;
  cursor: pointer;

  &:hover {
    color: var(--c-alert);
  }
}
</style>
