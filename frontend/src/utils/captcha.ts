/**
 * 本地图形验证码（前端生成 + 前端校验，不请求服务器）
 *
 * 为什么放在前端：
 *   课程项目尚未接入短信 / 图形验证码服务，且明确要求「验证码只在本地跑、不上服务器」。
 *   因此验证码由浏览器随机生成并画到 canvas 上，登录前在本地比对。
 *
 * 边界说明（很重要，写在这里免得误读）：
 *   前端验证码挡不住真正会渲染页面的脚本，它只是把「无脑重放登录请求」的成本抬高一点，
 *   真正的防爆破靠后端 AuthController 上的 @RateLimit（默认 10 次/分钟/IP）。
 *   要做到可校验的图形验证码，需要后端下发一次性 challenge（Redis 存答案 + 图片流）。
 */

/** 去掉 0O1I 等易混淆字符，减少正常用户的输入挫败 */
const CAPTCHA_ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ'

/** 验证码长度 */
export const CAPTCHA_LENGTH = 4

const CHAR_COLORS = ['#1d4ed8', '#4338ca', '#b91c1c', '#0f766e', '#6d28d9']

function randomInt(max: number): number {
  if (max <= 0) {
    return 0
  }
  // 优先用 crypto，退化时才用 Math.random（演示环境足够）
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    const buffer = new Uint32Array(1)
    crypto.getRandomValues(buffer)
    return buffer[0] % max
  }
  return Math.floor(Math.random() * max)
}

/** 随机生成验证码文本 */
export function createCaptchaCode(length = CAPTCHA_LENGTH): string {
  let code = ''
  for (let i = 0; i < length; i++) {
    code += CAPTCHA_ALPHABET.charAt(randomInt(CAPTCHA_ALPHABET.length))
  }
  return code
}

/** 本地校验：忽略大小写与首尾空格 */
export function verifyCaptcha(input: string, code: string): boolean {
  if (!input || !code) {
    return false
  }
  return input.trim().toUpperCase() === code.trim().toUpperCase()
}

export interface CaptchaDrawOptions {
  /** CSS 像素宽度，默认 116 */
  width?: number
  /** CSS 像素高度，默认 40 */
  height?: number
}

/**
 * 把验证码画到 canvas：底色 + 干扰线 + 干扰点 + 带旋转和颜色变化的字符。
 * 文字只存在于画布像素里，不写进 DOM，避免被简单脚本直接读走。
 */
export function drawCaptcha(canvas: HTMLCanvasElement, code: string, options: CaptchaDrawOptions = {}) {
  const width = options.width ?? 116
  const height = options.height ?? 40
  const ratio = window.devicePixelRatio || 1

  canvas.width = Math.round(width * ratio)
  canvas.height = Math.round(height * ratio)
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`

  const ctx = canvas.getContext('2d')
  if (!ctx) {
    return
  }
  ctx.setTransform(ratio, 0, 0, ratio, 0, 0)
  ctx.clearRect(0, 0, width, height)

  // 底色
  const background = ctx.createLinearGradient(0, 0, width, height)
  background.addColorStop(0, '#f2f6ff')
  background.addColorStop(1, '#e9eefb')
  ctx.fillStyle = background
  ctx.fillRect(0, 0, width, height)

  // 干扰线
  for (let i = 0; i < 5; i++) {
    ctx.beginPath()
    ctx.strokeStyle = `rgba(${90 + randomInt(120)}, ${110 + randomInt(110)}, ${200 + randomInt(55)}, 0.55)`
    ctx.lineWidth = 1
    ctx.moveTo(randomInt(width), randomInt(height))
    ctx.bezierCurveTo(
      randomInt(width),
      randomInt(height),
      randomInt(width),
      randomInt(height),
      randomInt(width),
      randomInt(height)
    )
    ctx.stroke()
  }

  // 干扰点
  for (let i = 0; i < 36; i++) {
    ctx.beginPath()
    ctx.fillStyle = `rgba(${60 + randomInt(150)}, ${80 + randomInt(130)}, ${150 + randomInt(100)}, 0.5)`
    ctx.arc(randomInt(width), randomInt(height), Math.max(1, randomInt(2)), 0, Math.PI * 2)
    ctx.fill()
  }

  // 字符
  const step = width / (code.length + 1)
  for (let i = 0; i < code.length; i++) {
    const fontSize = 24 + randomInt(6)
    const x = step * (i + 1) - 4
    const y = height / 2 + fontSize / 2 - 3 + (randomInt(5) - 2)

    ctx.save()
    ctx.translate(x, y)
    ctx.rotate((randomInt(30) - 15) * (Math.PI / 180))
    ctx.font = `800 ${fontSize}px 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif`
    ctx.fillStyle = CHAR_COLORS[randomInt(CHAR_COLORS.length)]
    ctx.textAlign = 'center'
    ctx.textBaseline = 'alphabetic'
    ctx.fillText(code.charAt(i), 0, 0)
    ctx.restore()
  }
}
