import { createApp } from 'vue'
import { ElAlert, ElButton, ElCard, ElInput } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/input/style/css'
import App from './App.vue'

const app = createApp(App)
for (const component of [ElAlert, ElButton, ElCard, ElInput]) {
  app.use(component)
}
app.mount('#app')
