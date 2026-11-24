<template>
  <main class="login-layout">
    <section class="brand" aria-label="松果售后品牌介绍">
      <svg class="arcs" viewBox="0 0 806 900" fill="none" aria-hidden="true" preserveAspectRatio="xMaxYMax slice">
        <g stroke="#b3f3de" stroke-width=".7" opacity=".1">
          <circle cx="824" cy="794" r="366" />
          <circle cx="824" cy="794" r="426" />
          <circle cx="824" cy="794" r="486" />
          <circle cx="824" cy="794" r="546" />
          <path d="M597 0v900M0 795h806" stroke-dasharray="2 8" />
        </g>
        <g stroke="#a1e8d3" opacity=".12" stroke-width=".7">
          <path d="M560 912c-74-182-6-416 235-501M615 930c-76-194-2-390 207-456" />
          <circle cx="597" cy="371" r="4" fill="#b4f4df" />
          <circle cx="724" cy="439" r="3" fill="#b4f4df" />
        </g>
      </svg>
      <header class="identity">
        <BrandLogo class="identity-logo" variant="white" alt="松果" priority="high" width="168px" />
        <div>
          <h2>售后工作台</h2>
          <small>SONGGUO AFTERSALES WORKBENCH</small>
        </div>
      </header>
      <div class="hero">
        <div class="eyebrow"><i></i>合肥松果科技 · 充电桩售后协同平台</div>
        <h1>每一次充电守护，<br><span>都有清晰可溯的闭环。</span></h1>
        <p>面向交流桩、直流快充与充放电四合一产品的售后工作台，打通来电查桩、认领协同、现场关联与回访验证全流程。</p>
      </div>
      <section class="workflow">
        <div class="flow-head"><h3>标准售后处理链路</h3><span class="live"><i></i>实时协同</span></div>
        <div class="steps">
          <article>
            <div class="step-top"><span class="step-icon" aria-hidden="true">01</span></div>
            <h4>查桩建单</h4>
            <p>桩编码 / 手机号速查<br>设备与历史记录</p>
          </article>
          <article>
            <div class="step-top"><span class="step-icon" aria-hidden="true">02</span></div>
            <h4>认领协同</h4>
            <p>待认领池抢单流转<br>负责人权限隔离</p>
          </article>
          <article>
            <div class="step-top"><span class="step-icon" aria-hidden="true">03</span></div>
            <h4>现场与验证</h4>
            <p>关联现场单回执<br>回访确认充电恢复</p>
          </article>
        </div>
      </section>
      <footer class="brand-footer">
        <span>合肥松果科技股份有限公司 · 售后服务中心</span>
        <span class="internal"><i></i>内部协同系统 · 仅限授权账号访问</span>
      </footer>
    </section>
    <section class="login-pane" aria-label="账号登录">
      <header class="compact-brand">
        <BrandLogo class="compact-logo" variant="teal" alt="松果" width="112px" />
        <strong>售后工作台</strong>
        <span class="env"><i></i>测试环境</span>
      </header>
      <div class="env desktop-env"><i></i>测试环境</div>
      <div class="form-wrap">
        <BrandLogo class="welcome-logo" variant="teal" alt="" width="168px" />
        <h1 id="login-title">欢迎登录</h1>
        <p class="subtitle">请使用坐席或管理员账号进入售后工作台</p>
        <div v-if="error" ref="errorBanner" tabindex="-1" class="error-banner">
          <el-alert :title="error" type="error" :closable="false" show-icon />
        </div>
        <form aria-labelledby="login-title" @submit.prevent="$emit('submit')">
          <div class="field">
            <label for="username">账号</label>
            <div class="input" :class="{ focus: username }">
              <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="3.5" /><path d="M5 21v-2a7 7 0 0 1 14 0v2" /></svg>
              <input id="username" name="username" :value="username" autocomplete="username" autocapitalize="none" autocorrect="off" spellcheck="false" enterkeyhint="next" required @input="$emit('update:username', $event.target.value)">
            </div>
          </div>
          <div class="field">
            <label for="password">密码</label>
            <div class="input">
              <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="5" y="10" width="14" height="11" rx="2.5" /><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2" /></svg>
              <input id="password" name="password" :value="password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" enterkeyhint="done" required @input="$emit('update:password', $event.target.value)">
              <button class="eye" type="button" :aria-pressed="showPassword" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z" /><circle cx="12" cy="12" r="2.5" /></svg>
              </button>
            </div>
          </div>
          <div class="assist">
            <label class="remember"><input type="checkbox" name="remember" :checked="remember" @change="$emit('update:remember', $event.target.checked)">记住账号</label>
            <span>忘记密码？请联系管理员重置</span>
          </div>
          <button class="submit" type="submit" :disabled="busy || !username || !password">{{ busy ? '登录中…' : '登录工作台' }} <span aria-hidden="true">→</span></button>
        </form>
        <div class="security">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3 4 6v6c0 4 4 7 8 9 4-2 8-5 8-9V6l-8-3Z" /><path d="m8.5 12 2.5 2.5 4.5-5" /></svg>
          <p>账号采用安全会话校验；修改密码或由管理员重置后，其他设备将自动退出。</p>
        </div>
      </div>
      <footer class="copyright">© 2026 合肥松果科技股份有限公司</footer>
    </section>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import BrandLogo from './BrandLogo.vue'

