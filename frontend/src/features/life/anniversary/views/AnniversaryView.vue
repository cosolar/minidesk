<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  AlarmClock,
  Calendar,
  Close,
  Collection,
  Delete,
  EditPen,
  Grid,
  List,
  Plus,
  Present,
  Star,
  Timer,
  UploadFilled,
} from '@element-plus/icons-vue'
import { useStorage } from '@vueuse/core'
import dayjs from 'dayjs'
import { Lunar } from 'lunar-javascript'
import lakeImage from '@/assets/anniversary-lake.webp'
import jarImage from '@/assets/anniv-jar.webp'
import calendarImage from '@/assets/anniv-calendar.webp'
import { uploadFile } from '@/core/http/upload'
import ImageCropDialog from '@/shared/components/ImageCropDialog.vue'
import { fileUrl } from '@/core/utils/file'
import { anniversaryApi, type AnniversaryVO, type CountdownVO } from '../api'

type FilterKey = 'all' | 'birthday' | 'special' | 'lunar'

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const FILTERS: Array<{ key: FilterKey; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'birthday', label: '生日' },
  { key: 'special', label: '特殊纪念日' },
  { key: 'lunar', label: '农历' },
]
const COUNTDOWN_COLORS = ['#7B6CF6', '#5C9BFA', '#F2789F', '#52A99C', '#E2924E']

const TYPE_META: Record<string, { label: string; emoji: string; tone: string }> = {
  birthday: { label: '生日', emoji: '🎂', tone: '#f2789f' },
  wedding: { label: '纪念日', emoji: '💍', tone: '#e2726c' },
  memorial: { label: '特殊', emoji: '🌿', tone: '#52a99c' },
  holiday: { label: '节日', emoji: '🎏', tone: '#5c9bfa' },
  trip: { label: '旅行', emoji: '🧳', tone: '#e2a24e' },
  custom: { label: '自定义', emoji: '✨', tone: '#7b6cf6' },
}

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

const activeFilter = ref<FilterKey>('all')

type ViewMode = 'list' | 'grid'
// 两块各自的列表/卡片偏好，记在本机，下次进来还是上次的样子
const annivView = useStorage<ViewMode>('minidesk:anniversary-view', 'grid')
const countdownView = useStorage<ViewMode>('minidesk:countdown-view', 'grid')
const countdownFormRef = ref<FormInstance>()

// ---------- 派生数据 ----------

function typeMeta(type: string) {
  return TYPE_META[type] ?? TYPE_META.custom!
}

function isSpecial(item: AnniversaryVO): boolean {
  return item.type !== 'birthday'
}

/** 已过的一次性纪念日排到最后，其余按剩余天数升序 */
const ordered = computed(() =>
  [...anniversaries.value].sort((a, b) => {
    const left = a.daysLeft < 0 ? Number.MAX_SAFE_INTEGER : a.daysLeft
    const right = b.daysLeft < 0 ? Number.MAX_SAFE_INTEGER : b.daysLeft
    if (left !== right) {
      return left - right
    }
    return a.title.localeCompare(b.title, 'zh-Hans-CN')
  }),
)

const visible = computed(() => {
  const list = ordered.value
  if (activeFilter.value === 'birthday') {
    return list.filter((item) => item.type === 'birthday')
  }
  if (activeFilter.value === 'special') {
    return list.filter(isSpecial)
  }
  if (activeFilter.value === 'lunar') {
    return list.filter((item) => item.lunar)
  }
  return list
})

/** 顶部只展示最近一个未来的日子；全部已过时取最近过去的那一个 */
const spotlight = computed(() => {
  const upcoming = ordered.value.find((item) => item.daysLeft >= 0)
  if (upcoming) {
    return upcoming
  }
  return [...ordered.value].sort((a, b) => b.daysLeft - a.daysLeft)[0]
})

/** 顶部倒数日只取最近一个：未到期优先，全部过期时取最近过的那一个 */
const nextCountdown = computed(() => {
  if (!countdowns.value.length) {
    return null
  }
  return [...countdowns.value].sort((a, b) => {
    const left = a.daysLeft < 0 ? Number.MAX_SAFE_INTEGER : a.daysLeft
    const right = b.daysLeft < 0 ? Number.MAX_SAFE_INTEGER : b.daysLeft
    if (left !== right) {
      return left - right
    }
    return a.targetDate.localeCompare(b.targetDate)
  })[0]
})

const stats = computed(() => {
  const all = anniversaries.value
  return [
    {
      key: 'all',
      label: '全部纪念日',
      value: all.length,
      icon: Calendar,
      tone: 'var(--brand-500)',
    },
    {
      key: 'birthday',
      label: '生日',
      value: all.filter((item) => item.type === 'birthday').length,
      icon: Present,
      tone: '#f2789f',
    },
    {
      key: 'special',
      label: '特殊纪念日',
      value: all.filter(isSpecial).length,
      icon: Star,
      tone: '#a495fa',
    },
    {
      key: 'upcoming',
      // 明确时间口径，避免「4 个生日却 0 个即将到来」看起来像算错
      label: '30 天内',
      value: all.filter((item) => item.daysLeft >= 0 && item.daysLeft <= 30).length,
      icon: Timer,
      tone: '#52a99c',
    },
    {
      key: 'countdown',
      label: '倒数日',
      value: countdowns.value.length,
      icon: AlarmClock,
      tone: '#7b6cf6',
    },
  ]
})

const progress = computed(() => {
  const item = spotlight.value
  if (!item) {
    return 0
  }
  if (!item.repeatYearly) {
    return item.daysLeft <= 0 ? 100 : 0
  }
  const next = dayjs(item.nextDate)
  const prev = next.subtract(1, 'year')
  const total = next.diff(prev, 'day')
  const passed = dayjs().startOf('day').diff(prev, 'day')
  if (total <= 0) {
    return 0
  }
  return Math.min(100, Math.max(4, Math.round((passed / total) * 100)))
})

function dateText(date: string): string {
  const day = dayjs(date)
  return day.isValid() ? `${day.format('YYYY-MM-DD')} ${WEEKDAYS[day.day()]}` : date
}

/** 倒数日的日历页：月 / 日 / 星期分开渲染 */
function monthText(date: string): string {
  const day = dayjs(date)
  return day.isValid() ? day.format('M 月') : ''
}

function dayText(date: string): string {
  const day = dayjs(date)
  return day.isValid() ? day.format('DD') : '--'
}

function weekdayText(date: string): string {
  const day = dayjs(date)
  return day.isValid() ? (WEEKDAYS[day.day()] ?? '') : ''
}

/** 倒数日右侧的大数字前缀：还有 / 已过 */
function countdownLabel(days: number): string {
  if (days === 0) {
    return ''
  }
  return days > 0 ? '还有' : '已过'
}

function lunarShortText(text: string | null): string {
  return text ? (text.startsWith('农历') ? text.slice(2) : text) : ''
}

function unitOf(days: number): string {
  if (days === 0) {
    return '天'
  }
  return days > 0 ? '天后' : '天前'
}

function numberText(days: number): string {
  return days === 0 ? '今' : String(Math.abs(days))
}

/** 焦点卡前面已经有「还有」，单位只留「天」，避免「还有 21 天后」 */
function spotUnit(days: number): string {
  return days >= 0 ? '天' : '天前'
}

/** 封面可能是本地 /api/files 地址，需要补 token */
function coverUrl(cover: string | null): string {
  return fileUrl(cover)
}

/** 封面兜底：按类型生成一层柔和渐变，避免空白格子 */
function coverStyle(type: string): Record<string, string> {
  const tone = typeMeta(type).tone
  return {
    background: `linear-gradient(145deg, color-mix(in srgb, ${tone} 26%, var(--bg-card)), color-mix(in srgb, ${tone} 9%, var(--bg-card)))`,
    color: `color-mix(in srgb, ${tone} 82%, var(--text-primary))`,
  }
}

/** 农历月日换算成对应公历日期再入库：后端按入库日期的农历月日滚动 */
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

/** 把「公历日期」的月/日直接当作农历的月/日解读，得到「农历六月十九」这类文案 */
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

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

// ---------- 表单与弹窗 ----------

const dialogVisible = ref(false)
const countdownVisible = ref(false)
const submitting = ref(false)
const uploading = ref(false)
const cropVisible = ref(false)
const cropFile = ref<File | null>(null)
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance>()
const coverInputRef = ref<HTMLInputElement>()

const form = reactive({
  title: '',
  type: 'birthday',
  person: '',
  date: dayjs().format('YYYY-MM-DD'),
  advanceDays: 3,
  lunar: false,
  note: '',
  cover: '',
  important: false,
})

