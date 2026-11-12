<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowRight, Delete, Plus } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { calendarApi } from '../api'
import type { CalendarItem } from '@/features/dashboard/api'

const queryClient = useQueryClient()
const cursor = ref(dayjs())
const selected = ref(dayjs().format('YYYY-MM-DD'))

const weekLabels = ['一', '二', '三', '四', '五', '六', '日']

const rangeStart = computed(() => cursor.value.startOf('month').format('YYYY-MM-DD'))
const rangeEnd = computed(() => cursor.value.endOf('month').format('YYYY-MM-DD'))

const { data, isLoading } = useQuery({
  queryKey: ['calendar', rangeStart, rangeEnd],
  queryFn: () => calendarApi.events(rangeStart.value, rangeEnd.value),
  staleTime: 60_000,
})

const items = computed<CalendarItem[]>(() => data.value ?? [])

/** 按日期分组，日历格子与侧栏共用 */
const byDate = computed(() => {
  const map = new Map<string, CalendarItem[]>()
  for (const item of items.value) {
    const date = item.start.slice(0, 10)
    const list = map.get(date) ?? []
    list.push(item)
    map.set(date, list)
  }
  return map
})

const cells = computed(() => {
  const first = cursor.value.startOf('month')
  const offset = (first.day() + 6) % 7
  const start = first.subtract(offset, 'day')

  return Array.from({ length: 42 }, (_, index) => {
    const date = start.add(index, 'day')
    const key = date.format('YYYY-MM-DD')
    return {
      key,
      day: date.date(),
      inMonth: date.month() === cursor.value.month(),
      isToday: date.isSame(dayjs(), 'day'),
      events: byDate.value.get(key) ?? [],
    }
  })
})

const selectedEvents = computed(() => byDate.value.get(selected.value) ?? [])
const selectedLabel = computed(() => dayjs(selected.value).format('M月D日 dddd'))

function shiftMonth(step: number): void {
  cursor.value = cursor.value.add(step, 'month')
}

function goToday(): void {
  cursor.value = dayjs()
  selected.value = dayjs().format('YYYY-MM-DD')
}

function sourceLabel(source: string): string {
  const map: Record<string, string> = {
    task: '待办',
    event: '日程',
    anniversary: '纪念日',
    countdown: '倒数',
    exam: '考试',
    birthday: '生日',
  }
  return map[source] ?? source
}

function timeLabel(item: CalendarItem): string {
  return item.allDay ? '全天' : item.start.slice(11, 16)
}

const eventDialog = ref(false)
const eventForm = reactive({
  title: '',
  date: dayjs().format('YYYY-MM-DD'),
  startTime: '09:00',
  endTime: '10:00',
  allDay: false,
  location: '',
  description: '',
})

function openCreateEvent(date?: string): void {
  eventForm.title = ''
  eventForm.date = date ?? selected.value
  eventForm.startTime = '09:00'
  eventForm.endTime = '10:00'
  eventForm.allDay = false
  eventForm.location = ''
  eventForm.description = ''
  eventDialog.value = true
}

