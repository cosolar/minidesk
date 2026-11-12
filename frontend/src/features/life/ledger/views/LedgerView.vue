<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft, ArrowRight, Delete, Plus } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { ledgerApi, type LedgerPayload, type LedgerVO } from '../api'

const queryClient = useQueryClient()

const month = ref(dayjs().format('YYYY-MM'))
const monthLabel = computed(() => dayjs(`${month.value}-01`).format('YYYY年M月'))

const listQuery = useQuery({
  queryKey: ['ledger', 'list', month],
  queryFn: () => ledgerApi.page({ month: month.value, size: 100 }),
})

const summaryQuery = useQuery({
  queryKey: ['ledger', 'summary', month],
  queryFn: () => ledgerApi.summary(month.value),
})

const categoryQuery = useQuery({
  queryKey: ['ledger', 'categories'],
  queryFn: () => ledgerApi.categories(),
  staleTime: 10 * 60_000,
})

const items = computed(() => listQuery.data.value?.list ?? [])
const summary = computed(() => summaryQuery.data.value)
const categories = computed(() => categoryQuery.data.value ?? [])

function shiftMonth(step: number): void {
  month.value = dayjs(`${month.value}-01`).add(step, 'month').format('YYYY-MM')
}

function yuan(cents: number | undefined): string {
  return ((cents ?? 0) / 100).toFixed(2)
}

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  type: 'expense' as 'expense' | 'income',
  amount: 0,
  categoryId: '',
  occurDate: dayjs().format('YYYY-MM-DD'),
  remark: '',
})

const filteredCategories = computed(() => categories.value.filter((item) => item.type === form.type))

const rules: FormRules = {
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
}

const createMutation = useMutation({
  mutationFn: (payload: LedgerPayload) => ledgerApi.create(payload),
  onSuccess: () => {
    ElMessage.success('已记录')
    dialogVisible.value = false
    form.amount = 0
    form.remark = ''
    invalidate()
  },
})

const removeMutation = useMutation({
  mutationFn: (id: string) => ledgerApi.remove(id),
  onSuccess: () => {
    ElMessage.success('已删除')
    invalidate()
  },
})

function invalidate(): void {
  queryClient.invalidateQueries({ queryKey: ['ledger'] })
  queryClient.invalidateQueries({ queryKey: ['dashboard'] })
}

function openCreate(type: 'expense' | 'income'): void {
  form.type = type
  form.categoryId = ''
  form.occurDate = dayjs().format('YYYY-MM-DD')
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (form.amount <= 0) {
    ElMessage.warning('金额必须大于 0')
    return
  }
  submitting.value = true
  try {
    await createMutation.mutateAsync({
      type: form.type,
      amountCents: Math.round(form.amount * 100),
      categoryId: form.categoryId || null,
      remark: form.remark || null,
      occurDate: form.occurDate,
    })
  } finally {
    submitting.value = false
  }
}

async function remove(item: LedgerVO): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    `删除这笔 ${item.type === 'expense' ? '支出' : '收入'} ¥${yuan(item.amountCents)}？`,
    '确认删除',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  ).catch(() => false)
  if (confirmed) {
    removeMutation.mutate(item.id)
  }
}

function budgetStatus(): 'normal' | 'warn' | 'danger' {
  const rate = summary.value?.budgetUsedRate ?? 0
  if (rate >= 1) {
    return 'danger'
  }
  return rate >= 0.8 ? 'warn' : 'normal'
}

function weekday(date: string): string {
  return dayjs(date).format('MM/DD')
}
</script>

