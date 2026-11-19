/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string
  readonly VITE_API_BASE_URL: string
  readonly VITE_WS_URL: string
  /** 是否显示扫码登录的「模拟手机」按钮，留空则仅开发环境显示 */
  readonly VITE_LOGIN_QR_DEMO?: string
  /** 扫码本地演示用的账号（只写在 .env.local，不入库） */
  readonly VITE_LOGIN_QR_DEMO_ACCOUNT?: string
  /** 扫码本地演示用的口令（只写在 .env.local，不入库） */
  readonly VITE_LOGIN_QR_DEMO_PASSWORD?: string
  /** 短信登录是否展示本地演示验证码，留空则仅开发环境显示 */
  readonly VITE_LOGIN_SMS_DEMO?: string
  /** 短信登录本地演示用的账号（只写在 .env.local，不入库） */
  readonly VITE_LOGIN_SMS_DEMO_ACCOUNT?: string
  /** 短信登录本地演示用的口令（只写在 .env.local，不入库） */
  readonly VITE_LOGIN_SMS_DEMO_PASSWORD?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
