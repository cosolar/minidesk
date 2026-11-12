import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import { createApp } from 'vue'
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/styles/index.scss'
import App from './App.vue'
import router from './app/router'
import { queryClient } from './app/providers/query'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(VueQueryPlugin, { queryClient })

app.mount('#app')
