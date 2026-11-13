import type { Component } from 'vue'
import {
  Bell,
  Calendar,
  ChatDotRound,
  Checked,
  Coffee,
  Collection,
  DataAnalysis,
  Document,
  Files,
  Folder,
  HomeFilled,
  Link,
  List,
  Money,
  Notebook,
  Picture,
  Present,
  Reading,
  Search,
  Setting,
  Trophy,
  User,
} from '@element-plus/icons-vue'

export interface NavItem {
  path: string
  name: string
  title: string
}

export interface NavGroup {
  key: string
  title: string
  icon: Component
  basePath: string
  children: NavItem[]
}

/**
 * 统一入口：跨板块的高频场景，都放在最上面。
 * 这几个页面的共同点是「一次看到全部」，所以不再按工作/学习/生活分区。
 */
export const unifiedNav: NavItem[] = [
  { path: '/todo', name: 'todo', title: '待办' },
  { path: '/calendar', name: 'calendar', title: '日历' },
  { path: '/note', name: 'note', title: '笔记' },
]

/** 统一入口的图标，与顺序一一对应 */
export const unifiedIcons: Record<string, Component> = {
  '/todo': List,
  '/calendar': Calendar,
  '/note': Notebook,
}

/**
 * 不进侧栏、但有独立页面的入口：由顶栏图标或页面内链接进入。
 * 提醒 = 顶栏铃铛；搜索 = 顶栏搜索框 / Ctrl+K；统计 = 工作台的数据概览。
 */
export const hiddenNav: NavItem[] = [
  { path: '/profile', name: 'profile', title: '个人中心' },
  { path: '/reminders', name: 'reminders', title: '提醒中心' },
  { path: '/search', name: 'search', title: '全局搜索' },
  { path: '/stats', name: 'stats', title: '数据统计' },
]

/**
 * 三大板块：各自领域内的专门页面。
 * 已经被统一入口覆盖的能力（任务、日历、笔记）不再重复放一份。
 */
export const navGroups: NavGroup[] = [
  {
    key: 'work',
    title: '工作',
    icon: List,
    basePath: '/work',
    children: [
      { path: '/work/project', name: 'work-project', title: '项目' },
      { path: '/work/journal', name: 'work-journal', title: '日志' },
      { path: '/work/asset', name: 'work-asset', title: '资产' },
      { path: '/work/tool', name: 'work-tool', title: '书签' },
      { path: '/work/meeting', name: 'work-meeting', title: '会议纪要' },
    ],
  },
  {
    key: 'study',
    title: '学习',
    icon: Reading,
    basePath: '/study',
    children: [
      { path: '/study/kb', name: 'study-kb', title: '知识库' },
      { path: '/study/plan', name: 'study-plan', title: '计划' },
      { path: '/study/checkin', name: 'study-checkin', title: '打卡' },
      { path: '/study/resource', name: 'study-resource', title: '资源' },
    ],
  },
  {
    key: 'life',
    title: '生活',
    icon: Coffee,
    basePath: '/life',
    children: [
      { path: '/life/ledger', name: 'life-ledger', title: '记账' },
      { path: '/life/anniversary', name: 'life-anniversary', title: '纪念日' },
      { path: '/life/diary', name: 'life-diary', title: '日记' },
      { path: '/life/album', name: 'life-album', title: '相册' },
      { path: '/life/contact', name: 'life-contact', title: '联系人' },
    ],
  },
]

export interface NavLink {
  path: string
  name: string
  title: string
  icon: Component
}

export const systemNav: NavLink[] = [
  { path: '/settings', name: 'settings', title: '设置', icon: Setting },
]

export const homeNav = { path: '/', name: 'dashboard', title: '工作台', icon: HomeFilled }

/** 旧路径兼容：避免收藏夹与已经发出去的链接失效 */
export const legacyRedirects: Array<{ from: string; to: string }> = [
  { from: '/work/task', to: '/todo' },
  { from: '/work/todo', to: '/todo' },
  { from: '/work/schedule', to: '/calendar' },
  { from: '/work/note', to: '/note' },
  { from: '/study/note', to: '/note' },
  { from: '/work/review', to: '/work/journal' },
  { from: '/study/read', to: '/study/resource' },
  { from: '/life/shopping', to: '/life/ledger' },
]

/** 图标出口，避免各页面重复 import */
export const navIcons = {
  Bell,
  Calendar,
  ChatDotRound,
  Checked,
  Collection,
  DataAnalysis,
  Document,
  Files,
  Folder,
  Link,
  Money,
  Notebook,
  Picture,
  Present,
  Search,
  Trophy,
  User,
}
