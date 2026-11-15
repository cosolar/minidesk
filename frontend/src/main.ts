import { createPinia, setActivePinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import { createApp } from 'vue'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import 'element-plus/theme-chalk/dark/css-vars.css'
// ElMessage / ElMessageBox 是 JS 调用，模板按需引入收集不到它们的基础样式，必须显式引入
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import '@/styles/index.scss'
import App from './App.vue'
import router from './app/router'
import { queryClient } from './app/providers/query'

// 全局中文：Element Plus 的 locale 在 App.vue，dayjs 的相对时间/月份靠这里
dayjs.locale('zh-cn')

// 生产构建会摇掉 install() 内部的激活调用，显式激活一次，
// 保证路由守卫与组件 setup 中的 useStore 都能拿到 activePinia。
const pinia = createPinia()
setActivePinia(pinia)

const app = createApp(App)

app.use(pinia)
app.use(router)
app.use(VueQueryPlugin, { queryClient })

app.mount('#app')
