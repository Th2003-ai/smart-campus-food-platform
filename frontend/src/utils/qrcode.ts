/**
 * 纯前端 QR 码编码器（Byte 模式 + 纠错等级 L，版本 1~9）
 *
 * 为什么自己写而不装依赖：
 *   项目要求「扫码登录只在本地演示、不经过服务器」，二维码由浏览器本地生成即可；
 *   而本机是离线 / 受限环境，装不了 qrcode 之类的第三方包，所以内置一个最小实现。
 *
 * 实现范围（够用即可，不做完整实现）：
 *   - 只支持 Byte 模式、纠错等级 L，版本按内容长度自动选择（1~9，最多约 226 字节）；
 *   - 掩码按标准罚分（规则 1~4）挑选；
 *   - 输出布尔矩阵，由调用方决定画到 canvas 还是别处。
 *
 * 参考：ISO/IEC 18004，算法结构参考 Project Nayuki 的公开参考实现思路。
 */

/** 二维码矩阵：modules[row][col]，true 表示深色模块 */
export interface QrMatrix {
  /** 边长（模块数），版本 v 对应 17 + 4v */
  size: number
  /** 实际选中的版本号，便于调试 */
  version: number
  modules: boolean[][]
}

interface QrSpec {
  version: number
  /** 数据码字总数（已扣除纠错码字） */
  dataCodewords: number
  /** 每个纠错块的纠错码字数 */
  eccPerBlock: number
  /** 纠错块数量 */
  blocks: number
}

/** 纠错等级 L 的 RS 分块表（版本 1~9） */
const SPECS: QrSpec[] = [
  { version: 1, dataCodewords: 19, eccPerBlock: 7, blocks: 1 },
  { version: 2, dataCodewords: 34, eccPerBlock: 10, blocks: 1 },
  { version: 3, dataCodewords: 55, eccPerBlock: 15, blocks: 1 },
  { version: 4, dataCodewords: 80, eccPerBlock: 20, blocks: 1 },
  { version: 5, dataCodewords: 108, eccPerBlock: 26, blocks: 1 },
  { version: 6, dataCodewords: 136, eccPerBlock: 18, blocks: 2 },
  { version: 7, dataCodewords: 156, eccPerBlock: 20, blocks: 2 },
  { version: 8, dataCodewords: 194, eccPerBlock: 24, blocks: 2 },
  { version: 9, dataCodewords: 232, eccPerBlock: 30, blocks: 2 }
]

/** 校正图形中心点坐标（版本 1~9） */
const ALIGNMENT_CENTERS: Record<number, number[]> = {
  1: [],
  2: [6, 18],
  3: [6, 22],
  4: [6, 26],
  5: [6, 30],
  6: [6, 34],
  7: [6, 22, 38],
  8: [6, 24, 42],
  9: [6, 26, 46]
}

/** 纠错等级 L 的格式信息位（2 位） */
const ECC_FORMAT_BITS = 1

/* ------------------------------------------------------------------ *
 * GF(2^8) 与 Reed-Solomon 纠错码
 * ------------------------------------------------------------------ */

const GF_EXP = new Uint8Array(512)
const GF_LOG = new Uint8Array(256)
let gfSeed = 1
for (let i = 0; i < 255; i++) {
  GF_EXP[i] = gfSeed
  GF_LOG[gfSeed] = i
  gfSeed <<= 1
  if (gfSeed & 0x100) {
    gfSeed ^= 0x11d
  }
}
for (let i = 255; i < 512; i++) {
  GF_EXP[i] = GF_EXP[i - 255]
}

function gfMultiply(a: number, b: number): number {
  if (a === 0 || b === 0) {
    return 0
  }
  return GF_EXP[GF_LOG[a] + GF_LOG[b]]
}

