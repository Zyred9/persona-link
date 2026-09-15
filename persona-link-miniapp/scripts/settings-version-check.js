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
  for (const [values, expectedVersion, expectedEmail] of [
    [{ version: '2.3.4', email: 'support@example.com' }, 'V2.3.4', 'support@example.com'],
    [{ version: ' V3.0.0-beta ', email: ' Service@Example.com ' }, 'V3.0.0-beta', 'Service@Example.com'],
    [{ version: '', email: '' }, '未配置', '未配置'],
    [{ version: null, email: null }, '未配置', '未配置'],
    [{}, '未配置', '未配置']
  ]) {
    const pending = component.loadSettingsConfig();
    assert.equal(component.data.versionLabel, '加载中...');
    assert.equal(component.data.contactEmail, '加载中...');
    assert.equal(requests.at(-1).options.url, '/api/miniapp/config/values');
    assert.equal(requests.at(-1).options.data.keys, 'miniapp.version,miniapp.contact_email');
    requests.at(-1).resolve({ 'miniapp.version': values.version, 'miniapp.contact_email': values.email });
    await pending;
    assert.equal(component.data.versionLabel, expectedVersion, String(values.version));
    assert.equal(component.data.contactEmail, expectedEmail, String(values.email));
  }
  const failed = component.loadSettingsConfig();
  requests.at(-1).reject(new Error('offline'));
  await failed;
  assert.equal(component.data.versionLabel, '暂不可用');
  assert.equal(component.data.contactEmail, '暂不可用');
  const earlier = component.loadSettingsConfig();
  const oldRequest = requests.at(-1);
  const latest = component.loadSettingsConfig();
  requests.at(-1).resolve({ 'miniapp.version': '5.0.0', 'miniapp.contact_email': 'latest@example.com' });
  await latest;
  oldRequest.resolve({ 'miniapp.version': '4.0.0', 'miniapp.contact_email': 'stale@example.com' });
  await earlier;
  assert.equal(component.data.versionLabel, 'V5.0.0', '旧响应不能覆盖最新配置');
  assert.equal(component.data.contactEmail, 'latest@example.com', '旧响应不能覆盖最新联系邮箱');
  const detached = component.loadSettingsConfig();
  component.componentAttached = false;
  requests.at(-1).resolve({ 'miniapp.version': '6.0.0', 'miniapp.contact_email': 'detached@example.com' });
  await detached;
  assert.equal(component.data.versionLabel, '加载中...', '组件销毁后不更新状态');
  assert.equal(component.data.contactEmail, '加载中...', '组件销毁后不更新联系邮箱');
  component.componentAttached = true;
  const left = component.loadSettingsConfig();
  component.data.currentView = 'profile';
  requests.at(-1).resolve({ 'miniapp.version': '7.0.0', 'miniapp.contact_email': 'left@example.com' });
  await left;
  assert.equal(component.data.versionLabel, '加载中...', '离开设置后忽略响应');
  assert.equal(component.data.contactEmail, '加载中...', '离开设置后忽略联系邮箱响应');
  let opened = 0;
  component.loadSettingsConfig = () => { opened++; };
  component.prepareView('settings');
  assert.equal(opened, 1, '进入设置必须读取服务端配置');
  const template = fs.readFileSync(path.join(__dirname, '../components/account-center/index.wxml'), 'utf8');
  assert.match(template, /settings-row__value">\{\{contactEmail\}\}</, '联系邮箱必须来自服务端配置');
  assert.doesNotMatch(template, /zyred_11211@163\.com/, '联系邮箱不得再硬编码');
  console.log('SETTINGS_VERSION_CHECK_OK version/contact-email/empty-fallback/failure/stale/detached/leave');
}
run().catch((error) => { console.error(error); process.exitCode = 1; });