<template>
  <div class="ledger-page">
    <header class="page-head">
      <div>
        <h1>记账管理</h1>
        <p class="page-head__sub">记录每一笔，心里有数</p>
      </div>
      <div class="page-head__actions">
        <el-button :icon="Plus" @click="openCreate('income')">记收入</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate('expense')">记支出</el-button>
      </div>
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

    <div class="summary">
      <div class="summary__card">
        <span class="summary__label">本月支出</span>
        <strong class="summary__value tabular">¥{{ yuan(summary?.expenseCents) }}</strong>
      </div>
      <div class="summary__card">
        <span class="summary__label">本月收入</span>
        <strong class="summary__value is-income tabular">¥{{ yuan(summary?.incomeCents) }}</strong>
      </div>
      <div class="summary__card">
        <span class="summary__label">结余</span>
        <strong class="summary__value tabular">¥{{ yuan(summary?.balanceCents) }}</strong>
      </div>

      <div class="summary__card summary__card--budget">
        <div class="budget__head">
          <span class="summary__label">预算使用</span>
          <span class="budget__text tabular">
            ¥{{ yuan(summary?.expenseCents) }} / ¥{{ yuan(summary?.budgetCents) }}
          </span>
        </div>
        <div class="budget__bar">
          <span
            :class="`is-${budgetStatus()}`"
            :style="{ width: `${Math.min((summary?.budgetUsedRate ?? 0) * 100, 100)}%` }"
          />
        </div>
        <span class="budget__rate tabular">{{ Math.round((summary?.budgetUsedRate ?? 0) * 100) }}%</span>
      </div>
    </div>

    <div class="ledger-body">
      <section class="panel panel--list">
        <header class="panel__head">
          <h3>明细</h3>
          <span class="panel__count">{{ items.length }} 笔</span>
        </header>

        <el-skeleton v-if="listQuery.isLoading.value" :rows="6" animated />

        <ul v-else-if="items.length" class="records">
          <li v-for="item in items" :key="item.id" class="record">
            <span class="record__date tabular">{{ weekday(item.occurDate) }}</span>
            <span class="record__name">{{ item.categoryName || '未分类' }}</span>
            <span class="record__remark">{{ item.remark || '' }}</span>
            <span class="record__amount tabular" :class="{ 'is-income': item.type === 'income' }">
              {{ item.type === 'income' ? '+' : '-' }}¥{{ yuan(item.amountCents) }}
            </span>
            <el-button link :icon="Delete" class="record__delete" @click="remove(item)" />
          </li>
        </ul>

        <el-empty v-else description="这个月还没有记录" />
      </section>

      <section class="panel panel--stats">
        <header class="panel__head">
          <h3>分类占比</h3>
        </header>

        <ul v-if="summary?.categories.length" class="cats">
          <li v-for="cat in summary.categories.slice(0, 6)" :key="cat.categoryId" class="cat">
            <div class="cat__row">
              <span class="cat__name">{{ cat.name }}</span>
              <span class="cat__amount tabular">¥{{ yuan(cat.amountCents) }}</span>
            </div>
            <div class="cat__bar">
              <span :style="{ width: `${Math.round(cat.ratio * 100)}%` }" />
            </div>
          </li>
        </ul>

        <el-empty v-else description="暂无数据" :image-size="70" />
      </section>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="form.type === 'expense' ? '记一笔支出' : '记一笔收入'"
      width="420px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="金额（元）" prop="amount">
          <el-input-number v-model="form.amount" :min="0" :precision="2" :step="10" style="width: 100%" />
        </el-form-item>

        <el-form-item label="分类">
          <el-select v-model="form.categoryId" placeholder="选择分类" style="width: 100%">
            <el-option v-for="cat in filteredCategories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="日期">
          <el-date-picker v-model="form.occurDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="买了什么（可选）" maxlength="200" />
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
.ledger-page {
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

.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;

  @include respond-below(lg) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @include respond-below(sm) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.summary__card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  @include card($radius-lg);
}

.summary__label {
  font-size: 11px;
  color: var(--text-tertiary);
}

.summary__value {
  font-size: 20px;
  font-weight: 700;

  &.is-income {
    color: var(--brand-600);
  }
}

.summary__card--budget {
  justify-content: center;
}

.budget__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.budget__text {
  font-size: 11px;
  color: var(--text-secondary);
}

.budget__bar {
  height: 6px;
  border-radius: 999px;
  background: var(--bg-subtle);
  overflow: hidden;

  span {
    display: block;
    height: 100%;
    border-radius: 999px;
    transition: width 500ms $ease-out;

    &.is-normal {
      background: linear-gradient(90deg, var(--brand-400), var(--brand-500));
    }

    &.is-warn {
      background: linear-gradient(90deg, #ffd28a, var(--c-warn));
    }

    &.is-danger {
      background: linear-gradient(90deg, #ffb3b3, var(--c-alert));
    }
  }
}

.budget__rate {
  font-size: 10.5px;
  color: var(--text-tertiary);
  text-align: right;
}

.ledger-body {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 16px;

  @include respond-below(lg) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.panel {
  padding: 14px 16px;
  @include card($radius-xl);
  min-height: 320px;
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;

  h3 {
    font-size: 14px;
    font-weight: 600;
  }
}

.panel__count {
  font-size: 11px;
  color: var(--text-tertiary);
}

.records {
  display: flex;
  flex-direction: column;
}

.record {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 4px;
  border-bottom: 1px solid var(--border-color);
  font-size: 12.5px;

  &:last-child {
    border-bottom: none;
  }

  &:hover .record__delete {
    opacity: 1;
  }
}

.record__date {
  width: 44px;
  color: var(--text-tertiary);
  font-size: 11px;
}

.record__name {
  width: 76px;
  color: var(--text-primary);
  font-weight: 500;
}

.record__remark {
  flex: 1;
  min-width: 0;
  color: var(--text-tertiary);
  @include ellipsis(1);
}

.record__amount {
  font-weight: 600;

  &.is-income {
    color: var(--brand-600);
  }
}

.record__delete {
  opacity: 0;
  color: var(--text-tertiary);

  &:hover {
    color: var(--c-alert);
  }
}

.cats {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 4px;
}

.cat__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 5px;
  font-size: 12px;
}

.cat__name {
  color: var(--text-secondary);
}

.cat__amount {
  font-weight: 500;
}

.cat__bar {
  height: 5px;
  border-radius: 999px;
  background: var(--bg-subtle);
  overflow: hidden;

  span {
    display: block;
    height: 100%;
    border-radius: 999px;
    background: linear-gradient(90deg, var(--brand-200), var(--brand-400));
  }
}
</style>
