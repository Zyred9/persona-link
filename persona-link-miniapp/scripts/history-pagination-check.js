const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '..');
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');
let definition, modal;
let pairAuto = true;
const requests = [];
vm.runInNewContext(read('components/account-center/index.js'), {
  Component: (value) => { definition = value; },
  require: () => ({ authenticatedRequestData: (options) => pairAuto && options.url.startsWith('/api/miniapp/pairs?')
    ? Promise.resolve({ records: [], total: 0 })
    : new Promise((resolve, reject) => requests.push({ ...options, resolve, reject })) }),
  wx: { getStorageSync() {}, showModal(value) { modal = value; }, showToast() {} },
  clearTimeout() {}
});
function component() {
  return { ...definition.methods, componentAttached: true,
    data: { ...definition.data, currentView: 'history' },
    setData(value, callback) { Object.assign(this.data, value); if (callback) callback(); }, triggerEvent() {} };
}
const page = (start, count = 20, total = 45) => ({ total, records: Array.from({ length: count }, (_, i) => ({
  reportId: String(start - i), generatedAt: '2026-09-09T10:00:00', resultCode: 'A'
})) });
async function run() {
  const history = component();
  const first = history.loadRecords();
  await history.loadMoreRecords(); assert.equal(requests.length, 1);
  requests.at(-1).resolve(page(45)); await first;
  assert.equal(history.data.records.length, 20); assert.equal(history.data.historyHasMore, true);
  const second = history.loadMoreRecords();
  await history.loadMoreRecords(); assert.equal(requests.length, 2);
  assert.match(requests.at(-1).url, /page=2&size=20/);
  requests.at(-1).reject(new Error('offline')); await second;
  assert.equal(history.data.records.length, 20); assert.equal(history.historyPage, 1);
  assert.equal(history.data.historyState, 'ready'); assert.match(history.data.historyMoreError, /offline/);
  const retry = history.loadMoreRecords(); assert.match(requests.at(-1).url, /page=2/);
  requests.at(-1).resolve(page(26)); await retry;
  assert.equal(history.data.records.length, 39); // 重叠记录 26 不重复插入。
  assert.equal(history.data.records[0].reportId, '45');
  const last = history.loadMoreRecords(); requests.at(-1).resolve(page(6, 6)); await last;
  assert.equal(history.data.records.length, 45); assert.equal(history.data.historyHasMore, false);
  const count = requests.length; await history.loadMoreRecords(); assert.equal(requests.length, count);

  let pending = history.loadRecords(); requests.at(-1).resolve(page(60, 20, 80)); await pending;
  const late = history.loadMoreRecords(); const staleRequest = requests.at(-1);
  const refreshed = history.loadRecords(); const freshRequest = requests.at(-1);
  staleRequest.resolve(page(40, 20, 80)); await late;
  assert.equal(history.historyPageLoading, true); // 旧页不能解锁刷新。
  freshRequest.resolve(page(90, 20, 100)); await refreshed;
  assert.equal(history.data.records[0].reportId, '90');
  pending = history.loadMoreRecords(); requests.at(-1).resolve(page(0, 0, 100)); await pending;
  assert.equal(history.data.historyHasMore, false); // total 过期也不会空页无限加载。

  pending = history.loadRecords(); requests.at(-1).resolve(page(40)); await pending;
  const beforeDelete = history.loadMoreRecords(); const deletedPage = requests.at(-1);
  history.deleteRecord({ currentTarget: { dataset: { reportId: '40' } } });
  const deletion = modal.success({ confirm: true }); requests.at(-1).resolve(); await deletion;
  const afterDelete = requests.at(-1);
  deletedPage.resolve(page(20)); await beforeDelete;
  assert.equal(history.data.historyState, 'loading');
  afterDelete.resolve(page(39)); await new Promise(setImmediate);
  assert.equal(history.data.records[0].reportId, '39');
  const leaving = history.loadMoreRecords(); history.setCurrentView('profile');
  requests.at(-1).resolve(page(19)); await leaving;
  assert.equal(history.data.records.length, 20);

  pairAuto = false;
  const pairs = component();
  let both = pairs.loadRecords();
  const singleFirst = requests.at(-2), pairFirst = requests.at(-1);
  assert.match(pairFirst.url, /\/pairs\?page=1&size=20/);
  singleFirst.reject(new Error('single offline'));
  const pairPage = (start, count = 20, total = 23) => ({ total, records: Array.from({ length: count }, (_, i) => ({
    pairSessionId: String(start - i), title: `真实题型${start - i}`, pairStatus: i % 2 ? 1 : 4,
    createdAt: '2026-09-08T12:00:00', versionNo: 3
  })) });
  pairFirst.resolve(pairPage(23)); await both;
  assert.equal(pairs.data.historyState, 'error');
  assert.equal(pairs.data.pairHistoryState, 'ready');
  assert.equal(pairs.data.pairRecords[0].title, '真实题型23');
  assert.match(pairs.data.pairRecords[0].meta, /V3/);
  let more = pairs.loadMoreRecords(); const pairSecond = requests.at(-1);
  const requestCount = requests.length;
  await pairs.loadMoreRecords(); assert.equal(requests.length, requestCount);
  pairSecond.reject(new Error('pair offline')); await more;
  assert.equal(pairs.data.pairRecords.length, 20); assert.equal(pairs.pairHistoryPage, 1);
  more = pairs.retryPairHistory(); assert.match(requests.at(-1).url, /page=2/);
  requests.at(-1).resolve(pairPage(4, 4)); await more;
  assert.equal(pairs.data.pairRecords.length, 23); assert.equal(pairs.data.pairHistoryHasMore, false);
  const recover = pairs.retrySingleHistory(); requests.at(-1).resolve(page(40)); await recover;
  assert.equal(pairs.data.historyState, 'ready'); assert.equal(pairs.data.pairRecords.length, 23);
  both = pairs.loadRecords(); requests.at(-2).resolve(page(40)); requests.at(-1).reject(new Error('pair first failed')); await both;
  assert.equal(pairs.data.historyState, 'ready'); assert.equal(pairs.data.pairHistoryState, 'error');
  const pairRetry = pairs.retryPairHistory(); const stalePair = requests.at(-1);
  const latest = pairs.loadRecords(); requests.at(-2).resolve(page(50)); requests.at(-1).resolve(pairPage(50)); await latest;
  stalePair.resolve(pairPage(23)); await pairRetry;
  assert.equal(pairs.data.pairRecords[0].pairSessionId, '50');
  const pairLate = pairs.retryPairHistory(); pairs.setCurrentView('profile');
  requests.at(-1).resolve(pairPage(30, 3)); await pairLate;
  assert.equal(pairs.data.pairRecords.length, 20);

  for (const [file, handler, selector] of [
    ['pages/home/index', 'loadMoreAccountRecords', '#home-account-center'],
    ['pages/profile/index', 'loadMoreAccountRecords', '#profile-account-center'],
    ['subpackages/account/pages/history/index', 'onReachBottom', '#history-account-center']
  ]) {
    let entry;
    vm.runInNewContext(read(`${file}.js`), { Page: (value) => { entry = value; }, require: () => ({}) });
    let triggered = 0;
    entry[handler].call({ data: { containerVisible: true, containerMode: 'account', accountVisible: true },
      selectComponent(id) { assert.equal(id, selector); return { loadMoreRecords() { triggered++; } }; } });
    assert.equal(triggered, 1);
    if (handler !== 'onReachBottom') assert.match(read(`${file}.wxml`), /bindscrolltolower="loadMoreAccountRecords"/);
    else assert.ok(read(`${file}.wxml`).includes(`id="${selector.slice(1)}"`));
  }
  const sql = read('../persona-link-server/src/main/resources/mapper/ReportMapper.xml');
  assert.match(sql, /ORDER BY r.generated_at DESC, r.id DESC/);
  for (const query of ['selectHistory', 'countHistory']) {
    const statement = sql.match(new RegExp(`<select id="${query}"[\\s\\S]*?</select>`));
    assert.ok(statement, query);
    assert.match(statement[0], /v.deleted = 0/);
    assert.match(statement[0], /r.deleted = 0/);
  }
  console.log('HISTORY_PAGINATION_CHECK_OK single+pair/paging/retry/dedup/stale/delete/failure-isolation/three-entry-wiring/order');
}
run().catch((error) => { console.error(error); process.exitCode = 1; });
