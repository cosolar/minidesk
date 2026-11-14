import type { RouteRecordRaw } from 'vue-router'
import { legacyRedirects, navGroups } from './nav'

const PlaceholderView = () => import('@/features/misc/PlaceholderView.vue')

function placeholder(path: string, name: string, title: string): RouteRecordRaw {
  return { path, name, component: PlaceholderView, meta: { title } }
}

/** 已实现的页面 */
const implementedRoutes: RouteRecordRaw[] = [
  {
    path: '/todo',
    name: 'todo',
    component: () => import('@/features/work/task/views/TodoView.vue'),
    meta: { title: '待办' },
  },
  {
    path: '/calendar',
    name: 'calendar',
    component: () => import('@/features/calendar/views/CalendarView.vue'),
    meta: { title: '日历' },
  },
  {
    path: '/reminders',
    name: 'reminders',
    component: () => import('@/features/common/reminder/views/RemindersView.vue'),
    meta: { title: '提醒中心' },
  },
  {
    path: '/note',
    name: 'note',
    component: () => import('@/features/note/views/NotesView.vue'),
    meta: { title: '笔记' },
  },
  {
    path: '/search',
    name: 'search',
    component: () => import('@/features/common/search/views/SearchView.vue'),
    meta: { title: '全局搜索' },
  },
  {
    path: '/stats',
    name: 'stats',
    component: () => import('@/features/stats/views/StatsView.vue'),
    meta: { title: '数据统计' },
  },
  {
    path: '/work/project',
    name: 'work-project',
    component: () => import('@/features/work/project/views/ProjectView.vue'),
    meta: { title: '项目' },
  },
  {
    path: '/work/journal',
    name: 'work-journal',
    component: () => import('@/features/work/journal/views/JournalView.vue'),
    meta: { title: '日志' },
  },
  {
    path: '/work/asset',
    name: 'work-asset',
    component: () => import('@/features/work/docasset/views/AssetView.vue'),
    meta: { title: '资产' },
  },
  {
    path: '/work/tool',
    name: 'work-tool',
    component: () => import('@/features/common/navlink/views/NavLinkView.vue'),
    props: { module: 'work' },
    meta: { title: '书签' },
  },
  {
    path: '/work/meeting',
    name: 'work-meeting',
    component: () => import('@/features/work/meeting/views/MeetingView.vue'),
    meta: { title: '会议纪要' },
  },
  {
    path: '/study/plan',
    name: 'study-plan',
    component: () => import('@/features/study/plan/views/StudyPlanView.vue'),
    meta: { title: '计划' },
  },
  {
    path: '/study/checkin',
    name: 'study-checkin',
    component: () => import('@/features/life/habit/views/CheckinView.vue'),
    meta: { title: '打卡' },
  },
  {
    path: '/study/kb',
    name: 'study-kb',
    component: () => import('@/features/study/kb/views/KnowledgeView.vue'),
    meta: { title: '知识库' },
  },
  {
    path: '/study/resource',
    name: 'study-resource',
    component: () => import('@/features/common/navlink/views/NavLinkView.vue'),
    props: { module: 'study' },
    meta: { title: '资源' },
  },
  {
    path: '/life/ledger',
    name: 'life-ledger',
    component: () => import('@/features/life/ledger/views/LedgerView.vue'),
    meta: { title: '记账' },
  },
  {
    path: '/life/anniversary',
    name: 'life-anniversary',
    component: () => import('@/features/life/anniversary/views/AnniversaryView.vue'),
    meta: { title: '纪念日' },
  },
  {
    path: '/life/diary',
    name: 'life-diary',
    component: () => import('@/features/life/lifelog/views/LifeLogView.vue'),
    meta: { title: '日记' },
  },
  {
    path: '/life/album',
    name: 'life-album',
    component: () => import('@/features/life/album/views/AlbumView.vue'),
    meta: { title: '相册' },
  },
  {
    path: '/life/contact',
    name: 'life-contact',
    component: () => import('@/features/life/contact/views/ContactView.vue'),
    meta: { title: '联系人' },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/features/profile/views/ProfileView.vue'),
    meta: { title: '个人中心' },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/features/profile/views/ProfileView.vue'),
    meta: { title: '个人中心' },
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('@/features/settings/views/SettingsView.vue'),
    meta: { title: '设置' },
  },
]

const implementedPaths = new Set(implementedRoutes.map((route) => route.path))

/** 菜单里还没实现的页面，先给占位视图，避免点进去 404 */
const moduleRoutes: RouteRecordRaw[] = navGroups.flatMap((group) =>
  group.children
    .filter((item) => !implementedPaths.has(item.path))
    .map((item) => placeholder(item.path, item.name, item.title)),
)

const redirectRoutes: RouteRecordRaw[] = legacyRedirects.map((item) => ({
  path: item.from,
  redirect: item.to,
}))

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/features/auth/views/LoginView.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/features/auth/views/RegisterView.vue'),
    meta: { title: '注册', public: true },
  },
  {
    // 笔记公开分享页：未登录可读，密码由后端校验
    path: '/share/note/:token',
    name: 'share-note',
    component: () => import('@/features/share/views/NoteShareView.vue'),
    meta: { title: '分享的笔记', public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/UserLayout.vue'),
    children: [
      {
        path: '',
        name: 'dashboard',
        component: () => import('@/features/dashboard/views/DashboardView.vue'),
        meta: { title: '工作台' },
      },
      ...redirectRoutes,
      ...implementedRoutes,
      ...moduleRoutes,
    ],
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/features/misc/ForbiddenView.vue'),
    meta: { title: '无权访问', public: true },
  },
  {
    // 管理后台：独立布局与视觉，整组需要 ADMIN
    path: '/console',
    component: () => import('@/features/console/layout/ConsoleLayout.vue'),
    meta: { requiresAdmin: true },
    children: [
      {
        path: '',
        name: 'console',
        component: () => import('@/features/console/views/ConsoleDashboard.vue'),
        meta: { title: '管理概览', requiresAdmin: true },
      },
      {
        path: 'users',
        name: 'console-users',
        component: () => import('@/features/console/views/ConsoleUsers.vue'),
        meta: { title: '用户管理', requiresAdmin: true },
      },
      {
        path: 'jobs',
        name: 'console-jobs',
        component: () => import('@/features/console/views/ConsoleJobs.vue'),
        meta: { title: '定时任务', requiresAdmin: true },
      },
      {
        path: 'backups',
        name: 'console-backups',
        component: () => import('@/features/console/views/ConsoleBackups.vue'),
        meta: { title: '备份恢复', requiresAdmin: true },
      },
      {
        path: 'settings',
        name: 'console-settings',
        component: () => import('@/features/console/views/ConsoleSettings.vue'),
        meta: { title: '系统设置', requiresAdmin: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]
