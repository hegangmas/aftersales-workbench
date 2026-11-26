// 轻量级前端代码护栏配置（未强制引入额外 eslint / eslint-plugin-vue 依赖）。
// 后续如在 devDependencies 中引入 eslint 与 eslint-plugin-vue，可补充：
//   extends: ['eslint:recommended', 'plugin:vue/vue3-essential']
module.exports = {
  root: true,
  env: {
    browser: true,
    es2021: true,
    node: true
  },
  extends: ['eslint:recommended'],
  parserOptions: {
    ecmaVersion: 2021,
    sourceType: 'module'
  },
  rules: {
    'no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    'no-console': ['warn', { allow: ['warn', 'error'] }],
    'no-debugger': 'warn',
    'no-var': 'error',
    'prefer-const': 'warn',
    eqeqeq: ['error', 'always', { null: 'ignore' }]
  }
};