const countdownForm = reactive({
  title: '',
  targetDate: dayjs().add(7, 'day').format('YYYY-MM-DD'),
  color: COUNTDOWN_COLORS[0]!,
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  date: [{ required: true, message: '请选择日期', trigger: 'change' }],
}

const countdownRules: FormRules = {
  title: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  targetDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
}

const lunarHint = computed(() => {
  if (!form.lunar) {
    return ''
  }
  const label = lunarLabel(form.date)
  return label ? `每年按 ${label} 重复` : ''
})

const dialogTitle = computed(() => (editingId.value ? '编辑纪念日' : '添加纪念日'))

function openDialog(item?: AnniversaryVO): void {
  editingId.value = item?.id ?? null
  form.title = item?.title ?? ''
  form.type = item?.type ?? 'birthday'
  form.person = item?.person ?? ''
  form.date = item?.date ?? dayjs().format('YYYY-MM-DD')
  form.advanceDays = item?.advanceDays ?? 3
  form.lunar = item?.lunar ?? false
  form.note = item?.note ?? ''
  form.cover = item?.cover ?? ''
  form.important = item?.important ?? false
  submitting.value = false
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

function closeDialog(): void {
  dialogVisible.value = false
  submitting.value = false
}

function openCountdownDialog(): void {
  countdownForm.title = ''
  countdownForm.targetDate = dayjs().add(7, 'day').format('YYYY-MM-DD')
  countdownForm.color = COUNTDOWN_COLORS[0]!
  countdownVisible.value = true
}

function pickCover(): void {
  coverInputRef.value?.click()
}

/** 选中文件后先进裁剪弹窗，不直接上传原图 */
function onCoverChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('图片不能超过 20MB')
    return
  }
  cropFile.value = file
  cropVisible.value = true
}

/** 裁剪弹窗确认：拿到已压成 WebP 的小图，再上传 */
async function onCropConfirm(file: File): Promise<void> {
  cropFile.value = null
  uploading.value = true
  try {
    const uploaded = await uploadFile(file, 'anniversary')
    form.cover = uploaded.url
    ElMessage.success('封面已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败')
  } finally {
    uploading.value = false
  }
}

const saveAnniversary = useMutation({
  mutationFn: () => {
    const payload = {
      title: form.title.trim(),
      type: form.type,
      person: form.person || null,
      // 农历模式把所选的「月/日」当农历，换算成对应公历再提交，后端据此还原农历口径
      date: form.lunar ? toSolarDate(form.date) : form.date,
      advanceDays: form.advanceDays,
      repeatYearly: true,
      lunar: form.lunar,
      note: form.note.trim() || null,
      cover: form.cover || null,
      important: form.important,
    }
    return editingId.value
      ? anniversaryApi.update(editingId.value, payload)
      : anniversaryApi.create(payload)
  },
  onSuccess: () => {
    ElMessage.success(editingId.value ? '已保存' : '已添加')
    closeDialog()
    queryClient.invalidateQueries({ queryKey: ['anniversaries'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  },
  onSettled: () => {
    submitting.value = false
  },
})

const createCountdown = useMutation({
  mutationFn: () =>
    anniversaryApi.createCountdown({
      title: countdownForm.title.trim(),
      targetDate: countdownForm.targetDate,
      color: countdownForm.color,
    }),
  onSuccess: () => {
    ElMessage.success('已添加')
    countdownVisible.value = false
    queryClient.invalidateQueries({ queryKey: ['countdowns'] })
  },
})

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  saveAnniversary.mutate()
}

async function submitCountdown(): Promise<void> {
  const valid = await countdownFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  createCountdown.mutate()
}

async function remove(item: AnniversaryVO): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`删除「${item.title}」？此操作不可恢复。`, '确认删除', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  }).catch(() => false)
  if (!confirmed) {
    return
  }
  await anniversaryApi.remove(item.id)
  ElMessage.success('已删除')
  queryClient.invalidateQueries({ queryKey: ['anniversaries'] })
  queryClient.invalidateQueries({ queryKey: ['dashboard'] })
}

async function removeCountdown(item: CountdownVO): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`删除「${item.title}」？`, '确认删除', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  }).catch(() => false)
  if (!confirmed) {
    return
  }
  await anniversaryApi.removeCountdown(item.id)
  ElMessage.success('已删除')
  queryClient.invalidateQueries({ queryKey: ['countdowns'] })
}
</script>

