<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft, ArrowRight, Plus } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { lifeLogApi } from '../api'

const queryClient = useQueryClient()
const month = ref(dayjs().format('YYYY-MM'))
const monthLabel = computed(() => dayjs(`${month.value}-01`).format('YYYY年M月'))

const { data, isLoading } = useQuery({
  queryKey: ['life-logs', month],
  queryFn: () => lifeLogApi.page(month.value),
  staleTime: 10_000,
})

const logs = computed(() => data.value?.list ?? [])

const moods = [
  { value: 'happy', label: '开心', emoji: '😊' },
  { value: 'calm', label: '平静', emoji: '🙂' },
  { value: 'tired', label: '有点累', emoji: '😮‍💨' },
  { value: 'sad', label: '低落', emoji: '😔' },
  { value: 'excited', label: '兴奋', emoji: '🤩' },
]

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  title: '',
  content: '',
  mood: 'calm',
  logDate: dayjs().format('YYYY-MM-DD'),
})

const rules: FormRules = {
  title: [{ required: true, message: '写个标题', trigger: 'blur' }],
}

const createMutation = useMutation({
  mutationFn: () =>
    lifeLogApi.create({
      title: form.title.trim(),
      content: form.content || undefined,
      mood: form.mood,
      logDate: form.logDate,
    }),
  onSuccess: () => {
    ElMessage.success('已记录')
    dialogVisible.value = false
    form.title = ''
    form.content = ''
    queryClient.invalidateQueries({ queryKey: ['life-logs'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  },
})

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    await createMutation.mutateAsync()
  } finally {
    submitting.value = false
  }
}

async function remove(id: string, title: string): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`删除「${title}」？`, '确认删除', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  }).catch(() => false)
  if (confirmed) {
    await lifeLogApi.remove(id)
    queryClient.invalidateQueries({ queryKey: ['life-logs'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }
}

function shiftMonth(step: number): void {
  month.value = dayjs(`${month.value}-01`).add(step, 'month').format('YYYY-MM')
}

function moodEmoji(mood: string | null): string {
  return moods.find((item) => item.value === mood)?.emoji ?? '📝'
}

function dayText(date: string): string {
  return dayjs(date).format('DD')
}
</script>

<template>
  <div class="lifelog-page">
    <header class="page-head">
      <div>
        <h1>生活记录</h1>
        <p class="page-head__sub">值得记住的小事，都放这里</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="dialogVisible = true">记一条</el-button>
    </header>

    <div class="month-bar">
      <button type="button" aria-label="上个月" @click="shiftMonth(-1)">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <strong>{{ monthLabel }}</strong>
      <button type="button" aria-label="下个月" @click="shiftMonth(1)">
        <el-icon><ArrowRight /></el-icon>
      </button>
    </div>

    <el-skeleton v-if="isLoading" :rows="5" animated />

    <div v-else-if="logs.length" class="log-list">
      <article v-for="log in logs" :key="log.id" class="log">
        <div class="log__date">
          <strong class="tabular">{{ dayText(log.logDate) }}</strong>
          <span>{{ dayjs(log.logDate).format('MM月') }}</span>
        </div>
        <div class="log__body">
          <div class="log__title">
            <span class="log__mood">{{ moodEmoji(log.mood) }}</span>
            {{ log.title }}
          </div>
          <p v-if="log.content" class="log__content">{{ log.content }}</p>
        </div>
        <button type="button" class="log__delete" @click="remove(log.id, log.title)">删除</button>
      </article>
    </div>

    <el-empty v-else description="这个月还没有记录">
      <el-button type="primary" @click="dialogVisible = true">记一条</el-button>
    </el-empty>

    <el-dialog v-model="dialogVisible" title="记一条生活记录" width="460px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="例如：第一次做提拉米苏" maxlength="60" />
        </el-form-item>

        <el-form-item label="日期">
          <el-date-picker v-model="form.logDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>

        <el-form-item label="心情">
          <el-radio-group v-model="form.mood">
            <el-radio-button v-for="mood in moods" :key="mood.value" :value="mood.value">
              {{ mood.emoji }} {{ mood.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="4" placeholder="发生了什么" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.lifelog-page {
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

.month-bar {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  align-self: flex-start;
  padding: 6px 10px;
  @include card($radius-lg);

  button {
    @include flex-center;
    width: 24px;
    height: 24px;
    border: none;
    border-radius: 7px;
    background: transparent;
    color: var(--text-tertiary);
    cursor: pointer;

    &:hover {
      background: var(--bg-hover);
      color: var(--brand-600);
    }
  }

  strong {
    font-size: 13px;
    min-width: 92px;
    text-align: center;
  }
}

.log-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.log {
  position: relative;
  display: flex;
  gap: 16px;
  padding: 16px 18px;
  @include card($radius-xl);

  &:hover .log__delete {
    opacity: 1;
  }
}

.log__date {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 46px;

  strong {
    font-size: 24px;
    font-weight: 700;
    line-height: 1;
    color: var(--brand-600);
  }

  span {
    margin-top: 3px;
    font-size: 10px;
    color: var(--text-tertiary);
  }
}

.log__body {
  flex: 1;
  min-width: 0;
}

.log__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
}

.log__mood {
  font-size: 15px;
}

.log__content {
  margin-top: 6px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--text-secondary);
  white-space: pre-wrap;
}

.log__delete {
  position: absolute;
  top: 10px;
  right: 12px;
  opacity: 0;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  font-size: 11px;
  cursor: pointer;

  &:hover {
    color: var(--c-alert);
  }
}
</style>
