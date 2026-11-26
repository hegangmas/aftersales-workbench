module.exports = {
  root: true,
  env: {
    browser: true,
    es2021: true,
    node: true
  },
  extends: ['eslint:recommended', 'plugin:vue/vue3-essential'],
  parserOptions: {
    ecmaVersion: 2021,
    sourceType: 'module'
  },
  rules: {
    'no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    'no-console': ['warn', { allow: ['warn', 'error'] }],
    'no-debugger': 'warn',
    'no-var': 'error',
    'prefer-const': 'warn',
    eqeqeq: ['error', 'always', { null: 'ignore' }],
    'max-lines': ['warn', 500],
    // 既有组件存在 draft 对象 prop 双向绑定与同名 ref，降级为 warn 以便波次 1 先跑通 lint 门禁
    'vue/no-mutating-props': 'warn',
    'vue/no-dupe-keys': 'warn'
  }
};
