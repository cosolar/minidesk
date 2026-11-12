<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { shoppingApi, type ShoppingList } from '../api'

const queryClient = useQueryClient()

const { data, isLoading } = useQuery({
  queryKey: ['shopping'],
  queryFn: () => shoppingApi.lists(),
  staleTime: 0,
})

const lists = computed(() => data.value ?? [])
const selectedId = ref<string | null>(null)
const activeList = computed<ShoppingList | null>(
  () => lists.value.find((list) => list.id === selectedId.value) ?? lists.value[0] ?? null,
)

const newItem = reactive({ name: '', quantity: 1, price: 0 })
const newListVisible = ref(false)
const newListName = ref('')

function invalidate(): void {
  queryClient.invalidateQueries({ queryKey: ['shopping'] })
}

const createList = useMutation({
  mutationFn: () => shoppingApi.createList({ name: newListName.value.trim() }),
  onSuccess: (list) => {
    newListVisible.value = false
    newListName.value = ''
    selectedId.value = list.id
    invalidate()
  },
})

const addItem = useMutation({
  mutationFn: (listId: string) =>
    shoppingApi.addItem(listId, {
      name: newItem.name.trim(),
      quantity: newItem.quantity,
      priceCents: Math.round(newItem.price * 100),
    }),
  onSuccess: () => {
    newItem.name = ''
    newItem.quantity = 1
    newItem.price = 0
    invalidate()
  },
})

const toggleItem = useMutation({
  mutationFn: (payload: { id: string; bought: boolean }) =>
    shoppingApi.toggleItem(payload.id, payload.bought),
  onSuccess: invalidate,
})

async function submitItem(): Promise<void> {
  if (!activeList.value) {
    return
  }
  if (!newItem.name.trim()) {
    ElMessage.warning('请输入商品名称')
    return
  }
  addItem.mutate(activeList.value.id)
}

async function removeItem(id: string, name: string): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`删除「${name}」？`, '确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  }).catch(() => false)
  if (confirmed) {
    await shoppingApi.removeItem(id)
    invalidate()
  }
}

async function removeList(list: ShoppingList): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`删除清单「${list.name}」及其全部商品？`, '确认删除', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  }).catch(() => false)
  if (confirmed) {
    await shoppingApi.removeList(list.id)
    selectedId.value = null
    invalidate()
  }
}

function yuan(cents: number): string {
  return (cents / 100).toFixed(2)
}
</script>