const createEventMutation = useMutation({
  mutationFn: () => {
    const start = eventForm.allDay
      ? `${eventForm.date}T00:00:00`
      : `${eventForm.date}T${eventForm.startTime}:00`
    const end = eventForm.allDay ? null : `${eventForm.date}T${eventForm.endTime}:00`
    return calendarApi.createEvent({
      title: eventForm.title,
      description: eventForm.description || null,
      startTime: start,
      endTime: end,
      allDay: eventForm.allDay,
      location: eventForm.location || null,
    })
  },
  onSuccess: () => {
    ElMessage.success('日程已创建')
    eventDialog.value = false
    queryClient.invalidateQueries({ queryKey: ['calendar'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  },
})

async function removeEvent(item: CalendarItem): Promise<void> {
  const ok = await ElMessageBox.confirm(`删除日程「${item.title}」？`, '确认删除', {
    type: 'warning',
  }).catch(() => false)
  if (!ok) {
    return
  }
  await calendarApi.deleteEvent(item.id)
  queryClient.invalidateQueries({ queryKey: ['calendar'] })
  ElMessage.success('已删除')
}
</script>

<template>
  <div class="calendar-page">
    <header class="page-head">
      <div>
        <h1>日历</h1>
        <p class="page-head__sub">工作日程、任务起止、考试、生日、纪念日汇聚到一张月历</p>
      </div>

      <div class="month-bar">
        <button type="button" aria-label="上个月" @click="shiftMonth(-1)">
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <strong>{{ cursor.format('YYYY年M月') }}</strong>
        <button type="button" aria-label="下个月" @click="shiftMonth(1)">
          <el-icon><ArrowRight /></el-icon>
        </button>
        <el-button size="small" text @click="goToday">今天</el-button>
      </div>

      <el-button type="primary" :icon="Plus" @click="openCreateEvent()">新建日程</el-button>
    </header>

    <div class="calendar-body">
      <section class="grid-panel">
        <div class="week-head">
          <span v-for="label in weekLabels" :key="label">{{ label }}</span>
        </div>

        <div v-if="isLoading" class="loading">
          <el-skeleton :rows="6" animated />
        </div>

        <div v-else class="grid">
          <button
            v-for="cell in cells"
            :key="cell.key"
            type="button"
            class="cell"
            :class="{
              'is-muted': !cell.inMonth,
              'is-today': cell.isToday,
              'is-selected': selected === cell.key,
            }"
            @click="selected = cell.key"
          >
            <span class="cell__day tabular">{{ cell.day }}</span>
            <span class="cell__events">
              <span
                v-for="event in cell.events.slice(0, 2)"
                :key="`${event.source}-${event.id}`"
                class="chip"
                :class="{ 'is-done': event.done }"
                :style="{ background: `${event.color}1A`, color: event.color }"
              >
                <i :style="{ background: event.color }" />
                <em>{{ event.title }}</em>
              </span>
              <span v-if="cell.events.length > 2" class="more">+{{ cell.events.length - 2 }}</span>
            </span>
          </button>
        </div>
      </section>

      <aside class="day-panel">
        <header class="day-panel__head">
          <h3>{{ selectedLabel }}</h3>
          <span>{{ selectedEvents.length }} 项</span>
        </header>

        <ul v-if="selectedEvents.length" class="day-list">
          <li v-for="event in selectedEvents" :key="`${event.source}-${event.id}`" class="day-item">
            <span class="day-item__bar" :style="{ background: event.color }" />
            <div class="day-item__body">
              <div class="day-item__title" :class="{ 'is-done': event.done }">{{ event.title }}</div>
              <div class="day-item__meta">
                <span class="day-item__tag">{{ sourceLabel(event.source) }}</span>
                <span>{{ timeLabel(event) }}</span>
              </div>
            </div>
            <el-button
              v-if="event.source === 'event'"
              text
              size="small"
              :icon="Delete"
              class="day-item__del"
              @click="removeEvent(event)"
            />
          </li>
        </ul>

        <el-empty v-else description="这一天没有安排" :image-size="70" />
        <el-button class="day-panel__add" text :icon="Plus" @click="openCreateEvent(selected)">
          在这一天新建日程
        </el-button>
      </aside>
    </div>

    <el-dialog v-model="eventDialog" title="新建日程" width="480px">
      <el-form :model="eventForm" label-width="72px">
        <el-form-item label="标题">
          <el-input v-model="eventForm.title" placeholder="如：客户拜访 / 体检" maxlength="200" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="eventForm.date" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="时间">
          <el-switch v-model="eventForm.allDay" active-text="全天" />
          <template v-if="!eventForm.allDay">
            <el-time-picker v-model="eventForm.startTime" format="HH:mm" value-format="HH:mm" style="width: 104px; margin-left: 8px" />
            <span class="event-sep">至</span>
            <el-time-picker v-model="eventForm.endTime" format="HH:mm" value-format="HH:mm" style="width: 104px" />
          </template>
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="eventForm.location" maxlength="200" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="eventForm.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="eventDialog = false">取消</el-button>
        <el-button
          type="primary"
          :loading="createEventMutation.isPending.value"
          :disabled="!eventForm.title.trim()"
          @click="createEventMutation.mutate()"
        >
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.calendar-page {
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
  gap: 6px;
  padding: 5px 10px;
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

.calendar-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 16px;
  align-items: start;

  @include respond-below(lg) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.grid-panel {
  padding: 14px 16px 16px;
  @include card($radius-xl);
}

.week-head {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-color);

  span {
    text-align: center;
    font-size: 11px;
    color: var(--text-tertiary);
  }
}

.loading {
  padding: 16px 0;
}

.grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  grid-auto-rows: 96px;
  gap: 6px;
  margin-top: 8px;
  padding: 2px;

  @include respond-below(sm) {
    grid-auto-rows: 72px;
  }
}

.cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 7px 8px;
  border: 1px solid #eff6f3;
  border-radius: 11px;
  background: var(--bg-subtle);
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  transition: background $duration-fast $ease-out;

  &:hover {
    background: var(--bg-subtle);
    border-color: var(--brand-200);
  }

  &.is-muted {
    .cell__day {
      color: var(--text-tertiary);
      opacity: 0.6;
    }
  }

  &.is-today .cell__day {
    @include flex-center;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    background: var(--brand-500);
    color: #fff;
    font-weight: 600;
  }

  &.is-selected {
    background: var(--brand-50);
    border-color: var(--brand-300, #c6efe1);
    box-shadow: 0 6px 16px rgba(53, 199, 155, 0.12);
  }
}

.cell__day {
  font-size: 12px;
  font-weight: 600;
  color: #33474b;
}

.cell__events {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  overflow: hidden;
}

.chip {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 1px 5px;
  border-radius: 5px;
  font-size: 10px;
  line-height: 1.5;
  max-width: 100%;

  i {
    width: 3px;
    height: 3px;
    border-radius: 50%;
    flex: 0 0 3px;
  }

  em {
    font-style: normal;
    @include ellipsis(1);
  }

  &.is-done em {
    text-decoration: line-through;
    opacity: 0.6;
  }
}

.more {
  padding-left: 5px;
  font-size: 9.5px;
  color: var(--text-tertiary);
}

.day-panel {
  padding: 14px 16px;
  @include card($radius-xl);
  min-height: 320px;
}

.day-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;

  h3 {
    font-size: 14px;
    font-weight: 600;
  }

  span {
    font-size: 11px;
    color: var(--text-tertiary);
  }
}

.day-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.day-item {
  display: flex;
  gap: 9px;
  padding: 9px 10px;
  border-radius: 10px;
  background: var(--bg-subtle);
}

.day-item__bar {
  width: 3px;
  border-radius: 2px;
  flex: 0 0 3px;
}

.day-item__del {
  margin-left: auto;
  opacity: 0;
  transition: opacity 0.16s ease;
}

.day-item:hover .day-item__del {
  opacity: 1;
}

.day-panel__add {
  margin-top: 10px;
  width: 100%;
}

.event-sep {
  margin: 0 6px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.day-item__title {
  font-size: 12.5px;
  color: var(--text-primary);

  &.is-done {
    color: var(--text-tertiary);
    text-decoration: line-through;
  }
}

.day-item__meta {
  display: flex;
  gap: 8px;
  margin-top: 3px;
  font-size: 10.5px;
  color: var(--text-tertiary);
}

.day-item__tag {
  padding: 0 6px;
  border-radius: 999px;
  background: var(--bg-card);
}
</style>
