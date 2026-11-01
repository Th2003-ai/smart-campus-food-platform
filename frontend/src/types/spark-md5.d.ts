declare module 'spark-md5' {
  export default class SparkMD5 {
    static hash(str: string, raw?: boolean): string
    static ArrayBuffer: {
      new (): {
        append(buf: ArrayBuffer): void
        end(raw?: boolean): string
        reset(): void
      }
      hash(buf: ArrayBuffer, raw?: boolean): string
    }
  }
}
