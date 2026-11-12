<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Check, Plus, Plus as PlusIcon } from '@element-plus/icons-vue'
import { projectApi, type ProjectPayload, type ProjectVO } from '../api'
import { taskApi, type TaskVO } from '@/features/work/task/api'

const queryClient = useQueryClient()

const statusFilter = ref('')
const { data, isLoading } = useQuery({
  queryKey: ['projects', statusFilter],
  queryFn: () => projectApi.list({ status: statusFilter.value || undefined }),
  staleTime: 30_000,
})

const projects = computed(() => data.value ?? [])

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  description: '',
  status: 'doing',
  startDate: '',
  endDate: '',
  color: '#4A86E8',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
}

const createMutation = useMutation({
  mutationFn: (payload: ProjectPayload) => projectApi.create(payload),
  onSuccess: () => {
    ElMessage.success('项目已创建')
    dialogVisible.value = false
    form.name = ''
    form.description = ''
    form.startDate = ''
    form.endDate = ''
    queryClient.invalidateQueries({ queryKey: ['projects'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  },
})

function openCreate(): void {
  formRef.value?.clearValidate()
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
      name: form.name.trim(),
      description: form.description || null,
      status: form.status,
      startDate: form.startDate || null,
      endDate: form.endDate || null,
      color: form.color,
    })
  } finally {
    submitting.value = false
  }
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    planning: '规划中',
    doing: '进行中',
    paused: '已暂停',
    done: '已完成',
    archived: '已归档',
  }
  return map[status] ?? status
}

function projectCardClass(project: ProjectVO): string {
  return `status-${project.status}`
}

/** 项目详情抽屉：一个项目下的所有任务，可就地新增与勾选 */
const activeProject = ref<ProjectVO | null>(null)
const taskDrawer = ref(false)
const quickTitle = ref('')
const quickDue = ref('')

const projectTasksQuery = useQuery({
  queryKey: ['project-tasks', activeProject],
  queryFn: () => taskApi.page({ projectId: String(activeProject.value?.id), size: 100 }),
  enabled: computed(() => activeProject.value !== null),
})

const projectTasks = computed<TaskVO[]>(() => projectTasksQuery.data.value?.list ?? [])
const projectDone = computed(() => projectTasks.value.filter((task) => task.done).length)

function openTasks(project: ProjectVO): void {
  activeProject.value = project
  quickTitle.value = ''
  quickDue.value = ''
  taskDrawer.value = true
}

async function addTask(): Promise<void> {
  if (!quickTitle.value.trim() || !activeProject.value) {
    return
  }
  await taskApi.create({
    title: quickTitle.value.trim(),
    module: 'work',
    projectId: String(activeProject.value.id),
    dueDate: quickDue.value || null,
  })
  quickTitle.value = ''
  quickDue.value = ''
  await projectTasksQuery.refetch()
  queryClient.invalidateQueries({ queryKey: ['projects'] })
  ElMessage.success('已加入项目')
}

async function toggleTask(task: TaskVO): Promise<void> {
  await taskApi.toggleDone(task.id, !task.done)
  await projectTasksQuery.refetch()
  queryClient.invalidateQueries({ queryKey: ['projects'] })
  queryClient.invalidateQueries({ queryKey: ['tasks'] })
}

async function removeTask(task: TaskVO): Promise<void> {
  const ok = await ElMessageBox.confirm(`删除「${task.title}」？`, '确认删除', { type: 'warning' }).catch(
    () => false,
  )
  if (!ok) {
    return
  }
  await taskApi.remove(task.id)
  await projectTasksQuery.refetch()
  queryClient.invalidateQueries({ queryKey: ['projects'] })
}
</script>

