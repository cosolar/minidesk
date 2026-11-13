/**
 * 首页静态配置（首页宫格用）。
 * 应用列表与侧栏菜单保持一致——只放高频、有联动的入口。
 */

export interface AppEntry {
  code: string
  name: string
  icon: string
  color: string
  bg: string
  route: string
}

export interface ModuleConfig {
  key: 'work' | 'study' | 'life'
  title: string
  subtitle: string
  accent: string
  bannerFrom: string
  bannerTo: string
  bannerIcon: string
  /** 卡片底部「进入 X」的目标页 */
  entry: string
  apps: AppEntry[]
}

export const quickEntries: AppEntry[] = [
  {
    code: 'note.new',
    name: '新建笔记',
    icon: 'lucide:file-plus-2',
    color: '#35C79B',
    bg: '#E7F9F2',
    route: '/study/note',
  },
  {
    code: 'task.new',
    name: '新建任务',
    icon: 'lucide:square-check-big',
    color: '#4A86E8',
    bg: '#EAF1FE',
    route: '/todo?new=1',
  },
  {
    code: 'diary.new',
    name: '写日记',
    icon: 'lucide:pen-line',
    color: '#FF9F43',
    bg: '#FFF3E4',
    route: '/life/diary',
  },
  {
    code: 'ledger.new',
    name: '记一笔',
    icon: 'lucide:wallet',
    color: '#FF6B6B',
    bg: '#FFEEEE',
    route: '/life/ledger',
  },
  {
    code: 'anniversary',
    name: '纪念日',
    icon: 'lucide:cake',
    color: '#F2789F',
    bg: '#FFEFF4',
    route: '/life/anniversary',
  },
  {
    code: 'checkin',
    name: '打卡',
    icon: 'lucide:calendar-check',
    color: '#23A981',
    bg: '#E7F9F2',
    route: '/study/checkin',
  },
]

const moreApp = (route: string, color: string): AppEntry => ({
  code: 'more',
  name: '更多应用',
  icon: 'lucide:layout-grid',
  color,
  bg: '#F1F6F5',
  route,
})

export const modules: ModuleConfig[] = [
  {
    key: 'work',
    title: '工作',
    subtitle: '高效工作，专注创造',
    accent: '#4A86E8',
    bannerFrom: '#EAF2FE',
    bannerTo: '#F7FAFF',
    bannerIcon: 'lucide:laptop',
    entry: '/work/project',
    apps: [
      {
        code: 'work.project',
        name: '项目',
        icon: 'lucide:folder-kanban',
        color: '#5B7BE0',
        bg: '#EDF1FE',
        route: '/work/project',
      },
      {
        code: 'work.todo',
        name: '待办',
        icon: 'lucide:list-todo',
        color: '#4A86E8',
        bg: '#EAF1FE',
        route: '/todo?module=work',
      },
      {
        code: 'work.calendar',
        name: '日历',
        icon: 'lucide:calendar-days',
        color: '#2AA6C9',
        bg: '#E8F6FB',
        route: '/calendar',
      },
      {
        code: 'work.journal',
        name: '日志',
        icon: 'lucide:notebook-pen',
        color: '#6C8AE4',
        bg: '#EFF4FE',
        route: '/work/journal',
      },
      {
        code: 'work.asset',
        name: '资产',
        icon: 'lucide:folder-open',
        color: '#3E9E8C',
        bg: '#E9F7F2',
        route: '/work/asset',
      },
      {
        code: 'work.tool',
        name: '书签',
        icon: 'lucide:link',
        color: '#4C6FFF',
        bg: '#EDF1FF',
        route: '/work/tool',
      },
      {
        code: 'work.meeting',
        name: '会议纪要',
        icon: 'lucide:users-round',
        color: '#7C93E8',
        bg: '#F1F5FE',
        route: '/work/meeting',
      },
      moreApp('/work/project', '#8AA0A8'),
    ],
  },
  {
    key: 'study',
    title: '学习',
    subtitle: '持续学习，不断成长',
    accent: '#8B7CF6',
    bannerFrom: '#EFECFE',
    bannerTo: '#FAF9FF',
    bannerIcon: 'lucide:book-open',
    entry: '/study/plan',
    apps: [
      {
        code: 'study.kb',
        name: '知识库',
        icon: 'lucide:library',
        color: '#8B7CF6',
        bg: '#F2EFFE',
        route: '/study/kb',
      },
      {
        code: 'study.note',
        name: '笔记',
        icon: 'lucide:notebook',
        color: '#7E6EF2',
        bg: '#F1EDFE',
        route: '/note',
      },
      {
        code: 'study.plan',
        name: '计划',
        icon: 'lucide:calendar-check',
        color: '#9A8CF8',
        bg: '#F4F1FE',
        route: '/study/plan',
      },
      {
        code: 'study.checkin',
        name: '打卡',
        icon: 'lucide:flame',
        color: '#6E5EE8',
        bg: '#EFEBFE',
        route: '/study/checkin',
      },
      {
        code: 'study.resource',
        name: '资源',
        icon: 'lucide:bookmark',
        color: '#8B7CF6',
        bg: '#F3F0FF',
        route: '/study/resource',
      },
      moreApp('/study/plan', '#9A8CF8'),
    ],
  },
  {
    key: 'life',
    title: '生活',
    subtitle: '记录生活，热爱生活',
    accent: '#FF9F43',
    bannerFrom: '#FFF2E2',
    bannerTo: '#FFFBF6',
    bannerIcon: 'lucide:coffee',
    entry: '/life/ledger',
    apps: [
      {
        code: 'life.ledger',
        name: '记账',
        icon: 'lucide:wallet',
        color: '#FF9F43',
        bg: '#FFF4E7',
        route: '/life/ledger',
      },
      {
        code: 'life.anniversary',
        name: '纪念日',
        icon: 'lucide:cake',
        color: '#F2789F',
        bg: '#FFF0F5',
        route: '/life/anniversary',
      },
      {
        code: 'life.diary',
        name: '日记',
        icon: 'lucide:pen-line',
        color: '#E58A3D',
        bg: '#FFF3E6',
        route: '/life/diary',
      },
      {
        code: 'life.album',
        name: '相册',
        icon: 'lucide:images',
        color: '#FFA94D',
        bg: '#FFF6EA',
        route: '/life/album',
      },
      {
        code: 'life.contact',
        name: '联系人',
        icon: 'lucide:users',
        color: '#4CAF93',
        bg: '#EAF8F3',
        route: '/life/contact',
      },
      moreApp('/life/ledger', '#E5A165'),
    ],
  },
]

export interface TodoItem {
  id: number
  title: string
  time: string
  done: boolean
  highlight?: boolean
}

export interface LifeLogItem {
  id: number
  title: string
  date: string
  desc: string
  from: string
  to: string
}

export const todayTodos: TodoItem[] = [
  { id: 1, title: '完成 MiniDesk 设计稿', time: '09:00', done: true },
  { id: 2, title: '学习 Vue 3 新特性', time: '14:00', done: false, highlight: true },
]

export const lifeLogs: LifeLogItem[] = [
  {
    id: 1,
    title: '和家人去海边游玩',
    date: '10月07日',
    desc: '阳光正好，海风很舒服',
    from: '#8FD8C4',
    to: '#3E9E8C',
  },
]
