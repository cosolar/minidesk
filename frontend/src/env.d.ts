/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 接口前缀，默认 /api */
  readonly VITE_API_BASE: string
  /** 应用标题 */
  readonly VITE_APP_TITLE: string
  /** 是否启用 mock 数据（后端未就绪时） */
  readonly VITE_USE_MOCK: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}
