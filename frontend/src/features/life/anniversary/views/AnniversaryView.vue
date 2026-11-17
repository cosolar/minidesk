<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { Lunar } from 'lunar-javascript'
import { anniversaryApi } from '../api'

const queryClient = useQueryClient()

const anniversaryQuery = useQuery({
  queryKey: ['anniversaries'],
  queryFn: () => anniversaryApi.list(),
})

const countdownQuery = useQuery({
  queryKey: ['countdowns'],
  queryFn: () => anniversaryApi.listCountdowns(),
})

const anniversaries = computed(() => anniversaryQuery.data.value ?? [])
const countdowns = computed(() => countdownQuery.data.value ?? [])

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'anniversary' | 'countdown'>('anniversary')

const form = reactive({
  title: '',
  type: 'birthday',
  person: '',
  date: dayjs().format('YYYY-MM-DD'),
  advanceDays: 3,
  lunar: false,
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  date: [{ required: true, message: '请选择日期', trigger: 'change' }],
}

const createAnniversary = useMutation({
  mutationFn: () =>
    anniversaryApi.create({
      title: form.title.trim(),
      type: form.type,
      person: form.person || null,
      // 农历模式把所选的「月/日」当农历，换算成对应公历再提交，后端据此还原农历口径
      date: form.lunar ? toSolarDate(form.date) : form.date,
      advanceDays: form.advanceDays,
      repeatYearly: form.type !== 'countdown',
      lunar: form.lunar,
    }),
  onSuccess: () => {
    ElMessage.success('已添加')
    closeDialog()
    queryClient.invalidateQueries({ queryKey: ['anniversaries'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  },
})

const createCountdown = useMutation({
  mutationFn: () => anniversaryApi.createCountdown({ title: form.title.trim(), targetDate: form.date }),
  onSuccess: () => {
    ElMessage.success('已添加')
    closeDialog()
    queryClient.invalidateQueries({ queryKey: ['countdowns'] })
  },
})

function openDialog(target: 'anniversary' | 'countdown'): void {
  mode.value = target
  form.title = ''
  form.person = ''
  form.type = target === 'countdown' ? 'custom' : 'birthday'
  form.date = dayjs().format('YYYY-MM-DD')
  form.lunar = false
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

function closeDialog(): void {
  dialogVisible.value = false
  submitting.value = false
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  if (mode.value === 'countdown') {
    createCountdown.mutate()
    return
  }
  createAnniversary.mutate()
}

async function remove(id: string, title: string, kind: 'anniversary' | 'countdown'): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`删除「${title}」？`, '确认删除', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  }).catch(() => false)
  if (!confirmed) {
    return
  }
  if (kind === 'countdown') {
    await anniversaryApi.removeCountdown(id)
    queryClient.invalidateQueries({ queryKey: ['countdowns'] })
  } else {
    await anniversaryApi.remove(id)
    queryClient.invalidateQueries({ queryKey: ['anniversaries'] })
  }
  ElMessage.success('已删除')
}

function daysText(days: number): string {
  if (days === 0) {
    return '就是今天'
  }
  return days > 0 ? `还有 ${days} 天` : `已过 ${Math.abs(days)} 天`
}

function typeLabel(type: string): string {
  const map: Record<string, string> = {
    birthday: '生日',
    wedding: '纪念日',
    memorial: '纪念',
    custom: '倒数',
    holiday: '节日',
    trip: '旅行',
  }
  return map[type] ?? '纪念'
}

/**
 * 把「公历日期」的月/日直接当作农历的月/日解读，得到「农历六月十九」这类文案。
 * 农历某月可能只有 29 天（如 2026 腊月无三十），换一年再算；文案与年份无关。
 */
function lunarLabel(dateStr: string): string {
  const [year = 0, month = 0, day = 0] = dateStr.split('-').map(Number)
  if (!year || !month || !day) {
    return ''
  }
  for (let offset = 0; offset < 5; offset += 1) {
    try {
      const lunar = Lunar.fromYmd(year + offset, month, day)
      return `农历${lunar.getMonthInChinese()}月${lunar.getDayInChinese()}`
    } catch {
      // 该农历年份此月没有这一天，继续尝试下一年
    }
  }
  return ''
}

/**
 * 农历月日换算成对应公历日期再入库：后端按入库日期的农历月日滚动，
 * 这样卡片文案与录入时看到的「农历X月X日」保持一致。换年只为避开无三十的月份。
 */
function toSolarDate(dateStr: string): string {
  const [year = 0, month = 0, day = 0] = dateStr.split('-').map(Number)
  for (let offset = 0; offset < 5; offset += 1) {
    try {
      const solar = Lunar.fromYmd(year + offset, month, day).getSolar()
      return `${solar.getYear()}-${pad2(solar.getMonth())}-${pad2(solar.getDay())}`
    } catch {
      // 该农历年份此月没有这一天，继续尝试下一年
    }
  }
  return dateStr
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

const lunarHint = computed(() => {
  if (mode.value !== 'anniversary' || !form.lunar) {
    return ''
  }
  const label = lunarLabel(form.date)
  return label ? `每年按 ${label} 重复` : ''
})

/** 卡片里已用「农历」标签兜底，正文去掉重复前缀 */
function lunarShortText(text: string): string {
  return text.startsWith('农历') ? text.slice(2) : text
}

/** 农历纪念日会同时展示农历与公历，公历前缀避免与农历混淆 */
function solarText(lunarText: string | null, nextDate: string): string {
  return lunarText ? `公历 ${nextDate}` : nextDate
}

/** 已过的纪念日不显示负号，用「天前」表示 */
function daysValue(days: number): number {
  return Math.abs(days)
}

function daysUnit(days: number): string {
  if (days === 0) {
    return '今天'
  }
  return days > 0 ? '天后' : '天前'
}
</script>

<template>
  <div class="anniversary-page">
    <header class="page-head">
      <div>
        <h1>纪念日</h1>
        <p class="page-head__sub">重要的日子不该靠记性</p>
      </div>
      <div class="page-head__actions">
        <el-button :icon="Plus" @click="openDialog('countdown')">加倒数日</el-button>
        <el-button type="primary" :icon="Plus" @click="openDialog('anniversary')">加纪念日</el-button>
      </div>
    </header>

    <section class="panel">
      <header class="panel__head">
        <h3>纪念日</h3>
        <span>{{ anniversaries.length }} 个</span>
      </header>

      <el-skeleton v-if="anniversaryQuery.isLoading.value" :rows="3" animated />

      <div v-else-if="anniversaries.length" class="anniv-grid">
        <article v-for="item in anniversaries" :key="item.id" class="anniv-card">
          <div class="anniv-card__days">
            <strong class="tabular">{{ daysValue(item.daysLeft) }}</strong>
            <span>{{ daysUnit(item.daysLeft) }}</span>
          </div>
          <div class="anniv-card__body">
            <div class="anniv-card__title">{{ item.title }}</div>
            <div class="anniv-card__meta">
              <span class="tag">{{ typeLabel(item.type) }}</span>
              <template v-if="item.lunarText">
                <span class="tag">农历</span>
                <span class="anniv-card__lunar">{{ lunarShortText(item.lunarText) }}</span>
              </template>
              <span>{{ solarText(item.lunarText, item.nextDate) }}</span>
              <span v-if="item.person">{{ item.person }}</span>
            </div>
          </div>
          <button class="anniv-card__delete" type="button" aria-label="删除" @click="remove(item.id, item.title, 'anniversary')">
            删除
          </button>
        </article>
      </div>

      <el-empty v-else description="还没有纪念日" :image-size="70" />
    </section>

    <section class="panel">
      <header class="panel__head">
        <h3>倒数日</h3>
        <span>{{ countdowns.length }} 个</span>
      </header>

      <ul v-if="countdowns.length" class="countdown-list">
        <li v-for="item in countdowns" :key="item.id" class="countdown-item">
          <span class="countdown-item__bar" :style="{ background: item.color || '#FF6B6B' }" />
          <span class="countdown-item__title">{{ item.title }}</span>
          <span class="countdown-item__date">{{ item.targetDate }}</span>
          <span class="countdown-item__days tabular">{{ daysText(item.daysLeft) }}</span>
          <button type="button" class="countdown-item__delete" @click="remove(item.id, item.title, 'countdown')">
            删除
          </button>
        </li>
      </ul>

      <el-empty v-else description="还没有倒数日" :image-size="70" />
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="mode === 'countdown' ? '添加倒数日' : '添加纪念日'"
      width="420px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="名称" prop="title">
          <el-input v-model="form.title" placeholder="例如：妈妈生日" maxlength="40" />
        </el-form-item>

        <el-form-item v-if="mode === 'anniversary'" label="类型">
          <el-radio-group v-model="form.type">
            <el-radio-button value="birthday">生日</el-radio-button>
            <el-radio-button value="wedding">纪念日</el-radio-button>
            <el-radio-button value="memorial">其他</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="mode === 'anniversary'" label="历法">
          <el-radio-group v-model="form.lunar">
            <el-radio-button :value="false">公历</el-radio-button>
            <el-radio-button :value="true">农历</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item :label="mode === 'countdown' ? '目标日期' : '日期'" prop="date">
          <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          <p v-if="lunarHint" class="lunar-hint">{{ lunarHint }}</p>
        </el-form-item>

        <el-form-item v-if="mode === 'anniversary'" label="相关的人">
          <el-input v-model="form.person" placeholder="可选" maxlength="20" />
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
.anniversary-page {
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

.panel {
  padding: 16px 18px;
  @include card($radius-xl);
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;

  h3 {
    font-size: 14px;
    font-weight: 600;
  }

  span {
    font-size: 11px;
    color: var(--text-tertiary);
  }
}

.anniv-grid {
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

.anniv-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: $radius-md;
  background: var(--bg-subtle);

  &:hover .anniv-card__delete {
    opacity: 1;
  }
}

.anniv-card__days {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 58px;

  strong {
    font-size: 26px;
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

.anniv-card__title {
  font-size: 13.5px;
  font-weight: 600;
}

.anniv-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
  margin-top: 5px;
  font-size: 10.5px;
  color: var(--text-tertiary);
}

.anniv-card__lunar {
  color: var(--text-secondary);
}

.lunar-hint {
  width: 100%;
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--text-tertiary);
}

.tag {
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--bg-card);
  color: var(--text-secondary);
}

.anniv-card__delete {
  position: absolute;
  top: 8px;
  right: 10px;
  opacity: 0;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  font-size: 10.5px;
  cursor: pointer;

  &:hover {
    color: var(--c-alert);
  }
}

.countdown-list {
  display: flex;
  flex-direction: column;
}

.countdown-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 6px;
  border-bottom: 1px solid var(--border-color);

  &:last-child {
    border-bottom: none;
  }

  &:hover .countdown-item__delete {
    opacity: 1;
  }
}

.countdown-item__bar {
  width: 3px;
  height: 20px;
  border-radius: 2px;
}

.countdown-item__title {
  flex: 1;
  font-size: 13px;
}

.countdown-item__date {
  font-size: 11px;
  color: var(--text-tertiary);
}

.countdown-item__days {
  min-width: 86px;
  text-align: right;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--brand-700);
}

.countdown-item__delete {
  opacity: 0;
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
