const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../config/env.js'), 'utf8');
function resolve(envVersion, stored = '') {
  const sandbox = {
    module: { exports: {} },
    wx: {
      getAccountInfoSync: () => ({ miniProgram: { envVersion } }),
      getStorageSync: () => stored
    }
  };
  vm.runInNewContext(source, sandbox);
  return sandbox.module.exports.getApiBaseUrl();
}
assert.equal(resolve('develop'), 'http://192.168.0.23:8080');
assert.equal(resolve('develop', 'http://localhost:9000/'), 'http://localhost:9000');
for (const env of ['trial', 'release']) {
  assert.equal(resolve(env), env === 'trial' ? 'http://45.77.4.60:8080' : 'https://persona.seeyoume.vip');
  assert.equal(resolve(env, 'http://localhost:9000/'), env === 'trial' ? 'http://45.77.4.60:8080' : 'https://persona.seeyoume.vip');
}
assert.throws(() => resolve('unknown'), /尚未配置 API 地址/);
console.log('ENV_CHECK_OK development/trial/release/cache-isolation');
