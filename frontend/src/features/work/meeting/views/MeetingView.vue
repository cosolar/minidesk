<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Calendar, Delete, Location, Plus, Search, User } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import MarkdownEditor from '@/shared/components/MarkdownEditor.vue'
import { meetingApi, type MeetingPayload, type MeetingVO } from '../api'

const queryClient = useQueryClient()

const keyword = ref('')

const listQuery = useQuery({
  queryKey: ['meetings', keyword],
  queryFn: () => meetingApi.list(keyword.value || undefined),
  staleTime: 20_000,
})

const meetings = computed(() => listQuery.data.value ?? [])

const drawerVisible = ref(false)
const detail = ref<MeetingVO | null>(null)

async function openDetail(item: MeetingVO): Promise<void> {
  detail.value = await meetingApi.detail(item.id)
  drawerVisible.value = true
}

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

interface ActionDraft {
  content: string
  owner: string
  dueDate: string
}

const form = reactive({
  title: '',
  meetingDate: dayjs().format('YYYY-MM-DD'),
  startTime: '10:00',
  endTime: '11:00',
  location: '',
  host: '',
  content: '',
  attendees: [] as string[],
  actions: [] as ActionDraft[],
})

function openCreate(): void {
  editingId.value = null
  Object.assign(form, {
    title: '',
    meetingDate: dayjs().format('YYYY-MM-DD'),
    startTime: '10:00',
    endTime: '11:00',
    location: '',
    host: '',
    content: '',
    attendees: [],
    actions: [],
  })
  dialogVisible.value = true
}

function openEdit(item: MeetingVO): void {
  editingId.value = item.id
  Object.assign(form, {
    title: item.title,
    meetingDate: item.meetingDate,
    startTime: item.startTime ?? '',
    endTime: item.endTime ?? '',
    location: item.location ?? '',
    host: item.host ?? '',
    content: item.content ?? '',
    attendees: [...item.attendees],
    actions: item.actions.map((action) => ({
      content: action.content,
      owner: action.owner ?? '',
      dueDate: action.dueDate ?? '',
    })),
  })
  drawerVisible.value = false
  dialogVisible.value = true
}

function addAction(): void {
  form.actions.push({ content: '', owner: '', dueDate: '' })
}

function removeAction(index: number): void {
  form.actions.splice(index, 1)
}

const saveMutation = useMutation({
  mutationFn: (payload: MeetingPayload) =>
    editingId.value ? meetingApi.update(editingId.value, payload) : meetingApi.create(payload),
  onSuccess: () => {
    ElMessage.success('已保存')
    dialogVisible.value = false
    queryClient.invalidateQueries({ queryKey: ['meetings'] })
    queryClient.invalidateQueries({ queryKey: ['tasks'] })
  },
})

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saveMutation.mutate({
    title: form.title,
    meetingDate: form.meetingDate,
    startTime: form.startTime || null,
    endTime: form.endTime || null,
    location: form.location || null,
    host: form.host || null,
    content: form.content,
    attendees: form.attendees,
    actions: form.actions
      .filter((action) => action.content.trim())
      .map((action) => ({
        content: action.content,
        owner: action.owner || null,
        dueDate: action.dueDate || null,
      })),
  })
}

async function convert(actionId: number): Promise<void> {
  await meetingApi.actionToTask(actionId)
  ElMessage.success('已加入任务清单')
  if (detail.value) {
    detail.value = await meetingApi.detail(detail.value.id)
  }
  queryClient.invalidateQueries({ queryKey: ['tasks'] })
  queryClient.invalidateQueries({ queryKey: ['meetings'] })
}

async function removeMeeting(item: MeetingVO): Promise<void> {
  const ok = await ElMessageBox.confirm(`删除「${item.title}」？`, '确认删除', { type: 'warning' }).catch(
    () => false,
  )
  if (!ok) {
    return
  }
  await meetingApi.remove(item.id)
  drawerVisible.value = false
  queryClient.invalidateQueries({ queryKey: ['meetings'] })
  ElMessage.success('已删除')
}
</script>

