/// 统一的 Markdown 编辑器（ByteMD）。
/// 全站 Markdown 场景只允许用这个组件，禁止直接引 bytemd 或其它编辑器。
<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { Editor as ByteMdEditor, Viewer as ByteMdViewer } from '@bytemd/vue-next'
import zhHans from 'bytemd/locales/zh_Hans.json'
import 'bytemd/dist/index.css'
import { markdownPlugins } from '@/shared/markdown/plugins'

interface Props {
  modelValue: string
  height?: number | string
  /** ir：单栏编辑；sv：源码 + 实时预览分屏；wysiwyg：ByteMD 无该模式，按单栏处理 */
  mode?: 'ir' | 'sv' | 'wysiwyg'
  placeholder?: string
  readonly?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  height: 520,
  mode: 'ir',
  placeholder: '开始记录…',
  readonly: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  ready: []
}>()

const locale = zhHans as Record<string, string>

const host = ref<HTMLElement>()
const bytemdMode = computed<'split' | 'tab'>(() => (props.mode === 'sv' ? 'split' : 'tab'))
const heightCss = computed(() =>
  typeof props.height === 'number' ? `${props.height}px` : props.height,
)

function onChange(value: string): void {
  emit('update:modelValue', value)
}

function focus(): void {
  // ByteMD 底层是 CodeMirror，光标落在一个隐藏 textarea 上
  host.value?.querySelector<HTMLTextAreaElement>('.CodeMirror textarea')?.focus()
}

onMounted(() => {
  void nextTick(() => emit('ready'))
})

defineExpose({
  focus,
  getValue: () => props.modelValue,
})
</script>

<template>
  <div ref="host" class="md-editor" :class="{ 'is-readonly': readonly }" :style="{ height: heightCss }">
    <ByteMdViewer v-if="readonly" :value="modelValue" :plugins="markdownPlugins" />
    <ByteMdEditor
      v-else
      :value="modelValue"
      :mode="bytemdMode"
      :plugins="markdownPlugins"
      :locale="locale"
      :placeholder="placeholder"
      @change="onChange"
    />
  </div>
</template>

<style scoped lang="scss">
.md-editor {
  display: flex;
  flex-direction: column;
  min-height: 0;

  // 只读预览给的是固定高度，超出要能自己滚
  &.is-readonly {
    overflow-y: auto;
  }

  // @bytemd/vue-next 会在组件外部再包一层无类名的 div，这里把它也撑满，否则高度按内容顶开
  > :deep(div) {
    display: flex;
    flex: 1;
    min-height: 0;
    flex-direction: column;
  }

  :deep(.bytemd) {
    flex: 1;
    min-height: 0;
    height: auto;
    border-color: var(--border-color);
    border-radius: $radius-md;
    background: var(--bg-card);
    color: var(--text-primary);

    // ByteMD 默认给 body 固定减 58px，改成 flex 布局更稳
    display: flex;
    flex-direction: column;
  }

  :deep(.bytemd-toolbar) {
    flex: 0 0 auto;
    border-bottom-color: var(--border-color);
    background: var(--bg-subtle);
  }

  :deep(.bytemd-toolbar-icon:hover) {
    background: var(--bg-hover);
  }

  :deep(.bytemd-toolbar-tab-active) {
    color: var(--brand-600);
  }

  :deep(.bytemd-body) {
    flex: 1;
    min-height: 0;
    height: auto;
  }

  :deep(.CodeMirror) {
    background: transparent;
    color: var(--text-primary);
    font-family: var(--font-sans);
    font-size: 13.5px;
    line-height: 1.75;
  }

  // ByteMD 默认把编辑正文限制在 800px 并居中（阅读宽度），卡片一宽就只剩中间一条细栏
  :deep(.bytemd-editor .CodeMirror .CodeMirror-lines) {
    max-width: none;
    margin: 0;
    padding: 14px 18px 28px;
  }

  // 行元素自带左右 4% 内边距，会再缩一圈，这里清掉
  :deep(.bytemd-editor .CodeMirror pre.CodeMirror-line),
  :deep(.bytemd-editor .CodeMirror pre.CodeMirror-line-like) {
    padding: 0;
  }

  :deep(.CodeMirror-cursor) {
    border-left-color: var(--text-primary);
  }

  :deep(.CodeMirror-selected) {
    background: var(--brand-100);
  }

  :deep(.CodeMirror pre.CodeMirror-placeholder) {
    color: var(--text-tertiary);
  }

  :deep(.bytemd-preview) {
    background: transparent;
  }

  :deep(.bytemd-split .bytemd-preview) {
    border-left-color: var(--border-color);
  }

  // 预览栏同样默认 800px 居中，宽卡片里会与编辑栏左右不齐
  :deep(.bytemd-preview .markdown-body) {
    max-width: none;
    margin: 0;
    padding: 18px 5% 32px;
  }

  // ByteMD 的全屏只给根节点加了 position: fixed，没有背景也没有层级：
  // 下面的页面会直接透上来、工具栏压在顶栏上，看起来像「整页乱掉」。
  // 这里补一层不透明底 + 足够的 z-index，并把工具栏/正文包成一张完整卡片。
  :deep(.bytemd-fullscreen) {
    position: fixed;
    inset: 0;
    z-index: 3000;
    display: flex;
    flex-direction: column;
    padding: 16px 20px;
    border: none;
    border-radius: 0;
    background: var(--bg-page);
    box-shadow: none;

    .bytemd-toolbar {
      border: 1px solid var(--border-color);
      border-bottom: none;
      border-radius: $radius-md $radius-md 0 0;
      background: var(--bg-subtle);
    }

    .bytemd-body {
      flex: 1;
      min-height: 0;
      overflow: hidden;
      border: 1px solid var(--border-color);
      border-top: none;
      border-radius: 0 0 $radius-md $radius-md;
      background: var(--bg-card);
    }

    .bytemd-editor,
    .bytemd-preview {
      background: var(--bg-card);
    }
  }
}
</style>
