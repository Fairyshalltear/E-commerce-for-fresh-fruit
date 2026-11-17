import fs from 'node:fs'
import assert from 'node:assert/strict'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as Vue from 'vue'

// Exercise the page's actual setup and handlers with controlled network/dialog responses.
const { descriptor } = parse(fs.readFileSync(new URL('../src/views/AdminAiModelsView.vue', import.meta.url), 'utf8'))
const compiled = compileScript(descriptor, { id: 'model-deletion-regression', genDefaultAs: 'component' }).content
const source = compiled
  .replace(/import\s+\{([^}]+)\}\s+from\s+['"]vue['"]/g, (_, names) => `const {${names.replace(/\s+as\s+/g, ':')}} = Vue`)
  .replace(/^import .*$/gm, '')
const javascript = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
const factory = new Function('Vue', 'api', 'ElMessageBox', 'PageStatus', 'errorMessage', javascript + '\nreturn component')
const renderer = Vue.createRenderer({
  createElement: () => ({}), createText: () => ({}), createComment: () => ({}),
  insert() {}, remove() {}, setText() {}, setElementText() {}, patchProp() {},
  parentNode: () => null, nextSibling: () => null,
})
const flush = async () => { await new Promise(resolve => setTimeout(resolve, 0)); await Vue.nextTick() }
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
const fixture = (mode = 'DELETE') => ({ id: 42, name: '临时草稿', active: false, encryptionReady: true, parameters: {}, deletionMode: mode, deletionReason: mode === 'ARCHIVE' ? '保留历史追溯，仅从管理列表移除' : '没有引用，可永久删除' })

async function mount(model) {
  let models = [model], dialog = deferred(), response = null
  const calls = { confirm: [], deleted: [], loads: 0 }
  const api = {
    listModels: async () => { calls.loads++; return models }, listProfiles: async () => [],
    deleteModel: async id => { calls.deleted.push(id); if (response) await response.promise; models = []; return { id, mode: model.deletionMode } },
  }
  const component = factory(Vue, api, { confirm: (...args) => { calls.confirm.push(args); return dialog.promise } }, {}, failure => failure.message)
  component.render = () => null
  const app = renderer.createApp(component), instance = app.mount({}).$
  await flush()
  return { state: instance.setupState, calls, dialog, setDialog(next) { dialog = next }, setResponse(next) { response = next }, unmount: () => app.unmount() }
}

for (const invalid of [{ ...fixture(), deletionMode: undefined, deletionReason: undefined }, fixture('BLOCKED'), { ...fixture(), deletionReason: '' }]) {
  const page = await mount(invalid)
  assert.equal(page.state.deletionAllowed, false)
  assert.ok(page.state.deletionReason && !page.state.deletionReason.includes('undefined'))
  await page.state.remove()
  assert.equal(page.calls.confirm.length, 0)
  assert.equal(page.calls.deleted.length, 0)
  page.unmount()
}

{
  const page = await mount(fixture())
  const first = page.state.remove()
  await page.state.remove() // A second click during confirmation must not create another modal.
  assert.equal(page.calls.confirm.length, 1)
  assert.equal(page.state.busy, true)
  page.dialog.reject('cancel'); await first
  assert.equal(page.state.busy, false)
  assert.equal(page.state.selected.id, 42)
  assert.equal(page.calls.deleted.length, 0)
  page.unmount()
}

for (const mode of ['DELETE', 'ARCHIVE']) {
  const page = await mount(fixture(mode)), network = deferred()
  page.setResponse(network)
  page.state.chatKey = 'temporary-unsaved-value'; page.state.embeddingKey = 'temporary-unsaved-value'
  page.state.results = [{ name: 'chat', success: true }]
  const deleting = page.state.remove(); page.dialog.resolve('confirm'); await flush()
  assert.deepEqual(page.calls.deleted, [42])
  assert.ok(page.calls.confirm[0][0].includes(page.state.deletionReason))
  await page.state.remove()
  assert.equal(page.calls.deleted.length, 1)
  network.resolve(); await deleting; await flush()
  assert.equal(page.state.busy, false)
  assert.equal(page.state.selected, null)
  assert.equal(page.state.models.length, 0)
  assert.equal(page.state.form.name, '')
  assert.equal(page.state.form.baseId, undefined)
  assert.equal(page.state.chatKey, '')
  assert.equal(page.state.embeddingKey, '')
  assert.equal(page.state.results.length, 0)
  assert.equal(page.calls.loads, 2)
  assert.match(page.state.notice, mode === 'DELETE' ? /永久删除/ : /历史追溯数据保留/)
  page.unmount()
}

{
  const page = await mount(fixture()), network = deferred()
  page.setResponse(network)
  const deleting = page.state.remove(); page.dialog.resolve('confirm'); await flush()
  network.reject(new Error('配置仍被使用')); await deleting
  assert.equal(page.state.busy, false)
  assert.equal(page.state.selected.id, 42)
  assert.equal(page.state.formError, '配置仍被使用')
  const retryDialog = deferred(); page.setDialog(retryDialog)
  const retry = page.state.remove(); retryDialog.reject('cancel'); await retry
  assert.equal(page.calls.confirm.length, 2)
  assert.equal(page.state.busy, false)
  page.unmount()
}

console.log('Model deletion: old API/blocked guards, cancel, repeated clicks, DELETE/ARCHIVE refresh and state clearing, failure and retry passed')
