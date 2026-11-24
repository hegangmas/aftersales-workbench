import { createApp } from 'vue'
import { ElAlert } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import './styles/workbench.css'
import App from './App.vue'

const app = createApp(App)
app.use(ElAlert)
app.mount('#app')
