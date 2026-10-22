/* eslint-env node */
/**
 * 智能 AI 校园外卖平台 · 前端代码检查规则
 *
 * 为什么文件名是 .cjs 而不是 .js：
 *   frontend/package.json 里有 "type": "module"，同目录下的 .js 会被 Node
 *   当成 ES Module，module.exports 直接抛错，配置根本加载不了。
 *   仓库里原来那个 .eslintrc.js 就是死在这个坑上，ESLint 一直没生效。
 *
 * 为什么钉在 eslint 8.x：
 *   eslint 9 只认 eslint.config.js（flat config），会自动忽略 eslintrc 格式的
 *   配置，表现同样是「装了但一行都不报」，很难查。
 */
module.exports = {
  root: true,

  // 代码运行环境：浏览器 + ES2022 语法 + 少量 node 环境（给配置文件本身用）
  env: { browser: true, es2022: true, node: true },

  // 规则集从右往左叠加，右边的覆盖左边的
  extends: [
    'eslint:recommended', // 官方推荐：未使用变量、未定义变量等
    'plugin:vue/vue3-recommended', // vue3 推荐：模板写法、组件命名等
    'plugin:@typescript-eslint/recommended', // TS 相关推荐规则
    'prettier' // 必须放最后：关掉所有与 Prettier 冲突的格式类规则
  ],

  // .vue 先用 vue-eslint-parser，其中的 <script lang="ts"> 再交给 TS 解析器
  parser: 'vue-eslint-parser',
  parserOptions: {
    parser: '@typescript-eslint/parser',
    ecmaVersion: 'latest',
    sourceType: 'module'
  },

  // 构建产物、依赖、自动生成的类型声明不检查
  ignorePatterns: ['dist', 'node_modules', '*.d.ts'],

  rules: {
    // 四端视图用的是单词名（Login.vue / Orders.vue / Cart.vue / Search.vue），
    // 这条规则要求组件名至少两个单词，不关掉会一片红
    'vue/multi-word-component-names': 'off',

    // 属性换行交给 Prettier 决定，避免两条规则互相打架
    'vue/max-attributes-per-line': 'off',

    // 骨架阶段 props 普遍没写默认值，先关掉
    'vue/require-default-prop': 'off',

    // 骨架阶段允许 any，避免大面积类型报错
    '@typescript-eslint/no-explicit-any': 'off',

    // 未使用变量降为警告，不阻断开发；以 _ 开头的参数不算
    '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],

    // 骨架用 console.log 标 TODO，先留警告；清理完成后可改成 'error'
    'no-console': 'warn',
    'no-debugger': 'warn'
  }
}