<template>
  <section class="meeting-page">
    <header class="page-head">
      <div>
        <h1>会议纪要</h1>
        <p class="page-head__sub">记下结论，把跟进事项一键转成任务</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建纪要</el-button>
    </header>

    <el-input v-model="keyword" :prefix-icon="Search" placeholder="搜索主题或正文" clearable class="meeting-search" />

    <div v-if="listQuery.isLoading.value" class="meeting-hint">加载中…</div>
    <el-empty v-else-if="!meetings.length" description="还没有会议纪要，开完会顺手记两条就够" />
    <div v-else class="meeting-list">
      <article v-for="item in meetings" :key="item.id" class="meeting-card" @click="openDetail(item)">
        <div class="meeting-card__date">
          <span class="meeting-card__day">{{ item.meetingDate.slice(8, 10) }}</span>
          <span class="meeting-card__month">{{ item.meetingDate.slice(5, 7) }}月</span>
        </div>
        <div class="meeting-card__body">
          <h3>{{ item.title }}</h3>
          <div class="meeting-card__meta">
            <span v-if="item.startTime"><el-icon><Calendar /></el-icon>{{ item.startTime }}<template v-if="item.endTime">-{{ item.endTime }}</template></span>
            <span v-if="item.location"><el-icon><Location /></el-icon>{{ item.location }}</span>
            <span v-if="item.attendees.length"><el-icon><User /></el-icon>{{ item.attendees.length }} 人</span>
          </div>
        </div>
        <el-tag v-if="item.actions.length" size="small" type="warning" effect="plain">
          {{ item.actions.filter((a) => !a.taskId).length }} 项待跟进
        </el-tag>
      </article>
    </div>

    <el-drawer v-model="drawerVisible" size="720px" :with-header="false">
      <div v-if="detail" class="meeting-detail">
        <header class="meeting-detail__head">
          <div>
            <h2>{{ detail.title }}</h2>
            <p class="meeting-detail__meta">
              {{ detail.meetingDate }}
              <template v-if="detail.startTime"> · {{ detail.startTime }}<template v-if="detail.endTime">-{{ detail.endTime }}</template></template>
              <template v-if="detail.location"> · {{ detail.location }}</template>
              <template v-if="detail.host"> · 主持：{{ detail.host }}</template>
            </p>
          </div>
          <div class="meeting-detail__ops">
            <el-button size="small" @click="openEdit(detail)">编辑</el-button>
            <el-button size="small" :icon="Delete" @click="removeMeeting(detail)" />
          </div>
        </header>

        <div v-if="detail.attendees.length" class="meeting-detail__block">
          <h4>参会人</h4>
          <div class="meeting-detail__tags">
            <el-tag v-for="name in detail.attendees" :key="name" effect="plain">{{ name }}</el-tag>
          </div>
        </div>

        <div class="meeting-detail__block">
          <h4>纪要</h4>
          <div v-if="detail.content" class="meeting-detail__content">
            <MarkdownEditor :model-value="detail.content" :height="300" readonly />
          </div>
          <p v-else class="meeting-detail__empty">没有正文</p>
        </div>

        <div class="meeting-detail__block">
          <h4>跟进事项</h4>
          <p v-if="!detail.actions.length" class="meeting-detail__empty">没有跟进事项</p>
          <ul v-else class="meeting-actions">
            <li v-for="action in detail.actions" :key="action.id" class="meeting-action">
              <div class="meeting-action__main">
                <span>{{ action.content }}</span>
                <span class="meeting-action__meta">
                  <template v-if="action.owner">{{ action.owner }}</template>
                  <template v-if="action.dueDate"> · 截止 {{ action.dueDate }}</template>
                </span>
              </div>
              <el-button v-if="!action.taskId" size="small" type="primary" plain @click="convert(action.id)">
                转任务
              </el-button>
              <el-tag v-else size="small" type="success" effect="plain">已建任务</el-tag>
            </li>
          </ul>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑纪要' : '新建纪要'" width="820px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="72px">
        <el-form-item
          label="主题"
          prop="title"
          :rules="[{ required: true, message: '请输入会议主题', trigger: 'blur' }]"
        >
          <el-input v-model="form.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="时间">
          <div class="meeting-form__row">
            <el-date-picker v-model="form.meetingDate" type="date" value-format="YYYY-MM-DD" style="width: 160px" />
            <el-time-picker v-model="form.startTime" format="HH:mm" value-format="HH:mm" placeholder="开始" style="width: 110px" />
            <el-time-picker v-model="form.endTime" format="HH:mm" value-format="HH:mm" placeholder="结束" style="width: 110px" />
          </div>
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="form.location" maxlength="200" />
        </el-form-item>
        <el-form-item label="主持">
          <el-input v-model="form.host" maxlength="64" style="width: 200px" />
        </el-form-item>
        <el-form-item label="参会人">
          <el-select
            v-model="form.attendees"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入姓名后回车"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="纪要">
          <MarkdownEditor v-model="form.content" :height="240" placeholder="结论、分歧、决策…" />
        </el-form-item>
        <el-form-item label="跟进">
          <div class="meeting-actions-edit">
            <div v-for="(action, index) in form.actions" :key="index" class="meeting-action-edit">
              <el-input v-model="action.content" placeholder="要做的事" />
              <el-input v-model="action.owner" placeholder="负责人" style="width: 110px" />
              <el-date-picker v-model="action.dueDate" type="date" value-format="YYYY-MM-DD" placeholder="截止" style="width: 140px" />
              <el-button :icon="Delete" text @click="removeAction(index)" />
            </div>
            <el-button :icon="Plus" text @click="addAction">添加跟进事项</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saveMutation.isPending.value" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.meeting-page {
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

