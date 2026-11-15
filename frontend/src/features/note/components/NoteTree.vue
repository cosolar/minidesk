<script setup lang="ts">
/// 笔记库左侧目录树：工作 / 学习 / 生活为固定根，支持多级目录、右键菜单与拖拽移动。
import { computed, ref, watch } from 'vue'
import {
  ArrowRight,
  Briefcase,
  Coffee,
  Document,
  Folder,
  FolderOpened,
  MoreFilled,
  Reading,
  StarFilled,
} from '@element-plus/icons-vue'
import type { NoteFolder, NoteModule, NoteOutline } from '../api'

interface Props {
  folders: NoteFolder[]
  notes: NoteOutline[]
  /** 当前选中节点：root:work / folder:12 / note:34 */
  selectedKey: string | null
}

interface Row {
  key: string
  kind: 'root' | 'folder' | 'note'
  depth: number
  module: NoteModule
  label: string
  /** 根目录与目录显示子树内的文档总数 */
  count: number
  folder?: NoteFolder
  note?: NoteOutline
}

const props = defineProps<Props>()

const emit = defineEmits<{
  selectFolder: [payload: { module: NoteModule; folderId: string | null }]
  selectNote: [note: NoteOutline]
  createFolder: [payload: { module: NoteModule; parentId: string | null }]
  createNote: [payload: { module: NoteModule; folderId: string | null }]
  renameFolder: [folder: NoteFolder]
  deleteFolder: [folder: NoteFolder]
  deleteNote: [note: NoteOutline]
  moveNote: [payload: { noteId: string; folderId: string | null; module: NoteModule }]
  moveFolder: [payload: { folderId: string; parentId: string | null }]
  moveRequest: [note: NoteOutline]
}>()

const ROOTS: { module: NoteModule; label: string }[] = [
  { module: 'work', label: '工作' },
  { module: 'study', label: '学习' },
  { module: 'life', label: '生活' },
]

const expanded = ref<Set<string>>(new Set(ROOTS.map((root) => `root:${root.module}`)))
let foldersInitialized = false

watch(
  () => props.folders,
  (folders) => {
    if (foldersInitialized || !folders.length) {
      return
    }
    // 首次拿到目录时全部展开，进入页面就能看到文档
    foldersInitialized = true
    folders.forEach((folder) => expanded.value.add(`folder:${folder.id}`))
  },
  { immediate: true },
)

const childFolders = computed(() => {
  const map = new Map<string, NoteFolder[]>()
  props.folders.forEach((folder) => {
    const parentKey = folder.parentId ? `folder:${folder.parentId}` : `root:${folder.module}`
    const bucket = map.get(parentKey) ?? []
    bucket.push(folder)
    map.set(parentKey, bucket)
  })
  return map
})

const notesByBucket = computed(() => {
  const map = new Map<string, NoteOutline[]>()
  props.notes.forEach((note) => {
    const bucketKey = note.folderId ? `folder:${note.folderId}` : `root:${note.module}`
    const bucket = map.get(bucketKey) ?? []
    bucket.push(note)
    map.set(bucketKey, bucket)
  })
  return map
})

/** 每个节点子树内的文档数，一次算完避免模板里递归 */
const counts = computed(() => {
  const map = new Map<string, number>()
  const walk = (key: string, guard: Set<string>): number => {
    if (guard.has(key)) {
      return 0
    }
    guard.add(key)
    let total = notesByBucket.value.get(key)?.length ?? 0
    for (const child of childFolders.value.get(key) ?? []) {
      total += walk(`folder:${child.id}`, guard)
    }
    map.set(key, total)
    return total
  }
  ROOTS.forEach((root) => walk(`root:${root.module}`, new Set<string>()))
  return map
})

const rows = computed<Row[]>(() => {
  const list: Row[] = []
  const pushChildren = (parentKey: string, depth: number): void => {
    for (const folder of childFolders.value.get(parentKey) ?? []) {
      const key = `folder:${folder.id}`
      list.push({
        key,
        kind: 'folder',
        depth,
        module: folder.module,
        label: folder.name,
        count: counts.value.get(key) ?? 0,
        folder,
      })
      if (expanded.value.has(key)) {
        pushChildren(key, depth + 1)
      }
    }
    for (const note of notesByBucket.value.get(parentKey) ?? []) {
      list.push({
        key: `note:${note.id}`,
        kind: 'note',
        depth,
        module: note.module,
        label: note.title || '无标题',
        count: 0,
        note,
      })
    }
  }
  for (const root of ROOTS) {
    const key = `root:${root.module}`
    list.push({
      key,
      kind: 'root',
      depth: 0,
      module: root.module,
      label: root.label,
      count: counts.value.get(key) ?? 0,
    })
    if (expanded.value.has(key)) {
      pushChildren(key, 1)
    }
  }
  return list
})