<template>
  <div class="anniv-page">
    <section class="anniv-hero">
      <img class="anniv-hero__photo" :src="lakeImage" alt="" aria-hidden="true" />
      <span class="anniv-hero__tint" aria-hidden="true" />
      <span class="anniv-hero__haze" aria-hidden="true" />
      <span class="anniv-hero__glow" aria-hidden="true" />
      <span class="anniv-hero__fade" aria-hidden="true" />

      <div class="anniv-hero__inner">
        <header class="anniv-hero__head">
          <div class="anniv-hero__brand">
            <span class="anniv-hero__badge">
              <el-icon><Present /></el-icon>
            </span>
            <div>
              <h1>纪念日</h1>
              <p>记录重要的日子，珍藏每一份美好回忆 <em>♡</em></p>
            </div>
          </div>
          <p class="anniv-hero__quote">有你在的日子<br />就是最好的时光</p>
        </header>

        <div class="anniv-hero__cards">
          <article v-if="spotlight" class="anniv-spot">
            <div class="anniv-spot__head">
              <el-icon><Timer /></el-icon>
              <span>
                {{ spotlight.daysLeft >= 0 ? '距离' : '已过去' }}
                <strong>{{ spotlight.title }}</strong>
                {{ spotlight.daysLeft >= 0 ? '还有' : '' }}
              </span>
              <span class="anniv-spot__type">{{ typeMeta(spotlight.type).label }}</span>
            </div>

            <div class="anniv-spot__main">
              <p class="anniv-spot__num">
                <strong class="tabular">{{ numberText(spotlight.daysLeft) }}</strong>
                <span>{{ spotUnit(spotlight.daysLeft) }}</span>
              </p>
              <p class="anniv-spot__date">
                {{ dateText(spotlight.nextDate) }}
                <span v-if="spotlight.lunarText">· {{ lunarShortText(spotlight.lunarText) }}</span>
              </p>
            </div>

            <div class="anniv-spot__foot">
              <span class="anniv-spot__track">
                <i :style="{ width: `${progress}%` }" />
              </span>
              <span class="anniv-spot__hint">一起走过更多的日子 <em>♥</em></span>
            </div>
          </article>

          <p v-else class="anniv-spot__empty">还没有纪念日，先添加一个值得记住的日子吧</p>

          <article
            v-if="nextCountdown"
            class="anniv-hero-count"
            :style="{ '--tone': nextCountdown.color || '#7b6cf6' }"
          >
            <header class="anniv-hero-count__head">
              <span class="anniv-hero-count__icon">
                <el-icon><Calendar /></el-icon>
              </span>
              <span class="anniv-hero-count__label">倒数日</span>
            </header>

            <div class="anniv-hero-count__main">
              <div class="anniv-hero-count__text">
                <h3>{{ nextCountdown.title }}</h3>
                <p>{{ dateText(nextCountdown.targetDate) }}</p>
              </div>
              <p class="anniv-hero-count__num">
                <strong class="tabular">{{ numberText(nextCountdown.daysLeft) }}</strong>
                <span>{{ unitOf(nextCountdown.daysLeft) }}</span>
              </p>
            </div>

            <span class="anniv-hero-count__rule" aria-hidden="true" />
          </article>
        </div>
      </div>

      <span class="anniv-hero__memento" aria-hidden="true">
        <img :src="jarImage" alt="" />
      </span>
    </section>

    <ul class="anniv-stats">
      <li
        v-for="item in stats"
        :key="item.key"
        class="anniv-stat"
        :class="{ 'is-countdown': item.key === 'countdown' }"
        :style="{ '--tone': item.tone }"
      >
        <span class="anniv-stat__icon">
          <el-icon><component :is="item.icon" /></el-icon>
        </span>
        <span class="anniv-stat__text">
          <strong class="tabular">{{ item.value }}</strong>
          <span>{{ item.label }}</span>
        </span>
      </li>
    </ul>

    <section class="anniv-panel">
      <header class="anniv-panel__head">
        <div class="anniv-panel__title">
          <span class="anniv-panel__icon">
            <el-icon><Collection /></el-icon>
          </span>
          <h2>我的纪念日</h2>
          <span class="anniv-panel__count">{{ visible.length }} / {{ anniversaries.length }}</span>
        </div>
        <div class="anniv-panel__tools">
          <div class="anniv-filters" role="tablist" aria-label="纪念日筛选">
            <button
              v-for="item in FILTERS"
              :key="item.key"
              type="button"
              class="anniv-filter"
              :class="{ 'is-active': activeFilter === item.key }"
              role="tab"
              :aria-selected="activeFilter === item.key"
              @click="activeFilter = item.key"
            >
              {{ item.label }}
            </button>
          </div>
          <div class="anniv-viewswitch" role="group" aria-label="显示方式">
            <button
              type="button"
              title="列表"
              :class="{ 'is-active': annivView === 'list' }"
              @click="annivView = 'list'"
            >
              <el-icon><List /></el-icon>
            </button>
            <button
              type="button"
              title="卡片"
              :class="{ 'is-active': annivView === 'grid' }"
              @click="annivView = 'grid'"
            >
              <el-icon><Grid /></el-icon>
            </button>
          </div>
          <button type="button" class="anniv-btn is-primary" @click="openDialog()">
            <el-icon><Plus /></el-icon>添加纪念日
          </button>
        </div>
      </header>

      <el-skeleton v-if="anniversaryQuery.isLoading.value" :rows="3" animated />

      <template v-else-if="visible.length">
        <div v-if="annivView === 'grid'" class="anniv-tiles">
          <article
            v-for="item in visible"
            :key="item.id"
            class="anniv-tile"
            :style="{ '--tone': typeMeta(item.type).tone }"
          >
            <div class="anniv-tile__cover" :style="item.cover ? undefined : coverStyle(item.type)">
              <img v-if="item.cover" :src="coverUrl(item.cover)" alt="" loading="lazy" />
              <span v-else class="anniv-tile__emoji">{{ typeMeta(item.type).emoji }}</span>

              <div class="anniv-tile__badges">
                <span class="anniv-tag is-type">{{ typeMeta(item.type).label }}</span>
                <span v-if="item.important" class="anniv-tag is-important">♥ 重要</span>
                <span v-if="item.lunar" class="anniv-tag is-lunar">农历</span>
              </div>

              <div class="anniv-tile__actions">
                <button type="button" class="anniv-icon-btn" title="编辑" @click.stop="openDialog(item)">
                  <el-icon><EditPen /></el-icon>
                </button>
                <button type="button" class="anniv-icon-btn is-danger" title="删除" @click.stop="remove(item)">
                  <el-icon><Delete /></el-icon>
                </button>
              </div>

              <p class="anniv-tile__days">
                <strong class="tabular">{{ numberText(item.daysLeft) }}</strong>
                <span>{{ unitOf(item.daysLeft) }}</span>
              </p>
            </div>

            <div class="anniv-tile__body">
              <h3>{{ item.title }}</h3>
              <p class="anniv-tile__note" :class="{ 'is-placeholder': !item.note }">
                {{ item.note || '还没有备注，点右上角编辑补一句' }}
              </p>
              <div class="anniv-tile__foot">
                <span>{{ dateText(item.nextDate) }}</span>
                <span v-if="item.person">{{ item.person }}</span>
              </div>
            </div>
          </article>
        </div>

        <div v-else class="anniv-list">
          <article
            v-for="item in visible"
            :key="item.id"
            class="anniv-card"
            :style="{ '--tone': typeMeta(item.type).tone }"
          >
            <div class="anniv-card__cover" :style="coverStyle(item.type)">
              <img v-if="item.cover" :src="coverUrl(item.cover)" alt="" loading="lazy" />
              <template v-else>
                <span class="anniv-card__emoji">{{ typeMeta(item.type).emoji }}</span>
                <span class="anniv-card__ring" aria-hidden="true" />
              </template>
            </div>

            <div class="anniv-card__body">
              <div class="anniv-card__title-row">
                <span class="anniv-card__accent" aria-hidden="true" />
                <h3>{{ item.title }}</h3>
                <div class="anniv-card__tags">
                  <span class="anniv-tag is-type">{{ typeMeta(item.type).label }}</span>
                  <span v-if="item.important" class="anniv-tag is-important">♥ 重要</span>
                  <span v-if="item.lunar" class="anniv-tag is-lunar">农历</span>
                </div>
              </div>
              <p class="anniv-card__note" :class="{ 'is-placeholder': !item.note }">
                {{ item.note || '还没有备注，点右侧编辑补上一句话' }}
              </p>
              <p class="anniv-card__meta">
                <span v-if="item.person">{{ item.person }}</span>
                <span v-if="item.lunarText">{{ lunarShortText(item.lunarText) }}</span>
                <span>{{ item.repeatYearly ? '每年重复' : '仅此一次' }}</span>
              </p>
            </div>

            <div class="anniv-card__side">
              <p class="anniv-card__num">
                <strong class="tabular">{{ numberText(item.daysLeft) }}</strong>
                <span>{{ unitOf(item.daysLeft) }}</span>
              </p>
              <p class="anniv-card__date">{{ dateText(item.nextDate) }}</p>
            </div>

            <div class="anniv-card__actions">
              <button type="button" class="anniv-icon-btn" title="编辑" @click="openDialog(item)">
                <el-icon><EditPen /></el-icon>
              </button>
              <button type="button" class="anniv-icon-btn is-danger" title="删除" @click="remove(item)">
                <el-icon><Delete /></el-icon>
              </button>
            </div>
          </article>
        </div>
      </template>
      <div v-else class="anniv-empty">
        <span class="anniv-empty__art">{{ activeFilter === 'all' ? '🌼' : '🔍' }}</span>
        <h3>{{ activeFilter === 'all' ? '还没有纪念日' : '这个筛选下暂时没有内容' }}</h3>
        <p>{{ activeFilter === 'all' ? '从生日、相识、旅行开始，把重要的日子记下来。' : '换个分类看看，或新增一个纪念日。' }}</p>
        <button type="button" class="anniv-btn is-primary" @click="openDialog()">
          <el-icon><Plus /></el-icon>添加纪念日
        </button>
      </div>
    </section>

    <section class="anniv-panel">
      <header class="anniv-panel__head">
        <div class="anniv-panel__title">
          <span class="anniv-panel__icon is-countdown">
            <el-icon><Timer /></el-icon>
          </span>
          <h2>倒数日</h2>
          <span class="anniv-panel__count">{{ countdowns.length }} 个</span>
        </div>
        <div class="anniv-panel__tools">
          <div class="anniv-viewswitch" role="group" aria-label="显示方式">
            <button
              type="button"
              title="列表"
              :class="{ 'is-active': countdownView === 'list' }"
              @click="countdownView = 'list'"
            >
              <el-icon><List /></el-icon>
            </button>
            <button
              type="button"
              title="卡片"
              :class="{ 'is-active': countdownView === 'grid' }"
              @click="countdownView = 'grid'"
            >
              <el-icon><Grid /></el-icon>
            </button>
          </div>
          <button type="button" class="anniv-btn" @click="openCountdownDialog">
            <el-icon><Plus /></el-icon>加倒数日
          </button>
        </div>
      </header>

      <div v-if="countdownView === 'grid' && countdowns.length" class="countdown-tiles">
        <article
          v-for="item in countdowns"
          :key="item.id"
          class="countdown-tile"
          :style="{ '--tone': item.color || '#7b6cf6' }"
        >
          <div class="countdown-tile__cover">
            <span class="countdown-tile__month">{{ monthText(item.targetDate) }}</span>
            <strong class="countdown-tile__day tabular">{{ dayText(item.targetDate) }}</strong>
            <span class="countdown-tile__week">{{ weekdayText(item.targetDate) }}</span>
            <button type="button" class="countdown-tile__del" title="删除" @click="removeCountdown(item)">
              <el-icon><Delete /></el-icon>
            </button>
          </div>
          <div class="countdown-tile__body">
            <h3>{{ item.title }}</h3>
            <p class="countdown-tile__left">
              {{ countdownLabel(item.daysLeft) }}
              <strong class="tabular">{{ numberText(item.daysLeft) }}</strong> 天
            </p>
          </div>
        </article>
      </div>

      <div v-else class="countdown-layout">
        <aside class="countdown-art">
          <div class="countdown-art__frame">
            <img :src="calendarImage" alt="倒数日插画" loading="lazy" />
          </div>
          <p class="countdown-art__text">
            把想去的远方、想见的人，<br />都数进日子里。
          </p>
        </aside>

        <div v-if="countdowns.length" class="countdown-cards">
          <article
            v-for="item in countdowns"
            :key="item.id"
            class="countdown-card"
            :style="{ '--tone': item.color || '#7b6cf6' }"
          >
            <div class="countdown-page" aria-hidden="true">
              <span class="countdown-page__rings"><i /><i /><i /></span>
              <span class="countdown-page__month">{{ monthText(item.targetDate) }}</span>
              <strong class="countdown-page__day tabular">{{ dayText(item.targetDate) }}</strong>
              <span class="countdown-page__week">{{ weekdayText(item.targetDate) }}</span>
            </div>

            <div class="countdown-body">
              <h3>{{ item.title }}</h3>
            </div>

            <p class="countdown-num">
              <span class="countdown-num__label">{{ countdownLabel(item.daysLeft) }}</span>
              <span class="countdown-num__value">
                <strong class="tabular">{{ numberText(item.daysLeft) }}</strong>
                <em>天</em>
              </span>
            </p>

            <button type="button" class="anniv-icon-btn is-danger" title="删除" @click="removeCountdown(item)">
              <el-icon><Delete /></el-icon>
            </button>
          </article>
        </div>

        <div v-else class="countdown-blank">
          <span class="countdown-blank__art">🗓️</span>
          <h3>还没有倒数日</h3>
          <p>旅行、考试、发薪日、想见的人……都可以放进来。</p>
          <button type="button" class="anniv-btn is-primary" @click="openCountdownDialog">
            <el-icon><Plus /></el-icon>加一个倒数日
          </button>
        </div>
      </div>
    </section>

    <p class="anniv-foot">把每一个普通的日子，都过成值得纪念的样子 <em>♡</em></p>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="anniv-form-cover">
          <button type="button" class="anniv-cover-picker" :style="form.cover ? undefined : coverStyle(form.type)" @click="pickCover">
            <img v-if="form.cover" :src="coverUrl(form.cover)" alt="" />
            <template v-else>
              <el-icon v-if="!uploading"><UploadFilled /></el-icon>
              <span>{{ uploading ? '上传中…' : '上传封面' }}</span>
            </template>
          </button>
          <div class="anniv-form-cover__text">
            <strong>封面</strong>
            <p>上传后可拖动裁剪，自动压缩为 WebP；不传则按类型生成柔和底纹。</p>
            <button v-if="form.cover" type="button" class="anniv-text-btn" @click="form.cover = ''">
              <el-icon><Close /></el-icon>移除封面
            </button>
          </div>
          <input ref="coverInputRef" type="file" accept="image/*" hidden @change="onCoverChange" />
        </div>

        <el-form-item label="名称" prop="title">
          <el-input v-model="form.title" placeholder="例如：小仙女生日" maxlength="40" show-word-limit />
        </el-form-item>

        <div class="anniv-form-row">
          <el-form-item label="类型">
            <el-radio-group v-model="form.type">
              <el-radio-button value="birthday">生日</el-radio-button>
              <el-radio-button value="wedding">纪念日</el-radio-button>
              <el-radio-button value="memorial">特殊</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="历法">
            <el-radio-group v-model="form.lunar">
              <el-radio-button :value="false">公历</el-radio-button>
              <el-radio-button :value="true">农历</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>

        <div class="anniv-form-row">
          <el-form-item label="日期" prop="date">
            <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="相关的人">
            <el-input v-model="form.person" placeholder="可选" maxlength="20" />
          </el-form-item>
        </div>

        <p v-if="lunarHint" class="anniv-hint">{{ lunarHint }}</p>

        <el-form-item label="备注">
          <el-input
            v-model="form.note"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="写一句想对 TA 说的话"
          />
        </el-form-item>

        <div class="anniv-form-switch">
          <div>
            <strong>标记为重要</strong>
            <p>重要纪念日在列表里显示爱心标签，提醒更容易看到。</p>
          </div>
          <el-switch v-model="form.important" />
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <ImageCropDialog
      v-model="cropVisible"
      :file="cropFile"
      title="裁剪封面"
      :output-size="640"
      @confirm="onCropConfirm"
    />

    <el-dialog v-model="countdownVisible" title="添加倒数日" width="420px" append-to-body>
      <el-form ref="countdownFormRef" :model="countdownForm" :rules="countdownRules" label-position="top">
        <el-form-item label="名称" prop="title">
          <el-input v-model="countdownForm.title" placeholder="例如：出发去大理" maxlength="40" />
        </el-form-item>
        <el-form-item label="目标日期" prop="targetDate">
          <el-date-picker v-model="countdownForm.targetDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="颜色">
          <div class="countdown-colors">
            <button
              v-for="color in COUNTDOWN_COLORS"
              :key="color"
              type="button"
              class="countdown-color"
              :class="{ 'is-active': countdownForm.color === color }"
              :style="{ background: color }"
              :aria-label="color"
              @click="countdownForm.color = color"
            />
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="countdownVisible = false">取消</el-button>
        <el-button type="primary" :loading="createCountdown.isPending.value" @click="submitCountdown">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.anniv-page {
  position: relative;
  display: flex;
  flex-direction: column;
  padding-bottom: 8px;

  // 天空蓝 + 淡紫的呼吸感底色，让玻璃卡片有可模糊的内容
  background:
    radial-gradient(520px 300px at 12% 40px, color-mix(in srgb, var(--brand-400) 14%, transparent), transparent 72%),
    radial-gradient(460px 300px at 86% 20px, color-mix(in srgb, #a495fa 14%, transparent), transparent 74%),
    radial-gradient(620px 340px at 58% 240px, color-mix(in srgb, #5c9bfa 9%, transparent), transparent 76%);
}

// ---------- Hero ----------
.anniv-hero {
  position: relative;
  margin: 0 (-1 * $content-padding);
  height: 312px;
  overflow: hidden;
  isolation: isolate;
}

.anniv-hero__photo {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 58%;
  z-index: 0;
  filter: var(--hero-photo-filter);
  opacity: var(--hero-photo-opacity);
  -webkit-mask-image: linear-gradient(180deg, #000 0%, #000 62%, rgba(0, 0, 0, 0.55) 84%, transparent 100%);
  mask-image: linear-gradient(180deg, #000 0%, #000 62%, rgba(0, 0, 0, 0.55) 84%, transparent 100%);
}

.anniv-hero__tint {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: var(--hero-tint);
  mix-blend-mode: var(--hero-tint-blend, normal);
  opacity: var(--hero-tint-opacity, 1);
}

.anniv-hero__haze {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: var(--hero-haze);
  // 这页想露出更多天空，雾气比首页淡一些；夜间主题的星点仍然保留
  opacity: 0.62;
}

.anniv-hero__glow {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background:
    radial-gradient(420px 240px at 78% 18%, color-mix(in srgb, #cbb6ff 26%, transparent), transparent 70%),
    radial-gradient(380px 220px at 22% 84%, color-mix(in srgb, #ffffff 30%, transparent), transparent 72%);
  opacity: 0.8;
}

.anniv-hero__fade {
  position: absolute;
  inset: auto 0 0 0;
  height: 110px;
  z-index: 1;
  background: linear-gradient(180deg, transparent 0%, var(--hero-fade-mid) 58%, var(--bg-page) 100%);
}

.anniv-hero__inner {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 20px;
  height: 100%;
  padding: 22px 36px 54px;
}

// 心形玻璃罐：从用户给的横幅里切出来的小卡，立在右侧填补空白
.anniv-hero__memento {
  position: absolute;
  top: 104px;
  right: 54px;
  z-index: 3;
  width: 136px;
  height: 136px;
  padding: 5px;
  border-radius: 18px;
  background: linear-gradient(150deg, rgba(255, 255, 255, 0.94), rgba(255, 255, 255, 0.6));
  box-shadow: 0 18px 36px rgba(28, 66, 118, 0.22), inset 0 1px 0 rgba(255, 255, 255, 0.9);
  transform: rotate(3.5deg);

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: 13px;
  }
}

.anniv-hero__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.anniv-hero__brand {
  display: flex;
  align-items: center;
  gap: 13px;
}

.anniv-hero__badge {
  @include flex-center;
  width: 46px;
  height: 46px;
  border-radius: 15px;
  color: #fff;
  font-size: 21px;
  background: linear-gradient(140deg, color-mix(in srgb, var(--brand-400) 88%, #fff), var(--brand-600));
  box-shadow: 0 10px 22px color-mix(in srgb, var(--brand-600) 30%, transparent);
}

.anniv-hero__brand h1 {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: 1px;
  color: var(--hero-title);
  text-shadow: 0 1px 16px rgba(10, 30, 40, 0.12);
}

.anniv-hero__brand p {
  margin-top: 6px;
  font-size: 12px;
  letter-spacing: 0.4px;
  color: var(--hero-sub);

  em {
    font-style: normal;
    color: color-mix(in srgb, #f2789f 78%, var(--hero-sub));
  }
}

.anniv-hero__quote {
  margin-top: 8px;
  padding-right: 4px;
  font-family: var(--font-display);
  font-size: 19px;
  font-style: italic;
  font-weight: 500;
  line-height: 1.85;
  letter-spacing: 0.16em;
  text-align: right;
  color: var(--hero-title);
  opacity: 0.72;
  transform: rotate(-2.5deg);
  text-shadow: 0 2px 18px rgba(255, 255, 255, 0.7);
}

// 焦点卡 + 最近的倒数日并排；右侧给玻璃罐留出 176px
.anniv-hero__cards {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  width: 100%;
  max-width: calc(100% - 176px);
}

// ---------- 焦点卡 ----------
.anniv-spot {
  flex: 0 1 470px;
  align-self: flex-start;
  width: min(470px, 100%);
  padding: 15px 20px 14px;
  border: 1px solid color-mix(in srgb, #fff 54%, transparent);
  border-radius: 20px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--surface-glass-strong) 96%, transparent),
    color-mix(in srgb, var(--brand-50) 72%, transparent)
  );
  box-shadow: 0 18px 40px rgba(24, 60, 120, 0.14), inset 0 1px 0 rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(18px) saturate(1.18);
  -webkit-backdrop-filter: blur(18px) saturate(1.18);
}

.anniv-spot__head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);

  .el-icon {
    font-size: 13px;
    color: var(--brand-600);
  }

  strong {
    color: var(--text-primary);
    font-weight: 600;
  }
}

.anniv-spot__type {
  margin-left: 2px;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 10.5px;
  color: var(--brand-700);
  background: color-mix(in srgb, var(--brand-500) 12%, transparent);
}

.anniv-spot__main {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-top: 4px;
}

.anniv-spot__num {
  display: flex;
  align-items: baseline;
  gap: 6px;
  line-height: 1;

  strong {
    font-family: var(--font-number);
    font-size: 56px;
    font-weight: 700;
    letter-spacing: -1px;
    color: var(--text-primary);
    text-shadow: 0 2px 18px rgba(255, 255, 255, 0.4);
  }

  span {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-secondary);
  }
}

.anniv-spot__date {
  margin-bottom: 8px;
  font-family: var(--font-number);
  font-size: 11.5px;
  letter-spacing: 0.3px;
  color: var(--text-tertiary);
}

.anniv-spot__foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 9px;
}

.anniv-spot__track {
  position: relative;
  flex: 1;
  height: 5px;
  border-radius: 999px;
  overflow: hidden;
  background: color-mix(in srgb, var(--brand-500) 14%, transparent);

  i {
    display: block;
    height: 100%;
    border-radius: inherit;
    background: linear-gradient(90deg, color-mix(in srgb, var(--brand-400) 80%, #fff), var(--brand-500));
    box-shadow: 0 0 12px color-mix(in srgb, var(--brand-500) 40%, transparent);
    transition: width 600ms $ease-out;
  }
}

.anniv-spot__hint {
  flex: 0 0 auto;
  font-size: 10.5px;
  color: var(--text-tertiary);

  em {
    font-style: normal;
    color: #f2789f;
  }
}

.anniv-spot__empty {
  align-self: flex-start;
  padding: 14px 18px;
  border-radius: 16px;
  font-size: 12.5px;
  color: var(--text-secondary);
  background: color-mix(in srgb, var(--surface-glass-strong) 78%, transparent);
  backdrop-filter: blur(12px);
}

// ---------- 最近的倒数日（英雄区第二张卡） ----------
.anniv-hero-count {
  --tone: #7b6cf6;
  display: flex;
  flex: 1 1 260px;
  flex-direction: column;
  gap: 12px;
  align-self: flex-start;
  max-width: 330px;
  // 和左侧焦点卡同高，底部对齐，避免压到下面的统计条
  min-height: 135px;
  padding: 15px 18px 14px;
  border: 1px solid color-mix(in srgb, #fff 54%, transparent);
  border-radius: 20px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--surface-glass-strong) 94%, transparent),
    color-mix(in srgb, var(--tone) 13%, var(--surface-glass-strong))
  );
  box-shadow: 0 18px 40px rgba(24, 60, 120, 0.14), inset 0 1px 0 rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(18px) saturate(1.18);
  -webkit-backdrop-filter: blur(18px) saturate(1.18);
}

.anniv-hero-count__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.anniv-hero-count__icon {
  @include flex-center;
  flex: 0 0 auto;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--tone) 16%, transparent);
  color: color-mix(in srgb, var(--tone) 76%, var(--text-primary));
  font-size: 13px;
}

.anniv-hero-count__label {
  font-size: 11.5px;
  letter-spacing: 0.06em;
  color: var(--text-secondary);
}

.anniv-hero-count__main {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
}

.anniv-hero-count__text {
  min-width: 0;

  h3 {
    overflow: hidden;
    font-size: 15px;
    font-weight: 650;
    line-height: 1.3;
    color: var(--text-primary);
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  p {
    margin-top: 5px;
    font-size: 11px;
    color: var(--text-tertiary);
    white-space: nowrap;
  }
}

.anniv-hero-count__num {
  display: inline-flex;
  align-items: baseline;
  flex: 0 0 auto;
  gap: 3px;

  strong {
    font-family: var(--font-number);
    font-size: 34px;
    font-weight: 700;
    line-height: 1;
    color: color-mix(in srgb, var(--tone) 68%, var(--text-primary));
    font-variant-numeric: tabular-nums;
  }

  span {
    font-size: 11px;
    color: var(--text-secondary);
  }
}

.anniv-hero-count__rule {
  width: 40px;
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--tone), transparent);
}

// ---------- 统计条 ----------
.anniv-stats {
  position: relative;
  z-index: 3;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 6px;
  margin: -34px 18px 0;
  padding: 13px 18px;
  list-style: none;
  border: 1px solid color-mix(in srgb, #fff 62%, var(--border-color));
  border-radius: 20px;
  background: color-mix(in srgb, var(--surface-glass-strong) 92%, transparent);
  box-shadow: 0 14px 34px rgba(24, 60, 120, 0.1);
  backdrop-filter: blur(18px) saturate(1.14);
  -webkit-backdrop-filter: blur(18px) saturate(1.14);
}

.anniv-stat {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 2px 8px;
  border-radius: 12px;
  transition: background $duration-fast $ease-out;

  & + & {
    border-left: 1px solid color-mix(in srgb, var(--border-color) 80%, transparent);
    padding-left: 16px;
  }

  &:hover {
    background: color-mix(in srgb, var(--brand-50) 60%, transparent);
  }

  // 倒数日与纪念日不是同一类数据，用稍强的分隔线和淡紫底提示这是另一组
  &.is-countdown {
    border-left-color: color-mix(in srgb, #7b6cf6 34%, var(--border-color));
    background: color-mix(in srgb, #7b6cf6 5%, transparent);

    &:hover {
      background: color-mix(in srgb, #7b6cf6 11%, transparent);
    }

    .anniv-stat__text span {
      color: color-mix(in srgb, #7b6cf6 52%, var(--text-tertiary));
    }
  }
}

.anniv-stat__icon {
  @include flex-center;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  border-radius: 11px;
  font-size: 16px;
  color: var(--tone);
  background: color-mix(in srgb, var(--tone) 13%, transparent);
}

.anniv-stat__text {
  display: flex;
  flex-direction: column;
  min-width: 0;

  strong {
    font-family: var(--font-number);
    font-size: 22px;
    font-weight: 700;
    line-height: 1.15;
    color: var(--text-primary);
  }

  span {
    font-size: 11px;
    color: var(--text-tertiary);
    @include ellipsis;
  }
}

// ---------- 面板 ----------
.anniv-panel {
  margin: 16px 4px 0;
  padding: 16px 18px 18px;
  border: 1px solid color-mix(in srgb, var(--border-color) 78%, #fff);
  border-radius: 22px;
  background: color-mix(in srgb, var(--bg-card) 76%, transparent);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(18px) saturate(1.12);
  -webkit-backdrop-filter: blur(18px) saturate(1.12);
}

.anniv-panel__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.anniv-panel__title {
  display: flex;
  align-items: center;
  gap: 9px;

  h2 {
    font-size: 14.5px;
    font-weight: 600;
    color: var(--text-primary);
  }
}

.anniv-panel__icon {
  @include flex-center;
  width: 26px;
  height: 26px;
  border-radius: 9px;
  font-size: 14px;
  color: var(--brand-600);
  background: color-mix(in srgb, var(--brand-500) 12%, transparent);

  &.is-countdown {
    color: #7b6cf6;
    background: color-mix(in srgb, #7b6cf6 13%, transparent);
  }
}

.anniv-panel__count {
  padding: 1px 8px;
  border-radius: 999px;
  font-family: var(--font-number);
  font-size: 10.5px;
  color: var(--text-tertiary);
  background: var(--bg-subtle);
}

.anniv-panel__tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.anniv-filters {
  display: flex;
  gap: 2px;
  padding: 3px;
  border-radius: 12px;
  background: var(--bg-subtle);
}

.anniv-filter {
  padding: 5px 11px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 11.5px;
  white-space: nowrap;
  cursor: pointer;
  transition: color $duration-fast $ease-out, background $duration-fast $ease-out;

  &:hover {
    color: var(--text-primary);
  }

  &.is-active {
    color: var(--brand-700);
    font-weight: 600;
    background: var(--bg-card);
    box-shadow: 0 2px 8px rgba(24, 60, 120, 0.08);
  }
}

.anniv-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 13px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-card);
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out,
    border-color $duration-fast $ease-out;

  &:hover {
    transform: translateY(-1px);
    border-color: color-mix(in srgb, var(--brand-400) 45%, var(--border-color));
    box-shadow: 0 8px 18px rgba(24, 60, 120, 0.1);
  }

  &.is-primary {
    border-color: transparent;
    color: #fff;
    background: linear-gradient(135deg, var(--brand-400), var(--brand-600));
    box-shadow: 0 8px 20px color-mix(in srgb, var(--brand-500) 30%, transparent);
  }
}

// ---------- 纪念日卡片 ----------
.anniv-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.anniv-card {
  position: relative;
  display: grid;
  grid-template-columns: 78px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 16px;
  padding: 13px 16px;
  border: 1px solid color-mix(in srgb, var(--border-color) 82%, transparent);
  border-radius: 18px;
  background: linear-gradient(
    120deg,
    color-mix(in srgb, var(--bg-card) 92%, transparent),
    color-mix(in srgb, var(--bg-subtle) 74%, transparent)
  );
  overflow: hidden;
  transition: transform $duration-base $ease-out, box-shadow $duration-base $ease-out,
    border-color $duration-base $ease-out;

  // 右下角一抹淡紫，避免大面积留白
  &::after {
    content: '';
    position: absolute;
    right: -40px;
    bottom: -60px;
    width: 200px;
    height: 140px;
    z-index: 0;
    border-radius: 50%;
    pointer-events: none;
    background: radial-gradient(
      closest-side,
      color-mix(in srgb, #a495fa 14%, transparent),
      transparent 100%
    );
    opacity: 0.9;
  }

  &:hover {
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--brand-400) 38%, var(--border-color));
    box-shadow: 0 16px 32px rgba(24, 60, 120, 0.1);
  }

  &:hover .anniv-card__actions {
    opacity: 1;
  }
}

.anniv-card__cover {
  position: relative;
  display: grid;
  place-items: center;
  width: 78px;
  height: 78px;
  border-radius: 15px;
  overflow: hidden;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.32);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

.anniv-card__emoji {
  position: relative;
  z-index: 1;
  font-size: 30px;
  filter: drop-shadow(0 4px 8px rgba(20, 40, 80, 0.14));
}

.anniv-card__ring {
  position: absolute;
  right: -16px;
  bottom: -20px;
  width: 58px;
  height: 58px;
  border-radius: 50%;
  background: color-mix(in srgb, #fff 34%, transparent);
}

.anniv-card__body {
  position: relative;
  z-index: 1;
  min-width: 0;
}

.anniv-card__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.anniv-card__accent {
  flex: 0 0 auto;
  width: 3px;
  height: 15px;
  border-radius: 2px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--tone) 55%, #fff), var(--tone));
}

.anniv-card__title-row h3 {
  min-width: 0;
  font-size: 14.5px;
  font-weight: 600;
  color: var(--text-primary);
  @include ellipsis;
}

.anniv-card__tags {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 5px;
}

.anniv-tag {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 10px;
  line-height: 1.7;
  color: var(--text-secondary);
  background: var(--bg-subtle);

  &.is-type {
    color: color-mix(in srgb, #5c8dff 74%, var(--text-primary));
    background: color-mix(in srgb, #5c9bfa 13%, transparent);
  }

  &.is-important {
    color: color-mix(in srgb, #f2789f 82%, var(--text-primary));
    background: color-mix(in srgb, #f2789f 14%, transparent);
  }

  &.is-lunar {
    color: color-mix(in srgb, #52a99c 82%, var(--text-primary));
    background: color-mix(in srgb, #52a99c 13%, transparent);
  }
}

.anniv-card__note {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-secondary);
  @include ellipsis(1);

  &.is-placeholder {
    color: var(--text-tertiary);
    opacity: 0.72;
  }
}

.anniv-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-top: 5px;
  font-size: 10.5px;
  color: var(--text-tertiary);

  span + span::before {
    content: '·';
    margin-right: 8px;
    opacity: 0.6;
  }
}

.anniv-card__side {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  min-width: 96px;
  padding-right: 4px;
}

.anniv-card__num {
  display: flex;
  align-items: baseline;
  gap: 4px;
  line-height: 1;

  strong {
    font-family: var(--font-number);
    font-size: 30px;
    font-weight: 700;
    letter-spacing: -0.5px;
    color: var(--brand-600);
  }

  span {
    font-size: 11px;
    font-weight: 600;
    color: var(--text-tertiary);
  }
}

.anniv-card__date {
  margin-top: 5px;
  font-family: var(--font-number);
  font-size: 10.5px;
  color: var(--text-tertiary);
  white-space: nowrap;
}

.anniv-card__actions {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  opacity: 0.72;
  transition: opacity $duration-fast $ease-out;
}

.anniv-icon-btn {
  @include flex-center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--border-color);
  border-radius: 9px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: color $duration-fast $ease-out, border-color $duration-fast $ease-out,
    background $duration-fast $ease-out;

  &:hover {
    color: var(--brand-600);
    border-color: color-mix(in srgb, var(--brand-400) 45%, var(--border-color));
    background: var(--bg-hover);
  }

  &.is-danger:hover {
    color: var(--c-alert);
    border-color: color-mix(in srgb, var(--c-alert) 40%, var(--border-color));
    background: var(--c-alert-bg);
  }
}

// ---------- 空状态 ----------
.anniv-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 26px 16px 30px;
  text-align: center;

  &__art {
    @include flex-center;
    width: 62px;
    height: 62px;
    margin-bottom: 4px;
    border-radius: 20px;
    font-size: 27px;
    background: linear-gradient(145deg, color-mix(in srgb, #a495fa 16%, var(--bg-card)), var(--bg-card));
    box-shadow: inset 0 0 0 1px color-mix(in srgb, #a495fa 20%, transparent);
  }

  h3 {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-primary);
  }

  p {
    font-size: 12px;
    color: var(--text-tertiary);
  }

  .anniv-btn {
    margin-top: 8px;
  }
}

// ---------- 倒数日 ----------
.countdown-layout {
  display: grid;
  grid-template-columns: 206px minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
}

// 左侧插画：把用户给的台历图裁成一张小卡，给整块区域定调
.countdown-art {
  display: flex;
  flex-direction: column;
  gap: 9px;
  min-height: 176px;
  padding: 9px 9px 11px;
  border: 1px solid color-mix(in srgb, #b9cdf0 26%, var(--border-color));
  border-radius: 16px;
  background: linear-gradient(
    160deg,
    color-mix(in srgb, #cfe0ff 30%, var(--bg-card)),
    color-mix(in srgb, #f0d9f4 20%, var(--bg-card))
  );
}

.countdown-art__frame {
  flex: 1;
  min-height: 112px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, #fff 70%, transparent);
  border-radius: 12px;
  box-shadow: 0 8px 18px rgba(40, 70, 120, 0.12), inset 0 1px 0 rgba(255, 255, 255, 0.6);

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
    object-position: center 46%;
  }
}

.countdown-art__text {
  font-size: 10.5px;
  line-height: 1.8;
  text-align: center;
  color: var(--text-secondary);
}

.countdown-cards {
  // 两列卡片，两行高度与左侧插画大致齐平
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  align-content: start;
}

// 条目为奇数时，最后一张占满整行，不留半格空位
.countdown-card:last-child:nth-child(odd) {
  grid-column: 1 / -1;
}

.countdown-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 13px;
  border: 1px solid color-mix(in srgb, var(--tone) 16%, var(--border-color));
  border-radius: 16px;
  background: linear-gradient(
    140deg,
    color-mix(in srgb, var(--tone) 9%, var(--bg-card)),
    color-mix(in srgb, var(--bg-card) 94%, transparent)
  );
  transition: transform $duration-base $ease-out, box-shadow $duration-base $ease-out,
    border-color $duration-base $ease-out;

  &:hover {
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--tone) 36%, var(--border-color));
    box-shadow: 0 14px 26px color-mix(in srgb, var(--tone) 16%, transparent);
  }

  &:hover .anniv-icon-btn {
    opacity: 1;
  }

  .anniv-icon-btn {
    flex: 0 0 auto;
    // 触屏没有 hover，留一点基础可见度，删得掉
    opacity: 0.55;
    transition: opacity $duration-fast $ease-out;
  }
}

// 日历页：呼应左侧台历插画，月 / 日 / 星期分三行
.countdown-page {
  position: relative;
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  width: 58px;
  height: 66px;
  padding-top: 5px;
  overflow: hidden;
  border-radius: 11px;
  color: #fff;
  background: linear-gradient(160deg, color-mix(in srgb, var(--tone) 72%, #fff), var(--tone));
  box-shadow: 0 7px 16px color-mix(in srgb, var(--tone) 26%, transparent);

  // 顶部一层柔光，做出纸面的厚度感
  &::before {
    content: '';
    position: absolute;
    inset: 0 0 auto;
    height: 52%;
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.3), transparent);
    pointer-events: none;
  }
}

.countdown-page__rings {
  position: absolute;
  top: 3px;
  display: flex;
  gap: 5px;

  i {
    width: 3px;
    height: 3px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.8);
    box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.3);
  }
}

.countdown-page__month {
  font-size: 9px;
  letter-spacing: 0.5px;
  opacity: 0.94;
}

.countdown-page__day {
  position: relative;
  font-family: var(--font-number);
  font-size: 22px;
  font-weight: 700;
  line-height: 1.05;
  letter-spacing: -0.5px;
}

.countdown-page__week {
  font-size: 8.5px;
  opacity: 0.88;
}

.countdown-body {
  flex: 1;
  min-width: 0;

  h3 {
    font-size: 13.5px;
    font-weight: 600;
    color: var(--text-primary);
    @include ellipsis;
  }
}

// 右侧剩余天数：小字「还有 / 已过」+ 大数字
.countdown-num {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  padding-right: 2px;
  line-height: 1;
}

.countdown-num__label {
  font-size: 9.5px;
  letter-spacing: 0.4px;
  color: var(--text-tertiary);
}

.countdown-num__value {
  display: flex;
  align-items: baseline;
  gap: 2px;

  strong {
    font-family: var(--font-number);
    font-size: 26px;
    font-weight: 700;
    letter-spacing: -0.5px;
    color: var(--tone);
  }

  em {
    font-style: normal;
    font-size: 10px;
    font-weight: 600;
    color: var(--text-tertiary);
  }
}

// 一条条目都没有时：插画旁边放说明，而不是一条孤零零的灰字
.countdown-blank {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 18px 16px;
  border: 1px dashed color-mix(in srgb, var(--border-strong) 80%, transparent);
  border-radius: 16px;
  text-align: center;

  &__art {
    @include flex-center;
    width: 46px;
    height: 46px;
    margin-bottom: 2px;
    border-radius: 14px;
    font-size: 21px;
    background: linear-gradient(145deg, color-mix(in srgb, #9ec2ff 22%, var(--bg-card)), var(--bg-card));
    box-shadow: inset 0 0 0 1px color-mix(in srgb, #9ec2ff 24%, transparent);
  }

  h3 {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
  }

  p {
    font-size: 11.5px;
    color: var(--text-tertiary);
  }

  .anniv-btn {
    margin-top: 6px;
  }
}

.anniv-foot {
  margin: 18px 0 4px;
  text-align: center;
  font-size: 11.5px;
  letter-spacing: 0.12em;
  color: var(--text-tertiary);
  opacity: 0.85;

  em {
    font-style: normal;
    color: #f2789f;
  }
}

// ---------- 表单 ----------
.anniv-form-cover {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
}

.anniv-cover-picker {
  @include flex-center;
  flex-direction: column;
  gap: 3px;
  width: 78px;
  height: 78px;
  border: 1px dashed color-mix(in srgb, var(--brand-400) 40%, var(--border-color));
  border-radius: 15px;
  background: var(--bg-subtle);
  color: var(--text-tertiary);
  font-size: 10.5px;
  cursor: pointer;
  overflow: hidden;
  transition: border-color $duration-fast $ease-out, transform $duration-fast $ease-out;

  .el-icon {
    font-size: 19px;
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  &:hover {
    transform: translateY(-1px);
    border-color: color-mix(in srgb, var(--brand-400) 70%, var(--border-color));
  }
}

.anniv-form-cover__text {
  strong {
    font-size: 12.5px;
    font-weight: 600;
    color: var(--text-primary);
  }

  p {
    margin-top: 3px;
    font-size: 11px;
    line-height: 1.6;
    color: var(--text-tertiary);
  }
}

.anniv-text-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-top: 5px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--c-alert);
  font-size: 11px;
  cursor: pointer;
}

.anniv-form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.anniv-hint {
  margin: -4px 0 12px;
  font-size: 11px;
  color: var(--text-tertiary);
}

.anniv-form-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--bg-subtle);

  strong {
    font-size: 12.5px;
    font-weight: 600;
    color: var(--text-primary);
  }

  p {
    margin-top: 2px;
    font-size: 11px;
    color: var(--text-tertiary);
  }
}

.countdown-colors {
  display: flex;
  gap: 8px;
}

.countdown-color {
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
  box-shadow: 0 0 0 2px transparent;
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &:hover {
    transform: scale(1.08);
  }

  &.is-active {
    box-shadow: 0 0 0 2px var(--bg-card), 0 0 0 4px currentColor;
  }
}

// 插画在深色主题下压暗，避免一张白纸糊在暗底上
html[data-theme='night'],
html[data-theme='dark'] {
  .countdown-art img,
  .anniv-hero__memento img {
    filter: brightness(0.68) saturate(0.86);
  }

  .countdown-art__frame,
  .anniv-hero__memento {
    box-shadow: 0 14px 30px rgba(0, 0, 0, 0.5);
  }
}

// 插画在深色主题下压暗，避免一张白纸糊在暗底上
html[data-theme='night'],
html[data-theme='dark'] {
  .countdown-art img,
  .anniv-hero__memento img {
    filter: brightness(0.68) saturate(0.86);
  }

  .countdown-art__frame,
  .anniv-hero__memento {
    box-shadow: 0 14px 30px rgba(0, 0, 0, 0.5);
  }
}

// ---------- 视图切换 ----------
.anniv-viewswitch {
  display: flex;
  gap: 2px;
  padding: 3px;
  border-radius: 10px;
  background: var(--bg-subtle);

  button {
    display: grid;
    place-items: center;
    width: 26px;
    height: 24px;
    padding: 0;
    border: 0;
    border-radius: 8px;
    background: transparent;
    color: var(--text-tertiary);
    font-size: 13px;
    cursor: pointer;
    transition: color $duration-fast $ease-out, background $duration-fast $ease-out;

    &:hover {
      color: var(--text-primary);
    }

    &.is-active {
      color: var(--brand-600);
      background: var(--bg-card);
      box-shadow: 0 2px 8px rgba(24, 60, 120, 0.1);
    }
  }
}

// ---------- 纪念日：卡片视图 ----------
.anniv-tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(272px, 1fr));
  gap: 14px;
}

.anniv-tile {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--border-color) 84%, transparent);
  border-radius: 18px;
  background: color-mix(in srgb, var(--bg-card) 88%, transparent);
  box-shadow: var(--shadow-card);
  transition: transform $duration-base $ease-out, box-shadow $duration-base $ease-out,
    border-color $duration-base $ease-out;

  &:hover {
    transform: translateY(-3px);
    border-color: color-mix(in srgb, var(--tone) 34%, var(--border-color));
    box-shadow: 0 18px 34px rgba(24, 60, 120, 0.12);
  }

  &:hover .anniv-tile__actions {
    opacity: 1;
  }
}

.anniv-tile__cover {
  position: relative;
  display: grid;
  place-items: center;
  aspect-ratio: 16 / 10;
  overflow: hidden;

  > img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.anniv-tile__emoji {
  font-size: 42px;
  filter: drop-shadow(0 6px 12px rgba(20, 40, 80, 0.16));
}

.anniv-tile__badges {
  position: absolute;
  top: 10px;
  left: 10px;
  display: flex;
  gap: 5px;

  .anniv-tag {
    background: rgba(255, 255, 255, 0.88);
    box-shadow: 0 2px 8px rgba(20, 50, 90, 0.1);
    backdrop-filter: blur(8px);
  }
}

.anniv-tile__actions {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  gap: 6px;
  opacity: 0.86;
  transition: opacity $duration-fast $ease-out;

  .anniv-icon-btn {
    border-color: transparent;
    background: rgba(255, 255, 255, 0.9);
    box-shadow: 0 2px 8px rgba(20, 50, 90, 0.12);
    backdrop-filter: blur(8px);
  }
}

.anniv-tile__days {
  position: absolute;
  right: 10px;
  bottom: 10px;
  display: flex;
  align-items: baseline;
  gap: 3px;
  padding: 4px 10px 5px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 6px 16px rgba(20, 50, 90, 0.14);
  backdrop-filter: blur(8px);

  strong {
    font-family: var(--font-number);
    font-size: 19px;
    font-weight: 700;
    line-height: 1;
    color: color-mix(in srgb, var(--tone) 82%, var(--text-primary));
  }

  span {
    font-size: 9.5px;
    color: var(--text-secondary);
  }
}

.anniv-tile__body {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  padding: 11px 14px 13px;

  h3 {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-primary);
    @include ellipsis;
  }
}

.anniv-tile__note {
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--text-secondary);
  @include ellipsis(2);

  &.is-placeholder {
    color: var(--text-tertiary);
    opacity: 0.82;
  }
}

.anniv-tile__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 2px;
  font-size: 10.5px;
  color: var(--text-tertiary);
  white-space: nowrap;
}

// ---------- 倒数日：卡片视图 ----------
.countdown-tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(196px, 1fr));
  gap: 12px;
}