.meeting-search {
  max-width: 320px;
}

.meeting-hint {
  padding: 40px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}

.meeting-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.meeting-card {
  @include card($radius-lg);
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 18px;
  cursor: pointer;
  transition: transform 0.16s ease;

  &:hover {
    transform: translateX(3px);
  }
}

.meeting-card__date {
  @include flex-center;
  flex-direction: column;
  width: 52px;
  height: 52px;
  border-radius: 12px;
  background: var(--brand-50);
  color: var(--brand-600);
  flex-shrink: 0;
}

.meeting-card__day {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.1;
}

.meeting-card__month {
  font-size: 11px;
}

.meeting-card__body {
  flex: 1;
  min-width: 0;

  h3 {
    font-size: 14px;
    font-weight: 600;
    @include ellipsis(1);
  }
}

.meeting-card__meta {
  display: flex;
  gap: 14px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-tertiary);

  span {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
}

.meeting-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 4px 4px 24px;
}

.meeting-detail__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;

  h2 {
    font-size: 18px;
    font-weight: 700;
  }
}

.meeting-detail__meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.meeting-detail__ops {
  display: flex;
  gap: 6px;
}

.meeting-detail__block {
  h4 {
    margin-bottom: 10px;
    font-size: 13px;
    font-weight: 600;
    color: var(--text-secondary);
  }
}

.meeting-detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.meeting-detail__content {
  border-radius: 12px;
  border: 1px solid var(--border-color);
  overflow: hidden;
}

.meeting-detail__empty {
  font-size: 12px;
  color: var(--text-tertiary);
}

.meeting-actions {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meeting-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--bg-hover);
}

.meeting-action__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
}

.meeting-action__meta {
  font-size: 11px;
  color: var(--text-tertiary);
}

.meeting-form__row {
  display: flex;
  gap: 8px;
}

.meeting-actions-edit {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.meeting-action-edit {
  display: flex;
  gap: 8px;
  width: 100%;
}
</style>
