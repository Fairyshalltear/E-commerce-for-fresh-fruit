export interface SseFrame { event: string; data: string }

export class SseParser {
  private buffer = ''
  private event = 'message'
  private data: string[] = []
  private size = 0
  constructor(private readonly onFrame: (frame: SseFrame) => void) {}
  feed(chunk: string) {
    this.buffer += chunk
    if (this.buffer.length + this.size > 1024 * 1024) throw new Error('流式响应帧过长')
    let end: number
    while ((end = this.buffer.indexOf('\n')) >= 0) {
      const line = this.buffer.slice(0, end).replace(/\r$/, '')
      this.buffer = this.buffer.slice(end + 1)
      if (!line) {
        if (this.data.length) this.onFrame({ event: this.event, data: this.data.join('\n') })
        this.event = 'message'; this.data = []; this.size = 0
        continue
      }
      if (line.startsWith(':')) continue
      const colon = line.indexOf(':')
      const field = colon < 0 ? line : line.slice(0, colon)
      let value = colon < 0 ? '' : line.slice(colon + 1)
      if (value.startsWith(' ')) value = value.slice(1)
      if (field === 'event') this.event = value
      if (field === 'data') { this.data.push(value); this.size += value.length }
    }
  }
  close() { this.buffer = ''; this.data = []; this.size = 0 }
}

export async function readSse(response: Response, onFrame: (frame: SseFrame) => void) {
  if (!response.body) throw new Error('浏览器暂不支持流式回答')
  const reader = response.body.getReader(), decoder = new TextDecoder('utf-8')
  const parser = new SseParser(onFrame)
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) { parser.feed(decoder.decode()); break }
      parser.feed(decoder.decode(value, { stream: true }))
    }
  } finally { parser.close(); await reader.cancel().catch(() => undefined); reader.releaseLock() }
}