.countdown-tile {
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--tone) 18%, var(--border-color));
  border-radius: 16px;
  background: color-mix(in srgb, var(--bg-card) 90%, transparent);
  transition: transform $duration-base $ease-out, box-shadow $duration-base $ease-out,
    border-color $duration-base $ease-out;

  &:hover {
    transform: translateY(-3px);
    border-color: color-mix(in srgb, var(--tone) 40%, var(--border-color));
    box-shadow: 0 16px 30px color-mix(in srgb, var(--tone) 18%, transparent);
  }

  &:hover .countdown-tile__del {
    background: rgba(255, 255, 255, 0.34);
  }
}

.countdown-tile__cover {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  height: 106px;
  color: #fff;
  background: linear-gradient(160deg, color-mix(in srgb, var(--tone) 70%, #fff), var(--tone));

  // 顶部柔光 + 三个线圈，呼应台历插画
  &::before {
    content: '';
    position: absolute;
    inset: 0 0 auto;
    height: 52%;
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.28), transparent);
    pointer-events: none;
  }

  &::after {
    content: '';
    position: absolute;
    top: 5px;
    left: 50%;
    width: 24px;
    height: 3px;
    border-radius: 999px;
    background: repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.82) 0 3px, transparent 3px 8px);
    transform: translateX(-50%);
  }
}

