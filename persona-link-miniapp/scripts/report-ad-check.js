const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function setup() {
  const calls = [];
  let status = 2;
  let failSubmit = false;
  let failAccess = false;
  let creates = 0;
  let reads = 0;
  const handlers = {};
  const ad = {
    onLoad(fn) { handlers.load = fn; }, offLoad() { delete handlers.load; },
    onError(fn) { handlers.error = fn; }, offError() { delete handlers.error; },
    onClose(fn) { handlers.close = fn; }, offClose() { delete handlers.close; },
    async load() {}, async show() {}
  };
  const sandbox = {
    module: { exports: {} },
    wx: { createRewardedVideoAd() { creates++; return ad; } },
    require() { return { async authenticatedRequestData(request) {
      calls.push(request);
      if (request.url.endsWith('/access')) {
        if (failAccess) throw new Error('network');
        return { status, taskId: 'task', adUnitId: 'adunit-test' };
      }
      if (failSubmit) throw new Error('network');
      return { status: 1 };
    } }; }
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../utils/report-access.js'), 'utf8'), sandbox);
  const page = { data: {}, setData(patch) { Object.assign(this.data, patch); } };
  const gate = sandbox.module.exports.createReportAccess(page, '/report');
  const load = async (active) => { if (active()) { reads++; page.setData({ state: 'ready' }); } };
  return { page, gate, calls, handlers, load, setStatus(value) { status = value; },
    setFailSubmit(value) { failSubmit = value; }, setFailAccess(value) { failAccess = value; },
    get creates() { return creates; }, get reads() { return reads; } };
}
const tick = () => new Promise((resolve) => setImmediate(resolve));
async function main() {
  let test = setup();
  await test.gate.run(test.load);
  assert.equal(test.page.data.state, 'locked');
  assert.equal(test.creates, 0);
  assert.equal(test.reads, 0);
  let run = test.gate.run(test.load, true);
  await tick();
  await test.gate.run(test.load, true);
  assert.equal(test.creates, 1);
  test.handlers.close({ isEnded: false });
  await run;
  assert.equal(test.calls.filter((call) => call.method === 'POST').length, 0);
  assert.equal(test.reads, 0);
  run = test.gate.run(test.load, true);
  await tick();
  test.setFailSubmit(true);
  const close = test.handlers.close;
  close({ isEnded: true }); close({ isEnded: true });
  await run;
  assert.equal(test.page.data.state, 'error');
  test.setFailSubmit(false);
  await test.gate.run(test.load);
  assert.equal(test.creates, 2, 'retry completed result without replay');
  assert.equal(test.reads, 1);

  test = setup();
  await test.gate.run(test.load);
  test.setStatus(1);
  await test.gate.run(test.load, true);
  assert.equal(test.creates, 0, 'latest disabled config avoids ad');
  assert.equal(test.reads, 1);

  test = setup(); test.setFailAccess(true);
  await test.gate.run(test.load, true);
  assert.equal(test.creates, 0);
  assert.equal(test.calls.length, 1, 'business failure is not ad failure');

  test = setup(); run = test.gate.run(test.load, true); await tick();
  test.handlers.error({ errCode: 1004 }); await run;
  assert.equal(test.calls.at(-1).data.outcome, 2);
  assert.equal(test.calls.at(-1).data.errorCode, 1004);

  test = setup(); run = test.gate.run(test.load, true); await tick();
  const lateClose = test.handlers.close;
  test.gate.dispose(); lateClose({ isEnded: true }); await run;
  assert.equal(Object.keys(test.handlers).length, 0);
  assert.equal(test.reads, 0);
  assert.equal(test.calls.length, 1, 'disposed callback cannot unlock');
  test = setup(); run = test.gate.run(test.load, true);
  test.gate.setHidden(true); await run;
  assert.equal(test.creates, 0, 'late access response cannot play on hidden page');
  test.gate.setHidden(false);
  test.setStatus(1); await test.gate.run(test.load);
  assert.equal(test.reads, 1);
  console.log('Report ad checks passed.');
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