/** 生成多项式 ∏(x - α^i)，首项系数为 1，按高次到低次排列 */
function rsGeneratorPoly(degree: number): Uint8Array {
  let poly = new Uint8Array([1])
  let root = 1
  for (let i = 0; i < degree; i++) {
    const next = new Uint8Array(poly.length + 1)
    for (let j = 0; j < poly.length; j++) {
      next[j] ^= poly[j]
      next[j + 1] ^= gfMultiply(poly[j], root)
    }
    poly = next
    root = gfMultiply(root, 2)
  }
  return poly
}

/** 计算纠错码字（多项式除法取余） */
function rsRemainder(data: Uint8Array, degree: number): Uint8Array {
  const generator = rsGeneratorPoly(degree)
  const remainder = new Uint8Array(degree)
  for (const byte of data) {
    const factor = byte ^ remainder[0]
    remainder.copyWithin(0, 1)
    remainder[degree - 1] = 0
    if (factor !== 0) {
      for (let i = 0; i < degree; i++) {
        remainder[i] ^= gfMultiply(generator[i + 1], factor)
      }
    }
  }
  return remainder
}

/* ------------------------------------------------------------------ *
 * 数据位流 -> 码字
 * ------------------------------------------------------------------ */

function pickSpec(byteLength: number): QrSpec {
  for (const spec of SPECS) {
    // 4 位模式指示 + 8 位字符计数（版本 1~9）+ 数据位
    if (4 + 8 + byteLength * 8 <= spec.dataCodewords * 8) {
      return spec
    }
  }
  throw new Error('内容过长，超出内置 QR 编码器容量（最多约 226 字节）')
}

function buildCodewords(spec: QrSpec, payload: Uint8Array): Uint8Array {
  const capacityBits = spec.dataCodewords * 8
  const bits: number[] = []
  const pushBits = (value: number, length: number) => {
    for (let i = length - 1; i >= 0; i--) {
      bits.push((value >>> i) & 1)
    }
  }

  pushBits(0b0100, 4) // Byte 模式
  pushBits(payload.length, 8) // 版本 1~9 的字节模式计数字段为 8 位
  for (const byte of payload) {
    pushBits(byte, 8)
  }

  // 终止符（最多 4 位）
  pushBits(0, Math.min(4, capacityBits - bits.length))
  // 补到字节边界
  while (bits.length % 8 !== 0) {
    bits.push(0)
  }
  // 用 0xEC / 0x11 交替填充剩余容量
  const padBytes = [0xec, 0x11]
  let padIndex = 0
  while (bits.length < capacityBits) {
    pushBits(padBytes[padIndex++ % 2], 8)
  }

  const data = new Uint8Array(spec.dataCodewords)
  for (let i = 0; i < data.length; i++) {
    let value = 0
    for (let j = 0; j < 8; j++) {
      value = (value << 1) | bits[i * 8 + j]
    }
    data[i] = value
  }

  // 分块 -> 各自算纠错 -> 交错排列
  if (spec.dataCodewords % spec.blocks !== 0) {
    throw new Error(`QR 分块参数不合法：version=${spec.version}`)
  }
  const blockLength = spec.dataCodewords / spec.blocks
  const dataBlocks: Uint8Array[] = []
  for (let b = 0; b < spec.blocks; b++) {
    dataBlocks.push(data.subarray(b * blockLength, (b + 1) * blockLength))
  }
  const eccBlocks = dataBlocks.map((block) => rsRemainder(block, spec.eccPerBlock))

  const codewords = new Uint8Array(spec.dataCodewords + spec.eccPerBlock * spec.blocks)
  let cursor = 0
  for (let i = 0; i < blockLength; i++) {
    for (const block of dataBlocks) {
      codewords[cursor++] = block[i]
    }
  }
  for (let i = 0; i < spec.eccPerBlock; i++) {
    for (const block of eccBlocks) {
      codewords[cursor++] = block[i]
    }
  }
  return codewords
}

/* ------------------------------------------------------------------ *
 * 矩阵绘制
 * ------------------------------------------------------------------ */

