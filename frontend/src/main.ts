import { createPinia, setActivePinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import { createApp } from 'vue'
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/styles/index.scss'
import App from './App.vue'
import router from './app/router'
import { queryClient } from './app/providers/query'

// 生产构建会摇掉 install() 内部的激活调用，显式激活一次，
// 保证路由守卫与组件 setup 中的 useStore 都能拿到 activePinia。
const pinia = createPinia()
setActivePinia(pinia)

const app = createApp(App)

app.use(pinia)
app.use(router)
app.use(VueQueryPlugin, { queryClient })

app.mount('#app')