.countdown-tile__month {
  position: relative;
  font-size: 9.5px;
  letter-spacing: 0.5px;
  opacity: 0.92;
}

.countdown-tile__day {
  position: relative;
  font-family: var(--font-number);
  font-size: 34px;
  font-weight: 700;
  line-height: 1.02;
  letter-spacing: -1px;
}

.countdown-tile__week {
  position: relative;
  font-size: 9px;
  opacity: 0.86;
}

.countdown-tile__del {
  position: absolute;
  top: 7px;
  right: 7px;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  transition: background $duration-fast $ease-out;
}

.countdown-tile__body {
  padding: 10px 12px 12px;

  h3 {
    font-size: 12.5px;
    font-weight: 600;
    color: var(--text-primary);
    @include ellipsis;
  }
}

.countdown-tile__left {
  margin-top: 4px;
  font-size: 10.5px;
  color: var(--text-tertiary);

  strong {
    font-family: var(--font-number);
    font-size: 13.5px;
    font-weight: 700;
    color: var(--tone);
  }
}

// ---------- 响应式 ----------
@include respond-below(lg) {
  .anniv-hero {
    height: 300px;
  }

  .anniv-hero__inner {
    gap: 16px;
    padding: 20px 24px 52px;
  }

  .anniv-hero__quote,
  .anniv-hero__memento {
    display: none;
  }

  // 玻璃罐收起后不再需要给右侧留位
  .anniv-hero__cards {
    max-width: 100%;
  }

  .anniv-hero-count {
    flex-basis: 300px;
  }

  // 插画改成横向条，避免占掉一半宽度
  .countdown-layout {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
  }

  .countdown-art {
    flex-direction: row;
    align-items: center;
    gap: 12px;
    padding: 8px 12px;
  }

  .countdown-art__frame {
    flex: 0 0 auto;
    width: 132px;

    img {
      height: 72px;
    }
  }

  .countdown-art__text {
    text-align: left;

    br {
      display: none;
    }
  }

  .countdown-cards {
    grid-template-columns: minmax(0, 1fr);
  }

  .countdown-card:last-child:nth-child(odd) {
    grid-column: auto;
  }

  .anniv-stat {
    gap: 8px;
    padding: 2px 6px;

    & + & {
      padding-left: 12px;
    }
  }
}

