/**
 * 主题目录：五套主题的元信息。
 *
 * 真正的色值在 `styles/theme.scss` 里用 CSS 变量定义（`[data-theme='x']`），
 * 这里只放「给选择器展示用」的信息，避免颜色在两处各维护一份。
 */
export type ThemeId = 'mint' | 'violet' | 'night' | 'warm' | 'pure'

export interface ThemeDef {
  id: ThemeId
  /** 短名，侧栏与按钮上显示 */
  name: string
  /** 全名，主题卡片上显示 */
  label: string
  /** 一句话气质描述 */
  tagline: string
  /** 首页 Hero 副标题：每套主题一句，换主题时氛围跟着换 */
  greeting: string
  /** 是否暗色主题（决定 Element Plus 的 .dark 类） */
  dark: boolean
  /** 预览缩略图的渐变，仅用于选择器 */
  swatch: string
  /** 浏览器地址栏色 */
  metaColor: string
}

export const THEMES: ThemeDef[] = [
  {
    id: 'mint',
    name: '青绿',
    label: '青绿 · 山水',
    tagline: '清爽耐看，默认主题',
    greeting: '让工作更高效，让生活更简单。',
    dark: false,
    swatch: 'linear-gradient(150deg, #eaf7f1 0%, #b9e6d6 44%, #35c79b 100%)',
    metaColor: '#F5FAF8',
  },
  {
    id: 'violet',
    name: '紫色',
    label: '紫色 · 极简',
    tagline: '安静内敛，适合夜间阅读',
    greeting: '从一件小事开始，今天也会很顺。',
    dark: false,
    swatch: 'linear-gradient(150deg, #f3efff 0%, #d2c7ff 44%, #7b6cf6 100%)',
    metaColor: '#F5F4FB',
  },
  {
    id: 'night',
    name: '深色',
    label: '深色 · 星空',
    tagline: '低亮护眼，数据更突出',
    greeting: '愿你所梦皆成真，也愿你早点休息。',
    dark: true,
    swatch: 'linear-gradient(150deg, #1b2340 0%, #2b3a6b 46%, #0f1320 100%)',
    metaColor: '#0F1320',
  },
  {
    id: 'warm',
    name: '暖色',
    label: '暖色 · 温馨',
    tagline: '米杏暖调，久看不累',
    greeting: '享受当下的好时光，慢慢来也来得及。',
    dark: false,
    swatch: 'linear-gradient(150deg, #fff6e9 0%, #f6d3ab 44%, #e2924e 100%)',
    metaColor: '#FDF9F3',
  },
  {
    id: 'pure',
    name: '纯净',
    label: '简约 · 纯净',
    tagline: '近乎无色的白，专注内容',
    greeting: '把复杂留给自己，把简单留给你。',
    dark: false,
    swatch: 'linear-gradient(150deg, #ffffff 0%, #e4eeea 46%, #9fc4b8 100%)',
    metaColor: '#F8FAF9',
  },
]

export const THEME_MAP: Record<ThemeId, ThemeDef> = THEMES.reduce(
  (acc, item) => ({ ...acc, [item.id]: item }),
  {} as Record<ThemeId, ThemeDef>,
)

export const DEFAULT_THEME: ThemeId = 'mint'

/** 兼容旧值：light/auto → mint，dark → night */
export function normalizeTheme(value: string | null | undefined): ThemeId {
  if (!value) {
    return DEFAULT_THEME
  }
  if (value === 'dark') {
    return 'night'
  }
  if (value === 'light' || value === 'auto') {
    return 'mint'
  }
  return value in THEME_MAP ? (value as ThemeId) : DEFAULT_THEME
}