/** 掩码图案：返回该坐标是否需要取反 */
function maskBit(mask: number, x: number, y: number): boolean {
  switch (mask) {
    case 0:
      return (x + y) % 2 === 0
    case 1:
      return y % 2 === 0
    case 2:
      return x % 3 === 0
    case 3:
      return (x + y) % 3 === 0
    case 4:
      return (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0
    case 5:
      return ((x * y) % 2) + ((x * y) % 3) === 0
    case 6:
      return (((x * y) % 2) + ((x * y) % 3)) % 2 === 0
    case 7:
      return (((x + y) % 2) + ((x * y) % 3)) % 2 === 0
    default:
      throw new Error(`不支持的掩码编号：${mask}`)
  }
}

function buildMatrix(spec: QrSpec, codewords: Uint8Array, forcedMask?: number): QrMatrix {
  const version = spec.version
  const size = version * 4 + 17
  const modules: boolean[][] = Array.from({ length: size }, () => new Array<boolean>(size).fill(false))
  const isFunction: boolean[][] = Array.from({ length: size }, () => new Array<boolean>(size).fill(false))

  const setFunction = (x: number, y: number, dark: boolean) => {
    if (x < 0 || y < 0 || x >= size || y >= size) {
      return
    }
    modules[y][x] = dark
    isFunction[y][x] = true
  }

  // 定时图形（先画，定位图形再覆盖上去）
  for (let i = 0; i < size; i++) {
    setFunction(6, i, i % 2 === 0)
    setFunction(i, 6, i % 2 === 0)
  }

  // 三个定位图形 + 分隔符（9x9 覆盖绘制）
  const drawFinder = (centerX: number, centerY: number) => {
    for (let dy = -4; dy <= 4; dy++) {
      for (let dx = -4; dx <= 4; dx++) {
        const distance = Math.max(Math.abs(dx), Math.abs(dy))
        setFunction(centerX + dx, centerY + dy, distance !== 2 && distance !== 4)
      }
    }
  }
  drawFinder(3, 3)
  drawFinder(size - 4, 3)
  drawFinder(3, size - 4)

  // 校正图形
  const centers = ALIGNMENT_CENTERS[version] ?? []
  const centerCount = centers.length
  for (let row = 0; row < centerCount; row++) {
    for (let col = 0; col < centerCount; col++) {
      const overlapsFinder =
        (row === 0 && col === 0) ||
        (row === 0 && col === centerCount - 1) ||
        (row === centerCount - 1 && col === 0)
      if (overlapsFinder) {
        continue
      }
      const centerX = centers[col]
      const centerY = centers[row]
      for (let dy = -2; dy <= 2; dy++) {
        for (let dx = -2; dx <= 2; dx++) {
          setFunction(centerX + dx, centerY + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1)
        }
      }
    }
  }

  // 格式信息（纠错等级 + 掩码），共 15 位，BCH(15,5) + 固定掩码 0x5412
  const drawFormatBits = (mask: number) => {
    const data = (ECC_FORMAT_BITS << 3) | mask
    let rem = data
    for (let i = 0; i < 10; i++) {
      rem = (rem << 1) ^ ((rem >>> 9) * 0x537)
    }
    const bits = ((data << 10) | rem) ^ 0x5412
    const bitAt = (index: number) => ((bits >>> index) & 1) !== 0

    // 第一份：左上角
    for (let i = 0; i <= 5; i++) {
      setFunction(8, i, bitAt(i))
    }
    setFunction(8, 7, bitAt(6))
    setFunction(8, 8, bitAt(7))
    setFunction(7, 8, bitAt(8))
    for (let i = 9; i < 15; i++) {
      setFunction(14 - i, 8, bitAt(i))
    }

    // 第二份：右上角 + 左下角
    for (let i = 0; i < 8; i++) {
      setFunction(size - 1 - i, 8, bitAt(i))
    }
    for (let i = 8; i < 15; i++) {
      setFunction(8, size - 15 + i, bitAt(i))
    }

    // 固定的深色模块
    setFunction(8, size - 8, true)
  }

  // 版本信息（7 及以上才有，18 位 BCH）
  if (version >= 7) {
    let rem = version
    for (let i = 0; i < 12; i++) {
      rem = (rem << 1) ^ ((rem >>> 11) * 0x1f25)
    }
    const bits = (version << 12) | rem
    for (let i = 0; i < 18; i++) {
      const dark = ((bits >>> i) & 1) !== 0
      const a = size - 11 + (i % 3)
      const b = Math.floor(i / 3)
      setFunction(a, b, dark)
      setFunction(b, a, dark)
    }
  }

  // 先占位画一次格式信息，让功能模块区域标记完整
  drawFormatBits(0)

  // 数据位流
  const dataBits: number[] = []
  for (const codeword of codewords) {
    for (let i = 7; i >= 0; i--) {
      dataBits.push((codeword >>> i) & 1)
    }
  }

  // 之字形填点：从右下角开始，两列一组向上/向下
  let bitIndex = 0
  for (let right = size - 1; right >= 1; right -= 2) {
    if (right === 6) {
      right = 5 // 跳过第 6 列（定时图形）
    }
    for (let vertical = 0; vertical < size; vertical++) {
      for (let j = 0; j < 2; j++) {
        const x = right - j
        const upward = ((right + 1) & 2) === 0
        const y = upward ? size - 1 - vertical : vertical
        if (!isFunction[y][x] && bitIndex < dataBits.length) {
          modules[y][x] = dataBits[bitIndex] === 1
          bitIndex++
        }
      }
    }
  }

  const applyMask = (mask: number) => {
    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        if (!isFunction[y][x] && maskBit(mask, x, y)) {
          modules[y][x] = !modules[y][x]
        }
      }
    }
  }

  // 逐个试掩码，取罚分最低的（罚分只影响识别率，不影响可解码性）
  let bestMask = 0
  if (typeof forcedMask === 'number') {
    // 自测用：固定掩码，便于和参考实现逐模块比对
    if (!Number.isInteger(forcedMask) || forcedMask < 0 || forcedMask > 7) {
      throw new Error(`掩码编号必须是 0~7 的整数：${forcedMask}`)
    }
    bestMask = forcedMask
  } else {
    let bestPenalty = Number.POSITIVE_INFINITY
    for (let mask = 0; mask <= 7; mask++) {
      applyMask(mask)
      drawFormatBits(mask)
      const penalty = penaltyScore(modules, size)
      if (penalty < bestPenalty) {
        bestPenalty = penalty
        bestMask = mask
      }
      applyMask(mask) // 异或两次 = 还原
    }
  }
  applyMask(bestMask)
  drawFormatBits(bestMask)

  return { size, version, modules }
}

