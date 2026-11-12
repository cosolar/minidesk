<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type Vditor from 'vditor'

interface Props {
  modelValue: string
  height?: number
  placeholder?: string
  readonly?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  height: 520,
  placeholder: '开始记录…',
  readonly: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  ready: []
}>()

const host = ref<HTMLElement>()
let instance: Vditor | null = null
let destroyed = false

function isDark(): boolean {
  return document.documentElement.dataset.theme === 'dark'
}

onMounted(async () => {
  // 动态加载：Vditor 体积较大，不进入首屏包
  const [{ default: VditorClass }] = await Promise.all([
    import('vditor'),
    import('vditor/dist/index.css'),
  ])
  if (destroyed || !host.value) {
    return
  }

  instance = new VditorClass(host.value, {
    mode: 'ir',
    height: props.height,
    placeholder: props.placeholder,
    theme: isDark() ? 'dark' : 'classic',
    icon: 'material',
    cache: { enable: false },
    toolbar: [
      'headings',
      'bold',
      'italic',
      'strike',
      '|',
      'list',
      'ordered-list',
      'check',
      'quote',
      '|',
      'code',
      'link',
      'table',
      '|',
      'undo',
      'redo',
      '|',
      'edit-mode',
      'fullscreen',
    ],
    input: (value: string) => {
      emit('update:modelValue', value)
    },
    after: () => {
      instance?.setValue(props.modelValue || '')
      emit('ready')
    },
  })
})

// 外部切换笔记时同步内容，避免 setValue 触发 input 造成回环
watch(
  () => props.modelValue,
  (value) => {
    if (instance && instance.getValue() !== value) {
      instance.setValue(value || '')
    }
  },
)

// 跟随全局主题切换
watch(
  () => document.documentElement.dataset.theme,
  () => {
    instance?.setTheme(isDark() ? 'dark' : 'classic')
  },
)

onBeforeUnmount(() => {
  destroyed = true
  instance?.destroy()
  instance = null
})
</script>

<template>
  <div ref="host" class="md-editor" />
</template>

<style scoped lang="scss">
.md-editor {
  :deep(.vditor) {
    border-radius: $radius-md;
    border-color: var(--border-color);
    background: var(--bg-card);
  }

  :deep(.vditor-toolbar) {
    border-bottom-color: var(--border-color);
    background: var(--bg-subtle);
  }

  :deep(.vditor-reset) {
    font-size: 13.5px;
    line-height: 1.75;
  }
}
</style>
