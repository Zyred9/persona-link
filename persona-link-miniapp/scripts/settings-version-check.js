const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

let definition;
const requests = [];
vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../components/account-center/index.js'), 'utf8'), {
  Component: (value) => { definition = value; },
  require: () => ({
    requestData: (options) => new Promise((resolve, reject) => requests.push({ options, resolve, reject }))
  }),
  wx: { getAccountInfoSync: () => { throw new Error('版本不能读取微信包信息'); } }
});
const component = { ...definition.methods, data: { ...definition.data, currentView: 'settings' }, componentAttached: true,
  setData(value) { Object.assign(this.data, value); } };

async function run() {
  for (const [version, expected] of [['2.3.4', 'V2.3.4'], [' V3.0.0-beta ', 'V3.0.0-beta'], ['', '未配置'], [null, '未配置']]) {
    const pending = component.loadVersionLabel();
    assert.equal(component.data.versionLabel, '加载中...');
    assert.equal(requests.at(-1).options.url, '/api/miniapp/config');
    requests.at(-1).resolve({ version });
    await pending;
    assert.equal(component.data.versionLabel, expected);
  }
  const failed = component.loadVersionLabel();
  requests.at(-1).reject(new Error('offline'));
  await failed;
  assert.equal(component.data.versionLabel, '暂不可用');
  const earlier = component.loadVersionLabel();
  const oldRequest = requests.at(-1);
  const latest = component.loadVersionLabel();
  requests.at(-1).resolve({ version: '5.0.0' });
  await latest;
  oldRequest.resolve({ version: '4.0.0' });
  await earlier;
  assert.equal(component.data.versionLabel, 'V5.0.0', '旧响应不能覆盖最新配置');
  const detached = component.loadVersionLabel();
  component.componentAttached = false;
  requests.at(-1).resolve({ version: '6.0.0' });
  await detached;
  assert.equal(component.data.versionLabel, '加载中...', '组件销毁后不更新状态');
  component.componentAttached = true;
  const left = component.loadVersionLabel();
  component.data.currentView = 'profile';
  requests.at(-1).resolve({ version: '7.0.0' });
  await left;
  assert.equal(component.data.versionLabel, '加载中...', '离开设置后忽略响应');
  let opened = 0;
  component.loadVersionLabel = () => { opened++; };
  component.prepareView('settings');
  assert.equal(opened, 1, '进入设置必须读取服务端配置');
  console.log('SETTINGS_VERSION_CHECK_OK');
}
run().catch((error) => { console.error(error); process.exitCode = 1; });