defineProps({
  username: { type: String, default: '' },
  password: { type: String, default: '' },
  remember: Boolean,
  busy: Boolean,
  error: { type: String, default: '' }
})
defineEmits(['submit', 'update:username', 'update:password', 'update:remember'])
const showPassword = ref(false)
const errorBanner = ref(null)

defineExpose({
  focusError() {
    if (errorBanner.value) errorBanner.value.focus()
  }
})
</script>

<style scoped>
.login-layout { display: grid; grid-template-columns: minmax(0, 1.272fr) minmax(320px, 1fr); min-height: 100dvh; overflow-x: clip; background: #fff; }
.brand {
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 100dvh;
  padding: clamp(32px, 5vh, 52px) clamp(36px, 5vw, 72px) 28px;
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(ellipse at 88% 75%, rgba(20, 184, 166, .22), transparent 62%),
    radial-gradient(ellipse at 4% 0%, rgba(56, 189, 248, .1), transparent 54%),
    linear-gradient(145deg, #072624 0%, #0b3d39 56%, #0f5952 100%);
  background:
    radial-gradient(ellipse at 88% 75%, rgba(20, 184, 166, .22), transparent 62%),
    radial-gradient(ellipse at 4% 0%, rgba(56, 189, 248, .1), transparent 54%),
    linear-gradient(145deg in oklab, #072624 0%, #0b3d39 56%, #0f5952 100%);
}
.brand::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: -1;
  background-image: radial-gradient(rgba(194, 244, 231, .55) .7px, transparent .8px);
  background-size: 24px 24px;
  opacity: .1;
  mask-image: linear-gradient(130deg, transparent 40%, #000 100%);
}
.arcs { position: absolute; inset: 0; z-index: -1; width: 100%; height: 100%; }
.identity, .brand-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.identity { justify-content: flex-start; gap: 16px; min-width: 0; }
.welcome-logo { margin-bottom: 18px; }
.identity h2 { margin: 0 0 5px; font-size: 20px; font-weight: 600; letter-spacing: 1px; }
.identity small { color: #a8c8c2; font-size: 10px; letter-spacing: .12em; }
.hero { width: min(662px, 100%); margin: auto 0 28px; }
.eyebrow, .live { display: inline-flex; align-items: center; gap: 8px; }
.eyebrow { padding: 7px 12px; border: 1px solid #a8e6d52b; border-radius: 30px; background: #e2fff008; color: #c0dfd7; font-size: 12px; }
.eyebrow i, .live i, .internal i, .env i { width: 5px; height: 5px; border-radius: 50%; background: #8ae2c4; }
.hero h1 { margin: 22px 0 16px; font-size: clamp(28px, 3vw, 38px); font-weight: 600; line-height: 1.42; letter-spacing: .5px; }
.hero h1 span { color: #c4efe1; }
.hero p { max-width: 36em; margin: 0; color: rgba(226, 245, 242, .76); font-size: 15px; line-height: 1.9; }
.workflow {
  width: min(662px, 100%);
  padding: 22px;
  border: 1px solid rgba(255, 255, 255, .12);
  border-radius: 16px;
  background: rgba(255, 255, 255, .06);
  box-shadow: inset 0 1px 0 #ffffff14;
  backdrop-filter: blur(16px);
}
.flow-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.flow-head h3 { margin: 0; color: #e1f1eb; font-size: 13px; font-weight: 500; }
.live { color: #a9cfc3; font-size: 12px; }
.steps { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; }
.step-icon { color: #83afa0; font-size: 12px; letter-spacing: 1px; }
.steps h4 { margin: 8px 0 6px; color: #edf8f3; font-size: 14px; font-weight: 500; }
.steps p { margin: 0; color: #b2d1c7; font-size: 12px; line-height: 1.8; }
.brand-footer { margin-top: 28px; color: #90b4a9; font-size: 12px; }
.internal { display: inline-flex; align-items: center; gap: 6px; color: #80a99e; }
.login-pane { position: relative; display: flex; flex-direction: column; min-width: 0; min-height: 100dvh; background: linear-gradient(110deg, #fbfdfc 0%, #fff 20%); }
.compact-brand { display: none; }
.desktop-env, .env {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 7px;
  background: #f7f9fa;
  color: #64748b;
  font-size: 12px;
}
.desktop-env { position: absolute; top: max(28px, env(safe-area-inset-top, 0px)); right: 32px; }
.desktop-env i { background: #0d9488; }
.form-wrap { width: min(388px, calc(100% - 48px)); margin: auto; }
.welcome-symbol { width: 37px; height: 37px; margin-bottom: 18px; border: 1px solid #e0efea; background: linear-gradient(145deg, #f3faf7, #eaf5f0); color: #0f766e; }
.form-wrap h1 { margin: 0 0 8px; color: #0f172a; font-size: 28px; font-weight: 600; }
.subtitle { margin: 0 0 24px; color: #64748b; font-size: 14px; }
.field { margin-bottom: 16px; }
.field label { display: block; margin-bottom: 8px; color: #334155; font-size: 12px; font-weight: 500; }
.input {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 46px;
  padding: 0 14px;
  border: 1px solid #dce3ec;
  border-radius: 10px;
  background: #fff;
  color: #94a3b8;
}
.input.focus, .input:focus-within { border-color: #0d9488; box-shadow: 0 0 0 3px #0d948819; color: #0f8b7e; }
.input svg { width: 17px; height: 17px; fill: none; stroke: currentColor; stroke-width: 1.6; stroke-linecap: round; stroke-linejoin: round; flex: none; }
.input input { width: 100%; min-width: 0; height: 40px; border: 0; outline: 0; background: transparent; color: #334155; }
.eye { display: grid; place-items: center; width: 44px; height: 44px; margin-right: -8px; border: 0; background: transparent; color: #94a3b8; }
.assist { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: #8a97a7; font-size: 12px; }
.remember { display: flex; align-items: center; gap: 8px; min-height: 44px; color: #64748b; }
.remember input { width: 16px; height: 16px; accent-color: #0f766e; }
.submit {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 46px;
  margin-top: 18px;
  border: 1px solid #0b8177;
  border-radius: 10px;
  background: linear-gradient(135deg, #0f766e, #0d9488);
  box-shadow: 0 6px 16px -4px #0f766e52;
  color: #fff;
  letter-spacing: 1px;
}
.submit:disabled { cursor: not-allowed; opacity: .65; }
.security { display: flex; gap: 10px; margin-top: 20px; padding: 12px 14px; border: 1px solid #e2ecea; border-radius: 10px; background: #f4f8f7; }
.security svg { width: 17px; height: 17px; margin-top: 3px; fill: none; stroke: #6b9b8e; stroke-width: 1.6; flex: none; }
.security p { margin: 0; color: #82948d; font-size: 12px; line-height: 1.8; }
.copyright { padding: 20px 16px 28px; color: #a2adba; font-size: 12px; text-align: center; }
.error-banner { margin-bottom: 14px; }
@media (max-width: 900px) {
  .login-layout { display: block; }
  .brand { display: none; }
  .desktop-env { display: none; }
  .compact-brand {
    display: flex;
    align-items: center;
    gap: 10px;
    min-height: 64px;
    padding: max(10px, env(safe-area-inset-top, 0px)) 16px 10px;
    border-bottom: 1px solid #eaeef2;
    color: #102c2b;
  }
  .compact-brand strong { min-width: 0; font-size: 16px; white-space: nowrap; }
  .env { margin-left: auto; }
  .env i { background: #0d9488; }
  .login-pane { min-height: 100dvh; }
  .form-wrap { width: min(388px, calc(100% - 32px)); padding: 28px 0 12px; }
}
</style>