/** 标准罚分（规则 1~4），用于挑选掩码 */
function penaltyScore(modules: boolean[][], size: number): number {
  const N1 = 3
  const N2 = 3
  const N3 = 40
  const N4 = 10
  let result = 0

  const countFinderLike = (history: number[]): number => {
    const n = history[1]
    const core = n > 0 && history[2] === n && history[3] === n * 3 && history[4] === n && history[5] === n
    return (
      (core && history[0] >= n * 4 && history[6] >= n ? 1 : 0) +
      (core && history[6] >= n * 4 && history[0] >= n ? 1 : 0)
    )
  }
  const addHistory = (runLength: number, history: number[]) => {
    if (history[0] === 0) {
      runLength += size // 首段补上外侧静默区
    }
    history.copyWithin(1, 0, history.length - 1)
    history[0] = runLength
  }
  const terminateAndCount = (runColor: boolean, runLength: number, history: number[]): number => {
    if (runColor) {
      addHistory(runLength, history)
      runLength = 0
    }
    runLength += size // 末段补上外侧静默区
    addHistory(runLength, history)
    return countFinderLike(history)
  }

  // 规则 1 / 规则 3：行
  for (let y = 0; y < size; y++) {
    let runColor = false
    let runX = 0
    const history = new Array<number>(7).fill(0)
    for (let x = 0; x < size; x++) {
      if (modules[y][x] === runColor) {
        runX++
        if (runX === 5) {
          result += N1
        } else if (runX > 5) {
          result++
        }
      } else {
        addHistory(runX, history)
        if (!runColor) {
          result += countFinderLike(history) * N3
        }
        runColor = modules[y][x]
        runX = 1
      }
    }
    result += terminateAndCount(runColor, runX, history) * N3
  }

  // 规则 1 / 规则 3：列
  for (let x = 0; x < size; x++) {
    let runColor = false
    let runY = 0
    const history = new Array<number>(7).fill(0)
    for (let y = 0; y < size; y++) {
      if (modules[y][x] === runColor) {
        runY++
        if (runY === 5) {
          result += N1
        } else if (runY > 5) {
          result++
        }
      } else {
        addHistory(runY, history)
        if (!runColor) {
          result += countFinderLike(history) * N3
        }
        runColor = modules[y][x]
        runY = 1
      }
    }
    result += terminateAndCount(runColor, runY, history) * N3
  }

  // 规则 2：2x2 同色块
  for (let y = 0; y < size - 1; y++) {
    for (let x = 0; x < size - 1; x++) {
      const color = modules[y][x]
      if (color === modules[y][x + 1] && color === modules[y + 1][x] && color === modules[y + 1][x + 1]) {
        result += N2
      }
    }
  }

  // 规则 4：深浅比例偏离 50%
  let dark = 0
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      if (modules[y][x]) {
        dark++
      }
    }
  }
  const total = size * size
  const deviation = Math.abs(dark * 20 - total * 10)
  const k = Math.floor((deviation + total - 1) / total) - 1
  result += Math.max(0, k) * N4

  return result
}

