import fs from 'node:fs'
import ts from 'typescript'
import assert from 'node:assert/strict'
const compiled = ts.transpileModule(fs.readFileSync(new URL('../src/ai/sse.ts', import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
const { SseParser, readSse } = await import('data:text/javascript;base64,' + Buffer.from(compiled).toString('base64'))
const text = ': heartbeat\r\nevent: delta\r\ndata: {\r\ndata: "text":"草莓🍎"}\r\n\r\nevent: done\ndata: {"status":"COMPLETED"}\n\n'
for (let split = 1; split < text.length; split++) {
  const frames = [], parser = new SseParser(frame => frames.push(frame))
  parser.feed(text.slice(0, split)); parser.feed(text.slice(split)); parser.close()
  assert.equal(frames.length, 2); assert.equal(JSON.parse(frames[0].data).text, '草莓🍎'); assert.equal(frames[1].event, 'done')
}
const bytes = new TextEncoder().encode(text), frames = []
const stream = new ReadableStream({ start(controller) { for (const byte of bytes) controller.enqueue(new Uint8Array([byte])); controller.close() } })
await readSse(new Response(stream), frame => frames.push(frame))
assert.equal(JSON.parse(frames[0].data).text, '草莓🍎')
const partial = [], parser = new SseParser(frame => partial.push(frame))
parser.feed('event: done\ndata: {"unfinished":'); parser.close(); assert.equal(partial.length, 0)
assert.throws(() => new SseParser(() => {}).feed('x'.repeat(1024 * 1024 + 1)))
console.log('SSE: network splits, multiline data, CRLF, UTF-8/emoji, partial EOF and frame size checks passed')