@include respond-below(md) {
  // 中等宽度放不下两张卡，倒数日只在下方列表展示
  .anniv-hero-count {
    display: none;
  }

  .anniv-stats {
    // 5 项挤在一行会把「全部纪念日」截断，这里改成 3 列两行
    grid-template-columns: repeat(3, minmax(0, 1fr));
    margin: -30px 10px 0;
    padding: 10px 12px;
    gap: 2px;
  }

  // 第二行从第 4 项开始，左侧不需要分隔线；第 5 项占两格
  .anniv-stat:nth-child(4) {
    border-left: none;
  }

  .anniv-stat:last-child {
    grid-column: span 2;
    justify-content: center;
  }

  .anniv-stat__icon {
    width: 28px;
    height: 28px;
    border-radius: 9px;
    font-size: 14px;
  }

  .anniv-stat__text strong {
    font-size: 17px;
  }

  .anniv-stat__text span {
    font-size: 10px;
  }

  .anniv-panel {
    margin: 14px 0 0;
    padding: 14px 13px 16px;
    border-radius: 18px;
  }

  .anniv-panel__tools {
    width: 100%;
    justify-content: space-between;
  }
}

@include respond-below(sm) {
  .anniv-hero {
    height: auto;
    min-height: 312px;
  }

  .anniv-hero__inner {
    padding: 16px 16px 52px;
    gap: 16px;
  }

  .countdown-art {
    display: none;
  }

  .anniv-tiles {
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 10px;
  }

  .countdown-tiles {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 10px;
  }

  .anniv-hero__brand h1 {
    font-size: 23px;
  }

  .anniv-hero__badge {
    width: 40px;
    height: 40px;
    border-radius: 13px;
    font-size: 18px;
  }

  .anniv-spot {
    width: 100%;
    padding: 13px 15px 12px;
  }

  .anniv-spot__num strong {
    font-size: 46px;
  }

  .anniv-spot__date {
    font-size: 10.5px;
    text-align: right;
  }

  .anniv-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    margin: -26px 8px 0;
    gap: 6px 0;
    padding: 10px 8px;
  }

  // 5 项在双列里会剩最后一项，让它占满整行，避免右半边空着
  .anniv-stat:last-child:nth-child(odd) {
    grid-column: span 2;
    justify-content: center;
    border-left: none;
  }

  .anniv-stat {
    padding: 4px 6px;

    & + & {
      border-left: 0;
      padding-left: 6px;
    }

    &:nth-child(even) {
      border-left: 1px solid color-mix(in srgb, var(--border-color) 80%, transparent);
      padding-left: 12px;
    }
  }

  .anniv-card {
    grid-template-columns: 62px minmax(0, 1fr) auto;
    gap: 12px;
    padding: 12px;
    border-radius: 16px;
  }

  .anniv-card__cover {
    width: 62px;
    height: 62px;
    border-radius: 13px;
  }

  .anniv-card__emoji {
    font-size: 24px;
  }

  .anniv-card__num strong {
    font-size: 24px;
  }

  .anniv-card__actions {
    grid-column: 2 / -1;
    flex-direction: row;
    justify-content: flex-end;
    opacity: 1;
  }

  .anniv-card__side {
    min-width: 74px;
  }

  .anniv-form-row {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }

  .anniv-filters {
    flex: 1;
    overflow-x: auto;
    @include scrollbar(0);
  }
}
</style>
