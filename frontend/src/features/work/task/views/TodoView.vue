<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { taskApi, type TaskPayload, type TaskVO } from '../api'
import { projectApi } from '@/features/work/project/api'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()

const statusTab = ref<'' | 'todo' | 'done'>('')
const moduleFilter = ref<'' | 'work' | 'study' | 'life'>('')
const keyword = ref('')

const queryKey = computed(() => ['tasks', statusTab.value, moduleFilter.value, keyword.value])

const { data, isLoading } = useQuery({
  queryKey,
  queryFn: () =>
    taskApi.page({
      status: statusTab.value || undefined,
      module: moduleFilter.value || undefined,
      keyword: keyword.value || undefined,
      size: 100,
    }),
})

const tasks = computed(() => data.value?.list ?? [])
const pendingCount = computed(() => tasks.value.filter((task) => !task.done).length)

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const projectsQuery = useQuery({
  queryKey: ['projects', 'options'],
  queryFn: () => projectApi.list(),
  staleTime: 60_000,
})
const projectOptions = computed(() => projectsQuery.data.value ?? [])

const form = reactive({
  title: '',
  module: 'work' as 'work' | 'study' | 'life',
  priority: 2,
  startDate: '',
  startTime: '',
  dueDate: '',
  dueTime: '',
  projectId: '',
  note: '',
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
}

const createMutation = useMutation({
  mutationFn: (payload: TaskPayload) => taskApi.create(payload),
  onSuccess: () => {
    ElMessage.success('已添加')
    dialogVisible.value = false
    resetForm()
    invalidate()
  },
})

const toggleMutation = useMutation({
  mutationFn: (payload: { id: string; done: boolean }) => taskApi.toggleDone(payload.id, payload.done),
  onSuccess: invalidate,
  onError: () => ElMessage.error('操作失败'),
})

const removeMutation = useMutation({
  mutationFn: (id: string) => taskApi.remove(id),
  onSuccess: () => {
    ElMessage.success('已删除')
    invalidate()
  },
})

function invalidate(): void {
  queryClient.invalidateQueries({ queryKey: ['tasks'] })
  queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  queryClient.invalidateQueries({ queryKey: ['calendar'] })
}

function resetForm(): void {
  form.title = ''
  form.module = 'work'
  form.priority = 2
  form.startDate = ''
  form.startTime = ''
  form.dueDate = ''
  form.dueTime = ''
  form.projectId = ''
  form.note = ''
  formRef.value?.clearValidate()
}

function openCreate(): void {
  resetForm()
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    await createMutation.mutateAsync({
      title: form.title.trim(),
      module: form.module,
      priority: form.priority,
      startDate: form.startDate || null,
      startTime: form.startTime || null,
      dueDate: form.dueDate || null,
      dueTime: form.dueTime || null,
      projectId: form.projectId || null,
      note: form.note || null,
    })
  } finally {
    submitting.value = false
  }
}

async function remove(task: TaskVO): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`删除「${task.title}」？`, '确认删除', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  }).catch(() => false)
  if (confirmed) {
    removeMutation.mutate(task.id)
  }
}

function isOverdue(task: TaskVO): boolean {
  if (!task.dueDate || task.done) {
    return false
  }
  return task.dueDate < new Date().toISOString().slice(0, 10)
}

function priorityLabel(priority: number): string {
  return priority === 1 ? '高' : priority === 3 ? '低' : '中'
}

function formatDue(task: TaskVO): string {
  const start = task.startDate ? `${task.startDate.slice(5).replace('-', '/')}${task.startTime ? ` ${task.startTime}` : ''}` : ''
  const due = task.dueDate ? `${task.dueDate.slice(5).replace('-', '/')}${task.dueTime ? ` ${task.dueTime}` : ''}` : ''
  if (start && due) {
    return `${start} → ${due}`
  }
  return due || start || '无截止'
}

