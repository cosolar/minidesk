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
import 'katex/dist/katex.min.css'

export const markdownPlugins: BytemdPlugin[] = [
  gfm(),
  highlight(),
  // 公式：$行内$ / $$块级$$，语法错误只标红不中断渲染
  math({ katexOptions: { throwOnError: false, strict: false } }),
  // 图片灯箱：点击放大，深色遮罩；包在链接里的图不处理，避免抢跳转
  mediumZoom({ background: 'rgba(9, 16, 24, 0.84)', margin: 28 }),
]
