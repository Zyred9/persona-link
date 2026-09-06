import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

export function createVueStubs(overrides = {}) {
  return {
    ref: (value) => ({ value }),
    reactive: (value) => value,
    computed: (getter) => ({ get value() { return getter() } }),
    onMounted: () => {},
    onUnmounted: () => {},
    onBeforeUnmount: () => {},
    watch: () => {},
    nextTick: async () => {},
    ...overrides,
  }
}

function executeCommonJs(compiled, options = {}) {
  const moduleStubs = {
    vue: createVueStubs(options.vue),
    'vue-router': {
      useRoute: () => ({ params: {}, query: {} }),
      useRouter: () => ({ push: () => {}, replace: () => {} }),
      onBeforeRouteLeave: () => {},
    },
    ...(options.modules || {}),
  }
  const context = {
    module: { exports: {} },
    exports: {},
    require(name) {
      if (Object.hasOwn(moduleStubs, name)) return moduleStubs[name]
      throw new Error(`Unexpected module: ${name}`)
    },
    crypto: globalThis.crypto,
    window: { alert: () => {}, confirm: () => true, setTimeout: () => {} },
    console,
    setTimeout,
    clearTimeout,
    ...(options.globals || {}),
  }
  context.exports = context.module.exports
  vm.runInNewContext(compiled, context)
  return context.module.exports
}

export function compileScriptSetup(source, exportExpression, options = {}) {
  const match = source.match(/<script setup(?:\s+lang="ts")?>([\s\S]*?)<\/script>/)
  assert.ok(match, '必须能读取 Vue script setup')
  const testableSource = `${match[1]}
export const __test = ${exportExpression}`
  const compiled = ts.transpileModule(testableSource, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText
  return executeCommonJs(compiled, options).__test
}

export function loadViewScriptSetup(filePath, exportExpression, options = {}) {
  return compileScriptSetup(readFileSync(filePath, 'utf8'), exportExpression, options)
}

export function loadTypeScriptModule(filePath, options = {}) {
  const compiled = ts.transpileModule(readFileSync(filePath, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText
  return executeCommonJs(compiled, options)
}