onMounted(() => {
  const moduleParam = route.query.module
  if (moduleParam === 'work' || moduleParam === 'study' || moduleParam === 'life') {
    moduleFilter.value = moduleParam
  }
  if (route.query.new) {
    openCreate()
    router.replace({ query: moduleParam ? { module: moduleParam } : {} })
  }
})
</script>

<template>
  <div class="todo-page">
    <header class="page-head">
      <div>
        <h1>待办</h1>
        <p class="page-head__sub">工作、学习、生活的待办都在这里，共 {{ pendingCount }} 条未完成</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建任务</el-button>
    </header>

    <div class="filters">
      <div class="filters__tabs">
        <button
          v-for="tab in [
            { key: '', label: '全部' },
            { key: 'todo', label: '未完成' },
            { key: 'done', label: '已完成' },
          ]"
          :key="tab.key"
          type="button"
          class="filters__tab"
          :class="{ 'is-active': statusTab === tab.key }"
          @click="statusTab = tab.key as '' | 'todo' | 'done'"
        >
          {{ tab.label }}
        </button>
      </div>

      <div class="filters__tabs filters__tabs--module">
        <button
          v-for="tab in [
            { key: '', label: '全部板块' },
            { key: 'work', label: '工作' },
            { key: 'study', label: '学习' },
            { key: 'life', label: '生活' },
          ]"
          :key="tab.key"
          type="button"
          class="filters__tab"
          :class="{ 'is-active': moduleFilter === tab.key }"
          @click="moduleFilter = tab.key as '' | 'work' | 'study' | 'life'"
        >
          {{ tab.label }}
        </button>
      </div>

      <el-input v-model="keyword" placeholder="搜索标题" clearable style="width: 200px" />
    </div>

    <div class="panel">
      <el-skeleton v-if="isLoading" :rows="6" animated />

      <ul v-else-if="tasks.length" class="task-list">
        <li
          v-for="task in tasks"
          :key="task.id"
          class="task"
          :class="[{ 'is-done': task.done }, `is-${task.module}`]"
        >
          <button
            class="task__check"
            type="button"
            :aria-pressed="task.done"
            @click="toggleMutation.mutate({ id: task.id, done: !task.done })"
          >
            <svg v-if="task.done" viewBox="0 0 16 16" width="11" height="11" aria-hidden="true">
              <path
                d="M3.5 8.4 6.4 11 12.5 4.9"
                fill="none"
                stroke="currentColor"
                stroke-width="1.9"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>

          <div class="task__body">
            <div class="task__title">{{ task.title }}</div>
            <div class="task__meta">
              <span class="tag" :class="`tag--${task.module}`">
                {{ task.module === 'study' ? '学习' : task.module === 'life' ? '生活' : '工作' }}
              </span>
              <span v-if="task.projectName" class="task__project">{{ task.projectName }}</span>
              <span class="task__due" :class="{ 'is-overdue': isOverdue(task) }">{{ formatDue(task) }}</span>
              <span v-if="task.note" class="task__note">{{ task.note }}</span>
            </div>
          </div>

          <span class="priority" :class="`priority--${task.priority}`">{{ priorityLabel(task.priority) }}</span>

          <el-button link :icon="Delete" class="task__delete" @click="remove(task)" />
        </li>
      </ul>

      <el-empty v-else description="这里还没有待办">
        <el-button type="primary" @click="openCreate">新建一条</el-button>
      </el-empty>
    </div>

    <el-dialog v-model="dialogVisible" title="新建任务" width="460px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="要做什么？" maxlength="200" show-word-limit />
        </el-form-item>

        <div class="form-row">
          <el-form-item label="板块">
            <el-select v-model="form.module" style="width: 100%">
              <el-option label="工作" value="work" />
              <el-option label="学习" value="study" />
              <el-option label="生活" value="life" />
            </el-select>
          </el-form-item>

          <el-form-item label="优先级">
            <el-select v-model="form.priority" style="width: 100%">
              <el-option label="高" :value="1" />
              <el-option label="中" :value="2" />
              <el-option label="低" :value="3" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="项目">
          <el-select v-model="form.projectId" placeholder="不挂项目" clearable style="width: 100%">
            <el-option v-for="item in projectOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>

        <div class="form-row">
          <el-form-item label="开始日期">
            <el-date-picker
              v-model="form.startDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="开发任务工期起点"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="开始时间">
            <el-time-select
              v-model="form.startTime"
              start="06:00"
              step="00:30"
              end="23:30"
              placeholder="可选"
              style="width: 100%"
            />
          </el-form-item>
        </div>

        <div class="form-row">
          <el-form-item label="截止日期">
            <el-date-picker
              v-model="form.dueDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="时间">
            <el-time-select
              v-model="form.dueTime"
              start="06:00"
              step="00:30"
              end="23:30"
              placeholder="可选"
              style="width: 100%"
            />
          </el-form-item>
        </div>

        <el-form-item label="备注">
          <el-input v-model="form.note" type="textarea" :rows="2" placeholder="补充说明（可选）" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.todo-page {
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

.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 10px 14px;
  @include card($radius-lg);
}