const rootIcons = {
  work: Briefcase,
  study: Reading,
  life: Coffee,
}

function rowIcon(row: Row) {
  if (row.kind === 'root') {
    return rootIcons[row.module]
  }
  if (row.kind === 'folder') {
    return expanded.value.has(row.key) ? FolderOpened : Folder
  }
  return Document
}

function isExpanded(row: Row): boolean {
  return expanded.value.has(row.key)
}

function toggle(row: Row): void {
  const next = new Set(expanded.value)
  if (next.has(row.key)) {
    next.delete(row.key)
  } else {
    next.add(row.key)
  }
  expanded.value = next
}

function onRowClick(row: Row): void {
  if (row.kind === 'note' && row.note) {
    emit('selectNote', row.note)
    return
  }
  emit('selectFolder', {
    module: row.module,
    folderId: row.kind === 'folder' && row.folder ? row.folder.id : null,
  })
}

function onRowCommand(command: unknown, row: Row): void {
  onCommand(String(command), row)
}

function onCommand(command: string, row: Row): void {
  if (row.kind === 'note' && row.note) {
    if (command === 'move') {
      emit('moveRequest', row.note)
    } else if (command === 'delete') {
      emit('deleteNote', row.note)
    }
    return
  }
  const module = row.module
  const parentId = row.kind === 'folder' && row.folder ? row.folder.id : null
  if (command === 'new-folder') {
    emit('createFolder', { module, parentId })
  } else if (command === 'new-note') {
    emit('createNote', { module, folderId: parentId })
  } else if (command === 'rename' && row.folder) {
    emit('renameFolder', row.folder)
  } else if (command === 'delete' && row.folder) {
    emit('deleteFolder', row.folder)
  }
}

type DragPayload = { type: 'note' | 'folder'; id: string }

const dragging = ref<DragPayload | null>(null)
const dragOverKey = ref<string | null>(null)