<template>
  <div class="shopping-page">
    <header class="page-head">
      <div>
        <h1>购物清单</h1>
        <p class="page-head__sub">买完勾掉，合计自动算</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="newListVisible = true">新建清单</el-button>
    </header>

    <div class="shopping-body">
      <aside class="lists-panel">
        <el-skeleton v-if="isLoading" :rows="4" animated />

        <ul v-else-if="lists.length" class="lists">
          <li
            v-for="list in lists"
            :key="list.id"
            class="list-item"
            :class="{ 'is-active': activeList?.id === list.id }"
            @click="selectedId = list.id"
          >
            <div class="list-item__name">{{ list.name }}</div>
            <div class="list-item__meta">
              <span>{{ list.boughtCount }}/{{ list.itemCount }} 已买</span>
              <span class="tabular">¥{{ yuan(list.totalCents) }}</span>
            </div>
            <button type="button" class="list-item__delete" aria-label="删除清单" @click.stop="removeList(list)">
              <el-icon><Delete /></el-icon>
            </button>
          </li>
        </ul>

        <div v-else class="lists-empty">
          <p>还没有清单</p>
          <el-button size="small" @click="newListVisible = true">创建一个</el-button>
        </div>
      </aside>

      <section v-if="activeList" class="items-panel">
        <header class="items-panel__head">
          <div>
            <h3>{{ activeList.name }}</h3>
            <span>{{ activeList.boughtCount }}/{{ activeList.itemCount }} 已购买</span>
          </div>
          <div class="items-panel__total">
            <span>合计</span>
            <strong class="tabular">¥{{ yuan(activeList.totalCents) }}</strong>
          </div>
        </header>

        <ul v-if="activeList.items.length" class="items">
          <li
            v-for="item in activeList.items"
            :key="item.id"
            class="item"
            :class="{ 'is-bought': item.bought }"
          >
            <button
              class="item__check"
              type="button"
              :aria-pressed="item.bought"
              @click="toggleItem.mutate({ id: item.id, bought: !item.bought })"
            >
              <svg v-if="item.bought" viewBox="0 0 16 16" width="11" height="11" aria-hidden="true">
                <path
                  d="M3.5 8.4 6.4 11 12.5 4.9"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </button>

            <span class="item__name">{{ item.name }}</span>
            <span class="item__qty tabular">×{{ item.quantity }}{{ item.unit || '' }}</span>
            <span class="item__price tabular">¥{{ yuan(item.priceCents * item.quantity) }}</span>
            <button type="button" class="item__delete" aria-label="删除" @click="removeItem(item.id, item.name)">
              <el-icon><Delete /></el-icon>
            </button>
          </li>
        </ul>

        <el-empty v-else description="清单还是空的" :image-size="66" />

        <div class="add-row">
          <el-input v-model="newItem.name" placeholder="商品名称" @keyup.enter="submitItem" />
          <el-input-number v-model="newItem.quantity" :min="1" :max="99" controls-position="right" style="width: 90px" />
          <el-input-number v-model="newItem.price" :min="0" :precision="2" :step="1" controls-position="right" style="width: 110px" />
          <el-button type="primary" :loading="addItem.isPending.value" @click="submitItem">添加</el-button>
        </div>
      </section>

      <el-empty v-else description="左侧选择一个清单" />
    </div>

    <el-dialog v-model="newListVisible" title="新建清单" width="360px" append-to-body>
      <el-input v-model="newListName" placeholder="例如：本周采购" maxlength="40" @keyup.enter="createList.mutate()" />
      <template #footer>
        <el-button @click="newListVisible = false">取消</el-button>
        <el-button type="primary" @click="createList.mutate()">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.shopping-page {
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

.shopping-body {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
  align-items: start;

  @include respond-below(md) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.lists-panel {
  padding: 12px;
  @include card($radius-xl);
  min-height: 300px;
}

.lists {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.list-item {
  position: relative;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background $duration-fast $ease-out;

  &:hover {
    background: var(--bg-hover);

    .list-item__delete {
      opacity: 1;
    }
  }

  &.is-active {
    background: var(--brand-50);

    .list-item__name {
      color: var(--brand-700);
      font-weight: 600;
    }
  }
}

.list-item__name {
  font-size: 13px;
}

.list-item__meta {
  display: flex;
  justify-content: space-between;
  margin-top: 3px;
  font-size: 10.5px;
  color: var(--text-tertiary);
}

.list-item__delete {
  position: absolute;
  top: 8px;
  right: 8px;
  opacity: 0;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;

  &:hover {
    color: var(--c-alert);
  }
}

.lists-empty {
  @include flex-center;
  flex-direction: column;
  gap: 10px;
  padding: 40px 0;
  color: var(--text-tertiary);
  font-size: 12px;
}

.items-panel {
  padding: 16px 18px;
  @include card($radius-xl);
  min-height: 300px;
}

.items-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color);

  h3 {
    font-size: 14.5px;
    font-weight: 600;
  }

  span {
    font-size: 11px;
    color: var(--text-tertiary);
  }
}

.items-panel__total {
  display: flex;
  align-items: baseline;
  gap: 6px;

  span {
    font-size: 11px;
  }

  strong {
    font-size: 20px;
    color: var(--brand-700);
  }
}

.items {
  display: flex;
  flex-direction: column;
}

.item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--border-color);

  &:last-child {
    border-bottom: none;
  }

  &:hover .item__delete {
    opacity: 1;
  }

  &.is-bought {
    .item__name,
    .item__qty,
    .item__price {
      color: var(--text-tertiary);
      text-decoration: line-through;
    }
  }
}

.item__check {
  @include flex-center;
  width: 17px;
  height: 17px;
  flex: 0 0 17px;
  padding: 0;
  border-radius: 5px;
  border: 1.4px solid var(--border-strong);
  background: transparent;
  color: #fff;
  cursor: pointer;

  .is-bought & {
    background: var(--brand-500);
    border-color: var(--brand-500);
  }
}

.item__name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  @include ellipsis(1);
}

.item__qty {
  font-size: 11.5px;
  color: var(--text-tertiary);
}

.item__price {
  min-width: 70px;
  text-align: right;
  font-size: 12.5px;
}

.item__delete {
  opacity: 0;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;

  &:hover {
    color: var(--c-alert);
  }
}

.add-row {
  display: flex;
  gap: 8px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed var(--border-color);

  @include respond-below(sm) {
    flex-wrap: wrap;
  }
}
</style>