.filters__tabs--module {
  padding-left: 12px;
  border-left: 1px solid var(--border-color);
}

.filters__tabs {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: 10px;
  background: var(--bg-subtle);
}

.filters__tab {
  padding: 5px 14px;
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

.task-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 6px 4px 4px;
}

.panel {
  padding: 8px 14px;
  @include card($radius-xl);
  min-height: 320px;
}

.task-list {
  display: flex;
  flex-direction: column;
}

.task {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px 12px 18px;
  border: 1px solid #eef5f2;
  border-radius: 12px;
  background: var(--bg-card);
  transition:
    border-color $duration-fast $ease-out,
    box-shadow $duration-fast $ease-out,
    background $duration-fast $ease-out;

  /* 左侧模块色条：一眼区分工作 / 学习 / 生活 */
  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 11px;
    bottom: 11px;
    width: 3px;
    border-radius: 0 3px 3px 0;
    background: var(--border-color);
  }

  &.is-work::before {
    background: rgba(74, 134, 232, 0.62);
  }

  &.is-study::before {
    background: rgba(139, 124, 246, 0.62);
  }

  &.is-life::before {
    background: rgba(255, 159, 67, 0.72);
  }

  &:hover {
    border-color: var(--brand-200);
    box-shadow: 0 8px 20px rgba(23, 74, 60, 0.06);
    background: var(--bg-subtle);

    .task__delete {
      opacity: 1;
    }
  }

  &.is-done {
    background: var(--bg-subtle);

    .task__title {
      color: var(--text-tertiary);
      text-decoration: line-through;
    }
  }
}

.task__check {
  @include flex-center;
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  padding: 0;
  border-radius: 6px;
  border: 1.4px solid var(--border-strong);
  background: transparent;
  color: #fff;
  cursor: pointer;

  .is-done & {
    background: var(--brand-500);
    border-color: var(--brand-500);
  }
}

.task__body {
  flex: 1;
  min-width: 0;
}

.task__title {
  font-size: 13.5px;
  color: var(--text-primary);
}

.task__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 11px;
  color: var(--text-tertiary);
}

.task__note {
  @include ellipsis(1);
  max-width: 260px;
}

.tag {
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 10.5px;

  &--work {
    background: var(--c-work-bg);
    color: var(--c-work);
  }

  &--study {
    background: var(--c-study-bg);
    color: var(--c-study);
  }

  &--life {
    background: var(--c-life-bg);
    color: var(--c-life);
  }
}

.priority {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 10.5px;
  white-space: nowrap;

  &--1 {
    background: var(--c-alert-bg);
    color: var(--c-alert);
  }

  &--2 {
    background: var(--bg-subtle);
    color: var(--text-secondary);
  }

  &--3 {
    background: var(--bg-subtle);
    color: var(--text-tertiary);
  }
}

.task__delete {
  opacity: 0;
  color: var(--text-tertiary);
  transition: opacity $duration-fast $ease-out;

  &:hover {
    color: var(--c-alert);
  }
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
</style>
