import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题，用于 document.title 与面包屑 */
    title?: string
    /** 免登录访问 */
    public?: boolean
    /** 仅管理员可访问（/console 下的路由统一挂） */
    requiresAdmin?: boolean
    /** 所属业务域，用于侧栏高亮 */
    module?: 'work' | 'study' | 'life'
  }
}