function onDragStart(row: Row, event: DragEvent): void {
  if (row.kind === 'note' && row.note) {
    dragging.value = { type: 'note', id: row.note.id }
  } else if (row.kind === 'folder' && row.folder) {
    dragging.value = { type: 'folder', id: row.folder.id }
  } else {
    return
  }
  event.dataTransfer?.setData('text/plain', row.key)
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

/** 只有根目录和目录可以接收放置；文档行不参与 */
function canDrop(row: Row): boolean {
  return row.kind === 'root' || row.kind === 'folder'
}

function onDragOver(row: Row, event: DragEvent): void {
  if (!dragging.value || !canDrop(row)) {
    return
  }
  event.preventDefault()
  dragOverKey.value = row.key
}

function onDragLeave(row: Row): void {
  if (dragOverKey.value === row.key) {
    dragOverKey.value = null
  }
}

function onDrop(row: Row, event: DragEvent): void {
  const payload = dragging.value
  dragOverKey.value = null
  dragging.value = null
  if (!payload || !canDrop(row)) {
    return
  }
  event.preventDefault()
  const targetFolderId = row.kind === 'folder' && row.folder ? row.folder.id : null
  if (payload.type === 'note') {
    emit('moveNote', { noteId: payload.id, folderId: targetFolderId, module: row.module })
    return
  }
  if (payload.id === targetFolderId) {
    return
  }
  emit('moveFolder', { folderId: payload.id, parentId: targetFolderId })
}

function onDragEnd(): void {
  dragging.value = null
  dragOverKey.value = null
}
</script>

<template>
  <ul class="note-tree" role="tree">
    <li v-for="row in rows" :key="row.key" class="note-tree__row">
      <div
        class="note-tree__item"
        :class="[
          `is-${row.module}`,
          `is-${row.kind}`,
          {
            'is-active': row.key === selectedKey,
            'is-drop': dragOverKey === row.key,
            'is-pinned': row.kind === 'note' && row.note?.pinned,
          },
        ]"
        :style="{ paddingLeft: `${6 + row.depth * 14}px` }"
        :draggable="row.kind !== 'root'"
        @click="onRowClick(row)"
        @dragstart="onDragStart(row, $event)"
        @dragend="onDragEnd"
        @dragover="onDragOver(row, $event)"
        @dragleave="onDragLeave(row)"
        @drop="onDrop(row, $event)"
      >
        <button
          v-if="row.kind !== 'note'"
          type="button"
          class="note-tree__caret"
          :class="{ 'is-open': isExpanded(row) }"
          :aria-label="isExpanded(row) ? '折叠' : '展开'"
          @click.stop="toggle(row)"
        >
          <el-icon><ArrowRight /></el-icon>
        </button>
        <span v-else class="note-tree__caret is-placeholder" />

        <span class="note-tree__icon">
          <el-icon><component :is="rowIcon(row)" /></el-icon>
        </span>
        <span class="note-tree__label">{{ row.label }}</span>
        <span v-if="row.kind === 'note' && row.note?.pinned" class="note-tree__pin">
          <el-icon><StarFilled /></el-icon>
        </span>
        <span v-if="row.kind !== 'note'" class="note-tree__count">{{ row.count }}</span>

        <el-dropdown trigger="click" :hide-on-click="true" @command="onRowCommand($event, row)">
          <button type="button" class="note-tree__more" aria-label="更多操作" @click.stop>
            <el-icon><MoreFilled /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <template v-if="row.kind === 'note'">
                <el-dropdown-item command="move">移动到…</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </template>
              <template v-else>
                <el-dropdown-item command="new-folder">新建子目录</el-dropdown-item>
                <el-dropdown-item command="new-note">新建文档</el-dropdown-item>
                <el-dropdown-item v-if="row.kind === 'folder'" command="rename" divided>
                  重命名
                </el-dropdown-item>
                <el-dropdown-item v-if="row.kind === 'folder'" command="delete">删除目录</el-dropdown-item>
              </template>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </li>
  </ul>
</template>

<style scoped lang="scss">
.note-tree {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin: 0;
  padding: 4px 6px 12px;
  list-style: none;
}

.note-tree__item {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding-right: 4px;
  border-radius: $radius-sm;
  color: var(--text-secondary);
  cursor: pointer;
  user-select: none;
  transition: background $duration-fast, color $duration-fast;

  &:hover {
    background: var(--bg-hover);
    color: var(--text-primary);
  }

  &.is-active {
    background: color-mix(in srgb, var(--brand-500) 14%, transparent);
    color: var(--text-primary);
    font-weight: 600;
  }

  &.is-drop {
    background: color-mix(in srgb, var(--brand-500) 20%, transparent);
    box-shadow: inset 0 0 0 1px var(--brand-500);
  }

  &.is-root {
    margin-top: 6px;
    font-weight: 600;
    color: var(--text-primary);
  }

  &.is-note {
    color: var(--text-secondary);
  }

  &.is-note.is-pinned {
    color: var(--text-primary);
  }

  &[draggable='true'] {
    cursor: grab;
  }
}

.note-tree__caret {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;

  &:hover {
    color: var(--text-primary);
  }

  &.is-placeholder {
    cursor: default;
  }

  :deep(.el-icon) {
    transition: transform $duration-fast;
    transform: rotate(0deg);
  }

  &.is-open :deep(.el-icon) {
    transform: rotate(90deg);
  }
}

.note-tree__icon {
  display: inline-flex;
  align-items: center;
  color: var(--text-tertiary);

  .is-work & {
    color: var(--accent-amber, #d97706);
  }

  .is-study & {
    color: var(--brand-500);
  }

  .is-life & {
    color: var(--accent-rose, #db2777);
  }
}

.note-tree__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-tree__pin {
  display: inline-flex;
  color: var(--brand-500);
  font-size: 11px;
}

.note-tree__count {
  min-width: 18px;
  color: var(--text-tertiary);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.note-tree__more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: $radius-sm;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  opacity: 0;
  transition: opacity $duration-fast, background $duration-fast;

  &:hover {
    background: var(--bg-subtle);
    color: var(--text-primary);
  }
}

.note-tree__item:hover .note-tree__more,
.note-tree__item.is-active .note-tree__more {
  opacity: 1;
}
</style>
