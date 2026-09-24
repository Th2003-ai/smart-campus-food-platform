// 前端代码检查配置（ESLint 8 的 eslintrc 格式）。
// 注意：package.json 里是 "type": "module"，若安装 ESLint 8 后本文件不生效，
// 把它改名为 .eslintrc.cjs 即可；若升级到 ESLint 9，则改用 eslint.config.js（扁平配置）。
module.exports = {
  root: true,
  env: { browser: true, es2022: true, node: true },
  extends: ['eslint:recommended', 'plugin:vue/vue3-recommended', 'plugin:@typescript-eslint/recommended'],
  parser: 'vue-eslint-parser',
  parserOptions: { parser: '@typescript-eslint/parser', ecmaVersion: 'latest', sourceType: 'module' },
  rules: {
    // 四端视图用的是单词名（Home.vue / Cart.vue / Search.vue），关掉「组件名需多个单词」
    'vue/multi-word-component-names': 'off',
    // 骨架用 console.log 标 TODO，已在对应行用 eslint-disable-next-line no-console 放行
    'no-console': 'warn',
    'no-debugger': 'warn',
    // 骨架阶段允许 any，避免大面积类型报错
    '@typescript-eslint/no-explicit-any': 'off',
    '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    // 骨架里的 props 基本没写默认值
    'vue/require-default-prop': 'off'
  },
  ignorePatterns: ['dist', 'node_modules', '*.d.ts']
}
