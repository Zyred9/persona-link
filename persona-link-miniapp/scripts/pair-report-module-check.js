const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function setup() {
  let definition, resolveModule, rejectModule, loads = 0, runs = 0;
  const imports = [];
  const requireModule = () => ({});
  requireModule.async = (name) => {
    imports.push(name);
    loads++;
    return new Promise((resolve, reject) => { resolveModule = resolve; rejectModule = reject; });
  };
  const gate = { setHidden(value) { this.hidden = value; }, dispose() { this.disposed = true; },
    async run() { runs++; page.setData({ state: 'ready' }); } };
  const file = path.join(__dirname, '../subpackages/pair/pages/result/index.js');
  vm.runInNewContext(fs.readFileSync(file, 'utf8'), {
    Page(value) { definition = value; }, require: requireModule
  });
  const page = { ...definition, data: { ...definition.data }, pairSessionId: '12',
    setData(patch) { Object.assign(this.data, patch); } };
  return { page, gate, imports,
    resolve() { resolveModule({ createReportAccess: () => gate }); },
    reject() { rejectModule(new Error('offline')); },
    get loads() { return loads; }, get runs() { return runs; } };
}

async function main() {
  let test = setup();
  let pending = test.page.loadReport();
  await test.page.loadReport();
  assert.equal(test.loads, 1, '连续点击只加载一次模块');
  assert.equal(test.imports[0], '../../../test/utils/report-access.js');
  assert.ok(fs.existsSync(path.resolve(__dirname, '../subpackages/pair/pages/result', test.imports[0])));
  test.resolve(); await pending;
  assert.equal(test.runs, 1, '加载后仍通过权限入口');

  test = setup(); pending = test.page.loadReport(); test.reject(); await pending;
  assert.equal(test.page.data.state, 'error');
  pending = test.page.loadReport(); test.resolve(); await pending;
  assert.equal(test.loads, 2, '下载失败后可以重试');
  assert.equal(test.runs, 1);

  test = setup(); pending = test.page.loadReport(); test.page.onUnload(); test.resolve(); await pending;
  assert.equal(test.runs, 0, '卸载后不创建权限入口');
  assert.equal(test.page.reportAccess, undefined);

  test = setup(); pending = test.page.loadReport(); test.page.onHide(); test.resolve(); await pending;
  assert.equal(test.runs, 0, '隐藏页面不发起权限流程');
  assert.equal(test.gate.hidden, true);
  test.page.onShow();
  assert.equal(test.runs, 1, '重新显示后恢复加载');
  assert.equal(test.gate.hidden, false);
  console.log('PAIR_REPORT_MODULE_CHECK_OK async/retry/dedup/unload/hide/show');
}

main().catch((error) => { console.error(error); process.exitCode = 1; });