<template>
  <div class="project-page">
    <header class="page-head">
      <div>
        <h1>项目管理</h1>
        <p class="page-head__sub">共 {{ projects.length }} 个项目，进度按任务完成率自动计算</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建项目</el-button>
    </header>

    <div class="filters">
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px">
        <el-option label="进行中" value="doing" />
        <el-option label="规划中" value="planning" />
        <el-option label="已完成" value="done" />
        <el-option label="已归档" value="archived" />
      </el-select>
    </div>

    <el-skeleton v-if="isLoading" :rows="6" animated />

    <div v-else-if="projects.length" class="project-grid">
      <article v-for="project in projects" :key="project.id" class="project-card" :class="projectCardClass(project)">
        <div class="project-card__head">
          <span class="project-card__dot" :style="{ background: project.color || '#4A86E8' }" />
          <h3>{{ project.name }}</h3>
          <span class="project-card__status">{{ statusLabel(project.status) }}</span>
        </div>

        <p class="project-card__desc">{{ project.description || '暂无描述' }}</p>

        <div class="project-card__progress">
          <div class="project-card__bar">
            <span :style="{ width: `${project.progress}%` }" />
          </div>
          <span class="project-card__percent tabular">{{ project.progress }}%</span>
        </div>

        <dl class="project-card__stats">
          <div><dt>任务</dt><dd>{{ project.stats.total }}</dd></div>
          <div><dt>已完成</dt><dd>{{ project.stats.done }}</dd></div>
          <div><dt>进行中</dt><dd>{{ project.stats.doing }}</dd></div>
          <div :class="{ 'is-warn': project.stats.overdue > 0 }">
            <dt>逾期</dt><dd>{{ project.stats.overdue }}</dd>
          </div>
        </dl>

        <div class="project-card__foot">
          <span>{{ project.startDate || '未设开始' }} → {{ project.endDate || '未设截止' }}</span>
          <el-button link type="primary" @click="openTasks(project)">查看任务</el-button>
        </div>
      </article>
    </div>

    <el-empty v-else description="还没有项目">
      <el-button type="primary" @click="openCreate">创建第一个项目</el-button>
    </el-empty>

    <el-drawer v-model="taskDrawer" size="560px" :with-header="false">
      <div v-if="activeProject" class="project-tasks">
        <header class="project-tasks__head">
          <div>
            <h2>{{ activeProject.name }}</h2>
            <p>{{ projectDone }} / {{ projectTasks.length }} 已完成 · 进度 {{ activeProject.progress }}%</p>
          </div>
          <el-button size="small" @click="taskDrawer = false">关闭</el-button>
        </header>

        <div class="project-tasks__add">
          <el-input v-model="quickTitle" placeholder="添加任务，回车提交" @keyup.enter="addTask">
            <template #append>
              <el-button :icon="PlusIcon" @click="addTask" />
            </template>
          </el-input>
          <el-date-picker v-model="quickDue" type="date" value-format="YYYY-MM-DD" placeholder="截止（可选）" style="width: 150px" />
        </div>

        <div v-if="projectTasksQuery.isLoading.value" class="project-tasks__hint">加载中…</div>
        <el-empty v-else-if="!projectTasks.length" description="这个项目还没有任务" :image-size="80" />
        <ul v-else class="project-task-list">
          <li v-for="task in projectTasks" :key="task.id" class="project-task" :class="{ 'is-done': task.done }">
            <el-button text :icon="Check" class="project-task__check" @click="toggleTask(task)" />
            <div class="project-task__body">
              <span class="project-task__title">{{ task.title }}</span>
              <span class="project-task__meta">
                {{ task.dueDate ? `截止 ${task.dueDate}` : '无截止' }}
              </span>
            </div>
            <el-button text size="small" @click="removeTask(task)">删除</el-button>
          </li>
        </ul>
      </div>
    </el-drawer>

    <el-dialog v-model="dialogVisible" title="新建项目" width="480px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：MiniDesk 开发" maxlength="120" />
        </el-form-item>

        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="一句话说明目标" />
        </el-form-item>

        <div class="form-row">
          <el-form-item label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="规划中" value="planning" />
              <el-option label="进行中" value="doing" />
              <el-option label="已完成" value="done" />
            </el-select>
          </el-form-item>

          <el-form-item label="主题色">
            <el-color-picker v-model="form.color" />
          </el-form-item>
        </div>

        <div class="form-row">
          <el-form-item label="开始日期">
            <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="截止日期">
            <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.project-tasks {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 4px 24px;
}

.project-tasks__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;

  h2 {
    font-size: 18px;
    font-weight: 700;
  }

  p {
    margin-top: 4px;
    font-size: 12px;
    color: var(--text-tertiary);
  }
}

.project-tasks__add {
  display: flex;
  gap: 8px;
}

.project-tasks__hint {
  padding: 30px;
  text-align: center;
  font-size: 12px;
  color: var(--text-tertiary);
}

.project-task-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.project-task {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  background: var(--bg-subtle);

  &.is-done .project-task__title {
    text-decoration: line-through;
    color: var(--text-tertiary);
  }
}

.project-task__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.project-task__title {
  font-size: 13px;
  @include ellipsis(1);
}

.project-task__meta {
  font-size: 11px;
  color: var(--text-tertiary);
}

.project-page {
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
  gap: 10px;
}

.project-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;

  @include respond-below(lg) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @include respond-below(sm) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.project-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px;
  @include card($radius-xl);
  transition:
    transform $duration-base $ease-out,
    box-shadow $duration-base $ease-out;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-card-hover);
  }
}

.project-card__head {
  display: flex;
  align-items: center;
  gap: 8px;

  h3 {
    flex: 1;
    min-width: 0;
    font-size: 14.5px;
    font-weight: 600;
    @include ellipsis(1);
  }
}

.project-card__dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex: 0 0 9px;
}

.project-card__status {
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 10.5px;
}

.project-card__desc {
  font-size: 12px;
  color: var(--text-tertiary);
  @include ellipsis(2);
  min-height: 34px;
}

.project-card__progress {
  display: flex;
  align-items: center;
  gap: 8px;
}

.project-card__bar {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: var(--bg-subtle);
  overflow: hidden;

  span {
    display: block;
    height: 100%;
    border-radius: 999px;
    background: linear-gradient(90deg, var(--brand-400), var(--brand-500));
    transition: width 600ms $ease-out;
  }
}

.project-card__percent {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--brand-700);
}

.project-card__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  margin: 0;
  padding: 8px 0;
  border-top: 1px solid var(--border-color);
  border-bottom: 1px solid var(--border-color);

  div {
    text-align: center;
  }

  dt {
    font-size: 10px;
    color: var(--text-tertiary);
  }

  dd {
    margin: 2px 0 0;
    font-size: 14px;
    font-weight: 600;
  }

  .is-warn dd {
    color: var(--c-alert);
  }
}

.project-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 10.5px;
  color: var(--text-tertiary);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
</style>
