/**
 * 全站 Markdown 渲染插件集合。
 *
 * 编辑器（ByteMD Editor / Viewer）与只读预览共用这一份，
 * 避免两处能力不一致（公式、图片灯箱、代码高亮）。
 * 插件实例必须稳定：放到模块级常量，不能放进组件里随渲染重建。
 */
import gfm from '@bytemd/plugin-gfm'
import highlight from '@bytemd/plugin-highlight'
import math from '@bytemd/plugin-math'
import mediumZoom from '@bytemd/plugin-medium-zoom'
import type { BytemdPlugin } from 'bytemd'
import type { Root } from 'mdast'
import 'katex/dist/katex.min.css'

interface MdNode {
  type: string
  value?: string
  children?: MdNode[]
}

/**
 * 把段落里的单个换行渲染成 &lt;br&gt;（等价 GitHub 的 breaks 选项）。
 *
 * CommonMark 规定段落内换行折叠为空格，用户按回车写的分行预览时会连成一行。
 * 这里只拆 text 节点：代码块（code）与行内代码（inlineCode）没有 children，不受影响；
 * 原本用两个空格或反斜杠产生的硬换行已经是 break 节点，不会重复插入。
 */
function splitSoftBreaks(node: MdNode): void {
  if (!node.children?.length) {
    return
  }
  const next: MdNode[] = []
  for (const child of node.children) {
    if (child.type === 'text' && child.value?.includes('\n')) {
      child.value.split('\n').forEach((segment, index) => {
        if (index > 0) {
          next.push({ type: 'break' })
        }
        if (segment) {
          next.push({ type: 'text', value: segment })
        }
      })
      continue
    }
    splitSoftBreaks(child)
    next.push(child)
  }
  node.children = next
}

const breaksPlugin = (processor: Parameters<NonNullable<BytemdPlugin['remark']>>[0]) =>
  processor.use(() => (tree: Root) => splitSoftBreaks(tree as unknown as MdNode))

export const markdownPlugins: BytemdPlugin[] = [
  gfm(),
  highlight(),
  // 公式：$行内$ / $$块级$$，语法错误只标红不中断渲染
  math({ katexOptions: { throwOnError: false, strict: false } }),
  // 图片灯箱：点击放大，深色遮罩；包在链接里的图不处理，避免抢跳转
  mediumZoom({ background: 'rgba(9, 16, 24, 0.84)', margin: 28 }),
  // 单换行即换行，所见即所得
  { remark: breaksPlugin },
]