/* ------------------------------------------------------------------ *
 * 对外接口
 * ------------------------------------------------------------------ */

/** 把文本编码成二维码矩阵（Byte 模式，UTF-8） */
export function encodeQr(content: string, options: { mask?: number } = {}): QrMatrix {
  const payload = new TextEncoder().encode(content)
  const spec = pickSpec(payload.length)
  const codewords = buildCodewords(spec, payload)
  return buildMatrix(spec, codewords, options.mask)
}

export interface QrDrawOptions {
  /** 每个模块的像素边长，默认 6 */
  moduleSize?: number
  /** 四周静默区宽度（模块数），标准要求 4 */
  quietZone?: number
  /** 背景色，默认白色 */
  lightColor?: string
  /** 前景色，默认近黑色 */
  darkColor?: string
}

/**
 * 把二维码矩阵画到 canvas 上。
 * 只改 canvas 的像素尺寸与显式样式尺寸，调用方负责布局。
 */
export function drawQrToCanvas(canvas: HTMLCanvasElement, matrix: QrMatrix, options: QrDrawOptions = {}) {
  const moduleSize = options.moduleSize ?? 6
  const quietZone = options.quietZone ?? 4
  const lightColor = options.lightColor ?? '#ffffff'
  const darkColor = options.darkColor ?? '#1f2329'

  const totalModules = matrix.size + quietZone * 2
  const pixelSize = totalModules * moduleSize
  const ratio = window.devicePixelRatio || 1

  canvas.width = Math.round(pixelSize * ratio)
  canvas.height = Math.round(pixelSize * ratio)
  canvas.style.width = `${pixelSize}px`
  canvas.style.height = `${pixelSize}px`

  const ctx = canvas.getContext('2d')
  if (!ctx) {
    return
  }
  ctx.setTransform(ratio, 0, 0, ratio, 0, 0)
  ctx.fillStyle = lightColor
  ctx.fillRect(0, 0, pixelSize, pixelSize)
  ctx.fillStyle = darkColor
  for (let y = 0; y < matrix.size; y++) {
    for (let x = 0; x < matrix.size; x++) {
      if (matrix.modules[y][x]) {
        ctx.fillRect(
          (x + quietZone) * moduleSize,
          (y + quietZone) * moduleSize,
          moduleSize,
          moduleSize
        )
      }
    }
  }
}
