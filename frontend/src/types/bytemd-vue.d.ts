/**
 * `@bytemd/vue-next@1.22.0` 的类型声明只有 `export { DefineComponent as default }`，
 * 组件被当成纯类型，导致 vue-tsc 无法使用。这里补上运行时组件的声明。
 */
declare module '@bytemd/vue-next' {
  import type { DefineComponent } from 'vue'
  import type { EditorProps, ViewerProps } from 'bytemd'

  export const Editor: DefineComponent<EditorProps>
  export const Viewer: DefineComponent<ViewerProps>
  const _default: DefineComponent
  export default _default
}
